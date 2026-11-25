package dev.dual.core.workflow;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Guards the declared continuation branches; destinations remain workflow-owned. */
public final class SelfTestContinuationSemantics {
    private static final String NODE = "implement.self_test_decision";
    private static final String OUTCOME = "SELF_TEST_INCOMPLETE";
    private static final String KEY = "continuation_kind";
    private static final Set<String> VALUES = Set.of("IMPLEMENTATION_REPAIR", "HUMAN_REVIEW");

    private SelfTestContinuationSemantics() {}

    static void validate(List<NodeDefinition> nodes, List<EdgeDefinition> edges) {
        if (nodes.stream().noneMatch(node -> NODE.equals(node.id()))) return;
        var branches = edges.stream().filter(edge -> NODE.equals(edge.from()) && OUTCOME.equals(edge.on())).toList();
        if (branches.size() != 2) throw invalid();
        var seen = new java.util.HashSet<String>();
        for (var edge : branches) {
            var params = edge.condition().params();
            var value = params.get("value");
            if (!"config_equals".equals(edge.condition().type()) || !KEY.equals(params.get("key"))
                    || !(value instanceof String text) || !VALUES.contains(text) || !seen.add(text)) throw invalid();
            if ("HUMAN_REVIEW".equals(value)
                    && nodes.stream().noneMatch(node -> node.id().equals(edge.to()) && node.kind() == NodeKind.GATE)) {
                throw invalid();
            }
        }
    }

    public static void validateResult(String nodeId, String outcome, Map<String, Object> result) {
        if (NODE.equals(nodeId) && OUTCOME.equals(outcome)
                && (result == null || !(result.get(KEY) instanceof String value) || !VALUES.contains(value))) {
            throw new IllegalArgumentException("self-test incomplete requires a supported continuation_kind");
        }
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("self-test incomplete requires unique implementation and human continuation branches");
    }
}
