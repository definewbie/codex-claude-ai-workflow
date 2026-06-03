package dev.dual.cli;

import dev.dual.core.id.DualId;
import dev.dual.ledger.file.WorkstreamPaths;

import java.nio.file.Path;

final class CalibrationCandidateFiles {
    private CalibrationCandidateFiles() {}

    static Path candidatesDir(Path workdir, String workstreamId) {
        var paths = new WorkstreamPaths(workdir.toAbsolutePath().normalize(), new DualId(workstreamId));
        return paths.workstreamDir().resolve("calibration").resolve("candidates");
    }

    static Path candidateJson(Path workdir, String workstreamId, String candidateId) {
        var safeId = normalizeCandidateId(candidateId);
        return candidatesDir(workdir, workstreamId).resolve(safeId + ".json").normalize();
    }

    static Path candidateMarkdown(Path jsonPath) {
        var fileName = jsonPath.getFileName().toString();
        return jsonPath.resolveSibling(fileName.substring(0, fileName.length() - ".json".length()) + ".md");
    }

    static String normalizeCandidateId(String candidateId) {
        if (candidateId == null || candidateId.isBlank()) {
            throw new IllegalArgumentException("candidate id is required");
        }
        var value = candidateId.strip();
        if (value.endsWith(".json")) {
            value = value.substring(0, value.length() - ".json".length());
        }
        if (value.contains("/") || value.contains("\\") || value.contains("..")) {
            throw new IllegalArgumentException("candidate id must be a file name, not a path: " + candidateId);
        }
        return value;
    }
}
