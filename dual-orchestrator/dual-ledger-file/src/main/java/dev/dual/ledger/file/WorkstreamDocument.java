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
        Instant updatedAt,
        java.util.Map<String, String> frozenRuntimeIdentity
) {
    static WorkstreamDocument from(Workstream workstream, java.util.Map<String, String> identity) {
        return new WorkstreamDocument(
                2,
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
                workstream.updatedAt(),
                java.util.Map.copyOf(identity)
        );
    }

    public record RepositoryDocument(String originalWorkdir, String gitRoot) {
    }
}
