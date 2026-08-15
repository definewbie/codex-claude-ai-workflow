package dev.dual.runtime.handler;

import dev.dual.core.contract.TaskContract;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskContractProposalRevisionMergerTest {
    private final TaskContractProposalRevisionMerger merger = new TaskContractProposalRevisionMerger();

    @TempDir
    Path workspace;

    @Test
    void capturedAc6BaselineIsPreservedWhileDeclaredClauseAndScopeChangesMerge() {
        var context = context();
        var patch = new AgentResultEnvelope.TaskContractRevisionPatchResult(
                "task_contract_revision_patch",
                "READY_FOR_PRODUCT_REVIEW",
                "Resolve obligations",
                context.previousReference().proposalId(),
                context.previousReference().sha256(),
                List.of(
                        new AgentResultEnvelope.ObligationPatch(
                                "CO-1", List.of("AC-8"),
                                List.of(new AgentResultEnvelope.ClauseChange(
                                        AgentResultEnvelope.ClauseChangeOperation.REPLACE,
                                        AgentResultEnvelope.ClauseSection.ACCEPTANCE_CRITERIA,
                                        "AC-8", "Revised anti-enumeration behavior.")),
                                List.of(), List.of()),
                        new AgentResultEnvelope.ObligationPatch(
                                "CO-6", List.of("TR-6"), List.of(),
                                List.of(new AgentResultEnvelope.ProposalScopePath(
                                        "src/test/java/com/captcha/auth/service/UserServiceTest.java")),
                                List.of()),
                        new AgentResultEnvelope.ObligationPatch(
                                "CO-7", List.of("TR-6"), List.of(), List.of(),
                                List.of("src/test/java/com/captcha/auth/service/UserServiceTest.java"))));

        var result = merger.merge(context, patch, workspace);

        assertTrue(result.valid(), result.errors().toString());
        assertEquals("Captcha challenges expire and cannot be reused.",
                clause(result.proposal(), "AC-6").text());
        assertEquals("Revised anti-enumeration behavior.",
                clause(result.proposal(), "AC-8").text());
        assertTrue(result.proposal().scope().included().contains(new TaskContract.ScopeEntry(
                "src/test/java/com/captcha/auth/service/UserServiceTest.java",
                TaskContract.ScopeOperation.CREATE)));
        assertTrue(result.proposal().evidenceRefs().contains(
                "src/test/java/com/captcha/auth/service/UserServiceTest.java"));
    }

    @Test
    void staleBaseUnknownObligationAndDuplicateTargetFailClosed() {
        var context = context();
        var patch = new AgentResultEnvelope.TaskContractRevisionPatchResult(
                "task_contract_revision_patch", "READY_FOR_PRODUCT_REVIEW", "invalid",
                "stale", "0".repeat(64),
                List.of(
                        new AgentResultEnvelope.ObligationPatch(
                                "CO-X", List.of("AC-8"),
                                List.of(new AgentResultEnvelope.ClauseChange(
                                        AgentResultEnvelope.ClauseChangeOperation.REPLACE,
                                        AgentResultEnvelope.ClauseSection.ACCEPTANCE_CRITERIA,
                                        "AC-8", "one")), List.of(), List.of()),
                        new AgentResultEnvelope.ObligationPatch(
                                "CO-1", List.of("AC-8"),
                                List.of(new AgentResultEnvelope.ClauseChange(
                                        AgentResultEnvelope.ClauseChangeOperation.REPLACE,
                                        AgentResultEnvelope.ClauseSection.ACCEPTANCE_CRITERIA,
                                        "AC-8", "two")), List.of(), List.of())));

        var result = merger.merge(context, patch, workspace);

        assertFalse(result.valid());
        assertTrue(result.errors().contains("REVISION_BASE_PROPOSAL_ID_MISMATCH"));
        assertTrue(result.errors().contains("REVISION_BASE_PROPOSAL_SHA256_MISMATCH"));
        assertTrue(result.errors().contains("UNKNOWN_REVISION_OBLIGATION: CO-X"));
        assertTrue(result.errors().contains("DUPLICATE_CLAUSE_PATCH_TARGET: AC-8"));
        assertTrue(result.errors().stream().anyMatch(error -> error.startsWith("MISSING_REVISION_OBLIGATION:")));
    }

    @Test
    void clauseIdPrefixDoesNotOverrideTypedSection() {
        var context = context(List.of(new AgentResultEnvelope.ReviewObligation("CO-1", "Add clause")));
        var patch = new AgentResultEnvelope.TaskContractRevisionPatchResult(
                "task_contract_revision_patch", "READY_FOR_PRODUCT_REVIEW", "add",
                context.previousReference().proposalId(), context.previousReference().sha256(),
                List.of(new AgentResultEnvelope.ObligationPatch(
                        "CO-1", List.of("TR-NAMED-BUT-AC-SECTION"),
                        List.of(new AgentResultEnvelope.ClauseChange(
                                AgentResultEnvelope.ClauseChangeOperation.ADD,
                                AgentResultEnvelope.ClauseSection.ACCEPTANCE_CRITERIA,
                                "TR-NAMED-BUT-AC-SECTION", "Typed section is authoritative.")),
                        List.of(), List.of())));

        var result = merger.merge(context, patch, workspace);

        assertTrue(result.valid(), result.errors().toString());
        assertEquals("Typed section is authoritative.",
                clause(result.proposal(), "TR-NAMED-BUT-AC-SECTION").text());
    }

    @Test
    void unknownClauseReplaceTargetFailsClosed() {
        var context = context(List.of(new AgentResultEnvelope.ReviewObligation("CO-1", "Replace exact clause")));
        var patch = new AgentResultEnvelope.TaskContractRevisionPatchResult(
                "task_contract_revision_patch", "READY_FOR_PRODUCT_REVIEW", "replace",
                context.previousReference().proposalId(), context.previousReference().sha256(),
                List.of(new AgentResultEnvelope.ObligationPatch(
                        "CO-1", List.of("AC-MISSING"),
                        List.of(new AgentResultEnvelope.ClauseChange(
                                AgentResultEnvelope.ClauseChangeOperation.REPLACE,
                                AgentResultEnvelope.ClauseSection.ACCEPTANCE_CRITERIA,
                                "AC-MISSING", "Must not be synthesized.")),
                        List.of(), List.of())));

        var result = merger.merge(context, patch, workspace);

        assertFalse(result.valid());
        assertEquals(List.of("UNKNOWN_CLAUSE_REPLACE_TARGET: AC-MISSING"), result.errors());
    }

    @Test
    void existingRevisionScopeAdditionResolvesToModify() throws Exception {
        Files.writeString(workspace.resolve("README.md"), "baseline\n");
        var context = context(List.of(new AgentResultEnvelope.ReviewObligation("CO-1", "Add exact scope")));
        var patch = new AgentResultEnvelope.TaskContractRevisionPatchResult(
                "task_contract_revision_patch", "READY_FOR_PRODUCT_REVIEW", "scope",
                context.previousReference().proposalId(), context.previousReference().sha256(),
                List.of(new AgentResultEnvelope.ObligationPatch(
                        "CO-1", List.of("AC-8"), List.of(),
                        List.of(new AgentResultEnvelope.ProposalScopePath("README.md")), List.of())));

        var result = merger.merge(context, patch, workspace);

        assertTrue(result.valid(), result.errors().toString());
        assertTrue(result.proposal().scope().included().contains(
                new TaskContract.ScopeEntry("README.md", TaskContract.ScopeOperation.MODIFY)));
    }

    @Test
    void capturedV14DuplicateEvidenceFailsButSingleAdditionPreservesAllObligations() {
        var obligations = List.of(
                new AgentResultEnvelope.ReviewObligation("CO-1", "Normalize identifiers"),
                new AgentResultEnvelope.ReviewObligation("CO-2", "Bind registration authorization"),
                new AgentResultEnvelope.ReviewObligation("CO-3", "Define rate limits"),
                new AgentResultEnvelope.ReviewObligation("CO-4", "Remove unsupported restriction"));
        var context = context(obligations);
        var duplicate = capturedV14Patch(context, true);

        var rejected = merger.merge(context, duplicate, workspace);

        assertFalse(rejected.valid());
        assertEquals(List.of(
                "DUPLICATE_EVIDENCE_PATCH_TARGET: src/main/resources/application.yml"), rejected.errors());

        var merged = merger.merge(context, capturedV14Patch(context, false), workspace);

        assertTrue(merged.valid(), merged.errors().toString());
        assertEquals(List.of("README.md", "src/main/resources/application.yml"),
                merged.proposal().evidenceRefs());
        assertEquals(List.of("CO-1", "CO-2", "CO-3", "CO-4"),
                merged.proposal().revisionResponses().stream()
                        .map(TaskContractProposal.RevisionResponse::reviewObligationId)
                        .toList());
        assertEquals("Normalize identifiers.", clause(merged.proposal(), "AC-8").text());
        assertEquals("Bind registration authorization.", clause(merged.proposal(), "AC-6").text());
        assertEquals("Define rate limits.", clause(merged.proposal(), "TR-6").text());
        assertEquals("Use the requested baseline only.", clause(merged.proposal(), "C-1").text());
    }

    private static TaskContractRevisionContextResolver.Context context() {
        return context(List.of(
                new AgentResultEnvelope.ReviewObligation("CO-1", "Fix AC-8"),
                new AgentResultEnvelope.ReviewObligation("CO-6", "Add exact scope"),
                new AgentResultEnvelope.ReviewObligation("CO-7", "Add evidence")));
    }

    private static TaskContractRevisionContextResolver.Context context(
            List<AgentResultEnvelope.ReviewObligation> obligations
    ) {
        var reference = new TaskContractProposalReference(
                1, "node-prev:0123456789abcdef", "ws-1",
                "intake.claude_brainstorm", "node-prev",
                ".agent/workstreams/ws-1/runs/previous.contract.json", "a".repeat(64));
        return new TaskContractRevisionContextResolver.Context(
                null, true, proposal(), reference, obligations);
    }

    private static AgentResultEnvelope.TaskContractRevisionPatchResult capturedV14Patch(
            TaskContractRevisionContextResolver.Context context,
            boolean duplicateEvidence
    ) {
        return new AgentResultEnvelope.TaskContractRevisionPatchResult(
                "task_contract_revision_patch", "READY_FOR_PRODUCT_REVIEW", "Resolve four obligations",
                context.previousReference().proposalId(), context.previousReference().sha256(),
                List.of(
                        replacement("CO-1", "AC-8", AgentResultEnvelope.ClauseSection.ACCEPTANCE_CRITERIA,
                                "Normalize identifiers.", List.of("src/main/resources/application.yml")),
                        replacement("CO-2", "AC-6", AgentResultEnvelope.ClauseSection.ACCEPTANCE_CRITERIA,
                                "Bind registration authorization.", List.of()),
                        replacement("CO-3", "TR-6", AgentResultEnvelope.ClauseSection.TEST_REQUIREMENTS,
                                "Define rate limits.", duplicateEvidence
                                        ? List.of("src/main/resources/application.yml") : List.of()),
                        replacement("CO-4", "C-1", AgentResultEnvelope.ClauseSection.CONSTRAINTS,
                                "Use the requested baseline only.", List.of())));
    }

    private static AgentResultEnvelope.ObligationPatch replacement(
            String obligationId,
            String clauseId,
            AgentResultEnvelope.ClauseSection section,
            String text,
            List<String> evidence
    ) {
        return new AgentResultEnvelope.ObligationPatch(
                obligationId, List.of(clauseId),
                List.of(new AgentResultEnvelope.ClauseChange(
                        AgentResultEnvelope.ClauseChangeOperation.REPLACE, section, clauseId, text)),
                List.of(), evidence);
    }

    private static TaskContractProposal proposal() {
        return new TaskContractProposal(
                "task_contract_proposal", 3, "Build captcha auth.",
                new TaskContractProposal.Scope(
                        List.of(new TaskContract.ScopeEntry("pom.xml", TaskContract.ScopeOperation.CREATE)),
                        List.of()),
                List.of(
                        new TaskContractProposal.ContractClause(
                                "AC-6", "Captcha challenges expire and cannot be reused."),
                        new TaskContractProposal.ContractClause("AC-8", "Old behavior.")),
                List.of(new TaskContractProposal.ContractClause("TR-6", "Test registration.")),
                List.of(new TaskContractProposal.ContractClause("C-1", "Use Java 21.")),
                List.of(), List.of(), List.of("README.md"), List.of(), List.of());
    }

    private static TaskContractProposal.ContractClause clause(TaskContractProposal proposal, String id) {
        return java.util.stream.Stream.of(
                        proposal.acceptanceCriteria(), proposal.testRequirements(), proposal.constraints(),
                        proposal.nonGoals(), proposal.riskAreas())
                .flatMap(List::stream)
                .filter(value -> id.equals(value.id()))
                .findFirst()
                .orElseThrow();
    }
}
