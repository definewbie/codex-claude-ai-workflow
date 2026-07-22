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
        var ref = write(".agent/workstreams/ws-1/runs/run-1/outputs/intake.json",
                envelope("intake.claude_brainstorm", proposal));
        var validated = validate("intake.claude_brainstorm", ref, "run-1");

        assertEquals(NodeStatus.COMPLETED, validated.status());
        assertTrue(validated.outputRefs().contains(
                ".agent/workstreams/ws-1/runs/run-1/outputs/intake.contract.json"));
        assertTrue(validated.outputRefs().stream().anyMatch(value -> value.endsWith(".ref.json")));
    }

    @Test
    void proposalRetriesPublishDistinctImmutableReferences() throws Exception {
        Files.writeString(tempDir.resolve("README.md"), "# sample\n");
        var first = new AgentResultEnvelope.TaskContractProposalResult(
                "task_contract_proposal", "READY_FOR_PRODUCT_REVIEW", "First proposal.", false,
                "Update documentation.", new AgentResultEnvelope.ProposalScope(List.of("README.md"), List.of()),
                List.of("README is updated", "No source files are changed"), List.of("inspect diff"), List.of("documentation only"),
                List.of(), List.of(), List.of("README.md"), List.of());
        var second = new AgentResultEnvelope.TaskContractProposalResult(
                "task_contract_proposal", "READY_FOR_PRODUCT_REVIEW", "Revised proposal.", false,
                "Update documentation safely.", new AgentResultEnvelope.ProposalScope(List.of("README.md"), List.of()),
                List.of("README is updated safely", "No source files are changed"), List.of("inspect diff"), List.of("documentation only"),
                List.of(), List.of(), List.of("README.md"), List.of());

        var firstOutput = write(".agent/workstreams/ws-1/runs/run-1/outputs/intake.json",
                envelope("intake.claude_brainstorm", first));
        var firstResult = validate("intake.claude_brainstorm", firstOutput, "run-1");
        var secondOutput = write(".agent/workstreams/ws-1/runs/run-2/outputs/intake.json",
                JSON.writeValueAsString(new AgentResultEnvelope(
                        1, "ws-1", "run-2", "intake.claude_brainstorm", second)));
        var secondResult = validate("intake.claude_brainstorm", secondOutput, "run-2");

        var firstRef = firstResult.outputRefs().stream().filter(value -> value.endsWith(".ref.json")).findFirst().orElseThrow();
        var secondRef = secondResult.outputRefs().stream().filter(value -> value.endsWith(".ref.json")).findFirst().orElseThrow();
        assertFalse(firstRef.equals(secondRef));
        assertTrue(Files.isRegularFile(tempDir.resolve(firstRef)));
        assertTrue(Files.isRegularFile(tempDir.resolve(secondRef)));
    }

    @Test
    void codexReviewSidecarBindsTrustedProposalSubjectFromNodeInputs() throws Exception {
        Files.writeString(tempDir.resolve("README.md"), "# sample\n");
        var proposal = new AgentResultEnvelope.TaskContractProposalResult(
                "task_contract_proposal", "READY_FOR_PRODUCT_REVIEW", "Proposal ready.", false,
                "Update documentation.", new AgentResultEnvelope.ProposalScope(List.of("README.md"), List.of()),
                List.of("README is updated", "No source files are changed"), List.of("inspect diff"),
                List.of("documentation only"), List.of("no source changes"), List.of(),
                List.of("README.md"), List.of());
        var proposalOutput = write(".agent/workstreams/ws-1/runs/run-1/outputs/intake.json",
                envelope("intake.claude_brainstorm", proposal));
        var proposalResult = validate("intake.claude_brainstorm", proposalOutput, "run-1");
        assertEquals(NodeStatus.COMPLETED, proposalResult.status(), proposalResult.result().toString());
        var proposalRef = proposalResult.outputRefs().stream()
                .filter(value -> value.endsWith(".ref.json"))
                .findFirst()
                .orElseThrow();

        var reviewOutput = write(".agent/workstreams/ws-1/runs/run-2/outputs/review.json",
                JSON.writeValueAsString(new AgentResultEnvelope(
                        1, "ws-1", "run-2", "intake.codex_product_review", productReviewResult())));
        var reviewNode = new NodeDefinition(
                "intake.codex_product_review", NodeKind.TASK, "acpx.agent_run", Map.of(
                        "expected_artifact", "agent_output_contract",
                        "review_subject_artifact", "task_contract_proposal"));
        var validated = validator.validate(
                tempDir, "ws-1", "node-review", reviewNode, List.of(proposalRef),
                NodeResult.completed("completed", List.of(reviewOutput), Map.of("run_id", "run-2")));

        assertEquals(NodeStatus.COMPLETED, validated.status(), validated.result().toString());
        var sidecar = AgentOutputContractDocument.read(
                tempDir.resolve(reviewOutput.replace(".json", ".contract.json")), JSON).orElseThrow();
        var expectedSubject = TaskContractProposalReference.readVerified(
                tempDir, "ws-1", proposalRef, JSON).orElseThrow();
        assertEquals(expectedSubject.proposalId(), sidecar.reviewSubject().proposalId());
        assertEquals(expectedSubject.sha256(), sidecar.reviewSubject().sha256());
    }

    @Test
    void codexReviewCannotPublishWithoutRequiredProposalSubject() throws Exception {
        var reviewOutput = write(".agent/workstreams/ws-1/runs/run-2/outputs/review.json",
                JSON.writeValueAsString(new AgentResultEnvelope(
                        1, "ws-1", "run-2", "intake.codex_product_review", productReviewResult())));
        var reviewNode = new NodeDefinition(
                "intake.codex_product_review", NodeKind.TASK, "acpx.agent_run", Map.of(
                        "expected_artifact", "agent_output_contract",
                        "review_subject_artifact", "task_contract_proposal"));

        var validated = validator.validate(
                tempDir, "ws-1", "node-review", reviewNode, List.of(),
                NodeResult.completed("completed", List.of(reviewOutput), Map.of("run_id", "run-2")));

        assertEquals(NodeStatus.FAILED, validated.status());
        assertTrue(validated.result().toString().contains("REVIEW_SUBJECT_MISSING"), validated.result().toString());
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
        var path = tempDir.resolve(ref);
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        Files.writeString(path, content);
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

    private static AgentResultEnvelope.AgentOutputResult productReviewResult() {
        return new AgentResultEnvelope.AgentOutputResult(
                "agent_output_contract", "READY_FOR_PRODUCT_REVIEW", "Product review is ready.", "READY", false,
                List.of("reviewed proposal"), List.of("proposal matches requirement"), List.of(), List.of(),
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
