package dev.dual.core.workflow;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChangeScopeViolationRoutingSemanticsTest {
    @Test
    void acceptsTheFixedRepairProtocolAndLegacyDefinitions() {
        assertDoesNotThrow(() -> definition(validNodes(), validEdges()));
        assertDoesNotThrow(() -> new WorkflowDefinition("legacy", 1, "validation",
                List.of(new NodeDefinition("validation", NodeKind.TASK, "artifact.validate", Map.of())), List.of()));
    }

    @Test
    void rejectsMalformedFlagOrSourceIdentity() {
        assertThrows(IllegalArgumentException.class, () -> definition(
                nodes(validation("implement.artifact_validation", "artifact.validate", "true")), validEdges()));
        assertThrows(IllegalArgumentException.class, () -> definition(
                nodes(validation("validation", "artifact.validate", true)), edgesFrom("validation")));
        assertThrows(IllegalArgumentException.class, () -> definition(
                nodes(validation("implement.artifact_validation", "noop.task", true)), validEdges()));
    }

    @Test
    void rejectsMissingDuplicateConditionalOrWrongViolationEdge() {
        assertThrows(IllegalArgumentException.class, () -> definition(validNodes(), repairEdges()));
        var duplicate = new ArrayList<>(validEdges());
        duplicate.add(violationEdge(EdgeCondition.unconditional(), "gate.human_implementation_repair"));
        assertThrows(IllegalArgumentException.class, () -> definition(validNodes(), duplicate));
        assertThrows(IllegalArgumentException.class, () -> definition(validNodes(), List.of(
                violationEdge(EdgeCondition.configEquals("mode", "repair"), "gate.human_implementation_repair"),
                repairEdge(EdgeCondition.unconditional(), "context.implement_claude", true))));
        assertThrows(IllegalArgumentException.class, () -> definition(validNodes(), List.of(
                violationEdge(EdgeCondition.unconditional(), "wrong.gate"),
                repairEdge(EdgeCondition.unconditional(), "context.implement_claude", true))));
    }

    @Test
    void rejectsMalformedRepairGate() {
        assertThrows(IllegalArgumentException.class, () -> definition(
                nodes(gate(NodeKind.TASK, "human.review", "implementation_repair", List.of("revise"))), validEdges()));
        assertThrows(IllegalArgumentException.class, () -> definition(
                nodes(gate(NodeKind.GATE, "noop.gate", "implementation_repair", List.of("revise"))), validEdges()));
        assertThrows(IllegalArgumentException.class, () -> definition(
                nodes(gate(NodeKind.GATE, "human.review", "design", List.of("revise"))), validEdges()));
        assertThrows(IllegalArgumentException.class, () -> definition(
                nodes(gate(NodeKind.GATE, "human.review", "implementation_repair", List.of("approve", "revise"))),
                List.of(violationEdge(EdgeCondition.unconditional(), "gate.human_implementation_repair"),
                        repairEdge(EdgeCondition.unconditional(), "context.implement_claude", true),
                        new EdgeDefinition("gate.human_implementation_repair", "approve", "context.implement_claude"))));
    }

    @Test
    void rejectsMissingDuplicateConditionalWrongOrNonLoopRepairEdge() {
        assertThrows(IllegalArgumentException.class, () -> definition(validNodes(), List.of(
                violationEdge(EdgeCondition.unconditional(), "gate.human_implementation_repair"))));
        var duplicate = new ArrayList<>(validEdges());
        duplicate.add(repairEdge(EdgeCondition.unconditional(), "context.implement_claude", true));
        assertThrows(IllegalArgumentException.class, () -> definition(validNodes(), duplicate));
        assertThrows(IllegalArgumentException.class, () -> definition(validNodes(), List.of(
                violationEdge(EdgeCondition.unconditional(), "gate.human_implementation_repair"),
                repairEdge(EdgeCondition.configEquals("mode", "repair"), "context.implement_claude", true))));
        assertThrows(IllegalArgumentException.class, () -> definition(validNodes(), List.of(
                violationEdge(EdgeCondition.unconditional(), "gate.human_implementation_repair"),
                repairEdge(EdgeCondition.unconditional(), "wrong.context", true))));
        assertThrows(IllegalArgumentException.class, () -> definition(validNodes(), List.of(
                violationEdge(EdgeCondition.unconditional(), "gate.human_implementation_repair"),
                repairEdge(EdgeCondition.unconditional(), "context.implement_claude", false))));
    }

    private WorkflowDefinition definition(List<NodeDefinition> nodes, List<EdgeDefinition> edges) {
        return new WorkflowDefinition("scope-repair", 1, "implement.artifact_validation", nodes, edges);
    }

    private List<NodeDefinition> validNodes() {
        return nodes(gate(NodeKind.GATE, "human.review", "implementation_repair", List.of("revise")));
    }

    private List<NodeDefinition> nodes(NodeDefinition replacement) {
        var validation = replacement.id().equals("implement.artifact_validation") || replacement.id().equals("validation")
                ? replacement : validation("implement.artifact_validation", "artifact.validate", true);
        var repairGate = replacement.id().equals("gate.human_implementation_repair")
                ? replacement : gate(NodeKind.GATE, "human.review", "implementation_repair", List.of("revise"));
        return List.of(validation, repairGate,
                new NodeDefinition("context.implement_claude", NodeKind.TASK, "context.build_package", Map.of()),
                new NodeDefinition("wrong.gate", NodeKind.GATE, "human.review", Map.of()),
                new NodeDefinition("wrong.context", NodeKind.TASK, "context.build_package", Map.of()));
    }

    private NodeDefinition validation(String id, String handler, Object enabled) {
        return new NodeDefinition(id, NodeKind.TASK, handler, Map.of("route_scope_violation", enabled));
    }

    private NodeDefinition gate(NodeKind kind, String handler, String purpose, List<String> actions) {
        return new NodeDefinition("gate.human_implementation_repair", kind, handler,
                Map.of("gate_purpose", purpose, "allowed_actions", actions));
    }

    private List<EdgeDefinition> validEdges() {
        return List.of(violationEdge(EdgeCondition.unconditional(), "gate.human_implementation_repair"),
                repairEdge(EdgeCondition.unconditional(), "context.implement_claude", true));
    }

    private List<EdgeDefinition> repairEdges() {
        return List.of(repairEdge(EdgeCondition.unconditional(), "context.implement_claude", true));
    }

    private List<EdgeDefinition> edgesFrom(String source) {
        return List.of(new EdgeDefinition(source, "CHANGE_SCOPE_VIOLATION", "gate.human_implementation_repair"),
                repairEdge(EdgeCondition.unconditional(), "context.implement_claude", true));
    }

    private EdgeDefinition violationEdge(EdgeCondition condition, String target) {
        return new EdgeDefinition("implement.artifact_validation", "CHANGE_SCOPE_VIOLATION", target, condition);
    }

    private EdgeDefinition repairEdge(EdgeCondition condition, String target, boolean loop) {
        return new EdgeDefinition("gate.human_implementation_repair", "revise", target, condition,
                loop ? TransitionMetadata.loop("Human authorized repair") : TransitionMetadata.normal());
    }
}
