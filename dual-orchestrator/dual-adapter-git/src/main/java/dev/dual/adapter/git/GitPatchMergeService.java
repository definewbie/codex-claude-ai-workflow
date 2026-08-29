package dev.dual.adapter.git;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

/** Git plumbing for immutable delivery candidates and target application. */
public final class GitPatchMergeService {
    private static final List<String> INTERNAL_PATHS = List.of(".agent/", ".serena/");
    private static final List<String> DEFAULT_EXCLUDES = List.of("node_modules/", "dist/", "build/", "target/");

    public CandidateCapture capture(Path sourceWorktree) throws IOException, InterruptedException {
        ensureGitRepo(sourceWorktree, "source worktree");
        var baseCommit = requireOutput(git(sourceWorktree, "rev-parse", "HEAD"), "failed to resolve source HEAD");
        var baseTree = requireOutput(git(sourceWorktree, "rev-parse", "HEAD^{tree}"), "failed to resolve source tree");
        var status = git(sourceWorktree, "status", "--porcelain=v1", "-z", "--untracked-files=all");
        requireSuccess(status, "failed to inspect source worktree");
        var classified = classifyStatus(status.stdout());
        if (classified.acceptedFiles().isEmpty()) {
            throw new IOException("no business changes to capture");
        }

        var temporaryIndex = Files.createTempFile("dual-delivery-index-", ".tmp");
        Files.deleteIfExists(temporaryIndex);
        var environment = Map.of("GIT_INDEX_FILE", temporaryIndex.toAbsolutePath().normalize().toString());
        try {
            requireSuccess(git(sourceWorktree, environment, "read-tree", "HEAD"), "failed to initialize delivery index");
            requireSuccess(gitWithStdin(
                    sourceWorktree,
                    environment,
                    nulDelimited(classified.acceptedFiles()),
                    "add", "-A", "--pathspec-from-file=-", "--pathspec-file-nul"
            ), "failed to stage delivery candidate in temporary index");
            var patch = requireRawOutput(
                    git(sourceWorktree, environment, "diff", "--cached", "--binary", "HEAD"),
                    "failed to generate delivery patch"
            );
            var expectedTree = requireOutput(
                    git(sourceWorktree, environment, "write-tree"),
                    "failed to write expected delivery tree"
            );
            return new CandidateCapture(
                    baseCommit,
                    baseTree,
                    classified.acceptedFiles(),
                    classified.excludedFiles(),
                    patch,
                    expectedTree
            );
        } finally {
            Files.deleteIfExists(temporaryIndex);
            Files.deleteIfExists(temporaryIndex.resolveSibling(temporaryIndex.getFileName() + ".lock"));
        }
    }

    public ApplyResult apply(Path targetRepo, String patch) throws IOException, InterruptedException {
        ensureGitRepo(targetRepo, "target repo");
        ensureClean(targetRepo);
        var result = gitWithStdin(
                targetRepo,
                Map.of(),
                patch.getBytes(StandardCharsets.UTF_8),
                "apply", "--index", "--whitespace=nowarn"
        );
        return new ApplyResult(result.exitCode(), result.stdout(), result.stderr());
    }

    public RepositoryState repositoryState(Path repository) throws IOException, InterruptedException {
        ensureGitRepo(repository, "repository");
        var headCommit = requireOutput(git(repository, "rev-parse", "HEAD"), "failed to resolve repository HEAD");
        var headTree = requireOutput(git(repository, "rev-parse", "HEAD^{tree}"), "failed to resolve repository tree");
        var status = git(repository, "status", "--porcelain=v1", "-z", "--untracked-files=all");
        requireSuccess(status, "failed to inspect repository status");
        return new RepositoryState(headCommit, headTree, status.stdout().isEmpty());
    }

    public String stagedTree(Path targetRepo) throws IOException, InterruptedException {
        ensureGitRepo(targetRepo, "target repo");
        return requireOutput(git(targetRepo, "write-tree"), "failed to resolve staged target tree");
    }

    public CommitState commitState(Path targetRepo, String commit) throws IOException, InterruptedException {
        ensureGitRepo(targetRepo, "target repo");
        var identity = requireOutput(git(targetRepo, "rev-parse", commit + "^{commit}"), "delivery commit does not exist");
        var tree = requireOutput(git(targetRepo, "rev-parse", identity + "^{tree}"), "failed to resolve delivery commit tree");
        var parentsLine = requireOutput(
                git(targetRepo, "rev-list", "--parents", "-n", "1", identity),
                "failed to resolve delivery commit parent"
        );
        var tokens = parentsLine.split("\\s+");
        if (tokens.length != 2) {
            throw new IOException("delivery commit must have exactly one parent: " + identity);
        }
        return new CommitState(identity, tokens[1], tree);
    }

    public String diffStat(Path targetRepo) throws IOException, InterruptedException {
        var result = git(targetRepo, "diff", "--cached", "--stat");
        requireSuccess(result, "failed to read target diff stat");
        return result.stdout().stripTrailing();
    }

    private ClassifiedStatus classifyStatus(String porcelain) throws IOException {
        var accepted = new LinkedHashSet<String>();
        var excluded = new LinkedHashSet<String>();
        var fields = porcelain.split("\\u0000", -1);
        for (int index = 0; index < fields.length - 1; index++) {
            var field = fields[index];
            if (field.length() < 4) {
                throw new IOException("invalid NUL-delimited git status entry");
            }
            var status = field.substring(0, 2);
            var paths = new ArrayList<String>();
            paths.add(field.substring(3));
            if (status.indexOf('R') >= 0 || status.indexOf('C') >= 0) {
                if (++index >= fields.length - 1 || fields[index].isBlank()) {
                    throw new IOException("rename/copy status is missing its source path");
                }
                paths.add(fields[index]);
            }
            var excludedCount = paths.stream().filter(this::isExcluded).count();
            var internalCount = paths.stream().filter(this::isInternal).count();
            if (internalCount > 0 && internalCount < paths.size()) {
                throw new IOException("delivery rename/copy crosses the internal ledger boundary: " + String.join(" -> ", paths));
            }
            if (internalCount == paths.size()) {
                continue;
            }
            if (excludedCount > 0 && excludedCount < paths.size()) {
                throw new IOException("delivery rename/copy crosses the excluded boundary: " + String.join(" -> ", paths));
            }
            (excludedCount == paths.size() ? excluded : accepted).addAll(paths);
        }
        return new ClassifiedStatus(
                accepted.stream().sorted(Comparator.naturalOrder()).toList(),
                excluded.stream().sorted(Comparator.naturalOrder()).toList()
        );
    }

    private boolean isExcluded(String path) {
        return DEFAULT_EXCLUDES.stream().anyMatch(path::startsWith) || path.endsWith(".log");
    }

    private boolean isInternal(String path) {
        return INTERNAL_PATHS.stream().anyMatch(path::startsWith);
    }

    private byte[] nulDelimited(List<String> paths) {
        var builder = new StringBuilder();
        paths.forEach(path -> builder.append(path).append('\0'));
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    private void ensureGitRepo(Path workdir, String label) throws IOException, InterruptedException {
        var result = git(workdir, "rev-parse", "--is-inside-work-tree");
        if (result.exitCode() != 0 || !"true".equals(result.stdout().trim())) {
            throw new IOException(label + " is not a git repository: " + workdir);
        }
    }

    private void ensureClean(Path targetRepo) throws IOException, InterruptedException {
        if (!repositoryState(targetRepo).clean()) {
            throw new IOException("target repo is not clean");
        }
    }

    private String requireOutput(CommandOutput output, String message) throws IOException {
        requireSuccess(output, message);
        var value = output.stdout().strip();
        if (value.isBlank()) {
            throw new IOException(message + ": empty output");
        }
        return value;
    }

    private String requireRawOutput(CommandOutput output, String message) throws IOException {
        requireSuccess(output, message);
        if (output.stdout().isBlank()) {
            throw new IOException(message + ": empty output");
        }
        return output.stdout();
    }

    private void requireSuccess(CommandOutput output, String message) throws IOException {
        if (output.exitCode() != 0) {
            throw new IOException(message + ": " + output.combinedOutput());
        }
    }

    private CommandOutput git(Path workdir, String... args) throws IOException, InterruptedException {
        return run(workdir, Map.of(), null, args);
    }

    private CommandOutput git(Path workdir, Map<String, String> environment, String... args)
            throws IOException, InterruptedException {
        return run(workdir, environment, null, args);
    }

    private CommandOutput gitWithStdin(Path workdir, Map<String, String> environment, byte[] stdin, String... args)
            throws IOException, InterruptedException {
        return run(workdir, environment, stdin, args);
    }

    private CommandOutput run(Path workdir, Map<String, String> environment, byte[] stdin, String... args)
            throws IOException, InterruptedException {
        var command = new ArrayList<String>();
        command.add("git");
        command.addAll(List.of(args));
        var builder = new ProcessBuilder(command).directory(workdir.toFile());
        builder.environment().putAll(environment);
        var process = builder.start();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var stdoutFuture = executor.submit(() -> process.getInputStream().readAllBytes());
            var stderrFuture = executor.submit(() -> process.getErrorStream().readAllBytes());
            if (stdin != null) {
                try (var out = process.getOutputStream()) {
                    out.write(stdin);
                }
            } else {
                process.getOutputStream().close();
            }
            var exitCode = process.waitFor();
            return new CommandOutput(
                    exitCode,
                    new String(await(stdoutFuture), StandardCharsets.UTF_8),
                    new String(await(stderrFuture), StandardCharsets.UTF_8)
            );
        }
    }

    private static byte[] await(java.util.concurrent.Future<byte[]> future) throws IOException, InterruptedException {
        try {
            return future.get();
        } catch (ExecutionException failure) {
            if (failure.getCause() instanceof IOException io) throw io;
            throw new IOException("failed to read Git process output", failure.getCause());
        }
    }

    public record CandidateCapture(
            String baseCommit,
            String baseTree,
            List<String> acceptedFiles,
            List<String> excludedFiles,
            String patch,
            String expectedResultTree
    ) {
        public CandidateCapture {
            acceptedFiles = List.copyOf(acceptedFiles);
            excludedFiles = List.copyOf(excludedFiles);
        }
    }

    public record ApplyResult(int exitCode, String stdout, String stderr) {
        public boolean succeeded() {
            return exitCode == 0;
        }
    }

    public record RepositoryState(String headCommit, String headTree, boolean clean) {
    }

    public record CommitState(String commit, String parent, String tree) {
    }

    private record ClassifiedStatus(List<String> acceptedFiles, List<String> excludedFiles) {
    }

    private record CommandOutput(int exitCode, String stdout, String stderr) {
        String combinedOutput() {
            return (stdout + stderr).stripTrailing();
        }
    }
}
