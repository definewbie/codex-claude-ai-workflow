package dev.dual.runtime.lifecycle;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.dual.core.workflow.EdgeDefinition;
import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.WorkflowDefinition;
import dev.dual.ledger.file.EdgeConditionModule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class WorkflowDefinitionLoader {
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new EdgeConditionModule())
            .enable(SerializationFeature.INDENT_OUTPUT);

    public WorkflowDefinition loadOrDefault(Path workflowPath) throws IOException {
        if (workflowPath == null) {
            return new LifecycleWorkflowDefinitionFactory().thinMainline();
        }
        return load(workflowPath);
    }

    public WorkflowDefinition load(Path workflowPath) throws IOException {
        if (!Files.exists(workflowPath)) {
            throw new IOException("workflow file not found: " + workflowPath);
        }
        try {
            var source = objectMapper.readValue(workflowPath.toFile(), WorkflowDefinitionSource.class);
            return new WorkflowDefinition(
                    source.workflowId(),
                    source.version(),
                    source.entryNodeId(),
                    applyDefaults(source.nodes(), source.defaults()),
                    source.edges()
            );
        } catch (RuntimeException e) {
            throw new IOException("invalid workflow definition: " + workflowPath + ": " + e.getMessage(), e);
        }
    }

    private static List<NodeDefinition> applyDefaults(List<NodeDefinition> nodes, Map<String, Object> defaults) {
        if (nodes == null || nodes.isEmpty() || defaults == null || defaults.isEmpty()) {
            return nodes == null ? List.of() : nodes;
        }
        return nodes.stream()
                .map(node -> applyNodeDefaults(node, defaults))
                .toList();
    }

    private static NodeDefinition applyNodeDefaults(NodeDefinition node, Map<String, Object> defaults) {
        var config = new LinkedHashMap<>(node.config());
        if ("acpx.agent_run".equals(node.handlerType())) {
            applySessionDefault(config, defaults);
            applyRetryDefaults(config, objectMap(defaults.get("retry")));
        }
        if ("delivery.create_pr".equals(node.handlerType())) {
            applyObjectDefaults(config, objectMap(defaults.get("delivery")));
        }
        if ("review.decision".equals(node.handlerType()) || "human.review".equals(node.handlerType())) {
            applyObjectDefaults(config, objectMap(defaults.get("revision")));
        }
        return new NodeDefinition(node.id(), node.kind(), node.handlerType(), config);
    }

    private static void applyRetryDefaults(LinkedHashMap<String, Object> config, Map<String, Object> retryDefaults) {
        for (var entry : retryDefaults.entrySet()) {
            config.putIfAbsent("retry." + entry.getKey(), entry.getValue());
        }
    }

    private static void applySessionDefault(LinkedHashMap<String, Object> config, Map<String, Object> defaults) {
        if (config.containsKey("session")) {
            return;
        }
        var agent = text(config.get("agent"));
        if (agent.isBlank()) {
            return;
        }
        var sessions = objectMap(defaults.get("sessions"));
        var session = sessions.get(agent);
        if (session != null && !String.valueOf(session).isBlank()) {
            config.put("session", session);
        }
    }

    private static void applyObjectDefaults(LinkedHashMap<String, Object> config, Map<String, Object> defaults) {
        for (var entry : defaults.entrySet()) {
            config.putIfAbsent(entry.getKey(), entry.getValue());
        }
    }

    private static Map<String, Object> objectMap(Object value) {
        if (!(value instanceof Map<?, ?> raw)) {
            return Map.of();
        }
        var copy = new LinkedHashMap<String, Object>();
        for (var entry : raw.entrySet()) {
            if (entry.getKey() != null) {
                copy.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return copy;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private record WorkflowDefinitionSource(
            String workflowId,
            int version,
            String entryNodeId,
            Map<String, Object> defaults,
            List<NodeDefinition> nodes,
            List<EdgeDefinition> edges
    ) {
    }
}
