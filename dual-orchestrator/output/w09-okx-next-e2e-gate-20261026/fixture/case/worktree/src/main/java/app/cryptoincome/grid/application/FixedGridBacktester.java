package app.cryptoincome.grid.application;

import app.cryptoincome.grid.domain.DualPathGridBacktest;
import app.cryptoincome.grid.domain.GridBacktestRequest;
import app.cryptoincome.grid.domain.GridExitReason;
import app.cryptoincome.grid.domain.GridInitialAllocation;
import app.cryptoincome.grid.domain.GridPathResult;
import app.cryptoincome.grid.domain.SpotCandle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class FixedGridBacktester {
    private final FixedGridPathSimulator simulator;

    public FixedGridBacktester() {
        this(new FixedGridPathSimulator());
    }

    @Autowired
    public FixedGridBacktester(FixedGridPathSimulator simulator) {
        this.simulator = simulator;
    }

    public DualPathGridBacktest backtest(GridBacktestRequest request, List<SpotCandle> input) {
        if (input == null || input.isEmpty()) throw new IllegalArgumentException("回测 K 线不能为空");
        List<SpotCandle> candles = input.stream().sorted(Comparator.comparing(SpotCandle::openTime)).toList();
        validateCandles(candles);
        BigDecimal initialPrice = candles.getFirst().open();
        if (initialPrice.compareTo(request.lowerPrice()) < 0 || initialPrice.compareTo(request.upperPrice()) > 0) {
            throw new IllegalArgumentException("首根 K 线开盘价必须位于网格区间内");
        }
        Simulation lowThenHigh = simulate(request, candles, true);
        Simulation highThenLow = simulate(request, candles, false);
        int ambiguous = 0;
        for (int i = 0; i < candles.size(); i++) {
            if (!lowThenHigh.fingerprints().get(i).equals(highThenLow.fingerprints().get(i))) ambiguous++;
        }
        Instant toExclusive = candles.getLast().openTime().plus(candles.getLast().bar().duration());
        return new DualPathGridBacktest(
                request, candles.getFirst().openTime(), toExclusive, initialPrice, candles.getLast().close(),
                lowThenHigh.initialAllocation(), lowThenHigh.result(), highThenLow.result(), ambiguous);
    }

    private static void validateCandles(List<SpotCandle> candles) {
        SpotCandle previous = null;
        for (SpotCandle candle : candles) {
            if (!candle.confirmed()) throw new IllegalArgumentException("未完成 K 线不能进入回测");
            if (previous != null) {
                if (!previous.exchange().equals(candle.exchange())
                        || !previous.instrumentId().equals(candle.instrumentId())
                        || previous.bar() != candle.bar()) {
                    throw new IllegalArgumentException("回测 K 线必须属于同一交易所、标的和周期");
                }
                if (!previous.openTime().plus(previous.bar().duration()).equals(candle.openTime())) {
                    throw new IllegalArgumentException("回测 K 线必须按周期连续且无重复");
                }
            }
            previous = candle;
        }
    }

    private Simulation simulate(GridBacktestRequest request, List<SpotCandle> candles, boolean lowThenHigh) {
        FixedGridPathSimulator.Session state = simulator.start(request, candles.getFirst().open());
        List<String> fingerprints = new ArrayList<>(candles.size());
        BigDecimal previousClose = candles.getFirst().open();
        for (SpotCandle candle : candles) {
            if (!state.terminated()) {
                Instant barEnd = candle.openTime().plus(candle.bar().duration());
                state.moveUntilExit(previousClose, candle.open(), candle.openTime());
                if (lowThenHigh) {
                    state.moveUntilExit(candle.open(), candle.low(), barEnd);
                    state.moveUntilExit(candle.low(), candle.high(), barEnd);
                    state.moveUntilExit(candle.high(), candle.close(), barEnd);
                } else {
                    state.moveUntilExit(candle.open(), candle.high(), barEnd);
                    state.moveUntilExit(candle.high(), candle.low(), barEnd);
                    state.moveUntilExit(candle.low(), candle.close(), barEnd);
                }
                if (candle.low().compareTo(request.lowerPrice()) < 0
                        || candle.high().compareTo(request.upperPrice()) > 0) {
                    state.addOutOfRangeMinutes(1);
                }
                if (!state.terminated()) state.observeEquity(candle.close());
            }
            fingerprints.add(state.fingerprint());
            previousClose = candle.close();
        }
        Instant windowEnd = candles.getLast().openTime().plus(candles.getLast().bar().duration());
        if (!state.terminated()) {
            state.terminate(GridExitReason.WINDOW_END, windowEnd, candles.getLast().close());
        }
        return new Simulation(state.result(), state.initialAllocation(), List.copyOf(fingerprints));
    }

    private record Simulation(GridPathResult result, GridInitialAllocation initialAllocation,
                              List<String> fingerprints) {}
}
