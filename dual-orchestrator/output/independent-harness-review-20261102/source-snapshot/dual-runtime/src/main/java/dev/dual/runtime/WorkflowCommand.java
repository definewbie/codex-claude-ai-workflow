package dev.dual.runtime;

import java.nio.file.Path;

public record WorkflowCommand(Path workspaceRoot, String workstreamId) {
    public WorkflowCommand {
        if (workspaceRoot == null) {
            throw new IllegalArgumentException("workspaceRoot is required");
        }
        if (workstreamId == null || workstreamId.isBlank()) {
            throw new IllegalArgumentException("workstreamId is required");
        }
    }
}
