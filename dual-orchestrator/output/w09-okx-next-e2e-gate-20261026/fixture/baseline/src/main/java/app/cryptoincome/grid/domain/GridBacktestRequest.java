package app.cryptoincome.grid.domain;

import java.math.BigDecimal;
import java.util.Optional;

public record GridBacktestRequest(
        BigDecimal capital,
        BigDecimal lowerPrice,
        BigDecimal upperPrice,
        int gridCount,
        BigDecimal makerFeeRate,
        BigDecimal takerFeeRate,
        BigDecimal exitSlippageBps,
        Optional<BigDecimal> takeProfitPrice,
        Optional<BigDecimal> stopLossPrice
) {
    public GridBacktestRequest(BigDecimal capital, BigDecimal lowerPrice, BigDecimal upperPrice,
                               int gridCount, BigDecimal makerFeeRate, BigDecimal takerFeeRate,
                               BigDecimal exitSlippageBps) {
        this(capital, lowerPrice, upperPrice, gridCount, makerFeeRate, takerFeeRate,
                exitSlippageBps, Optional.empty(), Optional.empty());
    }

    public GridBacktestRequest {
        positive(capital, "capital");
        positive(lowerPrice, "lowerPrice");
        positive(upperPrice, "upperPrice");
        if (upperPrice.compareTo(lowerPrice) <= 0) throw new IllegalArgumentException("upperPrice 必须大于 lowerPrice");
        if (gridCount < 2 || gridCount > 500) throw new IllegalArgumentException("gridCount 必须在 2 到 500 之间");
        rate(makerFeeRate, "makerFeeRate");
        rate(takerFeeRate, "takerFeeRate");
        if (exitSlippageBps == null || exitSlippageBps.signum() < 0
                || exitSlippageBps.compareTo(new BigDecimal("10000")) >= 0) {
            throw new IllegalArgumentException("exitSlippageBps 必须在 0 到 10000 之间");
        }
        takeProfitPrice = takeProfitPrice == null ? Optional.empty() : takeProfitPrice;
        stopLossPrice = stopLossPrice == null ? Optional.empty() : stopLossPrice;
        takeProfitPrice.ifPresent(value -> {
            if (value.compareTo(upperPrice) <= 0) {
                throw new IllegalArgumentException("takeProfitPrice 必须高于网格上限");
            }
        });
        stopLossPrice.ifPresent(value -> {
            if (value.compareTo(lowerPrice) >= 0 || value.signum() <= 0) {
                throw new IllegalArgumentException("stopLossPrice 必须大于 0 且低于网格下限");
            }
        });
    }

    private static void positive(BigDecimal value, String name) {
        if (value == null || value.signum() <= 0) throw new IllegalArgumentException(name + " 必须大于 0");
    }

    private static void rate(BigDecimal value, String name) {
        if (value == null || value.signum() < 0 || value.compareTo(BigDecimal.ONE) >= 0) {
            throw new IllegalArgumentException(name + " 必须在 0 到 1 之间");
        }
    }
}
