package dev.dual.core.workflow;

import java.util.List;
import java.util.HashSet;
import java.util.Map;

public record WorkflowDefinition(
        String workflowId,
        int version,
        String entryNodeId,
        List<NodeDefinition> nodes,
        List<EdgeDefinition> edges
) {
    public WorkflowDefinition {
        if (workflowId == null || workflowId.isBlank()) {
            throw new IllegalArgumentException("workflow id is required");
        }
        if (version <= 0) {
            throw new IllegalArgumentException("workflow version must be positive");
        }
        nodes = copyNodes(nodes);
        edges = copyEdges(edges);
        var nodeIds = validateUniqueNodeIds(nodes);
        if (!nodeIds.contains(entryNodeId)) {
            throw new IllegalArgumentException("entry node does not exist: " + entryNodeId);
        }
        for (var edge : edges) {
            if (!nodeIds.contains(edge.from())) {
                throw new IllegalArgumentException("edge from node does not exist: " + edge.from());
            }
            if (!nodeIds.contains(edge.to())) {
                throw new IllegalArgumentException("edge to node does not exist: " + edge.to());
            }
        }
        HumanGateActionSemantics.validateVisibility(nodes, edges);
    }

    public NodeDefinition node(String nodeId) {
        var node = nodeOrNull(nodeId, nodes);
        if (node == null) {
            throw new IllegalArgumentException("unknown node: " + nodeId);
        }
        return node;
    }

    public List<EdgeDefinition> edgesFrom(String nodeId) {
        return edges.stream().filter(edge -> edge.from().equals(nodeId)).toList();
    }

    /**
     * Find all edges from a node matching the given outcome, with condition evaluation.
     * Each edge's {@link EdgeCondition} is evaluated against the routing context.
     */
    public List<EdgeDefinition> edgesFrom(String nodeId, String outcome, Map<String, Object> routingContext) {
        return edges.stream()
                .filter(edge -> edge.from().equals(nodeId))
                .filter(edge -> edge.on().equals(outcome))
                .filter(edge -> edge.condition().evaluate(routingContext))
                .toList();
    }

    private static NodeDefinition nodeOrNull(String nodeId, List<NodeDefinition> candidates) {
        if (nodeId == null) {
            return null;
        }
        return candidates.stream().filter(node -> node.id().equals(nodeId)).findFirst().orElse(null);
    }

    private static List<NodeDefinition> copyNodes(List<NodeDefinition> source) {
        if (source == null) {
            return List.of();
        }
        for (var node : source) {
            if (node == null) {
                throw new IllegalArgumentException("nodes contains null element");
            }
        }
        return List.copyOf(source);
    }

    private static List<EdgeDefinition> copyEdges(List<EdgeDefinition> source) {
        if (source == null) {
            return List.of();
        }
        for (var edge : source) {
            if (edge == null) {
                throw new IllegalArgumentException("edges contains null element");
            }
        }
        return List.copyOf(source);
    }

    private static HashSet<String> validateUniqueNodeIds(List<NodeDefinition> nodes) {
        var nodeIds = new HashSet<String>();
        for (var node : nodes) {
            if (!nodeIds.add(node.id())) {
                throw new IllegalArgumentException("duplicate node id: " + node.id());
            }
        }
        return nodeIds;
    }
}
