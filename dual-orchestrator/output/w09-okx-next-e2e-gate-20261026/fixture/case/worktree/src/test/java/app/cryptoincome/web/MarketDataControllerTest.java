package app.cryptoincome.web;

import app.cryptoincome.grid.domain.CandleWindowCoverage;
import app.cryptoincome.market.application.MarketDataStatus;
import app.cryptoincome.market.application.MarketDataStatusService;
import app.cryptoincome.market.domain.BtcMarketTick;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketDataControllerTest {
    @Test
    void projectsOnlyBackendOwnedTickerAndCoverageFacts() {
        Instant checkedAt = Instant.parse("2026-08-22T02:20:30Z");
        Instant to = Instant.parse("2026-08-22T02:20:00Z");
        Instant from = to.minusSeconds(90L * 24 * 60 * 60);
        BtcMarketTick tick = new BtcMarketTick("OKX", "BTC-USDT", new BigDecimal("64123.4"),
                new BigDecimal("64123.3"), new BigDecimal("64123.5"), checkedAt.minusSeconds(5), checkedAt,
                "/api/v5/market/ticker?instId=BTC-USDT", "{}", "a".repeat(64));
        CandleWindowCoverage coverage = new CandleWindowCoverage(from, to, 129600, 129600,
                from, to.minusSeconds(60), true);
        MarketDataStatusService service = mock(MarketDataStatusService.class);
        when(service.refresh()).thenReturn(new MarketDataStatus(checkedAt, tick, true, coverage,
                null, null, null, MarketDataStatus.Readiness.READY));

        var result = new MarketDataController(service).refresh();

        assertThat(result.readiness()).isEqualTo("READY");
        assertThat(result.ticker().lastPrice()).isEqualByComparingTo("64123.4");
        assertThat(result.ticker().providerTimestamp()).isEqualTo(checkedAt.minusSeconds(5));
        assertThat(result.candles().actualMinutes()).isEqualTo(129600);
        assertThat(result.candles().missingMinutes()).isZero();
        assertThat(result.candles().complete()).isTrue();
        verify(service).refresh();
    }

    @Test
    void manualRefreshUsesTheExplicitCooldownBypassEndpoint() {
        Instant checkedAt = Instant.parse("2026-08-22T02:20:30Z");
        Instant to = Instant.parse("2026-08-22T02:20:00Z");
        CandleWindowCoverage coverage = new CandleWindowCoverage(to.minusSeconds(90L * 24 * 60 * 60),
                to, 129600, 129599, to.minusSeconds(90L * 24 * 60 * 60), to.minusSeconds(60), false);
        MarketDataStatusService service = mock(MarketDataStatusService.class);
        when(service.refreshManually()).thenReturn(new MarketDataStatus(checkedAt, null, false, coverage,
                null, null, null, MarketDataStatus.Readiness.NO_TICKER));

        var result = new MarketDataController(service).refreshManually();

        assertThat(result.readiness()).isEqualTo("NO_TICKER");
        verify(service).refreshManually();
    }
}
