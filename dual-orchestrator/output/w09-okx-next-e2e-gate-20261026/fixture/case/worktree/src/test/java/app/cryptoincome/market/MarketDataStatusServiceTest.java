package app.cryptoincome.market;

import app.cryptoincome.grid.application.SpotCandleCoverageQuery;
import app.cryptoincome.grid.application.SpotCandleTailRefreshResult;
import app.cryptoincome.grid.application.SpotCandleTailRefreshService;
import app.cryptoincome.grid.domain.CandleWindowCoverage;
import app.cryptoincome.grid.sync.CandleSyncExecution;
import app.cryptoincome.grid.sync.CandleSyncAutoStartDecision;
import app.cryptoincome.grid.sync.CandleSyncExecutionService;
import app.cryptoincome.grid.sync.CandleSyncState;
import app.cryptoincome.market.application.MarketDataStatus;
import app.cryptoincome.market.application.MarketDataStatusService;
import app.cryptoincome.market.domain.BtcMarketTick;
import app.cryptoincome.market.domain.BtcMarketTickStore;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MarketDataStatusServiceTest {
    @Test
    void refreshStoresActualTickerTailsCandlesAndStartsFullSyncWhenWindowIsIncomplete() {
        Instant now = Instant.parse("2026-08-22T00:00:30Z");
        Instant to = Instant.parse("2026-08-22T00:00:00Z");
        BtcMarketTick tick = tick(now.minusSeconds(5), now);
        BtcMarketTickStore store = mock(BtcMarketTickStore.class);
        SpotCandleTailRefreshService tail = mock(SpotCandleTailRefreshService.class);
        when(tail.refresh(to)).thenReturn(new SpotCandleTailRefreshResult(to, 300, 299, 1, 298));
        SpotCandleCoverageQuery coverage = (exchange, instrument, bar, from, windowTo, asOf) ->
                new CandleWindowCoverage(from, windowTo, 129_600, 300,
                        windowTo.minusSeconds(300 * 60L), windowTo.minusSeconds(60), false);
        CandleSyncExecutionService sync = mock(CandleSyncExecutionService.class);
        when(sync.active()).thenReturn(Optional.empty());
        CandleSyncExecution running = new CandleSyncExecution(9, CandleSyncState.QUEUED, "BTC-USDT", "1m",
                to.minus(Duration.ofDays(90)), to, null, 0, 0, 0, 0, 0,
                null, null, null, now, null, null, now);
        when(sync.startAutomatically(to)).thenReturn(new CandleSyncAutoStartDecision(running, null));
        var service = new MarketDataStatusService(instrument -> tick, store, tail, coverage, sync,
                Clock.fixed(now, ZoneOffset.UTC));

        var result = service.refresh();

        verify(store).append(tick);
        verify(tail).refresh(to);
        verify(sync).startAutomatically(to);
        assertThat(result.readiness()).isEqualTo(MarketDataStatus.Readiness.SYNCING_CANDLES);
        assertThat(result.candleCoverage().missingMinutes()).isEqualTo(129_300);
    }

    @Test
    void automaticRefreshProjectsCooldownWithoutStartingAnotherFullBackfill() {
        Instant now = Instant.parse("2026-08-22T00:00:30Z");
        Instant to = Instant.parse("2026-08-22T00:00:00Z");
        Instant retryAt = now.plus(Duration.ofHours(6));
        BtcMarketTick tick = tick(now.minusSeconds(5), now);
        SpotCandleTailRefreshService tail = mock(SpotCandleTailRefreshService.class);
        when(tail.refresh(to)).thenReturn(new SpotCandleTailRefreshResult(to, 1, 1, 0, 1));
        SpotCandleCoverageQuery coverage = (exchange, instrument, bar, from, windowTo, asOf) ->
                new CandleWindowCoverage(from, windowTo, 129_600, 129_599,
                        from, windowTo.minusSeconds(60), false);
        CandleSyncExecution failed = new CandleSyncExecution(8, CandleSyncState.FAILED, "BTC-USDT", "1m",
                to.minus(Duration.ofDays(90)), to, null, 432, 129_600, 129_599, 0, 129_599,
                to.minus(Duration.ofDays(90)), "INCOMPLETE_COVERAGE", "缺少1根", now.minusSeconds(60),
                now.minusSeconds(50), now.minusSeconds(40), now.minusSeconds(40));
        CandleSyncExecutionService sync = mock(CandleSyncExecutionService.class);
        when(sync.active()).thenReturn(Optional.empty());
        when(sync.startAutomatically(to)).thenReturn(new CandleSyncAutoStartDecision(failed, retryAt));
        var service = new MarketDataStatusService(instrument -> tick, mock(BtcMarketTickStore.class),
                tail, coverage, sync, Clock.fixed(now, ZoneOffset.UTC));

        MarketDataStatus result = service.refresh();

        assertThat(result.readiness()).isEqualTo(MarketDataStatus.Readiness.INCOMPLETE_CANDLES);
        assertThat(result.automaticRetryAt()).isEqualTo(retryAt);
        assertThat(result.candleSync()).isEqualTo(failed);
        verify(sync, never()).start(to);
    }

    @Test
    void trailingProviderConfirmationLagDoesNotStartAFullBackfill() {
        Instant now = Instant.parse("2026-08-22T00:00:30Z");
        Instant to = Instant.parse("2026-08-22T00:00:00Z");
        Instant from = to.minus(Duration.ofDays(90));
        BtcMarketTick tick = tick(now.minusSeconds(5), now);
        SpotCandleTailRefreshService tail = mock(SpotCandleTailRefreshService.class);
        when(tail.refresh(to)).thenReturn(new SpotCandleTailRefreshResult(to, 300, 299, 1, 298));
        SpotCandleCoverageQuery coverage = (exchange, instrument, bar, windowFrom, windowTo, asOf) ->
                new CandleWindowCoverage(windowFrom, windowTo, 129_600, 129_599,
                        from, to.minusSeconds(120), false);
        CandleSyncExecutionService sync = mock(CandleSyncExecutionService.class);
        when(sync.active()).thenReturn(Optional.empty());
        var service = new MarketDataStatusService(instrument -> tick, mock(BtcMarketTickStore.class),
                tail, coverage, sync, Clock.fixed(now, ZoneOffset.UTC));

        MarketDataStatus result = service.refresh();

        assertThat(result.readiness()).isEqualTo(MarketDataStatus.Readiness.WAITING_LATEST_CANDLE);
        assertThat(result.candleCoverage().missingMinutes()).isEqualTo(1);
        verify(sync, never()).startAutomatically(to);
        verify(sync, never()).start(to);
    }

    @Test
    void manualRefreshBypassesAutomaticCooldownPolicy() {
        Instant now = Instant.parse("2026-08-22T00:00:30Z");
        Instant to = Instant.parse("2026-08-22T00:00:00Z");
        BtcMarketTick tick = tick(now.minusSeconds(5), now);
        SpotCandleTailRefreshService tail = mock(SpotCandleTailRefreshService.class);
        when(tail.refresh(to)).thenReturn(new SpotCandleTailRefreshResult(to, 1, 1, 0, 1));
        SpotCandleCoverageQuery coverage = (exchange, instrument, bar, from, windowTo, asOf) ->
                new CandleWindowCoverage(from, windowTo, 129_600, 129_599,
                        from, windowTo.minusSeconds(60), false);
        CandleSyncExecution running = new CandleSyncExecution(10, CandleSyncState.QUEUED, "BTC-USDT", "1m",
                to.minus(Duration.ofDays(90)), to, null, 0, 0, 0, 0, 0,
                null, null, null, now, null, null, now);
        CandleSyncExecutionService sync = mock(CandleSyncExecutionService.class);
        when(sync.active()).thenReturn(Optional.empty());
        when(sync.start(to)).thenReturn(running);
        var service = new MarketDataStatusService(instrument -> tick, mock(BtcMarketTickStore.class),
                tail, coverage, sync, Clock.fixed(now, ZoneOffset.UTC));

        MarketDataStatus result = service.refreshManually();

        assertThat(result.readiness()).isEqualTo(MarketDataStatus.Readiness.SYNCING_CANDLES);
        verify(sync).start(to);
        verify(sync, never()).startAutomatically(to);
    }

    @Test
    void keepsReadinessAtSyncingUntilTheFullDatabaseVerificationIsTerminal() {
        Instant now = Instant.parse("2026-08-22T00:00:30Z");
        Instant to = Instant.parse("2026-08-22T00:00:00Z");
        Instant from = to.minus(Duration.ofDays(90));
        BtcMarketTickStore store = mock(BtcMarketTickStore.class);
        when(store.latest("OKX", "BTC-USDT")).thenReturn(Optional.of(tick(now.minusSeconds(5), now)));
        SpotCandleCoverageQuery coverage = (exchange, instrument, bar, windowFrom, windowTo, asOf) ->
                new CandleWindowCoverage(windowFrom, windowTo, 129_600, 129_600,
                        windowFrom, windowTo.minusSeconds(60), true);
        CandleSyncExecution running = new CandleSyncExecution(9, CandleSyncState.RUNNING, "BTC-USDT", "1m",
                from, to, null, 200, 60_000, 60_000, 100, 59_900,
                to.minus(Duration.ofDays(40)), null, null, now.minusSeconds(60), now.minusSeconds(50), null, now);
        CandleSyncExecutionService sync = mock(CandleSyncExecutionService.class);
        when(sync.active()).thenReturn(Optional.of(running));
        var service = new MarketDataStatusService(instrument -> tick(now.minusSeconds(5), now), store,
                mock(SpotCandleTailRefreshService.class), coverage, sync, Clock.fixed(now, ZoneOffset.UTC));

        MarketDataStatus result = service.current();

        assertThat(result.candleCoverage().complete()).isTrue();
        assertThat(result.readiness()).isEqualTo(MarketDataStatus.Readiness.SYNCING_CANDLES);
    }

    private static BtcMarketTick tick(Instant providerAt, Instant observedAt) {
        return new BtcMarketTick("OKX", "BTC-USDT", new BigDecimal("64185.1"),
                new BigDecimal("64185.0"), new BigDecimal("64185.2"), providerAt, observedAt,
                "/api/v5/market/ticker", "{}", "a".repeat(64));
    }
}
