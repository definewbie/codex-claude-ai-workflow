package dev.dual.adapter.git;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitWorkspaceServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void createWorktreeInstallsLocalExcludeForLocalToolArtifacts() throws Exception {
        var repo = tempDir.resolve("repo");
        var worktree = tempDir.resolve("repo-worktree");
        Files.createDirectories(repo);
        git(repo, "init");
        git(repo, "config", "user.email", "dual@example.test");
        git(repo, "config", "user.name", "Dual Test");
        Files.writeString(repo.resolve("README.md"), "hello\n");
        git(repo, "add", "README.md");
        git(repo, "commit", "-m", "init");

        new GitWorkspaceService().createWorktree(repo, worktree, "ai/test-worktree");
        Files.createDirectories(worktree.resolve(".agent/workstreams/ws-test"));
        Files.writeString(worktree.resolve(".agent/workstreams/ws-test/events.jsonl"), "{}\n");
        Files.createDirectories(worktree.resolve(".serena"));
        Files.writeString(worktree.resolve(".serena/project.yml"), "project_name: test\n");

        var excludePath = Path.of(git(worktree, "rev-parse", "--git-path", "info/exclude"));
        var exclude = Files.readString(excludePath);
        var status = git(worktree, "status", "--short");

        assertTrue(exclude.contains(".agent/"));
        assertTrue(exclude.contains(".serena/"));
        assertFalse(status.contains(".agent"));
        assertFalse(status.contains(".serena"));
    }

    private static String git(Path workdir, String... args) throws Exception {
        var command = new java.util.ArrayList<String>();
        command.add("git");
        command.addAll(java.util.List.of(args));
        var process = new ProcessBuilder(command)
                .directory(workdir.toFile())
                .redirectErrorStream(true)
                .start();
        var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        var exit = process.waitFor();
        if (exit != 0) {
            throw new AssertionError("git command failed: git " + String.join(" ", args) + "\n" + output);
        }
        return output;
    }
}
