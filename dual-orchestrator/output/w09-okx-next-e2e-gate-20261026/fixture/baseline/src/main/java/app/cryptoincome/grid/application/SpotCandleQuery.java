package app.cryptoincome.grid.application;

import app.cryptoincome.grid.domain.CandleBar;
import app.cryptoincome.grid.domain.SpotCandle;

import java.time.Instant;
import java.util.List;

public interface SpotCandleQuery {
    List<SpotCandle> findCompletedAsOf(
            String exchange, String instrumentId, CandleBar bar,
            Instant from, Instant to, Instant evaluationAsOf);
}
