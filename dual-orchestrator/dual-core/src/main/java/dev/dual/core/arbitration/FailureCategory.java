package dev.dual.core.arbitration;

/**
 * Canonical explicit acceptance failure categories consumed by FailureArbiter.
 * Declaration order has no routing priority.
 */
public enum FailureCategory {
    CONTRACT_GAP,
    CONTRACT_AMBIGUITY,
    IMPLEMENTATION_BUG,
    CONTEXT_INSUFFICIENT,
    VERIFICATION_INSUFFICIENT,
    TOOL_LIMITATION,
    REVIEW_NOISE
}
