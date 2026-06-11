package dev.dual.adapter.acpx;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

final class AgentOutputCompletionDetector {
    private final AgentTerminalContract terminalContract = new AgentTerminalContract();

    boolean isComplete(Path stdoutPath) {
        return terminalContract.isComplete(read(stdoutPath));
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
