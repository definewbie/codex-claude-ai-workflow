package dev.dual.runtime.handler;

import dev.dual.core.arbitration.FailureCategory;
import dev.dual.core.arbitration.FailureClassification;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Consumes the canonical category only after shared acceptance semantic validation. */
public final class FailureArbiter {
    public FailureClassification classify(FailureCategory category, List<String> evidence) {
        Objects.requireNonNull(category, "validated acceptance category is required");
        var action = switch (category) {
            case CONTRACT_GAP, CONTRACT_AMBIGUITY -> "Revise the proposal and obtain new approvals before compiling";
            case IMPLEMENTATION_BUG -> "Repair within the approved contract, then run self-test";
            case VERIFICATION_INSUFFICIENT -> "Collect existing verification evidence in self-test";
            case CONTEXT_INSUFFICIENT, TOOL_LIMITATION ->
                    "Obtain human disposition of the missing prerequisites: collect evidence or explicitly authorize repair";
            case REVIEW_NOISE -> "Re-run acceptance review";
        };
        return new FailureClassification(category, "Validated explicit acceptance category: " + category,
                action, "deterministic", Instant.now(), 0, "REJECT", List.copyOf(evidence));
    }
}
