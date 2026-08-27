package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.dual.core.contract.TaskContract;
import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeResult;
import dev.dual.core.workflow.NodeStatus;
import dev.dual.ledger.file.NodeInstanceDocument;
import dev.dual.ledger.file.WorkflowIterationDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArtifactContractValidatorTest {
    private static final ObjectMapper JSON = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    private static final ObjectMapper LEDGER_JSON = new ObjectMapper().registerModule(new JavaTimeModule());

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
        assertEquals(
                AgentOutputContractSidecar.sha256(tempDir.resolve("review.contract.json")),
                validated.result().get("contract_sidecar_sha256"));
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
    void repeatedReusableReadsDoNotInvalidateAnOtherwiseValidArtifact() throws Exception {
        var outputRef = write(".agent/workstreams/ws-1/runs/run-1/outputs/design.json",
                envelope("design.claude_design", reviewResult()));
        var runRef = ".agent/workstreams/ws-1/runs/run-1/run.json";
        write(runRef, JSON.writeValueAsString(Map.of(
                "toolUsage", Map.of("readFiles", List.of("REQUIREMENT.md", "VERIFICATION-GATE.md")))));
        write(".agent/workstreams/ws-1/memory/read-reuse-contract.json", JSON.writeValueAsString(Map.of(
                "targetNodeDefinitionId", "design.claude_design",
                "reusableReadFiles", List.of(
                        Map.of("path", "REQUIREMENT.md"),
                        Map.of("path", "VERIFICATION-GATE.md")),
                "invalidatedReadFiles", List.of(),
                "allowedRereadFiles", List.of())));

        var validated = validator.validate(
                tempDir, "ws-1", "node-design", node("design.claude_design"),
                NodeResult.completed("completed", List.of(outputRef), Map.of(
                        "run_id", "run-1",
                        "run_metadata", runRef)));

        assertEquals(NodeStatus.COMPLETED, validated.status(), validated.result().toString());
        assertEquals("READY_FOR_ARCH_REVIEW", validated.result().get("decision"));
        assertTrue(validated.outputRefs().contains(
                ".agent/workstreams/ws-1/runs/run-1/outputs/design.contract.json"));
    }

    @Test
    void nonIntakeReviewMayOmitContractObligationsWithoutProjectionFailure() throws Exception {
        var candidate = (com.fasterxml.jackson.databind.node.ObjectNode) JSON.readTree(
                envelope("design.codex_arch_review", reviewResult()));
        ((com.fasterxml.jackson.databind.node.ObjectNode) candidate.path("result"))
                .remove("contract_obligations");
        var ref = write("review-without-obligations.json", JSON.writeValueAsString(candidate));

        var validated = validate("design.codex_arch_review", ref, "run-1");

        assertEquals(NodeStatus.COMPLETED, validated.status(), validated.result().toString());
        var sidecar = AgentOutputContractDocument.read(
                tempDir.resolve("review-without-obligations.contract.json"), JSON).orElseThrow();
        assertTrue(sidecar.contractObligations().isEmpty());
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
                emptyFailure(), List.of());
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
        var contract = AgentOutputContractDocument.read(tempDir.resolve("plan.contract.json"), JSON).orElseThrow();
        assertTrue(contract.planArtifact() != null);
        assertEquals(List.of("edit the target"), contract.planArtifact().implementationSteps());
        assertTrue(contract.hasRequiredTypedPayload());
        assertTrue(Files.isRegularFile(tempDir.resolve(".agent/workstreams/ws-1/artifacts/plan/plan_artifact.json")));
    }

    @Test
    void taskProposalIsMechanicallyValidatedBeforeSidecarPublication() throws Exception {
        Files.writeString(tempDir.resolve("README.md"), "# sample\n");
        var proposal = new AgentResultEnvelope.TaskContractProposalResult(
                "task_contract_proposal", "READY_FOR_PRODUCT_REVIEW", "Proposal ready.", false,
                "Update documentation.", modifyScope("README.md", List.of("application code")),
                clauses("AC", "README is updated", "No source files are changed"),
                clauses("TEST", "inspect diff"), clauses("CONSTRAINT", "documentation only"),
                clauses("NON_GOAL", "no source changes"), clauses("RISK", "scope creep"),
                List.of("README.md"), List.of(), List.of());
        var ref = write(".agent/workstreams/ws-1/runs/run-1/outputs/intake.json",
                envelope("intake.claude_brainstorm", proposal));
        var validated = validate("intake.claude_brainstorm", ref, "run-1");

        assertEquals(NodeStatus.COMPLETED, validated.status());
        assertTrue(validated.outputRefs().contains(
                ".agent/workstreams/ws-1/runs/run-1/outputs/intake.contract.json"));
        assertTrue(validated.outputRefs().stream().anyMatch(value -> value.endsWith(".ref.json")));
    }

    @Test
    void taskProposalCarriesTypedCreateAndModifyScopeThroughValidation() throws Exception {
        Files.writeString(tempDir.resolve("README.md"), "# sample\n");
        var proposal = new AgentResultEnvelope.TaskContractProposalResult(
                "task_contract_proposal", "READY_FOR_PRODUCT_REVIEW", "Proposal ready.", false,
                "Add an API.", new AgentResultEnvelope.ProposalScope(
                        List.of(
                                new AgentResultEnvelope.ProposalScopePath("README.md"),
                                new AgentResultEnvelope.ProposalScopePath("src/issueApi.js")),
                        List.of("package.json")),
                clauses("AC", "API exists", "Tests pass"), clauses("TEST", "run tests"),
                clauses("CONSTRAINT", "keep scope bounded"), clauses("NON_GOAL", "no dependencies"),
                List.of(), List.of("README.md"), List.of(), List.of());
        var ref = write(".agent/workstreams/ws-1/runs/run-1/outputs/intake.json",
                envelope("intake.claude_brainstorm", proposal));

        var validated = validate("intake.claude_brainstorm", ref, "run-1");

        assertEquals(NodeStatus.COMPLETED, validated.status(), validated.result().toString());
        var sidecar = AgentOutputContractDocument.read(
                tempDir.resolve(".agent/workstreams/ws-1/runs/run-1/outputs/intake.contract.json"),
                new ObjectMapper()).orElseThrow();
        assertEquals(TaskContract.ScopeOperation.CREATE,
                sidecar.taskContractProposal().scope().included().get(1).operation());
        assertEquals("src/issueApi.js",
                sidecar.taskContractProposal().scope().included().get(1).path());
    }

    @Test
    void capturedV9NewPathsResolveToCreateBeforeSidecarPublication() throws Exception {
        var proposal = new AgentResultEnvelope.TaskContractProposalResult(
                "task_contract_proposal", "READY_FOR_PRODUCT_REVIEW", "Proposal ready.", false,
                "Create a new application.", new AgentResultEnvelope.ProposalScope(
                        List.of(
                                new AgentResultEnvelope.ProposalScopePath("pom.xml"),
                                new AgentResultEnvelope.ProposalScopePath("README.md")),
                        List.of()),
                clauses("AC", "Change is visible", "Tests pass"), clauses("TEST", "run tests"),
                clauses("CONSTRAINT", "keep scope bounded"), List.of(), List.of(), List.of(), List.of(), List.of());
        var ref = write(".agent/workstreams/ws-1/runs/run-1/outputs/intake.json",
                envelope("intake.claude_brainstorm", proposal));

        var validated = validate("intake.claude_brainstorm", ref, "run-1");

        assertEquals(NodeStatus.COMPLETED, validated.status(), validated.result().toString());
        var sidecar = AgentOutputContractDocument.read(
                tempDir.resolve(".agent/workstreams/ws-1/runs/run-1/outputs/intake.contract.json"),
                JSON).orElseThrow();
        assertTrue(sidecar.taskContractProposal().scope().included().stream()
                .allMatch(entry -> entry.operation() == TaskContract.ScopeOperation.CREATE));
    }

    @Test
    void existingPathResolvesToModifyBeforeSidecarPublication() throws Exception {
        Files.writeString(tempDir.resolve("README.md"), "# sample\n");
        var proposal = new AgentResultEnvelope.TaskContractProposalResult(
                "task_contract_proposal", "READY_FOR_PRODUCT_REVIEW", "Proposal ready.", false,
                "Update an existing file.", new AgentResultEnvelope.ProposalScope(
                        List.of(new AgentResultEnvelope.ProposalScopePath("README.md")),
                        List.of()),
                clauses("AC", "Change is visible", "Tests pass"), clauses("TEST", "run tests"),
                clauses("CONSTRAINT", "keep scope bounded"), List.of(), List.of(), List.of("README.md"), List.of(), List.of());
        var ref = write(".agent/workstreams/ws-1/runs/run-1/outputs/intake.json",
                envelope("intake.claude_brainstorm", proposal));

        var validated = validate("intake.claude_brainstorm", ref, "run-1");

        assertEquals(NodeStatus.COMPLETED, validated.status(), validated.result().toString());
        var sidecar = AgentOutputContractDocument.read(
                tempDir.resolve(".agent/workstreams/ws-1/runs/run-1/outputs/intake.contract.json"),
                JSON).orElseThrow();
        assertEquals(TaskContract.ScopeOperation.MODIFY,
                sidecar.taskContractProposal().scope().included().getFirst().operation());
    }

    @Test
    void proposalRetriesPublishDistinctImmutableReferences() throws Exception {
        Files.writeString(tempDir.resolve("README.md"), "# sample\n");
        var first = new AgentResultEnvelope.TaskContractProposalResult(
                "task_contract_proposal", "READY_FOR_PRODUCT_REVIEW", "First proposal.", false,
                "Update documentation.", modifyScope("README.md", List.of()),
                clauses("AC", "README is updated", "No source files are changed"),
                clauses("TEST", "inspect diff"), clauses("CONSTRAINT", "documentation only"),
                List.of(), List.of(), List.of("README.md"), List.of(), List.of());
        var second = new AgentResultEnvelope.TaskContractProposalResult(
                "task_contract_proposal", "READY_FOR_PRODUCT_REVIEW", "Revised proposal.", false,
                "Update documentation safely.", modifyScope("README.md", List.of()),
                clauses("AC", "README is updated safely", "No source files are changed"),
                clauses("TEST", "inspect diff"), clauses("CONSTRAINT", "documentation only"),
                List.of(), List.of(), List.of("README.md"), List.of(), List.of());

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
    void revisionFullProposalCannotBypassPatchContractOrPublishReference() throws Exception {
        Files.writeString(tempDir.resolve("README.md"), "# sample\n");
        var original = new AgentResultEnvelope.TaskContractProposalResult(
                "task_contract_proposal", "READY_FOR_PRODUCT_REVIEW", "Original proposal.", false,
                "Register users safely.", modifyScope("README.md", List.of()),
                clauses("AC", "Registration verifies identity", "Login requires captcha"),
                clauses("TEST", "Run registration tests"),
                List.of(
                        new AgentResultEnvelope.ContractClause("C-1", "Keep identity handling explicit"),
                        new AgentResultEnvelope.ContractClause("C-2", "Preserve the local part of email addresses")),
                List.of(), List.of(), List.of("README.md"), List.of(), List.of());
        var originalOutput = write(".agent/workstreams/ws-1/runs/run-original/outputs/intake.json",
                JSON.writeValueAsString(new AgentResultEnvelope(
                        1, "ws-1", "run-original", "intake.claude_brainstorm", original)));
        var originalResult = validate("intake.claude_brainstorm", originalOutput, "run-original");
        var originalRef = originalResult.outputRefs().stream()
                .filter(ref -> ref.endsWith(".ref.json"))
                .findFirst()
                .orElseThrow();
        AgentOutputLineageFixture.publish(
                tempDir,
                "ws-1",
                tempDir.resolve(originalOutput.replace(".json", ".contract.json")),
                "iter-001");

        var obligations = List.of(new AgentResultEnvelope.ReviewObligation(
                "CO-1", "Make registration authorization atomic"));
        var reviewOutput = write(".agent/workstreams/ws-1/runs/run-rejected/outputs/review.json",
                JSON.writeValueAsString(new AgentResultEnvelope(
                        1, "ws-1", "run-rejected", "intake.codex_product_review",
                        contractRevisionReview(obligations))));
        var reviewNode = new NodeDefinition(
                "intake.codex_product_review", NodeKind.TASK, "acpx.agent_run", Map.of(
                        "expected_artifact", "agent_output_contract",
                        "review_subject_artifact", "task_contract_proposal"));
        var reviewResult = validator.validate(
                tempDir, "ws-1", "node-review-rejected", reviewNode, List.of(originalRef),
                NodeResult.completed("completed", List.of(reviewOutput), Map.of("run_id", "run-rejected")));
        assertEquals(NodeStatus.COMPLETED, reviewResult.status(), reviewResult.result().toString());
        var reviewSidecar = tempDir.resolve(reviewOutput.replace(".json", ".contract.json"));
        AgentOutputLineageFixture.publish(tempDir, "ws-1", reviewSidecar, "iter-001");
        var reviewRef = tempDir.toAbsolutePath().normalize()
                .relativize(reviewSidecar.toAbsolutePath().normalize()).toString();
        writeRevisionLineage(reviewRef, "node-revision", "iter-002");

        var revised = new AgentResultEnvelope.TaskContractProposalResult(
                "task_contract_proposal", "READY_FOR_PRODUCT_REVIEW", "Revised proposal.", false,
                "Register users safely.", modifyScope("README.md", List.of()),
                clauses("AC", "Registration atomically verifies identity", "Login requires captcha"),
                clauses("TEST", "Run registration tests"),
                List.of(
                        new AgentResultEnvelope.ContractClause("C-1", "Keep identity handling explicit"),
                        new AgentResultEnvelope.ContractClause("C-2", "Lowercase complete email addresses")),
                List.of(), List.of(), List.of("README.md"), List.of(),
                List.of(new AgentResultEnvelope.RevisionResponse("CO-1", List.of("AC-1"))));
        var revisedOutput = write(".agent/workstreams/ws-1/runs/run-revision/outputs/intake.json",
                JSON.writeValueAsString(new AgentResultEnvelope(
                        1, "ws-1", "run-revision", "intake.claude_brainstorm", revised)));

        var validated = validator.validate(
                tempDir, "ws-1", "node-revision", node("intake.claude_brainstorm"),
                NodeResult.completed("completed", List.of(revisedOutput), Map.of("run_id", "run-revision")));

        assertEquals(NodeStatus.FAILED, validated.status(), validated.result().toString());
        var artifact = AgentExecutionResultFacts.artifactContractResults(validated).getFirst();
        assertEquals("TASK_CONTRACT_REVISION_SHAPE_INVALID", artifact.errorCode());
        assertTrue(validated.result().toString().contains("revision iteration requires task_contract_revision_patch"),
                validated.result().toString());
        assertTrue(validated.outputRefs().stream().noneMatch(ref -> ref.endsWith(".ref.json")),
                "an invalid revision must not publish a trusted proposal reference");

        var baseReference = TaskContractProposalReference.readVerified(
                tempDir, "ws-1", originalRef, JSON).orElseThrow();
        var patch = new AgentResultEnvelope.TaskContractRevisionPatchResult(
                "task_contract_revision_patch",
                "READY_FOR_PRODUCT_REVIEW",
                "Resolve the rejected atomic-registration obligation.",
                baseReference.proposalId(),
                baseReference.sha256(),
                List.of(new AgentResultEnvelope.ObligationPatch(
                        "CO-1",
                        List.of("AC-1"),
                        List.of(new AgentResultEnvelope.ClauseChange(
                                AgentResultEnvelope.ClauseChangeOperation.REPLACE,
                                AgentResultEnvelope.ClauseSection.ACCEPTANCE_CRITERIA,
                                "AC-1",
                                "Registration atomically verifies identity")),
                        List.of(),
                        List.of())));
        var patchOutput = write(".agent/workstreams/ws-1/runs/run-patch/outputs/intake.json",
                JSON.writeValueAsString(new AgentResultEnvelope(
                        1, "ws-1", "run-patch", "intake.claude_brainstorm", patch)));

        var patchValidated = validator.validate(
                tempDir, "ws-1", "node-revision", node("intake.claude_brainstorm"),
                NodeResult.completed("completed", List.of(patchOutput), Map.of("run_id", "run-patch")));

        assertEquals(NodeStatus.COMPLETED, patchValidated.status(), patchValidated.result().toString());
        var patchSidecarRef = patchValidated.outputRefs().stream()
                .filter(ref -> ref.endsWith(".contract.json"))
                .findFirst()
                .orElseThrow();
        var patchSidecar = AgentOutputContractDocument.read(tempDir.resolve(patchSidecarRef), JSON).orElseThrow();
        assertEquals("task_contract_revision_patch", patchSidecar.sourceArtifactType());
        assertEquals("revision_patch_merge", patchSidecar.proposalDerivation().kind());
        assertEquals(baseReference, patchSidecar.proposalDerivation().baseProposalReference());
        assertEquals(patchOutput, patchSidecar.proposalDerivation().patchSourceOutputRef());
        assertEquals("Registration atomically verifies identity",
                patchSidecar.taskContractProposal().acceptanceCriteria().getFirst().text());
        assertEquals("Preserve the local part of email addresses",
                patchSidecar.taskContractProposal().constraints().get(1).text(),
                "untouched baseline clauses must remain byte-for-byte equivalent");
        assertEquals(List.of(new TaskContractProposal.RevisionResponse("CO-1", List.of("AC-1"))),
                patchSidecar.taskContractProposal().revisionResponses());
        var patchReferenceRef = patchValidated.outputRefs().stream()
                .filter(ref -> ref.endsWith(".ref.json"))
                .findFirst()
                .orElseThrow();
        assertTrue(TaskContractProposalReference.readVerified(
                tempDir, "ws-1", patchReferenceRef, JSON).isPresent());

        var sidecarMap = JSON.readValue(
                tempDir.resolve(patchSidecarRef).toFile(), new TypeReference<Map<String, Object>>() {});
        sidecarMap.remove("proposal_derivation");
        var missingDerivation = AgentOutputContractDocument.from(sidecarMap);
        assertThrows(IllegalArgumentException.class, () -> TaskContractProposalReference.publishRevisionDerived(
                tempDir, "ws-1", tempDir.resolve(patchSidecarRef), missingDerivation, JSON));
    }

    @Test
    void codexReviewSidecarBindsTrustedProposalSubjectFromNodeInputs() throws Exception {
        Files.writeString(tempDir.resolve("README.md"), "# sample\n");
        var proposal = new AgentResultEnvelope.TaskContractProposalResult(
                "task_contract_proposal", "READY_FOR_PRODUCT_REVIEW", "Proposal ready.", false,
                "Update documentation.", modifyScope("README.md", List.of()),
                clauses("AC", "README is updated", "No source files are changed"), clauses("TEST", "inspect diff"),
                clauses("CONSTRAINT", "documentation only"), clauses("NON_GOAL", "no source changes"), List.of(),
                List.of("README.md"), List.of(), List.of());
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
    void intakeContractRevisionRequiresTypedObligationsAndPublishesThem() throws Exception {
        Files.writeString(tempDir.resolve("README.md"), "# sample\n");
        var proposal = new AgentResultEnvelope.TaskContractProposalResult(
                "task_contract_proposal", "READY_FOR_PRODUCT_REVIEW", "Proposal ready.", false,
                "Update documentation.", modifyScope("README.md", List.of()),
                clauses("AC", "README is updated", "No source files are changed"),
                clauses("TEST", "inspect diff"), clauses("CONSTRAINT", "documentation only"),
                List.of(), List.of(), List.of("README.md"), List.of(), List.of());
        var proposalOutput = write(".agent/workstreams/ws-1/runs/run-1/outputs/intake.json",
                envelope("intake.claude_brainstorm", proposal));
        var proposalResult = validate("intake.claude_brainstorm", proposalOutput, "run-1");
        var proposalRef = proposalResult.outputRefs().stream()
                .filter(value -> value.endsWith(".ref.json"))
                .findFirst()
                .orElseThrow();
        var reviewNode = new NodeDefinition(
                "intake.codex_product_review", NodeKind.TASK, "acpx.agent_run", Map.of(
                        "expected_artifact", "agent_output_contract",
                        "review_subject_artifact", "task_contract_proposal"));

        var missingOutput = write(".agent/workstreams/ws-1/runs/run-2/outputs/review.json",
                JSON.writeValueAsString(new AgentResultEnvelope(
                        1, "ws-1", "run-2", "intake.codex_product_review",
                        contractRevisionReview(List.of()))));
        var missing = validator.validate(
                tempDir, "ws-1", "node-review", reviewNode, List.of(proposalRef),
                NodeResult.completed("completed", List.of(missingOutput), Map.of("run_id", "run-2")));

        assertEquals(NodeStatus.FAILED, missing.status());
        assertTrue(missing.result().toString().contains("contract_obligations"), missing.result().toString());

        var obligation = new AgentResultEnvelope.ReviewObligation("OB-1", "Add observable acceptance coverage");
        var validOutput = write(".agent/workstreams/ws-1/runs/run-3/outputs/review.json",
                JSON.writeValueAsString(new AgentResultEnvelope(
                        1, "ws-1", "run-3", "intake.codex_product_review",
                        contractRevisionReview(List.of(obligation)))));
        var valid = validator.validate(
                tempDir, "ws-1", "node-review-valid", reviewNode, List.of(proposalRef),
                NodeResult.completed("completed", List.of(validOutput), Map.of("run_id", "run-3")));

        assertEquals(NodeStatus.COMPLETED, valid.status(), valid.result().toString());
        var sidecar = AgentOutputContractDocument.read(
                tempDir.resolve(validOutput.replace(".json", ".contract.json")), JSON).orElseThrow();
        assertEquals(List.of(obligation), sidecar.contractObligations());
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
        if ("intake.claude_brainstorm".equals(nodeId)) {
            writeInitialIntakeLineage("node-instance-1", "iter-001");
        }
        return validator.validate(
                tempDir, "ws-1", "node-instance-1", node(nodeId),
                NodeResult.completed("completed", List.of(outputRef), Map.of("run_id", runId, "agent", "test")));
    }

    private void writeInitialIntakeLineage(String nodeInstanceId, String iterationId) throws Exception {
        writeTrackedInventoryFixture();
        var iterationRef = ".agent/workstreams/ws-1/iterations/" + iterationId + ".json";
        if (!Files.isRegularFile(tempDir.resolve(iterationRef))) {
            var iterationPath = tempDir.resolve(iterationRef);
            Files.createDirectories(iterationPath.getParent());
            LEDGER_JSON.writeValue(iterationPath.toFile(),
                    WorkflowIterationDocument.initial(
                            "intake.claude_brainstorm", Instant.parse("2026-08-07T00:00:00Z")));
        }
        var nodeRef = ".agent/workstreams/ws-1/nodes/" + nodeInstanceId + ".json";
        if (!Files.isRegularFile(tempDir.resolve(nodeRef))) {
            write(nodeRef, JSON.writeValueAsString(Map.ofEntries(
                    Map.entry("schemaVersion", 1),
                    Map.entry("nodeInstanceId", nodeInstanceId),
                    Map.entry("nodeDefinitionId", "intake.claude_brainstorm"),
                    Map.entry("kind", "TASK"),
                    Map.entry("handlerType", "acpx.agent_run"),
                    Map.entry("status", "RUNNING"),
                    Map.entry("stageId", "stage-intake"),
                    Map.entry("iterationId", iterationId),
                    Map.entry("inputRefs", List.of()),
                    Map.entry("outputRefs", List.of()),
                    Map.entry("result", Map.of()))));
        }
    }

    private void writeTrackedInventoryFixture() throws Exception {
        var files = new java.util.ArrayList<Map<String, Object>>();
        try (var paths = Files.walk(tempDir)) {
            for (var path : paths.filter(Files::isRegularFile).toList()) {
                var ref = tempDir.relativize(path).toString().replace('\\', '/');
                if (!ref.startsWith(".agent/")) {
                    files.add(Map.of("path", ref));
                }
            }
        }
        files.sort(java.util.Comparator.comparing(item -> String.valueOf(item.get("path"))));
        write(".agent/workstreams/ws-1/memory/repo-map.json", JSON.writeValueAsString(Map.of(
                "artifactType", "repo_map",
                "files", files)));
    }

    private void writeRevisionLineage(String rejectedReviewRef, String revisionNodeId, String iterationId)
            throws Exception {
        var workstreamDir = tempDir.resolve(".agent/workstreams/ws-1");
        var nodes = workstreamDir.resolve("nodes");
        var iterations = workstreamDir.resolve("iterations");
        Files.createDirectories(nodes);
        Files.createDirectories(iterations);
        var now = Instant.parse("2026-08-07T00:00:00Z");
        var triggerId = "node-intake-decision-revision";
        LEDGER_JSON.writeValue(nodes.resolve(triggerId + ".json").toFile(), new NodeInstanceDocument(
                1, triggerId, "intake.decision", NodeKind.DECISION, "review.decision",
                NodeStatus.COMPLETED, "stage-intake", "iter-001", List.of(rejectedReviewRef), Map.of(),
                List.of(rejectedReviewRef), Map.of("decision", "NEEDS_REVISION"), "", "", now, now));
        LEDGER_JSON.writeValue(iterations.resolve(iterationId + ".json").toFile(),
                WorkflowIterationDocument.revision(
                        iterationId, 2, "iter-001", triggerId, "NEEDS_REVISION",
                        "intake.claude_brainstorm", "Resolve contract obligations", now));
        LEDGER_JSON.writeValue(nodes.resolve(revisionNodeId + ".json").toFile(), new NodeInstanceDocument(
                1, revisionNodeId, "intake.claude_brainstorm", NodeKind.TASK, "acpx.agent_run",
                NodeStatus.RUNNING, "stage-intake", iterationId, List.of(rejectedReviewRef), Map.of(),
                List.of(), Map.of(), "", "", now, null));
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
                List.of(), List.of(), List.of(), "review complete", emptyFailure(), List.of());
    }

    private static AgentResultEnvelope.AgentOutputResult productReviewResult() {
        return new AgentResultEnvelope.AgentOutputResult(
                "agent_output_contract", "READY_FOR_PRODUCT_REVIEW", "Product review is ready.", "READY", false,
                List.of("reviewed proposal"), List.of("proposal matches requirement"), List.of(), List.of(),
                List.of(), List.of(), List.of(), "review complete", emptyFailure(), List.of());
    }

    private static AgentResultEnvelope.AgentOutputResult contractRevisionReview(
            List<AgentResultEnvelope.ReviewObligation> obligations
    ) {
        return new AgentResultEnvelope.AgentOutputResult(
                "agent_output_contract", "NEEDS_REVISION", "Contract revision is required.", "READY", true,
                List.of("reviewed proposal"), List.of("proposal misses an acceptance obligation"),
                List.of(), List.of("add acceptance coverage"), obligations,
                List.of(), List.of("contract gap"), List.of(), "review complete",
                new AgentResultEnvelope.Failure(
                        "CONTRACT_GAP", AgentResultEnvelope.VerificationRemediation.NONE,
                        List.of("acceptance coverage is missing"), List.of(), List.of(), List.of(),
                        "proposal omits required behavior"),
                List.of());
    }

    private static AgentResultEnvelope.Failure emptyFailure() {
        return new AgentResultEnvelope.Failure("", AgentResultEnvelope.VerificationRemediation.NONE,
                List.of(), List.of(), List.of(), List.of(), "");
    }

    private static AgentResultEnvelope.ProposalScope modifyScope(
            String path,
            List<String> excluded
    ) {
        return new AgentResultEnvelope.ProposalScope(
                List.of(new AgentResultEnvelope.ProposalScopePath(path)),
                excluded);
    }

    private static List<AgentResultEnvelope.ContractClause> clauses(String prefix, String... values) {
        var clauses = new java.util.ArrayList<AgentResultEnvelope.ContractClause>();
        for (int index = 0; index < values.length; index++) {
            clauses.add(new AgentResultEnvelope.ContractClause(prefix + "-" + (index + 1), values[index]));
        }
        return List.copyOf(clauses);
    }

    @SuppressWarnings("unchecked")
    private static String artifactContractType(NodeResult result) {
        var contracts = (List<Map<String, Object>>) result.result().get(AgentExecutionResultFacts.ARTIFACT_CONTRACT_RESULTS);
        return String.valueOf(contracts.getFirst().get("contractType"));
    }
}
