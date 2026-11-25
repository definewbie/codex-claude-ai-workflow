package app.cryptoincome.cli;

import app.cryptoincome.grid.application.GridParameterSnapshotService;
import app.cryptoincome.grid.application.GridConfigurationSnapshotService;
import app.cryptoincome.grid.domain.GridDuration;
import app.cryptoincome.grid.domain.GridParameterSnapshot;
import app.cryptoincome.grid.domain.GridSourceException;
import app.cryptoincome.grid.domain.GridType;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class SyncProductCliRunnerTest {

    @Test
    void capturesAndPrintsCurrentGridParameterSnapshotWithoutTrading() {
        var invocation = invoke(new GridParameterSnapshotService(
                (instrument, duration) -> snapshot(), snapshot -> 17L),
                "sync-product", "grid-ai-params", "--instrument", "BTC-USDT", "--duration", "180D");

        assertThat(invocation.exitCode()).isZero();
        assertThat(invocation.output())
                .contains("网格参数快照已保存: id=17")
                .contains("BTC-USDT")
                .contains("区间=47483.2 - 76382.5 USDT")
                .contains("网格数=50")
                .contains("OKX展示年化=24.87%")
                .contains("不是本系统收益预测")
                .contains("只读公共接口，不会创建策略");
    }

    @Test
    void rejectsInvalidDurationBeforeExternalCall() {
        AtomicInteger calls = new AtomicInteger();
        var invocation = invoke(new GridParameterSnapshotService(
                (instrument, duration) -> { calls.incrementAndGet(); return snapshot(); },
                snapshot -> { calls.incrementAndGet(); return 1L; }),
                "sync-product", "grid-ai-params", "--instrument", "BTC-USDT", "--duration", "90D");

        assertThat(invocation.exitCode()).isEqualTo(SyncProductCliRunner.EXIT_ARGS);
        assertThat(invocation.output()).contains("duration 只允许 7D、30D、180D");
        assertThat(calls).hasValue(0);
    }

    @Test
    void reportsTypedSourceFailureWithoutPersistingAnything() {
        var invocation = invoke(new GridParameterSnapshotService(
                (instrument, duration) -> { throw new GridSourceException(
                        GridSourceException.Category.API_REJECTED, "50011", true, "limited", null); },
                snapshot -> { throw new AssertionError("来源失败时不能保存快照"); }),
                "sync-product", "grid-ai-params", "--instrument", "BTC-USDT", "--duration", "30D");

        assertThat(invocation.exitCode()).isEqualTo(SyncProductCliRunner.EXIT_SOURCE);
        assertThat(invocation.output()).contains("来源失败").contains("50011").contains("可重试=true");
    }

    @Test
    void recordsExplicitAppConfigurationWithoutConvertingThreeToSixMonthsIntoDays() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        SyncProductCliRunner runner = new SyncProductCliRunner(
                new GridParameterSnapshotService((instrument, duration) -> snapshot(), value -> 1L),
                new GridConfigurationSnapshotService(value -> 23L),
                new PrintStream(bytes, true, StandardCharsets.UTF_8));

        runner.run(new DefaultApplicationArguments(
                "sync-product", "grid-ui-config",
                "--instrument", "BTC-USDT", "--horizon", "3-6个月",
                "--lower-price", "46781.1", "--upper-price", "75680.3",
                "--grid-count", "50", "--grid-type", "arithmetic",
                "--displayed-annualized-rate", "0.2147",
                "--displayed-min-per-grid-rate", "0.0061",
                "--displayed-max-per-grid-rate", "0.0107",
                "--profit-reinvestment", "false", "--auto-earn", "false",
                "--trailing-up", "false", "--trailing-down", "false",
                "--take-profit", "disabled", "--stop-loss", "disabled",
                "--observed-at", "2026-08-19T08:00:00Z",
                "--source-reference", "OKX手机App复制AI参数后的手动创建页"));

        assertThat(runner.getExitCode()).isZero();
        assertThat(bytes.toString(StandardCharsets.UTF_8))
                .contains("config-id=23")
                .contains("3-6个月（非精确天数）")
                .contains("区间=46781.1 - 75680.3")
                .contains("页面展示年化=21.47%")
                .contains("页面单格收益率=0.61% - 1.07%")
                .contains("利润复投=关闭，自动赚币=关闭，上移=关闭，下移=关闭")
                .contains("止盈=关闭，止损=关闭")
                .contains("不会创建 OKX 策略");
    }

    private static Invocation invoke(GridParameterSnapshotService service, String... args) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        SyncProductCliRunner runner = new SyncProductCliRunner(
                service, new PrintStream(bytes, true, StandardCharsets.UTF_8));
        runner.run(new DefaultApplicationArguments(args));
        return new Invocation(runner.getExitCode(), bytes.toString(StandardCharsets.UTF_8));
    }

    private static GridParameterSnapshot snapshot() {
        return new GridParameterSnapshot(
                "OKX", "BTC-USDT", GridDuration.D180, GridType.ARITHMETIC,
                new BigDecimal("47483.2"), new BigDecimal("76382.5"), 50,
                new BigDecimal("0.2487"), new BigDecimal("0.0060"), new BigDecimal("0.0106"),
                new BigDecimal("100"), "USDT", "/api/v5/tradingBot/grid/ai-param",
                "{\"fixture\":true}", "a".repeat(64), Instant.parse("2026-08-19T08:00:00Z"));
    }

    private record Invocation(int exitCode, String output) {}
}
