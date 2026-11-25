package dev.dual.flow.liteflow;

import dev.dual.core.workflow.EdgeDefinition;

import java.util.List;
import java.util.Map;

public record LiteFlowCompiledDefinition(
        String xml,
        Map<String, String> chainNameByRouteKey,
        Map<String, List<String>> componentIdsByRouteKey,
        Map<String, EdgeDefinition> edgesByComponentId
) {
    public LiteFlowCompiledDefinition {
        if (xml == null || xml.isBlank()) {
            throw new IllegalArgumentException("LiteFlow XML is required");
        }
        chainNameByRouteKey = chainNameByRouteKey == null ? Map.of() : Map.copyOf(chainNameByRouteKey);
        componentIdsByRouteKey = componentIdsByRouteKey == null ? Map.of() : Map.copyOf(componentIdsByRouteKey);
        edgesByComponentId = edgesByComponentId == null ? Map.of() : Map.copyOf(edgesByComponentId);
    }

    public String chainName(String fromNodeDefinitionId, String outcome) {
        return chainNameByRouteKey.get(routeKey(fromNodeDefinitionId, outcome));
    }

    public Map<String, EdgeDefinition> candidateEdges(String fromNodeDefinitionId, String outcome) {
        var routeKey = routeKey(fromNodeDefinitionId, outcome);
        var componentIds = componentIdsByRouteKey.get(routeKey);
        if (componentIds == null || componentIds.isEmpty()) {
            return Map.of();
        }
        var selected = new java.util.LinkedHashMap<String, EdgeDefinition>();
        for (var componentId : componentIds) {
            selected.put(componentId, edgesByComponentId.get(componentId));
        }
        return selected;
    }

    public static String routeKey(String fromNodeDefinitionId, String outcome) {
        return fromNodeDefinitionId + "::" + outcome;
    }
}
