package app.cryptoincome.grid;

import app.cryptoincome.grid.application.GridParameterSnapshotService;
import app.cryptoincome.grid.domain.GridDuration;
import app.cryptoincome.grid.domain.GridParameterSnapshot;
import app.cryptoincome.grid.domain.GridType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GridParameterSnapshotServiceTest {

    @Test
    void capturesTypedExternalFactBeforePersistingIt() {
        AtomicInteger sourceCalls = new AtomicInteger();
        AtomicInteger storeCalls = new AtomicInteger();
        GridParameterSnapshot snapshot = snapshot();
        GridParameterSnapshotService service = new GridParameterSnapshotService(
                (instrument, duration) -> {
                    sourceCalls.incrementAndGet();
                    assertThat(instrument).isEqualTo("BTC-USDT");
                    assertThat(duration).isEqualTo(GridDuration.D180);
                    return snapshot;
                },
                value -> {
                    storeCalls.incrementAndGet();
                    assertThat(value).isSameAs(snapshot);
                    return 42L;
                });

        var captured = service.capture("BTC-USDT", GridDuration.D180);

        assertThat(captured.id()).isEqualTo(42L);
        assertThat(captured.snapshot()).isSameAs(snapshot);
        assertThat(sourceCalls).hasValue(1);
        assertThat(storeCalls).hasValue(1);
    }

    @Test
    void rejectsOutOfScopeInstrumentBeforeCallingSourceOrStore() {
        AtomicInteger calls = new AtomicInteger();
        GridParameterSnapshotService service = new GridParameterSnapshotService(
                (instrument, duration) -> { calls.incrementAndGet(); return snapshot(); },
                value -> { calls.incrementAndGet(); return 1L; });

        assertThatThrownBy(() -> service.capture("ETH-USDT", GridDuration.D180))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BTC-USDT");
        assertThat(calls).hasValue(0);
    }

    static GridParameterSnapshot snapshot() {
        return new GridParameterSnapshot(
                "OKX", "BTC-USDT", GridDuration.D180, GridType.ARITHMETIC,
                new BigDecimal("47483.2"), new BigDecimal("76382.5"), 50,
                new BigDecimal("0.2487"), new BigDecimal("0.0060"), new BigDecimal("0.0106"),
                new BigDecimal("100"), "USDT", "/api/v5/tradingBot/grid/ai-param",
                "{\"fixture\":true}", "a".repeat(64), Instant.parse("2026-08-19T08:00:00Z"));
    }
}
