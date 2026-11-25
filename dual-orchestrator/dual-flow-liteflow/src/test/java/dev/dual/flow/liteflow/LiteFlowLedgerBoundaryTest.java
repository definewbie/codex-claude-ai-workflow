package dev.dual.flow.liteflow;

import dev.dual.core.id.DualId;
import dev.dual.core.workflow.EdgeDefinition;
import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeStatus;
import dev.dual.core.workflow.WorkflowDefinition;
import dev.dual.core.workflow.WorkflowStatus;
import dev.dual.ledger.file.WorkflowFileLedger;
import dev.dual.ledger.file.WorkstreamPaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiteFlowLedgerBoundaryTest {
    @TempDir
    Path tempDir;

    @Test
    void revisionOutcomeStartsNextWorkflowIterationThroughLiteFlowKernel() throws Exception {
        var paths = paths("ws-liteflow-revision");
        var definition = new WorkflowDefinition(
                "agent-lifecycle",
                1,
                "gate.review",
                List.of(
                        new NodeDefinition("gate.review", NodeKind.GATE, "human.review", Map.of()),
                        new NodeDefinition("design.claude_design", NodeKind.TASK, "noop.task", Map.of())
                ),
                List.of(new EdgeDefinition("gate.review", "revise", "design.claude_design"))
        );
        var ledger = liteFlowLedger();
        FlowTestLedger.initialize(ledger, paths, "wf-liteflow-revision", definition);

        var gate = activeNode(ledger, paths);
        ledger.completeAndRoute(paths, definition, gate.nodeInstanceId(), NodeStatus.COMPLETED, "revise", List.of(), Map.of(
                "human_action", "revise",
                "note", "Narrow the design before implementation."
        ));

        var next = activeNode(ledger, paths);
        assertEquals("design.claude_design", next.nodeDefinitionId());
        assertEquals("iter-002", next.iterationId());
        assertTrue(Files.readString(paths.iterationJson("iter-002")).contains("\"triggerAction\" : \"revise\""));
        assertTrue(Files.readString(paths.eventsJsonl()).contains("iteration_started"));
    }

    @Test
    void revisionLimitStopsAtRecoveryThroughLiteFlowKernel() throws Exception {
        var paths = paths("ws-liteflow-revision-limit");
        var definition = new WorkflowDefinition(
                "agent-lifecycle",
                1,
                "gate.review",
                List.of(
                        new NodeDefinition("gate.review", NodeKind.GATE, "human.review", Map.of("max_revisions", 1)),
                        new NodeDefinition("design.claude_design", NodeKind.TASK, "noop.task", Map.of())
                ),
                List.of(
                        new EdgeDefinition("gate.review", "revise", "design.claude_design"),
                        new EdgeDefinition("design.claude_design", "completed", "gate.review")
                )
        );
        var ledger = liteFlowLedger();
        FlowTestLedger.initialize(ledger, paths, "wf-liteflow-revision-limit", definition);

        var firstGate = activeNode(ledger, paths);
        ledger.completeAndRoute(paths, definition, firstGate.nodeInstanceId(), NodeStatus.COMPLETED, "revise", List.of(), Map.of(
                "note", "First revision."
        ));
        var design = activeNode(ledger, paths);
        ledger.completeAndRoute(paths, definition, design.nodeInstanceId(), NodeStatus.COMPLETED, "completed", List.of(), Map.of());
        var secondGate = activeNode(ledger, paths);
        ledger.completeAndRoute(paths, definition, secondGate.nodeInstanceId(), NodeStatus.COMPLETED, "revise", List.of(), Map.of(
                "note", "Second revision should stop."
        ));

        var current = ledger.loadCurrent(paths);
        assertEquals(WorkflowStatus.WAITING_FOR_RECOVERY, current.status());
        assertEquals(List.of(secondGate.nodeInstanceId()), current.activeNodeInstanceIds());
        assertFalse(Files.exists(paths.iterationJson("iter-003")));
        assertEquals(NodeStatus.BLOCKED, ledger.loadNode(paths, secondGate.nodeInstanceId()).status());
        assertTrue(Files.readString(paths.eventsJsonl()).contains("revision_limit_reached"));
    }

    @Test
    void acceptanceRejectRoutesToClassifierBeforeRepairIterationThroughLiteFlowKernel() throws Exception {
        var paths = paths("ws-liteflow-acceptance-reject");
        var definition = new WorkflowDefinition(
                "agent-lifecycle-full",
                3,
                "acceptance.decision",
                List.of(
                        new NodeDefinition("acceptance.decision", NodeKind.DECISION, "review.decision", Map.of("max_revisions", 1)),
                        new NodeDefinition("acceptance.classify_failure", NodeKind.TASK, "acceptance.failure_classify", Map.of()),
                        new NodeDefinition("context.implement_claude", NodeKind.TASK, "context.build_package", Map.of())
                ),
                List.of(
                        new EdgeDefinition("acceptance.decision", "REJECT", "acceptance.classify_failure"),
                        new EdgeDefinition("acceptance.classify_failure", "REJECT_IMPLEMENTATION_BUG", "context.implement_claude")
                )
        );
        var ledger = liteFlowLedger();
        FlowTestLedger.initialize(ledger, paths, "wf-liteflow-acceptance-reject", definition);

        var decision = activeNode(ledger, paths);
        ledger.completeAndRoute(paths, definition, decision.nodeInstanceId(), NodeStatus.COMPLETED, "REJECT", List.of("review.md"), Map.of(
                "decision", "REJECT",
                "summary", "README has a factual error."
        ));

        var classifier = activeNode(ledger, paths);
        assertEquals("acceptance.classify_failure", classifier.nodeDefinitionId());
        assertEquals("iter-001", classifier.iterationId());
        assertFalse(Files.exists(paths.iterationJson("iter-002")));

        ledger.completeAndRoute(paths, definition, classifier.nodeInstanceId(), NodeStatus.COMPLETED,
                "REJECT_IMPLEMENTATION_BUG", List.of("failure-classification.json"), Map.of(
                        "category", "IMPLEMENTATION_BUG"
                ));

        var repair = activeNode(ledger, paths);
        assertEquals("context.implement_claude", repair.nodeDefinitionId());
        assertEquals("iter-002", repair.iterationId());
        assertTrue(Files.exists(paths.iterationJson("iter-002")));
    }

    @Test
    void failedNodeStopsWorkflowWithoutRoutingThroughLiteFlowKernel() throws Exception {
        var paths = paths("ws-liteflow-failed");
        var definition = new WorkflowDefinition(
                "agent-lifecycle",
                1,
                "first",
                List.of(
                        new NodeDefinition("first", NodeKind.TASK, "noop.task", Map.of()),
                        new NodeDefinition("second", NodeKind.TASK, "noop.task", Map.of())
                ),
                List.of(new EdgeDefinition("first", "completed", "second"))
        );
        var ledger = liteFlowLedger();
        FlowTestLedger.initialize(ledger, paths, "wf-liteflow-failed", definition);

        var first = activeNode(ledger, paths);
        ledger.completeAndRoute(paths, definition, first.nodeInstanceId(), NodeStatus.FAILED, "completed", List.of(), Map.of("exit_code", 4));

        var stopped = ledger.loadCurrent(paths);
        assertEquals(WorkflowStatus.WAITING_FOR_RECOVERY, stopped.status());
        assertEquals(List.of(first.nodeInstanceId()), stopped.activeNodeInstanceIds());
        assertTrue(Files.exists(paths.gateJson("recovery-" + first.nodeInstanceId())));
        assertFalse(Files.exists(paths.nodeJson("node-second-002")));
        assertTrue(Files.readString(paths.eventsJsonl()).contains("node_failed"));
    }

    @Test
    void finalGateBoundaryIsStillOwnedByLedgerWithLiteFlowKernel() throws Exception {
        var paths = paths("ws-liteflow-final-gate");
        var definition = new WorkflowDefinition(
                "agent-lifecycle",
                1,
                "stage.generate_next_prompt",
                List.of(
                        new NodeDefinition("stage.generate_next_prompt", NodeKind.TASK, "prompt.generate_next_stage", Map.of()),
                        new NodeDefinition("gate.human_next_stage_approval", NodeKind.GATE, "human.review", Map.of(
                                "allowed_actions", List.of("revise", "complete"),
                                "max_revisions", 1
                        ))
                ),
                List.of(new EdgeDefinition("stage.generate_next_prompt", "completed", "gate.human_next_stage_approval"))
        );
        var ledger = liteFlowLedger();
        FlowTestLedger.initialize(ledger, paths, "wf-liteflow-final-gate", definition);

        var generator = activeNode(ledger, paths);
        ledger.completeAndRoute(paths, definition, generator.nodeInstanceId(), NodeStatus.COMPLETED, "completed", List.of(), Map.of());

        assertThrows(IOException.class, () -> ledger.resolveOpenGate(paths, definition, "approved", "bad loop"));
        ledger.resolveOpenGate(paths, definition, "complete", "finish");

        assertEquals(WorkflowStatus.COMPLETED, ledger.loadCurrent(paths).status());
        assertTrue(ledger.loadCurrent(paths).activeNodeInstanceIds().isEmpty());
    }

    private WorkstreamPaths paths(String workstreamId) throws IOException {
        var paths = new WorkstreamPaths(tempDir, new DualId(workstreamId));
        Files.createDirectories(paths.workstreamDir());
        return paths;
    }

    private static WorkflowFileLedger liteFlowLedger() {
        return new WorkflowFileLedger(new LiteFlowPrototypeKernel(), Map.of("test_binary", "liteflow-tests-v3"));
    }

    private static dev.dual.ledger.file.NodeInstanceDocument activeNode(WorkflowFileLedger ledger, WorkstreamPaths paths) throws IOException {
        return ledger.loadNode(paths, ledger.loadCurrent(paths).activeNodeInstanceIds().get(0));
    }
}
