package dev.dual.runtime.lifecycle;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.dual.core.workflow.EdgeDefinition;
import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.WorkflowDefinition;
import dev.dual.ledger.file.EdgeConditionModule;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class WorkflowDefinitionLoader {
    private static final String FULL_WORKFLOW_RESOURCE = "/dev/dual/runtime/lifecycle/agent-lifecycle.full.json";
    private static final String CONTRACT_WORKFLOW_RESOURCE = "/dev/dual/runtime/lifecycle/agent-lifecycle.contract.json";
    private static final String TERMINAL_DELIVERY_WORKFLOW_RESOURCE =
            "/dev/dual/runtime/lifecycle/agent-lifecycle.terminal-delivery.json";

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new EdgeConditionModule())
            .enable(SerializationFeature.INDENT_OUTPUT);

    public WorkflowDefinition loadOrDefault(Path workflowPath) throws IOException {
        return loadOrDefault(workflowPath, null);
    }

    public WorkflowDefinition loadOrDefault(Path workflowPath, String variant) throws IOException {
        if (workflowPath != null && Files.exists(workflowPath)) {
            return load(workflowPath);
        }
        return loadBundledVariant(variant);
    }

    public WorkflowDefinition load(Path workflowPath) throws IOException {
        if (!Files.exists(workflowPath)) {
            throw new IOException("workflow file not found: " + workflowPath);
        }
        try {
            var source = objectMapper.readValue(workflowPath.toFile(), WorkflowDefinitionSource.class);
            return toWorkflowDefinition(source);
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

    WorkflowDefinition loadBundledFullWorkflow() throws IOException {
        // 内置生命周期定义的权威来源是 classpath JSON，而不是 Java factory。
        return loadBundledWorkflow(FULL_WORKFLOW_RESOURCE);
    }

    WorkflowDefinition loadBundledContractWorkflow() throws IOException {
        // contract 也是正式内置流程，权威来源同样必须是 JSON resource。
        // 这里保留 Java 入口只是为了 variant selection，不再手写 DAG。
        return loadBundledWorkflow(CONTRACT_WORKFLOW_RESOURCE);
    }

    WorkflowDefinition loadBundledTerminalDeliveryWorkflow() throws IOException {
        return loadBundledWorkflow(TERMINAL_DELIVERY_WORKFLOW_RESOURCE);
    }

    WorkflowDefinition loadBundledVariant(String variant) throws IOException {
        String normalized = variant == null ? "" : variant.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case "", "full" -> loadBundledFullWorkflow();
            case "contract" -> loadBundledContractWorkflow();
            case "terminal-delivery" -> loadBundledTerminalDeliveryWorkflow();
            default -> throw new IllegalArgumentException(
                    "unknown workflow variant: " + variant
                            + ". Supported: contract, full, terminal-delivery");
        };
    }

    private WorkflowDefinition loadBundledWorkflow(String resourcePath) throws IOException {
        try (InputStream input = WorkflowDefinitionLoader.class.getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new IOException("bundled workflow resource not found: " + resourcePath);
            }
            var source = objectMapper.readValue(input, WorkflowDefinitionSource.class);
            return toWorkflowDefinition(source);
        } catch (RuntimeException e) {
            throw new IOException("invalid bundled workflow definition: " + resourcePath + ": " + e.getMessage(), e);
        }
    }

    private static WorkflowDefinition toWorkflowDefinition(WorkflowDefinitionSource source) {
        var definition = new WorkflowDefinition(
                source.workflowId(),
                source.version(),
                source.entryNodeId(),
                applyDefaults(source.nodes(), source.defaults()),
                source.edges()
        );
        validateApprovedTaskContractRequirements(definition);
        return definition;
    }

    private static void validateApprovedTaskContractRequirements(WorkflowDefinition definition) {
        var contractGates = definition.nodes().stream()
                .filter(node -> "contract".equals(text(node.config().get("gate_purpose"))))
                .toList();
        if (contractGates.isEmpty()) {
            return;
        }
        if (contractGates.size() != 1) {
            throw new IllegalArgumentException("workflow must declare exactly one contract-purpose gate");
        }
        var contractGate = contractGates.getFirst();
        var approvedEdges = definition.edgesFrom(contractGate.id()).stream()
                .filter(edge -> "approved".equals(edge.on()))
                .toList();
        if (approvedEdges.size() != 1) {
            throw new IllegalArgumentException("contract-purpose gate must declare exactly one approved successor");
        }
        var approvedEdge = approvedEdges.getFirst();
        var negotiation = reachable(definition, definition.entryNodeId(), approvedEdge);
        if (negotiation.contains(approvedEdge.to())) {
            throw new IllegalArgumentException("contract approved successor overlaps the pre-approval negotiation region");
        }
        var contractGatePredecessors = definition.edges().stream()
                .filter(edge -> contractGate.id().equals(edge.to()))
                .map(EdgeDefinition::from)
                .filter(negotiation::contains)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        var postContract = new HashSet<String>();
        var queue = new ArrayDeque<String>();
        queue.add(approvedEdge.to());
        while (!queue.isEmpty()) {
            var nodeId = queue.removeFirst();
            if (negotiation.contains(nodeId)) {
                if (!contractGatePredecessors.contains(nodeId)) {
                    throw new IllegalArgumentException(
                            "post-contract region overlaps the pre-approval negotiation region: " + nodeId);
                }
                continue;
            }
            if (!postContract.add(nodeId)) {
                continue;
            }
            definition.edgesFrom(nodeId).forEach(edge -> queue.addLast(edge.to()));
        }
        for (var node : definition.nodes()) {
            var configured = node.config().get("requires_approved_task_contract");
            if (postContract.contains(node.id())) {
                if (!Boolean.TRUE.equals(configured)) {
                    throw new IllegalArgumentException(
                            "post-contract node must declare requires_approved_task_contract=true: " + node.id());
                }
            } else if (negotiation.contains(node.id()) && configured != null && !Boolean.FALSE.equals(configured)) {
                throw new IllegalArgumentException(
                        "pre-approval negotiation node cannot require an approved task contract: " + node.id());
            }
        }
    }

    private static Set<String> reachable(
            WorkflowDefinition definition,
            String start,
            EdgeDefinition excludedEdge
    ) {
        var reached = new HashSet<String>();
        var queue = new ArrayDeque<String>();
        queue.add(start);
        while (!queue.isEmpty()) {
            var nodeId = queue.removeFirst();
            if (!reached.add(nodeId)) {
                continue;
            }
            definition.edgesFrom(nodeId).stream()
                    .filter(edge -> !edge.equals(excludedEdge))
                    .forEach(edge -> queue.addLast(edge.to()));
        }
        return Set.copyOf(reached);
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
