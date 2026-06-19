package dev.dual.adapter.acpx;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentLifecycleEventReaderTest {
    @TempDir
    Path tempDir;

    @Test
    void initialStreamTailIgnoresHistoricalProviderFailuresButKeepsLifecycleRecoveryEvents() throws Exception {
        var stdout = tempDir.resolve("stdout.md");
        var stderr = tempDir.resolve("stderr.log");
        var sessionsDir = tempDir.resolve("sessions");
        var sessionId = "11111111-2222-3333-4444-555555555555";
        Files.createDirectories(sessionsDir);
        Files.writeString(stdout, "");
        Files.writeString(stderr, "[acpx] session claude-main (" + sessionId + ") · agent needs reconnect\n");
        Files.writeString(sessionsDir.resolve(sessionId + ".stream.ndjson"), """
                {"jsonrpc":"2.0","method":"session/prompt/failed","error":"Internal error: API Error: 529 old overload"}
                {"jsonrpc":"2.0","id":1,"method":"session/new","params":{}}
                {"jsonrpc":"2.0","result":{"sessionId":"fresh-session"},"id":1}
                """);

        var reader = new AgentLifecycleEventReader(stdout, stderr, sessionsDir);
        var events = reader.readNewEvents();

        assertTrue(events.stream().anyMatch(event -> event.kind() == AgentLifecycleEvent.Kind.RECONNECT_SIGNAL));
        assertTrue(events.stream().anyMatch(event -> event.kind() == AgentLifecycleEvent.Kind.SESSION_NEW_CREATED));
        assertEquals(0, events.stream()
                .filter(event -> event.kind() == AgentLifecycleEvent.Kind.PROVIDER_FAILURE)
                .count());
    }

    @Test
    void laterStreamProviderFailureIsCurrentEvidence() throws Exception {
        var stdout = tempDir.resolve("stdout.md");
        var stderr = tempDir.resolve("stderr.log");
        var sessionsDir = tempDir.resolve("sessions");
        var sessionId = "11111111-2222-3333-4444-555555555555";
        var stream = sessionsDir.resolve(sessionId + ".stream.ndjson");
        Files.createDirectories(sessionsDir);
        Files.writeString(stdout, "");
        Files.writeString(stderr, "[acpx] session claude-main (" + sessionId + ") · agent connected\n");
        Files.writeString(stream, "{\"jsonrpc\":\"2.0\",\"result\":{\"sessionId\":\"fresh-session\"},\"id\":1}\n");

        var reader = new AgentLifecycleEventReader(stdout, stderr, sessionsDir);
        reader.readNewEvents();
        Files.writeString(stream, """
                {"jsonrpc":"2.0","method":"session/prompt/failed","error":"Internal error: API Error: 529 current overload"}
                """, java.nio.file.StandardOpenOption.APPEND);

        var events = reader.readNewEvents();

        assertTrue(events.stream().anyMatch(event -> event.kind() == AgentLifecycleEvent.Kind.PROVIDER_FAILURE));
    }
}
