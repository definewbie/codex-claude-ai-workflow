package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dual.core.id.DualId;
import dev.dual.core.workflow.NodeStatus;
import dev.dual.core.workflow.WorkflowDefinition;
import dev.dual.core.workflow.WorkflowStatus;
import dev.dual.ledger.file.WorkflowFileLedger;
import dev.dual.ledger.file.WorkstreamPaths;
import dev.dual.runtime.lifecycle.WorkflowDefinitionLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmallWorkflowProfileGateIntegrationTest {
    private static final String EXPERIMENT_RESOURCE =
            "/dev/dual/runtime/lifecycle/agent-lifecycle.small-plan-human-review-experiment.json";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void validatedPlanSidecarRoutesDirectlyToHumanGateAndApproveStartsImplementationContext() throws Exception {
        var workspace = tempDir.resolve("approve-workspace");
        var state = initializeAtPlan(workspace, "ws-small-approve");
        var evidence = publishPlanSidecar(workspace, state.paths().workstreamId().value(), "run-plan-approve");

        routePlanToGate(state, evidence.refs(), "completed");
        var gateNode = state.ledger().loadCoreNode(
                state.paths(), state.ledger().loadCurrent(state.paths()).activeNodeInstanceIds().getFirst());
        var gate = state.ledger().loadOpenGateForNode(state.paths(), gateNode.nodeInstanceId());
        var result = new HumanReviewGateHandler().openOrInspect(new GateHandler.GateContext(
                workspace,
                state.paths().workstreamId().value(),
                state.definition().node("gate.human_plan_approval"),
                gateNode,
                String.valueOf(gate.result().get("source_outcome"))));

        @SuppressWarnings("unchecked")
        var packet = (Map<String, Object>) objectMapper.readValue(
                workspace.resolve(String.valueOf(result.result().get("review_packet_json"))).toFile(), Map.class);
        @SuppressWarnings("unchecked")
        var latestDecision = (Map<String, Object>) packet.get("latestAgentDecision");

        assertEquals("completed", packet.get("sourceOutcome"));
        assertEquals(List.of("approve", "revise"), packet.get("allowedActions"));
        assertEquals(evidence.refs(), packet.get("inputRefs"));
        assertEquals("READY_FOR_PLAN_REVIEW", latestDecision.get("decision"));
        assertFalse(objectMapper.writeValueAsString(packet).contains("plan.codex_plan_review"));

        state.ledger().resolveOpenGate(state.paths(), state.definition(), "approve", "Plan reviewed by operator.");
        var next = state.ledger().loadNode(
                state.paths(), state.ledger().loadCurrent(state.paths()).activeNodeInstanceIds().getFirst());
        assertEquals("context.implement_claude", next.nodeDefinitionId());
        try (var files = Files.walk(workspace)) {
            assertFalse(files.anyMatch(path -> path.toString().contains("plan-codex-review")));
        }
    }

    @Test
    void humanPlanRevisionUsesOnlyTheGateOwnedBudget() throws Exception {
        var workspace = tempDir.resolve("revise-workspace");
        var state = initializeAtPlan(workspace, "ws-small-revise");

        for (var revision = 1; revision <= 3; revision++) {
            var evidence = publishPlanSidecar(
                    workspace, state.paths().workstreamId().value(), "run-plan-revise-" + revision);
            routePlanToGate(state, evidence.refs(), "completed");
            state.ledger().resolveOpenGate(
                    state.paths(), state.definition(), "revise", "Human plan revision " + revision);

            var plan = state.ledger().loadNode(
                    state.paths(), state.ledger().loadCurrent(state.paths()).activeNodeInstanceIds().getFirst());
            assertEquals("plan.claude_plan", plan.nodeDefinitionId());
            assertEquals("iter-00" + (revision + 1), plan.iterationId());
        }

        var fourthEvidence = publishPlanSidecar(
                workspace, state.paths().workstreamId().value(), "run-plan-revise-4");
        routePlanToGate(state, fourthEvidence.refs(), "completed");
        state.ledger().resolveOpenGate(
                state.paths(), state.definition(), "revise", "Human plan revision 4 exceeds the budget");

        assertEquals(WorkflowStatus.WAITING_FOR_RECOVERY, state.ledger().loadCurrent(state.paths()).status());
        var events = Files.readString(state.paths().eventsJsonl());
        assertTrue(events.contains("revision_limit_reached"));
        assertTrue(events.contains("gate.human_plan_approval"));
        assertFalse(events.contains("plan.decision"));
    }

    @Test
    void lineageInvalidPlanSidecarCannotBecomeHumanReviewEvidence() throws Exception {
        var workspace = tempDir.resolve("invalid-workspace");
        var state = initializeAtPlan(workspace, "ws-small-invalid");
        var evidence = writePlanSidecar(
                workspace, state.paths().workstreamId().value(), "run-plan-forged");

        routePlanToGate(state, evidence.refs(), "completed");
        var gateNode = state.ledger().loadCoreNode(
                state.paths(), state.ledger().loadCurrent(state.paths()).activeNodeInstanceIds().getFirst());
        var gate = state.ledger().loadOpenGateForNode(state.paths(), gateNode.nodeInstanceId());

        assertThrows(IllegalStateException.class, () -> new HumanReviewGateHandler().openOrInspect(
                new GateHandler.GateContext(
                        workspace,
                        state.paths().workstreamId().value(),
                        state.definition().node("gate.human_plan_approval"),
                        gateNode,
                        String.valueOf(gate.result().get("source_outcome")))));
        assertFalse(Files.exists(workspace.resolve(
                ".agent/workstreams/" + state.paths().workstreamId().value()
                        + "/reviews/" + gateNode.nodeInstanceId() + ".json")));
    }

    @Test
    void blockedPlanOutcomeHasTheSameFailClosedBehaviorAsTerminalDelivery() throws Exception {
        var baseline = new WorkflowDefinitionLoader().loadOrDefault(null, "terminal-delivery");
        var experiment = loadExperiment();
        var baselineState = initializeAtPlan(
                tempDir.resolve("blocked-baseline"), "ws-blocked-baseline", baseline);
        var experimentState = initializeAtPlan(
                tempDir.resolve("blocked-experiment"), "ws-blocked-experiment", experiment);

        var baselinePlan = completeBlockedPlan(baselineState);
        var experimentPlan = completeBlockedPlan(experimentState);

        assertEquals(baselineState.ledger().loadCurrent(baselineState.paths()).status(),
                experimentState.ledger().loadCurrent(experimentState.paths()).status());
        assertEquals(WorkflowStatus.WAITING_FOR_RECOVERY,
                experimentState.ledger().loadCurrent(experimentState.paths()).status());
        assertEquals(baselineState.ledger().loadNode(baselineState.paths(), baselinePlan).status(),
                experimentState.ledger().loadNode(experimentState.paths(), experimentPlan).status());
        assertEquals(NodeStatus.BLOCKED,
                experimentState.ledger().loadNode(experimentState.paths(), experimentPlan).status());
    }

    private TestState initializeAtPlan(Path workspace, String workstreamId) throws Exception {
        Files.createDirectories(workspace);
        return initializeAtPlan(workspace, workstreamId, loadExperiment());
    }

    private TestState initializeAtPlan(
            Path workspace,
            String workstreamId,
            WorkflowDefinition loaded
    ) throws Exception {
        Files.createDirectories(workspace);
        var definition = new WorkflowDefinition(
                loaded.workflowId(), loaded.version(), "plan.claude_plan", loaded.nodes(), loaded.edges());
        var paths = new WorkstreamPaths(workspace, new DualId(workstreamId));
        var ledger = new WorkflowFileLedger();
        ledger.initialize(paths, "workflow-" + workstreamId, definition);
        return new TestState(paths, ledger, definition);
    }

    private WorkflowDefinition loadExperiment() throws Exception {
        return new WorkflowDefinitionLoader().load(
                Path.of(SmallWorkflowProfileGateIntegrationTest.class
                        .getResource(EXPERIMENT_RESOURCE).toURI()));
    }

    private void routePlanToGate(TestState state, List<String> evidenceRefs, String outcome) throws Exception {
        var plan = state.ledger().loadCurrent(state.paths()).activeNodeInstanceIds().getFirst();
        state.ledger().completeAndRoute(
                state.paths(), state.definition(), plan, NodeStatus.COMPLETED, outcome, evidenceRefs, Map.of());
        var gate = state.ledger().loadNode(
                state.paths(), state.ledger().loadCurrent(state.paths()).activeNodeInstanceIds().getFirst());
        assertEquals("gate.human_plan_approval", gate.nodeDefinitionId());
        assertEquals(evidenceRefs, gate.inputRefs());
    }

    private String completeBlockedPlan(TestState state) throws Exception {
        var plan = state.ledger().loadCurrent(state.paths()).activeNodeInstanceIds().getFirst();
        state.ledger().completeAndRoute(
                state.paths(), state.definition(), plan, NodeStatus.BLOCKED, "BLOCKED", List.of(), Map.of());
        return plan;
    }

    private PlanEvidence publishPlanSidecar(Path workspace, String workstreamId, String runId) throws Exception {
        var evidence = writePlanSidecar(workspace, workstreamId, runId);
        AgentOutputLineageFixture.publish(workspace, workstreamId, workspace.resolve(evidence.sidecarRef()));
        return evidence;
    }

    private PlanEvidence writePlanSidecar(Path workspace, String workstreamId, String runId) throws Exception {
        var outputRef = ".agent/workstreams/" + workstreamId + "/runs/" + runId + "/outputs/plan.json";
        var sidecarRef = ".agent/workstreams/" + workstreamId + "/runs/" + runId
                + "/outputs/plan.contract.json";
        var output = workspace.resolve(outputRef);
        var sidecar = workspace.resolve(sidecarRef);
        Files.createDirectories(output.getParent());
        Files.writeString(output, "{}", StandardCharsets.UTF_8);
        Files.writeString(sidecar, """
                {
                  "artifact_type": "agent_output_contract",
                  "schema_version": 1,
                  "workstream_id": "%s",
                  "run_id": "%s",
                  "node_id": "plan.claude_plan",
                  "node_instance_id": "node-fixture-%s",
                  "source_output_ref": "%s",
                  "decision": "READY_FOR_PLAN_REVIEW",
                  "summary": "The implementation plan is ready for human review.",
                  "source_artifact_type": "plan_result",
                  "stage_outcome": "READY",
                  "blocking": false,
                  "must_fix": [],
                  "missing_evidence": [],
                  "open_risks": [],
                  "changed_files": [],
                  "verification_summary": "Plan structure validated.",
                  "control": {
                    "decision": "READY_FOR_PLAN_REVIEW",
                    "summary": "The implementation plan is ready for human review."
                  },
                  "evidence_state": {
                    "blocking": false,
                    "must_fix_count": 0,
                    "missing_evidence_count": 0
                  },
                  "failure": {
                    "category_hint": "",
                    "contract_gaps": [],
                    "out_of_scope_files": [],
                    "missing_tests": [],
                    "context_gaps": [],
                    "reason": ""
                  }
                }
                """.formatted(workstreamId, runId, runId, outputRef), StandardCharsets.UTF_8);
        return new PlanEvidence(outputRef, sidecarRef);
    }

    private record TestState(
            WorkstreamPaths paths,
            WorkflowFileLedger ledger,
            WorkflowDefinition definition
    ) {
    }

    private record PlanEvidence(String outputRef, String sidecarRef) {
        private List<String> refs() {
            return List.of(outputRef, sidecarRef);
        }
    }
}
