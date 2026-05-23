package dev.dual.adapter.acpx;

import java.time.Instant;
import java.util.List;

public record AcpxRunResult(int exitCode, Instant startedAt, Instant finishedAt, List<String> command) {
}
