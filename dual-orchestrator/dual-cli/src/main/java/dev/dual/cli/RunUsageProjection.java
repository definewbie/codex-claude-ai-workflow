package dev.dual.cli;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dual.ledger.file.WorkstreamPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;

/**
 * CLI 侧的 token 使用量投影。
 *
 * <p>source of truth 是 {@code run.json} 中的真实 adapter usage。若外部 adapter
 * 未能及时写入 usage，CLI 只读 artifact metadata 给出估算值，并用
 * {@code source=artifact_estimate} 标明它不是计费事实。</p>
 */
final class RunUsageProjection {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private final ObjectMapper objectMapper = new ObjectMapper();

    RunMetadata.RunUsage usageOrEstimate(WorkstreamPaths paths, RunMetadata run) {
        if (run.usage() != null) {
            return run.usage();
        }
        try {
            var promptTokens = estimatedTokens(paths, run.promptMetadataPath());
            var transcriptTokens = estimatedTokens(paths, run.transcriptMetadataPath());
            var outputTokens = estimatedTokens(paths, run.outputMetadataPath());
            var visibleOutputTokens = transcriptTokens > 0 ? transcriptTokens : outputTokens;
            if (promptTokens == 0 && visibleOutputTokens == 0) {
                return null;
            }
            return new RunMetadata.RunUsage(
                    promptTokens,
                    visibleOutputTokens,
                    0,
                    0,
                    promptTokens + visibleOutputTokens,
                    null,
                    "",
                    "artifact_estimate",
                    "ARTIFACT_ESTIMATE",
                    "ARTIFACT_ESTIMATE",
                    false,
                    0,
                    null,
                    "",
                    ""
            );
        } catch (IOException ignored) {
            return null;
        }
    }

    private long estimatedTokens(WorkstreamPaths paths, String relativeMetadataPath) throws IOException {
        if (relativeMetadataPath == null || relativeMetadataPath.isBlank()) {
            return 0;
        }
        var metadataPath = paths.workspaceRoot().resolve(relativeMetadataPath).normalize();
        if (!metadataPath.startsWith(paths.workspaceRoot()) || !Files.isRegularFile(metadataPath)) {
            return 0;
        }
        return longValue(objectMapper.readValue(metadataPath.toFile(), MAP_TYPE).get("estimatedTokens"));
    }

    private long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value != null) {
            try {
                return Long.parseLong(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }
}
