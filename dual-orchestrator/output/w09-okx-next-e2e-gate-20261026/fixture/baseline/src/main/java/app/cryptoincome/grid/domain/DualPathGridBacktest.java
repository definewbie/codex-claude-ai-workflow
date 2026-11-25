package app.cryptoincome.grid.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record DualPathGridBacktest(
        GridBacktestRequest request,
        Instant from,
        Instant toExclusive,
        BigDecimal initialPrice,
        BigDecimal finalPrice,
        GridInitialAllocation initialAllocation,
        GridPathResult pathLowThenHigh,
        GridPathResult pathHighThenLow,
        int ambiguousCandles
) {}
