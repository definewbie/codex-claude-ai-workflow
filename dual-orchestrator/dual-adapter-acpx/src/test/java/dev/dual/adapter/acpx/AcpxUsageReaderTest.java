package dev.dual.adapter.acpx;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
        Files.writeString(sessionsDir.resolve(sessionId + ".json"), """
                {"current_model_id":"default","available_models":[{"id":"model","description":"Use the default model (currently glm-5.2[1m])"}]}
                """);
        Files.writeString(sessionsDir.resolve(sessionId + ".stream.ndjson"), """
                {"jsonrpc":"2.0","id":1,"method":"session/prompt","params":{"prompt":[{"type":"text","text":"other prompt"}]}}
                {"jsonrpc":"2.0","method":"session/update","params":{"update":{"sessionUpdate":"usage_update","used":11,"size":200000,"cost":{"amount":0.10,"currency":"USD"}}}}
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
        assertEquals("0.15", usage.get().costAmount().toPlainString());
        assertEquals("USD", usage.get().costCurrency());
        assertEquals("acpx_stream", usage.get().source());
        assertEquals(AcpxUsage.PROVIDER_RUN_USAGE, usage.get().measurementType());
        assertEquals(AcpxUsage.ATTR_EXACT_PROMPT_MATCH, usage.get().attribution());
        assertEquals("0.25", usage.get().sessionCostAmount().toPlainString());
        assertEquals("USD", usage.get().sessionCostCurrency());
        assertEquals("zhipu", usage.get().providerId());
        assertEquals("glm-5.2", usage.get().model());
        assertEquals(AcpxProviderMetadata.BILLING_SUBSCRIPTION_TOKENS, usage.get().billingMode());
        assertEquals(AcpxProviderMetadata.SOURCE_ACPX_SESSION_JSON, usage.get().providerMetadataSource());
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
                {"jsonrpc":"2.0","method":"session/update","params":{"update":{"sessionUpdate":"usage_update","used":11,"size":1000000,"cost":{"amount":1.00,"currency":"USD"}}}}
                {"jsonrpc":"2.0","id":1,"result":{"usage":{"inputTokens":10,"outputTokens":1,"cachedReadTokens":0,"cachedWriteTokens":0,"totalTokens":11}}}
                {"jsonrpc":"2.0","id":2,"method":"session/prompt","params":{"prompt":[{"type":"text","text":"adapter wrapped real prompt"}]}}
                {"jsonrpc":"2.0","method":"session/update","params":{"update":{"sessionUpdate":"usage_update","used":252332,"size":1000000,"cost":{"amount":1.22,"currency":"USD"}}}}
                {"jsonrpc":"2.0","id":2,"result":{"usage":{"inputTokens":6799,"outputTokens":2653,"cachedReadTokens":242880,"cachedWriteTokens":0,"totalTokens":252332}}}
                """);

        var usage = new AcpxUsageReader(sessionsDir).read(promptPath, stderrPath);

        assertTrue(usage.isPresent());
        assertEquals(6799, usage.get().inputTokens());
        assertEquals(2653, usage.get().outputTokens());
        assertEquals(242880, usage.get().cachedReadTokens());
        assertEquals(252332, usage.get().totalTokens());
        assertEquals("0.22", usage.get().costAmount().toPlainString());
        assertEquals("1.22", usage.get().sessionCostAmount().toPlainString());
        assertEquals(AcpxUsage.PROVIDER_RUN_USAGE, usage.get().measurementType());
        // 文本不一致时按 request id 取最近 prompt 的 result，必须诚实标注为 latest，不能伪装成精确匹配。
        assertEquals(AcpxUsage.ATTR_LATEST_PROMPT_IN_SESSION, usage.get().attribution());
    }

    @Test
    void leavesRunCostUnknownWhenNonFirstPromptHasNoStartingCostObservation() throws Exception {
        var sessionsDir = tempDir.resolve("sessions-missing-start-cost");
        var sessionId = "fd0caac4-0c68-423a-a448-60a58353ad7c";
        Files.createDirectories(sessionsDir);
        var promptPath = tempDir.resolve("prompt-missing-start-cost.md");
        var stderrPath = tempDir.resolve("stderr-missing-start-cost.log");
        Files.writeString(promptPath, "real prompt");
        Files.writeString(stderrPath, "[acpx] session claude-main (" + sessionId + ") · agent connected\n");
        Files.writeString(sessionsDir.resolve(sessionId + ".stream.ndjson"), """
                {"jsonrpc":"2.0","id":1,"method":"session/prompt","params":{"prompt":[{"type":"text","text":"older prompt"}]}}
                {"jsonrpc":"2.0","id":1,"result":{"usage":{"inputTokens":10,"outputTokens":1,"cachedReadTokens":0,"cachedWriteTokens":0,"totalTokens":11}}}
                {"jsonrpc":"2.0","id":2,"method":"session/prompt","params":{"prompt":[{"type":"text","text":"real prompt"}]}}
                {"jsonrpc":"2.0","method":"session/update","params":{"update":{"sessionUpdate":"usage_update","used":252332,"size":1000000,"cost":{"amount":1.22,"currency":"USD"}}}}
                {"jsonrpc":"2.0","id":2,"result":{"usage":{"inputTokens":6799,"outputTokens":2653,"cachedReadTokens":242880,"cachedWriteTokens":0,"totalTokens":252332}}}
                """);

        var usage = new AcpxUsageReader(sessionsDir).read(promptPath, stderrPath);

        assertTrue(usage.isPresent());
        assertNull(usage.get().costAmount());
        assertEquals("1.22", usage.get().sessionCostAmount().toPlainString());
        assertEquals(AcpxUsage.ATTR_EXACT_PROMPT_MATCH, usage.get().attribution());
    }

    @Test
    void prefersProviderRunUsageOverSessionSnapshotWhenBothArePresent() throws Exception {
        var sessionsDir = tempDir.resolve("sessions-prefer-provider");
        var sessionId = "1a0caac4-0c68-423a-a448-60a58353ad7c";
        Files.createDirectories(sessionsDir);
        var promptPath = tempDir.resolve("prompt-prefer.md");
        var stderrPath = tempDir.resolve("stderr-prefer.log");
        Files.writeString(promptPath, "real prompt");
        Files.writeString(stderrPath, "[acpx] session claude-main (" + sessionId + ") · agent connected\n");
        Files.writeString(sessionsDir.resolve(sessionId + ".stream.ndjson"), """
                {"jsonrpc":"2.0","id":2,"method":"session/prompt","params":{"prompt":[{"type":"text","text":"adapter wrapped real prompt"}]}}
                {"jsonrpc":"2.0","method":"session/update","params":{"update":{"sessionUpdate":"usage_update","used":99999,"size":1000000}}}
                {"jsonrpc":"2.0","id":2,"result":{"usage":{"inputTokens":6799,"outputTokens":2653,"cachedReadTokens":242880,"cachedWriteTokens":0,"totalTokens":252332}}}
                """);

        var usage = new AcpxUsageReader(sessionsDir, 1, 0).read(promptPath, stderrPath);

        assertTrue(usage.isPresent());
        // 有 result.usage 就必须用 provider run usage，不能被 usage_update snapshot 短路。
        assertEquals(AcpxUsage.PROVIDER_RUN_USAGE, usage.get().measurementType());
        assertEquals(6799, usage.get().inputTokens());
        assertEquals(AcpxUsage.ATTR_LATEST_PROMPT_IN_SESSION, usage.get().attribution());
    }

    @Test
    void waitsForProviderRunUsageEvenWhenSessionSnapshotIsAlreadyPresent() throws Exception {
        var sessionsDir = tempDir.resolve("sessions-late");
        var sessionId = "2b0caac4-0c68-423a-a448-60a58353ad7c";
        Files.createDirectories(sessionsDir);
        var promptPath = tempDir.resolve("prompt-late.md");
        var stderrPath = tempDir.resolve("stderr-late.log");
        Files.writeString(promptPath, "real prompt");
        Files.writeString(stderrPath, "[acpx] session claude-main (" + sessionId + ") · agent connected\n");
        var streamPath = sessionsDir.resolve(sessionId + ".stream.ndjson");
        // 起初只有 prompt + usage_update 快照，result.usage 还没异步落盘。
        Files.writeString(streamPath, """
                {"jsonrpc":"2.0","id":2,"method":"session/prompt","params":{"prompt":[{"type":"text","text":"adapter wrapped real prompt"}]}}
                {"jsonrpc":"2.0","method":"session/update","params":{"update":{"sessionUpdate":"usage_update","used":99999,"size":1000000}}}
                """);
        var appender = new Thread(() -> {
            try {
                Thread.sleep(60);
                Files.writeString(streamPath, """
                        {"jsonrpc":"2.0","id":2,"result":{"usage":{"inputTokens":6799,"outputTokens":2653,"cachedReadTokens":242880,"cachedWriteTokens":0,"totalTokens":252332}}}
                        """, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
            } catch (InterruptedException | java.io.IOException ignored) {
                // best-effort append for the timing test
            }
        });
        appender.start();
        // 10 attempts × 30ms = 300ms 窗口，足以等到 60ms 后才落盘的 result.usage。
        var usage = new AcpxUsageReader(sessionsDir, 10, 30).read(promptPath, stderrPath);
        appender.join();

        assertTrue(usage.isPresent());
        assertEquals(AcpxUsage.PROVIDER_RUN_USAGE, usage.get().measurementType());
        assertEquals(6799, usage.get().inputTokens());
        assertEquals(AcpxUsage.ATTR_LATEST_PROMPT_IN_SESSION, usage.get().attribution());
    }

    @Test
    void recordsUsageUpdateAsSessionSnapshotWhenAdapterDoesNotReturnResultUsage() throws Exception {
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

        // 单次重试即可：fixture 里没有 provider result.usage 可等，应直接回落到 session snapshot。
        var usage = new AcpxUsageReader(sessionsDir, 1, 0).read(promptPath, stderrPath);

        assertTrue(usage.isPresent());
        assertEquals(0, usage.get().inputTokens());
        assertEquals(0, usage.get().outputTokens());
        assertEquals(0, usage.get().totalTokens());
        assertEquals(78353, usage.get().contextUsedTokens());
        assertEquals("acpx_usage_update", usage.get().source());
        assertEquals(AcpxUsage.SESSION_USED_SNAPSHOT, usage.get().measurementType());
        assertEquals(AcpxUsage.ATTR_SESSION_USAGE_UPDATE, usage.get().attribution());
        assertTrue(usage.get().cumulative());
    }

    @Test
    void recordsProviderIdentityWhenClaudeDirectRunHasNoAcpxSessionId() throws Exception {
        var promptPath = tempDir.resolve("prompt-direct.md");
        var stderrPath = tempDir.resolve("stderr-direct.log");
        Files.writeString(promptPath, "real prompt");
        Files.writeString(stderrPath, "");
        var metadata = new AcpxProviderMetadata(
                "zhipu",
                "glm-5.2",
                AcpxProviderMetadata.BILLING_SUBSCRIPTION_TOKENS,
                AcpxProviderMetadata.SOURCE_CLAUDE_SETTINGS_ENV
        );

        var usage = new AcpxUsageReader(tempDir.resolve("sessions-direct"), 1, 0, metadata)
                .read(promptPath, stderrPath);

        assertTrue(usage.isPresent());
        assertEquals(AcpxUsage.UNAVAILABLE, usage.get().measurementType());
        assertEquals("NO_ACPX_SESSION_ID", usage.get().unavailableReason());
        assertEquals("zhipu", usage.get().providerId());
        assertEquals("glm-5.2", usage.get().model());
        assertEquals(AcpxProviderMetadata.BILLING_SUBSCRIPTION_TOKENS, usage.get().billingMode());
        assertEquals(AcpxProviderMetadata.SOURCE_CLAUDE_SETTINGS_ENV, usage.get().providerMetadataSource());
    }
}
