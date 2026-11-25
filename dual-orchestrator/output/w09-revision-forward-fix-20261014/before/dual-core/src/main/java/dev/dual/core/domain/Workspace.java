package dev.dual.core.domain;

import dev.dual.core.id.DualId;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public record Workspace(
        int schemaVersion,
        DualId workspaceId,
        DualId workstreamId,
        WorkspacePurpose purpose,
        WorkspaceKind kind,
        WorkspaceStatus status,
        Path originalWorkdir,
        Path effectiveWorkdir,
        String branch,
        String baseBranch,
        String baseCommit,
        List<String> cleanupCommands
) {
    public Workspace {
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(workstreamId, "workstreamId");
        Objects.requireNonNull(purpose, "purpose");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(originalWorkdir, "originalWorkdir");
        Objects.requireNonNull(effectiveWorkdir, "effectiveWorkdir");
        cleanupCommands = cleanupCommands == null ? List.of() : List.copyOf(cleanupCommands);
    }

    public static Workspace primaryGitWorktree(
            DualId workspaceId,
            DualId workstreamId,
            Path originalWorkdir,
            Path effectiveWorkdir,
            String branch,
            String baseBranch,
            String baseCommit
    ) {
        return new Workspace(
                1,
                workspaceId,
                workstreamId,
                WorkspacePurpose.PRIMARY,
                WorkspaceKind.GIT_WORKTREE,
                WorkspaceStatus.ACTIVE,
                originalWorkdir,
                effectiveWorkdir,
                branch,
                baseBranch,
                baseCommit,
                List.of(
                        "git -C " + originalWorkdir + " worktree remove " + effectiveWorkdir,
                        "git -C " + originalWorkdir + " branch -D " + branch
                )
        );
    }
}
