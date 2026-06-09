package dev.dual.ledger.file;

import dev.dual.core.id.DualId;
import dev.dual.core.workflow.EdgeDefinition;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowFileLedgerTest {
    @TempDir
    Path tempDir;

    @Test
    void initializesWorkflowLedgerAndCurrentSnapshot() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-test"));
        Files.createDirectories(paths.workstreamDir());
        var definition = new WorkflowDefinition(
                "agent-lifecycle",
                1,
                "intake.claude_brainstorm",
                List.of(new NodeDefinition("intake.claude_brainstorm", NodeKind.TASK, "noop.task", Map.of())),
                List.of()
        );

        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-test", definition);

        assertTrue(Files.exists(paths.workflowDefinitionJson()));
        assertTrue(Files.exists(paths.workflowInstanceJson()));
        assertTrue(Files.exists(paths.workflowCurrentJson()));
        assertEquals(1, Files.list(paths.nodesDir()).count());
        assertTrue(Files.readString(paths.eventsJsonl()).contains("workflow_started"));

        var current = ledger.loadCurrent(paths);
        assertEquals(WorkflowStatus.RUNNING, current.status());
        assertEquals(List.of("node-intake-claude_brainstorm-001"), current.activeNodeInstanceIds());
        assertTrue(current.openGateIds().isEmpty());

        var node = ledger.loadNode(paths, current.activeNodeInstanceIds().get(0));
        assertEquals(NodeStatus.READY, node.status());
        assertEquals("intake.claude_brainstorm", node.nodeDefinitionId());
        assertEquals("iter-001", node.iterationId());
        assertTrue(Files.readString(paths.iterationJson("iter-001")).contains("\"triggerAction\" : \"workflow_started\""));
    }

    @Test
    void completesNodeAndActivatesNextNodeByOutcome() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-route"));
        Files.createDirectories(paths.workstreamDir());
        var definition = new WorkflowDefinition(
                "agent-lifecycle",
                1,
                "first",
                List.of(
                        new NodeDefinition("first", NodeKind.TASK, "noop.task", Map.of()),
                        new NodeDefinition("second", NodeKind.GATE, "human.review", Map.of())
                ),
                List.of(new EdgeDefinition("first", "completed", "second"))
        );

        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-route", definition);
        var current = ledger.loadCurrent(paths);
        var node = ledger.loadNode(paths, current.activeNodeInstanceIds().get(0));

        ledger.completeAndRoute(paths, definition, node.nodeInstanceId(), NodeStatus.COMPLETED, "completed", List.of("first-output.md"), Map.of("ok", true));

        var next = ledger.loadCurrent(paths);
        assertEquals(WorkflowStatus.WAITING_FOR_HUMAN, next.status());
        assertEquals(1, next.activeNodeInstanceIds().size());
        assertEquals(List.of("gate-" + next.activeNodeInstanceIds().get(0)), next.openGateIds());
        assertTrue(Files.readString(paths.eventsJsonl()).contains("node_routed"));

        var completed = ledger.loadNode(paths, node.nodeInstanceId());
        assertEquals(NodeStatus.COMPLETED, completed.status());
        assertEquals(List.of("first-output.md"), completed.outputRefs());
        assertEquals(Map.of("ok", true), completed.result());
        assertTrue(completed.finishedAt() != null);

        var nextNode = ledger.loadNode(paths, next.activeNodeInstanceIds().get(0));
        assertEquals("second", nextNode.nodeDefinitionId());
        assertEquals(NodeKind.GATE, nextNode.kind());
        assertEquals(NodeStatus.WAITING, nextNode.status());
        assertEquals("iter-001", nextNode.iterationId());
        assertEquals(List.of("first-output.md"), nextNode.inputRefs());
        assertTrue(Files.exists(paths.gateJson("gate-" + nextNode.nodeInstanceId())));
    }

    @Test
    void resolvingGatePersistsGateResolutionDocument() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-gate-resolution"));
        Files.createDirectories(paths.workstreamDir());
        var definition = new WorkflowDefinition(
                "agent-lifecycle",
                1,
                "first",
                List.of(
                        new NodeDefinition("first", NodeKind.TASK, "noop.task", Map.of()),
                        new NodeDefinition("gate.human_requirement_review", NodeKind.GATE, "human.review", Map.of(
                                "allowed_actions", List.of("approved", "revise")
                        )),
                        new NodeDefinition("contract.compile", NodeKind.TASK, "contract.compile", Map.of())
                ),
                List.of(
                        new EdgeDefinition("first", "completed", "gate.human_requirement_review"),
                        new EdgeDefinition("gate.human_requirement_review", "approved", "contract.compile")
                )
        );

        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-gate-resolution", definition);
        var first = ledger.loadNode(paths, ledger.loadCurrent(paths).activeNodeInstanceIds().get(0));
        ledger.completeAndRoute(paths, definition, first.nodeInstanceId(), NodeStatus.COMPLETED,
                "completed", List.of("first-output.md"), Map.of());

        var waiting = ledger.loadCurrent(paths);
        var gateId = waiting.openGateIds().get(0);
        ledger.resolveOpenGate(paths, definition, "approved", "mandatory review confirmed");

        var gateJson = Files.readString(paths.gateJson(gateId));
        assertTrue(gateJson.contains("\"status\" : \"resolved\""));
        assertTrue(gateJson.contains("\"human_action\" : \"approved\""));
        assertTrue(gateJson.contains("\"note\" : \"mandatory review confirmed\""));
        assertTrue(gateJson.contains("\"resolvedAt\""));
    }

    @Test
    void writesTimelineProjectionFromWorkflowAndNodes() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-timeline"));
        Files.createDirectories(paths.workstreamDir());
        var definition = new WorkflowDefinition(
                "agent-lifecycle",
                1,
                "first",
                List.of(
                        new NodeDefinition("first", NodeKind.TASK, "noop.task", Map.of()),
                        new NodeDefinition("second", NodeKind.GATE, "human.review", Map.of())
                ),
                List.of(new EdgeDefinition("first", "completed", "second"))
        );

        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-timeline", definition);
        writeRun(paths, "run-claude", "claude", "completed", 100, false);
        writeRun(paths, "run-codex", "codex", "failed", 300, true);
        var first = ledger.loadNode(paths, ledger.loadCurrent(paths).activeNodeInstanceIds().get(0));
        ledger.completeAndRoute(paths, definition, first.nodeInstanceId(), NodeStatus.COMPLETED,
                "completed", List.of("first-output.md"), Map.of("ok", true));

        assertTrue(Files.exists(paths.timelineJson()));
        var timeline = Files.readString(paths.timelineJson());
        assertTrue(timeline.contains("\"schemaVersion\""));
        assertTrue(timeline.contains("\"workstreamId\" : \"ws-timeline\""));
        assertTrue(timeline.contains("\"workflowStatus\" : \"WAITING_FOR_HUMAN\""));
        assertTrue(timeline.contains("\"activeNodeInstanceIds\""));
        assertTrue(timeline.contains("\"nodeDefinitionId\" : \"first\""));
        assertTrue(timeline.contains("\"status\" : \"COMPLETED\""));
        assertTrue(timeline.contains("\"nodeDefinitionId\" : \"second\""));
        assertTrue(timeline.contains("\"status\" : \"WAITING\""));
        assertTrue(timeline.contains("\"durationMs\""));

        assertTrue(Files.exists(paths.workstreamMetricsJson()));
        assertTrue(Files.exists(paths.workstreamMetricsMarkdown()));
        var metrics = Files.readString(paths.workstreamMetricsJson());
        assertTrue(metrics.contains("\"workstreamId\" : \"ws-timeline\""));
        assertTrue(metrics.contains("\"workflowStatus\" : \"WAITING_FOR_HUMAN\""));
        assertTrue(metrics.contains("\"nodeCount\" : 2"));
        assertTrue(metrics.contains("\"gateCount\" : 1"));
        assertTrue(metrics.contains("\"completedCount\" : 1"));
        assertTrue(metrics.contains("\"waitingCount\" : 1"));
        assertTrue(metrics.contains("\"totalNodeDurationMs\""));
        assertTrue(metrics.contains("\"longestNodes\""));
        assertTrue(metrics.contains("\"providerMetrics\""));
        assertTrue(metrics.contains("\"claude\""));
        assertTrue(metrics.contains("\"codex\""));
        assertTrue(metrics.contains("\"recoverableFailureCount\" : 1"));
        var metricsMarkdown = Files.readString(paths.workstreamMetricsMarkdown());
        assertTrue(metricsMarkdown.contains("# Workstream Metrics"));
        assertTrue(metricsMarkdown.contains("## Provider Metrics"));
        assertTrue(metricsMarkdown.contains("| claude | 1 | 1 | 0 | 0 | 100 | 100 |"));
        assertTrue(metricsMarkdown.contains("| codex | 1 | 0 | 1 | 1 | 300 | 300 |"));
    }

    private void writeRun(WorkstreamPaths paths, String runId, String agent, String status, long durationMs,
                          boolean recoverable) throws Exception {
        var runDir = paths.runsDir().resolve(runId);
        Files.createDirectories(runDir);
        Files.writeString(runDir.resolve("run.json"), """
                {
                  "schemaVersion": 1,
                  "runId": "%s",
                  "workstreamId": "%s",
                  "nodeId": "agent.node",
                  "agent": "%s",
                  "durationMs": %d,
                  "status": "%s",
                  "recoverable": %s
                }
                """.formatted(runId, paths.workstreamId().value(), agent, durationMs, status, recoverable));
    }

    @Test
    void revisionOutcomeStartsNextWorkflowIteration() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-revision"));
        Files.createDirectories(paths.workstreamDir());
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

        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-revision", definition);
        var current = ledger.loadCurrent(paths);
        var gate = ledger.loadNode(paths, current.activeNodeInstanceIds().get(0));

        ledger.completeAndRoute(paths, definition, gate.nodeInstanceId(), NodeStatus.COMPLETED, "revise", List.of(), Map.of(
                "human_action", "revise",
                "note", "Narrow the design before implementation."
        ));

        var routed = ledger.loadCurrent(paths);
        var next = ledger.loadNode(paths, routed.activeNodeInstanceIds().get(0));
        assertEquals("design.claude_design", next.nodeDefinitionId());
        assertEquals("iter-002", next.iterationId());
        assertTrue(Files.readString(paths.iterationJson("iter-002")).contains("\"parentIterationId\" : \"iter-001\""));
        assertTrue(Files.readString(paths.iterationJson("iter-002")).contains("\"triggerAction\" : \"revise\""));
        assertTrue(Files.readString(paths.iterationJson("iter-002")).contains("\"targetNodeDefinitionId\" : \"design.claude_design\""));
        assertTrue(Files.readString(paths.iterationJson("iter-002")).contains("Narrow the design before implementation."));
        assertTrue(Files.readString(paths.eventsJsonl()).contains("iteration_started"));
    }

    @Test
    void revisionOutcomeStopsAtRecoveryWhenRevisionLimitIsReached() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-revision-limit"));
        Files.createDirectories(paths.workstreamDir());
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

        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-revision-limit", definition);
        var firstGate = ledger.loadNode(paths, ledger.loadCurrent(paths).activeNodeInstanceIds().get(0));
        ledger.completeAndRoute(paths, definition, firstGate.nodeInstanceId(), NodeStatus.COMPLETED, "revise", List.of(), Map.of(
                "note", "First revision."
        ));
        var design = ledger.loadNode(paths, ledger.loadCurrent(paths).activeNodeInstanceIds().get(0));
        ledger.completeAndRoute(paths, definition, design.nodeInstanceId(), NodeStatus.COMPLETED, "completed", List.of(), Map.of());
        var secondGate = ledger.loadNode(paths, ledger.loadCurrent(paths).activeNodeInstanceIds().get(0));

        ledger.completeAndRoute(paths, definition, secondGate.nodeInstanceId(), NodeStatus.COMPLETED, "revise", List.of(), Map.of(
                "note", "Second revision should stop."
        ));

        var current = ledger.loadCurrent(paths);
        assertEquals(WorkflowStatus.WAITING_FOR_RECOVERY, current.status());
        assertEquals(List.of(secondGate.nodeInstanceId()), current.activeNodeInstanceIds());
        assertFalse(Files.exists(paths.iterationJson("iter-003")));
        var stoppedGate = ledger.loadNode(paths, secondGate.nodeInstanceId());
        assertEquals(NodeStatus.BLOCKED, stoppedGate.status());
        assertTrue(Files.exists(paths.currentFailureMarkdown()),
                "Revision-limit recovery should write current-failure.md for operator visibility");
        var summary = Files.readString(paths.currentFailureMarkdown());
        assertTrue(summary.contains("revision_limit_reached"));
        assertTrue(summary.contains(secondGate.nodeDefinitionId()));
        var events = Files.readString(paths.eventsJsonl());
        assertTrue(events.contains("revision_limit_reached"));
        assertTrue(events.contains("\"max_revisions\":1"));
    }

    @Test
    void acceptanceRejectRoutesToClassifierBeforeStartingRepairIteration() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-acceptance-reject"));
        Files.createDirectories(paths.workstreamDir());
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

        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-acceptance-reject", definition);
        var decision = ledger.loadNode(paths, ledger.loadCurrent(paths).activeNodeInstanceIds().get(0));

        ledger.completeAndRoute(paths, definition, decision.nodeInstanceId(), NodeStatus.COMPLETED, "REJECT", List.of("review.md"), Map.of(
                "decision", "REJECT",
                "summary", "README has a factual error."
        ));

        var current = ledger.loadCurrent(paths);
        assertEquals(WorkflowStatus.RUNNING, current.status());
        var classifier = ledger.loadNode(paths, current.activeNodeInstanceIds().get(0));
        assertEquals("acceptance.classify_failure", classifier.nodeDefinitionId());
        assertEquals("iter-001", classifier.iterationId());
        assertFalse(Files.exists(paths.iterationJson("iter-002")));

        ledger.completeAndRoute(paths, definition, classifier.nodeInstanceId(), NodeStatus.COMPLETED,
                "REJECT_IMPLEMENTATION_BUG", List.of("failure-classification.json"), Map.of(
                        "category", "IMPLEMENTATION_BUG"
                ));

        current = ledger.loadCurrent(paths);
        var repair = ledger.loadNode(paths, current.activeNodeInstanceIds().get(0));
        assertEquals("context.implement_claude", repair.nodeDefinitionId());
        assertEquals("iter-002", repair.iterationId());
        assertTrue(Files.exists(paths.iterationJson("iter-002")));
    }

    @Test
    void nextStageApprovalStartsNextWorkflowIterationAndCarriesPrompt() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-next-stage"));
        Files.createDirectories(paths.workstreamDir());
        var definition = new WorkflowDefinition(
                "agent-lifecycle",
                1,
                "stage.generate_next_prompt",
                List.of(
                        new NodeDefinition("stage.generate_next_prompt", NodeKind.TASK, "prompt.generate_next_stage", Map.of()),
                        new NodeDefinition("gate.human_next_stage_approval", NodeKind.GATE, "human.review", Map.of("max_revisions", 0)),
                        new NodeDefinition("intake.claude_brainstorm", NodeKind.TASK, "noop.task", Map.of())
                ),
                List.of(
                        new EdgeDefinition("stage.generate_next_prompt", "completed", "gate.human_next_stage_approval"),
                        new EdgeDefinition("gate.human_next_stage_approval", "approved", "intake.claude_brainstorm")
                )
        );

        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-next-stage", definition);
        var current = ledger.loadCurrent(paths);
        var generator = ledger.loadNode(paths, current.activeNodeInstanceIds().get(0));
        var nextPrompt = ".agent/workstreams/ws-next-stage/artifacts/next-stage/next-stage-prompt.md";
        ledger.completeAndRoute(paths, definition, generator.nodeInstanceId(), NodeStatus.COMPLETED, "completed", List.of(nextPrompt), Map.of(
                "prompt_path", nextPrompt
        ));

        ledger.resolveOpenGate(paths, definition, "approved", "continue to next stage");

        var routed = ledger.loadCurrent(paths);
        var next = ledger.loadNode(paths, routed.activeNodeInstanceIds().get(0));
        assertEquals("intake.claude_brainstorm", next.nodeDefinitionId());
        assertEquals("iter-002", next.iterationId());
        assertEquals(List.of(nextPrompt), next.inputRefs());
        assertTrue(Files.readString(paths.iterationJson("iter-002")).contains("\"triggerAction\" : \"approved\""));
        assertTrue(Files.readString(paths.iterationJson("iter-002")).contains("\"targetNodeDefinitionId\" : \"intake.claude_brainstorm\""));
    }

    @Test
    void rejectDecisionStartsNextWorkflowIteration() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-reject"));
        Files.createDirectories(paths.workstreamDir());
        var definition = new WorkflowDefinition(
                "agent-lifecycle",
                1,
                "acceptance.decision",
                List.of(
                        new NodeDefinition("acceptance.decision", NodeKind.DECISION, "review.decision", Map.of()),
                        new NodeDefinition("context.implement_claude", NodeKind.TASK, "context.build_package", Map.of())
                ),
                List.of(new EdgeDefinition("acceptance.decision", "REJECT", "context.implement_claude"))
        );

        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-reject", definition);
        var current = ledger.loadCurrent(paths);
        var decision = ledger.loadNode(paths, current.activeNodeInstanceIds().get(0));

        ledger.completeAndRoute(paths, definition, decision.nodeInstanceId(), NodeStatus.COMPLETED, "REJECT", List.of(), Map.of(
                "decision", "REJECT",
                "summary", "Acceptance evidence is incomplete."
        ));

        var routed = ledger.loadCurrent(paths);
        var next = ledger.loadNode(paths, routed.activeNodeInstanceIds().get(0));
        assertEquals("context.implement_claude", next.nodeDefinitionId());
        assertEquals("iter-002", next.iterationId());
        assertTrue(Files.readString(paths.iterationJson("iter-002")).contains("\"triggerAction\" : \"REJECT\""));
        assertTrue(Files.readString(paths.iterationJson("iter-002")).contains("Acceptance evidence is incomplete."));
    }

    @Test
    void completesWorkflowWhenOutcomeHasNoEdge() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-complete"));
        Files.createDirectories(paths.workstreamDir());
        var definition = new WorkflowDefinition(
                "agent-lifecycle",
                1,
                "only",
                List.of(new NodeDefinition("only", NodeKind.TASK, "noop.task", Map.of())),
                List.of()
        );

        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-complete", definition);
        var current = ledger.loadCurrent(paths);
        var node = ledger.loadNode(paths, current.activeNodeInstanceIds().get(0));

        ledger.completeAndRoute(paths, definition, node.nodeInstanceId(), NodeStatus.COMPLETED, "completed", List.of(), Map.of());

        var completed = ledger.loadCurrent(paths);
        assertEquals(WorkflowStatus.COMPLETED, completed.status());
        assertTrue(completed.activeNodeInstanceIds().isEmpty());
        assertTrue(completed.openGateIds().isEmpty());
        assertTrue(Files.readString(paths.eventsJsonl()).contains("workflow_completed"));
        assertTrue(Files.readString(paths.eventsJsonl()).contains(completed.lastEventId()));
    }

    @Test
    void allowedGateActionWithoutEdgeCompletesWorkflow() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-gate-complete"));
        Files.createDirectories(paths.workstreamDir());
        var definition = new WorkflowDefinition(
                "agent-lifecycle",
                1,
                "gate.human_next_stage_approval",
                List.of(new NodeDefinition("gate.human_next_stage_approval", NodeKind.GATE, "human.review", Map.of(
                        "allowed_actions", List.of("approve", "revise", "complete")
                ))),
                List.of()
        );

        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-gate-complete", definition);

        ledger.resolveOpenGate(paths, definition, "complete", "all stages accepted");

        var completed = ledger.loadCurrent(paths);
        assertEquals(WorkflowStatus.COMPLETED, completed.status());
        assertTrue(completed.activeNodeInstanceIds().isEmpty());
        assertTrue(Files.readString(paths.eventsJsonl()).contains("workflow_completed"));
        var node = ledger.loadNode(paths, "node-gate-human_next_stage_approval-001");
        assertEquals("complete", node.result().get("human_action"));
    }

    @Test
    void gateDocumentUsesConfiguredAllowedActions() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-gate-actions"));
        Files.createDirectories(paths.workstreamDir());
        var definition = new WorkflowDefinition(
                "agent-lifecycle",
                1,
                "first",
                List.of(
                        new NodeDefinition("first", NodeKind.TASK, "noop.task", Map.of()),
                        new NodeDefinition("gate.human_next_stage_approval", NodeKind.GATE, "human.review", Map.of(
                                "allowed_actions", List.of("approve", "revise", "complete")
                        ))
                ),
                List.of(new EdgeDefinition("first", "completed", "gate.human_next_stage_approval"))
        );

        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-gate-actions", definition);
        var current = ledger.loadCurrent(paths);
        var first = ledger.loadNode(paths, current.activeNodeInstanceIds().get(0));

        ledger.completeAndRoute(paths, definition, first.nodeInstanceId(), NodeStatus.COMPLETED, "completed", List.of(), Map.of());

        var routed = ledger.loadCurrent(paths);
        var gateJson = Files.readString(paths.gateJson(routed.openGateIds().get(0)));
        assertTrue(gateJson.contains("\"complete\""));
    }

    @Test
    void failedNodeStopsWorkflowWithoutRoutingNextNode() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-failed"));
        Files.createDirectories(paths.workstreamDir());
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

        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-failed", definition);
        var current = ledger.loadCurrent(paths);
        var node = ledger.loadNode(paths, current.activeNodeInstanceIds().get(0));

        ledger.completeAndRoute(paths, definition, node.nodeInstanceId(), NodeStatus.FAILED, "completed", List.of(), Map.of("exit_code", 4));

        var stopped = ledger.loadCurrent(paths);
        assertEquals(WorkflowStatus.WAITING_FOR_RECOVERY, stopped.status());
        assertEquals(List.of(node.nodeInstanceId()), stopped.activeNodeInstanceIds());
        assertEquals(List.of("recovery-" + node.nodeInstanceId()), stopped.openGateIds());
        assertTrue(Files.exists(paths.gateJson("recovery-" + node.nodeInstanceId())));
        assertFalse(Files.exists(paths.nodeJson("node-second-002")));
        assertTrue(Files.readString(paths.eventsJsonl()).contains("node_failed"));
        assertTrue(Files.readString(paths.eventsJsonl()).contains("recovery_gate_id"));
        assertTrue(Files.readString(paths.eventsJsonl()).contains(stopped.lastEventId()));
    }

    @Test
    void failedNodeWritesCurrentFailureSummary() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-failure-summary"));
        Files.createDirectories(paths.workstreamDir());
        var definition = new WorkflowDefinition(
                "agent-lifecycle",
                1,
                "design.claude",
                List.of(new NodeDefinition("design.claude", NodeKind.TASK, "agent.acpx", Map.of())),
                List.of()
        );

        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-failure-summary", definition);
        var current = ledger.loadCurrent(paths);
        var node = ledger.loadNode(paths, current.activeNodeInstanceIds().get(0));

        ledger.completeAndRoute(paths, definition, node.nodeInstanceId(), NodeStatus.FAILED,
                "RECOVERABLE_RATE_LIMIT",
                List.of(".agent/workstreams/ws-failure-summary/runs/run-001/outputs/design.stderr.log"),
                Map.of(
                        "run_id", "run-001",
                        "exit_code", 4,
                        "agent", "claude",
                        "session", "claude-main",
                        "acpx_failure.reason", "RECOVERABLE_RATE_LIMIT",
                        "acpx_failure.recoverable", true
                ));

        var summary = Files.readString(paths.currentFailureMarkdown());
        assertTrue(summary.contains("# Current Failure"));
        assertTrue(summary.contains("design.claude"));
        assertTrue(summary.contains(node.nodeInstanceId()));
        assertTrue(summary.contains("RECOVERABLE_RATE_LIMIT"));
        assertTrue(summary.contains("design.stderr.log"));
        assertTrue(summary.contains("dualctl retry --workdir " + tempDir.toAbsolutePath().normalize()));
        assertFalse(summary.contains("--workstream-id"));
    }

    @Test
    void failedNodeCurrentFailureSummaryHighlightsContractDiagnostic() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-contract-failure-summary"));
        Files.createDirectories(paths.workstreamDir());
        var definition = new WorkflowDefinition(
                "agent-lifecycle",
                1,
                "acceptance.codex",
                List.of(new NodeDefinition("acceptance.codex", NodeKind.TASK, "acpx.agent_run", Map.of())),
                List.of()
        );

        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-contract-failure-summary", definition);
        var current = ledger.loadCurrent(paths);
        var node = ledger.loadNode(paths, current.activeNodeInstanceIds().get(0));

        ledger.completeAndRoute(paths, definition, node.nodeInstanceId(), NodeStatus.FAILED,
                "AGENT_OUTPUT_CONTRACT_VIOLATION",
                List.of(
                        ".agent/workstreams/ws-contract-failure-summary/artifacts/contract-failures/"
                                + node.nodeInstanceId() + ".md"
                ),
                Map.of(
                        "reason", "AGENT_OUTPUT_CONTRACT_VIOLATION",
                        "contract_failure_diagnostic_ref",
                        ".agent/workstreams/ws-contract-failure-summary/artifacts/contract-failures/"
                                + node.nodeInstanceId() + ".json",
                        "contract_failure_diagnostic_markdown_ref",
                        ".agent/workstreams/ws-contract-failure-summary/artifacts/contract-failures/"
                                + node.nodeInstanceId() + ".md",
                        "parse_error", Map.of(
                                "code", "STRUCTURED_ARTIFACT_MISSING",
                                "message", "Required structured artifact missing: acceptance_review",
                                "details", Map.of("required_artifact_type", "acceptance_review")
                        )
                ));

        var summary = Files.readString(paths.currentFailureMarkdown());
        assertTrue(summary.contains("## Contract Failure Diagnostic"));
        assertTrue(summary.contains("STRUCTURED_ARTIFACT_MISSING"));
        assertTrue(summary.contains("Required structured artifact missing: acceptance_review"));
        assertTrue(summary.contains("required_artifact_type: acceptance_review"));
        assertTrue(summary.contains(node.nodeInstanceId() + ".json"));
        assertTrue(summary.contains(node.nodeInstanceId() + ".md"));
    }

    @Test
    void invalidControlOutputOpensControlRepairGate() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-control-repair"));
        Files.createDirectories(paths.workstreamDir());
        var definition = new WorkflowDefinition(
                "agent-lifecycle",
                1,
                "design.decision",
                List.of(new NodeDefinition("design.decision", NodeKind.DECISION, "review.decision", Map.of())),
                List.of()
        );

        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-control-repair", definition);
        var current = ledger.loadCurrent(paths);
        var node = ledger.loadNode(paths, current.activeNodeInstanceIds().get(0));

        ledger.completeAndRoute(paths, definition, node.nodeInstanceId(), NodeStatus.FAILED, "INVALID_DUAL_CONTROL", List.of(), Map.of(
                "reason", "INVALID_DUAL_CONTROL",
                "message", "dual-control missing required key: decision",
                "input_refs", List.of("runs/run-1/outputs/design-codex-review.md")
        ));

        var stopped = ledger.loadCurrent(paths);
        assertEquals(WorkflowStatus.WAITING_FOR_RECOVERY, stopped.status());
        assertEquals(List.of("recovery-" + node.nodeInstanceId()), stopped.openGateIds());
        var gateJson = Files.readString(paths.gateJson(stopped.openGateIds().get(0)));
        assertTrue(gateJson.contains("\"gateType\" : \"control.repair\""));
        assertTrue(gateJson.contains("\"reason\" : \"INVALID_DUAL_CONTROL\""));
        assertTrue(gateJson.contains("dual-control missing required key"));
        var events = Files.readString(paths.eventsJsonl());
        assertTrue(events.contains("control_repair_required"));
        assertTrue(events.contains("runs/run-1/outputs/design-codex-review.md"));
    }

    @Test
    void retryActiveFailedNodeSetsItReadyAgain() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-retry"));
        Files.createDirectories(paths.workstreamDir());
        var definition = new WorkflowDefinition(
                "agent-lifecycle",
                1,
                "first",
                List.of(new NodeDefinition("first", NodeKind.TASK, "noop.task", Map.of())),
                List.of()
        );
        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-retry", definition);
        var current = ledger.loadCurrent(paths);
        var node = ledger.loadNode(paths, current.activeNodeInstanceIds().get(0));
        ledger.completeAndRoute(paths, definition, node.nodeInstanceId(), NodeStatus.FAILED, "RECOVERABLE_AGENT_RECONNECT", List.of("stderr.log"), Map.of("recoverable", true));

        ledger.retryActiveNode(paths, "session reconnected");

        var retryCurrent = ledger.loadCurrent(paths);
        var retryNode = ledger.loadNode(paths, retryCurrent.activeNodeInstanceIds().get(0));
        assertEquals(WorkflowStatus.RUNNING, retryCurrent.status());
        assertEquals(NodeStatus.READY, retryNode.status());
        assertTrue(retryCurrent.openGateIds().isEmpty());
        assertEquals("iter-001", retryNode.iterationId());
        assertFalse(Files.exists(paths.iterationJson("iter-002")));
        assertTrue(retryNode.outputRefs().isEmpty());
        assertEquals("FAILED", retryNode.result().get("retry_of_status"));
        assertTrue(Files.readString(paths.eventsJsonl()).contains("node_retry_requested"));
    }

    @Test
    void abortRecoveryMarksWorkflowFailedAndClearsActiveNodes() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-abort"));
        Files.createDirectories(paths.workstreamDir());
        var definition = new WorkflowDefinition(
                "agent-lifecycle",
                1,
                "first",
                List.of(new NodeDefinition("first", NodeKind.TASK, "noop.task", Map.of())),
                List.of()
        );
        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-abort", definition);
        var current = ledger.loadCurrent(paths);
        var node = ledger.loadNode(paths, current.activeNodeInstanceIds().get(0));
        ledger.completeAndRoute(paths, definition, node.nodeInstanceId(), NodeStatus.FAILED, "INVALID_DUAL_CONTROL", List.of(), Map.of());

        ledger.abortRecovery(paths, "not worth continuing");

        var aborted = ledger.loadCurrent(paths);
        assertEquals(WorkflowStatus.FAILED, aborted.status());
        assertTrue(aborted.activeNodeInstanceIds().isEmpty());
        assertTrue(aborted.openGateIds().isEmpty());
        var events = Files.readString(paths.eventsJsonl());
        assertTrue(events.contains("workflow_aborted"));
        assertTrue(events.contains("not worth continuing"));
    }

    @Test
    void abortRecoveryRejectsNonRecoveryWorkflow() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-abort-ready"));
        Files.createDirectories(paths.workstreamDir());
        var definition = new WorkflowDefinition(
                "agent-lifecycle",
                1,
                "first",
                List.of(new NodeDefinition("first", NodeKind.TASK, "noop.task", Map.of())),
                List.of()
        );
        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-abort-ready", definition);

        var error = assertThrows(java.io.IOException.class, () -> ledger.abortRecovery(paths, "not in recovery"));

        assertTrue(error.getMessage().contains("not waiting for recovery"));
    }

    @Test
    void retryRejectsNonRetryableActiveNode() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-retry-ready"));
        Files.createDirectories(paths.workstreamDir());
        var definition = new WorkflowDefinition(
                "agent-lifecycle",
                1,
                "first",
                List.of(new NodeDefinition("first", NodeKind.TASK, "noop.task", Map.of())),
                List.of()
        );
        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-retry-ready", definition);

        var error = assertThrows(java.io.IOException.class, () -> ledger.retryActiveNode(paths, "not failed"));

        assertTrue(error.getMessage().contains("not retryable"));
    }

    @Test
    void blockedNodeStopsWorkflowWithoutRoutingNextNode() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-blocked"));
        Files.createDirectories(paths.workstreamDir());
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

        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-blocked", definition);
        var current = ledger.loadCurrent(paths);
        var node = ledger.loadNode(paths, current.activeNodeInstanceIds().get(0));

        ledger.completeAndRoute(paths, definition, node.nodeInstanceId(), NodeStatus.BLOCKED, "completed", List.of(), Map.of());

        var stopped = ledger.loadCurrent(paths);
        assertEquals(WorkflowStatus.WAITING_FOR_RECOVERY, stopped.status());
        assertEquals(List.of(node.nodeInstanceId()), stopped.activeNodeInstanceIds());
        assertEquals(List.of("recovery-" + node.nodeInstanceId()), stopped.openGateIds());
        assertFalse(Files.exists(paths.nodeJson("node-second-002")));
        assertTrue(Files.readString(paths.eventsJsonl()).contains("node_blocked"));
        assertTrue(Files.readString(paths.eventsJsonl()).contains(stopped.lastEventId()));
    }

    @Test
    void rejectsDuplicateInitialization() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-duplicate"));
        Files.createDirectories(paths.workstreamDir());
        var definition = new WorkflowDefinition(
                "agent-lifecycle",
                1,
                "first",
                List.of(new NodeDefinition("first", NodeKind.TASK, "noop.task", Map.of())),
                List.of()
        );

        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-duplicate", definition);

        var error = assertThrows(java.io.IOException.class, () -> ledger.initialize(paths, "wf-duplicate", definition));
        assertTrue(error.getMessage().contains("already initialized"));
        var events = Files.readString(paths.eventsJsonl());
        assertEquals(events.indexOf("evt-000001"), events.lastIndexOf("evt-000001"));
    }

    @Test
    void documentRecordsRejectInvalidIdentityAndStatus() {
        assertThrows(IllegalArgumentException.class, () -> new WorkflowCurrentDocument(
                0,
                "ws",
                "wf",
                "agent-lifecycle",
                1,
                WorkflowStatus.RUNNING,
                List.of("node"),
                List.of(),
                "evt-000001",
                java.time.Instant.now()
        ));
        assertThrows(IllegalArgumentException.class, () -> new WorkflowInstanceDocument(
                1,
                "wf",
                "agent-lifecycle",
                1,
                null,
                List.of("node"),
                "evt-000001",
                java.time.Instant.now(),
                java.time.Instant.now()
        ));
        assertThrows(IllegalArgumentException.class, () -> new NodeInstanceDocument(
                1,
                "node",
                "first",
                NodeKind.TASK,
                "noop.task",
                NodeStatus.READY,
                "",
                "iter-001",
                List.of(),
                List.of(),
                Map.of(),
                null,
                null,
                null
        ));
        assertThrows(IllegalArgumentException.class, () -> new GateDocument(
                1,
                "gate",
                "node",
                "human.review",
                "open",
                List.of("approve"),
                null,
                null,
                Map.of()
        ));
    }
}
