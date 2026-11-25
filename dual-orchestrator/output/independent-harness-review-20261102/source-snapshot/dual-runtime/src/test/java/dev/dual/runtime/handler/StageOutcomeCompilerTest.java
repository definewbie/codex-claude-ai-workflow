package dev.dual.runtime.handler;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StageOutcomeCompilerTest {

    @Test
    void passUsesTheCanonicalReadyOutcome() {
        var result = StageOutcomeCompiler.compile("PASS", "");

        assertTrue(result.valid());
        assertEquals(StageOutcome.READY, result.outcome());
        assertEquals("decision", result.source());
    }

    @Test
    void preservesDeclaredInsufficientEvidenceForRejectedDecision() {
        var result = StageOutcomeCompiler.compile("REJECT", "INSUFFICIENT_EVIDENCE");

        assertTrue(result.valid());
        assertEquals(StageOutcome.INSUFFICIENT_EVIDENCE, result.outcome());
        assertEquals("declared_status", result.source());
    }

    @Test
    void rejectsDeclaredStatusThatConflictsWithDecision() {
        var result = StageOutcomeCompiler.compile("ACCEPT", "REJECTED");

        assertTrue(!result.valid());
        assertEquals("STRUCTURED_ARTIFACT_STATUS_MISMATCH", result.errorCode());
    }

    @Test
    void sidecarFieldsExposeCanonicalStageOutcome() {
        var sidecar = new LinkedHashMap<String, Object>();
        sidecar.put("decision", "REJECT");
        sidecar.put("lifecycle_status", "INSUFFICIENT_EVIDENCE");
        sidecar.put("must_fix", List.of("Regenerate git evidence"));
        sidecar.put("missing_evidence", List.of("git evidence is empty"));
        sidecar.put("blocking", true);

        StageOutcomeCompiler.addSidecarFields(sidecar);

        assertEquals("INSUFFICIENT_EVIDENCE", sidecar.get("stage_outcome"));
        assertEquals("INSUFFICIENT_EVIDENCE", sidecar.get("lifecycle_status"));
        assertEquals("declared_status", sidecar.get("stage_outcome_source"));
    }
}
