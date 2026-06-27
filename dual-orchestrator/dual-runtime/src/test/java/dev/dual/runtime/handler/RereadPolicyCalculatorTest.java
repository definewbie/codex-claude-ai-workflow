package dev.dual.runtime.handler;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the harness-computed {@link RereadPolicyCalculator}. The policy is
 * derived from objective facts (actual reads, reusable files, invalidation,
 * expected edits) — it never consults agent self-reported {@code reread_reasons}
 * as the sole justification.
 */
class RereadPolicyCalculatorTest {

    @Test
    void reReadOfExpectedEditFileIsAllowedWithoutSelfReport() {
        var result = RereadPolicyCalculator.compute(
                Set.of("src/a.js"),
                Set.of("src/a.js"),
                Set.of(),
                Set.of("src/a.js"));
        assertTrue(result.denied().isEmpty(), result.denied().toString());
        var decision = result.decisions().get(0);
        assertEquals("src/a.js", decision.path());
        assertTrue(decision.allowed());
        assertTrue(decision.reason().contains("editing"));
    }

    @Test
    void reReadOfInvalidatedFileIsAllowed() {
        var result = RereadPolicyCalculator.compute(
                Set.of("src/a.js"),
                Set.of("src/a.js"),
                Set.of("src/a.js"),
                Set.of());
        assertTrue(result.denied().isEmpty());
        assertTrue(result.decisions().get(0).reason().contains("changed"));
    }

    @Test
    void reReadOfNonExpectedReusableFileIsDenied() {
        var result = RereadPolicyCalculator.compute(
                Set.of("src/a.js", "docs/b.md"),
                Set.of("src/a.js", "docs/b.md"),
                Set.of(),
                Set.of("src/a.js"));
        // src/a.js is an expected edit -> allowed; docs/b.md is reusable but not edited/invalidated -> denied
        assertEquals(List.of("docs/b.md"), result.denied());
    }

    @Test
    void nonReusableReadsAreNotPolicyConcerns() {
        var result = RereadPolicyCalculator.compute(
                Set.of("src/new.js"),
                Set.of(),
                Set.of(),
                Set.of());
        assertTrue(result.decisions().isEmpty());
        assertTrue(result.denied().isEmpty());
    }

    @Test
    void calculatorDoesNotConsumeSelfReportedReasons() {
        // The calculator signature takes no reread_reasons: self-report cannot be the
        // sole justification. A denied file stays denied regardless of any prose reason.
        var result = RereadPolicyCalculator.compute(
                Set.of("docs/b.md"),
                Set.of("docs/b.md"),
                Set.of(),
                Set.of());
        assertEquals(List.of("docs/b.md"), result.denied());
    }
}
