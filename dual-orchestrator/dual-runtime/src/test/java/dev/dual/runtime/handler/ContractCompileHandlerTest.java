package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.dual.core.contract.TaskContract;
import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeInstance;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContractCompileHandlerTest {
    private static final String WORKSTREAM_ID = "ws-test-001";
    private static final String INITIAL_ITERATION_ID = "iter-001";
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

        var result = compile(context(root));

        assertEquals(NodeStatus.FAILED, result.status());
        assertEquals("TASK_CONTRACT_PROPOSAL_APPROVAL_MISSING", result.outcome());
        assertFalse(Files.exists(contractPath(root)));
    }

    @Test
    void reviewMarkdownCannotBecomeCanonicalContract(@TempDir Path root) throws Exception {
        prepare(root);
        var review = outputDir(root, "run-review").resolve("intake-codex-product-review.md");
        Files.writeString(review, "Objective: silently replace the proposal");

        var result = compile(context(root, List.of(relative(root, review))));

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

        var result = compile(context(root, List.of(reviewRef)));

        assertEquals(NodeStatus.COMPLETED, result.status(), result.result().toString());
        var contract = OBJECT_MAPPER.readValue(contractPath(root).toFile(), TaskContract.class);
        assertEquals("Add mandatory READY_FOR_REVIEW status", contract.objective());
        assertEquals(TaskContract.Phase.GOAL, contract.phase());
        assertTrue(contract.scope().included().isEmpty(), "requirements approval does not grant exact implementation paths");
        assertEquals(List.of("src/issueWorkflow.js", "src/issueApi.js"), contract.evidenceRefs());
        assertEquals(TaskContractArtifactWriter.sha256(contractPath(root)),
                result.result().get("task_contract_sha256"));
    }

    @Test
    void blockedReviewApprovedWithHumanNotePreservesTypedQuestions(@TempDir Path root) throws Exception {
        prepare(root);
        var proposalRef = writeProposal(root, "run-blocked", "Choose phone input semantics",
                List.of("README.md"), List.of());
        var reviewRef = writeReview(
                root, proposalRef, "run-blocked-review", "BLOCKED", List.of(),
                INITIAL_ITERATION_ID, false);
        var reviewPath = root.resolve(reviewRef);
        var review = (com.fasterxml.jackson.databind.node.ObjectNode) OBJECT_MAPPER.readTree(reviewPath.toFile());
        review.set("must_fix", OBJECT_MAPPER.valueToTree(List.of("Choose E.164-only or local phone input")));
        review.set("missing_evidence", OBJECT_MAPPER.valueToTree(List.of("Confirm the product policy")));
        OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(reviewPath.toFile(), review);
        AgentOutputLineageFixture.publish(root, WORKSTREAM_ID, reviewPath, INITIAL_ITERATION_ID);
        writeGate(root, reviewRef, "run-blocked-review", INITIAL_ITERATION_ID,
                "BLOCKED", "Use E.164-only phone numbers");

        var result = compile(context(root, List.of(reviewRef)));

        assertEquals(NodeStatus.COMPLETED, result.status(), result.result().toString());
        var resolution = OBJECT_MAPPER.readTree(workstreamDir(root).resolve("artifacts/contracts/inst-contract/contract-resolution.json").toFile());
        assertEquals(2, resolution.path("resolved_questions").size());
        assertEquals("Choose E.164-only or local phone input",
                resolution.path("resolved_questions").get(0).path("question").asText());
        assertEquals("Confirm the product policy",
                resolution.path("resolved_questions").get(1).path("question").asText());
        for (var item : resolution.path("resolved_questions")) {
            assertEquals("Use E.164-only phone numbers", item.path("resolution").asText());
            assertEquals("human", item.path("source").asText());
        }
    }

    @Test
    void completedContractGateNodeWithoutResolvedDecisionCannotAuthorizeClarification(@TempDir Path root)
            throws Exception {
        prepare(root);
        var proposalRef = writeProposal(
                root, "run-clarify-base", "Phone authentication", List.of("README.md"), List.of());
        var reviewRef = writeApprovedReview(root, proposalRef, "run-clarify-review", true);
        var compiled = compile(context(root, List.of(reviewRef)));
        assertEquals(NodeStatus.COMPLETED, compiled.status(), compiled.result().toString());
        var note = "Phone registration and login must accept E.164 identifiers only";
        prepareContractClarificationIteration(root, "iter-002", compiled.outputRefs(), note);

        var resolved = new TaskContractRevisionContextResolver(dev.dual.runtime.RuntimeTestLedger.ledger())
                .resolveForIteration(root, WORKSTREAM_ID, "iter-002");

        assertFalse(resolved.valid(), "a stable resolution and completed node are not a durable clarification decision");
    }

    @Test
    void contractClarificationCannotBypassVerifiedRequirementGateLineage(@TempDir Path root) throws Exception {
        prepare(root);
        var proposalRef = writeProposal(
                root, "run-clarify-tampered", "Phone authentication", List.of("README.md"), List.of());
        var reviewRef = writeApprovedReview(root, proposalRef, "run-clarify-tampered-review", true);
        var compiled = compile(context(root, List.of(reviewRef)));
        assertEquals(NodeStatus.COMPLETED, compiled.status(), compiled.result().toString());
        var resolutionPath = workstreamDir(root).resolve("artifacts/contracts/inst-contract/contract-resolution.json");
        var resolution = (com.fasterxml.jackson.databind.node.ObjectNode) OBJECT_MAPPER.readTree(resolutionPath.toFile());
        resolution.put("gate_ref", ".agent/workstreams/" + WORKSTREAM_ID + "/gates/missing.json");
        OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(resolutionPath.toFile(), resolution);
        prepareContractClarificationIteration(root, "iter-002", compiled.outputRefs(), "Require E.164 only");

        var resolved = new TaskContractRevisionContextResolver(dev.dual.runtime.RuntimeTestLedger.ledger())
                .resolveForIteration(root, WORKSTREAM_ID, "iter-002");

        assertFalse(resolved.valid());
        assertFalse(resolved.errors().isEmpty());
    }

    @Test
    void completedBlockedGateNodeAloneCannotAuthorizeHumanRevision(@TempDir Path root) throws Exception {
        prepare(root);
        var proposalRef = writeProposal(
                root, "run-human-revise", "Phone authentication", List.of("README.md"), List.of());
        var reviewRef = writeReview(
                root, proposalRef, "run-human-revise-review", "BLOCKED", List.of(),
                INITIAL_ITERATION_ID, false);
        var note = "Accept only explicit E.164 phone identifiers and canonical lowercase email identities";
        prepareHumanRequirementRevisionIteration(root, "iter-002", reviewRef, note);

        var resolved = new TaskContractRevisionContextResolver(dev.dual.runtime.RuntimeTestLedger.ledger())
                .resolveForIteration(root, WORKSTREAM_ID, "iter-002");

        // This fixture writes only a completed node, with no resolved gate or routing event.
        // Real READY/BLOCKED human continuations are covered through transport -> runtime -> ledger.
        assertFalse(resolved.valid());
    }

    @Test
    void completedReadyGateNodeAloneCannotAuthorizeHumanRevision(@TempDir Path root) throws Exception {
        prepare(root);
        var proposalRef = writeProposal(
                root, "run-human-revise-ready", "Phone authentication", List.of("README.md"), List.of());
        var reviewRef = writeReview(
                root, proposalRef, "run-human-revise-ready-review", "READY_FOR_PRODUCT_REVIEW", List.of(),
                INITIAL_ITERATION_ID, false);
        prepareHumanRequirementRevisionIteration(root, "iter-002", reviewRef, "Change the accepted contract");

        var resolved = new TaskContractRevisionContextResolver(dev.dual.runtime.RuntimeTestLedger.ledger())
                .resolveForIteration(root, WORKSTREAM_ID, "iter-002");

        assertFalse(resolved.valid(), "a node document must not impersonate the durable human decision");
    }

    @Test
    void blockedReviewCannotUseReadyGateAsApproval(@TempDir Path root) throws Exception {
        prepare(root);
        var proposalRef = writeProposal(root, "run-blocked-mismatch", "Choose phone input semantics",
                List.of("README.md"), List.of());
        var reviewRef = writeReview(
                root, proposalRef, "run-blocked-mismatch-review", "BLOCKED", List.of(),
                INITIAL_ITERATION_ID, true);

        var result = compile(context(root, List.of(reviewRef)));

        assertEquals(NodeStatus.FAILED, result.status());
        assertEquals("TASK_CONTRACT_PROPOSAL_INVALID", result.outcome());
        assertTrue(String.valueOf(result.result().get("validation_errors"))
                .contains("current review is not an approved intake product review"));
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

        var result = compile(context(root, List.of(reviewRef)));

        assertEquals(NodeStatus.FAILED, result.status());
        assertEquals("TASK_CONTRACT_PROPOSAL_INVALID", result.outcome());
        assertTrue(String.valueOf(result.result().get("validation_errors"))
                .contains("schema_version must be 3"));
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
        AgentOutputLineageFixture.publish(root, WORKSTREAM_ID, proposalPath, INITIAL_ITERATION_ID);
        var reviewRef = writeApprovedReview(root, proposalRef, "run-review-v2-string-scope", true);

        var result = compile(context(root, List.of(reviewRef)));

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

        var result = compile(context(root, List.of(reviewRef)));

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

        var result = compile(context(root, List.of(reviewRef)));

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

        var result = compile(context(root, List.of(reviewB)));

        assertEquals("TASK_CONTRACT_PROPOSAL_APPROVAL_MISSING", result.outcome());
        assertFalse(Files.exists(contractPath(root)));
        assertFalse(reviewA.equals(reviewB));
    }

    @Test
    void approvalGateFromDifferentIterationCannotCompileCurrentReview(@TempDir Path root) throws Exception {
        prepare(root);
        var proposal = writeProposal(root, "run-001", "objective", List.of("README.md"), List.of());
        var review = writeApprovedReview(root, proposal, "run-review", true);
        var gateNode = workstreamDir(root).resolve("nodes/node-gate-human_requirement_review-run-review.json");
        Files.writeString(gateNode, Files.readString(gateNode).replace(INITIAL_ITERATION_ID, "iter-other"));

        var result = compile(context(root, List.of(review)));

        assertEquals(NodeStatus.FAILED, result.status());
        assertEquals("TASK_CONTRACT_PROPOSAL_APPROVAL_MISSING", result.outcome());
        assertFalse(Files.exists(contractPath(root)));
    }

    @Test
    void approvedProposalHashMismatchIsRejected(@TempDir Path root) throws Exception {
        prepare(root);
        var proposalRef = writeProposal(root, "run-001", "approved objective", List.of("README.md"), List.of());
        var reviewRef = writeApprovedReview(root, proposalRef, "run-review", true);
        Files.writeString(root.resolve(proposalRef), "{}\n");

        var result = compile(context(root, List.of(reviewRef)));

        assertEquals("TASK_CONTRACT_PROPOSAL_INVALID", result.outcome());
        assertFalse(Files.exists(contractPath(root)));
    }

    @Test
    void approvedProposalWithOpenQuestionsProducesResolution(@TempDir Path root) throws Exception {
        prepare(root);
        var proposalRef = writeProposal(root, "run-001", "Add review", List.of("README.md"),
                List.of("Should review be mandatory?"));
        var reviewRef = writeApprovedReview(root, proposalRef, "run-review", true);

        var result = compile(context(root, List.of(reviewRef)));

        assertEquals(NodeStatus.COMPLETED, result.status(), result.result().toString());
        assertTrue(Files.isRegularFile(workstreamDir(root).resolve("artifacts/contracts/inst-contract/contract-resolution.json")));
    }

    @Test
    void revisionCompilesOnlyWhenEveryTriggeredReviewObligationIsCovered(@TempDir Path root) throws Exception {
        // Replace the old hand-written successful revision (no R01/R03) with the real run/ledger chain.
        var f=new AcceptanceRepairSequenceTest.Fixture(root,"CONTRACT_GAP","docs/output.txt");
        f.until("gate.human_requirement_review");f.step();
        var oldReview=f.ledger.loadNode(f.paths,f.ledger.loadCurrent(f.paths).activeNodeInstanceIds().getFirst()).inputRefs();
        var old=AgentOutputContractDocument.read(root.resolve(oldReview.stream().filter(x->x.endsWith(".contract.json")).findFirst().orElseThrow()),OBJECT_MAPPER).orElseThrow();
        var baseline=old.reviewSubject().resolveVerifiedProposal(root,f.ws,OBJECT_MAPPER).orElseThrow();
        f.ledger.resolveOpenGate(f.paths,f.ledger.loadDefinition(f.paths),"revise","Make behavior observable and preserve evidence");
        f.replaceAcceptanceClause=true;
        f.until("gate.human_requirement_review");f.step();
        var gate=f.ledger.loadNode(f.paths,f.ledger.loadCurrent(f.paths).activeNodeInstanceIds().getFirst());
        var context=new TaskContractRevisionContextResolver(f.ledger).resolveForIteration(root,f.ws,gate.iterationId());
        assertTrue(context.valid(),context.errors().toString());
        var review=AgentOutputContractDocument.read(root.resolve(gate.inputRefs().stream().filter(x->x.endsWith(".contract.json")).findFirst().orElseThrow()),OBJECT_MAPPER).orElseThrow();
        var revised=review.reviewSubject().resolveVerifiedProposal(root,f.ws,OBJECT_MAPPER).orElseThrow();
        assertTrue(new TaskContractProposalValidator().validateRevisionCoverage(revised,context.context().obligations()).isEmpty());
        assertTrue(revised.evidenceRefs().containsAll(baseline.evidenceRefs()));
        f.approve();f.until("gate.contract_review");f.step();
        var goal=TaskContractProjectionVerifier.resolve(f.ledger,root,f.ws,TaskContract.Phase.GOAL).qualified().orElseThrow().contract();
        assertEquals("Revised output must include a stable identifier",goal.acceptanceCriteria().getFirst().text());
        assertTrue(goal.scope().included().isEmpty(),"GOAL does not grant execution writes");
    }

    @Test
    void compileCannotBypassUndeclaredClauseDelta(@TempDir Path root) throws Exception {
        prepare(root);
        var originalProposal = writeTypedProposal(
                root, "run-original", "Original objective",
                List.of(new TaskContract.ScopeEntry("README.md", TaskContract.ScopeOperation.MODIFY)),
                List.of(), TaskContractProposal.SCHEMA_VERSION,
                INITIAL_ITERATION_ID, List.of(), List.of());
        replaceClauseText(
                root, originalProposal, "constraints", "CONSTRAINT-1",
                "Preserve the local part of email addresses", INITIAL_ITERATION_ID);
        var obligations = List.of(new AgentResultEnvelope.ReviewObligation(
                "OB-1", "Make registration authorization atomic"));
        var rejectedReview = writeReview(
                root, originalProposal, "run-rejected", "NEEDS_REVISION", obligations,
                INITIAL_ITERATION_ID, false);
        prepareRevisionIteration(root, "iter-002", rejectedReview);

        var revisedProposal = writeTypedProposal(
                root, "run-revised", "Revised objective",
                List.of(new TaskContract.ScopeEntry("README.md", TaskContract.ScopeOperation.MODIFY)),
                List.of(), TaskContractProposal.SCHEMA_VERSION,
                "iter-002",
                List.of(new AgentResultEnvelope.RevisionResponse("OB-1", List.of("AC-1"))),
                List.of());
        replaceClauseText(
                root, revisedProposal, "constraints", "CONSTRAINT-1",
                "Lowercase complete email addresses", "iter-002");
        var approvedReview = writeReview(
                root, revisedProposal, "run-ready", "READY_FOR_PRODUCT_REVIEW", List.of(),
                "iter-002", true);

        var result = compile(context(root, List.of(approvedReview), "iter-002"));

        assertEquals(NodeStatus.FAILED, result.status());
        assertEquals("TASK_CONTRACT_REVISION_DELTA_INVALID", result.outcome());
        assertTrue(String.valueOf(result.result().get("validation_errors"))
                .contains("UNDECLARED_CONTRACT_CLAUSE_CHANGE: CONSTRAINT-1"), result.result().toString());
        assertFalse(Files.exists(contractPath(root)));
    }

    @Test
    void revisionWithOmittedObligationFailsBeforeCanonicalContract(@TempDir Path root) throws Exception {
        prepare(root);
        var originalProposal = writeProposal(root, "run-original", "Original objective", List.of("README.md"), List.of());
        var rejectedReview = writeReview(
                root, originalProposal, "run-rejected", "NEEDS_REVISION",
                List.of(
                        new AgentResultEnvelope.ReviewObligation("OB-1", "First gap"),
                        new AgentResultEnvelope.ReviewObligation("OB-2", "Second gap")),
                INITIAL_ITERATION_ID, false);
        prepareRevisionIteration(root, "iter-002", rejectedReview);
        var revisedProposal = writeTypedProposal(
                root, "run-revised", "Revised objective",
                List.of(new TaskContract.ScopeEntry("README.md", TaskContract.ScopeOperation.MODIFY)),
                List.of(), TaskContractProposal.SCHEMA_VERSION,
                "iter-002",
                List.of(new AgentResultEnvelope.RevisionResponse("OB-1", List.of("AC-1"))),
                List.of());
        var approvedReview = writeReview(
                root, revisedProposal, "run-ready", "READY_FOR_PRODUCT_REVIEW", List.of(),
                "iter-002", true);

        var result = compile(context(root, List.of(approvedReview), "iter-002"));

        assertEquals(NodeStatus.FAILED, result.status());
        assertEquals("TASK_CONTRACT_PROPOSAL_INVALID", result.outcome());
        assertTrue(String.valueOf(result.result().get("validation_errors")).contains("OB-2"));
        assertFalse(Files.exists(contractPath(root)));
    }

    @Test
    void revisionWithoutVerifiedTriggerReviewFailsClosed(@TempDir Path root) throws Exception {
        prepare(root);
        prepareRevisionIteration(root, "iter-002", "missing-review.contract.json");
        var revisedProposal = writeTypedProposal(
                root, "run-revised", "Revised objective",
                List.of(new TaskContract.ScopeEntry("README.md", TaskContract.ScopeOperation.MODIFY)),
                List.of(), TaskContractProposal.SCHEMA_VERSION,
                "iter-002", List.of(), List.of());
        var approvedReview = writeReview(
                root, revisedProposal, "run-ready", "READY_FOR_PRODUCT_REVIEW", List.of(),
                "iter-002", true);

        var result = compile(context(root, List.of(approvedReview), "iter-002"));

        assertEquals(NodeStatus.FAILED, result.status());
        assertEquals("TASK_CONTRACT_PROPOSAL_INVALID", result.outcome());
        assertTrue(String.valueOf(result.result().get("message")).contains("exactly one verified rejected"));
    }

    @Test
    void initialIterationCannotCarryRevisionResponses(@TempDir Path root) throws Exception {
        prepare(root);
        var proposal = writeTypedProposal(
                root, "run-initial", "Initial objective",
                List.of(new TaskContract.ScopeEntry("README.md", TaskContract.ScopeOperation.MODIFY)),
                List.of(), TaskContractProposal.SCHEMA_VERSION,
                INITIAL_ITERATION_ID,
                List.of(new AgentResultEnvelope.RevisionResponse("OB-1", List.of("AC-1"))),
                List.of());
        var approvedReview = writeApprovedReview(root, proposal, "run-ready", true);

        var result = compile(context(root, List.of(approvedReview)));

        assertEquals(NodeStatus.FAILED, result.status());
        assertEquals("TASK_CONTRACT_PROPOSAL_INVALID", result.outcome());
        assertTrue(String.valueOf(result.result().get("validation_errors")).contains("initial workflow iteration"));
    }

    @Test
    void revisionWithMultipleVerifiedRejectedReviewsFailsClosed(@TempDir Path root) throws Exception {
        prepare(root);
        var originalA = writeProposal(root, "run-original-a", "Original A", List.of("README.md"), List.of());
        var originalB = writeProposal(root, "run-original-b", "Original B", List.of("README.md"), List.of());
        var rejectedA = writeReview(
                root, originalA, "run-rejected-a", "NEEDS_REVISION",
                List.of(new AgentResultEnvelope.ReviewObligation("OB-A", "First review gap")),
                INITIAL_ITERATION_ID, false);
        var rejectedB = writeReview(
                root, originalB, "run-rejected-b", "NEEDS_REVISION",
                List.of(new AgentResultEnvelope.ReviewObligation("OB-B", "Second review gap")),
                INITIAL_ITERATION_ID, false);
        prepareRevisionIteration(root, "iter-002", List.of(rejectedA, rejectedB));
        var revisedProposal = writeTypedProposal(
                root, "run-revised", "Revised objective",
                List.of(new TaskContract.ScopeEntry("README.md", TaskContract.ScopeOperation.MODIFY)),
                List.of(), TaskContractProposal.SCHEMA_VERSION,
                "iter-002",
                List.of(new AgentResultEnvelope.RevisionResponse("OB-A", List.of("AC-1"))),
                List.of());
        var approvedReview = writeReview(
                root, revisedProposal, "run-ready", "READY_FOR_PRODUCT_REVIEW", List.of(),
                "iter-002", true);

        var result = compile(context(root, List.of(approvedReview), "iter-002"));

        assertEquals(NodeStatus.FAILED, result.status());
        assertEquals("TASK_CONTRACT_PROPOSAL_INVALID", result.outcome());
        assertTrue(String.valueOf(result.result().get("validation_errors")).contains("review_count=2"));
    }

    private static void prepare(Path root) throws Exception {
        var ledger = dev.dual.runtime.RuntimeTestLedger.ledger();
        var paths = new dev.dual.ledger.file.WorkstreamPaths(root, new dev.dual.core.id.DualId(WORKSTREAM_ID));
        dev.dual.runtime.RuntimeTestLedger.initialize(ledger, paths, "wf-compile-fixture",
                new dev.dual.core.workflow.WorkflowDefinition("compile-fixture", 1, "contract.compile", List.of(
                        new NodeDefinition("contract.compile", NodeKind.TASK, "contract.compile", Map.of("contract_phase", "GOAL", "required_subject", "approved_task_contract_proposal"))), List.of()));
        Files.createDirectories(workstreamDir(root));
        Files.writeString(workstreamDir(root).resolve("requirement.md"), "Strict proposal fixture\n");
        Files.writeString(root.resolve("README.md"), "# fixture\n");
        var iterations = workstreamDir(root).resolve("iterations");
        Files.createDirectories(iterations);
        LEDGER_MAPPER.writeValue(iterations.resolve(INITIAL_ITERATION_ID + ".json").toFile(),
                WorkflowIterationDocument.initial("intake.claude_brainstorm", Instant.now()));
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
        return writeTypedProposal(
                root, runId, objective, included, questions, proposalSchemaVersion,
                INITIAL_ITERATION_ID, List.of(), included.stream().map(TaskContract.ScopeEntry::path).toList());
    }

    @SuppressWarnings("unchecked")
    private static String writeTypedProposal(
            Path root,
            String runId,
            String objective,
            List<TaskContract.ScopeEntry> included,
            List<String> questions,
            int proposalSchemaVersion,
            String iterationId,
            List<AgentResultEnvelope.RevisionResponse> revisionResponses,
            List<String> evidenceRefs
    ) throws Exception {
        var directory = outputDir(root, runId);
        var proposalResult = new AgentResultEnvelope.TaskContractProposalResult(
                "task_contract_proposal", "READY_FOR_PRODUCT_REVIEW", "proposal ready", false,
                objective, new AgentResultEnvelope.ProposalScope(
                        included.stream()
                                .map(entry -> new AgentResultEnvelope.ProposalScopePath(entry.path()))
                                .toList(),
                        List.of("No unrelated changes")),
                envelopeClauses("AC", "The requested behavior is observable", "Focused verification passes"),
                envelopeClauses("TEST", "Run focused verification"),
                envelopeClauses("CONSTRAINT", "Keep the change scoped"),
                envelopeClauses("NON_GOAL", "No unrelated refactor"),
                envelopeClauses("RISK", "Regression risk"),
                evidenceRefs, questions, revisionResponses);
        var envelope = new AgentResultEnvelope(
                1, WORKSTREAM_ID, runId, "intake.claude_brainstorm",
                proposalResult);
        var output = directory.resolve("intake-claude-brainstorm.json");
        var outputRef = relative(root, output);
        Files.writeString(output, OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(envelope));
        var projector = new AgentResultDocumentProjector();
        var resolvedProposal = projector.taskContractProposal(proposalResult, included);
        var document = projector.outputContract(
                envelope, ContractRegistry.forNode("intake.claude_brainstorm"), "acpx.agent_run",
                "inst-intake-" + runId, outputRef, resolvedProposal, null);
        ((Map<String, Object>) document.get("task_contract_proposal"))
                .put("schema_version", proposalSchemaVersion);
        AgentContractSemantics.addSidecarFields(document);
        var sidecar = AgentOutputContractSidecar.pathFor(output);
        Files.writeString(sidecar, OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(document));
        AgentOutputLineageFixture.publish(root, WORKSTREAM_ID, sidecar, iterationId);
        return relative(root, sidecar);
    }

    private static String writeApprovedReview(
            Path root,
            String proposalSidecarRef,
            String reviewRunId,
            boolean approved
    ) throws Exception {
        return writeReview(
                root, proposalSidecarRef, reviewRunId, "READY_FOR_PRODUCT_REVIEW", List.of(),
                INITIAL_ITERATION_ID, approved);
    }

    private static String writeReview(
            Path root,
            String proposalSidecarRef,
            String reviewRunId,
            String decision,
            List<AgentResultEnvelope.ReviewObligation> obligations,
            String iterationId,
            boolean approved
    ) throws Exception {
        var proposalPath = root.resolve(proposalSidecarRef);
        var proposalDocument = AgentOutputContractDocument.read(proposalPath, OBJECT_MAPPER).orElseThrow();
        var published = proposalDocument.proposalDerivation() == null
                ? TaskContractProposalReference.publishInitial(
                        root, WORKSTREAM_ID, proposalPath, proposalDocument, OBJECT_MAPPER)
                : TaskContractProposalReference.publishRevisionDerived(
                        root, WORKSTREAM_ID, proposalPath, proposalDocument, OBJECT_MAPPER);

        var reviewDirectory = outputDir(root, reviewRunId);
        var reviewEnvelope = new AgentResultEnvelope(
                1, WORKSTREAM_ID, reviewRunId, "intake.codex_product_review",
                new AgentResultEnvelope.AgentOutputResult(
                        "agent_output_contract", decision, "review ready", "READY", false,
                        List.of("reviewed proposal"), List.of("proposal is coherent"),
                        "BLOCKED".equals(decision) ? List.of("Confirm the product policy") : List.of(),
                        "BLOCKED".equals(decision) ? List.of("Choose E.164-only or local phone input") : List.of(),
                        obligations, List.of(), List.of(), List.of(), "review complete",
                        new AgentResultEnvelope.Failure("", AgentResultEnvelope.VerificationRemediation.NONE,
                                "NEEDS_REVISION".equals(decision) ? List.of("contract revision required") : List.of(),
                                List.of(), List.of(), List.of(), ""),
                        List.of()));
        var envelopeValidation = new AgentResultEnvelopeValidator().validate(
                reviewEnvelope, WORKSTREAM_ID, reviewRunId,
                ContractRegistry.forNode("intake.codex_product_review"));
        if (!envelopeValidation.valid()) {
            throw new IllegalStateException("invalid review fixture: " + envelopeValidation.errors());
        }
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
        AgentOutputLineageFixture.publish(root, WORKSTREAM_ID, reviewSidecar, iterationId);
        var reviewRef = relative(root, reviewSidecar);
        if (approved) {
            writeGate(root, reviewRef, reviewRunId, iterationId);
        }
        return reviewRef;
    }

    private static void replaceClauseText(
            Path root,
            String proposalSidecarRef,
            String section,
            String clauseId,
            String text,
            String iterationId
    ) throws Exception {
        var path = root.resolve(proposalSidecarRef);
        var document = (com.fasterxml.jackson.databind.node.ObjectNode) OBJECT_MAPPER.readTree(path.toFile());
        for (var clause : document.path("task_contract_proposal").path(section)) {
            if (clauseId.equals(clause.path("id").asText())) {
                ((com.fasterxml.jackson.databind.node.ObjectNode) clause).put("text", text);
            }
        }
        OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), document);
        AgentOutputLineageFixture.publish(root, WORKSTREAM_ID, path, iterationId);
    }

    private static void writeGate(Path root, String reviewRef, String suffix, String iterationId) throws Exception {
        writeGate(root, reviewRef, suffix, iterationId, "READY_FOR_PRODUCT_REVIEW", "approved fixture");
    }

    private static void writeGate(
            Path root, String reviewRef, String suffix, String iterationId, String sourceOutcome, String note
    ) throws Exception {
        var gateNodeId = "node-gate-human_requirement_review-" + suffix;
        var nodes = workstreamDir(root).resolve("nodes");
        var gates = workstreamDir(root).resolve("gates");
        Files.createDirectories(nodes);
        Files.createDirectories(gates);
        var nowInstant = Instant.now();
        LEDGER_MAPPER.writeValue(nodes.resolve(gateNodeId + ".json").toFile(),
                new dev.dual.ledger.file.NodeInstanceDocument(
                        1, gateNodeId, "gate.human_requirement_review", NodeKind.GATE, "human.review",
                        NodeStatus.COMPLETED, "stage-requirement", iterationId, List.of(reviewRef), Map.of(),
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
                        "note", note,
                        "source_outcome", sourceOutcome,
                        "subject_input_refs", List.of(reviewRef))));
    }

    // Unit-level compiler consumer: supply an explicit local write session, as runtime does.
    // End-to-end approval routing is covered by PhasedContractContinuationTest and authored sequences.
    private static NodeResult compile(TaskHandler.TaskContext context) throws Exception {
        var paths = new dev.dual.ledger.file.WorkstreamPaths(context.workspaceRoot(), new dev.dual.core.id.DualId(context.workstreamId()));
        try (var write = context.ledger().writeSession(paths, "compile unit fixture", context.instance().nodeInstanceId())) {
            var result = new ContractCompileHandler().execute(new TaskHandler.TaskContext(context.workspaceRoot(),
                    context.workstreamId(), context.definition(), context.instance(), write.ledger()));
            write.commit(); return result;
        }
    }

    private static TaskHandler.TaskContext context(Path root) {
        return context(root, List.of());
    }

    private static TaskHandler.TaskContext context(Path root, List<String> inputRefs) {
        return context(root, inputRefs, INITIAL_ITERATION_ID);
    }

    private static TaskHandler.TaskContext context(Path root, List<String> inputRefs, String iterationId) {
        return new TaskHandler.TaskContext(root, WORKSTREAM_ID, new NodeDefinition("contract.compile", NodeKind.TASK, "contract.compile",
                        Map.of("contract_phase", "GOAL", "required_subject", "approved_task_contract_proposal")), new NodeInstance("inst-contract", "contract.compile", NodeKind.TASK, "contract.compile",
                        NodeStatus.READY, "stage-contract", iterationId, inputRefs, List.of(), Map.of(), null,
                        Instant.now(), null), dev.dual.runtime.RuntimeTestLedger.ledger());
    }

    private static void prepareRevisionIteration(
            Path root,
            String revisionIterationId,
            String rejectedReviewRef
    ) throws Exception {
        prepareRevisionIteration(root, revisionIterationId, List.of(rejectedReviewRef));
    }

    private static void prepareContractClarificationIteration(
            Path root,
            String revisionIterationId,
            List<String> compiledRefs,
            String note
    ) throws Exception {
        var triggerNodeId = "node-gate-contract-review-clarify";
        var now = Instant.now();
        var nodes = workstreamDir(root).resolve("nodes");
        Files.createDirectories(nodes);
        LEDGER_MAPPER.writeValue(nodes.resolve(triggerNodeId + ".json").toFile(), new NodeInstanceDocument(
                1, triggerNodeId, "gate.contract_review", NodeKind.GATE, "human.review",
                NodeStatus.COMPLETED, "stage-gate", INITIAL_ITERATION_ID,
                compiledRefs, Map.of(), compiledRefs,
                Map.of("human_action", "clarify", "note", note),
                "", "", now, now));
        LEDGER_MAPPER.writeValue(workstreamDir(root).resolve("iterations").resolve(revisionIterationId + ".json").toFile(),
                WorkflowIterationDocument.revision(
                        revisionIterationId, 2, INITIAL_ITERATION_ID, triggerNodeId,
                        "clarify", "intake.claude_brainstorm", note, now));
    }

    private static void prepareHumanRequirementRevisionIteration(
            Path root,
            String revisionIterationId,
            String reviewRef,
            String note
    ) throws Exception {
        var triggerNodeId = "node-gate-human-requirement-revise";
        var now = Instant.now();
        var nodes = workstreamDir(root).resolve("nodes");
        Files.createDirectories(nodes);
        LEDGER_MAPPER.writeValue(nodes.resolve(triggerNodeId + ".json").toFile(), new NodeInstanceDocument(
                1, triggerNodeId, "gate.human_requirement_review", NodeKind.GATE, "human.review",
                NodeStatus.COMPLETED, "stage-gate", INITIAL_ITERATION_ID,
                List.of(reviewRef), Map.of(), List.of(reviewRef),
                Map.of("human_action", "revise", "note", note),
                "", "", now, now));
        LEDGER_MAPPER.writeValue(workstreamDir(root).resolve("iterations").resolve(revisionIterationId + ".json").toFile(),
                WorkflowIterationDocument.revision(
                        revisionIterationId, 2, INITIAL_ITERATION_ID, triggerNodeId,
                        "revise", "intake.claude_brainstorm", note, now));
    }

    private static void prepareRevisionIteration(
            Path root,
            String revisionIterationId,
            List<String> rejectedReviewRefs
    ) throws Exception {
        var triggerNodeId = "node-intake-decision-revision";
        var now = Instant.now();
        var nodes = workstreamDir(root).resolve("nodes");
        Files.createDirectories(nodes);
        LEDGER_MAPPER.writeValue(nodes.resolve(triggerNodeId + ".json").toFile(), new NodeInstanceDocument(
                1, triggerNodeId, "intake.decision", NodeKind.DECISION, "decision",
                NodeStatus.COMPLETED, "stage-intake", INITIAL_ITERATION_ID,
                rejectedReviewRefs, Map.of(), List.of(), Map.of("decision", "NEEDS_REVISION"),
                "", "", now, now));
        var iterations = workstreamDir(root).resolve("iterations");
        LEDGER_MAPPER.writeValue(iterations.resolve(revisionIterationId + ".json").toFile(),
                WorkflowIterationDocument.revision(
                        revisionIterationId, 2, INITIAL_ITERATION_ID, triggerNodeId,
                        "NEEDS_REVISION", "intake.claude_brainstorm", "Resolve contract gaps", now));
    }

    private static List<AgentResultEnvelope.ContractClause> envelopeClauses(String prefix, String... values) {
        var clauses = new java.util.ArrayList<AgentResultEnvelope.ContractClause>();
        for (int index = 0; index < values.length; index++) {
            clauses.add(new AgentResultEnvelope.ContractClause(prefix + "-" + (index + 1), values[index]));
        }
        return List.copyOf(clauses);
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
        return workstreamDir(root).resolve("artifacts/contracts/inst-contract/task-contract.json");
    }
}
