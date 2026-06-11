package dev.dual.runtime.handler;

final class ReviewNoiseSemantics {
    private ReviewNoiseSemantics() {
    }

    static boolean isLikelyReviewNoise(String reviewDecision, String reviewSummary) {
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
}
