package dev.dual.runtime.handler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChangeScopeEvaluationArtifactTest {
    @TempDir
    Path tempDir;

    @Test
    void publishesAndVerifiesOnlyTheExpectedOccurrence() throws Exception {
        var evaluation = new ChangeScopeEvaluation(
                1, ChangeScopeEvaluation.Status.FAIL, List.of(), List.of(), List.of(), List.of(), null);

        var reference = ChangeScopeEvaluationArtifact.publish(tempDir, "ws-one", "node-validation-001", evaluation);
        var verified = ChangeScopeEvaluationArtifact.verify(
                tempDir, "ws-one", "node-validation-001", reference.ref(), reference.sha256());

        assertEquals(ChangeScopeEvaluation.Status.FAIL, verified.document().evaluation().status());
        assertThrows(Exception.class, () -> ChangeScopeEvaluationArtifact.verify(
                tempDir, "ws-two", "node-validation-001", reference.ref(), reference.sha256()));
        assertThrows(Exception.class, () -> ChangeScopeEvaluationArtifact.verify(
                tempDir, "ws-one", "node-validation-002", reference.ref(), reference.sha256()));
        assertThrows(Exception.class, () -> ChangeScopeEvaluationArtifact.verify(
                tempDir, "ws-one", "node-validation-001",
                ".agent/workstreams/ws-one/artifacts/git-evidence/change-scope-evaluation.json",
                reference.sha256()));
    }

    @Test
    void rejectsDeletedOrTamperedOccurrence() throws Exception {
        var evaluation = new ChangeScopeEvaluation(
                1, ChangeScopeEvaluation.Status.FAIL, List.of(), List.of(), List.of(), List.of(), null);
        var deleted = ChangeScopeEvaluationArtifact.publish(tempDir, "ws", "node-one", evaluation);
        Files.delete(tempDir.resolve(deleted.ref()));
        assertThrows(Exception.class, () -> ChangeScopeEvaluationArtifact.verify(
                tempDir, "ws", "node-one", deleted.ref(), deleted.sha256()));

        var tampered = ChangeScopeEvaluationArtifact.publish(tempDir, "ws", "node-two", evaluation);
        Files.writeString(tempDir.resolve(tampered.ref()), "{}\n");
        assertThrows(Exception.class, () -> ChangeScopeEvaluationArtifact.verify(
                tempDir, "ws", "node-two", tampered.ref(), tampered.sha256()));
        assertThrows(Exception.class, () -> ChangeScopeEvaluationArtifact.publish(
                tempDir, "ws", "node-two", evaluation));
    }

    @Test
    void siblingPublicationCannotReplaceAnEarlierOccurrence() throws Exception {
        var firstEvaluation = new ChangeScopeEvaluation(
                1, ChangeScopeEvaluation.Status.FAIL, List.of(), List.of(), List.of(), List.of(), null);
        var secondEvaluation = ChangeScopeEvaluation.unavailable("second occurrence");
        var first = ChangeScopeEvaluationArtifact.publish(tempDir, "ws", "node-one", firstEvaluation);
        var firstBytes = Files.readAllBytes(tempDir.resolve(first.ref()));

        ChangeScopeEvaluationArtifact.publish(tempDir, "ws", "node-two", secondEvaluation);

        assertEquals(java.util.HexFormat.of().formatHex(
                        java.security.MessageDigest.getInstance("SHA-256").digest(firstBytes)),
                first.sha256());
        assertEquals(List.of(), ChangeScopeEvaluationArtifact.verify(
                tempDir, "ws", "node-one", first.ref(), first.sha256())
                .document().evaluation().violations());
    }
}
