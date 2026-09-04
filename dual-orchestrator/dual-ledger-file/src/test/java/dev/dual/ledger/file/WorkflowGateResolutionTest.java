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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void advisoryRevisionWaitsForHumanAndNotedHumanRevisionMayExceedAutomaticBudget() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-advisory-design-review"));
        Files.createDirectories(paths.workstreamDir());
        var definition = advisoryDesignDefinition();
        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-advisory-design-review", definition);
        var decision = ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst();

        ledger.completeAndRoute(paths, definition, decision, NodeStatus.COMPLETED,
                "NEEDS_REVISION", List.of("design-review.contract.json"), Map.of());

        var waiting = ledger.loadCurrent(paths);
        assertEquals(dev.dual.core.workflow.WorkflowStatus.WAITING_FOR_HUMAN, waiting.status());
        var firstGate = ledger.loadNode(paths, waiting.activeNodeInstanceIds().getFirst());
        assertEquals("gate.human_design_approval", firstGate.nodeDefinitionId());
        assertEquals("iter-001", firstGate.iterationId());
        assertFalse(Files.exists(paths.iterationJson("iter-002")),
                "an advisory verdict must not create a revision before the human decision");

        var missingNote = assertThrows(java.io.IOException.class,
                () -> ledger.resolveOpenGate(paths, definition, "revise", ""));
        assertEquals("a decision note is required to revise a design", missingNote.getMessage());

        var humanReason = "Keep the transaction design, but add a crash-recovery invariant.";
        ledger.resolveOpenGate(paths, definition, "revise", humanReason);
        var revisedDesign = ledger.loadNode(paths, ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst());
        assertEquals("design.claude_design", revisedDesign.nodeDefinitionId());
        assertEquals("iter-002", revisedDesign.iterationId());
        assertTrue(Files.readString(paths.iterationJson("iter-002")).contains(humanReason));

        ledger.completeAndRoute(paths, definition, revisedDesign.nodeInstanceId(), NodeStatus.COMPLETED,
                "completed", List.of("design-v2.contract.json"), Map.of());
        var secondGate = ledger.loadNode(paths, ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst());
        var beforeAuthorizedOverride = ledger.loadCurrent(paths);
        assertThrows(java.io.IOException.class,
                () -> ledger.resolveOpenGate(paths, definition, "revise", ""));
        assertEquals(beforeAuthorizedOverride, ledger.loadCurrent(paths));
        ledger.resolveOpenGate(paths, definition, "revise", "A human authorizes another bounded revision.");

        assertEquals(dev.dual.core.workflow.WorkflowStatus.RUNNING,
                ledger.loadCurrent(paths).status());
        var secondRevision = ledger.loadNode(paths, ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst());
        assertEquals(NodeStatus.COMPLETED, ledger.loadNode(paths, secondGate.nodeInstanceId()).status());
        assertEquals("design.claude_design", secondRevision.nodeDefinitionId());
        assertEquals("iter-003", secondRevision.iterationId());
        assertTrue(Files.readString(paths.iterationJson("iter-003"))
                .contains("A human authorizes another bounded revision."));
        assertFalse(Files.readString(paths.eventsJsonl()).contains("revision_limit_reached"));
    }

    @Test
    void humanCanApproveNegativeAdvisoryWithoutCreatingRevision() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-approve-negative-advisory"));
        Files.createDirectories(paths.workstreamDir());
        var definition = advisoryDesignDefinition();
        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-approve-negative-advisory", definition);
        var decision = ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst();

        ledger.completeAndRoute(paths, definition, decision, NodeStatus.COMPLETED,
                "NEEDS_REVISION", List.of("design-review.contract.json"), Map.of());
        ledger.resolveOpenGate(paths, definition, "approve", "The advisory risk is accepted for this scope.");

        var plan = ledger.loadNode(paths, ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst());
        assertEquals("plan.claude_plan", plan.nodeDefinitionId());
        assertEquals("iter-001", plan.iterationId());
        assertFalse(Files.exists(paths.iterationJson("iter-002")));
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

    private static WorkflowDefinition advisoryDesignDefinition() {
        return new WorkflowDefinition(
                "advisory-design",
                1,
                "design.decision",
                List.of(
                        new NodeDefinition("design.decision", NodeKind.DECISION, "review.decision", Map.of()),
                        new NodeDefinition("gate.human_design_approval", NodeKind.GATE, "human.review", Map.of(
                                "gate_purpose", "design",
                                "allowed_actions", List.of("approve", "revise"),
                                "max_revisions", 1
                        )),
                        new NodeDefinition("design.claude_design", NodeKind.TASK, "noop.task", Map.of()),
                        new NodeDefinition("plan.claude_plan", NodeKind.TASK, "noop.task", Map.of())
                ),
                List.of(
                        new EdgeDefinition("design.decision", "READY_FOR_ARCH_REVIEW", "gate.human_design_approval"),
                        new EdgeDefinition("design.decision", "NEEDS_REVISION", "gate.human_design_approval"),
                        new EdgeDefinition("design.decision", "BLOCKED", "gate.human_design_approval"),
                        new EdgeDefinition("gate.human_design_approval", "approved", "plan.claude_plan"),
                        new EdgeDefinition("gate.human_design_approval", "revise", "design.claude_design"),
                        new EdgeDefinition("design.claude_design", "completed", "gate.human_design_approval")
                )
        );
    }
}
