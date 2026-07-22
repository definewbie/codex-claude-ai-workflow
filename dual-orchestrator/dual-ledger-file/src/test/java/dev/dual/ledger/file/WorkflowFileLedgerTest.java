package dev.dual.ledger.file;

import dev.dual.core.id.DualId;
import dev.dual.core.workflow.EdgeDefinition;
import dev.dual.core.workflow.EdgeCondition;
import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeStatus;
import dev.dual.core.workflow.TransitionMetadata;
import dev.dual.core.workflow.WorkflowDefinition;
import dev.dual.core.workflow.WorkflowStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
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
        assertTrue(gateJson.contains("\"subject_input_refs\" : [ \"first-output.md\" ]"));
        assertTrue(gateJson.contains("\"resolvedAt\""));
    }

    @Test
    void resolveOpenGateNormalizesApproveToApprovedWhenWorkflowUsesApprovedOutcome() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-gate-approve-normalized"));
        Files.createDirectories(paths.workstreamDir());
        var definition = new WorkflowDefinition(
                "agent-lifecycle",
                1,
                "first",
                List.of(
                        new NodeDefinition("first", NodeKind.TASK, "noop.task", Map.of()),
                        new NodeDefinition("gate.human_requirement_review", NodeKind.GATE, "human.review", Map.of(
                                "allowed_actions", List.of("approve", "revise")
                        )),
                        new NodeDefinition("contract.compile", NodeKind.TASK, "contract.compile", Map.of())
                ),
                List.of(
                        new EdgeDefinition("first", "completed", "gate.human_requirement_review"),
                        new EdgeDefinition("gate.human_requirement_review", "approved", "contract.compile")
                )
        );

        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-gate-approve-normalized", definition);
        var first = ledger.loadNode(paths, ledger.loadCurrent(paths).activeNodeInstanceIds().get(0));
        ledger.completeAndRoute(paths, definition, first.nodeInstanceId(), NodeStatus.COMPLETED,
                "completed", List.of("first-output.md"), Map.of());

        ledger.resolveOpenGate(paths, definition, "approve", "human clicked approve");

        var current = ledger.loadCurrent(paths);
        var next = ledger.loadNode(paths, current.activeNodeInstanceIds().get(0));
        assertEquals("contract.compile", next.nodeDefinitionId());

        var completedGate = Files.readString(paths.nodeJson("node-gate-human_requirement_review-002"));
        assertTrue(completedGate.contains("\"human_action\" : \"approved\""));
        assertTrue(completedGate.contains("\"requested_action\" : \"approve\""));
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
    void loopTransitionMetadataStartsNextWorkflowIteration() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-loop-metadata"));
        Files.createDirectories(paths.workstreamDir());
        var definition = new WorkflowDefinition(
                "agent-lifecycle",
                1,
                "review.decision",
                List.of(
                        new NodeDefinition("review.decision", NodeKind.DECISION, "noop.decision", Map.of()),
                        new NodeDefinition("design.claude_design", NodeKind.TASK, "noop.task", Map.of())
                ),
                List.of(new EdgeDefinition("review.decision", "BACK_TO_DESIGN", "design.claude_design",
                        EdgeCondition.unconditional(),
                        TransitionMetadata.loop("review requested design changes")))
        );

        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-loop-metadata", definition);
        var decision = ledger.loadNode(paths, ledger.loadCurrent(paths).activeNodeInstanceIds().get(0));

        ledger.completeAndRoute(paths, definition, decision.nodeInstanceId(), NodeStatus.COMPLETED,
                "BACK_TO_DESIGN", List.of("review.md"), Map.of("reason", "missing architecture constraints"));

        var next = ledger.loadNode(paths, ledger.loadCurrent(paths).activeNodeInstanceIds().get(0));
        assertEquals("iter-002", next.iterationId());
        assertTrue(next.inputTransition().toString().contains("transition_type=loop"));
        var iteration = Files.readString(paths.iterationJson("iter-002"));
        assertTrue(iteration.contains("\"triggerAction\" : \"BACK_TO_DESIGN\""));
        assertTrue(Files.readString(paths.eventsJsonl()).contains("review requested design changes"));
    }

    @Test
    void acceptanceRejectClassificationRoutesToRepairIteration() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-acceptance-repair-loop"));
        Files.createDirectories(paths.workstreamDir());
        var definition = new WorkflowDefinition(
                "agent-lifecycle-full",
                3,
                "acceptance.classify_failure",
                List.of(
                        new NodeDefinition("acceptance.classify_failure", NodeKind.TASK,
                                "acceptance.failure_classify", Map.of()),
                        new NodeDefinition("context.implement_claude", NodeKind.TASK,
                                "context.build_package", Map.of())
                ),
                List.of(new EdgeDefinition(
                        "acceptance.classify_failure",
                        "REJECT_IMPLEMENTATION_BUG",
                        "context.implement_claude",
                        EdgeCondition.unconditional(),
                        TransitionMetadata.loop("Acceptance rejected because implementation repair is required")
                ))
        );

        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-acceptance-repair-loop", definition);
        var classify = ledger.loadNode(paths, ledger.loadCurrent(paths).activeNodeInstanceIds().get(0));

        ledger.completeAndRoute(paths, definition, classify.nodeInstanceId(), NodeStatus.COMPLETED,
                "REJECT_IMPLEMENTATION_BUG",
                List.of(".agent/workstreams/ws-acceptance-repair-loop/artifacts/failure-classification.json"),
                Map.of(
                        "review_decision", "REJECT",
                        "category", "IMPLEMENTATION_BUG",
                        "reasoning", "Verifier found a concrete acceptance failure."
                ));

        var current = ledger.loadCurrent(paths);
        var repair = ledger.loadNode(paths, current.activeNodeInstanceIds().get(0));
        var goal = Files.readString(paths.goalSatisfactionJson());
        var events = Files.readString(paths.eventsJsonl());

        assertEquals(WorkflowStatus.RUNNING, current.status());
        assertEquals("context.implement_claude", repair.nodeDefinitionId());
        assertEquals("iter-002", repair.iterationId());
        assertEquals("loop", repair.inputTransition().get("transition_type"));
        assertTrue(repair.inputTransition().get("loop_reason").toString().contains("implementation repair"));
        assertTrue(goal.contains("\"domainOutcome\" : \"NEEDS_REPAIR\""));
        assertTrue(events.contains("iteration_started"));
        assertTrue(events.contains("REJECT_IMPLEMENTATION_BUG"));
        assertFalse(current.activeNodeInstanceIds().isEmpty(), "semantic repair must not complete the workflow");
    }

    @Test
    void loopTransitionMetadataRespectsRevisionLimit() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-loop-limit"));
        Files.createDirectories(paths.workstreamDir());
        var definition = new WorkflowDefinition(
                "agent-lifecycle",
                1,
                "review.decision",
                List.of(
                        new NodeDefinition("review.decision", NodeKind.DECISION, "noop.decision", Map.of(
                                "max_revisions", 1
                        )),
                        new NodeDefinition("design.claude_design", NodeKind.TASK, "noop.task", Map.of())
                ),
                List.of(new EdgeDefinition("review.decision", "BACK_TO_DESIGN", "design.claude_design",
                        EdgeCondition.unconditional(),
                        TransitionMetadata.loop("review requested design changes")))
        );

        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-loop-limit", definition);
        var firstDecision = ledger.loadNode(paths, ledger.loadCurrent(paths).activeNodeInstanceIds().get(0));
        ledger.completeAndRoute(paths, definition, firstDecision.nodeInstanceId(), NodeStatus.COMPLETED,
                "BACK_TO_DESIGN", List.of("review-1.md"), Map.of());
        assertEquals("iter-002", ledger.loadNode(paths, ledger.loadCurrent(paths).activeNodeInstanceIds().get(0)).iterationId());

        var secondDecision = new NodeInstanceDocument(
                1,
                "node-review-decision-999",
                "review.decision",
                NodeKind.DECISION,
                "noop.decision",
                NodeStatus.READY,
                "stage-review",
                "iter-002",
                List.of(),
                Map.of(),
                List.of(),
                Map.of(),
                "",
                "",
                null,
                null
        );
        Files.write(paths.nodeJson(secondDecision.nodeInstanceId()), new com.fasterxml.jackson.databind.ObjectMapper()
                .findAndRegisterModules()
                .writerWithDefaultPrettyPrinter()
                .writeValueAsBytes(secondDecision));
        var current = ledger.loadCurrent(paths);
        var injectedCurrent = new WorkflowCurrentDocument(
                current.schemaVersion(),
                current.workstreamId(),
                current.workflowInstanceId(),
                current.workflowId(),
                current.definitionVersion(),
                WorkflowStatus.RUNNING,
                List.of(secondDecision.nodeInstanceId()),
                List.of(),
                current.lastEventId(),
                current.updatedAt()
        );
        Files.write(paths.workflowCurrentJson(), new com.fasterxml.jackson.databind.ObjectMapper()
                .findAndRegisterModules()
                .writerWithDefaultPrettyPrinter()
                .writeValueAsBytes(injectedCurrent));

        ledger.completeAndRoute(paths, definition, secondDecision.nodeInstanceId(), NodeStatus.COMPLETED,
                "BACK_TO_DESIGN", List.of("review-2.md"), Map.of());

        var stopped = ledger.loadCurrent(paths);
        assertEquals(WorkflowStatus.WAITING_FOR_RECOVERY, stopped.status());
        assertTrue(Files.readString(paths.eventsJsonl()).contains("revision_limit_reached"));
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
                        "review_decision", "REJECT",
                        "category", "IMPLEMENTATION_BUG"
                ));

        var timeline = Files.readString(paths.timelineJson());
        assertTrue(timeline.contains("\"displayName\" : \"acceptance.classify_failure\""));

        current = ledger.loadCurrent(paths);
        var repair = ledger.loadNode(paths, current.activeNodeInstanceIds().get(0));
        assertEquals("context.implement_claude", repair.nodeDefinitionId());
        assertEquals("iter-002", repair.iterationId());
        assertTrue(Files.exists(paths.iterationJson("iter-002")));
    }

    @Test
    void acceptanceWithNotesDisplaysClassifierAsNotesClassification() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-acceptance-notes"));
        Files.createDirectories(paths.workstreamDir());
        var definition = new WorkflowDefinition(
                "agent-lifecycle-full",
                3,
                "acceptance.decision",
                List.of(
                        new NodeDefinition("acceptance.decision", NodeKind.DECISION, "review.decision", Map.of()),
                        new NodeDefinition("acceptance.classify_failure", NodeKind.TASK, "acceptance.failure_classify", Map.of()),
                        new NodeDefinition("acceptance.generate_final_report", NodeKind.TASK, "report.generate_final", Map.of())
                ),
                List.of(
                        new EdgeDefinition("acceptance.decision", "ACCEPT_WITH_NOTES", "acceptance.classify_failure"),
                        new EdgeDefinition("acceptance.classify_failure", "NOTED_REVIEW_NOISE", "acceptance.generate_final_report")
                )
        );

        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-acceptance-notes", definition);
        var decision = ledger.loadNode(paths, ledger.loadCurrent(paths).activeNodeInstanceIds().get(0));

        ledger.completeAndRoute(paths, definition, decision.nodeInstanceId(), NodeStatus.COMPLETED,
                "ACCEPT_WITH_NOTES", List.of("review.md"), Map.of(
                        "decision", "ACCEPT_WITH_NOTES",
                        "summary", "Accepted with non-blocking notes."
                ));

        var classifier = ledger.loadNode(paths, ledger.loadCurrent(paths).activeNodeInstanceIds().get(0));
        ledger.completeAndRoute(paths, definition, classifier.nodeInstanceId(), NodeStatus.COMPLETED,
                "NOTED_REVIEW_NOISE", List.of("failure-classification.json"), Map.of(
                        "review_decision", "ACCEPT_WITH_NOTES",
                        "category", "REVIEW_NOISE"
                ));

        var timeline = Files.readString(paths.timelineJson());
        assertTrue(timeline.contains("\"nodeDefinitionId\" : \"acceptance.classify_failure\""));
        assertTrue(timeline.contains("\"displayName\" : \"acceptance.classify_notes\""));
    }

    @Test
    void finalNextStageGateRejectsApproveAndCompleteEndsWorkflow() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-next-stage"));
        Files.createDirectories(paths.workstreamDir());
        var definition = new WorkflowDefinition(
                "agent-lifecycle",
                1,
                "stage.generate_next_prompt",
                List.of(
                        new NodeDefinition("stage.generate_next_prompt", NodeKind.TASK, "prompt.generate_next_stage", Map.of()),
                        new NodeDefinition("gate.human_next_stage_approval", NodeKind.GATE, "human.review", Map.of(
                                "allowed_actions", List.of("revise", "complete"),
                                "max_revisions", 1
                        )),
                        new NodeDefinition("intake.claude_brainstorm", NodeKind.TASK, "noop.task", Map.of())
                ),
                List.of(
                        new EdgeDefinition("stage.generate_next_prompt", "completed", "gate.human_next_stage_approval")
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

        var error = assertThrows(IOException.class,
                () -> ledger.resolveOpenGate(paths, definition, "approved", "continue to next stage"));
        assertTrue(error.getMessage().contains("final next-stage gate cannot be approved"));

        ledger.resolveOpenGate(paths, definition, "complete", "finish workstream");

        var routed = ledger.loadCurrent(paths);
        assertEquals(WorkflowStatus.COMPLETED, routed.status());
        assertTrue(routed.activeNodeInstanceIds().isEmpty());
        assertTrue(routed.openGateIds().isEmpty());
        assertFalse(Files.exists(paths.iterationJson("iter-002")));
    }

    @Test
    void closesMistakenIterationStartedByHistoricalFinalGateApproval() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-close-mistaken"));
        Files.createDirectories(paths.workstreamDir());
        var definition = new WorkflowDefinition(
                "agent-lifecycle",
                1,
                "gate.human_next_stage_approval",
                List.of(
                        new NodeDefinition("gate.human_next_stage_approval", NodeKind.GATE, "human.review", Map.of("max_revisions", 1)),
                        new NodeDefinition("intake.claude_brainstorm", NodeKind.TASK, "noop.task", Map.of()),
                        new NodeDefinition("intake.decision", NodeKind.DECISION, "review.decision", Map.of())
                ),
                List.of(
                        new EdgeDefinition("gate.human_next_stage_approval", "revise", "intake.claude_brainstorm"),
                        new EdgeDefinition("intake.claude_brainstorm", "completed", "intake.decision")
                )
        );

        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-close-mistaken", definition);
        var gate = ledger.loadNode(paths, ledger.loadCurrent(paths).activeNodeInstanceIds().get(0));
        ledger.resolveOpenGate(paths, definition, "revise", "historical bad route");
        new AtomicJsonWriter().write(paths.iterationJson("iter-002"), WorkflowIterationDocument.revision(
                "iter-002",
                2,
                "iter-001",
                gate.nodeInstanceId(),
                "approved",
                "intake.claude_brainstorm",
                "Historical final gate approval opened this iteration.",
                Instant.now()
        ));

        var intake = ledger.loadNode(paths, ledger.loadCurrent(paths).activeNodeInstanceIds().get(0));
        ledger.completeAndRoute(paths, definition, intake.nodeInstanceId(), NodeStatus.COMPLETED, "completed", List.of(), Map.of());
        var decision = ledger.loadNode(paths, ledger.loadCurrent(paths).activeNodeInstanceIds().get(0));
        ledger.completeAndRoute(paths, definition, decision.nodeInstanceId(), NodeStatus.BLOCKED, "revision_limit_reached", List.of(), Map.of(
                "reason", "Accidental next iteration should be closed."
        ));

        ledger.closeMistakenIteration(paths, "iter-003 already completed");

        var current = ledger.loadCurrent(paths);
        assertEquals(WorkflowStatus.COMPLETED, current.status());
        assertTrue(current.activeNodeInstanceIds().isEmpty());
        assertTrue(current.openGateIds().isEmpty());
        var skipped = ledger.loadNode(paths, decision.nodeInstanceId());
        assertEquals(NodeStatus.SKIPPED, skipped.status());
        assertEquals(true, skipped.result().get("closed_as_mistaken_iteration"));
        var events = Files.readString(paths.eventsJsonl());
        assertTrue(events.contains("mistaken_iteration_closed"));
        assertTrue(events.contains("workflow_completed"));
        assertTrue(events.contains("close_mistaken_iteration"));
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
        assertTrue(Files.isRegularFile(paths.completionAuditJson()));
        var audit = Files.readString(paths.completionAuditJson());
        assertTrue(audit.contains("\"lifecycleState\" : \"ENDED\""));
        assertTrue(audit.contains("\"workflowStatus\" : \"COMPLETED\""));
        assertTrue(audit.contains("\"finalNodeDefinitionId\" : \"only\""));
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
                        "allowed_actions", List.of("revise", "complete")
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
        assertTrue(Files.readString(paths.completionAuditJson()).contains("\"finalOutcome\" : \"complete\""));
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
                                "allowed_actions", List.of("revise", "complete")
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
        assertFalse(Files.exists(paths.goalSatisfactionJson()));
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
    void modelMismatchFailureSummarySuggestsRebuildingAcpxSession() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-model-mismatch-summary"));
        Files.createDirectories(paths.workstreamDir());
        var definition = new WorkflowDefinition(
                "agent-lifecycle",
                1,
                "intake.claude",
                List.of(new NodeDefinition("intake.claude", NodeKind.TASK, "acpx.agent_run", Map.of())),
                List.of()
        );

        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-model-mismatch-summary", definition);
        var current = ledger.loadCurrent(paths);
        var node = ledger.loadNode(paths, current.activeNodeInstanceIds().get(0));

        ledger.completeAndRoute(paths, definition, node.nodeInstanceId(), NodeStatus.FAILED,
                "RECOVERABLE_AGENT_MODEL_MISMATCH",
                List.of(".agent/workstreams/ws-model-mismatch-summary/runs/run-001/outputs/intake.stderr.log"),
                Map.of(
                        "run_id", "run-001",
                        "exit_code", 1,
                        "agent", "claude",
                        "session", "claude-main",
                        "acpx_failure.reason", "RECOVERABLE_AGENT_MODEL_MISMATCH",
                        "acpx_failure.recoverable", true
                ));

        var summary = Files.readString(paths.currentFailureMarkdown());
        assertTrue(summary.contains("RECOVERABLE_AGENT_MODEL_MISMATCH"));
        assertTrue(summary.contains("acpx claude sessions close claude-main"));
        assertTrue(summary.contains("acpx claude sessions new --name claude-main"));
        assertFalse(summary.contains("quota, reconnect, or session state"));
    }

    @Test
    void reconnectFailureSummarySuggestsRebuildingAcpxSession() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-reconnect-summary"));
        Files.createDirectories(paths.workstreamDir());
        var definition = new WorkflowDefinition(
                "agent-lifecycle",
                1,
                "intake.claude",
                List.of(new NodeDefinition("intake.claude", NodeKind.TASK, "acpx.agent_run", Map.of())),
                List.of()
        );

        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-reconnect-summary", definition);
        var current = ledger.loadCurrent(paths);
        var node = ledger.loadNode(paths, current.activeNodeInstanceIds().get(0));

        ledger.completeAndRoute(paths, definition, node.nodeInstanceId(), NodeStatus.FAILED,
                "RECOVERABLE_AGENT_RECONNECT",
                List.of(".agent/workstreams/ws-reconnect-summary/runs/run-001/outputs/intake.stderr.log"),
                Map.of(
                        "run_id", "run-001",
                        "exit_code", 0,
                        "agent", "claude",
                        "session", "claude-main",
                        "acpx_failure.reason", "RECOVERABLE_AGENT_RECONNECT",
                        "acpx_failure.recoverable", true
                ));

        var summary = Files.readString(paths.currentFailureMarkdown());
        assertTrue(summary.contains("RECOVERABLE_AGENT_RECONNECT"));
        assertTrue(summary.contains("The acpx agent session needs reconnect"));
        assertTrue(summary.contains("acpx claude sessions close claude-main"));
        assertTrue(summary.contains("acpx claude sessions new --name claude-main"));
        assertFalse(summary.contains("quota, reconnect, or session state"));
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
    void staleRunningNodeMovesToRecoveryWhenPidIsGone() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-stale-running"));
        Files.createDirectories(paths.workstreamDir());
        var definition = new WorkflowDefinition(
                "agent-lifecycle",
                1,
                "first",
                List.of(new NodeDefinition("first", NodeKind.TASK, "noop.task", Map.of())),
                List.of()
        );
        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-stale-running", definition);
        var current = ledger.loadCurrent(paths);
        var node = ledger.loadNode(paths, current.activeNodeInstanceIds().get(0));
        ledger.markRunning(paths, node.nodeInstanceId(), Long.MAX_VALUE);

        ledger.recoverStaleRunningNode(paths, "agent process disappeared");

        var recovered = ledger.loadCurrent(paths);
        var failedNode = ledger.loadNode(paths, node.nodeInstanceId());
        assertEquals(WorkflowStatus.WAITING_FOR_RECOVERY, recovered.status());
        assertEquals(List.of(node.nodeInstanceId()), recovered.activeNodeInstanceIds());
        assertEquals(List.of("recovery-" + node.nodeInstanceId()), recovered.openGateIds());
        assertEquals(NodeStatus.FAILED, failedNode.status());
        assertEquals("STALE_RUNNING_PROCESS", failedNode.result().get("reason"));
        assertEquals(true, failedNode.result().get("recoverable"));
        assertTrue(Files.exists(paths.currentFailureMarkdown()));
        assertTrue(Files.readString(paths.currentFailureMarkdown()).contains("STALE_RUNNING_PROCESS"));
        assertTrue(Files.readString(paths.eventsJsonl()).contains("node_stale_running_recovered"));
    }

    @Test
    void staleRunningRecoveryRejectsLivePid() throws Exception {
        var paths = new WorkstreamPaths(tempDir, new DualId("ws-live-running"));
        Files.createDirectories(paths.workstreamDir());
        var definition = new WorkflowDefinition(
                "agent-lifecycle",
                1,
                "first",
                List.of(new NodeDefinition("first", NodeKind.TASK, "noop.task", Map.of())),
                List.of()
        );
        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-live-running", definition);
        var current = ledger.loadCurrent(paths);
        var node = ledger.loadNode(paths, current.activeNodeInstanceIds().get(0));
        ledger.markRunning(paths, node.nodeInstanceId(), ProcessHandle.current().pid());

        var error = assertThrows(IOException.class,
                () -> ledger.recoverStaleRunningNode(paths, "should reject live process"));

        assertTrue(error.getMessage().contains("still alive"));
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
                Map.of(),
                List.of(),
                Map.of(),
                null,
                "",
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
