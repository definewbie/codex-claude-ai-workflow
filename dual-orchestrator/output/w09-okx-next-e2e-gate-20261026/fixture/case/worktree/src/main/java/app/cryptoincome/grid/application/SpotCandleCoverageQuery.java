package app.cryptoincome.grid.application;

import app.cryptoincome.grid.domain.CandleBar;
import app.cryptoincome.grid.domain.CandleWindowCoverage;

import java.time.Instant;

public interface SpotCandleCoverageQuery {
    CandleWindowCoverage inspect(
            String exchange, String instrumentId, CandleBar bar,
            Instant from, Instant to, Instant evaluationAsOf);
}
