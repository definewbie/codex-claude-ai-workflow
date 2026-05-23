package dev.dual.ledger.file;

import java.time.Instant;

public record RunDocument(
        int schemaVersion,
        String runId,
        String workstreamId,
        String workspaceId,
        String stageId,
        String iterationId,
        String actor,
        String transport,
        String status,
        String promptPath,
        String stdoutPath,
        String stderrPath,
        Instant createdAt
) {
    public static RunDocument claudeDesign(
            String runId,
            String workstreamId,
            String workspaceId,
            String promptPath,
            String stdoutPath,
            String stderrPath
    ) {
        return new RunDocument(
                1,
                runId,
                workstreamId,
                workspaceId,
                "stage-design",
                "iter-001",
                "claude",
                "acpx",
                "prepared",
                promptPath,
                stdoutPath,
                stderrPath,
                Instant.now()
        );
    }

    public static RunDocument codexDesignReview(
            String runId,
            String workstreamId,
            String workspaceId,
            String promptPath,
            String stdoutPath,
            String stderrPath
    ) {
        return new RunDocument(
                1,
                runId,
                workstreamId,
                workspaceId,
                "stage-design",
                "iter-001",
                "codex",
                "acpx",
                "prepared",
                promptPath,
                stdoutPath,
                stderrPath,
                Instant.now()
        );
    }
}
