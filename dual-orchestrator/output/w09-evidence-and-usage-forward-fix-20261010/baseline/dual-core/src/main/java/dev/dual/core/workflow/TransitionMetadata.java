package dev.dual.core.workflow;

import java.util.LinkedHashMap;
import java.util.Map;

public record TransitionMetadata(
        String transitionType,
        String branchGroupId,
        boolean required,
        String purpose,
        String joinStrategy,
        String loopReason
) {
    public TransitionMetadata {
        transitionType = normalize(transitionType, "normal");
        branchGroupId = normalize(branchGroupId, "");
        purpose = normalize(purpose, "");
        joinStrategy = normalize(joinStrategy, "");
        loopReason = normalize(loopReason, "");
        if (!isKnownType(transitionType)) {
            throw new IllegalArgumentException("unknown transition type: " + transitionType);
        }
        if ("join".equals(transitionType) && joinStrategy.isBlank()) {
            joinStrategy = "all_terminal";
        }
    }

    public static TransitionMetadata normal() {
        return new TransitionMetadata("normal", "", true, "", "", "");
    }

    public static TransitionMetadata loop(String reason) {
        return new TransitionMetadata("loop", "", true, "", "", reason);
    }

    public static TransitionMetadata fork(String branchGroupId, boolean required, String purpose) {
        return new TransitionMetadata("fork", branchGroupId, required, purpose, "", "");
    }

    public static TransitionMetadata join(String branchGroupId, String strategy, String purpose) {
        return new TransitionMetadata("join", branchGroupId, true, purpose, strategy, "");
    }

    public Map<String, Object> toAttributes() {
        var attributes = new LinkedHashMap<String, Object>();
        attributes.put("transition_type", transitionType);
        attributes.put("required", required);
        if (!branchGroupId.isBlank()) {
            attributes.put("branch_group_id", branchGroupId);
        }
        if (!purpose.isBlank()) {
            attributes.put("purpose", purpose);
        }
        if (!joinStrategy.isBlank()) {
            attributes.put("join_strategy", joinStrategy);
        }
        if (!loopReason.isBlank()) {
            attributes.put("loop_reason", loopReason);
        }
        return Map.copyOf(attributes);
    }

    private static boolean isKnownType(String type) {
        return switch (type) {
            case "normal", "loop", "fork", "join" -> true;
            default -> false;
        };
    }

    private static String normalize(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }
}
