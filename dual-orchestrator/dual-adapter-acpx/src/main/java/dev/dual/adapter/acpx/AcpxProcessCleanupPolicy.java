package dev.dual.adapter.acpx;

import java.util.Set;

final class AcpxProcessCleanupPolicy {
    private AcpxProcessCleanupPolicy() {
    }

    static boolean shouldCleanup(ProcessInfo process, Set<Long> knownBefore) {
        if (knownBefore.contains(process.pid())) {
            return false;
        }
        var commandLine = process.commandLine().toLowerCase(java.util.Locale.ROOT);
        return isCodexSerenaMcp(commandLine) || isAcpxQueueOwner(commandLine);
    }

    private static boolean isCodexSerenaMcp(String commandLine) {
        return commandLine.contains("serena")
                && commandLine.contains("start-mcp-server")
                && commandLine.contains("--context")
                && commandLine.contains("codex");
    }

    private static boolean isAcpxQueueOwner(String commandLine) {
        return commandLine.contains("acpx")
                && commandLine.contains("dist/cli.js")
                && commandLine.contains("__queue-owner");
    }

    record ProcessInfo(long pid, String commandLine) {
    }
}
