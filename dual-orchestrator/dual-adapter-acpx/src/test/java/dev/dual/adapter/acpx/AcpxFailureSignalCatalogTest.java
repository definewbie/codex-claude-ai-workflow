package dev.dual.adapter.acpx;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcpxFailureSignalCatalogTest {
    private final AcpxFailureSignalCatalog catalog = new AcpxFailureSignalCatalog();

    @Test
    void matchesRateLimitSignalsAcrossStdoutAndLanguages() {
        var evidence = new AcpxFailureEvidence(
                "[error] RUNTIME: API Error: Request rejected (429) 已达到 5 小时的使用上限",
                "[acpx] session claude-main · agent connected",
                1
        );

        var signal = catalog.match(evidence).orElseThrow();

        assertEquals("rate_limit", signal.id());
        assertEquals("RECOVERABLE_RATE_LIMIT", signal.reason().code());
        assertTrue(signal.recoverable());
    }

    @Test
    void matchesProviderOverloadBeforeReconnect() {
        var evidence = new AcpxFailureEvidence(
                "",
                """
                [acpx] session claude-main · agent needs reconnect
                Internal error: API Error: 529 [1305][该模型当前访问量过大，请您稍后再试]
                """,
                1
        );

        var signal = catalog.match(evidence).orElseThrow();

        assertEquals("model_overloaded", signal.id());
        assertEquals("RECOVERABLE_AGENT_OVERLOADED", signal.reason().code());
        assertTrue(signal.recoverable());
    }

    @Test
    void matchesModelMismatchBeforeReconnectOrGenericInvalidRequest() {
        var evidence = new AcpxFailureEvidence(
                "",
                """
                [acpx] session claude-main · agent needs reconnect
                Internal error: API Error: 400 {"error":{"message":"The supported API model names are deepseek-v4-pro or deepseek-v4-flash, but you passed glm-5.2.","type":"invalid_request_error"}}
                """,
                1
        );

        var signal = catalog.match(evidence).orElseThrow();

        assertEquals("model_mismatch", signal.id());
        assertEquals("RECOVERABLE_AGENT_MODEL_MISMATCH", signal.reason().code());
        assertTrue(signal.recoverable());
    }

    @Test
    void matchesAuthFailureBeforeReconnect() {
        var evidence = new AcpxFailureEvidence(
                """
                [client] session/new (running)
                Failed to authenticate. API Error: 401 Invalid authentication credentials
                [error] RUNTIME: Internal error: Failed to authenticate. API Error: 401 Invalid authentication credentials
                """,
                "[acpx] session claude-main · agent needs reconnect",
                1
        );

        var signal = catalog.match(evidence).orElseThrow();

        assertEquals("agent_auth_failed", signal.id());
        assertEquals("RECOVERABLE_AGENT_AUTH_FAILED", signal.reason().code());
        assertTrue(signal.recoverable());
    }

    @Test
    void choosesHighestPrioritySignalInsteadOfListOrder() {
        var catalog = new AcpxFailureSignalCatalog(List.of(
                new AcpxFailureSignal(
                        "agent_reconnect",
                        AcpxFailureReason.AGENT_RECONNECT,
                        10,
                        "reconnect",
                        true,
                        false,
                        List.of(AcpxFailureSignal.contains("agent needs reconnect"))
                ),
                new AcpxFailureSignal(
                        "model_mismatch",
                        AcpxFailureReason.AGENT_MODEL_MISMATCH,
                        100,
                        "model mismatch",
                        true,
                        false,
                        List.of(AcpxFailureSignal.contains("supported api model names"))
                )
        ));
        var evidence = new AcpxFailureEvidence(
                "",
                """
                [acpx] session claude-main · agent needs reconnect
                The supported API model names are deepseek-v4-pro or deepseek-v4-flash, but you passed glm-5.2.
                """,
                1
        );

        var signal = catalog.match(evidence).orElseThrow();

        assertEquals("model_mismatch", signal.id());
        assertEquals("RECOVERABLE_AGENT_MODEL_MISMATCH", signal.reason().code());
    }

    @Test
    void matchesReconnectSignalFromStderr() {
        var evidence = new AcpxFailureEvidence("", "[acpx] session claude-main · agent needs reconnect", 5);

        var signal = catalog.match(evidence).orElseThrow();

        assertEquals("agent_reconnect", signal.id());
        assertEquals("RECOVERABLE_AGENT_RECONNECT", signal.reason().code());
    }

    @Test
    void matchesNonInteractiveToolPermissionFailuresFromStdout() {
        var evidence = new AcpxFailureEvidence(
                """
                <tool_use_error>Cancelled: parallel tool call Bash(ls /repo)
                Tool permission request failed: Error: Tool use aborted
                """,
                "",
                4
        );

        var signal = catalog.match(evidence).orElseThrow();

        assertEquals("agent_permission_blocked", signal.id());
        assertEquals("RECOVERABLE_AGENT_PERMISSION_BLOCKED", signal.reason().code());
        assertTrue(signal.recoverable());
    }

    @Test
    void doesNotMatchUnknownExitText() {
        var evidence = new AcpxFailureEvidence("plain failure", "tool exited", 9);

        assertTrue(catalog.match(evidence).isEmpty());
    }
}
