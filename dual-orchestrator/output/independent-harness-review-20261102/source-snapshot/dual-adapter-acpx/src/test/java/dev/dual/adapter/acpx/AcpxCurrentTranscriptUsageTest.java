package dev.dual.adapter.acpx;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class AcpxCurrentTranscriptUsageTest {
    @TempDir Path dir;
    private static final String RECORD = "44444444-4444-4444-8444-444444444444";
    private static final String TOKENS = "{\"inputTokens\":10,\"outputTokens\":2,\"cachedReadTokens\":20,\"totalTokens\":32}";
    private String result(String id, String session, String stop, String usage) {
        return "{\"jsonrpc\":\"2.0\",\"id\":\"" + id + "\",\"result\":{\"sessionId\":\"" + session + "\",\"stopReason\":\"" + stop + "\",\"usage\":" + usage + "}}\n";
    }
    private String prompt(String session) {
        return "{\"id\":3,\"method\":\"session/prompt\",\"params\":{\"sessionId\":\"" + session + "\"}}\n";
    }
    private AcpxUsage read(String agent, String current, String history) throws Exception {
        Files.createDirectories(dir.resolve("sessions"));
        Files.writeString(dir.resolve("sessions/" + RECORD + ".json"),
                "{\"current_model_id\":\"" + (agent.equals("claude") ? "glm-5.3" : "gpt-5") + "\"}");
        Files.writeString(dir.resolve("sessions/" + RECORD + ".stream.ndjson"), history);
        Files.writeString(dir.resolve("current.log"), current);
        Files.writeString(dir.resolve("stderr.log"), "[acpx] session current (" + RECORD + ")");
        return new AcpxUsageReader(dir.resolve("sessions"), 1, 0).read(agent,
                new AcpxPromptCompletion("3", "end_turn", "answer", "s"),
                dir.resolve("current.log"), dir.resolve("stderr.log")).orElseThrow();
    }
    @Test void readableMultilineModelJsonIsNotABrokenProtocolFrame() throws Exception {
        var body = "[agent]\n{\n  \"result\": {\n    \"text\": \"model body\"\n  }\n}\n";
        for (var agent : new String[]{"claude", "codex"}) {
            var usage = read(agent, body + result("3", "", "end_turn", TOKENS), "");
            assertEquals(AcpxUsage.PROVIDER_RUN_USAGE, usage.measurementType());
            assertEquals(32, usage.totalTokens());
        }
    }
    @Test void malformedRpcShapesNeverBorrowCurrentOrHistoricalSuccess() throws Exception {
        var good = result("3", "", "end_turn", TOKENS);
        var history = prompt("s") + good;
        for (var agent : new String[]{"claude", "codex"}) {
            for (var bad : new String[]{"{\"jsonrpc\":\"2.0\",\"id\":3,\"result\":42}",
                    "{\"jsonrpc\":\"1.0\",\"id\":3,\"result\":{}}",
                    "{\"jsonrpc\":2.0,\"id\":3,\"result\":{}}",
                    "{\"id\":{},\"result\":{}}", "{\"id\":3,\"error\":false}",
                    "{\"id\":3,\"result\":{},\"error\":{}}", "{\"jsonrpc\":\"2.0\"}"}) {
                var usage = read(agent, bad + "\n" + good, history);
                assertEquals(AcpxUsage.UNAVAILABLE, usage.measurementType(), bad);
                assertEquals("INVALID_CURRENT_TRANSCRIPT", usage.unavailableReason(), bad);
            }
        }
    }
    @Test void modelJsonObjectWithoutProtocolIdentityIsReadableText() throws Exception {
        assertEquals(32, read("claude", "{\"result\":{\"summary\":\"model body\"}}\n"
                + result("3", "", "end_turn", TOKENS), "").totalTokens());
    }

    @Test void modelClauseIdsAreNotRpcIdentityInReadableStdout() throws Exception {
        var body = "[agent]\n{\n  \"clauses\": [\n"
                + "    {\"id\":\"AC-3\",\"text\":\"a real model clause\"}\n  ]\n}\n";
        var good = result("3", "", "end_turn", TOKENS);
        for (var agent : new String[]{"claude", "codex"}) {
            assertEquals(32, read(agent, body + good, "").totalTokens());
            assertEquals(32, read(agent, body, prompt("s") + good).totalTokens());
        }
    }

    @Test void readableBodyKeepsTheStrictSharedExitReachable() throws Exception {
        var history = prompt("s") + result("3", "", "end_turn", TOKENS);
        assertEquals(32, read("claude", "[agent]\n{\n}\n", history).totalTokens());
        assertEquals(AcpxUsage.UNAVAILABLE, read("claude", "[agent]\n{\n}\n", history + history).measurementType());
    }

    @Test void legalMissingResultSessionAndOptionalCacheFieldsWorkForBothClients() throws Exception {
        for (var agent : new String[]{"claude", "codex"}) {
            var usage = read(agent, result("3", "", "end_turn", TOKENS), "");
            assertEquals(32, usage.totalTokens());
            assertEquals("acpx_current_transcript", usage.source());
            assertEquals(AcpxUsage.PROVIDER_RUN_USAGE, usage.measurementType());
            assertEquals(AcpxUsage.ATTR_EXACT_REQUEST_MATCH, usage.attribution());
            assertEquals(0, usage.cachedWriteTokens());
            assertFalse(usage.cumulative());
            assertNull(usage.costAmount());
            assertEquals(agent.equals("claude") ? "zhipu" : "openai", usage.providerId());
        }
    }
    @Test void appendAndReinitializeCannotRevokeEarlierCurrentUsage() throws Exception {
        var current = result("3", "", "end_turn", TOKENS);
        var old = prompt("s") + current;
        var first = read("codex", current, old);
        var extended = old + "{\"id\":0,\"method\":\"initialize\",\"params\":{}}\n"
                + prompt("s") + result("3", "", "end_turn", "{\"inputTokens\":90,\"outputTokens\":10,\"totalTokens\":100}");
        var reread = read("codex", current, extended);
        assertEquals(first, reread);
        assertEquals(32, reread.totalTokens());
        var next = read("codex", result("3", "", "end_turn", "{\"inputTokens\":90,\"outputTokens\":10,\"totalTokens\":100}"), extended);
        assertEquals(100, next.totalTokens());
    }
    @Test void wrongRequestSessionAndStopNeverBorrowValidHistory() throws Exception {
        var history = prompt("s") + result("3", "", "end_turn", TOKENS);
        for (var current : new String[]{result("4", "", "end_turn", TOKENS),
                result("3", "wrong", "end_turn", TOKENS), result("3", "", "cancelled", TOKENS),
                prompt("wrong") + result("3", "", "end_turn", TOKENS)}) {
            assertEquals(AcpxUsage.UNAVAILABLE, read("codex", current, history).measurementType());
        }
    }
    @Test void duplicateAndErrorResultsNeverBorrowHistory() throws Exception {
        var good = result("3", "", "end_turn", TOKENS);
        var history = prompt("s") + good;
        for (var current : new String[]{good + good,
                "{\"id\":3,\"error\":{\"message\":\"failed\"}}\n" + good,
                "{\"jsonrpc\":\"2.0\",\"id\":3,\"result\":42}\n" + good}) {
            assertEquals(AcpxUsage.UNAVAILABLE, read("codex", current, history).measurementType());
        }
    }
    @Test void partialMalformedAndNegativeTokensRemainUnmeasured() throws Exception {
        for (var tokens : new String[]{"{}", "null", "{\"totalTokens\":32}",
                "{\"inputTokens\":10,\"outputTokens\":2.5,\"totalTokens\":32}",
                "{\"inputTokens\":10,\"outputTokens\":2,\"totalTokens\":-32}",
                "{\"inputTokens\":10,\"outputTokens\":2,\"totalTokens\":9223372036854775808}",
                "{\"inputTokens\":10,\"outputTokens\":2,\"cachedReadTokens\":null,\"totalTokens\":32}"}) {
            assertEquals(AcpxUsage.UNAVAILABLE, read("claude", result("3", "", "end_turn", tokens),
                    prompt("s") + result("3", "", "end_turn", TOKENS)).measurementType());
        }
    }
    @Test void sessionSnapshotIsNotCurrentRequestTotal() throws Exception {
        var update = "{\"method\":\"session/update\",\"params\":{\"sessionId\":\"s\",\"update\":{\"sessionUpdate\":\"usage_update\",\"used\":500,\"cost\":{\"amount\":1.2,\"currency\":\"USD\"}}}}\n";
        var usage = read("claude", update + result("3", "", "end_turn", TOKENS), "");
        assertEquals(32, usage.totalTokens());
        assertEquals(0, usage.contextUsedTokens());
        assertEquals(new java.math.BigDecimal("1.2"), usage.sessionCostAmount());
        assertNull(usage.costAmount());
        assertEquals(AcpxUsage.PROVIDER_RUN_USAGE, usage.measurementType());
    }
    @Test void delayedCurrentResultIsReadWithinExistingBoundedWait() throws Exception {
        read("codex", "", "");
        var writer = new Thread(() -> {
            try {
                Thread.sleep(60);
                Files.writeString(dir.resolve("current.log"), result("3", "", "end_turn", TOKENS));
            } catch (Exception failure) { throw new AssertionError(failure); }
        });
        writer.start();
        try {
            var usage = new AcpxUsageReader(dir.resolve("sessions"), 20, 20).read("codex",
                    new AcpxPromptCompletion("3", "end_turn", "answer", "s"),
                    dir.resolve("current.log"), dir.resolve("stderr.log")).orElseThrow();
            assertEquals(32, usage.totalTokens());
            assertEquals("acpx_current_transcript", usage.source());
        } finally { writer.join(); }
    }
    @Test void monetaryEnrichmentRequiresSameTokensAndUnambiguousHistory() throws Exception {
        var current = result("3", "", "end_turn", TOKENS);
        var update = "{\"method\":\"session/update\",\"params\":{\"sessionId\":\"s\",\"update\":{\"sessionUpdate\":\"usage_update\",\"used\":500,\"cost\":{\"amount\":1.2,\"currency\":\"USD\"}}}}\n";
        var history = prompt("s") + update + current;
        assertEquals(new java.math.BigDecimal("1.2"), read("claude", current, history).costAmount());
        assertNull(read("claude", current, history + history).costAmount());
        assertNull(read("claude", current, history.replace("\"totalTokens\":32", "\"totalTokens\":33")).costAmount());
    }
    @Test void emptyCurrentTranscriptKeepsStrictDelayedStreamExit() throws Exception {
        var history = prompt("s") + result("3", "", "end_turn", TOKENS);
        assertEquals(32, read("claude", "", history).totalTokens());
        assertEquals(AcpxUsage.UNAVAILABLE, read("claude", "", history + history).measurementType());
    }
}
