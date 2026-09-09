package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.dual.core.contract.TaskContract;
import dev.dual.core.workflow.NodeResult;
import dev.dual.core.workflow.NodeStatus;
import dev.dual.ledger.file.WorkstreamPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ArtifactValidateHandler implements TaskHandler {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT);

    @Override
    public String type() {
        return "artifact.validate";
    }

    @Override
    public NodeResult execute(TaskContext context) throws Exception {
        var qualified = qualifiedContract(context);
        if (qualified.failure() != null) {
            return qualificationFailure(qualified.failure());
        }
        var evidenceDir = context.workspaceRoot()
                .resolve(".agent")
                .resolve("workstreams")
                .resolve(context.workstreamId())
                .resolve("artifacts")
                .resolve("git-evidence");
        Files.createDirectories(evidenceDir);

        var evidence = collectEvidence(context.workspaceRoot(), context.workstreamId(), evidenceDir);
        writeEvidenceJson(evidenceDir.resolve("git-evidence.json"), evidence);
        var evaluation = evaluateChangeScope(evidence, qualified.contract());
        var evaluationPath = evidenceDir.resolve("change-scope-evaluation.json");
        OBJECT_MAPPER.writeValue(evaluationPath.toFile(), evaluation);
        var occurrence = ChangeScopeEvaluationArtifact.publish(
                context.workspaceRoot(),
                context.workstreamId(),
                context.instance().nodeInstanceId(),
                evaluation);

        var outputRefs = new ArrayList<String>();
        outputRefs.addAll(AgentEvidenceIndex.fromRefs(
                        context.workspaceRoot(), context.workstreamId(), context.instance().inputRefs(), OBJECT_MAPPER)
                .validatedSidecarRefs(context.workspaceRoot()));
        for (String fileName : List.of(
                "git-evidence.json",
                "base-commit.txt",
                "head-commit.txt",
                "branch.txt",
                "git-root.txt",
                "git-status-short.txt",
                "git-name-status.txt",
                "diff.stat",
                "diff.patch",
                "changed-files.txt",
                "hygiene-warnings.txt",
                "change-scope-evaluation.json"
        )) {
            var path = evidenceDir.resolve(fileName);
            if (Files.exists(path)) {
                outputRefs.add(toWorkspaceRef(context.workspaceRoot(), path));
            }
        }
        outputRefs.add(occurrence.ref());

        var result = new LinkedHashMap<String, Object>();
        result.put("validation", evidence.available() ? "git_evidence_collected" : "git_unavailable");
        result.put("mode", "git-evidence");
        result.put("git_available", evidence.available());
        result.put("has_changes", evidence.hasChanges());
        result.put("changed_file_count", evidence.changedFileCount());
        result.put("hygiene_warning_count", evidence.hygieneWarningCount());
        result.put("change_scope_status", evaluation.status().name());
        result.put("change_scope_violation_count", evaluation.violations().size());
        result.put("change_scope_missing_declared_count", evaluation.missingDeclared().size());
        result.put("evidence_path", toWorkspaceRef(
                context.workspaceRoot(),
                evidenceDir.resolve("git-evidence.json")
        ));
        result.put("change_scope_evaluation_path", toWorkspaceRef(context.workspaceRoot(), evaluationPath));
        result.put("change_scope_evaluation_ref", occurrence.ref());
        result.put("change_scope_evaluation_sha256", occurrence.sha256());
        result.put("error", evidence.error());

        if (!evaluation.passed()) {
            var outcome = evaluation.status() == ChangeScopeEvaluation.Status.UNAVAILABLE
                    ? "CHANGE_SCOPE_EVIDENCE_UNAVAILABLE"
                    : "CHANGE_SCOPE_VIOLATION";
            var error = evaluation.error() == null
                    ? "actual Git changes do not satisfy TaskContract.scope"
                    : evaluation.error();
            if (evaluation.status() == ChangeScopeEvaluation.Status.FAIL && routesScopeViolation(context)) {
                if (qualified.reference() != null) {
                    result.putAll(TaskContractRevisionContextResolver.captureScopeReworkBaseline(
                            context.workspaceRoot(), context.workstreamId(), qualified.reference(), occurrence));
                }
                result.put("change_scope_disposition", "CHANGE_SCOPE_VIOLATION");
                result.put("change_scope_message", error);
                return new NodeResult(outcome, NodeStatus.COMPLETED, outputRefs, result, null);
            }
            return new NodeResult(outcome, NodeStatus.FAILED, outputRefs, result, error);
        }
        return NodeResult.completed("completed", outputRefs, result);
    }

    private static boolean routesScopeViolation(TaskContext context) {
        var configured = context.definition().config().get("route_scope_violation");
        if (configured == null) {
            return false;
        }
        if (!(configured instanceof Boolean enabled)) {
            throw new IllegalArgumentException("route_scope_violation must be a boolean");
        }
        return enabled;
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
                    "git repository unavailable: " + gitRoot.output().trim(),
                    List.of()
            );
        }

        var branchResult = git(workdir, "branch", "--show-current");
        var headResult = git(workdir, "rev-parse", "HEAD");
        var statusResult = git(workdir, "status", "--short", "--untracked-files=all", "--", ".", ":!.agent");
        if (branchResult.exitCode() != 0 || headResult.exitCode() != 0 || statusResult.exitCode() != 0) {
            return new GitEvidence(
                    "1",
                    false,
                    workdir.toAbsolutePath().normalize().toString(),
                    gitRoot.output().trim(),
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
                    "required Git evidence command failed",
                    List.of()
            );
        }
        var branch = branchResult.output().trim();
        var headCommit = headResult.output().trim();
        var base = readWorkspaceBase(workdir, workstreamId);
        var statusShort = statusResult.output();
        var untrackedResult = git(
                workdir,
                "ls-files",
                "--others",
                "--exclude-standard",
                "-z",
                "--",
                ".",
                ":!.agent"
        );
        if (untrackedResult.exitCode() != 0) {
            return unavailableGitEvidence(
                    workdir,
                    gitRoot.output().trim(),
                    branch,
                    base,
                    headCommit,
                    generatedAt,
                    "git ls-files --others failed: " + untrackedResult.output().trim()
            );
        }
        final List<String> untrackedFiles;
        try {
            untrackedFiles = parseNulPaths(untrackedResult.output());
        } catch (IllegalArgumentException exception) {
            return unavailableGitEvidence(
                    workdir,
                    gitRoot.output().trim(),
                    branch,
                    base,
                    headCommit,
                    generatedAt,
                    "git ls-files --others returned malformed NUL framing: " + exception.getMessage()
            );
        }
        var branchRange = base.baseCommit() == null || base.baseCommit().isBlank()
                ? null
                : base.baseCommit() + "...HEAD";
        if (branchRange != null && !commitExists(workdir, base.baseCommit())) {
            branchRange = null;
        }
        var diffStat = buildDiffStat(workdir, untrackedFiles, branchRange);
        var diffPatch = buildDiffPatch(workdir, untrackedFiles, branchRange);
        var nameStatus = buildNameStatus(workdir, untrackedFiles, base.baseCommit());
        if (nameStatus.exitCode() != 0) {
            return new GitEvidence(
                    "1",
                    false,
                    workdir.toAbsolutePath().normalize().toString(),
                    gitRoot.output().trim(),
                    branch,
                    base.baseBranch(),
                    base.baseCommit(),
                    headCommit,
                    base.source(),
                    branchRange,
                    false,
                    0,
                    0,
                    generatedAt,
                    "git diff --name-status failed: " + nameStatus.output().trim(),
                    List.of()
            );
        }
        final List<ChangeScopeEvaluation.ActualChange> actualChanges;
        try {
            actualChanges = ChangeScopeEvaluator.parseNameStatus(nameStatus.exitCode(), nameStatus.output());
        } catch (IllegalArgumentException exception) {
            return unavailableGitEvidence(
                    workdir,
                    gitRoot.output().trim(),
                    branch,
                    base,
                    headCommit,
                    generatedAt,
                    "git diff --name-status returned malformed NUL framing: " + exception.getMessage()
            );
        }
        var changedFiles = actualChanges.stream()
                .map(ChangeScopeEvaluation.ActualChange::path)
                .distinct()
                .toList();
        var hygieneWarnings = hygieneWarnings(changedFiles);

        write(evidenceDir.resolve("git-root.txt"), gitRoot.output().trim() + "\n");
        write(evidenceDir.resolve("branch.txt"), branch + "\n");
        write(evidenceDir.resolve("base-commit.txt"), textOrEmpty(base.baseCommit()) + "\n");
        write(evidenceDir.resolve("head-commit.txt"), headCommit + "\n");
        write(evidenceDir.resolve("git-status-short.txt"), statusShort);
        write(evidenceDir.resolve("git-name-status.txt"), nameStatus.output());
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
                null,
                actualChanges
        );
    }

    private static GitEvidence unavailableGitEvidence(
            Path workdir,
            String gitRoot,
            String branch,
            WorkspaceBase base,
            String headCommit,
            String generatedAt,
            String error
    ) {
        return new GitEvidence(
                "1",
                false,
                workdir.toAbsolutePath().normalize().toString(),
                gitRoot,
                branch,
                base.baseBranch(),
                base.baseCommit(),
                headCommit,
                base.source(),
                null,
                false,
                0,
                0,
                generatedAt,
                error,
                List.of()
        );
    }

    private static CommandResult buildNameStatus(Path workdir, List<String> untrackedFiles, String baseCommit)
            throws IOException, InterruptedException {
        var comparisonBase = baseCommit != null && !baseCommit.isBlank() && commitExists(workdir, baseCommit)
                ? baseCommit
                : "HEAD";
        var result = git(
                workdir,
                "diff",
                "--name-status",
                "-z",
                "--find-renames",
                comparisonBase,
                "--",
                ".",
                ":!.agent"
        );
        if (result.exitCode() != 0) {
            return result;
        }
        var builder = new StringBuilder(result.output());
        for (var file : untrackedFiles) {
            if (!Files.isDirectory(workdir.resolve(file))) {
                builder.append("A").append('\0').append(file).append('\0');
            }
        }
        return new CommandResult(0, builder.toString());
    }

    private static String buildDiffStat(Path workdir, List<String> untrackedFiles, String branchRange)
            throws IOException, InterruptedException {
        var builder = new StringBuilder();
        if (branchRange != null) {
            builder.append(git(workdir, "diff", "--stat", branchRange, "--", ".", ":!.agent").output());
        }
        builder.append(git(workdir, "diff", "--cached", "--stat", "--", ".", ":!.agent").output());
        builder.append(git(workdir, "diff", "--stat", "--", ".", ":!.agent").output());
        for (String file : untrackedFiles) {
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

    private static String buildDiffPatch(Path workdir, List<String> untrackedFiles, String branchRange)
            throws IOException, InterruptedException {
        var builder = new StringBuilder();
        if (branchRange != null) {
            builder.append(git(workdir, "diff", branchRange, "--", ".", ":!.agent").output());
        }
        builder.append(git(workdir, "diff", "--cached", "--", ".", ":!.agent").output());
        builder.append(git(workdir, "diff", "--", ".", ":!.agent").output());
        for (String file : untrackedFiles) {
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

    private static List<String> parseNulPaths(String framedPaths) {
        if (framedPaths == null || framedPaths.isEmpty()) {
            return List.of();
        }
        var fields = framedPaths.split(String.valueOf('\0'), -1);
        if (fields.length < 2 || !fields[fields.length - 1].isEmpty()) {
            throw new IllegalArgumentException("git path output is not NUL terminated");
        }
        var paths = new ArrayList<String>(fields.length - 1);
        for (var index = 0; index < fields.length - 1; index++) {
            if (fields[index].isEmpty()) {
                throw new IllegalArgumentException("git path output contains an empty path");
            }
            paths.add(fields[index]);
        }
        return List.copyOf(paths);
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
        json.put("actualChanges", evidence.actualChanges());
        OBJECT_MAPPER.writeValue(path.toFile(), json);
    }

    private static ChangeScopeEvaluation evaluateChangeScope(
            GitEvidence evidence,
            TaskContract contract
    ) {
        if (!evidence.available()) {
            return ChangeScopeEvaluation.unavailable("Git evidence is unavailable");
        }
        if (evidence.comparisonRange() == null || evidence.comparisonRange().isBlank()) {
            return ChangeScopeEvaluation.unavailable("Workspace base commit is unavailable");
        }
        if (contract == null) {
            return ChangeScopeEvaluation.unavailable("TaskContract artifact is missing");
        }
        return ChangeScopeEvaluator.evaluate(contract, evidence.actualChanges());
    }

    private static QualifiedContract qualifiedContract(TaskContext context) {
        if (!AgentRunGovernanceContext.requiresApprovedTaskContract(context)) {
            var legacy = TaskContractProjectionVerifier.readLegacyProjection(
                    context.workspaceRoot(), context.workstreamId());
            if (legacy.projection().isPresent()
                    && legacy.projection().orElseThrow().contract() != null) {
                var contract = legacy.projection().orElseThrow().contract();
                var errors = dev.dual.core.contract.TaskContractValidator.validate(contract);
                if (!errors.isEmpty()) {
                    return new QualifiedContract(null, TaskContractProjectionVerifier.Failure.of(
                            "CONTRACT_INVALID", String.join("; ", errors)));
                }
            }
            return legacy.failure() != null
                    ? new QualifiedContract(null, legacy.failure())
                    : new QualifiedContract(
                            legacy.projection().map(TaskContractProjectionVerifier.LegacyProjection::contract).orElse(null),
                            null);
        }
        var resolution = TaskContractProjectionVerifier.resolve(context.workspaceRoot(), context.workstreamId());
        if (resolution.failure() != null) {
            return new QualifiedContract(null, resolution.failure());
        }
        return resolution.qualified()
                .map(value -> new QualifiedContract(value.contract(), null, value))
                .orElseGet(() -> new QualifiedContract(null, TaskContractProjectionVerifier.Failure.of(
                        "APPROVAL_BINDING_MISSING", "change-scope validation requires an approved task contract")));
    }

    private static NodeResult qualificationFailure(TaskContractProjectionVerifier.Failure failure) {
        return new NodeResult(
                TaskContractProjectionVerifier.FAILURE_OUTCOME,
                NodeStatus.FAILED,
                List.of(),
                Map.of(
                        "failure_code", TaskContractProjectionVerifier.FAILURE_OUTCOME,
                        "reason", failure.reason(),
                        "error", failure.message()),
                failure.message());
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

    private record QualifiedContract(TaskContract contract, TaskContractProjectionVerifier.Failure failure,
                                     TaskContractProjectionVerifier.QualifiedTaskContract reference) {
        QualifiedContract(TaskContract contract, TaskContractProjectionVerifier.Failure failure) {
            this(contract, failure, null);
        }
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
            String error,
            List<ChangeScopeEvaluation.ActualChange> actualChanges
    ) {
        GitEvidence {
            actualChanges = List.copyOf(actualChanges == null ? List.of() : actualChanges);
        }
    }
}
