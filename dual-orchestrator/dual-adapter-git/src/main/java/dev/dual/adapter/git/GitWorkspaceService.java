package dev.dual.adapter.git;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

public final class GitWorkspaceService {
    private static final List<String> LOCAL_TOOL_EXCLUDES = List.of(".agent/", ".serena/");
    private final GitCommand gitCommand;

    public GitWorkspaceService() {
        this(new GitCommand());
    }

    GitWorkspaceService(GitCommand gitCommand) {
        this.gitCommand = gitCommand;
    }

    public GitRepositoryInfo inspect(Path workdir) throws IOException, InterruptedException {
        var gitRoot = Path.of(gitCommand.run(workdir, "rev-parse", "--show-toplevel"));
        var branch = gitCommand.run(gitRoot, "branch", "--show-current");
        var commit = gitCommand.run(gitRoot, "rev-parse", "HEAD");
        return new GitRepositoryInfo(gitRoot, branch.isBlank() ? "HEAD" : branch, commit);
    }

    public void createWorktree(Path gitRoot, Path worktreeDir, String branch) throws IOException, InterruptedException {
        if (Files.exists(worktreeDir)) {
            throw new IOException("worktree directory already exists: " + worktreeDir);
        }
        gitCommand.run(gitRoot, "worktree", "add", worktreeDir.toString(), "-b", branch);
        installLedgerExclude(worktreeDir);
    }

    private void installLedgerExclude(Path worktreeDir) throws IOException, InterruptedException {
        var excludePath = Path.of(gitCommand.run(worktreeDir, "rev-parse", "--git-path", "info/exclude"));
        Files.createDirectories(excludePath.getParent());
        var existing = Files.exists(excludePath)
                ? Files.readAllLines(excludePath).stream().map(String::trim).toList()
                : List.<String>of();
        var missing = LOCAL_TOOL_EXCLUDES.stream()
                .filter(exclude -> !existing.contains(exclude))
                .toList();
        if (missing.isEmpty()) {
            return;
        }
        var lines = new java.util.ArrayList<String>();
        if (Files.exists(excludePath)) {
            lines.add("");
        }
        lines.add("# dual-orchestrator local run and tool artifacts");
        lines.addAll(missing);
        Files.write(
                excludePath,
                lines,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
        );
    }
}
