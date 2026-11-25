package dev.dual.cli;

import dev.dual.core.id.DualId;
import dev.dual.core.workflow.EdgeDefinition;
import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.WorkflowDefinition;
import dev.dual.core.workflow.WorkflowStatus;
import dev.dual.ledger.file.WorkflowFileLedger;
import dev.dual.ledger.file.WorkstreamPaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReviewDecideCommandTest {
    @TempDir
    Path tempDir;

    @Test
    void decideAppliesAnyAllowedGateAction() throws Exception {
        var workstreamId = "ws-review-decide";
        var paths = new WorkstreamPaths(tempDir, new DualId(workstreamId));
        var definition = new WorkflowDefinition(
                "test-review-decide",
                1,
                "before.review",
                List.of(
                        new NodeDefinition("before.review", NodeKind.TASK, "noop.task", Map.of()),
                        new NodeDefinition("gate.contract_review", NodeKind.GATE, "human.review",
                                Map.of("allowed_actions", List.of("approve", "clarify", "reject"))),
                        new NodeDefinition("after.reject", NodeKind.TASK, "noop.task", Map.of())
                ),
                List.of(
                        new EdgeDefinition("before.review", "completed", "gate.contract_review"),
                        new EdgeDefinition("gate.contract_review", "reject", "after.reject"))
        );
        var ledger = CliLedgerFactory.create();
        CliTestLedger.initialize(ledger, paths, "wf-review-decide", definition);
        var entry = ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst();
        ledger.completeAndRoute(paths, definition, entry,
                dev.dual.core.workflow.NodeStatus.COMPLETED, "completed", List.of(), Map.of());

        var command = new ReviewDecideCommand();
        command.workdir = tempDir;
        command.action = "reject";
        command.note = "contract is empty";
        command.workstreamId = workstreamId;

        assertEquals(0, command.call());

        var current = ledger.loadCurrent(paths);
        assertEquals("after.reject", ledger.loadNode(paths, current.activeNodeInstanceIds().get(0)).nodeDefinitionId());
    }

    @Test
    void decideNormalizesApproveToApprovedWhenWorkflowUsesApprovedEdge() throws Exception {
        var workstreamId = "ws-review-decide-approve";
        var paths = new WorkstreamPaths(tempDir, new DualId(workstreamId));
        var definition = new WorkflowDefinition(
                "test-review-decide",
                1,
                "before.review",
                List.of(
                        new NodeDefinition("before.review", NodeKind.TASK, "noop.task", Map.of()),
                        new NodeDefinition("gate.contract_review", NodeKind.GATE, "human.review",
                                Map.of("allowed_actions", List.of("approve", "reject"))),
                        new NodeDefinition("after.approved", NodeKind.TASK, "noop.task", Map.of())
                ),
                List.of(
                        new EdgeDefinition("before.review", "completed", "gate.contract_review"),
                        new EdgeDefinition("gate.contract_review", "approved", "after.approved"))
        );
        var ledger = CliLedgerFactory.create();
        CliTestLedger.initialize(ledger, paths, "wf-review-decide", definition);
        var entry = ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst();
        ledger.completeAndRoute(paths, definition, entry,
                dev.dual.core.workflow.NodeStatus.COMPLETED, "completed", List.of(), Map.of());

        var command = new ReviewDecideCommand();
        command.workdir = tempDir;
        command.action = "approve";
        command.note = "looks good";
        command.workstreamId = workstreamId;

        assertEquals(0, command.call());

        var current = ledger.loadCurrent(paths);
        assertEquals("after.approved", ledger.loadNode(paths, current.activeNodeInstanceIds().get(0)).nodeDefinitionId());
    }

    @Test
    void approveShortcutCompletesTerminalGateThatAllowsApproveWithoutAnEdge() throws Exception {
        var workstreamId = "ws-review-approve-terminal";
        var paths = new WorkstreamPaths(tempDir, new DualId(workstreamId));
        var definition = new WorkflowDefinition(
                "test-review-approve-terminal",
                1,
                "before.review",
                List.of(
                        new NodeDefinition("before.review", NodeKind.TASK, "noop.task", Map.of()),
                        new NodeDefinition("gate.human_acceptance", NodeKind.GATE, "human.review",
                                Map.of("allowed_actions", List.of("approve", "revise")))
                ),
                List.of(new EdgeDefinition("before.review", "completed", "gate.human_acceptance"))
        );
        var ledger = CliLedgerFactory.create();
        CliTestLedger.initialize(ledger, paths, "wf-review-approve-terminal", definition);
        var entry = ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst();
        ledger.completeAndRoute(paths, definition, entry,
                dev.dual.core.workflow.NodeStatus.COMPLETED, "completed", List.of(), Map.of());

        var command = new ReviewApproveCommand();
        command.workdir = tempDir;
        command.note = "final acceptance";
        command.workstreamId = workstreamId;

        assertEquals(0, command.call());

        assertEquals(WorkflowStatus.COMPLETED, ledger.loadCurrent(paths).status());
    }

    @Test
    void decideWithStaleSubjectIsRefusedAndLeavesTheOpenGateUntouched() throws Exception {
        var workstreamId = "ws-review-decide-stale";
        var paths = new WorkstreamPaths(tempDir, new DualId(workstreamId));
        var definition = twoApprovalGates("stale");
        var ledger = CliLedgerFactory.create();
        CliTestLedger.initialize(ledger, paths, "wf-stale", definition);
        var entry = ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst();
        ledger.completeAndRoute(paths, definition, entry, dev.dual.core.workflow.NodeStatus.COMPLETED, "completed", List.of(), Map.of());
        var firstGate = ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst();
        ledger.resolveOpenGate(paths, definition, "approved", "another session moved on");
        var between = ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst();
        ledger.completeAndRoute(paths, definition, between, dev.dual.core.workflow.NodeStatus.COMPLETED, "completed", List.of(), Map.of());
        var secondGate = ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst();
        var eventsBefore = java.nio.file.Files.readAllLines(paths.eventsJsonl()).size();

        var command = new ReviewDecideCommand();
        command.workdir = tempDir;
        command.action = "approve";
        command.note = "approve the gate shown to me";
        command.subject = firstGate;
        command.workstreamId = workstreamId;

        var error = org.junit.jupiter.api.Assertions.assertThrows(java.io.IOException.class, command::call);
        org.junit.jupiter.api.Assertions.assertTrue(error.getMessage().contains("no longer the open gate"), error.getMessage());
        var current = ledger.loadCurrent(paths);
        assertEquals(List.of(secondGate), current.activeNodeInstanceIds(), "stale decision must not resolve gate B");
        assertEquals("open", ledger.loadOpenGateForNode(paths, secondGate).status());
        assertEquals(eventsBefore, java.nio.file.Files.readAllLines(paths.eventsJsonl()).size(), "no event may be appended");
    }

    @Test
    void decideWithCurrentSubjectResolvesTheGate() throws Exception {
        var workstreamId = "ws-review-decide-subject";
        var paths = new WorkstreamPaths(tempDir, new DualId(workstreamId));
        var definition = twoApprovalGates("subject");
        var ledger = CliLedgerFactory.create();
        CliTestLedger.initialize(ledger, paths, "wf-subject", definition);
        var entry = ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst();
        ledger.completeAndRoute(paths, definition, entry, dev.dual.core.workflow.NodeStatus.COMPLETED, "completed", List.of(), Map.of());
        var gate = ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst();

        var command = new ReviewDecideCommand();
        command.workdir = tempDir;
        command.action = "approve";
        command.note = "approve the gate shown to me";
        command.subject = gate;
        command.workstreamId = workstreamId;

        assertEquals(0, command.call());
        var current = ledger.loadCurrent(paths);
        assertEquals("between", ledger.loadNode(paths, current.activeNodeInstanceIds().getFirst()).nodeDefinitionId());
    }

    private static WorkflowDefinition twoApprovalGates(String suffix) {
        return new WorkflowDefinition(
                "two-gates-" + suffix,
                1,
                "before",
                List.of(
                        new NodeDefinition("before", NodeKind.TASK, "noop.task", Map.of()),
                        new NodeDefinition("gate.a", NodeKind.GATE, "human.review", Map.of()),
                        new NodeDefinition("between", NodeKind.TASK, "noop.task", Map.of()),
                        new NodeDefinition("gate.b", NodeKind.GATE, "human.review", Map.of()),
                        new NodeDefinition("after", NodeKind.TASK, "noop.task", Map.of())
                ),
                List.of(
                        new EdgeDefinition("before", "completed", "gate.a"),
                        new EdgeDefinition("gate.a", "approved", "between"),
                        new EdgeDefinition("between", "completed", "gate.b"),
                        new EdgeDefinition("gate.b", "approved", "after")
                )
        );
    }
}
