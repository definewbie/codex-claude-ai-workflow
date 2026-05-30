package dev.dual.core.arbitration;

/**
 * Semantic failure categories classified by FailureArbiter.
 * Priority-ordered: CONTRACT_GAP > CONTRACT_AMBIGUITY > IMPLEMENTATION_BUG >
 * CONTEXT_INSUFFICIENT > TOOL_LIMITATION > REVIEW_NOISE.
 */
public enum FailureCategory {
    CONTRACT_GAP,
    CONTRACT_AMBIGUITY,
    IMPLEMENTATION_BUG,
    CONTEXT_INSUFFICIENT,
    TOOL_LIMITATION,
    REVIEW_NOISE
}
