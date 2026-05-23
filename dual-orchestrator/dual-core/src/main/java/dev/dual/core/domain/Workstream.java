package dev.dual.core.domain;

import dev.dual.core.id.DualId;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;

public record Workstream(
        int schemaVersion,
        DualId workstreamId,
        String title,
        WorkstreamStatus status,
        RepositoryRef repository,
        DualId primaryWorkspaceId,
        String currentStageId,
        String currentIterationId,
        String requirementPath,
        Instant createdAt,
        Instant updatedAt
) {
    public Workstream {
        Objects.requireNonNull(workstreamId, "workstreamId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(primaryWorkspaceId, "primaryWorkspaceId");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static Workstream created(
            DualId workstreamId,
            String title,
            RepositoryRef repository,
            DualId primaryWorkspaceId,
            Instant now
    ) {
        return new Workstream(
                1,
                workstreamId,
                title,
                WorkstreamStatus.CREATED,
                repository,
                primaryWorkspaceId,
                "stage-design",
                "iter-001",
                "requirement.md",
                now,
                now
        );
    }

    public record RepositoryRef(Path originalWorkdir, Path gitRoot) {
        public RepositoryRef {
            Objects.requireNonNull(originalWorkdir, "originalWorkdir");
            Objects.requireNonNull(gitRoot, "gitRoot");
        }
    }
}
