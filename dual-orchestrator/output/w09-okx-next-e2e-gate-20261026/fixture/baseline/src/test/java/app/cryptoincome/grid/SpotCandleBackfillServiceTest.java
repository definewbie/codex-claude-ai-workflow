package app.cryptoincome.grid;

import app.cryptoincome.grid.application.SpotCandleBackfillService;
import app.cryptoincome.grid.domain.CandleAppendResult;
import app.cryptoincome.grid.domain.CandleBar;
import app.cryptoincome.grid.domain.CandleBackfillCoverage;
import app.cryptoincome.grid.domain.SpotCandle;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpotCandleBackfillServiceTest {
    private static final Instant FROM = Instant.parse("2026-05-20T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-08-18T00:00:00Z");

    @Test
    void reachesBoundaryAndNeverPersistsUncompletedOrOutOfWindowCandles() {
        AtomicInteger calls = new AtomicInteger();
        List<SpotCandle> stored = new ArrayList<>();
        var source = new ScriptedSource(List.of(
                List.of(candle(TO.minusSeconds(60), true), candle(TO.minusSeconds(120), false)),
                List.of(candle(FROM, true), candle(FROM.minusSeconds(60), true))));
        SpotCandleBackfillService service = new SpotCandleBackfillService(source, candles -> {
            calls.incrementAndGet();
            stored.addAll(candles);
            return new CandleAppendResult(candles.size(), candles.size(), 0);
        }, duration -> {});

        var result = service.backfill("BTC-USDT", CandleBar.M1, FROM, TO, 300, 10);

        assertThat(result.coverage()).isEqualTo(CandleBackfillCoverage.BOUNDARY_REACHED);
        assertThat(result.pages()).isEqualTo(2);
        assertThat(result.received()).isEqualTo(4);
        assertThat(stored).extracting(SpotCandle::openTime)
                .containsExactly(TO.minusSeconds(60), FROM);
        assertThat(stored).allMatch(SpotCandle::confirmed);
        assertThat(calls).hasValue(2);
    }

    @Test
    void rejectsNonDescendingPageBeforePersistingAnything() {
        AtomicInteger storeCalls = new AtomicInteger();
        var source = new ScriptedSource(List.of(List.of(
                candle(TO.minusSeconds(120), true), candle(TO.minusSeconds(60), true))));
        SpotCandleBackfillService service = new SpotCandleBackfillService(source, candles -> {
            storeCalls.incrementAndGet();
            return new CandleAppendResult(candles.size(), candles.size(), 0);
        }, duration -> {});

        assertThatThrownBy(() -> service.backfill("BTC-USDT", CandleBar.M1, FROM, TO, 300, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("倒序");
        assertThat(storeCalls).hasValue(0);
    }

    @Test
    void rejectsNonNinetyDayWindowBeforeCallingSource() {
        AtomicInteger calls = new AtomicInteger();
        SpotCandleBackfillService service = new SpotCandleBackfillService(
                (instrument, bar, limit, after) -> { calls.incrementAndGet(); return List.of(); },
                candles -> { calls.incrementAndGet(); return new CandleAppendResult(0, 0, 0); }, duration -> {});

        assertThatThrownBy(() -> service.backfill(
                "BTC-USDT", CandleBar.M1, FROM, TO.minusSeconds(1), 300, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("90 天");
        assertThat(calls).hasValue(0);
    }

    @Test
    void publishesCommittedProgressAfterEveryPage() {
        var source = new ScriptedSource(List.of(
                List.of(candle(TO.minusSeconds(60), true)),
                List.of(candle(FROM, true))));
        var progress = new ArrayList<app.cryptoincome.grid.application.SpotCandleBackfillProgress>();
        SpotCandleBackfillService service = new SpotCandleBackfillService(source,
                candles -> new CandleAppendResult(candles.size(), candles.size(), 0), duration -> {});

        service.backfill("BTC-USDT", CandleBar.M1, FROM, TO, 300, 10, progress::add);

        assertThat(progress).hasSize(2);
        assertThat(progress).extracting(app.cryptoincome.grid.application.SpotCandleBackfillProgress::pages)
                .containsExactly(1, 2);
        assertThat(progress.getLast().completedInWindow()).isEqualTo(2);
        assertThat(progress.getLast().cursor()).isEqualTo(FROM);
    }

    private static SpotCandle candle(Instant time, boolean confirmed) {
        return new SpotCandle(
                "OKX", "BTC-USDT", CandleBar.M1, time,
                new BigDecimal("64000"), new BigDecimal("64100"),
                new BigDecimal("63900"), new BigDecimal("64050"),
                BigDecimal.ONE, new BigDecimal("64000"), confirmed,
                "/api/v5/market/history-candles", "[]",
                Long.toHexString(time.toEpochMilli()) + "0".repeat(64 - Long.toHexString(time.toEpochMilli()).length()),
                Instant.parse("2026-08-19T08:00:00Z"));
    }

    private static final class ScriptedSource implements app.cryptoincome.grid.domain.SpotCandlePageSource {
        private final List<List<SpotCandle>> pages;
        private int index;

        private ScriptedSource(List<List<SpotCandle>> pages) { this.pages = pages; }

        @Override
        public List<SpotCandle> fetchPage(String instrumentId, CandleBar bar, int limit, Instant after) {
            return index < pages.size() ? pages.get(index++) : List.of();
        }
    }
}
