package dev.dual.adapter.acpx;

import java.time.Instant;

record AgentArtifactDocument(
        int schemaVersion,
        String artifactType,
        String runId,
        String workstreamId,
        String nodeId,
        String agent,
        String session,
        String promptTemplate,
        String workdir,
        String artifactPath,
        String contentType,
        long byteSize,
        long lineCount,
        String sha256,
        Instant generatedAt
) {
}
