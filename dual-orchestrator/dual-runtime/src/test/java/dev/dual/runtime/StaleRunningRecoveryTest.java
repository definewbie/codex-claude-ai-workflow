package dev.dual.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dual.core.id.DualId;
import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeStatus;
import dev.dual.core.workflow.WorkflowDefinition;
import dev.dual.core.workflow.WorkflowStatus;
import dev.dual.ledger.file.WorkflowFileLedger;
import dev.dual.ledger.file.WorkstreamPaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StaleRunningRecoveryTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void terminateAndRecoverClosesExecutionBeforeInstanceTransition() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-stop-loss"));
        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-stop-loss", new WorkflowDefinition(
                "stop-loss", 1, "agent.node",
                List.of(new NodeDefinition("agent.node", NodeKind.TASK, "agent.task", Map.of())),
                List.of()));
        var nodeId = ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst();
        var process = new ProcessBuilder("sleep", "30").start();
        ledger.markRunning(paths, nodeId, process.pid());
        var runJson = writeStartedRun(paths, process.pid());
        try {
            var result = new StaleRunningRecovery(ledger).terminateAndRecover(paths, "stop-loss");

            assertEquals(1, result.terminatedProcessTrees());
            assertEquals(1, result.reconciledRuns());
            assertFalse(process.isAlive());
            assertEquals("INTERRUPTED", MAPPER.readTree(runJson.toFile())
                    .path("executionOutcome").path("status").asText());
            assertTrue(MAPPER.readTree(runJson.toFile()).path("usage").isMissingNode());
            assertTrue(MAPPER.readTree(runJson.toFile()).path("agentResultAcquisition").isMissingNode());
            assertEquals(NodeStatus.FAILED, ledger.loadNode(paths, nodeId).status());
            assertEquals(WorkflowStatus.WAITING_FOR_RECOVERY, ledger.loadCurrent(paths).status());
            assertEquals(List.of("recovery-" + nodeId), ledger.loadCurrent(paths).openGateIds());
        } finally {
            process.destroyForcibly();
        }
    }

    @Test
    void recoverCannotTransitionInstanceWhileDurableProviderProcessIsAlive() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-live-provider"));
        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-live-provider", new WorkflowDefinition(
                "live-provider", 1, "agent.node",
                List.of(new NodeDefinition("agent.node", NodeKind.TASK, "agent.task", Map.of())),
                List.of()));
        var nodeId = ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst();
        ledger.markRunning(paths, nodeId, Long.MAX_VALUE);
        var process = new ProcessBuilder("sleep", "30").start();
        writeStartedRun(paths, process.pid());
        try {
            var error = assertThrows(IllegalStateException.class,
                    () -> new StaleRunningRecovery(ledger).recover(paths, "must fail closed"));

            assertTrue(error.getMessage().contains("remain in-flight"));
            assertEquals(NodeStatus.RUNNING, ledger.loadNode(paths, nodeId).status());
            assertEquals(WorkflowStatus.RUNNING, ledger.loadCurrent(paths).status());
            assertTrue(process.isAlive());
        } finally {
            process.destroyForcibly();
        }
    }

    @Test
    void terminateAndRecoverCannotTransitionWithoutDurableProcessEvidence() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-missing-process-evidence"));
        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-missing-process-evidence", new WorkflowDefinition(
                "missing-process-evidence", 1, "agent.node",
                List.of(new NodeDefinition("agent.node", NodeKind.TASK, "agent.task", Map.of())),
                List.of()));
        var nodeId = ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst();
        ledger.markRunning(paths, nodeId, Long.MAX_VALUE);
        writeStartedRunWithoutPid(paths);

        var error = assertThrows(IllegalStateException.class,
                () -> new StaleRunningRecovery(ledger).terminateAndRecover(paths, "must fail closed"));

        assertTrue(error.getMessage().contains("remain in-flight"));
        assertEquals(NodeStatus.RUNNING, ledger.loadNode(paths, nodeId).status());
        assertEquals(WorkflowStatus.RUNNING, ledger.loadCurrent(paths).status());
    }

    private Path writeStartedRun(WorkstreamPaths paths, long pid) throws Exception {
        var runJson = paths.runsDir().resolve("run-stop-loss/run.json");
        Files.createDirectories(runJson.getParent());
        var startedAt = Instant.now();
        var run = MAPPER.createObjectNode();
        run.put("schemaVersion", 3);
        run.put("status", "running");
        run.put("runId", "run-stop-loss");
        run.put("workstreamId", paths.workstreamId().value());
        run.put("nodeId", "agent.node");
        run.put("agent", "claude");
        run.put("startedAt", startedAt.toString());
        var attempt = run.putObject("executionAttempt");
        attempt.put("attemptId", "att-stop-loss");
        attempt.put("runId", "run-stop-loss");
        attempt.put("workstreamId", "ws-stop-loss");
        attempt.put("nodeId", "agent.node");
        attempt.put("agent", "claude");
        attempt.put("status", "RUNNING");
        attempt.put("processStatus", "RUNNING");
        attempt.put("startedAt", startedAt.toString());
        var event = MAPPER.createObjectNode();
        event.put("kind", "process_started");
        event.put("source", "adapter");
        event.put("sequence", 1);
        event.put("observedAt", startedAt.toString());
        event.put("elapsedMs", 0);
        event.putObject("details").put("pid", pid);
        run.putArray("executionTimeline").add(event);
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(runJson.toFile(), run);
        return runJson;
    }

    private void writeStartedRunWithoutPid(WorkstreamPaths paths) throws Exception {
        var runJson = writeStartedRun(paths, 0);
        var run = (com.fasterxml.jackson.databind.node.ObjectNode) MAPPER.readTree(runJson.toFile());
        run.withArray("executionTimeline").removeAll();
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(runJson.toFile(), run);
    }
}
