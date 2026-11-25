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
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SmallWorkflowProfileGateIntegrationTest {
    private static final String EXPERIMENT_RESOURCE =
            "/dev/dual/runtime/lifecycle/agent-lifecycle.small-plan-human-review-experiment.json";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void experimentalPlanRequiresIndependentReviewBeforeExecutionApproval() throws Exception {
        var f = new AuthoredStageSequenceTest.Fixture(tempDir, "src/new.ext",
                AuthoredStageSequenceTest.PlanTopology.DIRECT_HUMAN, "ws-small", loadExperiment());
        f.before("implement.claude_implement");
        assertTrue(f.calls.contains("plan.codex_plan_review"));
        var contract = TaskContractProjectionVerifier.resolve(f.ledger, f.root, f.ws).qualified().orElseThrow();
        assertEquals("src/new.ext", contract.contract().scope().included().getFirst().path());
        assertEquals(2, contract.contract().reviewedCandidates().size());
        try (var nodes = Files.list(f.paths.nodesDir())) {
            assertFalse(nodes.anyMatch(path -> path.getFileName().toString().contains("human_plan_approval")),
                    "normal READY is not a fabricated human approval");
        }
    }

    @Test
    void reviewerRevisionUsesAuthorAndReReviewBeforeExecutionApproval() throws Exception {
        var f = new AuthoredStageSequenceTest.Fixture(tempDir, "src/new.ext",
                AuthoredStageSequenceTest.PlanTopology.REVIEWED_HUMAN, "ws-small", loadExperiment());
        f.before("implement.claude_implement");
        assertEquals(2, java.util.Collections.frequency(f.calls, "plan.claude_plan"));
        assertEquals(2, java.util.Collections.frequency(f.calls, "plan.codex_plan_review"));
        assertEquals(List.of("plan issue 1"), f.inputs.get("plan.claude_plan").obligations().stream()
                .map(AuthoredExecutionContext.Obligation::text).toList());
        assertTrue(TaskContractProjectionVerifier.resolve(f.ledger, f.root, f.ws).qualified().isPresent());
    }

    @Test
    void invalidPlanCannotReachReviewOrExecutionApprovalInExperimentalVariant() throws Exception {
        var f = new AuthoredStageSequenceTest.Fixture(tempDir, "src/new.ext",
                AuthoredStageSequenceTest.PlanTopology.DIRECT_HUMAN, "ws-small", loadExperiment());
        f.before("plan.claude_plan");
        f.outputMutation = result -> {
            if (result.has("plan")) ((com.fasterxml.jackson.databind.node.ObjectNode) result.get("plan"))
                    .putArray("edit_files").add("../escape");
        };
        f.advanceToGate();
        assertEquals(WorkflowStatus.WAITING_FOR_RECOVERY, f.ledger.loadCurrent(f.paths).status());
        assertFalse(f.calls.contains("plan.codex_plan_review"));
        assertFalse(f.calls.contains("implement.claude_implement"));
        assertTrue(TaskContractProjectionVerifier.resolve(f.ledger, f.root, f.ws).qualified().isEmpty());
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
        var fixture = new AuthoredStageSequenceTest.Fixture(workspace, "src/Main.java",
                AuthoredStageSequenceTest.PlanTopology.DIRECT_HUMAN, workstreamId, loaded);
        fixture.before("plan.claude_plan");
        return new TestState(fixture.paths, fixture.ledger, fixture.ledger.loadDefinition(fixture.paths));
    }

    private WorkflowDefinition loadExperiment() throws Exception {
        return new WorkflowDefinitionLoader().load(
                Path.of(SmallWorkflowProfileGateIntegrationTest.class
                        .getResource(EXPERIMENT_RESOURCE).toURI()));
    }

    private void routePlanToGate(TestState state, List<String> evidenceRefs, String outcome) throws Exception {
        var plan = state.ledger().loadCurrent(state.paths()).activeNodeInstanceIds().getFirst();
        state.ledger().completeAndRoute(
                state.paths(), state.definition(), plan, NodeStatus.COMPLETED, outcome, evidenceRefs,
                state.ledger().loadNode(state.paths(), plan).result());
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
        var paths = new WorkstreamPaths(workspace, new DualId(workstreamId));
        var ledger = dev.dual.runtime.RuntimeTestLedger.ledger();
        var node = ledger.loadCoreNode(paths, ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst());
        var context = new TaskHandler.TaskContext(workspace, workstreamId, ledger.loadDefinition(paths).node(node.nodeDefinitionId()), node, dev.dual.runtime.RuntimeTestLedger.ledger());
        AuthoredInputFixture.record(context, runId);
        var snapshot = AuthoredExecutionContext.prepare(context, runId).orElseThrow();
        var prompt = AgentResultPromptContract.render(workstreamId, runId, "plan.claude_plan",
                "READY_FOR_PLAN_REVIEW", java.util.Set.of("READY_FOR_PLAN_REVIEW"));
        var envelope = (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.readTree(prompt.substring(prompt.indexOf('{')));
        var editFiles = ((com.fasterxml.jackson.databind.node.ObjectNode) envelope.at("/result/plan")).putArray("edit_files");
        TaskContractProjectionVerifier.resolve(dev.dual.runtime.RuntimeTestLedger.ledger(), workspace, workstreamId).qualified().orElseThrow().contract()
                .scope().included().forEach(entry -> editFiles.add(entry.path()));
        var responses = ((com.fasterxml.jackson.databind.node.ObjectNode) envelope.get("result")).putArray("revision_responses");
        snapshot.obligations().forEach(obligation -> responses.addObject().put("obligation_id", obligation.id())
                .put("disposition", "ADDRESSED").put("evidence", "Plan revised with the human note"));
        objectMapper.writeValue(workspace.resolve(evidence.outputRef()).toFile(), envelope);
        var result = new ArtifactContractValidator(dev.dual.runtime.RuntimeTestLedger.ledger()).validate(workspace, workstreamId, node.nodeInstanceId(),
                context.definition(), dev.dual.core.workflow.NodeResult.completed("completed", List.of(evidence.outputRef()),
                        Map.of("run_id", runId)));
        assertEquals(NodeStatus.COMPLETED, result.status(), result.result().toString());
        AgentOutputLineageFixture.publish(workspace, workstreamId, workspace.resolve(evidence.sidecarRef()),
                node.iterationId(), node.inputRefs());
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
