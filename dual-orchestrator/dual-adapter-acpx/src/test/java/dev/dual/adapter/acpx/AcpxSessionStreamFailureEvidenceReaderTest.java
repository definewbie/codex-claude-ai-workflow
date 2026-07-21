package dev.dual.adapter.acpx;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cannot-bypass tests for the structured session-stream evidence reader.
 *
 * <p>These prove the false rate-limit classification is gone: agent prose,
 * usage {@code _meta.quota}, {@code is_error=false} tool results, recovered
 * {@code session/resume} errors, and malformed NDJSON never become failure
 * evidence; only real top-level JSON-RPC errors do, and only explicit
 * 429/rate-limit errors map to {@code RECOVERABLE_RATE_LIMIT}.
 */
class AcpxSessionStreamFailureEvidenceReaderTest {

    private static final String SESSION_ID = "019f7a29-2262-7f93-843f-bc38a61dcacb";
    private static final String STDERR = "[acpx] session codex-reviewer-ws (" + SESSION_ID + ") · /tmp/ws · agent needs reconnect";

    @TempDir
    Path tempDir;

    private AcpxStreamEvidence read(String ndjson) throws IOException {
        Files.writeString(tempDir.resolve(SESSION_ID + ".stream.ndjson"), ndjson);
        var reader = new AcpxSessionStreamFailureEvidenceReader(tempDir);
        return reader.readFromStderr(STDERR).orElse(AcpxStreamEvidence.EMPTY);
    }

    /** 1 + 3: the real false-positive shape — prose mentioning error/quota/rate limit, an
     * is_error=false tool result, and usage — but no top-level JSON-RPC error. */
    @Test
    void agentProseAndToolResultAndUsageAreNotFailureEvidence() throws IOException {
        var evidence = read("""
                {"jsonrpc":"2.0","method":"session/update","params":{"sessionId":"s","update":{"sessionUpdate":"tool_call_update","toolCallId":"exec-1","status":"completed","rawOutput":{"formatted_output":"# Review\\nThe API quota was not exhausted; is_error=false. No rate limit hit. tool_use_error was not raised."}}}}
                {"jsonrpc":"2.0","method":"session/update","params":{"sessionId":"s","update":{"sessionUpdate":"agent_message_chunk","content":{"type":"text","text":"Consider the quota and error handling for rate limit policy."}}}}
                {"jsonrpc":"2.0","method":"session/update","params":{"sessionId":"s","update":{"sessionUpdate":"usage_update","used":100,"size":200000,"cost":{"amount":0,"currency":"USD"}}}}
                """);
        assertTrue(evidence.isEmpty(), "prose/tool-result/usage must not produce failure evidence");
    }

    /** 2: a success result carrying _meta.quota is not failure evidence. */
    @Test
    void usageMetaQuotaInSuccessResultIsIgnored() throws IOException {
        var evidence = read("""
                {"jsonrpc":"2.0","id":3,"result":{"stopReason":"completed","usage":{"totalTokens":28336},"_meta":{"quota":{"token_count":{"totalTokens":28336}}}}}
                """);
        assertTrue(evidence.isEmpty());
    }

    /** 4: a session/resume error recovered by a later session/new success is dropped. */
    @Test
    void sessionResumeErrorRecoveredBySessionNewIsDropped() throws IOException {
        var evidence = read("""
                {"jsonrpc":"2.0","method":"session/resume","id":1,"params":{"sessionId":"old"}}
                {"jsonrpc":"2.0","id":1,"error":{"code":-32002,"message":"session not found"}}
                {"jsonrpc":"2.0","method":"session/new","id":2,"params":{}}
                {"jsonrpc":"2.0","id":2,"result":{"sessionId":"fresh"}}
                {"jsonrpc":"2.0","method":"session/prompt","id":3,"params":{}}
                {"jsonrpc":"2.0","id":3,"result":{"stopReason":"completed"}}
                """);
        assertTrue(evidence.isEmpty(), "session/resume error superseded by session/new must not be evidence");
    }

    /** A session/resume error with NO recovery remains a real error. */
    @Test
    void unrecoveredSessionResumeErrorIsKept() throws IOException {
        var evidence = read("""
                {"jsonrpc":"2.0","method":"session/resume","id":1,"params":{"sessionId":"old"}}
                {"jsonrpc":"2.0","id":1,"error":{"code":-32002,"message":"session not found"}}
                """);
        assertEquals(1, evidence.errors().size());
        assertEquals("session/resume", evidence.errors().get(0).method());
    }

    /** 5: a real top-level session/prompt 429 error is captured and its message reaches the catalog. */
    @Test
    void realRateLimitErrorIsCaptured() throws IOException {
        var evidence = read("""
                {"jsonrpc":"2.0","method":"session/prompt","id":3,"params":{}}
                {"jsonrpc":"2.0","id":3,"error":{"code":-32603,"message":"Request rejected (429): rate limit exceeded"}}
                """);
        assertEquals(1, evidence.errors().size());
        var error = evidence.errors().get(0);
        assertEquals("session/prompt", error.method());
        assertEquals(-32603, error.code());
        var catalog = new AcpxFailureSignalCatalog();
        var signal = catalog.match(new AcpxFailureEvidence("", evidence.normalizedErrorText(), 1)).orElseThrow();
        assertEquals(AcpxFailureReason.RATE_LIMIT, signal.reason());
    }

    /** 6: a non-rate-limit prompt error is captured but does NOT classify as RATE_LIMIT. */
    @Test
    void nonRateLimitPromptErrorKeepsItsTypedShape() throws IOException {
        var evidence = read("""
                {"jsonrpc":"2.0","method":"session/prompt","id":3,"params":{}}
                {"jsonrpc":"2.0","id":3,"error":{"code":-32603,"message":"Internal error: no rollout found"}}
                """);
        assertEquals(1, evidence.errors().size());
        assertEquals("session/prompt", evidence.errors().get(0).method());
        var catalog = new AcpxFailureSignalCatalog();
        var match = catalog.match(new AcpxFailureEvidence("", evidence.normalizedErrorText(), 1));
        assertTrue(match.isEmpty() || match.get().reason() != AcpxFailureReason.RATE_LIMIT,
                "internal error must not classify as rate limit");
    }

    /** 5e: malformed / truncated NDJSON lines are ignored. */
    @Test
    void malformedAndTruncatedLinesAreIgnored() throws IOException {
        var evidence = read("""
                not json at all
                {"jsonrpc":"2.0","id":3,"error":{"code":-32603,"message":"rate lim
                """);
        assertTrue(evidence.isEmpty());
    }

    /** A request id correlates the response to its method even when the response omits method. */
    @Test
    void correlatesMethodByRequestId() throws IOException {
        var evidence = read("""
                {"jsonrpc":"2.0","method":"session/prompt","id":7,"params":{}}
                {"jsonrpc":"2.0","id":7,"error":{"code":-32603,"message":"Request rejected (429)"}}
                """);
        assertEquals("session/prompt", evidence.errors().get(0).method());
    }

    /** 7 (post-hoc path): the false-positive stream through the full classifier yields no RATE_LIMIT. */
    @Test
    void falsePositiveStreamDoesNotClassifyAsRateLimit() throws IOException {
        Files.writeString(tempDir.resolve(SESSION_ID + ".stream.ndjson"), """
                {"jsonrpc":"2.0","method":"session/update","params":{"sessionId":"s","update":{"sessionUpdate":"tool_call_update","toolCallId":"exec-1","status":"completed","rawOutput":{"formatted_output":"The quota and rate limit policy had is_error=false."}}}}
                {"jsonrpc":"2.0","method":"session/update","params":{"sessionId":"s","update":{"sessionUpdate":"usage_update","used":100,"size":200000,"cost":{"amount":0,"currency":"USD"}}}}
                """);
        var stderrPath = tempDir.resolve("stderr.log");
        Files.writeString(stderrPath, STDERR);
        var stdoutPath = tempDir.resolve("stdout.log");
        Files.writeString(stdoutPath, "# Product review discussing quota and error handling, no runtime markers");
        var classifier = new AcpxFailureClassifier();
        var failure = classifier.classify(stdoutPath, stderrPath, 1);
        assertFalse(failure.reason().startsWith("RECOVERABLE_RATE_LIMIT"),
                "false-positive stream must not classify as RATE_LIMIT; got " + failure.reason());
    }

    /** P1-1: a real Codex ACP model-mismatch error nests the provider text in
     * error.data.message (top-level message is just "Internal error"); the nested
     * text must reach the catalog so it classifies as model mismatch, not generic. */
    @Test
    void realModelMismatchInErrorMessageDataClassifiesAsModelMismatch() throws IOException {
        var evidence = read("""
                {"jsonrpc":"2.0","method":"session/prompt","id":3,"params":{}}
                {"jsonrpc":"2.0","id":3,"error":{"code":-32603,"message":"Internal error","data":{"message":"status=400 invalid_request_error: The supported API model names are deepseek-v4-pro or deepseek-v4-flash, but you passed glm-5.2."}}}
                """);
        assertEquals(1, evidence.errors().size());
        assertEquals("session/prompt", evidence.errors().get(0).method());
        assertTrue(evidence.errors().get(0).dataMessage().contains("supported API model names"));
        var signal = new AcpxFailureSignalCatalog()
                .match(new AcpxFailureEvidence("", evidence.normalizedErrorText(), 1))
                .orElseThrow();
        assertEquals(AcpxFailureReason.AGENT_MODEL_MISMATCH, signal.reason());
    }

    /** P1-1: a real 429 nested in error.data.message classifies as RATE_LIMIT. */
    @Test
    void realRateLimitInErrorMessageDataClassifiesAsRateLimit() throws IOException {
        var evidence = read("""
                {"jsonrpc":"2.0","method":"session/prompt","id":3,"params":{}}
                {"jsonrpc":"2.0","id":3,"error":{"code":-32603,"message":"Internal error","data":{"message":"status=429 rate_limit_exceeded: Request rejected (429) too many requests"}}}
                """);
        assertEquals(1, evidence.errors().size());
        var signal = new AcpxFailureSignalCatalog()
                .match(new AcpxFailureEvidence("", evidence.normalizedErrorText(), 1))
                .orElseThrow();
        assertEquals(AcpxFailureReason.RATE_LIMIT, signal.reason());
    }

    /** P1-1: error.data.details is preserved (real shape: "no rollout found for thread id ..."). */
    @Test
    void errorDataDetailsArePreserved() throws IOException {
        var evidence = read("""
                {"jsonrpc":"2.0","method":"session/resume","id":1,"params":{"sessionId":"x"}}
                {"jsonrpc":"2.0","id":1,"error":{"code":-32603,"message":"Internal error","data":{"details":"no rollout found for thread id 019f7a29-2262-7f93-843f-bc38a61dcacb"}}}
                """);
        assertEquals(1, evidence.errors().size());
        var error = evidence.errors().get(0);
        assertEquals("no rollout found for thread id 019f7a29-2262-7f93-843f-bc38a61dcacb", error.dataDetails());
        assertTrue(evidence.normalizedErrorText().contains("no rollout found"));
    }

    /** P1-2: order-based suppression — a session/new success that PRECEDES a
     * session/resume error does NOT suppress it (recovery must come after). */
    @Test
    void sessionNewBeforeResumeErrorDoesNotSuppressIt() throws IOException {
        var evidence = read("""
                {"jsonrpc":"2.0","method":"session/new","id":2,"params":{}}
                {"jsonrpc":"2.0","id":2,"result":{"sessionId":"fresh"}}
                {"jsonrpc":"2.0","method":"session/resume","id":1,"params":{"sessionId":"old"}}
                {"jsonrpc":"2.0","id":1,"error":{"code":-32002,"message":"session not found"}}
                """);
        assertEquals(1, evidence.errors().size(),
                "a session/resume error after the session/new success is a new failure and must be kept");
        assertEquals("session/resume", evidence.errors().get(0).method());
    }
}
