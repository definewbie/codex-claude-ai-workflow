package dev.dual.adapter.git;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

public final class GitWorkspaceService {
    private static final String LEDGER_EXCLUDE = ".agent/";
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
        if (Files.exists(excludePath)) {
            var lines = Files.readAllLines(excludePath);
            if (lines.stream().map(String::trim).anyMatch(LEDGER_EXCLUDE::equals)) {
                return;
            }
        }
        Files.write(
                excludePath,
                List.of("", "# dual-orchestrator run ledger", LEDGER_EXCLUDE),
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
        );
    }
}
