package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.dual.core.contract.TaskContract;
import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeInstance;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContractCompileHandlerTest {
    private static final String WORKSTREAM_ID = "ws-test-001";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    private static final ObjectMapper LEDGER_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void handlerTypeIsContractCompile() {
        assertEquals("contract.compile", new ContractCompileHandler().type());
    }

    @Test
    void missingApprovedProposalFailsClosed(@TempDir Path root) throws Exception {
        prepare(root);

        var result = new ContractCompileHandler().execute(context(root));

        assertEquals(NodeStatus.FAILED, result.status());
        assertEquals("TASK_CONTRACT_PROPOSAL_APPROVAL_MISSING", result.outcome());
        assertFalse(Files.exists(contractPath(root)));
    }

    @Test
    void reviewMarkdownCannotBecomeCanonicalContract(@TempDir Path root) throws Exception {
        prepare(root);
        var review = outputDir(root, "run-review").resolve("intake-codex-product-review.md");
        Files.writeString(review, "Objective: silently replace the proposal");

        var result = new ContractCompileHandler().execute(context(root, List.of(relative(root, review))));

        assertEquals("TASK_CONTRACT_PROPOSAL_APPROVAL_MISSING", result.outcome());
        assertFalse(Files.exists(contractPath(root)));
    }

    @Test
    void approvedStrictProposalCompilesCanonicalContract(@TempDir Path root) throws Exception {
        prepare(root);
        Files.createDirectories(root.resolve("src"));
        Files.writeString(root.resolve("src/issueWorkflow.js"), "export {}\n");
        var proposalRef = writeTypedProposal(
                root,
                "run-001",
                "Add mandatory READY_FOR_REVIEW status",
                List.of(
                        new TaskContract.ScopeEntry(
                                "src/issueWorkflow.js", TaskContract.ScopeOperation.MODIFY),
                        new TaskContract.ScopeEntry(
                                "src/issueApi.js", TaskContract.ScopeOperation.CREATE)),
                List.of(),
                TaskContractProposal.SCHEMA_VERSION);
        var reviewRef = writeApprovedReview(root, proposalRef, "run-review", true);

        var result = new ContractCompileHandler().execute(context(root, List.of(reviewRef)));

        assertEquals(NodeStatus.COMPLETED, result.status(), result.result().toString());
        var contract = OBJECT_MAPPER.readValue(contractPath(root).toFile(), TaskContract.class);
        assertEquals("Add mandatory READY_FOR_REVIEW status", contract.objective());
        assertEquals(List.of(
                new TaskContract.ScopeEntry(
                        "src/issueWorkflow.js", TaskContract.ScopeOperation.MODIFY),
                new TaskContract.ScopeEntry(
                        "src/issueApi.js", TaskContract.ScopeOperation.CREATE)), contract.scope().included());
    }

    @Test
    void legacyV1ProposalSidecarIsExplicitlyRejected(@TempDir Path root) throws Exception {
        prepare(root);
        var proposalRef = writeTypedProposal(
                root,
                "run-v1",
                "Legacy proposal",
                List.of(new TaskContract.ScopeEntry(
                        "README.md", TaskContract.ScopeOperation.MODIFY)),
                List.of(),
                1);
        var reviewRef = writeApprovedReview(root, proposalRef, "run-review-v1", true);

        var result = new ContractCompileHandler().execute(context(root, List.of(reviewRef)));

        assertEquals(NodeStatus.FAILED, result.status());
        assertEquals("TASK_CONTRACT_PROPOSAL_INVALID", result.outcome());
        assertTrue(String.valueOf(result.result().get("validation_errors"))
                .contains("schema_version must be 2"));
        assertFalse(Files.exists(contractPath(root)));
    }

    @Test
    @SuppressWarnings("unchecked")
    void v2ProposalWithLegacyStringScopeIsExplicitlyRejected(@TempDir Path root) throws Exception {
        prepare(root);
        var proposalRef = writeTypedProposal(
                root,
                "run-v2-string-scope",
                "Malformed v2 proposal",
                List.of(new TaskContract.ScopeEntry(
                        "README.md", TaskContract.ScopeOperation.MODIFY)),
                List.of(),
                TaskContractProposal.SCHEMA_VERSION);
        var proposalPath = root.resolve(proposalRef);
        var proposalDocument = OBJECT_MAPPER.readValue(proposalPath.toFile(), Map.class);
        var proposal = (Map<String, Object>) proposalDocument.get("task_contract_proposal");
        var scope = (Map<String, Object>) proposal.get("scope");
        scope.put("included", List.of("README.md"));
        OBJECT_MAPPER.writeValue(proposalPath.toFile(), proposalDocument);
        AgentOutputLineageFixture.publish(root, WORKSTREAM_ID, proposalPath);
        var reviewRef = writeApprovedReview(root, proposalRef, "run-review-v2-string-scope", true);

        var result = new ContractCompileHandler().execute(context(root, List.of(reviewRef)));

        assertEquals(NodeStatus.FAILED, result.status());
        assertEquals("TASK_CONTRACT_PROPOSAL_INVALID", result.outcome());
        var validationErrors = String.valueOf(result.result().get("validation_errors"));
        assertTrue(validationErrors.contains("scope.included contains blank path"), validationErrors);
        assertFalse(Files.exists(contractPath(root)));
    }

    @Test
    void unapprovedReviewCannotCompileProposal(@TempDir Path root) throws Exception {
        prepare(root);
        var proposalRef = writeProposal(root, "run-001", "objective", List.of("README.md"), List.of());
        var reviewRef = writeApprovedReview(root, proposalRef, "run-review", false);

        var result = new ContractCompileHandler().execute(context(root, List.of(reviewRef)));

        assertEquals("TASK_CONTRACT_PROPOSAL_APPROVAL_MISSING", result.outcome());
        assertFalse(Files.exists(contractPath(root)));
    }

    @Test
    void nonCanonicalRequestedActionCannotMasqueradeAsLedgerApproval(@TempDir Path root) throws Exception {
        prepare(root);
        var proposalRef = writeProposal(root, "run-001", "objective", List.of("README.md"), List.of());
        var reviewRef = writeApprovedReview(root, proposalRef, "run-review", true);
        var gatePath = workstreamDir(root).resolve("gates/gate-run-review.json");
        Files.writeString(gatePath, Files.readString(gatePath).replace("\"approved\"", "\"approve\""));

        var result = new ContractCompileHandler().execute(context(root, List.of(reviewRef)));

        assertEquals(NodeStatus.FAILED, result.status());
        assertEquals("TASK_CONTRACT_PROPOSAL_APPROVAL_MISSING", result.outcome());
    }

    @Test
    void approvalForDifferentReviewCannotCompileCurrentReview(@TempDir Path root) throws Exception {
        prepare(root);
        var proposalA = writeProposal(root, "run-a", "objective A", List.of("README.md"), List.of());
        var reviewA = writeApprovedReview(root, proposalA, "run-review-a", true);
        var proposalB = writeProposal(root, "run-b", "objective B", List.of("README.md"), List.of());
        var reviewB = writeApprovedReview(root, proposalB, "run-review-b", false);

        var result = new ContractCompileHandler().execute(context(root, List.of(reviewB)));

        assertEquals("TASK_CONTRACT_PROPOSAL_APPROVAL_MISSING", result.outcome());
        assertFalse(Files.exists(contractPath(root)));
        assertFalse(reviewA.equals(reviewB));
    }

    @Test
    void approvedProposalHashMismatchIsRejected(@TempDir Path root) throws Exception {
        prepare(root);
        var proposalRef = writeProposal(root, "run-001", "approved objective", List.of("README.md"), List.of());
        var reviewRef = writeApprovedReview(root, proposalRef, "run-review", true);
        Files.writeString(root.resolve(proposalRef), "{}\n");

        var result = new ContractCompileHandler().execute(context(root, List.of(reviewRef)));

        assertEquals("TASK_CONTRACT_PROPOSAL_INVALID", result.outcome());
        assertFalse(Files.exists(contractPath(root)));
    }

    @Test
    void approvedProposalWithOpenQuestionsProducesResolution(@TempDir Path root) throws Exception {
        prepare(root);
        var proposalRef = writeProposal(root, "run-001", "Add review", List.of("README.md"),
                List.of("Should review be mandatory?"));
        var reviewRef = writeApprovedReview(root, proposalRef, "run-review", true);

        var result = new ContractCompileHandler().execute(context(root, List.of(reviewRef)));

        assertEquals(NodeStatus.COMPLETED, result.status(), result.result().toString());
        assertTrue(Files.isRegularFile(workstreamDir(root).resolve("artifacts/contract-resolution.json")));
    }

    private static void prepare(Path root) throws Exception {
        Files.createDirectories(workstreamDir(root));
        Files.writeString(workstreamDir(root).resolve("requirement.md"), "Strict proposal fixture\n");
        Files.writeString(root.resolve("README.md"), "# fixture\n");
    }

    private static String writeProposal(
            Path root,
            String runId,
            String objective,
            List<String> included,
            List<String> questions
    ) throws Exception {
        return writeTypedProposal(
                root,
                runId,
                objective,
                included.stream()
                        .map(path -> new TaskContract.ScopeEntry(
                                path, TaskContract.ScopeOperation.MODIFY))
                        .toList(),
                questions,
                TaskContractProposal.SCHEMA_VERSION);
    }

    @SuppressWarnings("unchecked")
    private static String writeTypedProposal(
            Path root,
            String runId,
            String objective,
            List<TaskContract.ScopeEntry> included,
            List<String> questions,
            int proposalSchemaVersion
    ) throws Exception {
        var directory = outputDir(root, runId);
        var envelope = new AgentResultEnvelope(
                1, WORKSTREAM_ID, runId, "intake.claude_brainstorm",
                new AgentResultEnvelope.TaskContractProposalResult(
                        "task_contract_proposal", "READY_FOR_PRODUCT_REVIEW", "proposal ready", false,
                        objective, new AgentResultEnvelope.ProposalScope(
                                included,
                                List.of("No unrelated changes")),
                        List.of("The requested behavior is observable", "Focused verification passes"),
                        List.of("Run focused verification"), List.of("Keep the change scoped"),
                        List.of("No unrelated refactor"), List.of("Regression risk"),
                        included.stream().map(TaskContract.ScopeEntry::path).toList(), questions));
        var output = directory.resolve("intake-claude-brainstorm.json");
        var outputRef = relative(root, output);
        Files.writeString(output, OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(envelope));
        var document = new AgentResultDocumentProjector().outputContract(
                envelope, ContractRegistry.forNode("intake.claude_brainstorm"), "acpx.agent_run",
                "inst-intake-" + runId, outputRef);
        ((Map<String, Object>) document.get("task_contract_proposal"))
                .put("schema_version", proposalSchemaVersion);
        AgentContractSemantics.addSidecarFields(document);
        var sidecar = AgentOutputContractSidecar.pathFor(output);
        Files.writeString(sidecar, OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(document));
        AgentOutputLineageFixture.publish(root, WORKSTREAM_ID, sidecar);
        return relative(root, sidecar);
    }

    private static String writeApprovedReview(
            Path root,
            String proposalSidecarRef,
            String reviewRunId,
            boolean approved
    ) throws Exception {
        var proposalPath = root.resolve(proposalSidecarRef);
        var proposalDocument = AgentOutputContractDocument.read(proposalPath, OBJECT_MAPPER).orElseThrow();
        var published = TaskContractProposalReference.publish(
                root, WORKSTREAM_ID, proposalPath, proposalDocument, OBJECT_MAPPER);

        var reviewDirectory = outputDir(root, reviewRunId);
        var reviewEnvelope = new AgentResultEnvelope(
                1, WORKSTREAM_ID, reviewRunId, "intake.codex_product_review",
                new AgentResultEnvelope.AgentOutputResult(
                        "agent_output_contract", "READY_FOR_PRODUCT_REVIEW", "review ready", "READY", false,
                        List.of("reviewed proposal"), List.of("proposal is coherent"), List.of(), List.of(),
                        List.of(), List.of(), List.of(), "review complete",
                        new AgentResultEnvelope.Failure("", List.of(), List.of(), List.of(), List.of(), ""),
                        List.of()));
        var reviewOutput = reviewDirectory.resolve("intake-codex-product-review.json");
        var reviewOutputRef = relative(root, reviewOutput);
        Files.writeString(reviewOutput, OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(reviewEnvelope));
        var reviewDocument = new AgentResultDocumentProjector().outputContract(
                reviewEnvelope, ContractRegistry.forNode("intake.codex_product_review"), "acpx.agent_run",
                "inst-review-" + reviewRunId, reviewOutputRef);
        reviewDocument.put("review_subject", published.reference().toMap());
        AgentContractSemantics.addSidecarFields(reviewDocument);
        var reviewSidecar = AgentOutputContractSidecar.pathFor(reviewOutput);
        Files.writeString(reviewSidecar, OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(reviewDocument));
        AgentOutputLineageFixture.publish(root, WORKSTREAM_ID, reviewSidecar);
        var reviewRef = relative(root, reviewSidecar);
        if (approved) {
            writeGate(root, reviewRef, reviewRunId);
        }
        return reviewRef;
    }

    private static void writeGate(Path root, String reviewRef, String suffix) throws Exception {
        var gateNodeId = "node-gate-human_requirement_review-" + suffix;
        var nodes = workstreamDir(root).resolve("nodes");
        var gates = workstreamDir(root).resolve("gates");
        Files.createDirectories(nodes);
        Files.createDirectories(gates);
        var nowInstant = Instant.now();
        LEDGER_MAPPER.writeValue(nodes.resolve(gateNodeId + ".json").toFile(),
                new dev.dual.ledger.file.NodeInstanceDocument(
                        1, gateNodeId, "gate.human_requirement_review", NodeKind.GATE, "human.review",
                        NodeStatus.COMPLETED, "stage-requirement", "iteration-fixture", List.of(reviewRef), Map.of(),
                        List.of(reviewRef), Map.of("human_action", "approved"), "", "", nowInstant, nowInstant));
        var now = nowInstant.toString();
        OBJECT_MAPPER.writeValue(gates.resolve("gate-" + suffix + ".json").toFile(), Map.of(
                "schemaVersion", 1,
                "gateId", "gate-" + suffix,
                "nodeInstanceId", gateNodeId,
                "gateType", "human.review",
                "status", "resolved",
                "allowedActions", List.of("approve", "revise"),
                "openedAt", now,
                "resolvedAt", now,
                "result", Map.of(
                        "human_action", "approved",
                        "note", "approved fixture",
                        "subject_input_refs", List.of(reviewRef))));
    }

    private static TaskHandler.TaskContext context(Path root) {
        return context(root, List.of());
    }

    private static TaskHandler.TaskContext context(Path root, List<String> inputRefs) {
        return new TaskHandler.TaskContext(
                root, WORKSTREAM_ID,
                new NodeDefinition("contract.compile", NodeKind.TASK, "contract.compile",
                        Map.of("required_subject", "approved_task_contract_proposal")),
                new NodeInstance("inst-contract", "contract.compile", NodeKind.TASK, "contract.compile",
                        NodeStatus.READY, "stage-contract", "iter-1", inputRefs, List.of(), Map.of(), null,
                        Instant.now(), null));
    }

    private static String relative(Path root, Path path) {
        return root.toAbsolutePath().normalize().relativize(path.toAbsolutePath().normalize()).toString();
    }

    private static Path workstreamDir(Path root) {
        return root.resolve(".agent/workstreams").resolve(WORKSTREAM_ID);
    }

    private static Path outputDir(Path root, String runId) throws Exception {
        var path = workstreamDir(root).resolve("runs").resolve(runId).resolve("outputs");
        Files.createDirectories(path);
        return path;
    }

    private static Path contractPath(Path root) {
        return workstreamDir(root).resolve("artifacts/task-contract.json");
    }
}
