package dev.dual.core.workflow;

import java.util.List;

/** Shared definition and runtime semantics for human gate actions. */
public final class HumanGateActionSemantics {
    public static final String REQUEST_SCOPE_REVISION = "request_scope_revision";
    public static final String REQUEST_ACTION = "request_action";
    public static final String COLLECT_EVIDENCE = "collect_evidence";
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

    /** Definition declares possible actions; the source occurrence narrows their applicability. */
    public static List<String> allowedActions(NodeDefinition definition, String sourceOutcome) {
        var scopeRepair = definition.kind() == NodeKind.GATE
                && "human.review".equals(definition.handlerType())
                && "gate.human_implementation_repair".equals(definition.id())
                && "implementation_repair".equals(definition.config().get("gate_purpose"))
                && "CHANGE_SCOPE_VIOLATION".equals(sourceOutcome);
        return allowedActions(definition).stream()
                .filter(action -> !REQUEST_SCOPE_REVISION.equals(action) || scopeRepair)
                .filter(action -> !COLLECT_EVIDENCE.equals(action) || !"CHANGE_SCOPE_VIOLATION".equals(sourceOutcome))
                .toList();
    }

    static void validateVisibility(List<NodeDefinition> nodes, List<EdgeDefinition> edges) {
        for (var node : nodes) {
            if (node.kind() != NodeKind.GATE) {
                continue;
            }
            var outgoingEdges = edges.stream().filter(edge -> edge.from().equals(node.id())).toList();
            var allowedActions = allowedActions(node);
            if (allowedActions.contains(COLLECT_EVIDENCE)) {
                var routes = outgoingEdges.stream().filter(edge -> COLLECT_EVIDENCE.equals(edge.on())).toList();
                if (!"human.review".equals(node.handlerType())
                        || !"implementation_repair".equals(node.config().get("gate_purpose"))
                        || routes.size() != 1 || !"unconditional".equals(routes.getFirst().condition().type())
                        || !"normal".equals(routes.getFirst().metadata().transitionType())
                        || nodes.stream().noneMatch(target -> target.id().equals(routes.getFirst().to())
                        && "acpx.agent_run".equals(target.handlerType())
                        && "self_test_report".equals(target.config().get("expected_artifact")))) {
                    throw new IllegalArgumentException("collect_evidence requires one normal route to a self-test task");
                }
            }
            if (allowedActions.contains(REQUEST_ACTION)
                    && (!"human.review".equals(node.handlerType())
                    || !"implementation_repair".equals(node.config().get("gate_purpose")))) {
                throw new IllegalArgumentException("request_action is limited to implementation repair gates");
            }
            for (var edge : outgoingEdges) {
                if (REQUEST_ACTION.equals(edge.on())) {
                    throw new IllegalArgumentException("request_action is non-resolving and cannot have an outgoing edge");
                }
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
