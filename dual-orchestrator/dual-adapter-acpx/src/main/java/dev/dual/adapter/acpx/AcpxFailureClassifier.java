package dev.dual.adapter.acpx;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

final class AcpxFailureClassifier {
    AcpxFailure classify(Path stderrPath, int exitCode) {
        var stderr = read(stderrPath);
        var lower = stderr.toLowerCase();
        if (lower.contains("agent needs reconnect")) {
            return new AcpxFailure(
                    "RECOVERABLE_AGENT_RECONNECT",
                    "acpx agent session needs reconnect; fix the session and run dualctl retry, then dualctl resume",
                    true
            );
        }
        if (lower.contains("no acpx session found")) {
            return new AcpxFailure(
                    "RECOVERABLE_SESSION_MISSING",
                    "acpx session is missing; create or reconnect the session and run dualctl retry, then dualctl resume",
                    true
            );
        }
        if (lower.contains("rate limit") || lower.contains("quota")) {
            return new AcpxFailure(
                    "RECOVERABLE_RATE_LIMIT",
                    "agent quota or rate limit was hit; wait for quota recovery and run dualctl retry, then dualctl resume",
                    true
            );
        }
        if (lower.contains("context") && lower.contains("window")) {
            return new AcpxFailure(
                    "RECOVERABLE_CONTEXT_WINDOW",
                    "agent context window appears exhausted; start or reconnect a fresh session and run dualctl retry, then dualctl resume",
                    true
            );
        }
        return new AcpxFailure(
                "ACPX_EXIT_" + exitCode,
                "acpx exited with " + exitCode,
                false
        );
    }

    private String read(Path path) {
        if (path == null || !Files.exists(path)) {
            return "";
        }
        try {
            return Files.readString(path);
        } catch (IOException ignored) {
            return "";
        }
    }

    record AcpxFailure(String reason, String message, boolean recoverable) {
    }
}
