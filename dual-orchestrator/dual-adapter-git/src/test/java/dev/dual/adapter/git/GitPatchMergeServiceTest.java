package dev.dual.adapter.git;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        var capture = service.capture(source);
        var apply = service.apply(target, capture.patch());

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

        var capture = new GitPatchMergeService().capture(source);

        assertEquals(java.util.List.of("README.md"), capture.acceptedFiles());
        assertTrue(capture.excludedFiles().isEmpty(), "mutable ledger paths are internal, not candidate exclusions");
        assertFalse(capture.patch().contains(".agent/"));
    }

    @Test
    void refusesDirtyTargetRepoBeforeApply() throws Exception {
        var target = createRepo();
        var source = tempDir.resolve("source-worktree");
        git(target, "worktree", "add", source.toString(), "-b", "ai/test");
        Files.writeString(source.resolve("README.md"), "changed in source\n", StandardCharsets.UTF_8);
        Files.writeString(target.resolve("README.md"), "dirty target\n", StandardCharsets.UTF_8);

        var error = assertThrows(java.io.IOException.class, () ->
                new GitPatchMergeService().apply(target, new GitPatchMergeService().capture(source).patch()));

        assertTrue(error.getMessage().contains("target repo is not clean"));
        assertEquals("dirty target\n", Files.readString(target.resolve("README.md"), StandardCharsets.UTF_8));
    }

    @Test
    void capturesAndAppliesUntrackedBusinessFiles() throws Exception {
        var target = createRepo();
        var source = tempDir.resolve("source-worktree");
        git(target, "worktree", "add", source.toString(), "-b", "ai/test");
        Files.writeString(source.resolve("new-file.txt"), "new\n", StandardCharsets.UTF_8);

        var service = new GitPatchMergeService();
        var capture = service.capture(source);
        var apply = service.apply(target, capture.patch());

        assertEquals(java.util.List.of("new-file.txt"), capture.acceptedFiles());
        assertTrue(apply.succeeded(), apply.stderr());
        assertEquals("new\n", Files.readString(target.resolve("new-file.txt")));
        assertEquals(capture.expectedResultTree(), service.stagedTree(target));
    }

    @Test
    void capturesBinarySpacesRenameDeleteAndModeWithoutMutatingRealIndexes() throws Exception {
        var target = createRepo();
        Files.writeString(target.resolve("old name.txt"), "rename me\n");
        Files.writeString(target.resolve("delete.txt"), "delete me\n");
        Files.writeString(target.resolve("script.sh"), "#!/bin/sh\necho before\n");
        Files.write(target.resolve("binary.dat"), new byte[]{0, 1, 2, 3});
        git(target, "add", "-A");
        git(target, "commit", "-m", "complex base");
        var source = tempDir.resolve("complex source worktree");
        git(target, "worktree", "add", source.toString(), "-b", "ai/complex");
        Files.move(source.resolve("old name.txt"), source.resolve("new name.txt"));
        Files.delete(source.resolve("delete.txt"));
        Files.write(source.resolve("binary.dat"), new byte[]{0, 1, 2, 4, 0, 5});
        Files.setPosixFilePermissions(source.resolve("script.sh"), Set.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE,
                PosixFilePermission.GROUP_READ,
                PosixFilePermission.OTHERS_READ
        ));
        Files.writeString(source.resolve("space file.txt"), "new file\n");
        var targetIndexBefore = git(target, "write-tree").trim();
        var sourceIndexBefore = git(source, "write-tree").trim();

        var service = new GitPatchMergeService();
        var capture = service.capture(source);

        assertEquals(targetIndexBefore, git(target, "write-tree").trim());
        assertEquals(sourceIndexBefore, git(source, "write-tree").trim());
        assertTrue(service.apply(target, capture.patch()).succeeded());
        assertEquals(capture.expectedResultTree(), service.stagedTree(target));
        assertTrue(Files.exists(target.resolve("new name.txt")));
        assertFalse(Files.exists(target.resolve("old name.txt")));
        assertFalse(Files.exists(target.resolve("delete.txt")));
        assertEquals(6, Files.size(target.resolve("binary.dat")));
        assertTrue(Files.isExecutable(target.resolve("script.sh")));
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
