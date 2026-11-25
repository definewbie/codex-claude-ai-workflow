package app.cryptoincome.grid;

import app.cryptoincome.grid.application.FixedGridPathSimulator;
import app.cryptoincome.grid.domain.GridBacktestRequest;
import app.cryptoincome.grid.domain.GridExitReason;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class FixedGridPathSimulatorTest {
    @Test
    void exposesGrossAttributionWithoutChangingLegacyNetFields() {
        GridBacktestRequest request = new GridBacktestRequest(
                bd("1000"), bd("100"), bd("120"), 2, bd("0.001"), bd("0.002"), bd("10"));
        FixedGridPathSimulator.Session session = new FixedGridPathSimulator().start(request, bd("110"));

        session.moveUntilExit(bd("110"), bd("100"), Instant.parse("2026-01-01T01:00:00Z"));
        session.moveUntilExit(bd("100"), bd("120"), Instant.parse("2026-01-01T02:00:00Z"));
        session.moveUntilExit(bd("120"), bd("105"), Instant.parse("2026-01-01T03:00:00Z"));
        session.terminate(GridExitReason.WINDOW_END, Instant.parse("2026-01-02T00:00:00Z"), bd("105"));

        var legacy = session.result();
        var accounting = session.accounting();
        assertThat(legacy.completedGridProfit().add(legacy.inventoryPnl()))
                .isEqualByComparingTo(legacy.totalIncome());
        assertThat(accounting.attributedNetIncome()).isEqualByComparingTo(legacy.totalIncome());
        assertThat(accounting.grossCycleSpread()).isGreaterThan(legacy.completedGridProfit());
        assertThat(accounting.gridTradingFee()).isPositive();
        assertThat(accounting.entryExitFee()).isPositive();
        assertThat(accounting.exitSlippage()).isPositive();
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
