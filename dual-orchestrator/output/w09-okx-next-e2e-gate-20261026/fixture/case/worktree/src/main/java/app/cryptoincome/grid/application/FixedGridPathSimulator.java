package app.cryptoincome.grid.application;

import app.cryptoincome.grid.domain.GridAccountingResult;
import app.cryptoincome.grid.domain.GridBacktestRequest;
import app.cryptoincome.grid.domain.GridExitReason;
import app.cryptoincome.grid.domain.GridInitialAllocation;
import app.cryptoincome.grid.domain.GridPathResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;

/** Shared fixed-grid state machine. Adapters own historical candle or future scenario path semantics. */
@Component
public class FixedGridPathSimulator {
    private static final MathContext MC = MathContext.DECIMAL128;
    private static final BigDecimal BPS = new BigDecimal("10000");

    public Session start(GridBacktestRequest request, BigDecimal initialPrice) {
        return Session.initialize(request, initialPrice);
    }

    public static final class Session {
        private final GridBacktestRequest request;
        private final BigDecimal[] lower;
        private final BigDecimal[] upper;
        private final boolean[] sellActive;
        private final boolean[] boughtDuringRun;
        private final BigDecimal quantity;
        private final GridInitialAllocation initialAllocation;
        private BigDecimal cash;
        private BigDecimal btc;
        private BigDecimal totalFees;
        private BigDecimal gridTradingFee = BigDecimal.ZERO;
        private BigDecimal entryExitFee;
        private BigDecimal exitSlippage = BigDecimal.ZERO;
        private BigDecimal completedGridProfit = BigDecimal.ZERO;
        private BigDecimal grossCycleSpread = BigDecimal.ZERO;
        private BigDecimal peakEquity;
        private BigDecimal maximumDrawdownRate = BigDecimal.ZERO;
        private BigDecimal peakBtcDirectionalExposureRate = BigDecimal.ZERO;
        private int completedCycles;
        private int tradeCount;
        private long outOfRangeMinutes;
        private GridPathResult result;
        private GridAccountingResult accounting;

        private Session(GridBacktestRequest request, BigDecimal initialPrice,
                        BigDecimal[] lower, BigDecimal[] upper, boolean[] sellActive,
                        boolean[] boughtDuringRun, BigDecimal quantity,
                        BigDecimal cash, BigDecimal btc, BigDecimal initialFee,
                        GridInitialAllocation initialAllocation) {
            this.request = request;
            this.lower = lower;
            this.upper = upper;
            this.sellActive = sellActive;
            this.boughtDuringRun = boughtDuringRun;
            this.quantity = quantity;
            this.initialAllocation = initialAllocation;
            this.cash = cash;
            this.btc = btc;
            this.totalFees = initialFee;
            this.entryExitFee = initialFee;
            this.peakEquity = request.capital();
            observeEquity(initialPrice);
        }

        private static Session initialize(GridBacktestRequest request, BigDecimal initialPrice) {
            int count = request.gridCount();
            BigDecimal step = request.upperPrice().subtract(request.lowerPrice()).divide(new BigDecimal(count), MC);
            BigDecimal[] lower = new BigDecimal[count];
            BigDecimal[] upper = new BigDecimal[count];
            boolean[] sell = new boolean[count];
            boolean[] bought = new boolean[count];
            BigDecimal denominator = BigDecimal.ZERO;
            int initialSellCount = 0;
            for (int i = 0; i < count; i++) {
                lower[i] = request.lowerPrice().add(step.multiply(new BigDecimal(i), MC));
                upper[i] = i == count - 1 ? request.upperPrice()
                        : request.lowerPrice().add(step.multiply(new BigDecimal(i + 1), MC));
                sell[i] = upper[i].compareTo(initialPrice) > 0;
                if (sell[i]) {
                    initialSellCount++;
                } else {
                    denominator = denominator.add(lower[i].multiply(BigDecimal.ONE.add(request.makerFeeRate()), MC));
                }
            }
            denominator = denominator.add(initialPrice.multiply(new BigDecimal(initialSellCount), MC)
                    .multiply(BigDecimal.ONE.add(request.takerFeeRate()), MC));
            BigDecimal quantity = request.capital().divide(denominator, MC);
            BigDecimal initialNotional = initialPrice.multiply(quantity, MC)
                    .multiply(new BigDecimal(initialSellCount), MC);
            BigDecimal initialFee = initialNotional.multiply(request.takerFeeRate(), MC);
            BigDecimal cash = request.capital().subtract(initialNotional).subtract(initialFee);
            BigDecimal btc = quantity.multiply(new BigDecimal(initialSellCount), MC);
            GridInitialAllocation allocation = new GridInitialAllocation(
                    request.capital(), initialPrice, btc, initialNotional,
                    initialNotional.divide(request.capital(), MC), cash,
                    cash.divide(request.capital(), MC), initialFee);
            return new Session(request, initialPrice, lower, upper, sell, bought, quantity, cash, btc, initialFee,
                    allocation);
        }

        public void moveUntilExit(BigDecimal from, BigDecimal to, Instant detectedAt) {
            if (terminated() || from.compareTo(to) == 0) return;
            if (to.compareTo(from) > 0 && request.takeProfitPrice().isPresent()) {
                BigDecimal threshold = request.takeProfitPrice().get();
                if (threshold.compareTo(from) > 0 && threshold.compareTo(to) <= 0) {
                    move(from, threshold);
                    terminate(GridExitReason.TAKE_PROFIT, detectedAt, threshold);
                    return;
                }
            }
            if (to.compareTo(from) < 0 && request.stopLossPrice().isPresent()) {
                BigDecimal threshold = request.stopLossPrice().get();
                if (threshold.compareTo(from) < 0 && threshold.compareTo(to) >= 0) {
                    move(from, threshold);
                    terminate(GridExitReason.STOP_LOSS, detectedAt, threshold);
                    return;
                }
            }
            move(from, to);
        }

        private void move(BigDecimal from, BigDecimal to) {
            int direction = to.compareTo(from);
            if (direction > 0) {
                for (int i = 0; i < sellActive.length; i++) {
                    if (sellActive[i] && upper[i].compareTo(from) > 0 && upper[i].compareTo(to) <= 0) sell(i);
                }
            } else if (direction < 0) {
                for (int i = sellActive.length - 1; i >= 0; i--) {
                    if (!sellActive[i] && lower[i].compareTo(from) < 0 && lower[i].compareTo(to) >= 0) buy(i);
                }
            }
            observeEquity(to);
        }

        private void buy(int index) {
            BigDecimal notional = lower[index].multiply(quantity, MC);
            BigDecimal fee = notional.multiply(request.makerFeeRate(), MC);
            BigDecimal required = notional.add(fee);
            if (cash.compareTo(required) < 0 && required.subtract(cash).abs().compareTo(new BigDecimal("1E-18")) > 0) {
                throw new IllegalStateException("网格模拟现金不足，初始化分配模型不一致");
            }
            cash = cash.subtract(required);
            btc = btc.add(quantity);
            totalFees = totalFees.add(fee);
            gridTradingFee = gridTradingFee.add(fee);
            sellActive[index] = true;
            boughtDuringRun[index] = true;
            tradeCount++;
            observeEquity(lower[index]);
        }

        private void sell(int index) {
            observeBtcExposure(upper[index]);
            if (btc.compareTo(quantity) < 0) {
                BigDecimal shortfall = quantity.subtract(btc).abs();
                if (shortfall.compareTo(new BigDecimal("1E-18")) > 0) {
                    throw new IllegalStateException("网格模拟 BTC 库存不足");
                }
                btc = quantity;
            }
            BigDecimal notional = upper[index].multiply(quantity, MC);
            BigDecimal fee = notional.multiply(request.makerFeeRate(), MC);
            cash = cash.add(notional).subtract(fee);
            btc = btc.subtract(quantity);
            totalFees = totalFees.add(fee);
            gridTradingFee = gridTradingFee.add(fee);
            sellActive[index] = false;
            if (boughtDuringRun[index]) {
                BigDecimal buyFee = lower[index].multiply(quantity, MC).multiply(request.makerFeeRate(), MC);
                BigDecimal spread = upper[index].subtract(lower[index]).multiply(quantity, MC);
                grossCycleSpread = grossCycleSpread.add(spread);
                completedGridProfit = completedGridProfit.add(spread.subtract(buyFee).subtract(fee));
                completedCycles++;
                boughtDuringRun[index] = false;
            }
            tradeCount++;
            observeEquity(upper[index]);
        }

        public void observeEquity(BigDecimal price) {
            BigDecimal equity = cash.add(btc.multiply(price, MC));
            if (equity.compareTo(peakEquity) > 0) peakEquity = equity;
            if (peakEquity.signum() > 0) {
                BigDecimal drawdown = peakEquity.subtract(equity).divide(peakEquity, MC);
                if (drawdown.compareTo(maximumDrawdownRate) > 0) maximumDrawdownRate = drawdown;
            }
            observeBtcExposure(price);
        }

        private void observeBtcExposure(BigDecimal price) {
            BigDecimal exposure = btc.multiply(price, MC).divide(request.capital(), MC);
            if (exposure.compareTo(peakBtcDirectionalExposureRate) > 0) {
                peakBtcDirectionalExposureRate = exposure;
            }
        }

        public void addOutOfRangeMinutes(long minutes) {
            if (minutes < 0) throw new IllegalArgumentException("区间外分钟数不能为负数");
            outOfRangeMinutes = Math.addExact(outOfRangeMinutes, minutes);
        }

        public void terminate(GridExitReason reason, Instant exitTime, BigDecimal finalPrice) {
            if (result != null) return;
            finish(reason, exitTime, finalPrice);
        }

        public boolean terminated() { return result != null; }

        public GridPathResult result() {
            if (result == null) throw new IllegalStateException("网格路径尚未终止");
            return result;
        }

        public GridAccountingResult accounting() {
            if (accounting == null) throw new IllegalStateException("网格路径尚未终止");
            return accounting;
        }

        public GridInitialAllocation initialAllocation() { return initialAllocation; }

        private void finish(GridExitReason reason, Instant exitTime, BigDecimal finalPrice) {
            BigDecimal endingBtc = btc;
            BigDecimal equityBeforeExit = cash.add(endingBtc.multiply(finalPrice, MC));
            BigDecimal endingBtcValueRatio = equityBeforeExit.signum() == 0 ? BigDecimal.ZERO
                    : endingBtc.multiply(finalPrice, MC).divide(equityBeforeExit, MC);
            BigDecimal slippageRatio = request.exitSlippageBps().divide(BPS, MC);
            BigDecimal exitPrice = finalPrice.multiply(BigDecimal.ONE.subtract(slippageRatio), MC);
            BigDecimal exitNotional = btc.multiply(exitPrice, MC);
            BigDecimal exitFee = exitNotional.multiply(request.takerFeeRate(), MC);
            exitSlippage = btc.multiply(finalPrice.subtract(exitPrice), MC);
            cash = cash.add(exitNotional).subtract(exitFee);
            totalFees = totalFees.add(exitFee);
            entryExitFee = entryExitFee.add(exitFee);
            btc = BigDecimal.ZERO;
            tradeCount += endingBtc.signum() > 0 ? 1 : 0;
            observeEquity(finalPrice);
            BigDecimal totalIncome = cash.subtract(request.capital());
            BigDecimal inventoryPnl = totalIncome.subtract(completedGridProfit);
            result = new GridPathResult(
                    cash, cash, totalIncome, completedGridProfit, inventoryPnl, totalFees,
                    maximumDrawdownRate, completedCycles, endingBtc, endingBtcValueRatio,
                    outOfRangeMinutes, tradeCount, reason, exitTime, finalPrice);
            BigDecimal grossInventoryPnl = totalIncome.subtract(grossCycleSpread)
                    .add(gridTradingFee).add(entryExitFee).add(exitSlippage);
            accounting = new GridAccountingResult(
                    totalIncome, grossCycleSpread, grossInventoryPnl, gridTradingFee,
                    entryExitFee, exitSlippage, BigDecimal.ZERO, peakBtcDirectionalExposureRate);
            if (accounting.attributedNetIncome().compareTo(totalIncome) != 0) {
                throw new IllegalStateException("网格毛额归因不守恒");
            }
        }

        public String fingerprint() {
            StringBuilder value = new StringBuilder();
            for (int i = 0; i < sellActive.length; i++) {
                value.append(sellActive[i] ? 'S' : 'B').append(boughtDuringRun[i] ? '1' : '0');
            }
            return value.append('|').append(tradeCount).append('|')
                    .append(result == null ? "RUNNING" : result.exitReason()).toString();
        }
    }
}
