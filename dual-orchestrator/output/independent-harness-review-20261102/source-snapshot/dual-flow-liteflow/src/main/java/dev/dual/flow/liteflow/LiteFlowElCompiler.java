package dev.dual.flow.liteflow;

import dev.dual.core.workflow.EdgeDefinition;
import dev.dual.core.workflow.WorkflowDefinition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LiteFlowElCompiler {
    public LiteFlowCompiledDefinition compile(WorkflowDefinition definition) {
        if (definition == null) {
            throw new IllegalArgumentException("workflow definition is required");
        }
        var chainNameByRouteKey = new LinkedHashMap<String, String>();
        var componentIdsByRouteKey = new LinkedHashMap<String, List<String>>();
        var edgesByComponentId = new LinkedHashMap<String, EdgeDefinition>();

        var groupedEdges = groupEdges(definition);
        var xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<flow>\n");
        xml.append("    <nodes>\n");
        xml.append("        <node id=\"prepare\" class=\"dev.dual.flow.liteflow.PrepareFlowComponent\"/>\n");
        xml.append("        <node id=\"emit\" class=\"dev.dual.flow.liteflow.EmitFlowComponent\"/>\n");

        var componentOrdinal = 1;
        for (var entry : groupedEdges.entrySet()) {
            var componentIds = new ArrayList<String>();
            for (var edge : entry.getValue()) {
                var componentId = "edge_" + String.format("%04d", componentOrdinal++);
                componentIds.add(componentId);
                edgesByComponentId.put(componentId, edge);
                xml.append("        <node id=\"")
                        .append(componentId)
                        .append("\" class=\"dev.dual.flow.liteflow.RouteCandidateFlowComponent\"/>\n");
            }
            componentIdsByRouteKey.put(entry.getKey(), List.copyOf(componentIds));
        }
        xml.append("    </nodes>\n\n");

        var chainOrdinal = 1;
        for (var entry : componentIdsByRouteKey.entrySet()) {
            var chainName = "route_" + String.format("%04d", chainOrdinal++);
            chainNameByRouteKey.put(entry.getKey(), chainName);
            xml.append("    <chain name=\"").append(chainName).append("\">\n");
            xml.append("        ").append(renderThenExpression(entry.getValue())).append("\n");
            xml.append("    </chain>\n");
        }
        xml.append("</flow>\n");

        return new LiteFlowCompiledDefinition(
                xml.toString(),
                chainNameByRouteKey,
                componentIdsByRouteKey,
                edgesByComponentId
        );
    }

    private static Map<String, List<EdgeDefinition>> groupEdges(WorkflowDefinition definition) {
        var grouped = new LinkedHashMap<String, List<EdgeDefinition>>();
        for (var edge : definition.edges()) {
            var routeKey = LiteFlowCompiledDefinition.routeKey(edge.from(), edge.on());
            grouped.computeIfAbsent(routeKey, ignored -> new ArrayList<>()).add(edge);
        }
        return grouped;
    }

    private static String renderThenExpression(List<String> componentIds) {
        var steps = new ArrayList<String>();
        steps.add("prepare");
        steps.addAll(componentIds);
        steps.add("emit");
        return "THEN(" + String.join(", ", steps) + ");";
    }
}
