package dev.dual.ledger.file;

import java.io.IOException;
import java.nio.file.Path;

public final class WorkstreamLockedException extends IOException {
    public WorkstreamLockedException(Path workstreamDir) {
        super("workstream is already being mutated: " + workstreamDir);
    }
}
