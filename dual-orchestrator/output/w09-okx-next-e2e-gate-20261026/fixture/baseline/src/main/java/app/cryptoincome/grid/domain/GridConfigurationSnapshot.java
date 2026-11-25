package app.cryptoincome.grid.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** 用户在 OKX 最终确认页看到并准备执行的配置；不同于公共 AI API 候选。 */
public record GridConfigurationSnapshot(
        String exchange,
        String instrumentId,
        GridConfigurationSource source,
        String recommendationHorizonLabel,
        BigDecimal lowerPrice,
        BigDecimal upperPrice,
        int gridCount,
        GridType gridType,
        Optional<BigDecimal> displayedAnnualizedRate,
        Optional<BigDecimal> displayedMinimumPerGridRate,
        Optional<BigDecimal> displayedMaximumPerGridRate,
        boolean profitReinvestmentEnabled,
        boolean autoEarnEnabled,
        boolean trailingUpEnabled,
        boolean trailingDownEnabled,
        Optional<BigDecimal> takeProfitPrice,
        Optional<BigDecimal> stopLossPrice,
        String sourceReference,
        Instant observedAt
) {
    public GridConfigurationSnapshot {
        if (exchange == null || exchange.isBlank()) throw new IllegalArgumentException("exchange 不能为空");
        if (instrumentId == null || instrumentId.isBlank()) throw new IllegalArgumentException("instrumentId 不能为空");
        Objects.requireNonNull(source, "source");
        if (recommendationHorizonLabel == null || recommendationHorizonLabel.isBlank()) {
            throw new IllegalArgumentException("recommendationHorizonLabel 不能为空");
        }
        positive(lowerPrice, "lowerPrice");
        positive(upperPrice, "upperPrice");
        if (upperPrice.compareTo(lowerPrice) <= 0) throw new IllegalArgumentException("upperPrice 必须大于 lowerPrice");
        if (gridCount < 2 || gridCount > 500) throw new IllegalArgumentException("gridCount 必须在 2 到 500 之间");
        Objects.requireNonNull(gridType, "gridType");
        displayedAnnualizedRate = requireOptionalNonNegative(displayedAnnualizedRate, "displayedAnnualizedRate");
        displayedMinimumPerGridRate = requireOptionalNonNegative(displayedMinimumPerGridRate, "displayedMinimumPerGridRate");
        displayedMaximumPerGridRate = requireOptionalNonNegative(displayedMaximumPerGridRate, "displayedMaximumPerGridRate");
        if (displayedMinimumPerGridRate.isPresent() && displayedMaximumPerGridRate.isPresent()
                && displayedMaximumPerGridRate.get().compareTo(displayedMinimumPerGridRate.get()) < 0) {
            throw new IllegalArgumentException("页面单格最大收益率不能小于最小收益率");
        }
        takeProfitPrice = requireOptionalPositive(takeProfitPrice, "takeProfitPrice");
        stopLossPrice = requireOptionalPositive(stopLossPrice, "stopLossPrice");
        if (sourceReference == null || sourceReference.isBlank()) throw new IllegalArgumentException("sourceReference 不能为空");
        Objects.requireNonNull(observedAt, "observedAt");
    }

    private static Optional<BigDecimal> requireOptionalPositive(Optional<BigDecimal> value, String name) {
        Objects.requireNonNull(value, name);
        value.ifPresent(price -> positive(price, name));
        return value;
    }

    private static Optional<BigDecimal> requireOptionalNonNegative(Optional<BigDecimal> value, String name) {
        Objects.requireNonNull(value, name);
        value.ifPresent(rate -> {
            if (rate.signum() < 0) throw new IllegalArgumentException(name + " 不能为负数");
        });
        return value;
    }

    private static void positive(BigDecimal value, String name) {
        if (value == null || value.signum() <= 0) throw new IllegalArgumentException(name + " 必须大于 0");
    }
}
