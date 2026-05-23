package dev.dual.ledger.file;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class WorkstreamLock implements AutoCloseable {
    private final FileChannel channel;
    private final FileLock lock;
    private final Path ownerPath;

    private WorkstreamLock(FileChannel channel, FileLock lock, Path ownerPath) {
        this.channel = channel;
        this.lock = lock;
        this.ownerPath = ownerPath;
    }

    public static WorkstreamLock acquire(Path workstreamDir, LockOwner owner) throws IOException {
        Files.createDirectories(workstreamDir);
        var lockPath = workstreamDir.resolve(".mutation.lock");
        var ownerPath = workstreamDir.resolve(".mutation-lock-owner.json");
        var channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        var lock = channel.tryLock();
        if (lock == null) {
            channel.close();
            throw new WorkstreamLockedException(workstreamDir);
        }
        try {
            Files.writeString(ownerPath, owner.toJson());
            return new WorkstreamLock(channel, lock, ownerPath);
        } catch (IOException writeOwnerFailure) {
            lock.release();
            channel.close();
            throw writeOwnerFailure;
        }
    }

    @Override
    public void close() throws IOException {
        try {
            lock.release();
        } finally {
            channel.close();
            Files.deleteIfExists(ownerPath);
        }
    }
}
