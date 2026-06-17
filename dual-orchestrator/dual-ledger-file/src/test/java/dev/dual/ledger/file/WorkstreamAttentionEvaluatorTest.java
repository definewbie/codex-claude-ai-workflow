package dev.dual.ledger.file;

import dev.dual.core.id.DualId;
import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeStatus;
import dev.dual.core.workflow.WorkflowDefinition;
import dev.dual.core.workflow.WorkflowStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkstreamAttentionEvaluatorTest {
    @TempDir
    Path tempDir;

    @Test
    void recoverableRuntimeFailureIsRetrySafe() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-runtime-recovery"));
        var ledger = new WorkflowFileLedger();
        var definition = singleNodeDefinition();
        ledger.initialize(paths, "wf-runtime-recovery", definition);
        var node = ledger.loadNode(paths, ledger.loadCurrent(paths).activeNodeInstanceIds().get(0));

        ledger.completeAndRoute(paths, definition, node.nodeInstanceId(), NodeStatus.FAILED,
                "RECOVERABLE_RATE_LIMIT",
                List.of(".agent/workstreams/ws-runtime-recovery/runs/run-001/outputs/design.stderr.log"),
                Map.of(
                        "acpx_failure.reason", "RECOVERABLE_RATE_LIMIT",
                        "acpx_failure.recoverable", true
                ));

        var attention = WorkstreamAttentionEvaluator.evaluate(paths);

        assertEquals(WorkstreamAttentionCategory.RUNTIME_RECOVERY, attention.category());
        assertTrue(attention.retrySafe());
        assertTrue(attention.command().contains("dualctl retry --workdir " + tempDir.toAbsolutePath().normalize()));
    }

    @Test
    void currentRecoveryTakesPrecedenceOverStaleGoalOutcome() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-runtime-recovery-with-stale-goal"));
        var ledger = new WorkflowFileLedger();
        var definition = singleNodeDefinition();
        ledger.initialize(paths, "wf-runtime-recovery-with-stale-goal", definition);
        var node = ledger.loadNode(paths, ledger.loadCurrent(paths).activeNodeInstanceIds().get(0));

        new AtomicJsonWriter().write(paths.goalSatisfactionJson(), new GoalSatisfactionDocument(
                1,
                paths.workstreamId().value(),
                "wf-runtime-recovery-with-stale-goal",
                "node-design-decision-000",
                "design.decision",
                "iter-001",
                "NEEDS_REVISION",
                GoalDomainOutcome.NEEDS_REDESIGN,
                "historical design revision",
                List.of("runs/design-review.md"),
                Map.of("decision", "NEEDS_REVISION"),
                java.time.Instant.now()
        ));

        ledger.completeAndRoute(paths, definition, node.nodeInstanceId(), NodeStatus.FAILED,
                "RECOVERABLE_AGENT_OUTPUT_INTERRUPTED",
                List.of(".agent/workstreams/ws-runtime-recovery-with-stale-goal/runs/run-001/outputs/implement.md"),
                Map.of(
                        "acpx_failure.reason", "RECOVERABLE_AGENT_OUTPUT_INTERRUPTED",
                        "acpx_failure.recoverable", true
                ));

        var attention = WorkstreamAttentionEvaluator.evaluate(paths);
        var summary = Files.readString(paths.currentFailureMarkdown());

        assertEquals(WorkstreamAttentionCategory.RUNTIME_RECOVERY, attention.category());
        assertEquals("Agent output interrupted", attention.title());
        assertTrue(attention.retrySafe());
        assertTrue(attention.nextAction().contains("Rebuild the acpx agent session"));
        assertEquals("NEEDS_REDESIGN", attention.domainOutcome());
        assertTrue(summary.contains("Attention Category: RUNTIME_RECOVERY"));
        assertTrue(summary.contains("Retry Safe: true"));
    }

    @Test
    void revisionLimitIsSemanticAndNotRetrySafe() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-revision-limit-attention"));
        var ledger = new WorkflowFileLedger();
        var definition = new WorkflowDefinition(
                "wf-revision-limit-attention",
                1,
                "design.decision",
                List.of(
                        new NodeDefinition("design.decision", NodeKind.DECISION, "review.decision", Map.of("max_revisions", 0)),
                        new NodeDefinition("design.claude_design", NodeKind.TASK, "acpx.agent_run", Map.of())
                ),
                List.of(new dev.dual.core.workflow.EdgeDefinition("design.decision", "NEEDS_REVISION", "design.claude_design"))
        );
        ledger.initialize(paths, "wf-revision-limit-attention", definition);
        var node = ledger.loadNode(paths, ledger.loadCurrent(paths).activeNodeInstanceIds().get(0));

        ledger.completeAndRoute(paths, definition, node.nodeInstanceId(), NodeStatus.COMPLETED,
                "NEEDS_REVISION",
                List.of(".agent/workstreams/ws-revision-limit-attention/runs/run-001/outputs/design-codex-review.md"),
                Map.of("summary", "Scope and test policy do not converge."));

        var current = ledger.loadCurrent(paths);
        var attention = WorkstreamAttentionEvaluator.evaluate(paths);
        var summary = Files.readString(paths.currentFailureMarkdown());

        assertEquals(WorkflowStatus.WAITING_FOR_RECOVERY, current.status());
        assertEquals(WorkstreamAttentionCategory.SEMANTIC_REVISION_LIMIT, attention.category());
        assertFalse(attention.retrySafe());
        assertTrue(attention.nextAction().contains("revise the task contract"));
        assertTrue(summary.contains("Attention Category: SEMANTIC_REVISION_LIMIT"));
        assertTrue(summary.contains("Retry Safe: false"));
        assertFalse(summary.contains("Fix the external issue"));
    }

    @Test
    void completedWorkflowNeverSuggestsGoalLoopRun() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-completed-with-goal-warning"));
        var ledger = new WorkflowFileLedger();
        var definition = new WorkflowDefinition(
                "wf-completed-with-goal-warning",
                1,
                "acceptance.decision",
                List.of(new NodeDefinition("acceptance.decision", NodeKind.DECISION, "review.decision", Map.of())),
                List.of()
        );
        ledger.initialize(paths, "wf-completed-with-goal-warning", definition);
        var node = ledger.loadNode(paths, ledger.loadCurrent(paths).activeNodeInstanceIds().get(0));

        ledger.completeAndRoute(paths, definition, node.nodeInstanceId(), NodeStatus.COMPLETED,
                "REJECT",
                List.of("runs/acceptance-review.md"),
                Map.of("decision", "REJECT", "summary", "Goal not satisfied."));

        var attention = WorkstreamAttentionEvaluator.evaluate(paths);

        assertEquals(WorkstreamAttentionCategory.COMPLETED, attention.category());
        assertEquals("warning", attention.severity());
        assertEquals("NEEDS_REPAIR", attention.domainOutcome());
        assertTrue(attention.command().contains("dualctl summary"));
        assertFalse(attention.command().contains("dualctl run"));
    }

    @Test
    void completedWorkflowWithAppliedDeliveryMergeSuggestsCommitNotMergeAgain() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-delivery-applied"));
        var ledger = new WorkflowFileLedger();
        var definition = new WorkflowDefinition(
                "wf-delivery-applied",
                1,
                "acceptance.decision",
                List.of(new NodeDefinition("acceptance.decision", NodeKind.DECISION, "review.decision", Map.of())),
                List.of()
        );
        ledger.initialize(paths, "wf-delivery-applied", definition);
        var node = ledger.loadNode(paths, ledger.loadCurrent(paths).activeNodeInstanceIds().get(0));

        ledger.completeAndRoute(paths, definition, node.nodeInstanceId(), NodeStatus.COMPLETED,
                "ACCEPT",
                List.of("runs/acceptance-review.md"),
                Map.of("decision", "ACCEPT", "summary", "Goal satisfied."));
        Files.createDirectories(paths.deliveryDir());
        Files.writeString(paths.deliveryMergePlanJson(), """
                {
                  "schemaVersion": 1,
                  "workstreamId": "ws-delivery-applied",
                  "sourceWorktree": "/tmp/source",
                  "targetRepo": "/tmp/target",
                  "acceptanceDecision": "ACCEPT",
                  "changedFiles": ["README.md"],
                  "excludedFiles": [],
                  "status": "APPLIED",
                  "commit": "",
                  "failureReason": ""
                }
                """);

        var attention = WorkstreamAttentionEvaluator.evaluate(paths);

        assertEquals(WorkstreamAttentionCategory.COMPLETED, attention.category());
        assertEquals("Delivery merge applied", attention.title());
        assertTrue(attention.nextAction().contains("Review the staged target diff"));
        assertTrue(attention.command().contains("git -C /tmp/target diff --cached"));
        assertFalse(attention.command().contains("delivery merge"));
    }

    private static WorkflowDefinition singleNodeDefinition() {
        return new WorkflowDefinition(
                "wf-single",
                1,
                "design.claude",
                List.of(new NodeDefinition("design.claude", NodeKind.TASK, "acpx.agent_run", Map.of())),
                List.of()
        );
    }
}
