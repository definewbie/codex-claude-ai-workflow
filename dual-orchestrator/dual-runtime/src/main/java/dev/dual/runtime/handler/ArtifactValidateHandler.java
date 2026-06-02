package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.dual.core.workflow.NodeResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public final class ArtifactValidateHandler implements TaskHandler {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    @Override
    public String type() {
        return "artifact.validate";
    }

    @Override
    public NodeResult execute(TaskContext context) throws Exception {
        var evidenceDir = context.workspaceRoot()
                .resolve(".agent")
                .resolve("workstreams")
                .resolve(context.workstreamId())
                .resolve("artifacts")
                .resolve("git-evidence");
        Files.createDirectories(evidenceDir);

        var evidence = collectEvidence(context.workspaceRoot(), evidenceDir);
        writeEvidenceJson(evidenceDir.resolve("git-evidence.json"), evidence);

        var outputRefs = new ArrayList<String>();
        for (String fileName : List.of(
                "git-evidence.json",
                "base-commit.txt",
                "branch.txt",
                "git-root.txt",
                "git-status-short.txt",
                "diff.stat",
                "diff.patch",
                "changed-files.txt"
        )) {
            var path = evidenceDir.resolve(fileName);
            if (Files.exists(path)) {
                outputRefs.add(toWorkspaceRef(context.workspaceRoot(), path));
            }
        }

        var result = new LinkedHashMap<String, Object>();
        result.put("validation", evidence.available() ? "git_evidence_collected" : "git_unavailable");
        result.put("mode", "git-evidence");
        result.put("git_available", evidence.available());
        result.put("has_changes", evidence.hasChanges());
        result.put("changed_file_count", evidence.changedFileCount());
        result.put("evidence_path", toWorkspaceRef(
                context.workspaceRoot(),
                evidenceDir.resolve("git-evidence.json")
        ));
        result.put("error", evidence.error());

        return NodeResult.completed("completed", outputRefs, result);
    }

    private static GitEvidence collectEvidence(Path workdir, Path evidenceDir) throws IOException, InterruptedException {
        var generatedAt = Instant.now().toString();
        var gitRoot = git(workdir, "rev-parse", "--show-toplevel");
        if (gitRoot.exitCode() != 0) {
            return new GitEvidence(
                    "1",
                    false,
                    workdir.toAbsolutePath().normalize().toString(),
                    null,
                    null,
                    null,
                    false,
                    0,
                    generatedAt,
                    "git repository unavailable: " + gitRoot.output().trim()
            );
        }

        var branch = git(workdir, "branch", "--show-current").output().trim();
        var baseCommit = git(workdir, "rev-parse", "HEAD").output().trim();
        var statusShort = git(workdir, "status", "--short", "--", ".", ":!.agent").output();
        var diffStat = buildDiffStat(workdir, statusShort);
        var diffPatch = buildDiffPatch(workdir, statusShort);
        var changedFiles = changedFilesFromStatus(statusShort);

        write(evidenceDir.resolve("git-root.txt"), gitRoot.output().trim() + "\n");
        write(evidenceDir.resolve("branch.txt"), branch + "\n");
        write(evidenceDir.resolve("base-commit.txt"), baseCommit + "\n");
        write(evidenceDir.resolve("git-status-short.txt"), statusShort);
        write(evidenceDir.resolve("diff.stat"), diffStat);
        write(evidenceDir.resolve("diff.patch"), diffPatch);
        var changedFilesText = String.join("\n", changedFiles) + (changedFiles.isEmpty() ? "" : "\n");
        write(evidenceDir.resolve("changed-files.txt"), changedFilesText);

        return new GitEvidence(
                "1",
                true,
                workdir.toAbsolutePath().normalize().toString(),
                gitRoot.output().trim(),
                branch,
                baseCommit,
                !statusShort.isBlank(),
                changedFiles.size(),
                generatedAt,
                null
        );
    }

    private static List<String> changedFilesFromStatus(String statusShort) {
        var changedFiles = new ArrayList<String>();
        for (String line : statusShort.split("\\R")) {
            if (line.isBlank()) {
                continue;
            }
            if (line.length() <= 3) {
                changedFiles.add(line.trim());
                continue;
            }
            changedFiles.add(line.substring(3).trim());
        }
        return changedFiles;
    }

    private static String buildDiffStat(Path workdir, String statusShort) throws IOException, InterruptedException {
        var builder = new StringBuilder();
        builder.append(git(workdir, "diff", "--stat", "--", ".", ":!.agent").output());
        for (String file : untrackedFilesFromStatus(statusShort)) {
            var result = git(workdir, "diff", "--no-index", "--stat", "--", "/dev/null", file);
            if (!result.output().isBlank()) {
                builder.append(result.output());
            }
        }
        return builder.toString();
    }

    private static String buildDiffPatch(Path workdir, String statusShort) throws IOException, InterruptedException {
        var builder = new StringBuilder();
        builder.append(git(workdir, "diff", "--", ".", ":!.agent").output());
        for (String file : untrackedFilesFromStatus(statusShort)) {
            var result = git(workdir, "diff", "--no-index", "--", "/dev/null", file);
            if (!result.output().isBlank()) {
                builder.append(result.output());
            }
        }
        return builder.toString();
    }

    private static List<String> untrackedFilesFromStatus(String statusShort) {
        var files = new ArrayList<String>();
        for (String line : statusShort.split("\\R")) {
            if (line.startsWith("?? ") && line.length() > 3) {
                var file = line.substring(3).trim();
                if (!file.startsWith(".agent/")) {
                    files.add(file);
                }
            }
        }
        return files;
    }

    private static void write(Path path, String content) throws IOException {
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    private static void writeEvidenceJson(Path path, GitEvidence evidence) throws IOException {
        var json = new LinkedHashMap<String, Object>();
        json.put("schemaVersion", evidence.schemaVersion());
        json.put("available", evidence.available());
        json.put("workdir", evidence.workdir());
        json.put("gitRoot", evidence.gitRoot());
        json.put("branch", evidence.branch());
        json.put("baseCommit", evidence.baseCommit());
        json.put("hasChanges", evidence.hasChanges());
        json.put("changedFileCount", evidence.changedFileCount());
        json.put("generatedAt", evidence.generatedAt());
        json.put("error", evidence.error());
        OBJECT_MAPPER.writeValue(path.toFile(), json);
    }

    private static String toWorkspaceRef(Path workspaceRoot, Path path) {
        return workspaceRoot.toAbsolutePath().normalize().relativize(path.toAbsolutePath().normalize()).toString();
    }

    private static CommandResult git(Path workdir, String... args) throws IOException, InterruptedException {
        var command = new ArrayList<String>();
        command.add("git");
        command.addAll(List.of(args));
        var process = new ProcessBuilder(command)
                .directory(workdir.toFile())
                .redirectErrorStream(true)
                .start();
        var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        var exitCode = process.waitFor();
        return new CommandResult(exitCode, output);
    }

    private record CommandResult(int exitCode, String output) {
    }

    private record GitEvidence(
            String schemaVersion,
            boolean available,
            String workdir,
            String gitRoot,
            String branch,
            String baseCommit,
            boolean hasChanges,
            int changedFileCount,
            String generatedAt,
            String error
    ) {
    }
}
