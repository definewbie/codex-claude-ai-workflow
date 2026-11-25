package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;

public final class AgentOutputContractSidecar {
    private AgentOutputContractSidecar() {
    }

    public static Path pathFor(Path outputPath) {
        var fileName = outputPath.getFileName().toString();
        var baseName = fileName.endsWith(".md")
                ? fileName.substring(0, fileName.length() - 3)
                : fileName.endsWith(".json")
                ? fileName.substring(0, fileName.length() - 5)
                : fileName;
        var sidecarName = baseName + ".contract.json";
        return outputPath.resolveSibling(sidecarName);
    }

    public static Optional<AgentOutputContractDocument> read(Path outputPath, ObjectMapper objectMapper) {
        if (outputPath == null) {
            return Optional.empty();
        }
        return AgentOutputContractDocument.read(pathFor(outputPath), objectMapper);
    }

    public static String sha256(Path sidecarPath) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(sidecarPath)));
        } catch (Exception e) {
            throw new IllegalStateException("failed to hash agent output sidecar: " + sidecarPath, e);
        }
    }
}
