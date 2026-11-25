package app.cryptoincome.grid.domain;

import java.math.BigDecimal;

/** Additive gross-to-net attribution for scenario calculations. */
public record GridAccountingResult(
        BigDecimal netIncome,
        BigDecimal grossCycleSpread,
        BigDecimal grossInventoryPnl,
        BigDecimal gridTradingFee,
        BigDecimal entryExitFee,
        BigDecimal exitSlippage,
        BigDecimal otherKnownCost,
        BigDecimal peakBtcDirectionalExposureRate
) {
    public BigDecimal attributedNetIncome() {
        return grossCycleSpread.add(grossInventoryPnl)
                .subtract(gridTradingFee)
                .subtract(entryExitFee)
                .subtract(exitSlippage)
                .subtract(otherKnownCost);
    }
}
