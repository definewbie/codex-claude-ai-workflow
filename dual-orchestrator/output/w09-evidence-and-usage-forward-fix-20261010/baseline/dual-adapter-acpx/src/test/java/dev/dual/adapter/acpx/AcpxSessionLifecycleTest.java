package dev.dual.adapter.acpx;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcpxSessionLifecycleTest {

    @TempDir
    Path tempDir;

    /** Records close calls instead of spawning acpx. */
    static class RecordingTransport extends AcpxAgentTransport {
        final List<String> closed = new ArrayList<>();

        RecordingTransport() {
            super("acpx-dummy");
        }

        @Override
        public boolean closeSession(String agent, Path workdir, String sessionName) {
            closed.add(agent + ":" + sessionName);
            return true;
        }
    }

    @Test
    void closesDistinctAgentSessionsReadFromRuns() throws Exception {
        var ws = "ws-close";
        writeRun(ws, "run-1", "claude", "claude-main-ws-close-run-1");
        writeRun(ws, "run-2", "codex", "codex-reviewer-ws-close");
        writeRun(ws, "run-3", "claude", "claude-main-ws-close-run-3");
        writeRun(ws, "run-4", "claude", "claude-main-ws-close-run-1"); // duplicate -> deduped
        writeRun(ws, "run-5", "", "blank-agent-skipped");        // blank agent -> skipped

        var transport = new RecordingTransport();
        var summary = new AcpxSessionLifecycle(transport).closeWorkstreamSessions(tempDir, ws);

        assertEquals(3, summary.distinctSessions());
        assertEquals(3, summary.closed());
        assertEquals(0, summary.failed());
        assertTrue(transport.closed.contains("claude:claude-main-ws-close-run-1"));
        assertTrue(transport.closed.contains("claude:claude-main-ws-close-run-3"));
        assertTrue(transport.closed.contains("codex:codex-reviewer-ws-close"));
    }

    @Test
    void neverThrowsWhenRunsDirMissing() {
        var transport = new RecordingTransport();
        var summary = new AcpxSessionLifecycle(transport).closeWorkstreamSessions(tempDir, "no-such-workstream");

        assertEquals(0, summary.distinctSessions());
        assertEquals(0, summary.closed());
        assertTrue(transport.closed.isEmpty());
    }

    @Test
    void countsFailedClosesWithoutThrowing() throws Exception {
        var ws = "ws-fail";
        writeRun(ws, "run-1", "claude", "claude-main-ws-fail");
        var transport = new RecordingTransport() {
            @Override
            public boolean closeSession(String agent, Path workdir, String sessionName) {
                super.closeSession(agent, workdir, sessionName);
                return false; // simulate acpx close failure
            }
        };

        var summary = new AcpxSessionLifecycle(transport).closeWorkstreamSessions(tempDir, ws);

        assertEquals(1, summary.distinctSessions());
        assertEquals(0, summary.closed());
        assertEquals(1, summary.failed());
    }

    @Test
    void writesDiagnosticLogAlongsideRuns() throws Exception {
        var ws = "ws-diag";
        writeRun(ws, "run-1", "claude", "claude-main-ws-diag");
        new AcpxSessionLifecycle(new RecordingTransport()).closeWorkstreamSessions(tempDir, ws);

        var log = tempDir.resolve(".agent/workstreams").resolve(ws).resolve("session-cleanup.log");
        assertTrue(Files.isRegularFile(log));
        var content = Files.readString(log);
        assertTrue(content.contains("session-cleanup workstream=ws-diag"));
        assertTrue(content.contains("claude claude-main-ws-diag"));
    }

    private void writeRun(String ws, String runId, String agent, String session) throws Exception {
        var dir = tempDir.resolve(".agent/workstreams").resolve(ws).resolve("runs").resolve(runId);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("run.json"),
                "{\"runId\":\"" + runId + "\",\"agent\":\"" + agent + "\",\"session\":\"" + session + "\"}");
    }
}
