package dev.dual.cli;

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

class RecoveryStaleCommandTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void commandRecoversLocalStaleNodeWithoutPromotingAnUnboundMutableRun() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-stale-command"));
        var ledger = CliLedgerFactory.create();
        CliTestLedger.initialize(ledger, paths, "wf-stale-command", new WorkflowDefinition(
                "stale-command", 1, "agent.node",
                List.of(new NodeDefinition("agent.node", NodeKind.TASK, "agent.task", Map.of())),
                List.of()));
        var nodeId = ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst();
        ledger.markRunning(paths, nodeId, Long.MAX_VALUE);
        var runJson = writeDeadRun(paths);

        var rawBefore = Files.readAllBytes(runJson);
        var command = new RecoveryStaleCommand();
        command.workdir = tempDir;
        command.workstreamId = "ws-stale-command";
        command.note = "deterministic stale close";

        assertEquals(0, command.call());
        org.junit.jupiter.api.Assertions.assertArrayEquals(rawBefore, Files.readAllBytes(runJson));
        assertEquals(NodeStatus.FAILED, ledger.loadNode(paths, nodeId).status());
        assertEquals(WorkflowStatus.WAITING_FOR_RECOVERY, ledger.loadCurrent(paths).status());
    }

    @Test
    void adoptionRejectsWrongDispositionAndConflictingStopBeforeAnyLedgerMutation() throws Exception {
        var command = new RecoveryStaleCommand();
        command.workdir = tempDir; command.workstreamId = "ws-no-side-effect";
        command.decision = tempDir.resolve("decision.json");
        Files.writeString(command.decision, "{\"disposition\":\"AUTHORIZE_NEW_ATTEMPT\"}");
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, command::call);
        command.terminateProcess = true;
        Files.delete(command.decision);
        var rejected = org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, command::call);
        org.junit.jupiter.api.Assertions.assertTrue(rejected.getMessage().contains("separate operations"));
        org.junit.jupiter.api.Assertions.assertFalse(Files.exists(tempDir.resolve(".agent")));
    }

    private Path writeDeadRun(WorkstreamPaths paths) throws Exception {
        var runJson = paths.runsDir().resolve("run-dead/run.json");
        Files.createDirectories(runJson.getParent());
        var startedAt = Instant.now().minusSeconds(30);
        var run = MAPPER.createObjectNode();
        run.put("schemaVersion", 3);
        run.put("status", "running");
        run.put("runId", "run-dead");
        run.put("workstreamId", paths.workstreamId().value());
        run.put("nodeId", "agent.node");
        run.put("agent", "claude");
        run.put("startedAt", startedAt.toString());
        var attempt = run.putObject("executionAttempt");
        attempt.put("attemptId", "att-dead");
        attempt.put("runId", "run-dead");
        attempt.put("workstreamId", paths.workstreamId().value());
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
        event.putObject("details").put("pid", Long.MAX_VALUE);
        run.putArray("executionTimeline").add(event);
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(runJson.toFile(), run);
        return runJson;
    }
}
