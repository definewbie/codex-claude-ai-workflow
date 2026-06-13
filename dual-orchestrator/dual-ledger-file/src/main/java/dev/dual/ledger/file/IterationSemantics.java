package dev.dual.ledger.file;

public final class IterationSemantics {
    private IterationSemantics() {
    }

    public static boolean startsNewIteration(String outcome, String targetNodeDefinitionId) {
        if ("REJECT".equals(outcome) && "acceptance.classify_failure".equals(targetNodeDefinitionId)) {
            return false;
        }
        return isRevisionTrigger(outcome);
    }

    public static boolean isRevisionTrigger(String action) {
        if (action == null || action.isBlank()) {
            return false;
        }
        var normalized = action.trim();
        return switch (normalized) {
            case "revise", "revision_requested", "NEEDS_REVISION", "REJECT" -> true;
            default -> isRepairLoopTrigger(normalized);
        };
    }

    public static boolean isRepairLoopTrigger(String action) {
        if (action == null || action.isBlank()) {
            return false;
        }
        return action.trim().startsWith("REJECT_");
    }
}
