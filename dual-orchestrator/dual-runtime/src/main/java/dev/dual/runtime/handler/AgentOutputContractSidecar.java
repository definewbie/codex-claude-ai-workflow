package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.util.Optional;

public final class AgentOutputContractSidecar {
    private AgentOutputContractSidecar() {
    }

    public static Path pathFor(Path outputPath) {
        var fileName = outputPath.getFileName().toString();
        var sidecarName = fileName.endsWith(".md")
                ? fileName.substring(0, fileName.length() - 3) + ".contract.json"
                : fileName + ".contract.json";
        return outputPath.resolveSibling(sidecarName);
    }

    public static Optional<AgentOutputContractDocument> read(Path outputPath, ObjectMapper objectMapper) {
        if (outputPath == null) {
            return Optional.empty();
        }
        return AgentOutputContractDocument.read(pathFor(outputPath), objectMapper);
    }
}
