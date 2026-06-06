package dev.dual.runtime.handler;

enum AgentLifecycleStatus {
    READY,
    NEEDS_REVISION,
    BLOCKED,
    ACCEPTED,
    ACCEPTED_WITH_NOTES,
    REJECTED,
    INSUFFICIENT_EVIDENCE,
    UNKNOWN;

    static AgentLifecycleStatus fromDecision(String decision) {
        return switch (normalized(decision)) {
            case "READY_FOR_PRODUCT_REVIEW",
                 "READY_FOR_ARCH_REVIEW",
                 "READY_FOR_PLAN_REVIEW",
                 "READY_TO_IMPLEMENT",
                 "IMPLEMENTED",
                 "SELF_TEST_COMPLETE",
                 "HANDOFF_READY",
                 "READY_FOR_REVIEW" -> READY;
            case "NEEDS_REVISION",
                 "NEEDS_CLARIFICATION",
                 "NEEDS_SCOPE_REDUCTION" -> NEEDS_REVISION;
            case "BLOCKED" -> BLOCKED;
            case "ACCEPT" -> ACCEPTED;
            case "ACCEPT_WITH_NOTES" -> ACCEPTED_WITH_NOTES;
            case "REJECT",
                 "REJECTED",
                 "REJECT_CONTRACT_GAP",
                 "REJECT_CONTRACT_AMBIGUITY",
                 "REJECT_IMPLEMENTATION_BUG",
                 "REJECT_CONTEXT_INSUFFICIENT",
                 "REJECT_TOOL_LIMITATION",
                 "REJECT_REVIEW_NOISE" -> REJECTED;
            case "SELF_TEST_INCOMPLETE",
                 "NOTED_CONTEXT_INSUFFICIENT" -> INSUFFICIENT_EVIDENCE;
            case "NOTED_CONTRACT_GAP",
                 "NOTED_CONTRACT_AMBIGUITY",
                 "NOTED_IMPLEMENTATION_BUG",
                 "NOTED_TOOL_LIMITATION",
                 "NOTED_REVIEW_NOISE" -> ACCEPTED_WITH_NOTES;
            default -> UNKNOWN;
        };
    }

    static AgentLifecycleStatus fromText(String value) {
        try {
            return AgentLifecycleStatus.valueOf(normalized(value));
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }
}
