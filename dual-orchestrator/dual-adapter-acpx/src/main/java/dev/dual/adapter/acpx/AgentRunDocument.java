package dev.dual.adapter.acpx;

import java.time.Instant;
import java.util.List;
import java.util.Map;

record AgentRunDocument(
        int schemaVersion,
        String runId,
        String workstreamId,
        String nodeId,
        String agent,
        String session,
        String sessionScope,
        String sessionSegmentKey,
        String sessionScopeReason,
        String stage,
        String semanticIteration,
        String promptTemplate,
        String workdir,
        String promptPath,
        String promptManifestPath,
        String outputPath,
        String transcriptPath,
        String stderrPath,
        String promptMetadataPath,
        String outputMetadataPath,
        String transcriptMetadataPath,
        String stderrMetadataPath,
        List<String> command,
        Instant startedAt,
        Instant finishedAt,
        long durationMs,
        Integer exitCode,
        String status,
        String failureReason,
        Boolean recoverable,
        AcpxUsage usage,
        AcpxToolUsage toolUsage,
        String contextGovernanceActualReportPath,
        Map<String, Object> processResult,
        List<Map<String, Object>> adapterSignals,
        Map<String, Object> terminalControlResult,
        List<Map<String, Object>> artifactContractResults,
        Map<String, Object> executionOutcome
) {
}
