package app.cryptoincome.scenario.application;

import app.cryptoincome.grid.application.FixedGridPathSimulator;
import app.cryptoincome.grid.domain.GridAccountingResult;
import app.cryptoincome.grid.domain.GridBacktestRequest;
import app.cryptoincome.grid.domain.GridExitReason;
import app.cryptoincome.grid.domain.GridPathResult;
import app.cryptoincome.scenario.domain.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Component
public class FixedGridScenarioCalculator implements ProductScenarioCalculator<GridScenarioAssumption> {
    private static final MathContext MC = MathContext.DECIMAL128;
    private final FixedGridPathSimulator simulator;

    public FixedGridScenarioCalculator(FixedGridPathSimulator simulator) {
        this.simulator = simulator;
    }

    @Override
    public boolean supports(ScenarioProductCode productCode) {
        return productCode == ScenarioProductCode.SPOT_GRID;
    }

    @Override
    public ProductScenarioOutcome calculate(ScenarioCalculationContext context,
                                            GridScenarioAssumption assumption) {
        GridBacktestRequest request = new GridBacktestRequest(
                context.allocatedCapitalUsdt(), assumption.lowerPrice(), assumption.upperPrice(),
                assumption.gridCount(), assumption.makerFeeRate(), assumption.takerFeeRate(),
                assumption.exitSlippageBps(), assumption.takeProfitPrice(), assumption.stopLossPrice());
        List<ScenarioPricePoint> points = context.pricePath().points();
        BigDecimal initialPrice = points.getFirst().btcUsdtPrice();
        if (initialPrice.compareTo(assumption.lowerPrice()) < 0
                || initialPrice.compareTo(assumption.upperPrice()) > 0) {
            throw new IllegalArgumentException("情景路径起始价必须位于网格区间内");
        }
        FixedGridPathSimulator.Session session = simulator.start(request, initialPrice);
        for (int i = 1; i < points.size() && !session.terminated(); i++) {
            ScenarioPricePoint previous = points.get(i - 1);
            ScenarioPricePoint current = points.get(i);
            session.moveUntilExit(previous.btcUsdtPrice(), current.btcUsdtPrice(), current.at());
            session.addOutOfRangeMinutes(outOfRangeMinutes(previous, current,
                    assumption.lowerPrice(), assumption.upperPrice()));
        }
        ScenarioPricePoint last = points.getLast();
        if (!session.terminated()) session.terminate(GridExitReason.WINDOW_END, last.at(), last.btcUsdtPrice());
        GridAccountingResult accounting = session.accounting();
        GridPathResult legacy = session.result();
        BigDecimal netIncome = accounting.netIncome().subtract(assumption.otherKnownCost());
        BigDecimal endingExposure = legacy.endingBtcBeforeExit().multiply(legacy.exitTriggerPrice(), MC)
                .divide(context.allocatedCapitalUsdt(), MC);
        return ProductScenarioOutcome.create(ScenarioProductCode.SPOT_GRID, context, netIncome,
                accounting.peakBtcDirectionalExposureRate(), endingExposure,
                Optional.of(legacy.maximumDrawdownRate()),
                List.of(
                        new IncomeContribution(ContributionCode.GRID_CYCLE_SPREAD,
                                accounting.grossCycleSpread()),
                        new IncomeContribution(ContributionCode.GRID_INVENTORY_PNL,
                                accounting.grossInventoryPnl()),
                        new IncomeContribution(ContributionCode.GRID_TRADING_FEE,
                                accounting.gridTradingFee().negate()),
                        new IncomeContribution(ContributionCode.ENTRY_EXIT_FEE,
                                accounting.entryExitFee().negate()),
                        new IncomeContribution(ContributionCode.SLIPPAGE,
                                accounting.exitSlippage().negate()),
                        new IncomeContribution(ContributionCode.OTHER_KNOWN_COST,
                                assumption.otherKnownCost().negate())),
                List.of("显式情景路径计算，不代表该路径发生概率"));
    }

    private static long outOfRangeMinutes(ScenarioPricePoint from, ScenarioPricePoint to,
                                          BigDecimal lower, BigDecimal upper) {
        long seconds = Duration.between(from.at(), to.at()).toSeconds();
        BigDecimal left = from.btcUsdtPrice();
        BigDecimal right = to.btcUsdtPrice();
        if (left.compareTo(right) == 0) {
            return left.compareTo(lower) < 0 || left.compareTo(upper) > 0 ? seconds / 60 : 0;
        }
        BigDecimal segmentLow = left.min(right);
        BigDecimal segmentHigh = left.max(right);
        BigDecimal insideLow = segmentLow.max(lower);
        BigDecimal insideHigh = segmentHigh.min(upper);
        BigDecimal insideLength = insideHigh.compareTo(insideLow) > 0
                ? insideHigh.subtract(insideLow) : BigDecimal.ZERO;
        BigDecimal fullLength = segmentHigh.subtract(segmentLow);
        BigDecimal outsideFraction = BigDecimal.ONE.subtract(insideLength.divide(fullLength, MC));
        return new BigDecimal(seconds).multiply(outsideFraction, MC)
                .divide(new BigDecimal("60"), MC).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }
}
