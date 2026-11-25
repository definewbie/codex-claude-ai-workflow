package dev.dual.ledger.file;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class WorkstreamLock implements AutoCloseable {
    private final FileChannel channel;
    private final FileLock lock;
    private final Path ownerPath;
    private final String ownerJson;
    private boolean closed;

    private WorkstreamLock(FileChannel channel, FileLock lock, Path ownerPath, String ownerJson) {
        this.channel = channel;
        this.lock = lock;
        this.ownerPath = ownerPath;
        this.ownerJson = ownerJson;
    }

    public static WorkstreamLock acquire(Path workstreamDir, LockOwner owner) throws IOException {
        Files.createDirectories(workstreamDir);
        workstreamDir = workstreamDir.toRealPath();
        var lockPath = workstreamDir.resolve(".mutation.lock");
        var ownerPath = workstreamDir.resolve(".mutation-lock-owner.json");
        var channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        final FileLock lock;
        try {
            lock = channel.tryLock();
        } catch (OverlappingFileLockException busy) {
            channel.close();
            throw new WorkstreamLockedException(workstreamDir);
        } catch (IOException | RuntimeException failure) {
            try { channel.close(); } catch (IOException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
        if (lock == null) {
            channel.close();
            throw new WorkstreamLockedException(workstreamDir);
        }
        try {
            var ownerJson = owner.toJson();
            Files.writeString(ownerPath, ownerJson);
            return new WorkstreamLock(channel, lock, ownerPath, ownerJson);
        } catch (IOException writeOwnerFailure) {
            try { lock.release(); } catch (IOException cleanup) { writeOwnerFailure.addSuppressed(cleanup); }
            try { channel.close(); } catch (IOException cleanup) { writeOwnerFailure.addSuppressed(cleanup); }
            throw writeOwnerFailure;
        }
    }

    @Override
    public synchronized void close() throws IOException {
        if (closed) return;
        closed = true;
        IOException failure = null;
        try {
            // The owner file is diagnostic. Delete only ours, before another owner can acquire the lock.
            if (lock.isValid() && Files.exists(ownerPath) && ownerJson.equals(Files.readString(ownerPath)))
                Files.delete(ownerPath);
        } catch (IOException error) {
            failure = error;
        }
        try { lock.release(); } catch (IOException error) {
            if (failure == null) failure = error; else failure.addSuppressed(error);
        }
        try { channel.close(); } catch (IOException error) {
            if (failure == null) failure = error; else failure.addSuppressed(error);
        }
        if (failure != null) throw failure;
    }
}
