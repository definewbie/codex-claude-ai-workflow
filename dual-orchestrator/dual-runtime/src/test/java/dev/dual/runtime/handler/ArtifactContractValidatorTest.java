package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeResult;
import dev.dual.core.workflow.NodeStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArtifactContractValidatorTest {
    private static final ObjectMapper JSON = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    @TempDir
    Path tempDir;

    private final ArtifactContractValidator validator = new ArtifactContractValidator();

    @Test
    void validEnvelopeWritesSidecarAndRoutesTypedDecision() throws Exception {
        var ref = write("review.json", envelope("design.codex_arch_review", reviewResult()));
        var validated = validate("design.codex_arch_review", ref, "run-1");

        assertEquals(NodeStatus.COMPLETED, validated.status());
        assertEquals("READY_FOR_ARCH_REVIEW", validated.result().get("decision"));
        assertTrue(validated.outputRefs().contains("review.contract.json"));
        var sidecar = AgentOutputContractDocument.read(tempDir.resolve("review.contract.json"), new ObjectMapper()).orElseThrow();
        assertEquals("ws-1", sidecar.workstreamId());
        assertEquals("run-1", sidecar.runId());
        assertEquals("node-instance-1", sidecar.nodeInstanceId());
        assertEquals("review.json", sidecar.sourceOutputRef());
        assertEquals("design.codex_arch_review", sidecar.nodeId());
        assertEquals("Architecture is ready.", sidecar.summary());
        assertEquals(List.of("design matches contract"), sidecar.evidence());
    }

    @Test
    void trailingMarkdownCannotBypassStrictJsonParser() throws Exception {
        var ref = write("review.json", envelope("design.codex_arch_review", reviewResult()) + "\nDone.");
        var validated = validate("design.codex_arch_review", ref, "run-1");

        assertEquals(NodeStatus.FAILED, validated.status());
        assertEquals("AGENT_OUTPUT_CONTRACT_VIOLATION", validated.outcome());
        assertTrue(String.valueOf(validated.result().get("parse_error")).contains("AGENT_RESULT_PARSE_FAILED"));
        assertFalse(Files.exists(tempDir.resolve("review.contract.json")));
    }

    @Test
    void staleRunLineageCannotProduceWorkflowDecision() throws Exception {
        var stale = new AgentResultEnvelope(1, "ws-1", "run-stale", "design.codex_arch_review", reviewResult());
        var ref = write("review.json", JSON.writeValueAsString(stale));
        var validated = validate("design.codex_arch_review", ref, "run-1");

        assertEquals(NodeStatus.FAILED, validated.status());
        assertTrue(String.valueOf(validated.result().get("message")).contains("run_id"));
    }

    @Test
    void wrongArtifactTypeCannotProduceWorkflowDecision() throws Exception {
        var wrong = new AgentResultEnvelope.AgentOutputResult(
                "agent_output_contract", "READY_FOR_PLAN_REVIEW", "wrong type", "READY", false,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), "checked",
                emptyFailure(), List.of(), null);
        var ref = write("plan.json", envelope("plan.claude_plan", wrong));
        var validated = validate("plan.claude_plan", ref, "run-1");

        assertEquals(NodeStatus.FAILED, validated.status());
        assertTrue(String.valueOf(validated.result().get("message")).contains("artifact_type"));
    }

    @Test
    void planResultProducesOneValidatedOutputAndCanonicalPlanProjection() throws Exception {
        var plan = new AgentResultEnvelope.PlanResult(
                "plan_result", "READY_FOR_PLAN_REVIEW", "Plan is ready.", "READY", false,
                List.of("reviewed design"), List.of("scoped plan"), List.of(), List.of(), List.of(),
                List.of(), List.of(), "planning complete", emptyFailure(),
                new AgentResultEnvelope.Plan(
                        List.of("edit the target"), List.of("src/Main.java"), List.of("README.md"),
                        List.of("src/Main.java: implement"), List.of("mvn test"), "revert commit",
                        List.of("integration risk"), List.of("review scope")));
        var ref = write("plan.json", envelope("plan.claude_plan", plan));
        var validated = validate("plan.claude_plan", ref, "run-1");

        assertEquals(NodeStatus.COMPLETED, validated.status());
        assertEquals("plan_result", artifactContractType(validated));
        assertTrue(validated.outputRefs().contains("plan.plan.json"));
        assertTrue(Files.isRegularFile(tempDir.resolve(".agent/workstreams/ws-1/artifacts/plan/plan_artifact.json")));
    }

    @Test
    void taskProposalIsMechanicallyValidatedBeforeSidecarPublication() throws Exception {
        Files.writeString(tempDir.resolve("README.md"), "# sample\n");
        var proposal = new AgentResultEnvelope.TaskContractProposalResult(
                "task_contract_proposal", "READY_FOR_PRODUCT_REVIEW", "Proposal ready.", false,
                "Update documentation.", new AgentResultEnvelope.ProposalScope(List.of("README.md"), List.of("application code")),
                List.of("README is updated", "No source files are changed"), List.of("inspect diff"), List.of("documentation only"),
                List.of("no source changes"), List.of("scope creep"), List.of("README.md"), List.of());
        var ref = write("intake.json", envelope("intake.claude_brainstorm", proposal));
        var validated = validate("intake.claude_brainstorm", ref, "run-1");

        assertEquals(NodeStatus.COMPLETED, validated.status());
        assertTrue(validated.outputRefs().contains("intake.contract.json"));
    }

    @Test
    void typedScopeExpansionFlowsToValidatedSidecar() throws Exception {
        var output = new AgentResultEnvelope.AgentOutputResult(
                "agent_output_contract", "IMPLEMENTED", "Implementation complete.", "READY", false,
                List.of("tests"), List.of("passed"), List.of(), List.of(), List.of(), List.of(), List.of(),
                "verified", emptyFailure(), List.of(),
                new AgentResultEnvelope.ScopeExpansionRequest(List.of("src/Extra.java"), List.of(), List.of(), "needed", "reference"));
        var ref = write("implement.json", envelope("implement.claude_implement", output));
        var validated = validate("implement.claude_implement", ref, "run-1");

        assertEquals(NodeStatus.COMPLETED, validated.status());
        var sidecar = AgentOutputContractDocument.read(tempDir.resolve("implement.contract.json"), new ObjectMapper()).orElseThrow();
        assertEquals(List.of("src/Extra.java"), sidecar.scopeExpansionRequest().requestedPaths());
    }

    @Test
    void missingJsonResultFailsClosed() throws Exception {
        var validated = validator.validate(
                tempDir, "ws-1", "node-1", node("design.codex_arch_review"),
                NodeResult.completed("completed", List.of("missing.json"), Map.of("run_id", "run-1")));
        assertEquals(NodeStatus.FAILED, validated.status());
        assertTrue(String.valueOf(validated.result().get("parse_error")).contains("AGENT_RESULT_MISSING"));
    }

    @Test
    void preparedAndNonAgentNodesRemainOutsideTheBoundary() throws Exception {
        var prepared = NodeResult.completed("completed", List.of(), Map.of("prepared", true));
        assertEquals(prepared, validator.validate(tempDir, node("design.codex_arch_review"), prepared));

        var nonAgent = new NodeDefinition("design.codex_arch_review", NodeKind.TASK, "deterministic", Map.of());
        var result = NodeResult.completed("completed", List.of(), Map.of());
        assertEquals(result, validator.validate(tempDir, nonAgent, result));
    }

    private NodeResult validate(String nodeId, String outputRef, String runId) throws Exception {
        return validator.validate(
                tempDir, "ws-1", "node-instance-1", node(nodeId),
                NodeResult.completed("completed", List.of(outputRef), Map.of("run_id", runId, "agent", "test")));
    }

    private String envelope(String nodeId, AgentResultEnvelope.AgentResult result) throws Exception {
        return JSON.writeValueAsString(new AgentResultEnvelope(1, "ws-1", "run-1", nodeId, result));
    }

    private String write(String ref, String content) throws Exception {
        Files.writeString(tempDir.resolve(ref), content);
        return ref;
    }

    private static NodeDefinition node(String nodeId) {
        var expected = ContractRegistry.forNode(nodeId).outputArtifactTypes().iterator().next();
        return new NodeDefinition(nodeId, NodeKind.TASK, "acpx.agent_run", Map.of("expected_artifact", expected));
    }

    private static AgentResultEnvelope.AgentOutputResult reviewResult() {
        return new AgentResultEnvelope.AgentOutputResult(
                "agent_output_contract", "READY_FOR_ARCH_REVIEW", "Architecture is ready.", "READY", false,
                List.of("reviewed design"), List.of("design matches contract"), List.of(), List.of(),
                List.of(), List.of(), List.of(), "review complete", emptyFailure(), List.of(), null);
    }

    private static AgentResultEnvelope.Failure emptyFailure() {
        return new AgentResultEnvelope.Failure("", List.of(), List.of(), List.of(), List.of(), "");
    }

    @SuppressWarnings("unchecked")
    private static String artifactContractType(NodeResult result) {
        var contracts = (List<Map<String, Object>>) result.result().get(AgentExecutionResultFacts.ARTIFACT_CONTRACT_RESULTS);
        return String.valueOf(contracts.getFirst().get("contractType"));
    }
}
