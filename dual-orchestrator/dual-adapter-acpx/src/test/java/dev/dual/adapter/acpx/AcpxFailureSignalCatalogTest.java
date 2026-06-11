package dev.dual.adapter.acpx;

import org.junit.jupiter.api.Test;

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
        assertEquals("RECOVERABLE_RATE_LIMIT", signal.reason());
        assertTrue(signal.recoverable());
    }

    @Test
    void matchesReconnectSignalFromStderr() {
        var evidence = new AcpxFailureEvidence("", "[acpx] session claude-main · agent needs reconnect", 5);

        var signal = catalog.match(evidence).orElseThrow();

        assertEquals("agent_reconnect", signal.id());
        assertEquals("RECOVERABLE_AGENT_RECONNECT", signal.reason());
    }

    @Test
    void doesNotMatchUnknownExitText() {
        var evidence = new AcpxFailureEvidence("plain failure", "tool exited", 9);

        assertTrue(catalog.match(evidence).isEmpty());
    }
}
