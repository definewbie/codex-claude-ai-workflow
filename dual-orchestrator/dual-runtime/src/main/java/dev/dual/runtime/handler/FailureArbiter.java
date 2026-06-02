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

    private static final List<String> CONTEXT_LACK_KEYWORDS = List.of(
            "unclear", "insufficient", "missing context", "could not determine",
            "not enough information", "ambiguous", "vague"
    );

    public FailureArbiter() {}

    /**
     * Classify a failure based on review outcome and available context.
     *
     * @param contractGaps      optional contract_gaps from Codex review (may be null/empty)
     * @param outOfScopeFiles   optional out_of_scope_files from Codex review (may be null/empty)
     * @param missingTests      optional missing_tests from Codex review (may be null/empty)
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
            String reviewSummary,
            TaskContract taskContract,
            int changedFileCount,
            String reviewDecision
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

        // Rule 4: CONTEXT_INSUFFICIENT
        if (reviewSummary != null) {
            String lower = reviewSummary.toLowerCase();
            for (String keyword : CONTEXT_LACK_KEYWORDS) {
                if (lower.contains(keyword)) {
                    return build(FailureCategory.CONTEXT_INSUFFICIENT, 4,
                            "Review indicates insufficient context: keyword '" + keyword + "' found",
                            "Enrich context package and re-submit",
                            List.of(keyword), reviewDecision);
                }
            }
        }

        // Rule 5: TOOL_LIMITATION
        if (changedFileCount == 0) {
            return build(FailureCategory.TOOL_LIMITATION, 5,
                    "No files were changed — tool/execution issue",
                    "Retry implementation with adjusted tool configuration",
                    List.of(), reviewDecision);
        }

        // Rule 6: REVIEW_NOISE
        // A hard REJECT with changed files should bias toward repair unless the
        // review summary itself clearly says the issue is non-blocking noise.
        boolean hasGaps = contractGaps != null && !contractGaps.isEmpty();
        boolean hasOOS = outOfScopeFiles != null && !outOfScopeFiles.isEmpty();
        boolean hasMissing = missingTests != null && !missingTests.isEmpty();
        boolean noOptionalFields = !hasGaps && !hasOOS && !hasMissing;
        boolean noAmbiguity = taskContract == null ||
                (taskContract.ambiguityNotes() == null || taskContract.ambiguityNotes().isEmpty());
        if (noOptionalFields && noAmbiguity && isLikelyReviewNoise(reviewDecision, reviewSummary)) {
            return build(FailureCategory.REVIEW_NOISE, 6,
                    "No structured failure indicators found — likely reviewer noise",
                    "Re-run acceptance review automatically",
                    List.of(), reviewDecision);
        }

        // Rule 7 (fallback): IMPLEMENTATION_BUG
        return build(FailureCategory.IMPLEMENTATION_BUG, 7,
                "Default classification — assume implementation needs fixing",
                "Re-implement addressing review feedback",
                List.of(), reviewDecision);
    }

    private boolean isLikelyReviewNoise(String reviewDecision, String reviewSummary) {
        if (!"REJECT".equals(reviewDecision)) {
            return true;
        }
        if (reviewSummary == null || reviewSummary.isBlank()) {
            return false;
        }
        var lower = reviewSummary.toLowerCase();
        return lower.contains("review noise")
                || lower.contains("non-blocking")
                || lower.contains("non blocking")
                || lower.contains("minor stylistic")
                || lower.contains("preference only");
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
