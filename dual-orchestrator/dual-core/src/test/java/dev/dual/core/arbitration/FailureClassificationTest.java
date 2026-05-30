package dev.dual.core.arbitration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FailureClassificationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private static FailureClassification sampleClassification(
            FailureCategory category,
            String reasoning,
            String suggestedAction,
            String source,
            Instant classifiedAt,
            int matchedRule,
            String reviewDecision,
            List<String> evidence
    ) {
        return new FailureClassification(
                category, reasoning, suggestedAction, source,
                classifiedAt, matchedRule, reviewDecision, evidence
        );
    }

    private static FailureClassification validClassification() {
        return sampleClassification(
                FailureCategory.IMPLEMENTATION_BUG,
                "Code references undefined variable",
                "Fix the variable reference",
                "deterministic",
                Instant.parse("2026-05-30T10:00:00Z"),
                3,
                "REJECT",
                List.of("line 42: undefined var")
        );
    }

    @Test
    void enumHasSixValues() {
        FailureCategory[] values = FailureCategory.values();
        assertEquals(6, values.length);
        assertTrue(Arrays.asList(values).containsAll(List.of(
                FailureCategory.CONTRACT_GAP,
                FailureCategory.CONTRACT_AMBIGUITY,
                FailureCategory.IMPLEMENTATION_BUG,
                FailureCategory.CONTEXT_INSUFFICIENT,
                FailureCategory.TOOL_LIMITATION,
                FailureCategory.REVIEW_NOISE
        )));
    }

    @Test
    void recordRequiresCategory() {
        assertThrows(IllegalArgumentException.class, () -> sampleClassification(
                null, "reasoning", "action", "source",
                Instant.now(), 1, "REJECT", List.of()
        ));
    }

    @Test
    void recordRequiresReasoning() {
        assertThrows(IllegalArgumentException.class, () -> sampleClassification(
                FailureCategory.IMPLEMENTATION_BUG, "", "action", "source",
                Instant.now(), 1, "REJECT", List.of()
        ));
        assertThrows(IllegalArgumentException.class, () -> sampleClassification(
                FailureCategory.IMPLEMENTATION_BUG, null, "action", "source",
                Instant.now(), 1, "REJECT", List.of()
        ));
    }

    @Test
    void recordRequiresSuggestedAction() {
        assertThrows(IllegalArgumentException.class, () -> sampleClassification(
                FailureCategory.IMPLEMENTATION_BUG, "reasoning", "", "source",
                Instant.now(), 1, "REJECT", List.of()
        ));
        assertThrows(IllegalArgumentException.class, () -> sampleClassification(
                FailureCategory.IMPLEMENTATION_BUG, "reasoning", null, "source",
                Instant.now(), 1, "REJECT", List.of()
        ));
    }

    @Test
    void recordRequiresSource() {
        assertThrows(IllegalArgumentException.class, () -> sampleClassification(
                FailureCategory.IMPLEMENTATION_BUG, "reasoning", "action", "",
                Instant.now(), 1, "REJECT", List.of()
        ));
        assertThrows(IllegalArgumentException.class, () -> sampleClassification(
                FailureCategory.IMPLEMENTATION_BUG, "reasoning", "action", null,
                Instant.now(), 1, "REJECT", List.of()
        ));
    }

    @Test
    void recordRequiresClassifiedAt() {
        assertThrows(IllegalArgumentException.class, () -> sampleClassification(
                FailureCategory.IMPLEMENTATION_BUG, "reasoning", "action", "source",
                null, 1, "REJECT", List.of()
        ));
    }

    @Test
    void recordRequiresReviewDecision() {
        assertThrows(IllegalArgumentException.class, () -> sampleClassification(
                FailureCategory.IMPLEMENTATION_BUG, "reasoning", "action", "source",
                Instant.now(), 1, "", List.of()
        ));
        assertThrows(IllegalArgumentException.class, () -> sampleClassification(
                FailureCategory.IMPLEMENTATION_BUG, "reasoning", "action", "source",
                Instant.now(), 1, null, List.of()
        ));
    }

    @Test
    void recordValidatesMatchedRule() {
        // Below range
        assertThrows(IllegalArgumentException.class, () -> sampleClassification(
                FailureCategory.IMPLEMENTATION_BUG, "reasoning", "action", "source",
                Instant.now(), 0, "REJECT", List.of()
        ));
        // Above range
        assertThrows(IllegalArgumentException.class, () -> sampleClassification(
                FailureCategory.IMPLEMENTATION_BUG, "reasoning", "action", "source",
                Instant.now(), 8, "REJECT", List.of()
        ));
        // Boundary valid: 1
        assertDoesNotThrow(() -> sampleClassification(
                FailureCategory.IMPLEMENTATION_BUG, "reasoning", "action", "source",
                Instant.now(), 1, "REJECT", List.of()
        ));
        // Boundary valid: 7
        assertDoesNotThrow(() -> sampleClassification(
                FailureCategory.IMPLEMENTATION_BUG, "reasoning", "action", "source",
                Instant.now(), 7, "REJECT", List.of()
        ));
    }

    @Test
    void recordNullEvidenceBecomesEmpty() {
        FailureClassification fc = sampleClassification(
                FailureCategory.IMPLEMENTATION_BUG, "reasoning", "action", "source",
                Instant.now(), 1, "REJECT", null
        );
        assertNotNull(fc.evidence());
        assertTrue(fc.evidence().isEmpty());
        // Verify immutability
        assertThrows(UnsupportedOperationException.class, () ->
                fc.evidence().add("should fail")
        );
    }

    @Test
    void serializationRoundTrip() throws Exception {
        FailureClassification original = validClassification();

        String json = MAPPER.writeValueAsString(original);
        FailureClassification deserialized = MAPPER.readValue(json, FailureClassification.class);

        assertEquals(original.category(), deserialized.category());
        assertEquals(original.reasoning(), deserialized.reasoning());
        assertEquals(original.suggestedAction(), deserialized.suggestedAction());
        assertEquals(original.source(), deserialized.source());
        assertEquals(original.classifiedAt(), deserialized.classifiedAt());
        assertEquals(original.matchedRule(), deserialized.matchedRule());
        assertEquals(original.reviewDecision(), deserialized.reviewDecision());
        assertEquals(original.evidence(), deserialized.evidence());

        // Verify snake_case JSON field names
        assertTrue(json.contains("\"category\""));
        assertTrue(json.contains("\"reasoning\""));
        assertTrue(json.contains("\"suggested_action\""));
        assertTrue(json.contains("\"source\""));
        assertTrue(json.contains("\"classified_at\""));
        assertTrue(json.contains("\"matched_rule\""));
        assertTrue(json.contains("\"review_decision\""));
        assertTrue(json.contains("\"evidence\""));
    }
}
