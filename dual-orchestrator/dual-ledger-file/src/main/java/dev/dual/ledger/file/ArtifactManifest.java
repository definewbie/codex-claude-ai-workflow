package dev.dual.ledger.file;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public record ArtifactManifest(String status, List<ArtifactEntry> artifacts) {
    private static final String START = "<<<DUAL_ARTIFACT_MANIFEST>>>";
    private static final String END = "<<<END_DUAL_ARTIFACT_MANIFEST>>>";

    public static ArtifactManifest parse(Path stdoutPath) throws IOException {
        var stdout = Files.readString(stdoutPath);
        var start = stdout.lastIndexOf(START);
        var end = stdout.lastIndexOf(END);
        if (start < 0 || end < 0 || end <= start) {
            throw new IOException("missing DUAL_ARTIFACT_MANIFEST in stdout: " + stdoutPath);
        }
        var json = stdout.substring(start + START.length(), end).trim();
        return new ObjectMapper().readValue(json, ArtifactManifest.class);
    }

    public record ArtifactEntry(String type, String path) {
    }
}
