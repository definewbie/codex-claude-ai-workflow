package app.cryptoincome.scenario.application;

import app.cryptoincome.scenario.domain.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.Optional;

@Component
public class BtcSpotScenarioCalculator implements ProductScenarioCalculator<BtcSpotScenarioAssumption> {
    private static final MathContext MC = MathContext.DECIMAL128;
    private static final BigDecimal BPS = new BigDecimal("10000");

    @Override
    public boolean supports(ScenarioProductCode productCode) {
        return productCode == ScenarioProductCode.BTC_SPOT;
    }

    @Override
    public ProductScenarioOutcome calculate(ScenarioCalculationContext context,
                                            BtcSpotScenarioAssumption assumption) {
        BigDecimal capital = context.allocatedCapitalUsdt();
        List<ScenarioPricePoint> points = context.pricePath().points();
        BigDecimal entryPrice = points.getFirst().btcUsdtPrice();
        BigDecimal exitPrice = points.getLast().btcUsdtPrice();
        BigDecimal quantity = capital.divide(
                entryPrice.multiply(BigDecimal.ONE.add(assumption.entryFeeRate()), MC), MC);
        BigDecimal entryFee = quantity.multiply(entryPrice, MC).multiply(assumption.entryFeeRate(), MC);
        BigDecimal grossPricePnl = quantity.multiply(exitPrice.subtract(entryPrice), MC);
        BigDecimal slippageRatio = assumption.exitSlippageBps().divide(BPS, MC);
        BigDecimal slippage = quantity.multiply(exitPrice, MC).multiply(slippageRatio, MC);
        BigDecimal exitNotionalAfterSlippage = quantity.multiply(exitPrice, MC)
                .multiply(BigDecimal.ONE.subtract(slippageRatio), MC);
        BigDecimal exitFee = exitNotionalAfterSlippage.multiply(assumption.exitFeeRate(), MC);
        BigDecimal totalFee = entryFee.add(exitFee);
        BigDecimal netIncome = grossPricePnl.subtract(totalFee).subtract(slippage);

        BigDecimal peakExposure = BigDecimal.ZERO;
        BigDecimal peakEquity = capital;
        BigDecimal maximumDrawdown = BigDecimal.ZERO;
        for (ScenarioPricePoint point : points) {
            BigDecimal equity = quantity.multiply(point.btcUsdtPrice(), MC);
            BigDecimal exposure = equity.divide(capital, MC);
            if (exposure.compareTo(peakExposure) > 0) peakExposure = exposure;
            if (equity.compareTo(peakEquity) > 0) peakEquity = equity;
            BigDecimal drawdown = peakEquity.subtract(equity).divide(peakEquity, MC);
            if (drawdown.compareTo(maximumDrawdown) > 0) maximumDrawdown = drawdown;
        }
        BigDecimal endingExposure = quantity.multiply(exitPrice, MC).divide(capital, MC);
        return ProductScenarioOutcome.create(ScenarioProductCode.BTC_SPOT, context, netIncome,
                peakExposure, endingExposure, Optional.of(maximumDrawdown),
                List.of(new IncomeContribution(ContributionCode.BTC_SPOT_PNL, grossPricePnl),
                        new IncomeContribution(ContributionCode.ENTRY_EXIT_FEE, totalFee.negate()),
                        new IncomeContribution(ContributionCode.SLIPPAGE, slippage.negate())),
                List.of());
    }
}
