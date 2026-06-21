package dev.dual.adapter.acpx;

import java.math.BigDecimal;

record AcpxUsage(
        long inputTokens,
        long outputTokens,
        long cachedReadTokens,
        long cachedWriteTokens,
        long totalTokens,
        BigDecimal costAmount,
        String costCurrency,
        String source
) {
}
