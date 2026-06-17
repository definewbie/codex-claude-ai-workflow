package dev.dual.adapter.git;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitPatchMergeServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void appliesTrackedWorktreeDiffToCleanTargetRepo() throws Exception {
        var target = createRepo();
        var source = tempDir.resolve("source-worktree");
        git(target, "worktree", "add", source.toString(), "-b", "ai/test");
        Files.writeString(source.resolve("README.md"), "changed\n", StandardCharsets.UTF_8);

        var service = new GitPatchMergeService();
        var preview = service.preview(source, target);
        var apply = service.apply(target, preview.patch());

        assertTrue(apply.succeeded(), apply.stderr());
        assertEquals("changed\n", Files.readString(target.resolve("README.md"), StandardCharsets.UTF_8));
        assertTrue(git(target, "diff", "--cached", "--name-only").contains("README.md"));
    }

    @Test
    void excludesLedgerFilesFromPatch() throws Exception {
        var target = createRepo();
        var source = tempDir.resolve("source-worktree");
        git(target, "worktree", "add", source.toString(), "-b", "ai/test");
        Files.writeString(source.resolve("README.md"), "changed\n", StandardCharsets.UTF_8);
        Files.createDirectories(source.resolve(".agent/workstreams/ws-test"));
        Files.writeString(source.resolve(".agent/workstreams/ws-test/local.log"), "local\n", StandardCharsets.UTF_8);

        var preview = new GitPatchMergeService().preview(source, target);

        assertEquals(java.util.List.of("README.md"), preview.changedFiles());
        assertTrue(preview.excludedFiles().stream().anyMatch(path -> path.startsWith(".agent/")));
    }

    @Test
    void refusesDirtyTargetRepoBeforeApply() throws Exception {
        var target = createRepo();
        var source = tempDir.resolve("source-worktree");
        git(target, "worktree", "add", source.toString(), "-b", "ai/test");
        Files.writeString(source.resolve("README.md"), "changed in source\n", StandardCharsets.UTF_8);
        Files.writeString(target.resolve("README.md"), "dirty target\n", StandardCharsets.UTF_8);

        var error = assertThrows(java.io.IOException.class, () ->
                new GitPatchMergeService().preview(source, target));

        assertTrue(error.getMessage().contains("target repo is not clean"));
        assertEquals("dirty target\n", Files.readString(target.resolve("README.md"), StandardCharsets.UTF_8));
    }

    @Test
    void reportsUnsupportedUntrackedBusinessFiles() throws Exception {
        var target = createRepo();
        var source = tempDir.resolve("source-worktree");
        git(target, "worktree", "add", source.toString(), "-b", "ai/test");
        Files.writeString(source.resolve("new-file.txt"), "new\n", StandardCharsets.UTF_8);

        var error = assertThrows(java.io.IOException.class, () ->
                new GitPatchMergeService().preview(source, target));

        assertTrue(error.getMessage().contains("untracked business files"));
    }

    private Path createRepo() throws Exception {
        var repo = tempDir.resolve("repo");
        Files.createDirectories(repo);
        git(repo, "init");
        git(repo, "config", "user.email", "dual@example.test");
        git(repo, "config", "user.name", "Dual Test");
        Files.writeString(repo.resolve("README.md"), "base\n", StandardCharsets.UTF_8);
        git(repo, "add", "README.md");
        git(repo, "commit", "-m", "init");
        return repo;
    }

    private String git(Path workdir, String... args) throws Exception {
        var command = new java.util.ArrayList<String>();
        command.add("git");
        command.addAll(java.util.List.of(args));
        var process = new ProcessBuilder(command)
                .directory(workdir.toFile())
                .redirectErrorStream(true)
                .start();
        var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        var exit = process.waitFor();
        if (exit != 0) {
            throw new AssertionError("git failed: git " + String.join(" ", args) + "\n" + output);
        }
        return output;
    }
}
