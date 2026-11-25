package dev.dual.runtime.handler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class EvidenceCollectionSourceTest {
    @TempDir Path root;

    @Test void rawContentsAndApprovedIgnoredFilesCannotHideBehindPathInventory() throws Exception {
        initialize();
        Files.writeString(root.resolve("ignored.java"), "first");
        var before = EvidenceCollectionSource.capture(root, List.of("ignored.java"));
        assertEquals(before, EvidenceCollectionSource.capture(root, before.paths()));
        Files.writeString(root.resolve("source.java"), "changed");
        assertNotEquals(before.sha256(), EvidenceCollectionSource.capture(root, before.paths()).sha256());
        Files.writeString(root.resolve("source.java"), "original");
        Files.writeString(root.resolve("ignored.java"), "second");
        assertNotEquals(before.sha256(), EvidenceCollectionSource.capture(root, before.paths()).sha256());
    }

    @Test void additionsDeletionsExecutableModeAndSymlinksAreObserved() throws Exception {
        initialize();
        Files.writeString(root.resolve("new file.txt"), "untracked");
        var before = EvidenceCollectionSource.capture(root, List.of());
        Files.delete(root.resolve("new file.txt"));
        assertNotEquals(before.sha256(), EvidenceCollectionSource.capture(root, before.paths()).sha256());
        Files.writeString(root.resolve("new file.txt"), "untracked");
        Files.writeString(root.resolve("another.txt"), "addition");
        assertNotEquals(before.sha256(), EvidenceCollectionSource.capture(root, before.paths()).sha256());
        Files.delete(root.resolve("another.txt"));
        assertTrue(root.resolve("source.java").toFile().setExecutable(true));
        assertNotEquals(before.sha256(), EvidenceCollectionSource.capture(root, before.paths()).sha256());
        Files.delete(root.resolve("source.java"));
        Files.createSymbolicLink(root.resolve("source.java"), Path.of(".git/config"));
        assertThrows(java.io.IOException.class, () -> EvidenceCollectionSource.capture(root, before.paths()));
    }

    @Test void internalEvidenceAndIgnoredBuildOutputAreNotProductChanges() throws Exception {
        initialize();
        var before = EvidenceCollectionSource.capture(root, List.of());
        Files.createDirectories(root.resolve(".agent/evidence"));
        Files.writeString(root.resolve(".agent/evidence/result.json"), "{}");
        Files.writeString(root.resolve("ignored.java"), "not part of contract");
        assertEquals(before, EvidenceCollectionSource.capture(root, before.paths()));
    }

    private void initialize() throws Exception {
        GitTrackedPathInventory.command(root, "init");
        Files.writeString(root.resolve("source.java"), "original");
        Files.writeString(root.resolve(".gitignore"), "ignored.java\n");
        GitTrackedPathInventory.command(root, "add", ".");
        GitTrackedPathInventory.command(root, "-c", "user.name=Test", "-c", "user.email=test@example.invalid",
                "commit", "-m", "baseline");
    }

    @Test void dirtyAndUntrackedContentsChangeWithoutChangingHeadIndexOrPorcelain() throws Exception {
        initialize();
        Files.writeString(root.resolve("source.java"), "dirty A");
        Files.writeString(root.resolve("new file.txt"), "untracked A");
        var before = EvidenceCollectionSource.capture(root, List.of());
        var status = GitTrackedPathInventory.command(root, "status", "--porcelain", "--untracked-files=all");
        for (var path : List.of("source.java", "new file.txt")) {
            Files.writeString(root.resolve(path), "different bytes B");
            var after = EvidenceCollectionSource.capture(root, before.paths());
            assertEquals(before.head(), after.head());
            assertEquals(before.indexSha256(), after.indexSha256());
            assertEquals(before.paths(), after.paths());
            assertEquals(status, GitTrackedPathInventory.command(root, "status", "--porcelain", "--untracked-files=all"));
            assertNotEquals(before.sha256(), after.sha256(), path);
            Files.writeString(root.resolve(path), path.equals("source.java") ? "dirty A" : "untracked A");
        }
    }
}
