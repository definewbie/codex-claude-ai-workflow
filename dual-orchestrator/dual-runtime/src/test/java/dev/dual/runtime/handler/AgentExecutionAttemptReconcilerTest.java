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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
