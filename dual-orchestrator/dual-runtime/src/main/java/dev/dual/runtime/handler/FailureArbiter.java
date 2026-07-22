package dev.dual.runtime.handler;

import dev.dual.core.arbitration.FailureCategory;
import dev.dual.core.arbitration.FailureClassification;
import dev.dual.core.contract.TaskContract;

import java.time.Instant;
import java.util.List;

/**
 * Deterministic rule-based failure classifier.
 * Priority-ordered rules: first match wins.
 */
public final class FailureArbiter {

    public FailureArbiter() {}

    /**
     * Classify a failure based on review outcome and available context.
     *
     * @param contractGaps      optional contract_gaps from Codex review (may be null/empty)
     * @param outOfScopeFiles   optional out_of_scope_files from Codex review (may be null/empty)
     * @param missingTests      optional missing_tests from Codex review (may be null/empty)
     * @param contextGaps       optional context_gaps from Codex review (may be null/empty)
     * @param reviewSummary     the review summary text
     * @param taskContract      the TaskContract (may be null)
     * @param changedFileCount  number of files changed (from git evidence), -1 if unknown
     * @param reviewDecision    "REJECT" or "ACCEPT_WITH_NOTES"
     * @return FailureClassification with the matched category
     */
    public FailureClassification classify(
            List<String> contractGaps,
            List<String> outOfScopeFiles,
            List<String> missingTests,
            List<String> contextGaps,
            String reviewSummary,
            TaskContract taskContract,
            int changedFileCount,
            String reviewDecision
    ) {
        return classify(
                contractGaps, outOfScopeFiles, missingTests, contextGaps,
                reviewSummary, taskContract, changedFileCount, reviewDecision, ""
        );
    }

    public FailureClassification classify(
            List<String> contractGaps,
            List<String> outOfScopeFiles,
            List<String> missingTests,
            List<String> contextGaps,
            String reviewSummary,
            TaskContract taskContract,
            int changedFileCount,
            String reviewDecision,
            String categoryHint
    ) {
        // Rule 1: CONTRACT_GAP
        if (contractGaps != null && !contractGaps.isEmpty()) {
            return build(FailureCategory.CONTRACT_GAP, 1,
                    "Review identified contract gaps: " + String.join(", ", contractGaps),
                    "Revise TaskContract to address gaps, then re-compile",
                    contractGaps, reviewDecision);
        }

        // Rule 2: CONTRACT_AMBIGUITY
        if (taskContract != null) {
            boolean hasAmbiguityNotes = taskContract.ambiguityNotes() != null && !taskContract.ambiguityNotes().isEmpty();
            boolean emptyScope = taskContract.scope() == null || taskContract.scope().included().isEmpty();
            boolean weakCriteria = taskContract.acceptanceCriteria() != null && taskContract.acceptanceCriteria().size() < 2;
            if (hasAmbiguityNotes || emptyScope || weakCriteria) {
                String detail = hasAmbiguityNotes ? "ambiguity notes present" :
                        emptyScope ? "scope.included is empty" : "fewer than 2 acceptance criteria";
                return build(FailureCategory.CONTRACT_AMBIGUITY, 2,
                        "Contract has ambiguity indicators: " + detail,
                        "Clarify TaskContract scope and acceptance criteria",
                        List.of(detail), reviewDecision);
            }
        }

        // Rule 3: IMPLEMENTATION_BUG
        if (outOfScopeFiles != null && !outOfScopeFiles.isEmpty()) {
            return build(FailureCategory.IMPLEMENTATION_BUG, 3,
                    "Implementation touched out-of-scope files: " + String.join(", ", outOfScopeFiles),
                    "Re-implement, staying within scope boundaries",
                    outOfScopeFiles, reviewDecision);
        }

        // Rule 4: VERIFICATION_INSUFFICIENT. Explicit missing verification evidence
        // outranks a generic context gap so recovery returns to self-test collection.
        if ((missingTests != null && !missingTests.isEmpty()) || isVerificationInsufficient(categoryHint)) {
            var evidence = missingTests == null ? List.<String>of() : missingTests;
            var detail = evidence.isEmpty() ? "typed review category_hint indicates insufficient verification evidence"
                    : "Review identified missing verification evidence: " + String.join(", ", evidence);
            return build(FailureCategory.VERIFICATION_INSUFFICIENT, 4,
                    detail,
                    "Re-run self-test/evidence collection before implementation repair",
                    evidence, reviewDecision);
        }

        // Rule 5: CONTEXT_INSUFFICIENT
        if (contextGaps != null && !contextGaps.isEmpty()) {
            return build(FailureCategory.CONTEXT_INSUFFICIENT, 5,
                    "Review identified context gaps: " + String.join(", ", contextGaps),
                    "Enrich context package and re-submit",
                    contextGaps, reviewDecision);
        }

        // Rule 6: TOOL_LIMITATION
        if (changedFileCount == 0) {
            return build(FailureCategory.TOOL_LIMITATION, 6,
                    "No files were changed — tool/execution issue",
                    "Retry implementation with adjusted tool configuration",
                    List.of(), reviewDecision);
        }

        // Rule 7: REVIEW_NOISE
        // A hard REJECT with changed files should bias toward repair unless the
        // typed review category explicitly says this is review noise.
        boolean hasGaps = contractGaps != null && !contractGaps.isEmpty();
        boolean hasOOS = outOfScopeFiles != null && !outOfScopeFiles.isEmpty();
        boolean hasMissing = missingTests != null && !missingTests.isEmpty();
        boolean hasContextGaps = contextGaps != null && !contextGaps.isEmpty();
        boolean noOptionalFields = !hasGaps && !hasOOS && !hasMissing && !hasContextGaps;
        boolean noAmbiguity = taskContract == null ||
                (taskContract.ambiguityNotes() == null || taskContract.ambiguityNotes().isEmpty());
        if (noOptionalFields && noAmbiguity && isReviewNoise(categoryHint)) {
            var suggestedAction = "REJECT".equals(reviewDecision)
                    ? "Re-run acceptance review automatically"
                    : "Record non-blocking acceptance notes and continue";
            return build(FailureCategory.REVIEW_NOISE, 7,
                    "No structured failure indicators found — likely reviewer noise",
                    suggestedAction,
                    List.of(), reviewDecision);
        }

        // Rule 8 (fallback): IMPLEMENTATION_BUG
        return build(FailureCategory.IMPLEMENTATION_BUG, 8,
                "Default classification — assume implementation needs fixing",
                "Re-implement addressing review feedback",
                List.of(), reviewDecision);
    }

    private boolean isVerificationInsufficient(String categoryHint) {
        if (categoryHint == null || categoryHint.isBlank()) {
            return false;
        }
        var normalized = normalizeCategoryHint(categoryHint);
        return normalized.equals("VERIFICATION_INSUFFICIENT")
                || normalized.equals("INSUFFICIENT_VERIFICATION")
                || normalized.equals("INSUFFICIENT_EVIDENCE")
                || normalized.equals("MISSING_VERIFICATION")
                || normalized.equals("MISSING_TEST")
                || normalized.equals("MISSING_TEST_EVIDENCE");
    }

    private boolean isReviewNoise(String categoryHint) {
        return "REVIEW_NOISE".equals(normalizeCategoryHint(categoryHint));
    }

    private String normalizeCategoryHint(String categoryHint) {
        return categoryHint == null ? "" : categoryHint.trim()
                .replace('-', '_')
                .replace(' ', '_')
                .toUpperCase(java.util.Locale.ROOT);
    }

    private FailureClassification build(FailureCategory category, int rule,
                                        String reasoning, String suggestedAction,
                                        List<String> evidence, String reviewDecision) {
        return new FailureClassification(
                category, reasoning, suggestedAction, "deterministic",
                Instant.now(), rule, reviewDecision, evidence
        );
    }
}
