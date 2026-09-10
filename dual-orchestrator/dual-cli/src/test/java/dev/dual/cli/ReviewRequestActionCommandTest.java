package dev.dual.cli;

import dev.dual.core.id.DualId;
import dev.dual.core.workflow.*;
import dev.dual.ledger.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ReviewRequestActionCommandTest {
    @TempDir Path root;
    @Test void collectionCommandCannotResolveWithoutQualifiedSource() throws Exception {
        var paths = new WorkstreamPaths(root, new DualId("ws-cli-collect"));
        var definition = new WorkflowDefinition("collect", 1, "start", List.of(
                new NodeDefinition("start", NodeKind.TASK, "noop.task", Map.of()),
                new NodeDefinition("repair", NodeKind.GATE, "human.review", Map.of("gate_purpose", "implementation_repair",
                        "allowed_actions", List.of("collect_evidence"))),
                new NodeDefinition("collect", NodeKind.TASK, "acpx.agent_run", Map.of("expected_artifact", "self_test_report"))),
                List.of(new EdgeDefinition("start", "completed", "repair"), new EdgeDefinition("repair", "collect_evidence", "collect")));
        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-collect", definition);
        ledger.completeAndRoute(paths, definition, ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst(),
                NodeStatus.COMPLETED, "completed", List.of(), Map.of());
        var before = ledger.loadCurrent(paths);
        var command = new ReviewDecideCommand();
        command.workdir = root;
        command.workstreamId = "ws-cli-collect";
        command.action = "collect_evidence";
        command.note = "Collect evidence only";
        command.runImmediately = true;
        assertThrows(java.io.IOException.class, command::call);
        assertEquals(before, ledger.loadCurrent(paths));
    }

    @Test void commandRecordsAnAdvisoryRequestAndDoesNotResolve() throws Exception {
        var paths = new WorkstreamPaths(root, new DualId("ws-cli-request"));
        var definition = new WorkflowDefinition("cli-request", 1, "start", List.of(
                new NodeDefinition("start", NodeKind.TASK, "noop.task", Map.of()),
                new NodeDefinition("repair", NodeKind.GATE, "human.review", Map.of(
                        "gate_purpose", "implementation_repair", "allowed_actions", List.of("revise", "request_action"))),
                new NodeDefinition("after", NodeKind.TASK, "noop.task", Map.of())), List.of(
                new EdgeDefinition("start", "completed", "repair"), new EdgeDefinition("repair", "revise", "after")));
        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-cli-request", definition);
        ledger.completeAndRoute(paths, definition, ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst(),
                NodeStatus.COMPLETED, "completed", List.of(), Map.of());
        var before = ledger.loadCurrent(paths);
        var command = new ReviewRequestActionCommand();
        command.workdir = root;
        command.workstreamId = "ws-cli-request";
        command.gate = before.activeNodeInstanceIds().getFirst();
        command.requestId = "device";
        command.expectedResult = "Device verification";
        command.reason = "No available action";
        assertEquals(0, command.call());
        assertEquals(0, command.call());
        assertEquals(before, ledger.loadCurrent(paths));
        assertEquals(1, ((List<?>) ledger.loadNode(paths, command.gate).result().get("action_requests")).size());
    }
}
