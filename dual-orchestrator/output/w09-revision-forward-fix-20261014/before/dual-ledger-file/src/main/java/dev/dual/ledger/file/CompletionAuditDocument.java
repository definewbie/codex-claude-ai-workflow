package dev.dual.ledger.file;

import java.time.Instant;
import java.util.List;

public record CompletionAuditDocument(
        int schemaVersion,
        String lifecycleState,
        String status,
        String workflowStatus,
        String finalNodeDefinitionId,
        String finalOutcome,
        List<String> gaps,
        Instant completedAt,
        Instant generatedAt
) {
}
