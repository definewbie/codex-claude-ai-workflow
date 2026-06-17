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
    }
}
