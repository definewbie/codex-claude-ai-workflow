package dev.dual.core.flow;

import java.util.List;

public record FlowExecutionSnapshot(
        String workflowInstanceId,
        List<String> activeNodeDefinitionIds
) {
    public FlowExecutionSnapshot {
        if (workflowInstanceId == null || workflowInstanceId.isBlank()) {
            throw new IllegalArgumentException("workflow instance id is required");
        }
        activeNodeDefinitionIds = activeNodeDefinitionIds == null ? List.of() : List.copyOf(activeNodeDefinitionIds);
        for (var nodeId : activeNodeDefinitionIds) {
            if (nodeId == null || nodeId.isBlank()) {
                throw new IllegalArgumentException("active node definition id is required");
            }
        }
    }

    public static FlowExecutionSnapshot singleActiveNode(String workflowInstanceId, String nodeDefinitionId) {
        return new FlowExecutionSnapshot(workflowInstanceId, List.of(nodeDefinitionId));
    }
}
