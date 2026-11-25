package app.cryptoincome.market.application;

import app.cryptoincome.grid.application.SpotCandleTailRefreshResult;
import app.cryptoincome.grid.domain.CandleWindowCoverage;
import app.cryptoincome.grid.sync.CandleSyncExecution;
import app.cryptoincome.market.domain.BtcMarketTick;

import java.time.Instant;

public record MarketDataStatus(
        Instant checkedAt,
        BtcMarketTick latestTick,
        boolean tickerFresh,
        CandleWindowCoverage candleCoverage,
        SpotCandleTailRefreshResult tailRefresh,
        CandleSyncExecution candleSync,
        Instant automaticRetryAt,
        Readiness readiness
) {
    public enum Readiness {
        READY,
        WAITING_LATEST_CANDLE,
        SYNCING_CANDLES,
        INCOMPLETE_CANDLES,
        STALE_TICKER,
        NO_TICKER
    }
}
