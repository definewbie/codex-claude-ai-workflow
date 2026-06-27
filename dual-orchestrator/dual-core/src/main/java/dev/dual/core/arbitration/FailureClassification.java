package dev.dual.core.arbitration;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

/**
 * Immutable classification result produced by FailureArbiter.
 *
 * @param category        the semantic failure category (never null)
 * @param reasoning       why this category was chosen (never blank)
 * @param suggestedAction recommended recovery action (never blank)
 * @param source          classification origin: "deterministic" for MVP
 * @param classifiedAt    timestamp of classification
 * @param matchedRule     which rule number matched (1-based, 8 for fallback)
 * @param reviewDecision  the original decision that triggered classification (REJECT or ACCEPT_WITH_NOTES)
 * @param evidence        supporting evidence snippets (may be empty)
 */
public record FailureClassification(
        @JsonProperty("category") FailureCategory category,
        @JsonProperty("reasoning") String reasoning,
        @JsonProperty("suggested_action") String suggestedAction,
        @JsonProperty("source") String source,
        @JsonProperty("classified_at") Instant classifiedAt,
        @JsonProperty("matched_rule") int matchedRule,
        @JsonProperty("review_decision") String reviewDecision,
        @JsonProperty("evidence") List<String> evidence
) {
    public FailureClassification {
        if (category == null) {
            throw new IllegalArgumentException("category is required");
        }
        if (reasoning == null || reasoning.isBlank()) {
            throw new IllegalArgumentException("reasoning is required");
        }
        if (suggestedAction == null || suggestedAction.isBlank()) {
            throw new IllegalArgumentException("suggestedAction is required");
        }
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("source is required");
        }
        if (classifiedAt == null) {
            throw new IllegalArgumentException("classifiedAt is required");
        }
        if (reviewDecision == null || reviewDecision.isBlank()) {
            throw new IllegalArgumentException("reviewDecision is required");
        }
        if (matchedRule < 1 || matchedRule > 8) {
            throw new IllegalArgumentException("matchedRule must be 1-8");
        }
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }
}
