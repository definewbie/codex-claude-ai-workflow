package app.cryptoincome.market.application;

import app.cryptoincome.grid.application.SpotCandleBackfillService;
import app.cryptoincome.grid.application.SpotCandleCoverageQuery;
import app.cryptoincome.grid.application.SpotCandleTailRefreshResult;
import app.cryptoincome.grid.application.SpotCandleTailRefreshService;
import app.cryptoincome.grid.domain.CandleBar;
import app.cryptoincome.grid.domain.CandleWindowCoverage;
import app.cryptoincome.grid.sync.CandleSyncExecution;
import app.cryptoincome.grid.sync.CandleSyncAutoStartDecision;
import app.cryptoincome.grid.sync.CandleSyncExecutionService;
import app.cryptoincome.market.domain.BtcMarketTick;
import app.cryptoincome.market.domain.BtcMarketTickSource;
import app.cryptoincome.market.domain.BtcMarketTickStore;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class MarketDataStatusService {
    private static final Duration TICKER_FRESHNESS = Duration.ofMinutes(2);
    private static final int MAX_PROVIDER_CONFIRMATION_LAG_MINUTES = 2;
    private final BtcMarketTickSource tickSource;
    private final BtcMarketTickStore tickStore;
    private final SpotCandleTailRefreshService tailRefreshService;
    private final SpotCandleCoverageQuery coverageQuery;
    private final CandleSyncExecutionService candleSyncService;
    private final Clock clock;

    public MarketDataStatusService(
            BtcMarketTickSource tickSource,
            BtcMarketTickStore tickStore,
            SpotCandleTailRefreshService tailRefreshService,
            SpotCandleCoverageQuery coverageQuery,
            CandleSyncExecutionService candleSyncService,
            Clock clock) {
        this.tickSource = tickSource;
        this.tickStore = tickStore;
        this.tailRefreshService = tailRefreshService;
        this.coverageQuery = coverageQuery;
        this.candleSyncService = candleSyncService;
        this.clock = clock;
    }

    public MarketDataStatus refresh() {
        return refresh(false);
    }

    public MarketDataStatus refreshManually() {
        return refresh(true);
    }

    private MarketDataStatus refresh(boolean manualFullSync) {
        BtcMarketTick tick = tickSource.fetch("BTC-USDT");
        tickStore.append(tick);
        Instant to = clock.instant().truncatedTo(ChronoUnit.MINUTES);
        SpotCandleTailRefreshResult tail = tailRefreshService.refresh(to);
        CandleWindowCoverage coverage = coverage(to, clock.instant());
        CandleSyncExecution execution = candleSyncService.active().orElse(null);
        Instant automaticRetryAt = null;
        if (!coverage.complete() && !waitingForProviderConfirmation(coverage) && execution == null) {
            if (manualFullSync) {
                execution = candleSyncService.start(to);
            } else {
                CandleSyncAutoStartDecision decision = candleSyncService.startAutomatically(to);
                execution = decision.execution();
                automaticRetryAt = decision.automaticRetryAt();
            }
        }
        return build(clock.instant(), tick, coverage, tail, execution, automaticRetryAt);
    }

    public MarketDataStatus current() {
        Instant now = clock.instant();
        Instant to = now.truncatedTo(ChronoUnit.MINUTES);
        BtcMarketTick tick = tickStore.latest("OKX", "BTC-USDT").orElse(null);
        CandleWindowCoverage coverage = coverage(to, now);
        CandleSyncExecution execution = candleSyncService.active().orElse(null);
        Instant automaticRetryAt = null;
        if (!coverage.complete() && !waitingForProviderConfirmation(coverage) && execution == null) {
            CandleSyncAutoStartDecision decision = candleSyncService.automaticStatus();
            execution = decision.execution();
            automaticRetryAt = decision.automaticRetryAt();
        }
        return build(now, tick, coverage, null, execution, automaticRetryAt);
    }

    private CandleWindowCoverage coverage(Instant to, Instant asOf) {
        return coverageQuery.inspect("OKX", "BTC-USDT", CandleBar.M1,
                to.minus(SpotCandleBackfillService.REQUIRED_WINDOW), to, asOf);
    }

    private MarketDataStatus build(
            Instant now, BtcMarketTick tick, CandleWindowCoverage coverage,
            SpotCandleTailRefreshResult tail, CandleSyncExecution execution, Instant automaticRetryAt) {
        boolean fresh = tick != null
                && !tick.providerTimestamp().isBefore(now.minus(TICKER_FRESHNESS))
                && !tick.providerTimestamp().isAfter(now.plusSeconds(30));
        MarketDataStatus.Readiness readiness;
        if (tick == null) readiness = MarketDataStatus.Readiness.NO_TICKER;
        else if (!fresh) readiness = MarketDataStatus.Readiness.STALE_TICKER;
        else if (execution != null && !execution.terminal()) readiness = MarketDataStatus.Readiness.SYNCING_CANDLES;
        else if (coverage.complete()) readiness = MarketDataStatus.Readiness.READY;
        else if (waitingForProviderConfirmation(coverage)) {
            readiness = MarketDataStatus.Readiness.WAITING_LATEST_CANDLE;
        }
        else readiness = MarketDataStatus.Readiness.INCOMPLETE_CANDLES;
        return new MarketDataStatus(now, tick, fresh, coverage, tail, execution, automaticRetryAt, readiness);
    }

    private static boolean waitingForProviderConfirmation(CandleWindowCoverage coverage) {
        return coverage.onlyTrailingMinutesMissing(MAX_PROVIDER_CONFIRMATION_LAG_MINUTES);
    }
}
