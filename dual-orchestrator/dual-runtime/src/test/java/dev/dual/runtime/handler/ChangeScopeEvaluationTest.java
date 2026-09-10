package dev.dual.runtime.handler;

import dev.dual.core.contract.TaskContract;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChangeScopeEvaluationTest {
    @Test
    void approvedFileModificationIsNotProofOfEvidenceOnlyImmutability() {
        var result = ChangeScopeEvaluator.evaluate(
                contract(entry("src/Service.java", TaskContract.ScopeOperation.MODIFY)),
                List.of(new ChangeScopeEvaluation.ActualChange(
                        "src/Service.java", ChangeScopeEvaluation.ActualOperation.MODIFY, null, "M")));
        assertEquals(ChangeScopeEvaluation.Status.PASS, result.status(),
                "scope authorizes paths/operations; it cannot certify unchanged source bytes");
        assertEquals(List.of("src/Service.java"), result.matchedPaths());
    }

    @Test
    void parsesGitNameStatusIntoTypedActualChanges() {
        var changes = ChangeScopeEvaluator.parseNameStatus(
                "A\0new.txt\0"
                        + "M\0existing.txt\0"
                        + "D\0deleted.txt\0"
                        + "R100\0old.txt\0renamed.txt\0"
        );

        assertEquals(List.of(
                new ChangeScopeEvaluation.ActualChange(
                        "new.txt", ChangeScopeEvaluation.ActualOperation.CREATE, null, "A"),
                new ChangeScopeEvaluation.ActualChange(
                        "existing.txt", ChangeScopeEvaluation.ActualOperation.MODIFY, null, "M"),
                new ChangeScopeEvaluation.ActualChange(
                        "deleted.txt", ChangeScopeEvaluation.ActualOperation.DELETE, null, "D"),
                new ChangeScopeEvaluation.ActualChange(
                        "renamed.txt", ChangeScopeEvaluation.ActualOperation.RENAME, "old.txt", "R100")
        ), changes);
    }

    @Test
    void nulFramingPreservesUnicodeTabsAndNewlinesInPaths() {
        var path = "docs/路径\t说明\n补充.md";

        var changes = ChangeScopeEvaluator.parseNameStatus("M\0" + path + "\0");

        assertEquals(List.of(new ChangeScopeEvaluation.ActualChange(
                path,
                ChangeScopeEvaluation.ActualOperation.MODIFY,
                null,
                "M"
        )), changes);
    }

    @Test
    void lineFramedNameStatusIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ChangeScopeEvaluator.parseNameStatus("M\tREADME.md\n")
        );
    }

    @Test
    void failedGitNameStatusCommandCannotBecomeEmptyActualChanges() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ChangeScopeEvaluator.parseNameStatus(128, "")
        );
    }

    @Test
    void missingDeclaredChangeIsObservationNotFailure() {
        var evaluation = ChangeScopeEvaluator.evaluate(
                contract(
                        entry("README.md", TaskContract.ScopeOperation.MODIFY),
                        entry("new.txt", TaskContract.ScopeOperation.CREATE)
                ),
                List.of(new ChangeScopeEvaluation.ActualChange(
                        "README.md",
                        ChangeScopeEvaluation.ActualOperation.MODIFY,
                        null,
                        "M"
                ))
        );

        assertEquals(ChangeScopeEvaluation.Status.PASS, evaluation.status());
        assertEquals(List.of("README.md"), evaluation.matchedPaths());
        assertEquals(List.of(entry("new.txt", TaskContract.ScopeOperation.CREATE)), evaluation.missingDeclared());
        assertTrue(evaluation.violations().isEmpty());
    }

    @Test
    void deleteAndRenameFailClosedBecauseContractDoesNotModelThem() {
        var evaluation = ChangeScopeEvaluator.evaluate(
                contract(entry("deleted.txt", TaskContract.ScopeOperation.MODIFY)),
                List.of(
                        new ChangeScopeEvaluation.ActualChange(
                                "deleted.txt", ChangeScopeEvaluation.ActualOperation.DELETE, null, "D"),
                        new ChangeScopeEvaluation.ActualChange(
                                "renamed.txt", ChangeScopeEvaluation.ActualOperation.RENAME, "old.txt", "R100")
                )
        );

        assertEquals(ChangeScopeEvaluation.Status.FAIL, evaluation.status());
        assertEquals(2, evaluation.violations().size());
        assertTrue(evaluation.violations().stream()
                .allMatch(violation -> "UNSUPPORTED_ACTUAL_OPERATION".equals(violation.code())));
    }

    private static TaskContract contract(TaskContract.ScopeEntry... entries) {
        return new TaskContract(
                "task-ws",
                1,
                java.time.Instant.parse("2026-07-26T00:00:00Z"),
                "Validate implementation changes",
                new TaskContract.Scope(List.of(entries), List.of()),
                List.of("AC1", "AC2"),
                List.of("Run tests"),
                List.of("Keep scope bounded"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                dev.dual.core.contract.ContractStatus.READY_FOR_REVIEW,
                1,
                new TaskContract.Source("requirement.md", "test")
        );
    }

    private static TaskContract.ScopeEntry entry(String path, TaskContract.ScopeOperation operation) {
        return new TaskContract.ScopeEntry(path, operation);
    }
}
