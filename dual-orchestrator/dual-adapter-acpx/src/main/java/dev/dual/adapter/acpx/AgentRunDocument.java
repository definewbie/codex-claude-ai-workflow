package dev.dual.adapter.acpx;

import java.time.Instant;
import java.util.List;

record AgentRunDocument(
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
