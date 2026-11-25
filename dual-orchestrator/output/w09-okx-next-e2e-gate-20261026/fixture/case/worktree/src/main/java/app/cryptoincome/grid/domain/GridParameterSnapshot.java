package app.cryptoincome.grid.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/** OKX 在 observedAt 时刻返回的网格候选参数事实；其展示年化不是本系统的收益结论。 */
public record GridParameterSnapshot(
        String exchange,
        String instrumentId,
        GridDuration duration,
        GridType gridType,
        BigDecimal lowerPrice,
        BigDecimal upperPrice,
        int gridCount,
        BigDecimal annualizedRate,
        BigDecimal minimumPerGridProfitRate,
        BigDecimal maximumPerGridProfitRate,
        BigDecimal minimumInvestment,
        String investmentCurrency,
        String sourceEndpoint,
        String sourcePayload,
        String payloadHash,
        Instant observedAt
) {
    public GridParameterSnapshot {
        requireText(exchange, "exchange");
        requireText(instrumentId, "instrumentId");
        Objects.requireNonNull(duration, "duration");
        Objects.requireNonNull(gridType, "gridType");
        requirePositive(lowerPrice, "lowerPrice");
        requirePositive(upperPrice, "upperPrice");
        if (upperPrice.compareTo(lowerPrice) <= 0) throw new IllegalArgumentException("upperPrice 必须大于 lowerPrice");
        if (gridCount < 2 || gridCount > 500) throw new IllegalArgumentException("gridCount 必须在 2 到 500 之间");
        requireNonNegative(annualizedRate, "annualizedRate");
        requireNonNegative(minimumPerGridProfitRate, "minimumPerGridProfitRate");
        requireNonNegative(maximumPerGridProfitRate, "maximumPerGridProfitRate");
        if (maximumPerGridProfitRate.compareTo(minimumPerGridProfitRate) < 0) {
            throw new IllegalArgumentException("每格最大收益率不能小于最小收益率");
        }
        requireNonNegative(minimumInvestment, "minimumInvestment");
        requireText(investmentCurrency, "investmentCurrency");
        requireText(sourceEndpoint, "sourceEndpoint");
        requireText(sourcePayload, "sourcePayload");
        if (payloadHash == null || !payloadHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("payloadHash 必须是 64 位小写十六进制 SHA-256");
        }
        Objects.requireNonNull(observedAt, "observedAt");
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " 不能为空");
    }

    private static void requirePositive(BigDecimal value, String name) {
        if (value == null || value.signum() <= 0) throw new IllegalArgumentException(name + " 必须大于 0");
    }

    private static void requireNonNegative(BigDecimal value, String name) {
        if (value == null || value.signum() < 0) throw new IllegalArgumentException(name + " 不能为负数");
    }
}
