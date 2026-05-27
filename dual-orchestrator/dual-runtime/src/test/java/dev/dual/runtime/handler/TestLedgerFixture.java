package dev.dual.runtime.handler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

class TestLedgerFixture {
    Path workspaceRoot;
    String workstreamId;
    Path wsDir;

    TestLedgerFixture(Path workspaceRoot, String workstreamId) {
        this.workspaceRoot = workspaceRoot;
        this.workstreamId = workstreamId;
    }

    void init() throws IOException {
        wsDir = workspaceRoot.resolve(".agent").resolve("workstreams").resolve(workstreamId);
        Files.createDirectories(wsDir);
    }

    Path writeRequirement(String text) throws IOException {
        var path = wsDir.resolve("requirement.md");
        Files.writeString(path, text, StandardCharsets.UTF_8);
        return path;
    }

    Path writeAgentOutput(String name, String content) throws IOException {
        var path = wsDir.resolve("runs").resolve("run-001").resolve("outputs").resolve(name);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
        return path;
    }

    Path writeContextPackage(String name, String content) throws IOException {
        var path = wsDir.resolve("context").resolve(name);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
        return path;
    }

    Path writeHandoff(String markdown) throws IOException {
        var path = wsDir.resolve("runs").resolve("run-010").resolve("outputs").resolve("stage-claude-handoff.md");
        Files.createDirectories(path.getParent());
        Files.writeString(path, markdown, StandardCharsets.UTF_8);
        return path;
    }

    Path writeGitEvidence(String statusShort, String diffStat, String diffPatch) throws IOException {
        var evidenceDir = wsDir.resolve("artifacts").resolve("git-evidence");
        Files.createDirectories(evidenceDir);
        if (statusShort != null) {
            Files.writeString(evidenceDir.resolve("git-status-short.txt"), statusShort, StandardCharsets.UTF_8);
        }
        if (diffStat != null) {
            Files.writeString(evidenceDir.resolve("diff.stat"), diffStat, StandardCharsets.UTF_8);
        }
        if (diffPatch != null) {
            Files.writeString(evidenceDir.resolve("diff.patch"), diffPatch, StandardCharsets.UTF_8);
        }
        Files.writeString(evidenceDir.resolve("git-evidence.json"), "{\"available\":true}", StandardCharsets.UTF_8);
        var changedFiles = new StringBuilder();
        if (statusShort != null) {
            for (var line : statusShort.split("\n")) {
                var parts = line.trim().split("\\s+", 2);
                changedFiles.append(parts.length > 1 ? parts[1] : line.trim()).append("\n");
            }
        }
        Files.writeString(evidenceDir.resolve("changed-files.txt"), changedFiles.toString(), StandardCharsets.UTF_8);
        return evidenceDir;
    }
}
