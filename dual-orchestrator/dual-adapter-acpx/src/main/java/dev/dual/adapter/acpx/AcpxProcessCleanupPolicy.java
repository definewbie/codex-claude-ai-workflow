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
        return commandLine.contains("serena")
                && commandLine.contains("start-mcp-server")
                && commandLine.contains("--context")
                && commandLine.contains("codex");
    }

    record ProcessInfo(long pid, String commandLine) {
    }
}
