package dev.dual.ledger.file;

import dev.dual.core.domain.Workstream;

import java.time.Instant;

public record WorkstreamDocument(
        int schemaVersion,
        String workstreamId,
        String title,
        String status,
        RepositoryDocument repository,
        String primaryWorkspaceId,
        String currentStageId,
        String currentIterationId,
        String requirementPath,
        Instant createdAt,
        Instant updatedAt
) {
    static WorkstreamDocument from(Workstream workstream) {
        return new WorkstreamDocument(
                workstream.schemaVersion(),
                workstream.workstreamId().value(),
                workstream.title(),
                workstream.status().name().toLowerCase(),
                new RepositoryDocument(
                        workstream.repository().originalWorkdir().toString(),
                        workstream.repository().gitRoot().toString()
                ),
                workstream.primaryWorkspaceId().value(),
                workstream.currentStageId(),
                workstream.currentIterationId(),
                workstream.requirementPath(),
                workstream.createdAt(),
                workstream.updatedAt()
        );
    }

    public record RepositoryDocument(String originalWorkdir, String gitRoot) {
    }
}
