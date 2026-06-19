package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.dual.core.workflow.NodeResult;
import dev.dual.ledger.file.WorkstreamPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

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

        var evidence = collectEvidence(context.workspaceRoot(), context.workstreamId(), evidenceDir);
        writeEvidenceJson(evidenceDir.resolve("git-evidence.json"), evidence);

        var outputRefs = new ArrayList<String>();
        for (String fileName : List.of(
                "git-evidence.json",
                "base-commit.txt",
                "head-commit.txt",
                "branch.txt",
                "git-root.txt",
                "git-status-short.txt",
                "diff.stat",
                "diff.patch",
                "changed-files.txt",
                "hygiene-warnings.txt"
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
        result.put("hygiene_warning_count", evidence.hygieneWarningCount());
        result.put("evidence_path", toWorkspaceRef(
                context.workspaceRoot(),
                evidenceDir.resolve("git-evidence.json")
        ));
        result.put("error", evidence.error());

        return NodeResult.completed("completed", outputRefs, result);
    }

    private static GitEvidence collectEvidence(Path workdir, String workstreamId, Path evidenceDir) throws IOException, InterruptedException {
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
                    null,
                    null,
                    null,
                    null,
                    false,
                    0,
                    0,
                    generatedAt,
                    "git repository unavailable: " + gitRoot.output().trim()
            );
        }

        var branch = git(workdir, "branch", "--show-current").output().trim();
        var headCommit = git(workdir, "rev-parse", "HEAD").output().trim();
        var base = readWorkspaceBase(workdir, workstreamId);
        var statusShort = git(workdir, "status", "--short", "--", ".", ":!.agent").output();
        var branchRange = base.baseCommit() == null || base.baseCommit().isBlank()
                ? null
                : base.baseCommit() + "...HEAD";
        if (branchRange != null && !commitExists(workdir, base.baseCommit())) {
            branchRange = null;
        }
        var diffStat = buildDiffStat(workdir, statusShort, branchRange);
        var diffPatch = buildDiffPatch(workdir, statusShort, branchRange);
        var changedFiles = changedFiles(workdir, statusShort, branchRange);
        var hygieneWarnings = hygieneWarnings(changedFiles);

        write(evidenceDir.resolve("git-root.txt"), gitRoot.output().trim() + "\n");
        write(evidenceDir.resolve("branch.txt"), branch + "\n");
        write(evidenceDir.resolve("base-commit.txt"), textOrEmpty(base.baseCommit()) + "\n");
        write(evidenceDir.resolve("head-commit.txt"), headCommit + "\n");
        write(evidenceDir.resolve("git-status-short.txt"), statusShort);
        write(evidenceDir.resolve("diff.stat"), diffStat);
        write(evidenceDir.resolve("diff.patch"), diffPatch);
        var changedFilesText = String.join("\n", changedFiles) + (changedFiles.isEmpty() ? "" : "\n");
        write(evidenceDir.resolve("changed-files.txt"), changedFilesText);
        if (!hygieneWarnings.isEmpty()) {
            write(evidenceDir.resolve("hygiene-warnings.txt"), String.join("\n", hygieneWarnings) + "\n");
        }

        return new GitEvidence(
                "1",
                true,
                workdir.toAbsolutePath().normalize().toString(),
                gitRoot.output().trim(),
                branch,
                base.baseBranch(),
                base.baseCommit(),
                headCommit,
                base.source(),
                branchRange,
                !statusShort.isBlank() || !changedFiles.isEmpty(),
                changedFiles.size(),
                hygieneWarnings.size(),
                generatedAt,
                null
        );
    }

    private static List<String> changedFiles(Path workdir, String statusShort, String branchRange)
            throws IOException, InterruptedException {
        var changedFiles = new LinkedHashSet<String>();
        if (branchRange != null) {
            addNameOnly(changedFiles, git(workdir, "diff", "--name-only", branchRange, "--", ".", ":!.agent").output());
        }
        addNameOnly(changedFiles, git(workdir, "diff", "--name-only", "--", ".", ":!.agent").output());
        addNameOnly(changedFiles, git(workdir, "diff", "--cached", "--name-only", "--", ".", ":!.agent").output());
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
        changedFiles.removeIf(file -> file.isBlank() || file.startsWith(".agent/"));
        return new ArrayList<>(changedFiles);
    }

    private static void addNameOnly(Set<String> changedFiles, String output) {
        for (String line : output.split("\\R")) {
            if (!line.isBlank()) {
                changedFiles.add(line.trim());
            }
        }
    }

    private static String buildDiffStat(Path workdir, String statusShort, String branchRange) throws IOException, InterruptedException {
        var builder = new StringBuilder();
        if (branchRange != null) {
            builder.append(git(workdir, "diff", "--stat", branchRange, "--", ".", ":!.agent").output());
        }
        builder.append(git(workdir, "diff", "--cached", "--stat", "--", ".", ":!.agent").output());
        builder.append(git(workdir, "diff", "--stat", "--", ".", ":!.agent").output());
        for (String file : untrackedFilesFromStatus(statusShort)) {
            if (Files.isDirectory(workdir.resolve(file))) {
                continue;
            }
            var result = git(workdir, "diff", "--no-index", "--stat", "--", "/dev/null", file);
            if (!result.output().isBlank()) {
                builder.append(result.output());
            }
        }
        return builder.toString();
    }

    private static String buildDiffPatch(Path workdir, String statusShort, String branchRange) throws IOException, InterruptedException {
        var builder = new StringBuilder();
        if (branchRange != null) {
            builder.append(git(workdir, "diff", branchRange, "--", ".", ":!.agent").output());
        }
        builder.append(git(workdir, "diff", "--cached", "--", ".", ":!.agent").output());
        builder.append(git(workdir, "diff", "--", ".", ":!.agent").output());
        for (String file : untrackedFilesFromStatus(statusShort)) {
            if (Files.isDirectory(workdir.resolve(file))) {
                continue;
            }
            var result = git(workdir, "diff", "--no-index", "--", "/dev/null", file);
            if (!result.output().isBlank()) {
                builder.append(result.output());
            }
        }
        return builder.toString();
    }

    private static WorkspaceBase readWorkspaceBase(Path workdir, String workstreamId) throws IOException {
        if (workstreamId == null || workstreamId.isBlank()) {
            return WorkspaceBase.unavailable();
        }
        var paths = new WorkstreamPaths(workdir, new dev.dual.core.id.DualId(workstreamId));
        if (!Files.isRegularFile(paths.workstreamJson())) {
            return WorkspaceBase.unavailable();
        }
        var workstream = OBJECT_MAPPER.readTree(paths.workstreamJson().toFile());
        var workspaceId = workstream.path("primaryWorkspaceId").asText("");
        if (workspaceId.isBlank()) {
            return WorkspaceBase.unavailable();
        }
        var workspaceJson = paths.workstreamDir().resolve("workspaces").resolve(workspaceId + ".json");
        if (!Files.isRegularFile(workspaceJson)) {
            return WorkspaceBase.unavailable();
        }
        var workspace = OBJECT_MAPPER.readTree(workspaceJson.toFile());
        return new WorkspaceBase(
                textOrNull(workspace.path("baseBranch").asText("")),
                textOrNull(workspace.path("baseCommit").asText("")),
                "workspace_metadata"
        );
    }

    private static boolean commitExists(Path workdir, String commit) throws IOException, InterruptedException {
        return git(workdir, "cat-file", "-e", commit + "^{commit}").exitCode() == 0;
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

    private static List<String> hygieneWarnings(List<String> changedFiles) {
        var warnings = new ArrayList<String>();
        for (String file : changedFiles) {
            if (isLikelyGeneratedByproduct(file)) {
                warnings.add(file + " looks like a generated self-test byproduct; remove it unless it is an intentional deliverable.");
            }
        }
        return warnings;
    }

    private static boolean isLikelyGeneratedByproduct(String file) {
        return file.equals("__pycache__/")
                || file.startsWith("__pycache__/")
                || file.contains("/__pycache__/")
                || file.equals(".pytest_cache/")
                || file.startsWith(".pytest_cache/")
                || file.contains("/.pytest_cache/")
                || file.endsWith(".pyc")
                || file.endsWith(".pyo")
                || file.endsWith(".coverage")
                || file.endsWith(".tmp")
                || file.endsWith(".log")
                || file.equals(".DS_Store")
                || file.endsWith("/.DS_Store");
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
        json.put("baseBranch", evidence.baseBranch());
        json.put("baseCommit", evidence.baseCommit());
        json.put("headCommit", evidence.headCommit());
        json.put("baseSource", evidence.baseSource());
        json.put("comparisonRange", evidence.comparisonRange());
        json.put("hasChanges", evidence.hasChanges());
        json.put("changedFileCount", evidence.changedFileCount());
        json.put("hygieneWarningCount", evidence.hygieneWarningCount());
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

    private static String textOrNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String textOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private record WorkspaceBase(String baseBranch, String baseCommit, String source) {
        static WorkspaceBase unavailable() {
            return new WorkspaceBase(null, null, "unavailable");
        }
    }

    private record GitEvidence(
            String schemaVersion,
            boolean available,
            String workdir,
            String gitRoot,
            String branch,
            String baseBranch,
            String baseCommit,
            String headCommit,
            String baseSource,
            String comparisonRange,
            boolean hasChanges,
            int changedFileCount,
            int hygieneWarningCount,
            String generatedAt,
            String error
    ) {
    }
}
