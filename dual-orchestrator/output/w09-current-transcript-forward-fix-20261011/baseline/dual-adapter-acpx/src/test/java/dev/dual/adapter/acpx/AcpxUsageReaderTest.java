package dev.dual.adapter.acpx;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcpxUsageReaderTest {
    @TempDir
    Path tempDir;

    @Test
    void primaryUsageCannotBorrowCompletedCorrectionWhenWireTrimsTrailingNewline() throws Exception {
        var sessions = tempDir.resolve("paired");
        Files.createDirectories(sessions);
        var id = "11111111-1111-4111-8111-111111111111";
        var stderr = tempDir.resolve("paired.stderr");
        var prompt = tempDir.resolve("primary.md");
        Files.writeString(stderr, "[acpx] session paired (" + id + ")\n");
        Files.writeString(prompt, "primary\n");
        Files.writeString(sessions.resolve(id + ".stream.ndjson"), """
                {"jsonrpc":"2.0","id":3,"method":"session/prompt","params":{"sessionId":"native-session","prompt":[{"type":"text","text":"primary"}]}}
                {"jsonrpc":"2.0","id":3,"result":{"stopReason":"end_turn","usage":{"inputTokens":17904,"outputTokens":14305,"cachedReadTokens":4736,"totalTokens":36945}}}
                {"jsonrpc":"2.0","id":4,"method":"session/prompt","params":{"sessionId":"native-session","prompt":[{"type":"text","text":"correction"}]}}
                {"jsonrpc":"2.0","id":4,"result":{"stopReason":"end_turn","usage":{"inputTokens":3950,"outputTokens":4806,"cachedReadTokens":17792,"totalTokens":26548}}}
                """);
        var reader = new AcpxUsageReader(sessions, 1, 0);
        var usage = reader.read("claude", new AcpxPromptCompletion("3", "end_turn", "answer", "native-session"), stderr).orElseThrow();
        assertEquals(36945, usage.totalTokens(), "the primary occurrence owns request 3, never request 4");
        var correction = reader.read("claude", new AcpxPromptCompletion("4", "end_turn", "answer", "native-session"), stderr).orElseThrow();
        assertEquals(26548, correction.totalTokens());
        assertEquals(63493, AcpxUsage.aggregatePromptOccurrences(usage, correction).totalTokens());
        assertNull(usage.costAmount(), "tokens do not manufacture a monetary observation");
    }

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
                {"jsonrpc":"2.0","id":1,"method":"session/prompt","params":{"sessionId":"acp-test","prompt":[{"type":"text","text":"other prompt"}]}}
                {"jsonrpc":"2.0","method":"session/update","params":{"sessionId":"acp-test","update":{"sessionUpdate":"usage_update","used":11,"size":200000,"cost":{"amount":0.10,"currency":"USD"}}}}
                {"jsonrpc":"2.0","id":1,"result":{"usage":{"inputTokens":10,"outputTokens":1,"cachedReadTokens":0,"cachedWriteTokens":0,"totalTokens":11}}}
                {"jsonrpc":"2.0","id":2,"method":"session/prompt","params":{"sessionId":"acp-test","prompt":[{"type":"text","text":"real prompt"}]}}
                {"jsonrpc":"2.0","method":"session/update","params":{"sessionId":"acp-test","update":{"sessionUpdate":"usage_update","used":110,"size":200000,"cost":{"amount":0.25,"currency":"USD"}}}}
                {"jsonrpc":"2.0","id":2,"result":{"usage":{"inputTokens":100,"outputTokens":5,"cachedReadTokens":1000,"cachedWriteTokens":2,"totalTokens":1107}}}
                """);

        var usage = new AcpxUsageReader(sessionsDir).read("claude", new AcpxPromptCompletion("2", "end_turn", "answer", "acp-test"), stderrPath);

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
        assertEquals(AcpxUsage.ATTR_EXACT_REQUEST_MATCH, usage.get().attribution());
        assertEquals("0.25", usage.get().sessionCostAmount().toPlainString());
        assertEquals("USD", usage.get().sessionCostCurrency());
        assertEquals("zhipu", usage.get().providerId());
        assertEquals("glm-5.2", usage.get().model());
        assertEquals(AcpxProviderMetadata.BILLING_SUBSCRIPTION_TOKENS, usage.get().billingMode());
        assertEquals(AcpxProviderMetadata.SOURCE_ACPX_SESSION_JSON, usage.get().providerMetadataSource());
    }

    @Test
    void attributesByCompletionIdentityWhenPromptTextIsRewrittenByAdapter() throws Exception {
        var sessionsDir = tempDir.resolve("sessions-fallback");
        var sessionId = "dd0caac4-0c68-423a-a448-60a58353ad7c";
        Files.createDirectories(sessionsDir);
        var promptPath = tempDir.resolve("prompt-fallback.md");
        var stderrPath = tempDir.resolve("stderr-fallback.log");
        Files.writeString(promptPath, "real prompt");
        Files.writeString(stderrPath, "[acpx] session claude-main (" + sessionId + ") · agent connected\n");
        Files.writeString(sessionsDir.resolve(sessionId + ".stream.ndjson"), """
                {"jsonrpc":"2.0","id":1,"method":"session/prompt","params":{"sessionId":"acp-test","prompt":[{"type":"text","text":"older prompt"}]}}
                {"jsonrpc":"2.0","method":"session/update","params":{"sessionId":"acp-test","update":{"sessionUpdate":"usage_update","used":11,"size":1000000,"cost":{"amount":1.00,"currency":"USD"}}}}
                {"jsonrpc":"2.0","id":1,"result":{"usage":{"inputTokens":10,"outputTokens":1,"cachedReadTokens":0,"cachedWriteTokens":0,"totalTokens":11}}}
                {"jsonrpc":"2.0","id":2,"method":"session/prompt","params":{"sessionId":"acp-test","prompt":[{"type":"text","text":"adapter wrapped real prompt"}]}}
                {"jsonrpc":"2.0","method":"session/update","params":{"sessionId":"acp-test","update":{"sessionUpdate":"usage_update","used":252332,"size":1000000,"cost":{"amount":1.22,"currency":"USD"}}}}
                {"jsonrpc":"2.0","id":2,"result":{"usage":{"inputTokens":6799,"outputTokens":2653,"cachedReadTokens":242880,"cachedWriteTokens":0,"totalTokens":252332}}}
                """);

        var usage = new AcpxUsageReader(sessionsDir).read("claude", new AcpxPromptCompletion("2", "end_turn", "answer", "acp-test"), stderrPath);

        assertTrue(usage.isPresent());
        assertEquals(6799, usage.get().inputTokens());
        assertEquals(2653, usage.get().outputTokens());
        assertEquals(242880, usage.get().cachedReadTokens());
        assertEquals(252332, usage.get().totalTokens());
        assertEquals("0.22", usage.get().costAmount().toPlainString());
        assertEquals("1.22", usage.get().sessionCostAmount().toPlainString());
        assertEquals(AcpxUsage.PROVIDER_RUN_USAGE, usage.get().measurementType());
        // Text does not identify the occurrence; the current completion session/request does.
        assertEquals(AcpxUsage.ATTR_EXACT_REQUEST_MATCH, usage.get().attribution());
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
                {"jsonrpc":"2.0","id":1,"method":"session/prompt","params":{"sessionId":"acp-test","prompt":[{"type":"text","text":"older prompt"}]}}
                {"jsonrpc":"2.0","id":1,"result":{"usage":{"inputTokens":10,"outputTokens":1,"cachedReadTokens":0,"cachedWriteTokens":0,"totalTokens":11}}}
                {"jsonrpc":"2.0","id":2,"method":"session/prompt","params":{"sessionId":"acp-test","prompt":[{"type":"text","text":"real prompt"}]}}
                {"jsonrpc":"2.0","method":"session/update","params":{"sessionId":"acp-test","update":{"sessionUpdate":"usage_update","used":252332,"size":1000000,"cost":{"amount":1.22,"currency":"USD"}}}}
                {"jsonrpc":"2.0","id":2,"result":{"usage":{"inputTokens":6799,"outputTokens":2653,"cachedReadTokens":242880,"cachedWriteTokens":0,"totalTokens":252332}}}
                """);

        var usage = new AcpxUsageReader(sessionsDir).read("claude", new AcpxPromptCompletion("2", "end_turn", "answer", "acp-test"), stderrPath);

        assertTrue(usage.isPresent());
        assertNull(usage.get().costAmount());
        assertEquals("1.22", usage.get().sessionCostAmount().toPlainString());
        assertEquals(AcpxUsage.ATTR_EXACT_REQUEST_MATCH, usage.get().attribution());
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
                {"jsonrpc":"2.0","id":2,"method":"session/prompt","params":{"sessionId":"acp-test","prompt":[{"type":"text","text":"adapter wrapped real prompt"}]}}
                {"jsonrpc":"2.0","method":"session/update","params":{"sessionId":"acp-test","update":{"sessionUpdate":"usage_update","used":99999,"size":1000000}}}
                {"jsonrpc":"2.0","id":2,"result":{"usage":{"inputTokens":6799,"outputTokens":2653,"cachedReadTokens":242880,"cachedWriteTokens":0,"totalTokens":252332}}}
                """);

        var usage = new AcpxUsageReader(sessionsDir, 1, 0).read("claude", new AcpxPromptCompletion("2", "end_turn", "answer", "acp-test"), stderrPath);

        assertTrue(usage.isPresent());
        // 有 result.usage 就必须用 provider run usage，不能被 usage_update snapshot 短路。
        assertEquals(AcpxUsage.PROVIDER_RUN_USAGE, usage.get().measurementType());
        assertEquals(6799, usage.get().inputTokens());
        assertEquals(AcpxUsage.ATTR_EXACT_REQUEST_MATCH, usage.get().attribution());
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
                {"jsonrpc":"2.0","id":2,"method":"session/prompt","params":{"sessionId":"acp-test","prompt":[{"type":"text","text":"adapter wrapped real prompt"}]}}
                {"jsonrpc":"2.0","method":"session/update","params":{"sessionId":"acp-test","update":{"sessionUpdate":"usage_update","used":99999,"size":1000000}}}
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
        var usage = new AcpxUsageReader(sessionsDir, 10, 30).read("claude", new AcpxPromptCompletion("2", "end_turn", "answer", "acp-test"), stderrPath);
        appender.join();

        assertTrue(usage.isPresent());
        assertEquals(AcpxUsage.PROVIDER_RUN_USAGE, usage.get().measurementType());
        assertEquals(6799, usage.get().inputTokens());
        assertEquals(AcpxUsage.ATTR_EXACT_REQUEST_MATCH, usage.get().attribution());
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
                {"jsonrpc":"2.0","id":1,"method":"session/prompt","params":{"sessionId":"acp-test","prompt":[{"type":"text","text":"adapter wrapped real prompt"}]}}
                {"jsonrpc":"2.0","method":"session/update","params":{"sessionId":"acp-test","update":{"sessionUpdate":"usage_update","used":78353,"size":258400}}}
                {"jsonrpc":"2.0","result":{"stopReason":"end_turn"},"id":1}
                """);

        // 单次重试即可：fixture 里没有 provider result.usage 可等，应直接回落到 session snapshot。
        var usage = new AcpxUsageReader(sessionsDir, 1, 0).read("codex", new AcpxPromptCompletion("1", "end_turn", "answer", "acp-test"), stderrPath);

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
                .read("claude", new AcpxPromptCompletion("2", "end_turn", "answer", "acp-test"), stderrPath);

        assertTrue(usage.isPresent());
        assertEquals(AcpxUsage.UNAVAILABLE, usage.get().measurementType());
        assertEquals("NO_ACPX_SESSION_ID", usage.get().unavailableReason());
        assertEquals("zhipu", usage.get().providerId());
        assertEquals("glm-5.2", usage.get().model());
        assertEquals(AcpxProviderMetadata.BILLING_SUBSCRIPTION_TOKENS, usage.get().billingMode());
        assertEquals(AcpxProviderMetadata.SOURCE_CLAUDE_SETTINGS_ENV, usage.get().providerMetadataSource());
    }

    @Test
    void codexRunWithoutSessionMetadataStaysUnknownAndDoesNotInheritClaudeConfig() throws Exception {
        var promptPath = tempDir.resolve("prompt-codex-identity.md");
        var stderrPath = tempDir.resolve("stderr-codex-identity.log");
        Files.writeString(promptPath, "real prompt");
        Files.writeString(stderrPath, ""); // no acpx session id resolvable

        // No explicit fallback: Codex must not fall back to Claude settings/env identity.
        // When no current-agent session metadata exists, the result stays absent (unknown)
        // rather than being projected through AcpxProviderMetadata.runtimeDefault().
        var usage = new AcpxUsageReader(tempDir.resolve("sessions-codex-identity"), 1, 0)
                .read("codex", new AcpxPromptCompletion("1", "end_turn", "answer", "acp-test"), stderrPath);

        assertFalse(usage.isPresent(),
                "Codex without current-agent session metadata must stay unknown identity");
    }
    @Test
    void unrelatedRequestAndMissingCompletionCannotBorrowSessionUsage() throws Exception {
        var unrelated = readFixture(promptFrame("1", "native") + resultFrame("1", 50),
                new AcpxPromptCompletion("2", "end_turn", "answer", "native"));
        assertEquals(AcpxUsage.UNAVAILABLE, unrelated.measurementType());
        assertEquals("PROMPT_USAGE_UNAVAILABLE", unrelated.unavailableReason());
        var missing = readFixture(promptFrame("1", "native") + resultFrame("1", 50), AcpxPromptCompletion.EMPTY);
        assertEquals(AcpxUsage.UNAVAILABLE, missing.measurementType());
        assertEquals("NO_PROMPT_COMPLETION_IDENTITY", missing.unavailableReason());
    }

    @Test
    void reusedIdInAnotherAcpSessionIsNotTheCurrentRequest() throws Exception {
        var usage = readFixture(promptFrame("1", "old") + resultFrame("1", 91)
                + promptFrame("1", "native") + resultFrame("1", 50),
                new AcpxPromptCompletion("1", "end_turn", "answer", "native"));
        assertEquals(50, usage.totalTokens());
        assertEquals(AcpxUsage.ATTR_EXACT_REQUEST_MATCH, usage.attribution());
    }

    @Test
    void repeatedIdInSameSessionIsUnattributableRatherThanLatest() throws Exception {
        var usage = readFixture(promptFrame("1", "native") + resultFrame("1", 91)
                + promptFrame("1", "native") + resultFrame("1", 50),
                new AcpxPromptCompletion("1", "end_turn", "answer", "native"));
        assertEquals(AcpxUsage.UNAVAILABLE, usage.measurementType());
        assertEquals("AMBIGUOUS_PROMPT_REQUEST_IDENTITY", usage.unavailableReason());
        assertEquals(0, usage.totalTokens(), "unavailable is not a measured zero");
    }

    @Test
    void duplicateCompletionDoesNotOverwriteFirstUsage() throws Exception {
        var usage = readFixture(promptFrame("1", "native") + resultFrame("1", 91) + resultFrame("1", 50),
                new AcpxPromptCompletion("1", "end_turn", "answer", "native"));
        assertEquals(AcpxUsage.UNAVAILABLE, usage.measurementType());
        assertEquals("AMBIGUOUS_PROMPT_REQUEST_IDENTITY", usage.unavailableReason());
    }

    @Test
    void partialOccurrenceCannotBecomeCompleteMultiPromptTotal() throws Exception {
        var measured = readFixture(promptFrame("1", "native") + resultFrame("1", 50),
                new AcpxPromptCompletion("1", "end_turn", "answer", "native"));
        var missing = AcpxUsage.unavailable("PROMPT_USAGE_UNAVAILABLE");
        for (var sum : java.util.List.of(AcpxUsage.aggregatePromptOccurrences(measured, missing),
                AcpxUsage.aggregatePromptOccurrences(missing, measured),
                AcpxUsage.aggregatePromptOccurrences(null, measured))) {
            assertEquals(AcpxUsage.UNAVAILABLE, sum.measurementType());
            assertEquals("MULTI_PROMPT_USAGE_REQUIRES_OCCURRENCE_SUM", sum.unavailableReason());
        }
    }

    private AcpxUsage readFixture(String stream, AcpxPromptCompletion completion) throws Exception {
        var sessions = tempDir.resolve("boundary-sessions");
        Files.createDirectories(sessions);
        var id = "22222222-2222-4222-8222-222222222222";
        var stderr = tempDir.resolve("boundary.stderr");
        Files.writeString(stderr, "[acpx] session boundary (" + id + ")\n");
        Files.writeString(sessions.resolve(id + ".stream.ndjson"), stream);
        return new AcpxUsageReader(sessions, 1, 0).read("codex", completion, stderr).orElseThrow();
    }

    private static String promptFrame(String id, String session) {
        return "{\"jsonrpc\":\"2.0\",\"id\":\"" + id + "\",\"method\":\"session/prompt\",\"params\":{\"sessionId\":\""
                + session + "\",\"prompt\":[{\"type\":\"text\",\"text\":\"same text\"}]}}\n";
    }

    private static String resultFrame(String id, long total) {
        return "{\"jsonrpc\":\"2.0\",\"id\":\"" + id + "\",\"result\":{\"usage\":{\"inputTokens\":" + total
                + ",\"outputTokens\":0,\"totalTokens\":" + total + "}}}\n";
    }

}
