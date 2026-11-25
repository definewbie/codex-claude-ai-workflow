package dev.dual.runtime.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.dual.core.execution.AdapterSignal;
import dev.dual.core.execution.AdapterSignalType;
import dev.dual.core.execution.AgentExecutionAttempt;
import dev.dual.core.execution.AgentExecutionAttemptStatus;
import dev.dual.core.execution.AgentExecutionEvidence;
import dev.dual.core.execution.AgentExecutionFinalizer;
import dev.dual.core.execution.AgentExecutionOutcome;
import dev.dual.core.execution.AgentExecutionOutcomeResolver;
import dev.dual.core.execution.AgentExecutionProcessStatus;
import dev.dual.core.execution.AgentExecutionRetryPolicy;
import dev.dual.core.execution.AgentExecutionTimelineEventKind;
import dev.dual.core.execution.ArtifactContractResult;
import dev.dual.core.execution.ProcessResult;
import dev.dual.core.execution.ProcessUsability;
import dev.dual.core.execution.AgentResultContractResult;
import dev.dual.core.workflow.NodeResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Owns the run.json execution finalization projection.
 *
 * <p>Artifact validators provide result/artifact facts. This class is the
 * single runtime writer that combines those facts with process evidence and
 * persists terminal attempt/outcome fields into the agent run read model.</p>
 */
public final class AgentRunMetadataFinalizer {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final AgentExecutionFinalizer executionFinalizer;

    public AgentRunMetadataFinalizer() {
        this(new AgentExecutionFinalizer(new AgentExecutionOutcomeResolver()));
    }

    AgentRunMetadataFinalizer(AgentExecutionFinalizer executionFinalizer) {
        if (executionFinalizer == null) {
            throw new IllegalArgumentException("executionFinalizer is required");
        }
        this.executionFinalizer = executionFinalizer;
    }

    public NodeResult finalizeNodeResult(Path workspaceRoot, NodeResult result) {
        return resolveNodeResult(workspaceRoot, result, true);
    }

    /** Read the same execution facts before publication, without writing a terminal run projection. */
    NodeResult assessPublication(Path workspaceRoot, NodeResult result) {
        return resolveNodeResult(workspaceRoot, result, false);
    }

    private NodeResult resolveNodeResult(Path workspaceRoot, NodeResult result, boolean persist) {
        if (AgentExecutionResultFacts.inputPreparationFailed(result)) return result;
        if (!AgentExecutionResultFacts.present(result)) {
            return result;
        }
        var outcome = finalizeRunMetadata(
                workspaceRoot,
                result,
                AgentExecutionResultFacts.agentResultContract(result),
                AgentExecutionResultFacts.artifactContractResults(result),
                AgentExecutionResultFacts.rereadReasons(result), persist
        );
        var finalized = new LinkedHashMap<String, Object>();
        finalized.putAll(result.result());
        finalized.put("agent_execution_outcome", outcomeToMap(outcome));
        return new NodeResult(
                outcome.nodeMaySucceed() ? result.outcome() : outcome.primaryReason(),
                outcome.nodeMaySucceed() ? result.status() : dev.dual.core.workflow.NodeStatus.FAILED,
                result.outputRefs(),
                finalized,
                outcome.nodeMaySucceed() ? result.errorMessage() : outcome.operatorMessage()
        );
    }

    AgentExecutionOutcome finalizeRunMetadata(
            Path workspaceRoot,
            NodeResult result,
            AgentResultContractResult agentResultContract,
            List<ArtifactContractResult> artifactContractResults,
            Object rereadReasons
    ) {
        return finalizeRunMetadata(workspaceRoot, result, agentResultContract, artifactContractResults, rereadReasons, true);
    }

    private AgentExecutionOutcome finalizeRunMetadata(
            Path workspaceRoot, NodeResult result, AgentResultContractResult agentResultContract,
            List<ArtifactContractResult> artifactContractResults, Object rereadReasons, boolean persist
    ) {
        var runMetadataRef = text(result.result().get("run_metadata"));
        if (runMetadataRef.isBlank()) throw new IllegalStateException("typed execution requires program-owned run metadata");
        var root = workspaceRoot.toAbsolutePath().normalize();
        var path = root.resolve(runMetadataRef).normalize();
        if (!path.startsWith(root)) throw new IllegalStateException("run metadata escapes workspace");
        try {
            var recorded = AgentRunCommitProtocol.recordedOutcome(workspaceRoot, path, result);
            if (recorded != null) return recorded;
            if (!Files.isRegularFile(path)) throw new java.io.IOException("typed run metadata is missing");
            var json = (ObjectNode) OBJECT_MAPPER.readTree(path.toFile());
            var process = processResultFromRunMetadata(json);
            if (process == null) throw new java.io.IOException("typed process evidence is missing; no legacy success inference");
            var signals = json.path("adapterSignals").isMissingNode()
                    ? resultAdapterSignals(result)
                    : adapterSignalsFromRunMetadata(json);
            var evidence = new AgentExecutionEvidence(
                    process,
                    agentResultContract,
                    artifactContractResults,
                    signals, permissionAssessments(result)
            );
            var processResult = mergedProcessResult(json, process);
            var finalized = executionFinalizer.finalize(baseAttempt(json, result), evidence);
            if (!persist) return finalized.outcome();
            json.put("schemaVersion", Math.max(json.path("schemaVersion").asInt(0), 3));
            json.put("status", finalized.legacyStatus());
            json.put("failureReason", finalized.legacyFailureReason());
            json.put("recoverable", finalized.legacyRecoverable());
            json.set("processResult", OBJECT_MAPPER.valueToTree(processResult));
            json.set("adapterSignals", OBJECT_MAPPER.valueToTree(signals.stream()
                    .map(AgentRunMetadataFinalizer::adapterSignalToMap)
                    .toList()));
            json.set("permissionAssessments", OBJECT_MAPPER.valueToTree(evidence.permissionAssessments()));
            json.set("agentResultContract", OBJECT_MAPPER.valueToTree(
                    AgentExecutionResultFacts.agentResultContractToMap(agentResultContract)));
            json.set("artifactContractResults", OBJECT_MAPPER.valueToTree(artifactContractResults.stream()
                    .map(AgentExecutionResultFacts::artifactContractToMap)
                    .toList()));
            if (rereadReasons != null) {
                json.set("reread_reasons", OBJECT_MAPPER.valueToTree(rereadReasons));
            }
            appendTimelineEvent(json, AgentExecutionTimelineEventKind.CONTRACT_VALIDATED);
            json.set("executionAttempt", OBJECT_MAPPER.valueToTree(executionAttemptToMap(finalized.attempt())));
            json.set("executionOutcome", OBJECT_MAPPER.valueToTree(outcomeToMap(finalized.outcome())));
            AgentRunCommitProtocol.recordOutcome(workspaceRoot, path, result, finalized.outcome(), json);
            new dev.dual.ledger.file.AtomicJsonWriter().write(path, json);
            return finalized.outcome();
        } catch (Exception failure) {
            throw new IllegalStateException("failed to finalize typed agent run metadata", failure);
        }
    }

    static List<AdapterSignal> resultAdapterSignals(NodeResult result) {
        if (result.result().containsKey("adapter_signals")) {
            return adapterSignalsFromValue(result.result().get("adapter_signals"));
        }
        return legacyAdapterSignals(result);
    }

    private static List<AgentExecutionEvidence.PermissionAssessment> permissionAssessments(NodeResult result) {
        var raw = result.result().get(PermissionEvidence.FACT);
        if (raw == null) return List.of();
        if (!(raw instanceof List<?> items)) throw new IllegalArgumentException("permission assessments must be an array");
        return items.stream().map(item -> OBJECT_MAPPER.convertValue(item, AgentExecutionEvidence.PermissionAssessment.class)).toList();
    }

    private static List<AdapterSignal> legacyAdapterSignals(NodeResult result) {
        var reason = text(result.result().get("acpx_failure.reason"));
        if (reason.isBlank()) {
            return List.of();
        }
        var type = switch (reason) {
            case "RECOVERABLE_AGENT_RECONNECT" -> AdapterSignalType.AGENT_RECONNECT_REQUIRED;
            case "RECOVERABLE_AGENT_MODEL_MISMATCH" -> AdapterSignalType.MODEL_MISMATCH;
            case "RECOVERABLE_SESSION_BOOTSTRAP_FAILED" -> AdapterSignalType.SESSION_BOOTSTRAP_FAILED;
            case "RECOVERABLE_SESSION_MISSING" -> AdapterSignalType.SESSION_RESUME_FAILED;
            case "RECOVERABLE_AGENT_AUTH_FAILED" -> AdapterSignalType.AUTH_FAILED;
            case "RECOVERABLE_PROVIDER_CONNECTION_FAILED" -> AdapterSignalType.PROVIDER_CONNECTION_FAILED;
            case "RECOVERABLE_RATE_LIMIT", "RECOVERABLE_AGENT_OVERLOADED" -> AdapterSignalType.RATE_LIMITED;
            case "RECOVERABLE_CONTEXT_WINDOW" -> AdapterSignalType.CONTEXT_WINDOW_EXCEEDED;
            case "RECOVERABLE_AGENT_PERMISSION_BLOCKED" -> AdapterSignalType.PERMISSION_BLOCKED;
            case "RECOVERABLE_AGENT_OUTPUT_INTERRUPTED" -> AdapterSignalType.PROCESS_STALLED;
            case "RECOVERABLE_TRANSPORT_FAILED" -> AdapterSignalType.PROCESS_STALLED;
            default -> null;
        };
        return type == null ? List.of() : List.of(new AdapterSignal(type, reason, 0));
    }

    static List<AdapterSignal> adapterSignalsFromRunMetadata(ObjectNode json) {
        var existing = json.path("adapterSignals");
        if (!existing.isArray()) {
            throw new IllegalArgumentException("typed adapterSignals must be an array");
        }
        var signals = new ArrayList<AdapterSignal>();
        for (var item : existing) {
            if (!item.isObject()) {
                throw new IllegalArgumentException("typed adapterSignals entries must be objects");
            }
            var type = enumValue(AdapterSignalType.class, item.path("type").asText(""), null);
            if (type == null) {
                throw new IllegalArgumentException("typed adapterSignals.type is missing or invalid");
            }
            signals.add(new AdapterSignal(
                    type,
                    item.path("message").asText(""),
                    item.path("observedSequence").isNumber() ? Math.max(0L, item.path("observedSequence").asLong()) : 0L,
                    item.path("source").asText(""),
                    item.path("sessionId").asText(""),
                    item.path("superseded").isBoolean() && item.path("superseded").asBoolean(),
                    item.hasNonNull("operationRef") ? OBJECT_MAPPER.convertValue(item.get("operationRef"), AdapterSignal.OperationRef.class) : null
            ));
        }
        return List.copyOf(signals);
    }

    private static List<AdapterSignal> adapterSignalsFromValue(Object value) {
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException("typed adapter_signals must be a list");
        }
        var signals = new ArrayList<AdapterSignal>();
        for (var item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                throw new IllegalArgumentException("typed adapter_signals entries must be maps");
            }
            var type = enumValue(AdapterSignalType.class, text(map.get("type")), null);
            if (type == null) {
                throw new IllegalArgumentException("typed adapter_signals.type is missing or invalid");
            }
            var sequence = map.get("observedSequence") instanceof Number number
                    ? Math.max(0L, number.longValue())
                    : 0L;
            signals.add(new AdapterSignal(
                    type,
                    text(map.get("message")),
                    sequence,
                    text(map.get("source")),
                    text(map.get("sessionId")),
                    Boolean.TRUE.equals(map.get("superseded")),
                    map.get("operationRef") == null ? null : OBJECT_MAPPER.convertValue(map.get("operationRef"), AdapterSignal.OperationRef.class)
            ));
        }
        return List.copyOf(signals);
    }

    static Map<String, Object> outcomeToMap(AgentExecutionOutcome outcome) {
        var map = new LinkedHashMap<String, Object>();
        map.put("status", outcome.status().name());
        map.put("primaryReason", outcome.primaryReason());
        map.put("nodeMaySucceed", outcome.nodeMaySucceed());
        map.put("retryPolicy", outcome.retryPolicy().name());
        map.put("recoveryCategory", outcome.recoveryCategory().name());
        map.put("operatorMessage", outcome.operatorMessage());
        map.put("secondarySignals", outcome.secondarySignals().stream()
                .map(AgentRunMetadataFinalizer::signalToMap)
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
            if (signal.operationRef() != null) map.put("operationRef", signal.operationRef());
        return map;
    }

    private static Map<String, Object> processResultToMap(ProcessResult process) {
        var map = new LinkedHashMap<String, Object>();
        map.put("command", process.command());
        map.put("pid", process.pid());
        map.put("exitCode", process.exitCode());
        map.put("usability", process.usability().name());
        map.put("durationMs", process.durationMs());
        map.put("stdoutPath", process.stdoutPath());
        map.put("stderrPath", process.stderrPath());
        map.put("interrupted", process.interrupted());
        map.put("timedOut", process.timedOut());
        return map;
    }

    private static AgentExecutionAttempt baseAttempt(ObjectNode json, NodeResult result) {
        var attempt = json.path("executionAttempt");
        var runId = firstNonBlank(json.path("runId").asText(""), text(result.result().get("run_id")), "run-unknown");
        return new AgentExecutionAttempt(
                firstNonBlank(attempt.path("attemptId").asText(""), "att-" + runId),
                runId,
                firstNonBlank(json.path("workstreamId").asText(""), attempt.path("workstreamId").asText("")),
                firstNonBlank(json.path("nodeId").asText(""), attempt.path("nodeId").asText("")),
                firstNonBlank(json.path("agent").asText(""), attempt.path("agent").asText(""), text(result.result().get("agent"))),
                AgentExecutionAttemptStatus.RUNNING,
                AgentExecutionProcessStatus.RUNNING,
                null,
                false,
                false,
                firstNonBlank(attempt.path("message").asText(""), "process exited"),
                attempt.path("stderrPath").asText(""),
                attempt.path("transcriptPath").asText(""),
                attempt.path("sessionName").asText(""),
                attempt.path("sessionId").asText(""),
                attempt.path("sessionScope").asText(""),
                attempt.path("sessionSegmentKey").asText(""),
                firstInstant(attempt.path("startedAt").asText(""), json.path("startedAt").asText("")),
                firstInstant(attempt.path("finishedAt").asText(""), json.path("finishedAt").asText(""))
        );
    }

    private static Map<String, Object> executionAttemptToMap(AgentExecutionAttempt attempt) {
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

    private static Map<String, Object> mergedProcessResult(ObjectNode json, ProcessResult process) {
        var merged = new LinkedHashMap<String, Object>();
        var existing = json.path("processResult");
        if (existing.isObject()) {
            existing.fields().forEachRemaining(entry -> merged.put(entry.getKey(), OBJECT_MAPPER.convertValue(entry.getValue(), Object.class)));
        }
        var semantic = processResultToMap(process);
        for (var entry : semantic.entrySet()) {
            if (entry.getValue() != null && !String.valueOf(entry.getValue()).isBlank()) {
                merged.put(entry.getKey(), entry.getValue());
            }
        }
        return merged;
    }

    static ProcessResult processResultFromRunMetadata(ObjectNode json) {
        var existing = json.path("processResult");
        if (existing.isMissingNode()) {
            return null;
        }
        if (!existing.isObject()) {
            throw new IllegalArgumentException("typed processResult must be an object");
        }
        var usability = enumValue(ProcessUsability.class, existing.path("usability").asText(""), null);
        if (usability == null) {
            throw new IllegalArgumentException("typed processResult.usability is missing or invalid");
        }
        Long pid = existing.path("pid").isNumber() ? existing.path("pid").asLong() : null;
        Integer exitCode = existing.path("exitCode").isNumber() ? existing.path("exitCode").asInt() : null;
        return new ProcessResult(
                existing.path("command").asText(""),
                pid,
                exitCode,
                usability,
                existing.path("durationMs").isNumber() ? Math.max(0L, existing.path("durationMs").asLong()) : 0L,
                existing.path("stdoutPath").asText(""),
                existing.path("stderrPath").asText(""),
                existing.path("interrupted").asBoolean(false),
                existing.path("timedOut").asBoolean(false)
        );
    }

    private static Map<String, Object> adapterSignalToMap(AdapterSignal signal) {
        var map = new LinkedHashMap<String, Object>();
        map.put("type", signal.type().name());
        map.put("severity", signal.type().severity().name());
        map.put("source", signal.source());
        map.put("message", signal.message());
        map.put("sessionId", signal.sessionId());
        map.put("observedSequence", signal.observedSequence());
        map.put("superseded", signal.superseded());
            if (signal.operationRef() != null) map.put("operationRef", signal.operationRef());
        return map;
    }

    private static Instant instantOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static Instant firstInstant(String first, String second) {
        var parsed = instantOrNull(first);
        return parsed == null ? instantOrNull(second) : parsed;
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

    private static int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static String firstNonBlank(String first, String second) {
        return firstNonBlank(new String[]{first, second});
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (var value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static void appendTimelineEvent(ObjectNode json, AgentExecutionTimelineEventKind kind) {
        var timeline = new ArrayList<Map<String, Object>>();
        var existing = json.get("executionTimeline");
        if (existing != null && existing.isArray()) {
            for (var item : existing) {
                timeline.add(OBJECT_MAPPER.convertValue(item, MAP_TYPE));
            }
        }
        var startedAt = instantOrNull(text(json.get("startedAt")));
        var observedAt = Instant.now();
        timeline.add(Map.of(
                "kind", kind.wireName(),
                "source", "runtime",
                "sequence", timeline.size() + 1L,
                "observedAt", observedAt.toString(),
                "elapsedMs", startedAt == null ? 0L : Math.max(0L, Duration.between(startedAt, observedAt).toMillis())
        ));
        json.set("executionTimeline", OBJECT_MAPPER.valueToTree(timeline));
    }
}
