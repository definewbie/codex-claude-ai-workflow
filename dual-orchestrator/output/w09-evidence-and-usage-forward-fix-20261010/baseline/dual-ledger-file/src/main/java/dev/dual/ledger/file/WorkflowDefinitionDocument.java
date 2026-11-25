package dev.dual.ledger.file;

import dev.dual.core.workflow.EdgeDefinition;
import dev.dual.core.workflow.NodeDefinition;

import java.util.List;

public record WorkflowDefinitionDocument(
        int schemaVersion,
        String workflowId,
        int version,
        String entryNodeId,
        List<NodeDefinition> nodes,
        List<EdgeDefinition> edges
) {
    public WorkflowDefinitionDocument {
        if (schemaVersion <= 0) {
            throw new IllegalArgumentException("schemaVersion must be positive");
        }
        if (workflowId == null || workflowId.isBlank()) {
            throw new IllegalArgumentException("workflowId is required");
        }
        if (version <= 0) {
            throw new IllegalArgumentException("version must be positive");
        }
        if (entryNodeId == null || entryNodeId.isBlank()) {
            throw new IllegalArgumentException("entryNodeId is required");
        }
        nodes = List.copyOf(nodes == null ? List.of() : nodes);
        edges = List.copyOf(edges == null ? List.of() : edges);
        new dev.dual.core.workflow.WorkflowDefinition(workflowId, version, entryNodeId, nodes, edges);
    }
}
