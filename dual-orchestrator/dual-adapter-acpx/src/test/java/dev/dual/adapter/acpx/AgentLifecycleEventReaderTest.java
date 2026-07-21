package dev.dual.adapter.acpx;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void buffersPartialNdjsonUntilTheLineIsComplete() throws Exception {
        var stdout = tempDir.resolve("stdout-partial.log");
        var stderr = tempDir.resolve("stderr-partial.log");
        var sessionsDir = tempDir.resolve("sessions-partial");
        var sessionId = "123e4567-e89b-12d3-a456-426614174099";
        var stream = sessionsDir.resolve(sessionId + ".stream.ndjson");
        Files.createDirectories(sessionsDir);
        Files.writeString(stdout, "");
        Files.writeString(stderr, "[acpx] session claude-main (" + sessionId + ")\n");
        Files.writeString(stream, "");

        var reader = new AgentLifecycleEventReader(stdout, stderr, sessionsDir);
        reader.readNewEvents();
        Files.writeString(stream, "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"session/prompt\"",
                java.nio.file.StandardOpenOption.APPEND);

        assertTrue(reader.readNewEvents().stream()
                .noneMatch(event -> event.kind() == AgentLifecycleEvent.Kind.PROMPT_ACCEPTED));

        Files.writeString(stream, ",\"params\":{}}\n", java.nio.file.StandardOpenOption.APPEND);

        assertTrue(reader.readNewEvents().stream()
                .anyMatch(event -> event.kind() == AgentLifecycleEvent.Kind.PROMPT_ACCEPTED));
    }

    @Test
    void preservesUtf8ProviderErrorSplitInsideAMultibyteCharacter() throws Exception {
        var stdout = tempDir.resolve("stdout-unicode.log");
        var stderr = tempDir.resolve("stderr-unicode.log");
        var sessionsDir = tempDir.resolve("sessions-unicode");
        var sessionId = "123e4567-e89b-12d3-a456-426614174100";
        var stream = sessionsDir.resolve(sessionId + ".stream.ndjson");
        Files.createDirectories(sessionsDir);
        Files.writeString(stdout, "");
        Files.writeString(stderr, "[acpx] session claude-main (" + sessionId + ")\n");
        Files.writeString(stream, "");

        var reader = new AgentLifecycleEventReader(stdout, stderr, sessionsDir);
        reader.readNewEvents();
        Files.writeString(stream, "{\"jsonrpc\":\"2.0\",\"id\":8,\"method\":\"session/prompt\",\"params\":{}}\n",
                StandardOpenOption.APPEND);
        reader.readNewEvents();

        var error = "{\"jsonrpc\":\"2.0\",\"id\":8,\"error\":{\"code\":-32603,\"message\":\"访问量过大\"}}\n"
                .getBytes(StandardCharsets.UTF_8);
        var firstChineseByte = indexOf(error, "访问量过大".getBytes(StandardCharsets.UTF_8));
        var split = firstChineseByte + 1;
        Files.write(stream, Arrays.copyOfRange(error, 0, split), StandardOpenOption.APPEND);

        assertTrue(reader.readNewEvents().stream()
                .noneMatch(event -> event.kind() == AgentLifecycleEvent.Kind.PROVIDER_FAILURE));

        Files.write(stream, Arrays.copyOfRange(error, split, error.length), StandardOpenOption.APPEND);
        var failures = reader.readNewEvents().stream()
                .filter(event -> event.kind() == AgentLifecycleEvent.Kind.PROVIDER_FAILURE)
                .toList();

        assertEquals(1, failures.size());
        assertEquals("访问量过大", failures.get(0).streamError().message());
    }

    @Test
    void sessionChangeDiscardsPendingBytesFromThePreviousSession() throws Exception {
        var stdout = tempDir.resolve("stdout-session-change.log");
        var stderr = tempDir.resolve("stderr-session-change.log");
        var sessionsDir = tempDir.resolve("sessions-change");
        var firstSession = "123e4567-e89b-12d3-a456-426614174101";
        var secondSession = "123e4567-e89b-12d3-a456-426614174102";
        var firstStream = sessionsDir.resolve(firstSession + ".stream.ndjson");
        var secondStream = sessionsDir.resolve(secondSession + ".stream.ndjson");
        Files.createDirectories(sessionsDir);
        Files.writeString(stdout, "");
        Files.writeString(stderr, "[acpx] session claude-main (" + firstSession + ")\n");
        Files.writeString(firstStream, "");

        var reader = new AgentLifecycleEventReader(stdout, stderr, sessionsDir);
        reader.readNewEvents();
        Files.writeString(firstStream, "{\"jsonrpc\":\"2.0\",\"id\":10,\"method\":\"session/prompt\"",
                StandardOpenOption.APPEND);
        assertTrue(reader.readNewEvents().isEmpty());

        Files.writeString(secondStream, "{\"jsonrpc\":\"2.0\",\"id\":11,\"method\":\"session/prompt\",\"params\":{}}\n");
        Files.writeString(stderr, "[acpx] session claude-main (" + secondSession + ")\n",
                StandardOpenOption.APPEND);

        assertTrue(reader.readNewEvents().stream()
                .anyMatch(event -> event.kind() == AgentLifecycleEvent.Kind.PROMPT_ACCEPTED));
    }

    @Test
    void sessionChangeClearsRequestCorrelationFromThePreviousSession() throws Exception {
        var stdout = tempDir.resolve("stdout-correlation.log");
        var stderr = tempDir.resolve("stderr-correlation.log");
        var sessionsDir = tempDir.resolve("sessions-correlation");
        var firstSession = "123e4567-e89b-12d3-a456-426614174103";
        var secondSession = "123e4567-e89b-12d3-a456-426614174104";
        var firstStream = sessionsDir.resolve(firstSession + ".stream.ndjson");
        var secondStream = sessionsDir.resolve(secondSession + ".stream.ndjson");
        Files.createDirectories(sessionsDir);
        Files.writeString(stdout, "");
        Files.writeString(stderr, "[acpx] session claude-main (" + firstSession + ")\n");
        Files.writeString(firstStream, "");

        var reader = new AgentLifecycleEventReader(stdout, stderr, sessionsDir);
        reader.readNewEvents();
        Files.writeString(firstStream, "{\"jsonrpc\":\"2.0\",\"id\":12,\"method\":\"session/resume\",\"params\":{}}\n",
                StandardOpenOption.APPEND);
        reader.readNewEvents();

        Files.writeString(secondStream, "");
        Files.writeString(stderr, "[acpx] session claude-main (" + secondSession + ")\n",
                StandardOpenOption.APPEND);
        reader.readNewEvents();
        Files.writeString(secondStream, "{\"jsonrpc\":\"2.0\",\"id\":12,\"error\":{\"code\":-32603,\"message\":\"new session failure\"}}\n",
                StandardOpenOption.APPEND);

        var events = reader.readNewEvents();
        assertTrue(events.stream().anyMatch(event -> event.kind() == AgentLifecycleEvent.Kind.PROVIDER_FAILURE));
        assertFalse(events.stream().anyMatch(event -> event.kind() == AgentLifecycleEvent.Kind.SESSION_RESUME_FAILED));
    }

    @Test
    void repeatedSessionIdentityDoesNotReplayAlreadyConsumedStreamEvents() throws Exception {
        var stdout = tempDir.resolve("stdout-same-session.log");
        var stderr = tempDir.resolve("stderr-same-session.log");
        var sessionsDir = tempDir.resolve("sessions-same-session");
        var sessionId = "123e4567-e89b-12d3-a456-426614174105";
        var stream = sessionsDir.resolve(sessionId + ".stream.ndjson");
        Files.createDirectories(sessionsDir);
        Files.writeString(stdout, "");
        Files.writeString(stderr, "[acpx] session claude-main (" + sessionId + ")\n");
        Files.writeString(stream, "");

        var reader = new AgentLifecycleEventReader(stdout, stderr, sessionsDir);
        reader.readNewEvents();
        Files.writeString(stream, "{\"jsonrpc\":\"2.0\",\"id\":13,\"method\":\"session/prompt\",\"params\":{}}\n",
                StandardOpenOption.APPEND);
        assertEquals(1, reader.readNewEvents().stream()
                .filter(event -> event.kind() == AgentLifecycleEvent.Kind.PROMPT_ACCEPTED)
                .count());

        Files.writeString(stderr, "[acpx] session claude-main (" + sessionId + ") · agent connected\n",
                StandardOpenOption.APPEND);

        assertEquals(0, reader.readNewEvents().stream()
                .filter(event -> event.kind() == AgentLifecycleEvent.Kind.PROMPT_ACCEPTED)
                .count());
    }

    private static int indexOf(byte[] source, byte[] target) {
        for (int start = 0; start <= source.length - target.length; start++) {
            var matches = true;
            for (int offset = 0; offset < target.length; offset++) {
                if (source[start + offset] != target[offset]) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return start;
            }
        }
        throw new IllegalArgumentException("target bytes not found");
    }
}
