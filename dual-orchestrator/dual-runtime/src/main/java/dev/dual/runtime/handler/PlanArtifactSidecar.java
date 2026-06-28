package dev.dual.runtime.handler;

import java.nio.file.Path;

final class PlanArtifactSidecar {
    private PlanArtifactSidecar() {
    }

    static Path pathFor(Path outputPath) {
        var fileName = outputPath.getFileName().toString();
        var sidecarName = fileName.endsWith(".md")
                ? fileName.substring(0, fileName.length() - 3) + ".plan.json"
                : fileName + ".plan.json";
        return outputPath.resolveSibling(sidecarName);
    }
}
