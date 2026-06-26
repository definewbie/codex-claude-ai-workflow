package dev.dual.cli;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * CLI 侧的 Agent run 读模型。
 *
 * <p>它对应 ledger 中的 {@code runs/<run_id>/run.json}，只用于 trace/runs/inspect
 * 等观测命令。该模型不能参与 workflow 路由；新增观测字段应优先在这里补齐，避免
 * CLI 因 schema 漂移把正常历史 run 误判为不可读。</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record RunMetadata(
        int schemaVersion,
        String runId,
        String workstreamId,
        String nodeId,
        String agent,
        String session,
        String sessionScope,
        String sessionSegmentKey,
        String stage,
        String semanticIteration,
        String sessionCompactionMode,
        Boolean sessionCompactionEnabled,
        String sessionCompactionReason,
        String sessionCompactionSeedKind,
        String sessionCompactionSeedPath,
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
        RunUsage usage,
        Map<String, Object> executionOutcome
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    record RunUsage(
            long inputTokens,
            long outputTokens,
            long cachedReadTokens,
            long cachedWriteTokens,
            long totalTokens,
            BigDecimal costAmount,
            String costCurrency,
            String source,
            String measurementType,
            String attribution,
            Boolean cumulative,
            long contextUsedTokens,
            BigDecimal sessionCostAmount,
            String sessionCostCurrency
    ) {
    }
}
