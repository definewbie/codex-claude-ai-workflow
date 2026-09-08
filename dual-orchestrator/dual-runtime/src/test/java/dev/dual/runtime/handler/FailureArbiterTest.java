package dev.dual.runtime.handler;

import dev.dual.core.arbitration.FailureCategory;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class FailureArbiterTest {
    @Test
    void everyCanonicalCategoryIsPreservedWithoutEvidenceHeuristics() {
        for (var category : FailureCategory.values()) {
            var evidence = List.of("Python service missing test output", "Java tool contract wording", "site/README.md");
            var result = new FailureArbiter().classify(category, evidence);
            assertEquals(category, result.category());
            assertEquals(evidence, result.evidence());
            assertEquals("REJECT", result.reviewDecision());
            assertEquals(0, result.matchedRule(), "explicit classification has no priority rule");
            assertFalse(result.suggestedAction().isBlank());
        }
    }

    @Test
    void noMissingCategoryFallback() {
        assertThrows(NullPointerException.class, () -> new FailureArbiter().classify(null, List.of()));
    }
}
