package dev.dual.core.provider;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProviderCachePolicyTest {
    @Test
    void cheapCacheHitPreservesSession() {
        var decision = ProviderCachePolicy.decide(new BigDecimal("0.003625"), new BigDecimal("0.435"));

        assertEquals("preserve_session", decision.recommendation());
    }

    @Test
    void expensiveCacheHitConsidersCompaction() {
        var decision = ProviderCachePolicy.decide(new BigDecimal("0.75"), BigDecimal.ONE);

        assertEquals("consider_compaction", decision.recommendation());
    }

    @Test
    void missingPriceOnlyMeasures() {
        var decision = ProviderCachePolicy.decide(BigDecimal.ZERO, BigDecimal.ZERO);

        assertEquals("measure_only", decision.recommendation());
    }
}
