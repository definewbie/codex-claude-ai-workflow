package dev.dual.adapter.acpx;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

final class AgentOutputCompletionDetector {
    boolean isComplete(Path stdoutPath) {
        var stdout = read(stdoutPath);
        if (!stdout.contains("[done] end_turn")) {
            return false;
        }
        var fenceStart = stdout.lastIndexOf("```dual-control");
        if (fenceStart < 0) {
            return false;
        }
        var fenceEnd = stdout.indexOf("```", fenceStart + "```dual-control".length());
        if (fenceEnd < 0) {
            return false;
        }
        var body = stdout.substring(fenceStart, fenceEnd);
        return body.contains("\ndecision:") && body.contains("\nsummary:");
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
}
