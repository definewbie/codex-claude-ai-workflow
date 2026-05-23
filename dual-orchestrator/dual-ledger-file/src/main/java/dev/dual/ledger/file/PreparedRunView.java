package dev.dual.ledger.file;

import java.nio.file.Path;

public record PreparedRunView(String runId, Path runDir, Path promptPath, Path stdoutPath, Path stderrPath) {
}
