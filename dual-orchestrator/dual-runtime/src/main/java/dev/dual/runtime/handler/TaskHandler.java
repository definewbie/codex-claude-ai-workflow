package dev.dual.runtime.handler;

import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeInstance;
import dev.dual.core.workflow.NodeResult;

import java.nio.file.Path;

public interface TaskHandler {
    String type();

    NodeResult execute(TaskContext context) throws Exception;

    record TaskContext(
            Path workspaceRoot,
            String workstreamId,
            NodeDefinition definition,
            NodeInstance instance
    ) {
        public TaskContext {
            if (workspaceRoot == null) {
                throw new IllegalArgumentException("workspaceRoot is required");
            }
            if (workstreamId == null || workstreamId.isBlank()) {
                throw new IllegalArgumentException("workstreamId is required");
            }
            if (definition == null) {
                throw new IllegalArgumentException("definition is required");
            }
            if (instance == null) {
                throw new IllegalArgumentException("instance is required");
            }
        }
    }
}
