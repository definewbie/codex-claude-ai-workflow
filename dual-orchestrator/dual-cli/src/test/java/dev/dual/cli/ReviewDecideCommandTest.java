package dev.dual.cli;

import dev.dual.core.id.DualId;
import dev.dual.core.workflow.EdgeDefinition;
import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.WorkflowDefinition;
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
        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-review-decide", definition);
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
        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-review-decide", definition);
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
}
