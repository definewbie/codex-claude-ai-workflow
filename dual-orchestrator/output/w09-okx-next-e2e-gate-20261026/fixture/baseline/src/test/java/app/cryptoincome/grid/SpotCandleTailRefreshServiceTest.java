package app.cryptoincome.grid;

import app.cryptoincome.grid.application.SpotCandleTailRefreshService;
import app.cryptoincome.grid.domain.CandleAppendResult;
import app.cryptoincome.grid.domain.CandleBar;
import app.cryptoincome.grid.domain.SpotCandle;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SpotCandleTailRefreshServiceTest {
    @Test
    void appendsOnlyConfirmedCandlesBeforeTheCompletedMinuteBoundary() {
        Instant to = Instant.parse("2026-08-22T00:01:00Z");
        SpotCandle completed = candle(to.minusSeconds(60), true, "a");
        SpotCandle forming = candle(to, false, "b");
        var service = new SpotCandleTailRefreshService(
                (instrument, bar, limit, after) -> List.of(forming, completed),
                rows -> new CandleAppendResult(rows.size(), rows.size(), 0));

        var result = service.refresh(to);

        assertThat(result.received()).isEqualTo(2);
        assertThat(result.completed()).isEqualTo(1);
        assertThat(result.newFacts()).isEqualTo(1);
    }

    private static SpotCandle candle(Instant openTime, boolean confirmed, String hash) {
        return new SpotCandle("OKX", "BTC-USDT", CandleBar.M1, openTime,
                new BigDecimal("64000"), new BigDecimal("64100"), new BigDecimal("63900"),
                new BigDecimal("64050"), BigDecimal.ONE, new BigDecimal("64050"), confirmed,
                "/test", "[]", hash.repeat(64), Instant.parse("2026-08-22T00:01:30Z"));
    }
}
