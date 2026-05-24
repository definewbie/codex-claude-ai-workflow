package dev.dual.cli;

import java.time.Instant;
import java.util.List;

record RunMetadata(
        int schemaVersion,
        String runId,
        String workstreamId,
        String nodeId,
        String agent,
        String session,
        String promptTemplate,
        String workdir,
        String promptPath,
        String outputPath,
        String stderrPath,
        String promptMetadataPath,
        String outputMetadataPath,
        String stderrMetadataPath,
        List<String> command,
        Instant startedAt,
        Instant finishedAt,
        long durationMs,
        Integer exitCode,
        String status,
        String failureReason,
        Boolean recoverable
) {
}
