package dev.dual.runtime.handler;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AuthoredRevisionCoverageTest {
    private static final String FIRST = "sha256:" + "1".repeat(64) + "#must_fix:0";
    private static final String SECOND = "sha256:" + "2".repeat(64) + "#human-note:0";

    @Test
    void aLaterResponseCannotEraseAnEarlierObligation() {
        for (var text : List.of("Keep document formatting", "Preserve site navigation")) {
            var first = response(FIRST, text);
            var second = response(SECOND, text);
            assertTrue(AuthoredRevisionValidator.validate(List.of(first), List.of(FIRST)).isEmpty());
            assertFalse(AuthoredRevisionValidator.validate(List.of(second), List.of(FIRST, SECOND)).isEmpty());
            assertTrue(AuthoredRevisionValidator.validate(List.of(second, first), List.of(FIRST, SECOND)).isEmpty());
            assertFalse(AuthoredRevisionValidator.validate(List.of(first, first), List.of(FIRST, SECOND)).isEmpty());
            assertFalse(AuthoredRevisionValidator.validate(List.of(first, second), List.of(FIRST)).isEmpty());
        }
    }

    @Test
    void initialMustBeExplicitlyEmptyAndMissingEvidenceCannotClaimCoverage() {
        assertTrue(AuthoredRevisionValidator.validate(List.of(), List.of()).isEmpty());
        assertFalse(AuthoredRevisionValidator.validate(null, List.of()).isEmpty());
        assertFalse(AuthoredRevisionValidator.validate(List.of(response(FIRST, "")), List.of(FIRST)).isEmpty());
        assertFalse(AuthoredRevisionValidator.validate(List.of(response(FIRST, "answer")), List.of()).isEmpty());
        assertTrue(AuthoredRevisionValidator.validate(List.of(new AgentResultEnvelope.AuthoredRevisionResponse(
                FIRST, AgentResultEnvelope.AuthoredRevisionDisposition.NOT_ADDRESSED, "Needs a decision")),
                List.of(FIRST)).isEmpty());
    }

    private AgentResultEnvelope.AuthoredRevisionResponse response(String id, String text) {
        return new AgentResultEnvelope.AuthoredRevisionResponse(id,
                AgentResultEnvelope.AuthoredRevisionDisposition.ADDRESSED, text);
    }
}
