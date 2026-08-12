package dev.dual.ledger.file;

import dev.dual.core.id.DualId;
import dev.dual.core.workflow.EdgeDefinition;
import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeStatus;
import dev.dual.core.workflow.WorkflowDefinition;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkflowGateResolutionTest {
    @TempDir
    Path tempDir;

    @Test
    void approvingOpenGateRoutesByHumanAction() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-gate"));
        Files.createDirectories(paths.workstreamDir());
        var definition = gateDefinition();
        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-gate", definition);
        var entry = ledger.loadCurrent(paths).activeNodeInstanceIds().get(0);

        ledger.completeAndRoute(paths, definition, entry, NodeStatus.COMPLETED, "completed", List.of(), Map.of());
        var gateNodeId = ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst();
        assertEquals("completed", ledger.loadOpenGateForNode(paths, gateNodeId).result().get("source_outcome"));
        ledger.resolveOpenGate(paths, definition, "approved", "approved by test");

        var next = ledger.loadCurrent(paths);
        assertEquals("after.approve", ledger.loadNode(paths, next.activeNodeInstanceIds().get(0)).nodeDefinitionId());
    }

    @Test
    void blockedSourceCannotAdvanceWithoutHumanDecisionNote() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-blocked-gate"));
        Files.createDirectories(paths.workstreamDir());
        var definition = gateDefinition();
        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-blocked-gate", definition);
        var entry = ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst();
        ledger.completeAndRoute(paths, definition, entry, NodeStatus.COMPLETED, "BLOCKED", List.of("review.contract.json"), Map.of());

        var before = ledger.loadCurrent(paths);
        var error = assertThrows(java.io.IOException.class,
                () -> ledger.resolveOpenGate(paths, definition, "approved", ""));

        assertEquals("a decision note is required for a BLOCKED human gate", error.getMessage());
        assertEquals(before, ledger.loadCurrent(paths));
        var gate = ledger.loadOpenGateForNode(paths, before.activeNodeInstanceIds().getFirst());
        assertEquals("open", gate.status());
        assertEquals("BLOCKED", gate.result().get("source_outcome"));
        assertEquals(List.of("review.contract.json"), gate.result().get("subject_input_refs"));
    }

    @Test
    void rejectsResolutionWhenActiveNodeIsNotGate() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-not-gate"));
        Files.createDirectories(paths.workstreamDir());
        var definition = new WorkflowDefinition(
                "agent-lifecycle",
                1,
                "first",
                List.of(new NodeDefinition("first", NodeKind.TASK, "noop.task", Map.of())),
                List.of()
        );
        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-not-gate", definition);

        var error = assertThrows(java.io.IOException.class, () ->
                ledger.resolveOpenGate(paths, definition, "approved", "not a gate"));
        assertEquals("active node is not a gate: first", error.getMessage());
    }

    private static WorkflowDefinition gateDefinition() {
        return new WorkflowDefinition(
                "agent-lifecycle",
                1,
                "before.gate",
                List.of(
                        new NodeDefinition("before.gate", NodeKind.TASK, "noop.task", Map.of()),
                        new NodeDefinition("gate.review", NodeKind.GATE, "human.review", Map.of()),
                        new NodeDefinition("after.approve", NodeKind.TASK, "noop.task", Map.of())
                ),
                List.of(
                        new EdgeDefinition("before.gate", "completed", "gate.review"),
                        new EdgeDefinition("before.gate", "BLOCKED", "gate.review"),
                        new EdgeDefinition("gate.review", "approved", "after.approve")
                )
        );
    }
}
