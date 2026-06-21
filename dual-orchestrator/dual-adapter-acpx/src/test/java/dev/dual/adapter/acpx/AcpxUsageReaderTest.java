package dev.dual.adapter.acpx;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcpxUsageReaderTest {
    @TempDir
    Path tempDir;

    @Test
    void extractsUsageForMatchingPromptOnly() throws Exception {
        var sessionsDir = tempDir.resolve("sessions");
        var sessionId = "cd0caac4-0c68-423a-a448-60a58353ad7c";
        Files.createDirectories(sessionsDir);
        var promptPath = tempDir.resolve("prompt.md");
        var stderrPath = tempDir.resolve("stderr.log");
        Files.writeString(promptPath, "real prompt");
        Files.writeString(stderrPath, "[acpx] session claude-main (" + sessionId + ") · agent connected\n");
        Files.writeString(sessionsDir.resolve(sessionId + ".stream.ndjson"), """
                {"jsonrpc":"2.0","id":1,"method":"session/prompt","params":{"prompt":[{"type":"text","text":"other prompt"}]}}
                {"jsonrpc":"2.0","id":1,"result":{"usage":{"inputTokens":10,"outputTokens":1,"cachedReadTokens":0,"cachedWriteTokens":0,"totalTokens":11}}}
                {"jsonrpc":"2.0","id":2,"method":"session/prompt","params":{"prompt":[{"type":"text","text":"real prompt"}]}}
                {"jsonrpc":"2.0","method":"session/update","params":{"update":{"sessionUpdate":"usage_update","used":110,"size":200000,"cost":{"amount":0.25,"currency":"USD"}}}}
                {"jsonrpc":"2.0","id":2,"result":{"usage":{"inputTokens":100,"outputTokens":5,"cachedReadTokens":1000,"cachedWriteTokens":2,"totalTokens":1107}}}
                """);

        var usage = new AcpxUsageReader(sessionsDir).read(promptPath, stderrPath);

        assertTrue(usage.isPresent());
        assertEquals(100, usage.get().inputTokens());
        assertEquals(5, usage.get().outputTokens());
        assertEquals(1000, usage.get().cachedReadTokens());
        assertEquals(2, usage.get().cachedWriteTokens());
        assertEquals(1107, usage.get().totalTokens());
        assertEquals("0.25", usage.get().costAmount().toPlainString());
        assertEquals("USD", usage.get().costCurrency());
        assertEquals("acpx_stream", usage.get().source());
    }

    @Test
    void fallsBackToLatestPromptUsageWhenPromptTextIsRewrittenByAdapter() throws Exception {
        var sessionsDir = tempDir.resolve("sessions-fallback");
        var sessionId = "dd0caac4-0c68-423a-a448-60a58353ad7c";
        Files.createDirectories(sessionsDir);
        var promptPath = tempDir.resolve("prompt-fallback.md");
        var stderrPath = tempDir.resolve("stderr-fallback.log");
        Files.writeString(promptPath, "real prompt");
        Files.writeString(stderrPath, "[acpx] session claude-main (" + sessionId + ") · agent connected\n");
        Files.writeString(sessionsDir.resolve(sessionId + ".stream.ndjson"), """
                {"jsonrpc":"2.0","id":1,"method":"session/prompt","params":{"prompt":[{"type":"text","text":"older prompt"}]}}
                {"jsonrpc":"2.0","id":1,"result":{"usage":{"inputTokens":10,"outputTokens":1,"cachedReadTokens":0,"cachedWriteTokens":0,"totalTokens":11}}}
                {"jsonrpc":"2.0","id":2,"method":"session/prompt","params":{"prompt":[{"type":"text","text":"adapter wrapped real prompt"}]}}
                {"jsonrpc":"2.0","method":"session/update","params":{"update":{"sessionUpdate":"usage_update","used":252332,"size":1000000,"cost":{"amount":0.22,"currency":"USD"}}}}
                {"jsonrpc":"2.0","id":2,"result":{"usage":{"inputTokens":6799,"outputTokens":2653,"cachedReadTokens":242880,"cachedWriteTokens":0,"totalTokens":252332}}}
                """);

        var usage = new AcpxUsageReader(sessionsDir).read(promptPath, stderrPath);

        assertTrue(usage.isPresent());
        assertEquals(6799, usage.get().inputTokens());
        assertEquals(2653, usage.get().outputTokens());
        assertEquals(242880, usage.get().cachedReadTokens());
        assertEquals(252332, usage.get().totalTokens());
        assertEquals("0.22", usage.get().costAmount().toPlainString());
    }

    @Test
    void fallsBackToUsageUpdateWhenAdapterDoesNotReturnResultUsage() throws Exception {
        var sessionsDir = tempDir.resolve("sessions-usage-update");
        var sessionId = "ed0caac4-0c68-423a-a448-60a58353ad7c";
        Files.createDirectories(sessionsDir);
        var promptPath = tempDir.resolve("prompt-usage-update.md");
        var stderrPath = tempDir.resolve("stderr-usage-update.log");
        Files.writeString(promptPath, "real prompt");
        Files.writeString(stderrPath, "[acpx] session codex-reviewer (" + sessionId + ") · agent needs reconnect\n");
        Files.writeString(sessionsDir.resolve(sessionId + ".stream.ndjson"), """
                {"jsonrpc":"2.0","id":1,"method":"session/prompt","params":{"prompt":[{"type":"text","text":"adapter wrapped real prompt"}]}}
                {"jsonrpc":"2.0","method":"session/update","params":{"update":{"sessionUpdate":"usage_update","used":78353,"size":258400}}}
                {"jsonrpc":"2.0","result":{"stopReason":"end_turn"},"id":1}
                """);

        var usage = new AcpxUsageReader(sessionsDir).read(promptPath, stderrPath);

        assertTrue(usage.isPresent());
        assertEquals(0, usage.get().inputTokens());
        assertEquals(0, usage.get().outputTokens());
        assertEquals(78353, usage.get().totalTokens());
        assertEquals("acpx_usage_update", usage.get().source());
    }
}
