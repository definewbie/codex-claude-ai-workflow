package dev.dual.core.workflow;

import java.util.List;

/** Definition-time guard for the opt-in change-scope repair route. */
final class ChangeScopeViolationRoutingSemantics {
    private static final String REPAIR_GATE = "gate.human_implementation_repair";
    private static final String REPAIR_CONTEXT = "context.implement_claude";
    private static final String VALIDATION_NODE = "implement.artifact_validation";

    private ChangeScopeViolationRoutingSemantics() {
    }

    static void validate(List<NodeDefinition> nodes, List<EdgeDefinition> edges) {
        for (var source : nodes) {
            var configured = source.config().get("route_scope_violation");
            if (configured == null) {
                continue;
            }
            if (!(configured instanceof Boolean enabled)) {
                throw new IllegalArgumentException("route_scope_violation must be a boolean on " + source.id());
            }
            if (!enabled) {
                continue;
            }
            if (!VALIDATION_NODE.equals(source.id()) || !"artifact.validate".equals(source.handlerType())) {
                throw new IllegalArgumentException(
                        "route_scope_violation requires " + VALIDATION_NODE + " with artifact.validate");
            }
            var violationEdges = edges.stream()
                    .filter(edge -> edge.from().equals(source.id()))
                    .filter(edge -> edge.on().equals("CHANGE_SCOPE_VIOLATION"))
                    .toList();
            if (violationEdges.size() != 1) {
                throw new IllegalArgumentException(source.id()
                        + " must declare exactly one CHANGE_SCOPE_VIOLATION edge");
            }
            var edge = violationEdges.getFirst();
            if (!REPAIR_GATE.equals(edge.to()) || !"unconditional".equals(edge.condition().type())) {
                throw new IllegalArgumentException("CHANGE_SCOPE_VIOLATION must route unconditionally to " + REPAIR_GATE);
            }
            var gate = node(nodes, REPAIR_GATE);
            var resolvingActions = HumanGateActionSemantics.allowedActions(gate, "CHANGE_SCOPE_VIOLATION").stream()
                    .filter(action -> !HumanGateActionSemantics.REQUEST_ACTION.equals(action)).toList();
            if (gate.kind() != NodeKind.GATE
                    || !"human.review".equals(gate.handlerType())
                    || !"implementation_repair".equals(String.valueOf(gate.config().get("gate_purpose")))
                    || !(List.of("revise").equals(resolvingActions)
                        || List.of("revise", HumanGateActionSemantics.REQUEST_SCOPE_REVISION)
                            .equals(resolvingActions))) {
                throw new IllegalArgumentException(REPAIR_GATE + " must declare only bounded implementation repair actions");
            }
            var scopeEdges = edges.stream().filter(candidate -> candidate.from().equals(REPAIR_GATE)
                    && candidate.on().equals(HumanGateActionSemantics.REQUEST_SCOPE_REVISION)).toList();
            var scopeEnabled = HumanGateActionSemantics.allowedActions(gate)
                    .contains(HumanGateActionSemantics.REQUEST_SCOPE_REVISION);
            if (scopeEdges.size() != (scopeEnabled ? 1 : 0)
                    || (scopeEnabled && (!scopeEdges.getFirst().to().equals("context.intake_claude_brainstorm")
                        || !scopeEdges.getFirst().condition().type().equals("unconditional")
                        || !scopeEdges.getFirst().metadata().transitionType().equals("loop")
                        || !scopeEdges.getFirst().metadata().required()
                        || !scopeEdges.getFirst().metadata().branchGroupId().isBlank()))) {
                throw new IllegalArgumentException("scope revision requires exactly one bounded intake loop");
            }
            var repairEdges = edges.stream()
                    .filter(candidate -> candidate.from().equals(REPAIR_GATE))
                    .filter(candidate -> candidate.on().equals("revise"))
                    .toList();
            if (repairEdges.size() != 1) {
                throw new IllegalArgumentException(REPAIR_GATE + " must declare exactly one revise edge");
            }
            var repair = repairEdges.getFirst();
            if (!REPAIR_CONTEXT.equals(repair.to())
                    || !"unconditional".equals(repair.condition().type())
                    || !"loop".equals(repair.metadata().transitionType())) {
                throw new IllegalArgumentException(REPAIR_GATE
                        + " revise must be an unconditional loop to " + REPAIR_CONTEXT);
            }
        }
    }

    private static NodeDefinition node(List<NodeDefinition> nodes, String id) {
        return nodes.stream().filter(node -> node.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("required node does not exist: " + id));
    }
}
