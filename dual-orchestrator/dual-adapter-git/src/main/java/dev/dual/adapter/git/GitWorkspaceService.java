package dev.dual.adapter.git;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class GitWorkspaceService {
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
    }
}
