package dev.dual.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.dual.core.domain.Workspace;
import dev.dual.core.domain.Workstream;
import dev.dual.core.id.DualId;
import dev.dual.core.workflow.EdgeDefinition;
import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeStatus;
import dev.dual.core.workflow.WorkflowDefinition;
import dev.dual.core.workflow.WorkflowStatus;
import dev.dual.ledger.file.FileLedgerStore;
import dev.dual.ledger.file.GoalDomainOutcome;
import dev.dual.ledger.file.GoalSatisfactionDocument;
import dev.dual.ledger.file.NodeExecutionDocument;
import dev.dual.ledger.file.NodeExecutionStatus;
import dev.dual.ledger.file.NodeInstanceDocument;
import dev.dual.ledger.file.WorkflowFileLedger;
import dev.dual.ledger.file.WorkflowCurrentDocument;
import dev.dual.ledger.file.WorkstreamAttentionEvaluator;
import dev.dual.ledger.file.WorkstreamPaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class WorkstreamApiControllerTest {
    @TempDir
    Path tempDir;

    @Test
    @SuppressWarnings("unchecked")
    void statusReturnsWorkflowFirstSnapshot() throws Exception {
        var fixture = createWorkstream("ws-api-status");
        var controller = controller();

        var response = controller.status(fixture.workstreamId(), tempDir);

        assertEquals(fixture.workstreamId(), response.get("workstreamId"));
        assertFalse(response.containsKey("legacy"));
        var workspace = (Map<String, Object>) response.get("workspace");
        assertEquals("ai/" + fixture.workstreamId(), workspace.get("branch"));
        var workflow = (Map<String, Object>) response.get("workflow");
        assertEquals("api-workflow", workflow.get("workflowId"));
        assertEquals("WAITING_FOR_HUMAN", workflow.get("status"));
        assertEquals("WAITING_FOR_HUMAN", response.get("status"));
        assertEquals("workflow_current", response.get("statusSource"));
        assertEquals("created", response.get("legacyInitialStatus"));
        var activeNodes = (List<Map<String, Object>>) workflow.get("activeNodes");
        assertEquals("review.gate", activeNodes.get(0).get("nodeDefinitionId"));
        assertFalse(response.containsKey("delivery"));
        assertFalse(response.containsKey("deliveryMerge"));
        var delivery = (Map<String, Object>) response.get("deliveryView");
        assertEquals("BLOCKED", delivery.get("status"));
        assertEquals("WORKFLOW_NOT_DELIVERABLE", delivery.get("reasonCode"));
        assertEquals(false, delivery.get("canMerge"));
        assertEquals(false, delivery.get("canFinalize"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void statusReturnsArtifactContractSummaries() throws Exception {
        var fixture = createWorkstream("ws-api-contracts");
        var paths = new WorkstreamPaths(tempDir, new DualId(fixture.workstreamId()));
        var sidecar = paths.workstreamDir().resolve("runs/run-001/outputs/design-codex-review.contract.json");
        Files.createDirectories(sidecar.getParent());
        Files.writeString(sidecar, """
                {
                  "artifact_type": "agent_output_contract",
                  "node_id": "design.codex_arch_review",
                  "decision": "READY_FOR_ARCH_REVIEW",
                  "summary": "Architecture is ready.",
                  "must_fix": ["Clarify rollout scope"],
                  "open_risks": ["Migration risk"],
                  "changed_files": ["docs/design.md"],
                  "verification_summary": "No runtime verification needed yet.",
                  "source_output_ref": ".agent/workstreams/ws-api-contracts/runs/run-001/outputs/design-codex-review.md"
                }
                """, StandardCharsets.UTF_8);
        var controller = controller();

        var response = controller.status(fixture.workstreamId(), tempDir);

        var contracts = (List<Map<String, Object>>) response.get("artifactContracts");
        assertEquals(1, contracts.size());
        assertEquals("design.codex_arch_review", contracts.get(0).get("node_id"));
        assertEquals("READY_FOR_ARCH_REVIEW", contracts.get(0).get("decision"));
        assertEquals(List.of("Clarify rollout scope"), contracts.get(0).get("must_fix"));
        assertEquals(".agent/workstreams/ws-api-contracts/runs/run-001/outputs/design-codex-review.contract.json",
                contracts.get(0).get("sidecar_ref"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void listReturnsWorkstreamSummaries() throws Exception {
        createWorkstream("ws-api-list-a");
        createWorkstream("ws-api-list-b");
        var controller = controller();

        var response = controller.list(tempDir.toString());

        var workstreams = (List<Map<String, Object>>) response.get("workstreams");
        assertEquals(2, workstreams.size());
        assertEquals("ws-api-list-a", workstreams.get(0).get("workstreamId"));
        assertEquals("WAITING_FOR_HUMAN", workstreams.get(0).get("status"));
        assertEquals("workflow_current", workstreams.get(0).get("statusSource"));
        assertEquals("created", workstreams.get(0).get("legacyInitialStatus"));
        assertFalse(workstreams.get(0).containsKey("workflowStatus"));
        assertEquals(1, workstreams.get(0).get("openGateCount"));
        assertFalse(workstreams.get(0).containsKey("legacy"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void timelineReturnsProjectionJson() throws Exception {
        var fixture = createWorkstream("ws-api-timeline");
        var controller = controller();

        var timeline = controller.timeline(fixture.workstreamId(), tempDir.toString());

        assertEquals(fixture.workstreamId(), timeline.get("workstreamId"));
        assertEquals("WAITING_FOR_HUMAN", timeline.get("workflowStatus"));
        var nodes = (List<Map<String, Object>>) timeline.get("nodes");
        assertEquals(2, nodes.size());
        assertEquals("first", nodes.get(0).get("nodeDefinitionId"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void timelineReturnsNodesInChronologicalOrder() throws Exception {
        var fixture = createWorkstream("ws-api-timeline-order");
        var paths = new WorkstreamPaths(tempDir, new DualId(fixture.workstreamId()));
        Files.writeString(paths.timelineJson(), """
                {
                  "workstreamId": "ws-api-timeline-order",
                  "workflowStatus": "COMPLETED",
                  "activeNodeInstanceIds": [],
                  "openGateIds": [],
                  "nodes": [
                    {
                      "nodeInstanceId": "node-late",
                      "nodeDefinitionId": "late",
                      "status": "COMPLETED",
                      "kind": "TASK",
                      "startedAt": "2026-06-01T00:02:00Z"
                    },
                    {
                      "nodeInstanceId": "node-early",
                      "nodeDefinitionId": "early",
                      "status": "COMPLETED",
                      "kind": "TASK",
                      "startedAt": "2026-06-01T00:01:00Z"
                    }
                  ]
                }
                """, StandardCharsets.UTF_8);
        var controller = controller();

        var timeline = controller.timeline(fixture.workstreamId(), tempDir.toString());

        var nodes = (List<Map<String, Object>>) timeline.get("nodes");
        assertEquals("early", nodes.get(0).get("nodeDefinitionId"));
        assertEquals("late", nodes.get(1).get("nodeDefinitionId"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void overviewReturnsTraceSummary() throws Exception {
        var fixture = createWorkstream("ws-api-overview");
        var paths = new WorkstreamPaths(tempDir, new DualId(fixture.workstreamId()));
        Files.createDirectories(paths.workstreamDir().resolve("reports"));
        Files.writeString(paths.workstreamDir().resolve("reports").resolve("real-case-summary.json"), """
                {
                  "final_status": "COMPLETED",
                  "final_acceptance_decision": "ACCEPT_WITH_NOTES",
                  "repair_loop_count": 1,
                  "verification_confidence": "MEDIUM"
                }
                """, StandardCharsets.UTF_8);
        Files.createDirectories(paths.runsDir().resolve("run-001"));
        Files.writeString(paths.runsDir().resolve("run-001").resolve("run.json"), """
                {
                  "runId": "run-001",
                  "agent": "codex",
                  "nodeId": "acceptance.codex_verifier_review",
                  "status": "completed",
                  "durationMs": 42,
                  "toolUsage": {
                    "toolCallCount": 2,
                    "readCount": 1,
                    "writeCount": 0
                  },
                  "contextGovernanceActualReportPath": ".agent/workstreams/ws-api-overview/artifacts/context-governance/acceptance.codex_verifier_review-actual-run-001.json"
                }
                """, StandardCharsets.UTF_8);
        Files.writeString(paths.eventsJsonl(), """
                {"event_type":"iteration_started","payload":{"trigger_action":"REJECT_IMPLEMENTATION_BUG"}}
                """, StandardCharsets.UTF_8);
        addCompletionEvidence(paths, "ACCEPT_WITH_NOTES");
        var controller = controller();

        var response = controller.overview(fixture.workstreamId(), tempDir.toString());

        var workflow = (Map<String, Object>) response.get("workflow");
        assertEquals("WAITING_FOR_HUMAN", workflow.get("status"));
        assertEquals("workflow_current", workflow.get("statusSource"));
        assertEquals("review.gate", workflow.get("currentNode"));
        var acceptance = (Map<String, Object>) response.get("acceptance");
        assertEquals("ACCEPT_WITH_NOTES", acceptance.get("finalDecision"));
        assertEquals("MEDIUM", acceptance.get("verificationConfidence"));
        assertEquals("real_case_summary_projection", acceptance.get("verificationConfidenceSource"));
        assertEquals(1, acceptance.get("repairLoopCount"));
        var completionEvidence = (Map<String, Object>) response.get("completionEvidence");
        assertEquals("available", completionEvidence.get("status"));
        assertEquals("ACCEPT_WITH_NOTES", completionEvidence.get("goalOutcome"));
        assertEquals("acceptance.codex_verifier_review",
                ((Map<String, Object>) completionEvidence.get("acceptance")).get("nodeId"));
        assertEquals("implement.claude_self_test_report",
                ((Map<String, Object>) completionEvidence.get("selfTest")).get("nodeId"));
        assertEquals(true, response.get("whyStopped").toString().contains("Waiting for human review"));
        assertEquals(true, response.get("nextAction").toString().contains("dualctl review show"));
        var attention = (List<Map<String, Object>>) response.get("attention");
        var sharedAttention = WorkstreamAttentionEvaluator.evaluate(paths);
        assertEquals(sharedAttention.detail(), response.get("whyStopped"));
        assertEquals(sharedNextAction(sharedAttention), response.get("nextAction"));
        assertEquals(sharedAttention.toMap(), attention.getFirst());
        assertEquals(true, attention.stream().anyMatch(item -> "Accepted with notes".equals(item.get("title"))));
        assertEquals(true, attention.stream().anyMatch(item -> "Repair loop occurred".equals(item.get("title"))));
        assertEquals(true, attention.stream().anyMatch(item -> "Human gate is open".equals(item.get("title"))));
        assertEquals(false, ((List<Map<String, Object>>) response.get("recentTimeline")).isEmpty());
        var latestAgentRuns = (List<Map<String, Object>>) response.get("latestAgentRuns");
        assertEquals(3, latestAgentRuns.size());
        var originalRun = latestAgentRuns.stream()
                .filter(run -> "run-001".equals(run.get("runId")))
                .findFirst()
                .orElseThrow();
        assertEquals(2, ((Map<String, Object>) originalRun.get("toolUsage")).get("toolCallCount"));
        assertEquals(
                ".agent/workstreams/ws-api-overview/artifacts/context-governance/acceptance.codex_verifier_review-actual-run-001.json",
                originalRun.get("contextGovernanceActualReportPath")
        );
        var metrics = (Map<String, Object>) response.get("metrics");
        assertEquals(fixture.workstreamId(), metrics.get("workstreamId"));
        assertEquals(2, metrics.get("nodeCount"));
        var artifacts = (Map<String, Object>) response.get("artifacts");
        assertEquals(true, artifacts.containsKey("finalReport"));
        assertFalse(response.containsKey("deliveryMerge"));
        var delivery = (Map<String, Object>) response.get("deliveryView");
        assertEquals("WORKFLOW_NOT_DELIVERABLE", delivery.get("reasonCode"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void serverProjectsNotConfiguredWithoutScanningDecoyAcceptanceRuns() throws Exception {
        var fixture = createWorkstream("ws-api-completion-not-configured");
        var paths = new WorkstreamPaths(tempDir, new DualId(fixture.workstreamId()));
        var decoy = paths.runsDir().resolve("run-decoy/outputs/acceptance-codex-review.contract.json");
        Files.createDirectories(decoy.getParent());
        Files.writeString(decoy, """
                {"node_id":"acceptance.codex_verifier_review","decision":"ACCEPT",
                 "source_artifact_type":"acceptance_review","summary":"decoy"}
                """);
        var controller = controller();

        var status = controller.status(fixture.workstreamId(), tempDir);
        var statusEvidence = (Map<String, Object>) status.get("completionEvidence");
        assertEquals("not_configured", statusEvidence.get("status"));
        assertEquals("COMPLETION_GOAL_NOT_CONFIGURED", statusEvidence.get("failureCode"));

        var overview = controller.overview(fixture.workstreamId(), tempDir.toString());
        assertEquals("none", ((Map<String, Object>) overview.get("acceptance")).get("finalDecision"));
        assertEquals("UNKNOWN", ((Map<String, Object>) overview.get("acceptance")).get("verificationConfidence"));
        var attention = (List<Map<String, Object>>) overview.get("attention");
        assertEquals(false, attention.stream().anyMatch(item -> "No obvious follow-up".equals(item.get("title"))));
    }

    @Test
    @SuppressWarnings("unchecked")
    void serverProjectsLegitimateNonFinalGoalWithoutCorruptionOrSuccessAttention() throws Exception {
        var fixture = createWorkstream("ws-api-completion-not-final");
        var paths = new WorkstreamPaths(tempDir, new DualId(fixture.workstreamId()));
        writeNonFinalGoal(paths);
        var controller = controller();

        var status = controller.status(fixture.workstreamId(), tempDir);
        assertEquals("not_final",
                ((Map<String, Object>) status.get("completionEvidence")).get("status"));

        var overview = controller.overview(fixture.workstreamId(), tempDir.toString());
        assertEquals("not_final", ((Map<String, Object>) overview.get("completionEvidence")).get("status"));
        assertEquals("none", ((Map<String, Object>) overview.get("acceptance")).get("finalDecision"));
        var attention = (List<Map<String, Object>>) overview.get("attention");
        assertEquals(false, attention.stream().anyMatch(item -> "No obvious follow-up".equals(item.get("title"))));
        assertEquals(false, attention.stream()
                .anyMatch(item -> "Completion evidence lineage is invalid".equals(item.get("title"))));
    }

    @Test
    @SuppressWarnings("unchecked")
    void serverProjectsInvalidLineageAsUnavailableAndCritical() throws Exception {
        var fixture = createWorkstream("ws-api-completion-invalid");
        var paths = new WorkstreamPaths(tempDir, new DualId(fixture.workstreamId()));
        addCompletionEvidence(paths, "ACCEPT_WITH_NOTES");
        Files.writeString(
                paths.runsDir().resolve("run-acceptance/outputs/acceptance-codex-review.contract.json"),
                "\n", java.nio.file.StandardOpenOption.APPEND);
        var controller = controller();

        var overview = controller.overview(fixture.workstreamId(), tempDir.toString());

        var completion = (Map<String, Object>) overview.get("completionEvidence");
        assertEquals("unavailable", completion.get("status"));
        assertEquals("COMPLETION_ACCEPTANCE_LINEAGE_INVALID", completion.get("failureCode"));
        assertEquals("none", ((Map<String, Object>) overview.get("acceptance")).get("finalDecision"));
        var attention = (List<Map<String, Object>>) overview.get("attention");
        assertEquals(true, attention.stream()
                .anyMatch(item -> "Completion evidence lineage is invalid".equals(item.get("title"))));
        assertEquals(false, attention.stream().anyMatch(item -> "No obvious follow-up".equals(item.get("title"))));
    }

    @Test
    @SuppressWarnings("unchecked")
    void completedWorkflowWithoutCompletionEvidenceGetsWarningNotSuccess() throws Exception {
        var fixture = createWorkstream("ws-api-completion-warning");
        var paths = new WorkstreamPaths(tempDir, new DualId(fixture.workstreamId()));
        markWorkflowCompleted(paths);
        var controller = controller();

        var overview = controller.overview(fixture.workstreamId(), tempDir.toString());

        var attention = (List<Map<String, Object>>) overview.get("attention");
        assertEquals(true, attention.stream()
                .anyMatch(item -> "Completion evidence is not available".equals(item.get("title"))));
        assertEquals(false, attention.stream().anyMatch(item -> "No obvious follow-up".equals(item.get("title"))));
    }

    @Test
    @SuppressWarnings("unchecked")
    void statusAndOverviewExposeAgentTaskDispatchReadModel() throws Exception {
        var fixture = createRunningWorkstream("ws-api-agent-task");
        var paths = new WorkstreamPaths(tempDir, new DualId(fixture.workstreamId()));
        var ledger = new WorkflowFileLedger();
        var current = ledger.loadCurrent(paths);
        var node = ledger.loadNode(paths, current.activeNodeInstanceIds().get(0));
        var definition = new NodeDefinition(node.nodeDefinitionId(), NodeKind.TASK, node.handlerType(), Map.of(
                "agent", "claude",
                "session", "claude-main",
                "prompt_template", "implementation-claude",
                "expected_contract", "implementation_handoff"
        ));
        var task = ledger.createAgentTask(paths, "wfe-api-agent-task", definition, node);
        ledger.markAgentTaskRunning(paths, task, "nxe-api-agent-task");
        var controller = controller();

        var status = controller.status(fixture.workstreamId(), tempDir);

        var tasks = (List<Map<String, Object>>) status.get("agentTasks");
        assertEquals(1, tasks.size());
        assertEquals("RUNNING", tasks.get(0).get("status"));
        assertEquals("claude", tasks.get(0).get("agent"));
        var workflow = (Map<String, Object>) status.get("workflow");
        var activeNodes = (List<Map<String, Object>>) workflow.get("activeNodes");
        var latestTask = (Map<String, Object>) activeNodes.get(0).get("latestAgentTask");
        assertEquals("implementation-claude", latestTask.get("promptTemplate"));

        var overview = controller.overview(fixture.workstreamId(), tempDir.toString());

        var latestAgentTasks = (List<Map<String, Object>>) overview.get("latestAgentTasks");
        assertEquals(1, latestAgentTasks.size());
        assertEquals("nxe-api-agent-task", latestAgentTasks.get(0).get("nodeExecutionId"));
        assertEquals("implementation_handoff", latestAgentTasks.get(0).get("expectedContract"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void overviewUsesGoalSatisfactionReadModel() throws Exception {
        var fixture = createWorkstream("ws-api-goal-satisfaction");
        var paths = new WorkstreamPaths(tempDir, new DualId(fixture.workstreamId()));
        Files.createDirectories(paths.domainDir());
        Files.writeString(paths.goalSatisfactionJson(), """
                {
                  "schemaVersion": 1,
                  "workstreamId": "ws-api-goal-satisfaction",
                  "workflowInstanceId": "wf-ws-api-goal-satisfaction",
                  "nodeInstanceId": "node-acceptance-decision-001",
                  "nodeDefinitionId": "acceptance.decision",
                  "iterationId": "iter-001",
                  "outcome": "REJECT",
                  "domainOutcome": "NEEDS_REPAIR",
                  "reason": "implementation gap",
                  "evidenceRefs": ["runs/reject.md"],
                  "sourceResult": {"review_decision": "REJECT"},
                  "generatedAt": "2026-06-15T00:00:00Z"
                }
                """, StandardCharsets.UTF_8);
        Files.writeString(paths.workflowCurrentJson(), """
                {
                  "schemaVersion": 1,
                  "workstreamId": "ws-api-goal-satisfaction",
                  "workflowInstanceId": "wf-ws-api-goal-satisfaction",
                  "workflowId": "api-workflow",
                  "definitionVersion": 1,
                  "status": "COMPLETED",
                  "activeNodeInstanceIds": [],
                  "openGateIds": [],
                  "lastEventId": "evt-000099",
                  "updatedAt": "2026-06-15T00:00:01Z"
                }
                """, StandardCharsets.UTF_8);
        var controller = controller();

        var response = controller.overview(fixture.workstreamId(), tempDir.toString());

        var goal = (Map<String, Object>) response.get("goalSatisfaction");
        assertEquals("NEEDS_REPAIR", goal.get("domainOutcome"));
        assertEquals(true, response.get("whyStopped").toString().contains("implementation gap"));
        assertEquals(true, response.get("nextAction").toString().contains("summary"));
        var attention = (List<Map<String, Object>>) response.get("attention");
        assertEquals(true, attention.stream().anyMatch(item ->
                "Workflow completed with goal warning".equals(item.get("title"))
                        && item.get("detail").toString().contains("implementation gap")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void overviewUsesPersistedCompletionAuditReadModel() throws Exception {
        var fixture = createWorkstream("ws-api-completion-audit");
        var paths = new WorkstreamPaths(tempDir, new DualId(fixture.workstreamId()));
        Files.createDirectories(paths.domainDir());
        Files.writeString(paths.completionAuditJson(), """
                {
                  "schemaVersion": 1,
                  "lifecycleState": "ENDED",
                  "status": "needs_review",
                  "workflowStatus": "COMPLETED",
                  "finalNodeDefinitionId": "gate.human_next_stage_approval",
                  "finalOutcome": "complete",
                  "gaps": ["handoff.json is missing"],
                  "completedAt": "2026-06-15T00:00:00Z",
                  "generatedAt": "2026-06-15T00:00:01Z"
                }
                """, StandardCharsets.UTF_8);
        Files.writeString(paths.workflowCurrentJson(), """
                {
                  "schemaVersion": 1,
                  "workstreamId": "ws-api-completion-audit",
                  "workflowInstanceId": "wf-ws-api-completion-audit",
                  "workflowId": "api-workflow",
                  "definitionVersion": 1,
                  "status": "COMPLETED",
                  "activeNodeInstanceIds": [],
                  "openGateIds": [],
                  "lastEventId": "event-completed",
                  "updatedAt": "2026-06-15T00:00:01Z"
                }
                """, StandardCharsets.UTF_8);
        var controller = controller();

        var response = controller.overview(fixture.workstreamId(), tempDir.toString());

        var completion = (Map<String, Object>) response.get("completionAudit");
        assertEquals("ENDED", completion.get("lifecycleState"));
        assertEquals("needs_review", completion.get("status"));
        assertEquals(List.of("handoff.json is missing"), completion.get("gaps"));
        var attention = (List<Map<String, Object>>) response.get("attention");
        assertEquals(true, attention.stream().anyMatch(item ->
                "Completion artifacts need review".equals(item.get("title"))
                        && item.get("detail").toString().contains("handoff.json is missing")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void overviewDoesNotRecomputeNextStageRequirementsWithoutCompletionAudit() throws Exception {
        var fixture = createWorkstream("ws-api-no-completion-audit");
        var paths = new WorkstreamPaths(tempDir, new DualId(fixture.workstreamId()));
        Files.writeString(paths.workflowCurrentJson(), """
                {
                  "schemaVersion": 1,
                  "workstreamId": "ws-api-no-completion-audit",
                  "workflowInstanceId": "wf-ws-api-no-completion-audit",
                  "workflowId": "api-workflow",
                  "definitionVersion": 1,
                  "status": "COMPLETED",
                  "activeNodeInstanceIds": [],
                  "openGateIds": [],
                  "lastEventId": "event-completed",
                  "updatedAt": "2026-06-15T00:00:01Z"
                }
                """, StandardCharsets.UTF_8);
        var controller = controller();

        var response = controller.overview(fixture.workstreamId(), tempDir.toString());

        var completion = (Map<String, Object>) response.get("completionAudit");
        assertEquals("not_available", completion.get("status"));
        assertEquals(List.of("completion audit is missing"), completion.get("gaps"));
        var attention = (List<Map<String, Object>>) response.get("attention");
        assertFalse(attention.stream().anyMatch(item ->
                item.get("title").toString().contains("handoff")
                        || item.get("title").toString().contains("next-stage")
                        || item.get("detail").toString().contains("handoff.json")
                        || item.get("detail").toString().contains("next-stage prompt")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void overviewFailsVisibleWhenPersistedCompletionAuditIsInvalid() throws Exception {
        var fixture = createWorkstream("ws-api-invalid-completion-audit");
        var paths = new WorkstreamPaths(tempDir, new DualId(fixture.workstreamId()));
        Files.createDirectories(paths.domainDir());
        Files.writeString(paths.completionAuditJson(), "{not-json", StandardCharsets.UTF_8);
        var controller = controller();

        var response = controller.overview(fixture.workstreamId(), tempDir.toString());

        var completion = (Map<String, Object>) response.get("completionAudit");
        assertEquals("invalid", completion.get("status"));
        assertEquals(List.of("completion audit is unreadable or invalid"), completion.get("gaps"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void overviewUsesWorkflowCurrentWhenTimelineProjectionIsStale() throws Exception {
        var fixture = createWorkstream("ws-api-stale-timeline");
        var paths = new WorkstreamPaths(tempDir, new DualId(fixture.workstreamId()));
        Files.writeString(paths.timelineJson(), """
                {
                  "workflowId": "api-workflow",
                  "definitionVersion": "1",
                  "workflowStatus": "RUNNING",
                  "activeNodeInstanceIds": ["node-first"],
                  "openGateIds": [],
                  "updatedAt": "2026-06-01T00:00:01Z",
                  "nodes": [
                    {
                      "nodeInstanceId": "node-first",
                      "nodeDefinitionId": "first",
                      "status": "RUNNING",
                      "kind": "TASK",
                      "iterationId": "iter-001"
                    },
                    {
                      "nodeInstanceId": "node-review-gate-002",
                      "nodeDefinitionId": "review.gate",
                      "status": "WAITING",
                      "kind": "GATE",
                      "iterationId": "iter-001"
                    }
                  ]
                }
                """, StandardCharsets.UTF_8);
        var controller = controller();

        var response = controller.overview(fixture.workstreamId(), tempDir.toString());

        var workflow = (Map<String, Object>) response.get("workflow");
        assertEquals("WAITING_FOR_HUMAN", workflow.get("status"));
        assertEquals("review.gate", workflow.get("currentNode"));
        assertEquals(true, response.get("whyStopped").toString().contains("Waiting for human review"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void overviewFlagsStaleRunningWhenCurrentAgentRunFailed() throws Exception {
        var fixture = createWorkstream("ws-api-stale-running");
        var paths = new WorkstreamPaths(tempDir, new DualId(fixture.workstreamId()));
        Files.writeString(paths.workflowCurrentJson(), """
                {
                  "schemaVersion": 1,
                  "workstreamId": "ws-api-stale-running",
                  "workflowInstanceId": "wf-ws-api-stale-running",
                  "workflowId": "api-workflow",
                  "definitionVersion": 1,
                  "status": "RUNNING",
                  "activeNodeInstanceIds": ["node-self-test"],
                  "openGateIds": [],
                  "lastEventId": "event-running",
                  "updatedAt": "2026-06-01T00:01:00Z"
                }
                """, StandardCharsets.UTF_8);
        Files.writeString(paths.timelineJson(), """
                {
                  "workflowId": "api-workflow",
                  "definitionVersion": "1",
                  "workflowStatus": "RUNNING",
                  "activeNodeInstanceIds": ["node-self-test"],
                  "openGateIds": [],
                  "updatedAt": "2026-06-01T00:01:00Z",
                  "nodes": [
                    {
                      "nodeInstanceId": "node-self-test",
                      "nodeDefinitionId": "implement.claude_self_test_report",
                      "status": "RUNNING",
                      "kind": "TASK",
                      "iterationId": "iter-001"
                    }
                  ]
                }
                """, StandardCharsets.UTF_8);
        Files.createDirectories(paths.nodesDir());
        Files.writeString(paths.nodeJson("node-self-test"), """
                {
                  "schemaVersion": 1,
                  "nodeInstanceId": "node-self-test",
                  "nodeDefinitionId": "implement.claude_self_test_report",
                  "kind": "TASK",
                  "handlerType": "acpx.agent_run",
                  "status": "RUNNING",
                  "stageId": "stage-implement",
                  "iterationId": "iter-001",
                  "inputRefs": [],
                  "inputTransition": {},
                  "outputRefs": [],
                  "result": {},
                  "runRef": null,
                  "latestExecutionId": "",
                  "startedAt": "2026-06-01T00:01:00Z",
                  "finishedAt": null
                }
                """, StandardCharsets.UTF_8);
        Files.createDirectories(paths.runsDir().resolve("run-002"));
        Files.writeString(paths.runsDir().resolve("run-002").resolve("run.json"), """
                {
                  "runId": "run-002",
                  "agent": "claude",
                  "nodeId": "implement.claude_self_test_report",
                  "status": "failed",
                  "durationMs": 117719
                }
                """, StandardCharsets.UTF_8);
        Files.createDirectories(paths.recoveryDir());
        Files.writeString(paths.currentFailureMarkdown(), "# Current Failure\n", StandardCharsets.UTF_8);
        var controller = controller();

        var response = controller.overview(fixture.workstreamId(), tempDir.toString());

        assertEquals(true, response.get("whyStopped").toString().contains("current failure summary exists"));
        assertEquals(true, response.get("nextAction").toString().contains("dualctl resume"));
        var attention = (List<Map<String, Object>>) response.get("attention");
        assertEquals(true, attention.stream().anyMatch(item -> "Running state looks stale".equals(item.get("title"))));
    }

    @Test
    @SuppressWarnings("unchecked")
    void overviewFlagsStaleRunningWhenRecordedPidIsGone() throws Exception {
        var fixture = createRunningWorkstream("ws-api-dead-pid");
        var paths = new WorkstreamPaths(tempDir, new DualId(fixture.workstreamId()));
        var ledger = new WorkflowFileLedger();
        var activeNodeId = ledger.loadCurrent(paths).activeNodeInstanceIds().get(0);
        ledger.markRunning(paths, activeNodeId, Long.MAX_VALUE);
        var controller = controller();

        var response = controller.overview(fixture.workstreamId(), tempDir.toString());

        assertEquals(true, response.get("whyStopped").toString().contains("recorded process is no longer alive"));
        assertEquals(true, response.get("whyStopped").toString().contains("PID=" + Long.MAX_VALUE));
        assertEquals(true, response.get("nextAction").toString().contains("dualctl resume"));
        var attention = (List<Map<String, Object>>) response.get("attention");
        assertEquals(true, attention.stream().anyMatch(item -> "Running state looks stale".equals(item.get("title"))));
    }

    @Test
    void currentReviewReturnsActiveGatePacket() throws Exception {
        var fixture = createWorkstream("ws-api-review");
        var paths = new WorkstreamPaths(tempDir, new DualId(fixture.workstreamId()));
        var ledger = new WorkflowFileLedger();
        var activeNodeId = ledger.loadCurrent(paths).activeNodeInstanceIds().get(0);
        var packetPath = paths.workstreamDir().resolve("reviews").resolve(activeNodeId + ".md");
        Files.createDirectories(packetPath.getParent());
        Files.writeString(packetPath, "# Human Review Packet\n\nRead this before approval.\n", StandardCharsets.UTF_8);
        Files.writeString(paths.workstreamDir().resolve("reviews").resolve(activeNodeId + ".json"),
                "{\"allowedActions\":[\"approve\",\"revise\"],\"authoredStage\":{\"candidate\":{\"runId\":\"run-current\"},\"activeObligations\":[{\"id\":\"source-1\",\"text\":\"Keep scope\"}],\"revisionResponses\":[{\"obligation_id\":\"source-1\",\"disposition\":\"NOT_ADDRESSED\",\"evidence\":\"Needs decision\"}]}}", StandardCharsets.UTF_8);
        var controller = controller();

        var response = controller.currentReview(fixture.workstreamId(), tempDir.toString());

        assertEquals(fixture.workstreamId(), response.get("workstreamId"));
        assertEquals("review.gate", response.get("gateNodeDefinitionId"));
        assertEquals(".agent/workstreams/" + fixture.workstreamId() + "/reviews/" + activeNodeId + ".md",
                response.get("packetPath"));
        assertEquals(true, response.get("packetMarkdown").toString().contains("Read this before approval."));
        var expectedPacket = new com.fasterxml.jackson.databind.ObjectMapper().readTree(
                paths.workstreamDir().resolve("reviews").resolve(activeNodeId + ".json").toFile());
        assertEquals(expectedPacket, new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(response.get("packet")));
    }

    @Test
    void reviewReturnsGatePacketByNodeInstanceId() throws Exception {
        var fixture = createWorkstream("ws-api-review-by-node");
        var paths = new WorkstreamPaths(tempDir, new DualId(fixture.workstreamId()));
        var ledger = new WorkflowFileLedger();
        var activeNodeId = ledger.loadCurrent(paths).activeNodeInstanceIds().get(0);
        var packetPath = paths.workstreamDir().resolve("reviews").resolve(activeNodeId + ".md");
        Files.createDirectories(packetPath.getParent());
        Files.writeString(packetPath, "# Historical Human Review Packet\n\nVisible from timeline.\n",
                StandardCharsets.UTF_8);
        Files.writeString(paths.workstreamDir().resolve("reviews").resolve(activeNodeId + ".json"),
                "{\"operatorGuidance\":{\"recommendedAction\":\"approve\"}}", StandardCharsets.UTF_8);
        var controller = controller();

        var response = controller.review(fixture.workstreamId(), activeNodeId, tempDir.toString());

        assertEquals(fixture.workstreamId(), response.get("workstreamId"));
        assertEquals(activeNodeId, response.get("gateNodeInstanceId"));
        assertEquals("review.gate", response.get("gateNodeDefinitionId"));
        assertEquals(true, response.get("packetMarkdown").toString().contains("Visible from timeline."));
        var expectedPacket = new com.fasterxml.jackson.databind.ObjectMapper().readTree(
                paths.workstreamDir().resolve("reviews").resolve(activeNodeId + ".json").toFile());
        assertEquals(expectedPacket, new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(response.get("packet")));
    }

    @Test
    void currentRecoveryReturnsFailureSummaryWhenPresent() throws Exception {
        var fixture = createWorkstream("ws-api-recovery");
        var paths = new WorkstreamPaths(tempDir, new DualId(fixture.workstreamId()));
        Files.createDirectories(paths.recoveryDir());
        Files.writeString(paths.currentFailureMarkdown(), """
                # Current Failure

                - Node: acceptance.classify_failure
                - Category: IMPLEMENTATION_BUG
                """, StandardCharsets.UTF_8);
        var controller = controller();

        var response = controller.currentRecovery(fixture.workstreamId(), tempDir.toString());

        assertEquals(true, response.get("available"));
        assertEquals(".agent/workstreams/" + fixture.workstreamId() + "/recovery/current-failure.md",
                response.get("recoveryPath"));
        assertEquals(true, response.get("markdown").toString().contains("IMPLEMENTATION_BUG"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void currentRecoveryReturnsStructuredAssessmentForContractFailures() throws Exception {
        var fixture = createFailedWorkstream("ws-api-contract-recovery");
        var paths = new WorkstreamPaths(tempDir, new DualId(fixture.workstreamId()));
        var diagnosticRef = ".agent/workstreams/" + fixture.workstreamId()
                + "/artifacts/contract-failures/node-agent.json";
        var diagnosticPath = tempDir.resolve(diagnosticRef);
        Files.createDirectories(diagnosticPath.getParent());
        Files.writeString(diagnosticPath, """
                {
                  "artifact_type": "contract_failure_diagnostic",
                  "category": "missing_structured_artifact",
                  "recovery_hint": "Ask the agent to emit the typed artifact."
                }
                """, StandardCharsets.UTF_8);
        var ledger = new WorkflowFileLedger();
        var current = ledger.loadCurrent(paths);
        var activeNodeId = current.activeNodeInstanceIds().get(0);
        var node = ledger.loadNode(paths, activeNodeId);
        Files.writeString(paths.nodeJson(activeNodeId), new ObjectMapper().findAndRegisterModules()
                .writerWithDefaultPrettyPrinter().writeValueAsString(
                new dev.dual.ledger.file.NodeInstanceDocument(
                        node.schemaVersion(),
                        node.nodeInstanceId(),
                        node.nodeDefinitionId(),
                        node.kind(),
                        node.handlerType(),
                        node.status(),
                        node.stageId(),
                        node.iterationId(),
                        node.inputRefs(),
                        node.inputTransition(),
                        node.outputRefs(),
                        Map.of(
                                "reason", "AGENT_OUTPUT_CONTRACT_VIOLATION",
                                "contract_failure_diagnostic_ref", diagnosticRef
                        ),
                        node.runRef(),
                        node.latestExecutionId(),
                        node.startedAt(),
                        node.finishedAt()
                )
        ), StandardCharsets.UTF_8);
        Files.createDirectories(paths.recoveryDir());
        Files.writeString(paths.currentFailureMarkdown(), "# Current Failure\n\nAGENT_OUTPUT_CONTRACT_VIOLATION\n",
                StandardCharsets.UTF_8);
        var controller = controller();

        var response = controller.currentRecovery(fixture.workstreamId(), tempDir.toString());

        var assessment = (Map<String, Object>) response.get("assessment");
        assertEquals("artifact_contract_failure", assessment.get("classification"));
        assertEquals("Ask the agent to emit the typed artifact.", assessment.get("reason"));
        assertEquals(diagnosticRef, assessment.get("diagnosticJsonRef"));

        var overview = controller.overview(fixture.workstreamId(), tempDir.toString());
        var attention = (List<Map<String, Object>>) overview.get("attention");
        assertEquals(true, attention.stream().anyMatch(item ->
                "Artifact contract failed".equals(item.get("title"))
                        && item.get("detail").toString().contains("typed artifact")));
    }

    @Test
    void currentRecoveryReturnsUnavailableWhenMissing() throws Exception {
        var fixture = createWorkstream("ws-api-no-recovery");
        var controller = controller();

        var response = controller.currentRecovery(fixture.workstreamId(), tempDir.toString());

        assertEquals(false, response.get("available"));
        assertEquals("", response.get("markdown"));
    }

    @Test
    void streamReturnsSseEmitter() throws Exception {
        var fixture = createWorkstream("ws-api-stream");
        var controller = controller();

        var emitter = controller.stream(fixture.workstreamId(), tempDir.toString(), 1000);

        assertNotNull(emitter);
        emitter.complete();
    }

    @Test
    void streamServiceClosesActiveStreamsOnShutdown() throws Exception {
        var fixture = createWorkstream("ws-api-stream-shutdown");
        var objectMapper = new ObjectMapper();
        var streamService = new WorkstreamStreamService(
                new WorkstreamQueryService(objectMapper, new DeliveryViewService(objectMapper)),
                objectMapper
        );

        streamService.overviewStream(tempDir, fixture.workstreamId(), 60_000);

        assertEquals(1, streamService.activeStreamCount());
        streamService.closeActiveStreams();
        assertEquals(0, streamService.activeStreamCount());
    }

    @Test
    void streamExceptionBoundaryRecognizesSseRequests() {
        assertEquals(true, WorkstreamApiController.isStreamRequest(
                "/api/workstreams/ws-1/stream",
                null,
                null
        ));
        assertEquals(true, WorkstreamApiController.isStreamRequest(
                "/api/workstreams/ws-1/status",
                "text/event-stream",
                null
        ));
        assertEquals(true, WorkstreamApiController.isStreamRequest(
                "/api/workstreams/ws-1/status",
                null,
                "text/event-stream;charset=UTF-8"
        ));
        assertEquals(false, WorkstreamApiController.isStreamRequest(
                "/api/workstreams/ws-1/status",
                "application/json",
                "application/json"
        ));
    }

    private WorkstreamApiController controller() {
        var objectMapper = new ObjectMapper();
        var deliveryViewService = new DeliveryViewService(objectMapper);
        var queryService = new WorkstreamQueryService(objectMapper, deliveryViewService);
        return new WorkstreamApiController(
                queryService,
                new WorkstreamActionService(new WorkflowFileLedger(), () -> command ->
                        new dev.dual.runtime.WorkflowRuntimeResult(command.workstreamId(),
                                dev.dual.core.workflow.WorkflowStatus.COMPLETED, "", "unused")),
                new WorkstreamStreamService(queryService, objectMapper),
                deliveryViewService
        );
    }

    private String sharedNextAction(dev.dual.ledger.file.WorkstreamAttention attention) {
        if (attention.command().isBlank()) {
            return attention.nextAction();
        }
        return attention.nextAction() + " Command: " + attention.command();
    }

    private Fixture createWorkstream(String workstreamIdValue) throws Exception {
        var workstreamId = new DualId(workstreamIdValue);
        var workspaceId = new DualId("wsp-" + workstreamIdValue);
        var paths = new WorkstreamPaths(tempDir, workstreamId);
        new FileLedgerStore().initializeWorkstream(
                paths,
                Workstream.created(
                        workstreamId,
                        "api test",
                        new Workstream.RepositoryRef(tempDir.resolve("repo"), tempDir.resolve("repo")),
                        workspaceId,
                        Instant.parse("2026-06-01T00:00:00Z")
                ),
                Workspace.primaryGitWorktree(
                        workspaceId,
                        workstreamId,
                        tempDir.resolve("repo"),
                        tempDir.resolve("repo-agent-runs").resolve(workstreamIdValue),
                        "ai/" + workstreamIdValue,
                        "main",
                        "abc123"
                ),
                "# Requirement\n"
        );
        var definition = new WorkflowDefinition(
                "api-workflow",
                1,
                "first",
                List.of(
                        new NodeDefinition("first", NodeKind.TASK, "noop.task", Map.of()),
                        new NodeDefinition("review.gate", NodeKind.GATE, "human.review", Map.of()),
                        new NodeDefinition("implement.claude_self_test_report", NodeKind.TASK,
                                "acpx.agent_run", Map.of()),
                        new NodeDefinition("context.acceptance_codex_verifier_review", NodeKind.TASK,
                                "context.build_package", Map.of(
                                "profile", "acceptance",
                                "target_node", "acceptance.codex_verifier_review",
                                "required_agent_evidence", Map.of(
                                        "node_id", "implement.claude_self_test_report",
                                        "source_artifact_type", "self_test_report",
                                        "decision", "SELF_TEST_COMPLETE"))),
                        new NodeDefinition("acceptance.codex_verifier_review", NodeKind.TASK,
                                "acpx.agent_run", Map.of()),
                        new NodeDefinition("acceptance.decision", NodeKind.DECISION,
                                "review.decision", Map.of()),
                        new NodeDefinition("acceptance.generate_final_report", NodeKind.TASK,
                                "report.generate_final", Map.of())
                ),
                List.of(
                        new EdgeDefinition("first", "completed", "review.gate"),
                        new EdgeDefinition("acceptance.codex_verifier_review", "ACCEPT_WITH_NOTES",
                                "acceptance.decision"),
                        new EdgeDefinition("acceptance.decision", "ACCEPT_WITH_NOTES",
                                "acceptance.generate_final_report"))
        );
        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-" + workstreamIdValue, definition);
        var current = ledger.loadCurrent(paths);
        var first = ledger.loadNode(paths, current.activeNodeInstanceIds().get(0));
        ledger.completeAndRoute(paths, definition, first.nodeInstanceId(), NodeStatus.COMPLETED,
                "completed", List.of("first-output.md"), Map.of("ok", true));
        return new Fixture(workstreamIdValue);
    }

    private Fixture createRunningWorkstream(String workstreamIdValue) throws Exception {
        var workstreamId = new DualId(workstreamIdValue);
        var workspaceId = new DualId("wsp-" + workstreamIdValue);
        var paths = new WorkstreamPaths(tempDir, workstreamId);
        new FileLedgerStore().initializeWorkstream(
                paths,
                Workstream.created(
                        workstreamId,
                        "api running test",
                        new Workstream.RepositoryRef(tempDir.resolve("repo"), tempDir.resolve("repo")),
                        workspaceId,
                        Instant.parse("2026-06-01T00:00:00Z")
                ),
                Workspace.primaryGitWorktree(
                        workspaceId,
                        workstreamId,
                        tempDir.resolve("repo"),
                        tempDir.resolve("repo-agent-runs").resolve(workstreamIdValue),
                        "ai/" + workstreamIdValue,
                        "main",
                        "abc123"
                ),
                "# Requirement\n"
        );
        var definition = new WorkflowDefinition(
                "api-workflow",
                1,
                "first",
                List.of(new NodeDefinition("first", NodeKind.TASK, "noop.task", Map.of())),
                List.of()
        );
        new WorkflowFileLedger().initialize(paths, "wf-" + workstreamIdValue, definition);
        return new Fixture(workstreamIdValue);
    }

    private Fixture createFailedWorkstream(String workstreamIdValue) throws Exception {
        var workstreamId = new DualId(workstreamIdValue);
        var workspaceId = new DualId("wsp-" + workstreamIdValue);
        var paths = new WorkstreamPaths(tempDir, workstreamId);
        new FileLedgerStore().initializeWorkstream(
                paths,
                Workstream.created(
                        workstreamId,
                        "api failed test",
                        new Workstream.RepositoryRef(tempDir.resolve("repo"), tempDir.resolve("repo")),
                        workspaceId,
                        Instant.parse("2026-06-01T00:00:00Z")
                ),
                Workspace.primaryGitWorktree(
                        workspaceId,
                        workstreamId,
                        tempDir.resolve("repo"),
                        tempDir.resolve("repo-agent-runs").resolve(workstreamIdValue),
                        "ai/" + workstreamIdValue,
                        "main",
                        "abc123"
                ),
                "# Requirement\n"
        );
        var definition = new WorkflowDefinition(
                "api-recovery-workflow",
                1,
                "agent.step",
                List.of(new NodeDefinition("agent.step", NodeKind.TASK, "acpx.agent_run", Map.of())),
                List.of()
        );
        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "wf-" + workstreamIdValue, definition);
        var current = ledger.loadCurrent(paths);
        var node = ledger.loadNode(paths, current.activeNodeInstanceIds().get(0));
        ledger.completeAndRoute(paths, definition, node.nodeInstanceId(), NodeStatus.FAILED,
                "AGENT_OUTPUT_CONTRACT_VIOLATION",
                List.of(".agent/workstreams/" + workstreamIdValue + "/runs/run-001/outputs/agent.md"),
                Map.of("reason", "AGENT_OUTPUT_CONTRACT_VIOLATION"));
        return new Fixture(workstreamIdValue);
    }

    private void addCompletionEvidence(WorkstreamPaths paths, String acceptanceDecision) throws Exception {
        var mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        var iterationId = "iter-completion";
        var workstreamId = paths.workstreamId().value();
        var selfTest = writeAgentEvidence(
                mapper, paths, "run-self-test", "node-self-test", "implement.claude_self_test_report",
                "implement-claude-self-test", "self_test_report", "SELF_TEST_COMPLETE",
                "Self-test passed.", iterationId, List.of());

        var contextNodeId = "node-acceptance-context";
        var packageRef = ".agent/workstreams/" + workstreamId + "/context/" + contextNodeId + ".md";
        var metadataRef = ".agent/workstreams/" + workstreamId + "/context/" + contextNodeId + ".json";
        Files.createDirectories(tempDir.resolve(packageRef).getParent());
        Files.writeString(tempDir.resolve(packageRef), "# Acceptance context\n");
        Files.writeString(tempDir.resolve(metadataRef), "{}\n");
        mapper.writeValue(paths.nodeJson(contextNodeId).toFile(), new NodeInstanceDocument(
                1, contextNodeId, "context.acceptance_codex_verifier_review", NodeKind.TASK,
                "context.build_package", NodeStatus.COMPLETED, "stage-acceptance", iterationId,
                List.of(selfTest.sidecarRef()), Map.of(), List.of(packageRef, metadataRef), Map.of(),
                "", "", Instant.parse("2026-06-01T01:00:00Z"), Instant.parse("2026-06-01T01:00:01Z")));

        var acceptance = writeAgentEvidence(
                mapper, paths, "run-acceptance", "node-acceptance", "acceptance.codex_verifier_review",
                "acceptance-codex-review", "acceptance_review", acceptanceDecision,
                "Accepted with notes.", iterationId, List.of(packageRef, metadataRef));
        var acceptanceRefs = List.of(acceptance.sourceRef(), acceptance.sidecarRef());
        var decisionNodeId = "node-acceptance-decision";
        var decisionResult = Map.<String, Object>of("decision", acceptanceDecision);
        mapper.writeValue(paths.nodeJson(decisionNodeId).toFile(), new NodeInstanceDocument(
                1, decisionNodeId, "acceptance.decision", NodeKind.DECISION, "review.decision",
                NodeStatus.COMPLETED, "stage-acceptance", iterationId, acceptanceRefs, Map.of(),
                acceptanceRefs, decisionResult, "", "", Instant.parse("2026-06-01T01:00:02Z"),
                Instant.parse("2026-06-01T01:00:03Z")));
        Files.createDirectories(paths.domainDir());
        mapper.writeValue(paths.goalSatisfactionJson().toFile(), new GoalSatisfactionDocument(
                1, workstreamId, "wf-" + workstreamId, decisionNodeId, "acceptance.decision", iterationId,
                acceptanceDecision, GoalDomainOutcome.SATISFIED_WITH_NOTES, "Accepted with notes.",
                acceptanceRefs, decisionResult, Instant.parse("2026-06-01T01:00:04Z")));
    }

    private void writeNonFinalGoal(WorkstreamPaths paths) throws Exception {
        var mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        Files.createDirectories(paths.domainDir());
        mapper.writeValue(paths.goalSatisfactionJson().toFile(), new GoalSatisfactionDocument(
                1, paths.workstreamId().value(), "wf-" + paths.workstreamId().value(),
                "node-design-decision", "design.decision", "iter-design", "NEEDS_REVISION",
                GoalDomainOutcome.NEEDS_REDESIGN, "Design needs revision.", List.of(), Map.of(),
                Instant.parse("2026-06-01T02:00:00Z")));
    }

    private void markWorkflowCompleted(WorkstreamPaths paths) throws Exception {
        var mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        var current = mapper.readValue(paths.workflowCurrentJson().toFile(), WorkflowCurrentDocument.class);
        mapper.writeValue(paths.workflowCurrentJson().toFile(), new WorkflowCurrentDocument(
                current.schemaVersion(), current.workstreamId(), current.workflowInstanceId(), current.workflowId(),
                current.definitionVersion(), WorkflowStatus.COMPLETED, List.of(), List.of(), current.lastEventId(),
                Instant.parse("2026-06-01T03:00:00Z")));
    }

    private AgentEvidenceRefs writeAgentEvidence(
            ObjectMapper mapper,
            WorkstreamPaths paths,
            String runId,
            String nodeInstanceId,
            String nodeId,
            String outputStem,
            String artifactType,
            String decision,
            String summary,
            String iterationId,
            List<String> inputRefs
    ) throws Exception {
        var outputDir = paths.runsDir().resolve(runId).resolve("outputs");
        Files.createDirectories(outputDir);
        var sourcePath = outputDir.resolve(outputStem + ".json");
        var sidecarPath = outputDir.resolve(outputStem + ".contract.json");
        Files.writeString(sourcePath, "{}\n");
        var sourceRef = relative(sourcePath);
        var sidecarRef = relative(sidecarPath);
        mapper.writeValue(sidecarPath.toFile(), Map.of(
                "workstream_id", paths.workstreamId().value(),
                "run_id", runId,
                "node_id", nodeId,
                "node_instance_id", nodeInstanceId,
                "decision", decision,
                "stage_outcome", "COMPLETED",
                "summary", summary,
                "source_artifact_type", artifactType,
                "source_output_ref", sourceRef
        ));
        var result = Map.<String, Object>of(
                "run_id", runId,
                "decision", decision,
                "contract_sidecar_sha256", sha256(sidecarPath));
        var outputRefs = List.of(sourceRef, sidecarRef);
        var executionId = "nxe-" + nodeInstanceId;
        var started = Instant.parse("2026-06-01T00:30:00Z");
        var finished = Instant.parse("2026-06-01T00:30:01Z");
        mapper.writeValue(paths.nodeJson(nodeInstanceId).toFile(), new NodeInstanceDocument(
                1, nodeInstanceId, nodeId, NodeKind.TASK, "acpx.agent_run", NodeStatus.COMPLETED,
                "stage-acceptance", iterationId, inputRefs, Map.of(), outputRefs, result,
                runId, executionId, started, finished));
        Files.createDirectories(paths.nodeExecutionsDir());
        mapper.writeValue(paths.nodeExecutionJson(executionId).toFile(), new NodeExecutionDocument(
                1, executionId, "agt-" + nodeInstanceId, "wfe-test", nodeInstanceId, nodeId,
                "acpx.agent_run", NodeExecutionStatus.SUCCEEDED, 1L, "completed", NodeStatus.COMPLETED,
                outputRefs, result, "", runId, "", "fixture-agent", "fixture-session", 0,
                started, finished, 1L));
        return new AgentEvidenceRefs(sourceRef, sidecarRef);
    }

    private String relative(Path path) {
        return tempDir.toAbsolutePath().normalize().relativize(path.toAbsolutePath().normalize()).toString();
    }

    private static String sha256(Path path) throws Exception {
        var digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path));
        return java.util.HexFormat.of().formatHex(digest);
    }

    private record AgentEvidenceRefs(String sourceRef, String sidecarRef) {
    }

    private record Fixture(String workstreamId) {
    }
}
