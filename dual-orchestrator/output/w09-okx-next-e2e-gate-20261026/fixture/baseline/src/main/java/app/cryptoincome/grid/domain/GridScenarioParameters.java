package app.cryptoincome.grid.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/** 一次只读回测使用的有效参数；不会覆盖 OKX 人工确认配置。 */
public record GridScenarioParameters(
        BigDecimal lowerPrice,
        BigDecimal upperPrice,
        int gridCount,
        Optional<BigDecimal> takeProfitPrice,
        Optional<BigDecimal> stopLossPrice
) {
    public GridScenarioParameters {
        positive(lowerPrice, "lowerPrice");
        positive(upperPrice, "upperPrice");
        if (upperPrice.compareTo(lowerPrice) <= 0) throw new IllegalArgumentException("upperPrice 必须大于 lowerPrice");
        if (gridCount < 2 || gridCount > 500) throw new IllegalArgumentException("gridCount 必须在 2 到 500 之间");
        takeProfitPrice = optionalPositive(takeProfitPrice, "takeProfitPrice");
        stopLossPrice = optionalPositive(stopLossPrice, "stopLossPrice");
        takeProfitPrice.ifPresent(value -> {
            if (value.compareTo(upperPrice) <= 0) throw new IllegalArgumentException("止盈价格必须高于网格上限");
        });
        stopLossPrice.ifPresent(value -> {
            if (value.compareTo(lowerPrice) >= 0) throw new IllegalArgumentException("止损价格必须低于网格下限");
        });
    }

    public static GridScenarioParameters confirmed(GridConfigurationSnapshot configuration) {
        return new GridScenarioParameters(
                configuration.lowerPrice(), configuration.upperPrice(), configuration.gridCount(),
                configuration.takeProfitPrice(), configuration.stopLossPrice());
    }

    private static Optional<BigDecimal> optionalPositive(Optional<BigDecimal> value, String name) {
        Objects.requireNonNull(value, name);
        value.ifPresent(item -> positive(item, name));
        return value;
    }

    private static void positive(BigDecimal value, String name) {
        if (value == null || value.signum() <= 0) throw new IllegalArgumentException(name + " 必须大于 0");
    }
}
