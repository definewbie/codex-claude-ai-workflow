package app.cryptoincome.income;

import java.math.BigDecimal;
import java.time.Instant;

public record SimpleEarnMarketRate(
        String currency,
        BigDecimal averageBorrowApr24h,
        BigDecimal previousBorrowApr,
        BigDecimal estimatedNextBorrowApr,
        Instant observedAt,
        String source
) {}
