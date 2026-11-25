package dev.dual.adapter.acpx;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionCompactionSignalTest {
    @Test
    void usesAgentCachedReadWhenByAgentIsPresent() {
        var signal = SessionCompactionSignal.from(Map.of(
                "tokenMetrics", Map.of(
                        "total", Map.of("cachedReadTokens", 1_500_000),
                        "byAgent", Map.of(
                                "claude", Map.of("cachedReadTokens", 100_000),
                                "codex", Map.of("cachedReadTokens", 1_400_000)
                        )
                )
        ));

        assertTrue(signal.available());
        assertEquals(100_000, signal.cachedReadTokensFor("claude"));
        assertEquals(1_400_000, signal.cachedReadTokensFor("codex"));
    }

    @Test
    void doesNotFallbackToTotalWhenRequestedAgentIsMissingFromByAgent() {
        var signal = SessionCompactionSignal.from(Map.of(
                "tokenMetrics", Map.of(
                        "total", Map.of("cachedReadTokens", 1_500_000),
                        "byAgent", Map.of("codex", Map.of("cachedReadTokens", 1_500_000))
                )
        ));

        assertTrue(signal.available());
        assertEquals(0, signal.cachedReadTokensFor("claude"));
    }

    @Test
    void fallsBackToTotalForLegacyMetricsWithoutByAgent() {
        var signal = SessionCompactionSignal.from(Map.of(
                "tokenMetrics", Map.of("total", Map.of("cachedReadTokens", 700_000))
        ));

        assertTrue(signal.available());
        assertEquals(700_000, signal.cachedReadTokensFor("claude"));
    }

    @Test
    void unavailableWhenNoTokenMetricsExist() {
        var signal = SessionCompactionSignal.from(Map.of());

        assertFalse(signal.available());
        assertEquals(0, signal.cachedReadTokensFor("claude"));
    }
}
