package app.cryptoincome.web;

import app.cryptoincome.grid.application.SpotCandleTailRefreshResult;
import app.cryptoincome.grid.domain.CandleWindowCoverage;
import app.cryptoincome.grid.domain.GridSourceException;
import app.cryptoincome.grid.sync.CandleSyncExecution;
import app.cryptoincome.market.application.MarketDataStatus;
import app.cryptoincome.market.application.MarketDataStatusService;
import app.cryptoincome.market.domain.BtcMarketTick;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;

@RestController
@RequestMapping("/api/workbench/market-data")
public class MarketDataController {
    private final MarketDataStatusService service;

    public MarketDataController(MarketDataStatusService service) {
        this.service = service;
    }

    @GetMapping
    public MarketDataView current() {
        return view(service.current());
    }

    @PostMapping("/refresh")
    public MarketDataView refresh() {
        return view(service.refresh());
    }

    @PostMapping("/refresh/manual")
    public MarketDataView refreshManually() {
        return view(service.refreshManually());
    }

    private static MarketDataView view(MarketDataStatus status) {
        return new MarketDataView(status.checkedAt(), status.readiness().name(),
                tick(status.latestTick()), status.tickerFresh(), coverage(status.candleCoverage()),
                tail(status.tailRefresh()), sync(status.candleSync()), status.automaticRetryAt());
    }

    private static TickView tick(BtcMarketTick tick) {
        return tick == null ? null : new TickView(tick.instrumentId(), tick.lastPrice(), tick.bidPrice(),
                tick.askPrice(), tick.providerTimestamp(), tick.observedAt(), tick.sourceEndpoint());
    }

    private static CoverageView coverage(CandleWindowCoverage value) {
        return new CoverageView(value.from(), value.to(), value.expectedMinutes(), value.actualMinutes(),
                value.missingMinutes(), value.firstOpenTime(), value.lastOpenTime(), value.complete());
    }

    private static TailRefreshView tail(SpotCandleTailRefreshResult value) {
        return value == null ? null : new TailRefreshView(value.toExclusive(), value.received(), value.completed(),
                value.newFacts(), value.duplicateFacts());
    }

    private static CandleSyncView sync(CandleSyncExecution value) {
        return value == null ? null : new CandleSyncView(value.id(), value.state().name(), value.from(), value.to(),
                value.pages(), value.completedInWindow(), value.cursor(), value.failureCategory(), value.failureMessage());
    }

    @ExceptionHandler(GridSourceException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public ErrorView sourceFailure(GridSourceException error) {
        return new ErrorView("MARKET_SOURCE_FAILURE",
                "OKX 公共行情暂时不可用，类别=" + error.category() + "，可重试=" + error.retryable());
    }

    public record MarketDataView(
            Instant checkedAt, String readiness, TickView ticker, boolean tickerFresh,
            CoverageView candles, TailRefreshView tailRefresh, CandleSyncView candleSync,
            Instant automaticRetryAt) {}
    public record TickView(
            String instrumentId, BigDecimal lastPrice, BigDecimal bidPrice, BigDecimal askPrice,
            Instant providerTimestamp, Instant observedAt, String sourceEndpoint) {}
    public record CoverageView(
            Instant from, Instant to, int expectedMinutes, int actualMinutes, int missingMinutes,
            Instant firstOpenTime, Instant lastOpenTime, boolean complete) {}
    public record TailRefreshView(
            Instant toExclusive, int received, int completed, int newFacts, int duplicateFacts) {}
    public record CandleSyncView(
            long id, String state, Instant from, Instant to, int pages, int completedInWindow,
            Instant cursor, String failureCategory, String failureMessage) {}
    public record ErrorView(String code, String message) {}
}
