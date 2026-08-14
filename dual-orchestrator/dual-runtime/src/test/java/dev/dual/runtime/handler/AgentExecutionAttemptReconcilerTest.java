package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.dual.core.execution.AgentExecutionOutcome;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class AgentExecutionAttemptReconcilerTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    private Path writeInFlightRun(String runId, Long pid, Instant startedAt) throws Exception {
        return writeInFlightRun(runId, pid, startedAt, null);
    }

    private Path writeInFlightRun(String runId, Long pid, Instant startedAt, ObjectNode terminalOutcome) throws Exception {
        var runJson = tempDir.resolve("runs/" + runId + "/run.json");
        Files.createDirectories(runJson.getParent());
        var node = MAPPER.createObjectNode();
        node.put("schemaVersion", 3);
        node.put("status", terminalOutcome == null ? "prepared" : "failed");
        node.put("runId", runId);
        node.put("workstreamId", "ws-test");
        node.put("nodeId", "design.claude_design");
        node.put("agent", "claude");
        node.put("promptPath", ".agent/workstreams/ws-test/runs/" + runId + "/prompts/design-claude.md");
        node.put("stdoutPath", ".agent/workstreams/ws-test/runs/" + runId + "/outputs/design-claude.md");
        node.put("stderrPath", ".agent/workstreams/ws-test/runs/" + runId + "/outputs/design-claude.stderr.log");
        node.put("startedAt", startedAt.toString());
        var attempt = MAPPER.createObjectNode();
        attempt.put("attemptId", "att-" + runId);
        attempt.put("runId", runId);
        attempt.put("workstreamId", "ws-test");
        attempt.put("nodeId", "design.claude_design");
        attempt.put("agent", "claude");
        attempt.put("status", "RUNNING");
        attempt.put("processStatus", "RUNNING");
        attempt.put("startedAt", startedAt.toString());
        node.set("executionAttempt", attempt);
        var processResult = MAPPER.createObjectNode();
        if (pid != null) {
            processResult.put("pid", pid);
        }
        node.set("processResult", processResult);
        if (terminalOutcome != null) {
            node.set("executionOutcome", terminalOutcome);
        }
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(runJson.toFile(), node);
        return runJson;
    }

    /**
     * Realistic in-flight fixture: the exact state left by {@code writePrepared} +
     * a {@code process_started} timeline event (pid persisted by the transport's
     * event sink) when {@code writeCompleted} never ran. No top-level
     * {@code processResult}, no occurrence, no usage, no outcome — a bare
     * "prepared" with only a non-terminal attempt and a pid in the timeline.
     */
    private Path writeStartedInFlightRun(String runId, long pid, Instant startedAt) throws Exception {
        var runJson = tempDir.resolve("runs/" + runId + "/run.json");
        Files.createDirectories(runJson.getParent());
        var node = MAPPER.createObjectNode();
        node.put("schemaVersion", 3);
        node.put("status", "prepared");
        node.put("runId", runId);
        node.put("workstreamId", "ws-test");
        node.put("nodeId", "intake.claude_brainstorm");
        node.put("agent", "claude");
        node.put("promptPath", ".agent/workstreams/ws-test/runs/" + runId + "/prompts/intake-claude.md");
        node.put("stdoutPath", ".agent/workstreams/ws-test/runs/" + runId + "/outputs/intake-claude.md");
        node.put("stderrPath", ".agent/workstreams/ws-test/runs/" + runId + "/outputs/intake-claude.stderr.log");
        node.put("transcriptPath", ".agent/workstreams/ws-test/runs/" + runId + "/outputs/intake-claude.transcript.log");
        var attempt = MAPPER.createObjectNode();
        attempt.put("attemptId", "att-" + runId);
        attempt.put("runId", runId);
        attempt.put("workstreamId", "ws-test");
        attempt.put("nodeId", "intake.claude_brainstorm");
        attempt.put("agent", "claude");
        attempt.put("status", "RUNNING");
        attempt.put("processStatus", "RUNNING");
        attempt.put("startedAt", startedAt.toString());
        attempt.put("transcriptPath", ".agent/workstreams/ws-test/runs/" + runId + "/outputs/intake-claude.transcript.log");
        node.set("executionAttempt", attempt);
        var timeline = MAPPER.createArrayNode();
        var processStarted = MAPPER.createObjectNode();
        processStarted.put("kind", "process_started");
        processStarted.put("source", "adapter");
        processStarted.put("sequence", 1);
        processStarted.put("observedAt", startedAt.toString());
        processStarted.put("elapsedMs", 0);
        var details = MAPPER.createObjectNode();
        details.put("pid", pid);
        processStarted.set("details", details);
        timeline.add(processStarted);
        node.set("executionTimeline", timeline);
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(runJson.toFile(), node);
        return runJson;
    }

    @Test
    void preparedRunWithDeadPidReconcilesToInterruptedOutcome() throws Exception {
        var runJson = writeInFlightRun("run-dead-pid", 99_999_999L, Instant.now().minusSeconds(30));

        var result = new AgentExecutionAttemptReconciler(Duration.ofMinutes(10)).reconcile(runJson);

        assertTrue(result.reconciled(), "a prepared run with a dead pid must be reconciled");
        assertEquals("INTERRUPTED", result.outcome().status().name());
        var node = (ObjectNode) MAPPER.readTree(runJson.toFile());
        assertEquals("failed", node.path("status").asText());
        assertEquals("INTERRUPTED", node.path("executionAttempt").path("status").asText());
        assertEquals("INTERRUPTED", node.path("executionOutcome").path("status").asText());
        assertFalse(node.path("executionOutcome").path("primaryReason").asText().isBlank(),
                "reconciled outcome must carry a typed primaryReason");
    }

    @Test
    void completedMetadataWithoutOutcomeReplaysDurablePermissionFailure() throws Exception {
        var runJson = tempDir.resolve("runs/run-finalizer-gap/run.json");
        Files.createDirectories(runJson.getParent());
        Files.writeString(runJson, """
                {
                  "schemaVersion": 3,
                  "status": "process_exited",
                  "runId": "run-finalizer-gap",
                  "workstreamId": "ws-test",
                  "nodeId": "implement.claude_implement",
                  "agent": "claude",
                  "startedAt": "2026-08-14T01:33:27Z",
                  "finishedAt": "2026-08-14T01:43:32Z",
                  "executionAttempt": {
                    "attemptId": "att-run-finalizer-gap",
                    "status": "INTERRUPTED",
                    "processStatus": "INTERRUPTED",
                    "failureSignal": "PERMISSION_BLOCKED",
                    "recoverable": true,
                    "startedAt": "2026-08-14T01:33:27Z",
                    "finishedAt": "2026-08-14T01:43:32Z"
                  },
                  "processResult": {
                    "pid": 99999999,
                    "exitCode": 5,
                    "usability": "INTERRUPTED",
                    "durationMs": 605000,
                    "interrupted": true,
                    "timedOut": false
                  },
                  "adapterSignals": [ {
                    "type": "PERMISSION_BLOCKED",
                    "message": "acpx exited with PERMISSION_DENIED (5)",
                    "observedSequence": 44256,
                    "source": "acpx_exit",
                    "sessionId": "claude-main-ws-test",
                    "superseded": false
                  } ]
                }
                """);

        var result = new AgentExecutionAttemptReconciler(Duration.ofMinutes(10)).reconcile(runJson);

        assertTrue(result.reconciled());
        assertEquals("RECOVERABLE_ADAPTER_FAILURE", result.outcome().status().name());
        assertEquals("permission_blocked", result.outcome().primaryReason());
        var node = (ObjectNode) MAPPER.readTree(runJson.toFile());
        assertEquals("INTERRUPTED", node.path("executionAttempt").path("status").asText());
        assertEquals("PERMISSION_BLOCKED", node.path("executionAttempt").path("failureSignal").asText());
        assertEquals("PERMISSION_BLOCKED",
                node.path("executionOutcome").path("secondarySignals").path(0).path("type").asText());
    }

    @Test
    void preparedRunExceedingStaleTimeoutReconcilesToTimeout() throws Exception {
        var runJson = writeInFlightRun("run-timeout", null, Instant.now().minus(Duration.ofHours(1)));

        var result = new AgentExecutionAttemptReconciler(Duration.ofMinutes(1)).reconcile(runJson);

        assertTrue(result.reconciled());
        assertEquals("TIMEOUT", result.outcome().status().name());
        var node = (ObjectNode) MAPPER.readTree(runJson.toFile());
        assertEquals("failed", node.path("status").asText());
        assertEquals("TIMEOUT", node.path("executionOutcome").path("status").asText());
    }

    @Test
    void timeoutReconcileRecordsConfiguredTimeoutMsInTimeline() throws Exception {
        var runJson = writeInFlightRun("run-timeout-ms", null, Instant.now().minus(Duration.ofHours(1)));

        var result = new AgentExecutionAttemptReconciler(Duration.ofMinutes(8)).reconcile(runJson);

        assertEquals("TIMEOUT", result.outcome().status().name());
        var node = (ObjectNode) MAPPER.readTree(runJson.toFile());
        long timeoutMs = -1L;
        var reason = "";
        for (var item : node.path("executionTimeline")) {
            if ("attempt_reconciled".equals(item.path("kind").asText(""))) {
                reason = item.path("details").path("reason").asText("");
                timeoutMs = item.path("details").path("timeoutMs").asLong(-1L);
                break;
            }
        }
        assertEquals("stale_running_attempt", reason, "expected an attempt_reconciled timeline event");
        assertEquals(8L * 60_000L, timeoutMs,
                "timeout reconcile must record the configured timeoutMs so the timeout is explainable later");
    }

    @Test
    void deadPidWithinTimeoutReconcilesImmediatelyToInterrupted() throws Exception {
        // startedAt well within the timeout, but the pid is dead: INTERRUPTED immediately, not TIMEOUT.
        var runJson = writeInFlightRun("run-dead-within", 99_999_994L, Instant.now().minusSeconds(10));

        var result = new AgentExecutionAttemptReconciler(Duration.ofMinutes(8)).reconcile(runJson);

        assertTrue(result.reconciled());
        assertEquals("INTERRUPTED", result.outcome().status().name(),
                "a dead pid must reconcile to INTERRUPTED regardless of the timeout");
        var node = (ObjectNode) MAPPER.readTree(runJson.toFile());
        var reason = "";
        for (var item : node.path("executionTimeline")) {
            if ("process_interrupted".equals(item.path("kind").asText(""))) {
                reason = item.path("details").path("reason").asText("");
                break;
            }
        }
        assertEquals("process_interrupted", reason);
    }

    @Test
    void completedRunIsNotReconciled() throws Exception {
        var outcome = MAPPER.createObjectNode();
        outcome.put("status", "SUCCESS");
        outcome.put("primaryReason", "completed");
        var runJson = writeInFlightRun("run-completed", 99_999_998L, Instant.now().minusSeconds(10), outcome);

        var result = new AgentExecutionAttemptReconciler(Duration.ofMinutes(10)).reconcile(runJson);

        assertFalse(result.reconciled(), "a run that already has a terminal outcome must not be overwritten");
        var node = (ObjectNode) MAPPER.readTree(runJson.toFile());
        assertEquals("SUCCESS", node.path("executionOutcome").path("status").asText());
    }

    @Test
    void existingAdapterFailureIsNotReconciled() throws Exception {
        var outcome = MAPPER.createObjectNode();
        outcome.put("status", "RECOVERABLE_ADAPTER_FAILURE");
        outcome.put("primaryReason", "process_stalled");
        var runJson = writeInFlightRun("run-adapter-failure", 99_999_997L, Instant.now().minusSeconds(10), outcome);

        var result = new AgentExecutionAttemptReconciler(Duration.ofMinutes(10)).reconcile(runJson);

        assertFalse(result.reconciled(), "an existing typed adapter failure must not be overwritten");
        var node = (ObjectNode) MAPPER.readTree(runJson.toFile());
        assertEquals("RECOVERABLE_ADAPTER_FAILURE", node.path("executionOutcome").path("status").asText());
    }

    @Test
    void reconcilePreservesPromptTranscriptStderrRefs() throws Exception {
        var runJson = writeInFlightRun("run-refs", 99_999_996L, Instant.now().minusSeconds(30));

        new AgentExecutionAttemptReconciler(Duration.ofMinutes(10)).reconcile(runJson);

        var node = (ObjectNode) MAPPER.readTree(runJson.toFile());
        assertEquals(".agent/workstreams/ws-test/runs/run-refs/prompts/design-claude.md",
                node.path("promptPath").asText());
        assertEquals(".agent/workstreams/ws-test/runs/run-refs/outputs/design-claude.md",
                node.path("stdoutPath").asText());
        assertEquals(".agent/workstreams/ws-test/runs/run-refs/outputs/design-claude.stderr.log",
                node.path("stderrPath").asText());
    }

    @Test
    void reconcileIsIdempotent() throws Exception {
        var runJson = writeInFlightRun("run-idempotent", 99_999_995L, Instant.now().minusSeconds(30));
        var reconciler = new AgentExecutionAttemptReconciler(Duration.ofMinutes(10));

        var first = reconciler.reconcile(runJson);
        var second = reconciler.reconcile(runJson);

        assertTrue(first.reconciled());
        assertFalse(second.reconciled(), "reconciling an already-terminal run must be a no-op");
    }

    @Test
    void liveRunWithinTimeoutIsNotReconciled() throws Exception {
        // No pid, recently started, within timeout: nothing proves it is stuck, so do not force-close it.
        var runJson = writeInFlightRun("run-live", null, Instant.now().minusSeconds(5));

        var result = new AgentExecutionAttemptReconciler(Duration.ofMinutes(10)).reconcile(runJson);

        assertFalse(result.reconciled(), "an in-flight run with no dead pid and within timeout must not be force-closed");
    }

    /** Cannot-bypass: a started process with no captured exit must close without inventing provider facts. */
    @Test
    void startedRunInterruptedBeforeFinalizerIsClosedWithAuthoritativeFacts() throws Exception {
        var deadPid = 99_999_990L; // not alive, so the closer proves the process ended
        var runJson = writeStartedInFlightRun("run-interrupted", deadPid, Instant.now().minusSeconds(30));

        var result = new AgentExecutionAttemptReconciler(Duration.ofMinutes(10)).reconcile(runJson);

        assertTrue(result.reconciled(), "a started run whose process is gone must be closed, not left prepared");
        var node = (ObjectNode) MAPPER.readTree(runJson.toFile());

        // run.json must not still masquerade as "prepared".
        assertNotEquals("prepared", node.path("status").asText(),
                "an interrupted started run must not be left at status=prepared");

        // terminal disposition is typed.
        var outcome = node.path("executionOutcome");
        assertNotEquals("", outcome.path("status").asText(), "terminal executionOutcome.status must be present");
        assertEquals("INTERRUPTED", outcome.path("status").asText(),
                "a dead-pid started run must close to INTERRUPTED");
        assertFalse(outcome.path("primaryReason").asText().isBlank(),
                "terminal disposition must carry a typed primaryReason");

        // process result carries the real pid and the strongest fact available: interrupted.
        var processResult = node.path("processResult");
        assertTrue(processResult.isObject(), "the closer must persist a processResult");
        assertEquals(deadPid, processResult.path("pid").asLong(),
                "processResult must carry the real pid of the started process");
        assertEquals("INTERRUPTED", processResult.path("usability").asText(),
                "processResult.usability must be typed INTERRUPTED");

        // typed signal: the terminal attempt carries a typed status/failureSignal.
        var attempt = node.path("executionAttempt");
        assertNotEquals("RUNNING", attempt.path("status").asText(),
                "executionAttempt must be advanced past RUNNING to a typed terminal status");
        assertEquals("INTERRUPTED", attempt.path("status").asText());

        assertTrue(node.path("agentResultAcquisition").isMissingNode(),
                "process_started does not prove a provider result occurrence");
        assertTrue(node.path("usage").isMissingNode(),
                "reconciliation must not synthesize provider usage");
    }

    @Test
    void closerDoesNotFabricateUsageForStaleTimeoutClose() throws Exception {
        // No dead pid, but well past the stale boundary: closed as TIMEOUT, still no fabricated usage.
        var runJson = writeStartedInFlightRun("run-stale-no-pid", 0L, Instant.now().minus(Duration.ofHours(1)));
        // remove the process_started timeline so there is no pid at all (only staleness proves it ended)
        var raw = (ObjectNode) MAPPER.readTree(runJson.toFile());
        raw.remove("executionTimeline");
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(runJson.toFile(), raw);

        var result = new AgentExecutionAttemptReconciler(Duration.ofMinutes(1)).reconcile(runJson);

        assertTrue(result.reconciled());
        var node = (ObjectNode) MAPPER.readTree(runJson.toFile());
        assertEquals("TIMEOUT", node.path("executionOutcome").path("status").asText());
        assertTrue(node.path("usage").isMissingNode(),
                "a timeout close must not create a zero-token provider observation");
    }

    @Test
    void capturedPromptCompletionAndProcessExitWinOverDeadPidWithoutInventingAcquisition() throws Exception {
        var deadPid = 99_999_988L;
        var startedAt = Instant.parse("2026-08-02T10:17:52Z");
        var runJson = writeStartedInFlightRun("run-captured-completion", deadPid, startedAt);
        var node = (ObjectNode) MAPPER.readTree(runJson.toFile());
        var timeline = node.withArray("executionTimeline");
        timeline.add(event("prompt_accepted", startedAt.plusSeconds(1), Map.of()));
        timeline.add(event("prompt_completed", startedAt.plusSeconds(40), Map.of()));
        timeline.add(event("process_wait_completed", startedAt.plusSeconds(41), Map.of("exitCode", 0)));
        timeline.add(event("process_exited", startedAt.plusSeconds(41), Map.of("exitCode", 0)));
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(runJson.toFile(), node);

        var result = new AgentExecutionAttemptReconciler(Duration.ofMinutes(10)).reconcile(runJson);

        assertTrue(result.reconciled());
        var reconciled = (ObjectNode) MAPPER.readTree(runJson.toFile());
        assertEquals("EXITED_ZERO", reconciled.path("processResult").path("usability").asText());
        assertEquals(0, reconciled.path("processResult").path("exitCode").asInt());
        assertFalse(reconciled.path("processResult").path("interrupted").asBoolean());
        assertEquals("OUTPUT_INCOMPLETE", reconciled.path("executionOutcome").path("status").asText(),
                "the process exit is known, but the unpersisted AgentResultEnvelope remains unavailable");
        assertTrue(reconciled.path("agentResultAcquisition").isMissingNode(),
                "prompt completion alone does not authorize a fabricated parsed candidate");
        assertTrue(reconciled.path("usage").isMissingNode(),
                "captured lifecycle events do not contain provider token usage");
    }

    @Test
    void processWaitCompletionAloneClosesFromDurableExitInsteadOfDeadPid() throws Exception {
        var deadPid = 99_999_987L;
        var startedAt = Instant.parse("2026-08-02T10:17:52Z");
        var runJson = writeStartedInFlightRun("run-wait-completed", deadPid, startedAt);
        var node = (ObjectNode) MAPPER.readTree(runJson.toFile());
        node.withArray("executionTimeline").add(event(
                "process_wait_completed",
                startedAt.plusSeconds(20),
                Map.of("exitCode", 0)
        ));
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(runJson.toFile(), node);

        var result = new AgentExecutionAttemptReconciler(Duration.ofMinutes(10)).reconcile(runJson);

        assertTrue(result.reconciled());
        var reconciled = MAPPER.readTree(runJson.toFile());
        assertEquals("EXITED_ZERO", reconciled.path("processResult").path("usability").asText(),
                "durable wait completion must win over the later dead-pid observation");
        assertEquals(0, reconciled.path("processResult").path("exitCode").asInt());
        assertFalse(reconciled.path("processResult").path("interrupted").asBoolean());
    }

    @Test
    void durableNonZeroExitKeepsTypedNonZeroUsability() throws Exception {
        var startedAt = Instant.parse("2026-08-02T10:17:52Z");
        var runJson = writeStartedInFlightRun("run-non-zero-exit", 99_999_986L, startedAt);
        var node = (ObjectNode) MAPPER.readTree(runJson.toFile());
        node.withArray("executionTimeline").add(event(
                "process_exited",
                startedAt.plusSeconds(20),
                Map.of("exitCode", 17)
        ));
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(runJson.toFile(), node);

        var result = new AgentExecutionAttemptReconciler(Duration.ofMinutes(10)).reconcile(runJson);

        assertTrue(result.reconciled());
        var reconciled = MAPPER.readTree(runJson.toFile());
        assertEquals("EXITED_NON_ZERO_WITH_INCOMPLETE_OUTPUT",
                reconciled.path("processResult").path("usability").asText());
        assertEquals(17, reconciled.path("processResult").path("exitCode").asInt());
        assertNotEquals("INTERRUPTED", reconciled.path("executionOutcome").path("status").asText(),
                "a durable non-zero exit must not collapse into dead-pid interruption");
    }

    @Test
    void stopLossTerminatesDurableInFlightProcessTreeBeforeReconciliation() throws Exception {
        var process = new ProcessBuilder("sh", "-c", "sleep 30 & wait").start();
        var root = process.toHandle();
        var child = awaitDescendant(root);
        var runJson = writeStartedInFlightRun("run-live-stop-loss", process.pid(), Instant.now());
        var reconciler = new AgentExecutionAttemptReconciler(Duration.ofMinutes(10));
        try {
            assertEquals(1, reconciler.terminateInFlightProcesses(tempDir.resolve("runs")));
            assertDoesNotThrow(() -> root.onExit().get(2, TimeUnit.SECONDS));
            assertFalse(root.isAlive());
            assertDoesNotThrow(() -> child.onExit().get(2, TimeUnit.SECONDS));
            assertFalse(child.isAlive());

            var result = reconciler.reconcile(runJson);
            assertTrue(result.reconciled());
            var reconciled = MAPPER.readTree(runJson.toFile());
            assertEquals("INTERRUPTED", reconciled.path("executionOutcome").path("status").asText());
            assertTrue(reconciled.path("agentResultAcquisition").isMissingNode());
            assertTrue(reconciled.path("usage").isMissingNode());
        } finally {
            root.destroyForcibly();
        }
    }

    private static ProcessHandle awaitDescendant(ProcessHandle root) throws Exception {
        for (int attempt = 0; attempt < 100; attempt++) {
            var descendant = root.descendants().findFirst();
            if (descendant.isPresent()) {
                return descendant.get();
            }
            Thread.sleep(10);
        }
        throw new AssertionError("provider child process did not start");
    }

    private static ObjectNode event(String kind, Instant observedAt, Map<String, Object> details) {
        var event = MAPPER.createObjectNode();
        event.put("kind", kind);
        event.put("source", "adapter");
        event.put("sequence", 1);
        event.put("observedAt", observedAt.toString());
        event.put("elapsedMs", 0);
        event.set("details", MAPPER.valueToTree(details));
        return event;
    }
}
