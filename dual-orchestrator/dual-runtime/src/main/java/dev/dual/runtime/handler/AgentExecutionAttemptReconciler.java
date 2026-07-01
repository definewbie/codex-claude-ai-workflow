package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.dual.core.execution.AgentExecutionAttempt;
import dev.dual.core.execution.AgentExecutionAttemptStatus;
import dev.dual.core.execution.AgentExecutionEvidence;
import dev.dual.core.execution.AgentExecutionFinalizer;
import dev.dual.core.execution.AgentExecutionOutcome;
import dev.dual.core.execution.AgentExecutionOutcomeResolver;
import dev.dual.core.execution.AgentExecutionProcessStatus;
import dev.dual.core.execution.AgentExecutionRetryPolicy;
import dev.dual.core.execution.AgentExecutionTimelineEventKind;
import dev.dual.core.execution.AdapterSignal;
import dev.dual.core.execution.FinalizedAgentExecution;
import dev.dual.core.execution.ProcessResult;
import dev.dual.core.execution.TerminalControlResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Closes agent run executions that never reached a typed terminal outcome.
 *
 * <p>When the acpx transport does not return (process killed by stop-loss, acpx
 * pending, or a tool pending indefinitely), run.json is left in-flight
 * (prepared/RUNNING) with no executionOutcome. This reconciler inspects such a
 * run.json and, when the process is provably gone (dead pid) or the run has
 * exceeded the stale boundary, converges it to a typed terminal
 * {@link AgentExecutionAttempt} + {@link AgentExecutionOutcome} using the same
 * {@link AgentExecutionFinalizer} as the completion path.</p>
 *
 * <p>It never overwrites a run that already has a typed executionOutcome, and it
 * never force-closes a run that may still be progressing (no dead pid and within
 * the stale boundary). The write preserves all existing prompt/transcript/stderr
 * refs and only adds/updates execution fields.</p>
 */
public final class AgentExecutionAttemptReconciler {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final Duration staleTimeout;
    private final AgentExecutionFinalizer finalizer;

    public AgentExecutionAttemptReconciler(Duration staleTimeout) {
        this(staleTimeout, new AgentExecutionFinalizer(new AgentExecutionOutcomeResolver()));
    }

    AgentExecutionAttemptReconciler(Duration staleTimeout, AgentExecutionFinalizer finalizer) {
        if (staleTimeout == null || staleTimeout.isNegative() || staleTimeout.isZero()) {
            throw new IllegalArgumentException("staleTimeout must be positive");
        }
        this.staleTimeout = staleTimeout;
        this.finalizer = finalizer == null ? new AgentExecutionFinalizer(new AgentExecutionOutcomeResolver()) : finalizer;
    }

    public Reconciliation reconcile(Path runJson) throws IOException {
        if (runJson == null || !Files.isRegularFile(runJson)) {
            return Reconciliation.absent();
        }
        var json = (ObjectNode) OBJECT_MAPPER.readTree(runJson.toFile());
        if (hasTerminalOutcome(json)) {
            return Reconciliation.absent();
        }
        var classification = classify(json);
        if (classification.isEmpty()) {
            return Reconciliation.absent();
        }
        var evidence = new AgentExecutionEvidence(
                classification.get().process(),
                TerminalControlResult.missing(),
                List.of(),
                List.of()
        );
        var finalized = finalizer.finalize(baseAttempt(json), evidence);
        writeReconciled(runJson, json, finalized, classification.get());
        return new Reconciliation(true, finalized.outcome());
    }

    /**
     * Reconciles every in-flight run.json under a runs directory (e.g. a
     * workstream's runs/). Returns the number of runs that were closed to a
     * typed terminal outcome. Runs that already have a terminal outcome, or that
     * may still be progressing, are left untouched.
     */
    public int reconcileRuns(Path runsDir) throws IOException {
        return reconcileRuns(runsDir, "");
    }

    /**
     * Reconciles in-flight run.json files for one node definition. A blank
     * {@code nodeId} preserves the legacy "all runs" sweep used by stale
     * RUNNING-node recovery.
     */
    public int reconcileRuns(Path runsDir, String nodeId) throws IOException {
        if (runsDir == null || !Files.isDirectory(runsDir)) {
            return 0;
        }
        int reconciled = 0;
        try (var stream = Files.list(runsDir)) {
            for (var runDir : stream.sorted().toList()) {
                var runJson = runDir.resolve("run.json");
                if (Files.isRegularFile(runJson)
                        && matchesNode(runJson, nodeId)
                        && reconcile(runJson).reconciled()) {
                    reconciled++;
                }
            }
        }
        return reconciled;
    }

    private boolean matchesNode(Path runJson, String nodeId) throws IOException {
        if (nodeId == null || nodeId.isBlank()) {
            return true;
        }
        var json = OBJECT_MAPPER.readTree(runJson.toFile());
        return nodeId.equals(json.path("nodeId").asText(""));
    }

    private static boolean hasTerminalOutcome(ObjectNode json) {
        var outcome = json.path("executionOutcome");
        return outcome.isObject() && !outcome.path("status").asText("").isBlank();
    }

    private Optional<Classification> classify(ObjectNode json) {
        var now = Instant.now();
        var pid = readPid(json);
        if (pid > 0 && !ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false)) {
            return Optional.of(new Classification(
                    ProcessResult.interruptedProcess(),
                    AgentExecutionTimelineEventKind.PROCESS_INTERRUPTED,
                    "process_interrupted"));
        }
        var startedAt = readStartedAt(json);
        if (startedAt != null && Duration.between(startedAt, now).compareTo(staleTimeout) > 0) {
            return Optional.of(new Classification(
                    ProcessResult.timedOutProcess(),
                    AgentExecutionTimelineEventKind.ATTEMPT_RECONCILED,
                    "stale_running_attempt"));
        }
        return Optional.empty();
    }

    private static long readPid(ObjectNode json) {
        var processResult = json.path("processResult");
        if (processResult.isObject() && processResult.path("pid").isNumber()) {
            return processResult.path("pid").asLong();
        }
        var timeline = json.path("executionTimeline");
        if (timeline.isArray()) {
            for (var event : timeline) {
                if ("process_started".equals(event.path("kind").asText(""))) {
                    var pid = event.path("details").path("pid");
                    if (pid.isNumber()) {
                        return pid.asLong();
                    }
                }
            }
        }
        return 0L;
    }

    private static Instant readStartedAt(ObjectNode json) {
        var attemptStarted = json.path("executionAttempt").path("startedAt").asText("");
        if (!attemptStarted.isBlank()) {
            return parseInstant(attemptStarted);
        }
        return parseInstant(json.path("startedAt").asText(""));
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private AgentExecutionAttempt baseAttempt(ObjectNode json) {
        var attempt = json.path("executionAttempt");
        var runId = firstNonBlank(
                json.path("runId").asText(""),
                attempt.path("runId").asText(""),
                "run-unknown");
        return new AgentExecutionAttempt(
                firstNonBlank(attempt.path("attemptId").asText(""), "att-" + runId),
                runId,
                firstNonBlank(json.path("workstreamId").asText(""), attempt.path("workstreamId").asText("")),
                firstNonBlank(json.path("nodeId").asText(""), attempt.path("nodeId").asText("")),
                firstNonBlank(json.path("agent").asText(""), attempt.path("agent").asText("")),
                enumValue(AgentExecutionAttemptStatus.class, attempt.path("status").asText(""),
                        AgentExecutionAttemptStatus.RUNNING),
                enumValue(AgentExecutionProcessStatus.class, attempt.path("processStatus").asText(""),
                        AgentExecutionProcessStatus.RUNNING),
                null,
                false,
                false,
                firstNonBlank(attempt.path("message").asText(""), "agent execution reconciled"),
                firstNonBlank(attempt.path("stderrPath").asText(""), json.path("stderrPath").asText("")),
                firstNonBlank(attempt.path("transcriptPath").asText(""), json.path("stdoutPath").asText("")),
                attempt.path("sessionName").asText(""),
                attempt.path("sessionId").asText(""),
                attempt.path("sessionScope").asText(""),
                attempt.path("sessionSegmentKey").asText(""),
                readStartedAt(json),
                null
        );
    }

    private void writeReconciled(
            Path runJson,
            ObjectNode json,
            FinalizedAgentExecution finalized,
            Classification classification
    ) throws IOException {
        json.put("schemaVersion", Math.max(json.path("schemaVersion").asInt(0), 3));
        json.put("status", finalized.legacyStatus());
        if (finalized.legacyFailureReason() != null) {
            json.put("failureReason", finalized.legacyFailureReason());
        }
        json.put("recoverable", finalized.legacyRecoverable());
        json.set("executionAttempt", OBJECT_MAPPER.valueToTree(attemptToMap(finalized.attempt())));
        json.set("executionOutcome", OBJECT_MAPPER.valueToTree(outcomeToMap(finalized.outcome())));
        appendTimelineEvent(json, classification);
        writeJsonAtomically(runJson, json);
    }

    private static void writeJsonAtomically(Path target, ObjectNode json) throws IOException {
        var directory = target.getParent();
        Files.createDirectories(directory);
        var tmp = Files.createTempFile(directory, target.getFileName().toString(), ".tmp");
        try {
            OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), json);
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException atomicFailure) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private static Map<String, Object> attemptToMap(AgentExecutionAttempt attempt) {
        var map = new LinkedHashMap<String, Object>();
        map.put("attemptId", attempt.attemptId());
        map.put("runId", attempt.runId());
        map.put("workstreamId", attempt.workstreamId());
        map.put("nodeId", attempt.nodeId());
        map.put("agent", attempt.agent());
        map.put("status", attempt.status().name());
        map.put("processStatus", attempt.processStatus().name());
        map.put("failureSignal", attempt.failureSignal() == null ? null : attempt.failureSignal().name());
        map.put("recoverable", attempt.recoverable());
        map.put("retrySafe", attempt.retrySafe());
        map.put("message", attempt.message());
        map.put("stderrPath", attempt.stderrPath());
        map.put("transcriptPath", attempt.transcriptPath());
        map.put("sessionName", attempt.sessionName());
        map.put("sessionId", attempt.sessionId());
        map.put("sessionScope", attempt.sessionScope());
        map.put("sessionSegmentKey", attempt.sessionSegmentKey());
        map.put("startedAt", attempt.startedAt() == null ? null : attempt.startedAt().toString());
        map.put("finishedAt", attempt.finishedAt() == null ? null : attempt.finishedAt().toString());
        return map;
    }

    private static Map<String, Object> outcomeToMap(AgentExecutionOutcome outcome) {
        var map = new LinkedHashMap<String, Object>();
        map.put("status", outcome.status().name());
        map.put("primaryReason", outcome.primaryReason());
        map.put("nodeMaySucceed", outcome.nodeMaySucceed());
        map.put("retryPolicy", outcome.retryPolicy().name());
        map.put("retrySafe", outcome.retryPolicy() != AgentExecutionRetryPolicy.NONE);
        map.put("recoveryCategory", outcome.recoveryCategory().name());
        map.put("operatorMessage", outcome.operatorMessage());
        map.put("secondarySignals", outcome.secondarySignals().stream()
                .map(AgentExecutionAttemptReconciler::signalToMap)
                .toList());
        return map;
    }

    private static Map<String, Object> signalToMap(AdapterSignal signal) {
        var map = new LinkedHashMap<String, Object>();
        map.put("type", signal.type().name());
        map.put("severity", signal.type().severity().name());
        map.put("message", signal.message());
        map.put("observedSequence", signal.observedSequence());
        map.put("source", signal.source());
        map.put("sessionId", signal.sessionId());
        map.put("superseded", signal.superseded());
        return map;
    }

    private void appendTimelineEvent(ObjectNode json, Classification classification) {
        var timeline = new ArrayList<Map<String, Object>>();
        var existing = json.get("executionTimeline");
        if (existing != null && existing.isArray()) {
            for (JsonNode item : existing) {
                timeline.add(OBJECT_MAPPER.convertValue(item, Map.class));
            }
        }
        var startedAt = readStartedAt(json);
        var observedAt = Instant.now();
        var details = new LinkedHashMap<String, Object>();
        details.put("reason", classification.reason());
        details.put("reconciled", true);
        timeline.add(Map.of(
                "kind", classification.timelineKind().wireName(),
                "source", "runtime",
                "sequence", (long) timeline.size() + 1L,
                "observedAt", observedAt.toString(),
                "elapsedMs", startedAt == null ? 0L : Math.max(0L, Duration.between(startedAt, observedAt).toMillis()),
                "details", details
        ));
        json.set("executionTimeline", OBJECT_MAPPER.valueToTree(timeline));
    }

    private static String firstNonBlank(String... values) {
        for (var value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value, E fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, value);
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private record Classification(
            ProcessResult process,
            AgentExecutionTimelineEventKind timelineKind,
            String reason
    ) {
    }

    public record Reconciliation(boolean reconciled, AgentExecutionOutcome outcome) {
        static Reconciliation absent() {
            return new Reconciliation(false, null);
        }
    }
}
