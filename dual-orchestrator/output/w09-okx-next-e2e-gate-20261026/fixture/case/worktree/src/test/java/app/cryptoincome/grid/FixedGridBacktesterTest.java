package app.cryptoincome.grid;

import app.cryptoincome.grid.application.FixedGridBacktester;
import app.cryptoincome.grid.domain.CandleBar;
import app.cryptoincome.grid.domain.GridBacktestRequest;
import app.cryptoincome.grid.domain.GridExitReason;
import app.cryptoincome.grid.domain.SpotCandle;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class FixedGridBacktesterTest {

    @Test
    void exposesIntracandlePathDivergenceInsteadOfChoosingBetterPath() {
        FixedGridBacktester backtester = new FixedGridBacktester();
        SpotCandle candle = candle("2026-01-01T00:00:00Z", "110", "120", "100", "120");

        var result = backtester.backtest(request("0", "0"), List.of(candle));

        assertThat(result.ambiguousCandles()).isEqualTo(1);
        assertThat(result.pathLowThenHigh().totalIncome())
                .isNotEqualByComparingTo(result.pathHighThenLow().totalIncome());
        assertThat(result.pathLowThenHigh().completedCycles()).isEqualTo(1);
        assertThat(result.pathHighThenLow().completedCycles()).isEqualTo(2);
        assertThat(result.pathLowThenHigh().totalIncome()).isPositive();
        assertThat(result.pathHighThenLow().totalIncome()).isPositive();
    }

    @Test
    void chargesEveryTradeOnceAndIncludesTerminalExitToUsdt() {
        FixedGridBacktester backtester = new FixedGridBacktester();
        List<SpotCandle> candles = List.of(
                candle("2026-01-01T00:00:00Z", "110", "115", "105", "110"),
                candle("2026-01-01T00:01:00Z", "110", "110", "90", "90"));

        var result = backtester.backtest(request("0.001", "0.002"), candles);

        assertThat(result.pathLowThenHigh().totalFees()).isPositive();
        assertThat(result.pathLowThenHigh().endingBtcBeforeExit()).isPositive();
        assertThat(result.pathLowThenHigh().endingUsdtAfterExit())
                .isEqualByComparingTo(result.pathLowThenHigh().finalEquity());
        assertThat(result.pathLowThenHigh().inventoryPnl()
                .add(result.pathLowThenHigh().completedGridProfit()))
                .isEqualByComparingTo(result.pathLowThenHigh().totalIncome());
    }

    @Test
    void exposesTheExactInitialBtcAndUsdtAllocationUsedByTheSimulation() {
        FixedGridBacktester backtester = new FixedGridBacktester();

        var result = backtester.backtest(request("0.001", "0.002"),
                List.of(candle("2026-01-01T00:00:00Z", "110", "115", "105", "110")));

        var allocation = result.initialAllocation();
        assertThat(allocation.candidateCapital()).isEqualByComparingTo("1000");
        assertThat(allocation.btcQuantity()).isPositive();
        assertThat(allocation.btcNotional()).isPositive();
        assertThat(allocation.reservedBuyOrderUsdt()).isPositive();
        assertThat(allocation.btcNotional().add(allocation.reservedBuyOrderUsdt()).add(allocation.initialBuyFee()))
                .isEqualByComparingTo(allocation.candidateCapital());
        assertThat(allocation.btcCapitalRatio()).isEqualByComparingTo(
                allocation.btcNotional().divide(allocation.candidateCapital(), java.math.MathContext.DECIMAL128));
    }

    @Test
    void recordsPathSpecificTerminalReasonWhenOneMinuteTouchesBothStops() {
        FixedGridBacktester backtester = new FixedGridBacktester();
        GridBacktestRequest request = new GridBacktestRequest(
                new BigDecimal("1000"), new BigDecimal("100"), new BigDecimal("120"), 2,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                Optional.of(new BigDecimal("130")), Optional.of(new BigDecimal("90")));
        SpotCandle candle = candle("2026-01-01T00:00:00Z", "110", "135", "85", "110");

        var result = backtester.backtest(request, List.of(candle));

        assertThat(result.pathLowThenHigh().exitReason()).isEqualTo(GridExitReason.STOP_LOSS);
        assertThat(result.pathLowThenHigh().exitTriggerPrice()).isEqualByComparingTo("90");
        assertThat(result.pathHighThenLow().exitReason()).isEqualTo(GridExitReason.TAKE_PROFIT);
        assertThat(result.pathHighThenLow().exitTriggerPrice()).isEqualByComparingTo("130");
        assertThat(result.pathLowThenHigh().exitTime())
                .isEqualTo(Instant.parse("2026-01-01T00:01:00Z"));
        assertThat(result.pathHighThenLow().exitTime())
                .isEqualTo(Instant.parse("2026-01-01T00:01:00Z"));
    }

    @Test
    void rejectsStopPricesInsideTheGrid() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new GridBacktestRequest(
                        new BigDecimal("1000"), new BigDecimal("100"), new BigDecimal("120"), 2,
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        Optional.of(new BigDecimal("119")), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("高于网格上限");
    }

    private static GridBacktestRequest request(String makerFee, String takerFee) {
        return new GridBacktestRequest(
                new BigDecimal("1000"), new BigDecimal("100"), new BigDecimal("120"), 2,
                new BigDecimal(makerFee), new BigDecimal(takerFee), BigDecimal.ZERO);
    }

    private static SpotCandle candle(String time, String open, String high, String low, String close) {
        return new SpotCandle(
                "OKX", "BTC-USDT", CandleBar.M1, Instant.parse(time),
                new BigDecimal(open), new BigDecimal(high), new BigDecimal(low), new BigDecimal(close),
                BigDecimal.ONE, new BigDecimal("100"), true,
                "/api/v5/market/history-candles", "[]", "b".repeat(64),
                Instant.parse("2026-08-19T08:00:00Z"));
    }
}
