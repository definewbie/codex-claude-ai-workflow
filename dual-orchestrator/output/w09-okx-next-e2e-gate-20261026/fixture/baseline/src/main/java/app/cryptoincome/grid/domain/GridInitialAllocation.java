package app.cryptoincome.grid.domain;

import java.math.BigDecimal;

public record GridInitialAllocation(
        BigDecimal candidateCapital,
        BigDecimal initialPrice,
        BigDecimal btcQuantity,
        BigDecimal btcNotional,
        BigDecimal btcCapitalRatio,
        BigDecimal reservedBuyOrderUsdt,
        BigDecimal reservedBuyOrderRatio,
        BigDecimal initialBuyFee
) {}
