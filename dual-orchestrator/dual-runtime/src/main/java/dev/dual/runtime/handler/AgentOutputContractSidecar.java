package dev.dual.runtime.handler;

import java.nio.file.Path;

final class AgentOutputContractSidecar {
    private AgentOutputContractSidecar() {
    }

    static Path pathFor(Path outputPath) {
        var fileName = outputPath.getFileName().toString();
        var sidecarName = fileName.endsWith(".md")
                ? fileName.substring(0, fileName.length() - 3) + ".contract.json"
                : fileName + ".contract.json";
        return outputPath.resolveSibling(sidecarName);
    }
}
