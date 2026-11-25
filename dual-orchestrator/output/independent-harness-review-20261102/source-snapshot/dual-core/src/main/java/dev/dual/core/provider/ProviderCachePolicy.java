package dev.dual.core.provider;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class ProviderCachePolicy {
    private static final BigDecimal CHEAP_RATIO = new BigDecimal("0.20");
    private static final BigDecimal EXPENSIVE_RATIO = new BigDecimal("0.50");

    private ProviderCachePolicy() {
    }

    public static Decision decide(BigDecimal inputCacheHitPer1M, BigDecimal inputCacheMissPer1M) {
        if (inputCacheHitPer1M == null || inputCacheMissPer1M == null || inputCacheMissPer1M.signum() <= 0) {
            return new Decision("measure_only", "", "pricing missing");
        }
        var ratio = inputCacheHitPer1M.divide(inputCacheMissPer1M, 6, RoundingMode.HALF_UP);
        if (ratio.compareTo(CHEAP_RATIO) <= 0) {
            return new Decision("preserve_session", ratio.toPlainString(), "cache hit is cheap relative to cache miss");
        }
        if (ratio.compareTo(EXPENSIVE_RATIO) >= 0) {
            return new Decision("consider_compaction", ratio.toPlainString(), "cache hit is expensive relative to cache miss");
        }
        return new Decision("measure_only", ratio.toPlainString(), "cache hit economics are moderate");
    }

    public record Decision(String recommendation, String cacheHitToMissRatio, String reason) {
    }
}
