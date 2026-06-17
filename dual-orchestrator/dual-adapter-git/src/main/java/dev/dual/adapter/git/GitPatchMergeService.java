package dev.dual.adapter.git;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Git delivery merge adapter.
 *
 * <p>这个类只封装 Git 外部活动：检查 target repo、计算 source worktree diff、
 * apply patch。它不判断 workflow 是否完成，也不判断验收是否通过。</p>
 */
public final class GitPatchMergeService {
    private static final List<String> DEFAULT_EXCLUDES = List.of(
            ".agent/",
            ".serena/",
            "node_modules/",
            "dist/",
            "build/",
            "target/"
    );

    public MergePreview preview(Path sourceWorktree, Path targetRepo) throws IOException, InterruptedException {
        ensureGitRepo(sourceWorktree, "source worktree");
        ensureGitRepo(targetRepo, "target repo");
        ensureClean(targetRepo);
        var status = git(sourceWorktree, "status", "--short");
        if (status.exitCode() != 0) {
            throw new IOException("failed to inspect source worktree: " + status.combinedOutput());
        }
        var changed = new ArrayList<String>();
        var excluded = new ArrayList<String>();
        var unsupportedUntracked = new ArrayList<String>();
        for (var line : status.stdout().split("\\R")) {
            var path = statusPath(line);
            if (path.isBlank()) {
                continue;
            }
            if (isExcluded(path)) {
                excluded.add(path);
                continue;
            }
            if (isUntracked(line)) {
                unsupportedUntracked.add(path);
                continue;
            }
            changed.add(path);
        }
        changed.sort(Comparator.naturalOrder());
        excluded.sort(Comparator.naturalOrder());
        unsupportedUntracked.sort(Comparator.naturalOrder());
        if (!unsupportedUntracked.isEmpty()) {
            throw new IOException("delivery merge does not yet support untracked business files: "
                    + String.join(", ", unsupportedUntracked));
        }
        if (changed.isEmpty()) {
            throw new IOException("no business changes to merge");
        }
        return new MergePreview(changed, excluded, buildPatch(sourceWorktree, changed));
    }

    public ApplyResult apply(Path targetRepo, String patch) throws IOException, InterruptedException {
        ensureGitRepo(targetRepo, "target repo");
        ensureClean(targetRepo);
        var result = gitWithStdin(targetRepo, patch, "apply", "--index", "--whitespace=nowarn");
        return new ApplyResult(result.exitCode(), result.stdout(), result.stderr());
    }

    public String diffStat(Path targetRepo) throws IOException, InterruptedException {
        var result = git(targetRepo, "diff", "--cached", "--stat");
        if (result.exitCode() != 0) {
            throw new IOException("failed to read target diff stat: " + result.combinedOutput());
        }
        return result.stdout().stripTrailing();
    }

    private String buildPatch(Path sourceWorktree, List<String> changedFiles) throws IOException, InterruptedException {
        var args = new ArrayList<String>();
        args.add("diff");
        args.add("--binary");
        args.add("HEAD");
        args.add("--");
        args.addAll(changedFiles);
        var result = git(sourceWorktree, args.toArray(String[]::new));
        if (result.exitCode() != 0) {
            throw new IOException("failed to generate delivery patch: " + result.combinedOutput());
        }
        if (result.stdout().isBlank()) {
            throw new IOException("generated delivery patch is empty");
        }
        return result.stdout();
    }

    private void ensureGitRepo(Path workdir, String label) throws IOException, InterruptedException {
        var result = git(workdir, "rev-parse", "--is-inside-work-tree");
        if (result.exitCode() != 0 || !"true".equals(result.stdout().trim())) {
            throw new IOException(label + " is not a git repository: " + workdir);
        }
    }

    private void ensureClean(Path targetRepo) throws IOException, InterruptedException {
        var result = git(targetRepo, "status", "--short");
        if (result.exitCode() != 0) {
            throw new IOException("failed to inspect target repo: " + result.combinedOutput());
        }
        if (!result.stdout().isBlank()) {
            throw new IOException("target repo is not clean:\n" + result.stdout().stripTrailing());
        }
    }

    private boolean isExcluded(String path) {
        return DEFAULT_EXCLUDES.stream().anyMatch(path::startsWith)
                || path.endsWith(".log");
    }

    private boolean isUntracked(String line) {
        return line.length() >= 2 && line.charAt(0) == '?' && line.charAt(1) == '?';
    }

    private String statusPath(String line) {
        if (line.length() < 4) {
            return "";
        }
        var path = line.substring(3).trim();
        var renameIndex = path.indexOf(" -> ");
        if (renameIndex >= 0) {
            return path.substring(renameIndex + 4).trim();
        }
        return path;
    }

    private CommandOutput git(Path workdir, String... args) throws IOException, InterruptedException {
        return run(workdir, null, args);
    }

    private CommandOutput gitWithStdin(Path workdir, String stdin, String... args) throws IOException, InterruptedException {
        return run(workdir, stdin, args);
    }

    private CommandOutput run(Path workdir, String stdin, String... args) throws IOException, InterruptedException {
        var command = new ArrayList<String>();
        command.add("git");
        command.addAll(List.of(args));
        var process = new ProcessBuilder(command)
                .directory(workdir.toFile())
                .start();
        if (stdin != null) {
            try (var out = process.getOutputStream()) {
                out.write(stdin.getBytes(StandardCharsets.UTF_8));
            }
        } else {
            process.getOutputStream().close();
        }
        var stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        var stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        var exit = process.waitFor();
        return new CommandOutput(exit, stdout, stderr);
    }

    public record MergePreview(List<String> changedFiles, List<String> excludedFiles, String patch) {
    }

    public record ApplyResult(int exitCode, String stdout, String stderr) {
        public boolean succeeded() {
            return exitCode == 0;
        }
    }

    private record CommandOutput(int exitCode, String stdout, String stderr) {
        String combinedOutput() {
            return (stdout + stderr).stripTrailing();
        }
    }
}
