package dev.dual.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.dual.ledger.file.WorkstreamPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 读取 Agent run metadata 的 CLI 投影入口。
 *
 * <p>{@link #listWithWarnings(WorkstreamPaths)} 面向列表型观测命令，允许部分历史
 * metadata 缺失或损坏并返回 warning；{@link #read(WorkstreamPaths, String)} 面向
 * 显式 inspect 单次 run，保持严格失败。两者语义不要混用。</p>
 */
final class RunMetadataReader {
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    List<RunMetadata> list(WorkstreamPaths paths) throws IOException {
        return listWithWarnings(paths).runs();
    }

    RunMetadataListing listWithWarnings(WorkstreamPaths paths) throws IOException {
        if (!Files.exists(paths.runsDir())) {
            return new RunMetadataListing(List.of(), List.of());
        }
        var runs = new ArrayList<RunMetadata>();
        var warnings = new ArrayList<RunMetadataWarning>();
        try (var stream = Files.list(paths.runsDir())) {
            var runDirs = stream
                    .filter(Files::isDirectory)
                    .sorted()
                    .toList();
            for (var runDir : runDirs) {
                readListEntry(runDir, runs, warnings);
            }
        }
        runs.sort(Comparator.comparing(RunMetadata::runId));
        warnings.sort(Comparator.comparing(RunMetadataWarning::runId));
        return new RunMetadataListing(List.copyOf(runs), List.copyOf(warnings));
    }

    RunMetadata read(WorkstreamPaths paths, String runId) throws IOException {
        var path = paths.runsDir().resolve(runId).resolve("run.json");
        if (!Files.exists(path)) {
            throw new IOException("run metadata not found: " + path);
        }
        return objectMapper.readValue(path.toFile(), RunMetadata.class);
    }

    private void readListEntry(Path runDir, List<RunMetadata> runs, List<RunMetadataWarning> warnings) {
        var runId = runDir.getFileName().toString();
        var path = runDir.resolve("run.json");
        if (!Files.exists(path)) {
            warnings.add(new RunMetadataWarning(runId, path, "missing run.json"));
            return;
        }
        try {
            runs.add(objectMapper.readValue(path.toFile(), RunMetadata.class));
        } catch (IOException | RuntimeException e) {
            // 列表类命令是观测投影：单个历史 run metadata 损坏时降级为 warning，
            // 不覆盖 workflow 的权威终态；显式 inspect --run 仍由 read(...) 严格失败。
            warnings.add(new RunMetadataWarning(runId, path, e.getMessage()));
        }
    }

    record RunMetadataListing(List<RunMetadata> runs, List<RunMetadataWarning> warnings) {
    }

    record RunMetadataWarning(String runId, Path path, String reason) {
    }
}
