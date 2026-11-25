package dev.dual.runtime;

import dev.dual.core.workflow.WorkflowStatus;

public record AgentWorkerResult(
        String workstreamId,
        String agentTaskId,
        String nodeDefinitionId,
        WorkflowStatus status,
        boolean taskExecuted,
        String message
) {
}
