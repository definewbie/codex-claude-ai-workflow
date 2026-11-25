package dev.dual.ledger.file;

import dev.dual.core.domain.Workspace;

import java.util.List;

public record WorkspaceDocument(
        int schemaVersion,
        String workspaceId,
        String workstreamId,
        String purpose,
        String kind,
        String status,
        String originalWorkdir,
        String effectiveWorkdir,
        String branch,
        String baseBranch,
        String baseCommit,
        List<String> cleanupCommands
) {
    static WorkspaceDocument from(Workspace workspace) {
        return new WorkspaceDocument(
                workspace.schemaVersion(),
                workspace.workspaceId().value(),
                workspace.workstreamId().value(),
                workspace.purpose().name().toLowerCase(),
                workspace.kind().name().toLowerCase(),
                workspace.status().name().toLowerCase(),
                workspace.originalWorkdir().toString(),
                workspace.effectiveWorkdir().toString(),
                workspace.branch(),
                workspace.baseBranch(),
                workspace.baseCommit(),
                workspace.cleanupCommands()
        );
    }
}
