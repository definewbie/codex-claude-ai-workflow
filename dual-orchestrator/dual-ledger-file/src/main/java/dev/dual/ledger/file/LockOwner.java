package dev.dual.ledger.file;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;

public record LockOwner(String workstreamId, long pid, String hostname, String command, Instant acquiredAt) {
    public static LockOwner currentProcess(String workstreamId, String command) {
        return new LockOwner(workstreamId, ProcessHandle.current().pid(), currentHostname(), command, Instant.now());
    }

    public String toJson() {
        return """
                {
                  "workstream_id": "%s",
                  "pid": %d,
                  "hostname": "%s",
                  "command": "%s",
                  "acquired_at": "%s"
                }
                """.formatted(workstreamId, pid, hostname, command, acquiredAt);
    }

    private static String currentHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException ignored) {
            return ManagementFactory.getRuntimeMXBean().getName();
        }
    }
}
