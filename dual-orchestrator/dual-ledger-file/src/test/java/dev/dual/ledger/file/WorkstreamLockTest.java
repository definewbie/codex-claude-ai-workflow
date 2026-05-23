package dev.dual.ledger.file;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkstreamLockTest {
    @TempDir
    Path tempDir;

    @Test
    void rejectsSecondMutationLock() throws Exception {
        var workstreamDir = tempDir.resolve("ws");
        try (var ignored = WorkstreamLock.acquire(workstreamDir, LockOwner.currentProcess("ws", "resume"))) {
            assertTrue(Files.exists(workstreamDir.resolve(".mutation-lock-owner.json")));
            assertThrows(
                    Exception.class,
                    () -> WorkstreamLock.acquire(workstreamDir, LockOwner.currentProcess("ws", "review approve"))
            );
        }
    }
}
