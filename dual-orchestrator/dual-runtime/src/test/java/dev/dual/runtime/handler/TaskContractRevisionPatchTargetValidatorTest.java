package dev.dual.runtime.handler;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskContractRevisionPatchTargetValidatorTest {
    @Test
    void rejectsAllPatchWideDuplicateTargetKinds() {
        var patch = patch(
                obligation("CO-1", " AC-1 ", "src/A.java", "evidence/a.json"),
                obligation("CO-2", "AC-1", "src/A.java", "evidence/a.json"));

        assertEquals(List.of(
                        "DUPLICATE_CLAUSE_PATCH_TARGET: AC-1",
                        "DUPLICATE_SCOPE_PATCH_TARGET: src/A.java",
                        "DUPLICATE_EVIDENCE_PATCH_TARGET: evidence/a.json"),
                TaskContractRevisionPatchTargetValidator.validate(patch));
    }

    @Test
    void preservesExistingWhitespaceComparisonSemantics() {
        var patch = patch(
                obligation("CO-1", " AC-1 ", " src/A.java ", " evidence/a.json "),
                obligation("CO-2", "AC-1", "src/A.java", "evidence/a.json"));

        var errors = TaskContractRevisionPatchTargetValidator.validate(patch);

        assertEquals(List.of("DUPLICATE_CLAUSE_PATCH_TARGET: AC-1"), errors);
        assertTrue(errors.stream().noneMatch(value -> value.contains("SCOPE")));
        assertTrue(errors.stream().noneMatch(value -> value.contains("EVIDENCE")));
    }

    private static AgentResultEnvelope.TaskContractRevisionPatchResult patch(
            AgentResultEnvelope.ObligationPatch... obligations
    ) {
        return new AgentResultEnvelope.TaskContractRevisionPatchResult(
                "task_contract_revision_patch", "READY_FOR_PRODUCT_REVIEW", "revision",
                "node-prev:0123456789abcdef", "a".repeat(64), List.of(obligations));
    }

    private static AgentResultEnvelope.ObligationPatch obligation(
            String obligationId,
            String clauseId,
            String scopePath,
            String evidenceRef
    ) {
        return new AgentResultEnvelope.ObligationPatch(
                obligationId,
                List.of(clauseId.trim()),
                List.of(new AgentResultEnvelope.ClauseChange(
                        AgentResultEnvelope.ClauseChangeOperation.ADD,
                        AgentResultEnvelope.ClauseSection.CONSTRAINTS,
                        clauseId,
                        "text")),
                List.of(new AgentResultEnvelope.ProposalScopePath(scopePath)),
                List.of(evidenceRef));
    }
}
