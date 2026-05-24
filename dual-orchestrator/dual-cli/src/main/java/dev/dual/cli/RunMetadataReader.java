package dev.dual.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.dual.ledger.file.WorkstreamPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

final class RunMetadataReader {
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    List<RunMetadata> list(WorkstreamPaths paths) throws IOException {
        if (!Files.exists(paths.runsDir())) {
            return List.of();
        }
        try (var stream = Files.list(paths.runsDir())) {
            return stream
                    .map(path -> path.resolve("run.json"))
                    .filter(Files::exists)
                    .map(this::readUnchecked)
                    .sorted(Comparator.comparing(RunMetadata::runId))
                    .toList();
        }
    }

    RunMetadata read(WorkstreamPaths paths, String runId) throws IOException {
        var path = paths.runsDir().resolve(runId).resolve("run.json");
        if (!Files.exists(path)) {
            throw new IOException("run metadata not found: " + path);
        }
        return objectMapper.readValue(path.toFile(), RunMetadata.class);
    }

    private RunMetadata readUnchecked(Path path) {
        try {
            return objectMapper.readValue(path.toFile(), RunMetadata.class);
        } catch (IOException e) {
            throw new IllegalStateException("failed to read run metadata: " + path, e);
        }
    }
}
