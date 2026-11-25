package dev.dual.runtime.handler;

import java.nio.file.Path;

final class PlanArtifactSidecar {
    private PlanArtifactSidecar() {
    }

    static Path pathFor(Path outputPath) {
        var fileName = outputPath.getFileName().toString();
        var baseName = fileName.endsWith(".md")
                ? fileName.substring(0, fileName.length() - 3)
                : fileName.endsWith(".json")
                ? fileName.substring(0, fileName.length() - 5)
                : fileName;
        var sidecarName = baseName + ".plan.json";
        return outputPath.resolveSibling(sidecarName);
    }
}
