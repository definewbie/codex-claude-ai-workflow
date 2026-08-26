package dev.dual.core.workflow;

import java.util.List;

/** Shared definition and runtime semantics for human gate actions. */
public final class HumanGateActionSemantics {
    private static final List<String> DEFAULT_ACTIONS = List.of("approve", "revise");

    private HumanGateActionSemantics() {
    }

    public static List<String> allowedActions(NodeDefinition definition) {
        var configured = definition.config().get("allowed_actions");
        if (configured instanceof List<?> list && !list.isEmpty()) {
            return list.stream()
                    .map(String::valueOf)
                    .filter(action -> !action.isBlank())
                    .toList();
        }
        return DEFAULT_ACTIONS;
    }

    public static String canonicalAction(List<EdgeDefinition> outgoingEdges, String requestedAction) {
        if (!"approve".equals(requestedAction)) {
            return requestedAction;
        }
        var hasApproveEdge = outgoingEdges.stream().anyMatch(edge -> "approve".equals(edge.on()));
        var hasApprovedEdge = outgoingEdges.stream().anyMatch(edge -> "approved".equals(edge.on()));
        return !hasApproveEdge && hasApprovedEdge ? "approved" : requestedAction;
    }

    static void validateVisibility(List<NodeDefinition> nodes, List<EdgeDefinition> edges) {
        for (var node : nodes) {
            if (node.kind() != NodeKind.GATE) {
                continue;
            }
            var outgoingEdges = edges.stream().filter(edge -> edge.from().equals(node.id())).toList();
            var allowedActions = allowedActions(node);
            for (var edge : outgoingEdges) {
                var visible = allowedActions.stream()
                        .map(action -> canonicalAction(outgoingEdges, action))
                        .anyMatch(edge.on()::equals);
                if (!visible) {
                    throw new IllegalArgumentException(
                            "gate " + node.id() + " has hidden outgoing action " + edge.on()
                                    + "; effective allowed actions: " + allowedActions);
                }
            }
        }
    }
}
