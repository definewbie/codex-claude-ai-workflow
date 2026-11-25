package app.cryptoincome.grid;

import app.cryptoincome.grid.application.FixedGridAssessmentService;
import app.cryptoincome.grid.application.FixedGridBacktester;
import app.cryptoincome.grid.domain.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class FixedGridAssessmentServiceTest {
    private static final Instant FROM = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-04-01T00:00:00Z");
    private static final Instant AS_OF = Instant.parse("2026-04-02T00:00:00Z");

    @Test
    void comparesBothPathsWithSimpleEarnAndBuyHoldBeforeMicroValidation() {
        var service = service(oscillatingCandles());

        FixedGridAssessment result = service.assess(request("1", "1", "1"));

        assertThat(result.recommendation()).isEqualTo(GridAssessmentRecommendation.MICRO_VALIDATE_GRID);
        assertThat(result.maximumFirstLiveCapital()).contains(new BigDecimal("5000"));
        assertThat(result.worsePathIncome()).isPositive();
        assertThat(result.worsePathIncome()).isGreaterThan(result.simpleEarnIncome());
        assertThat(result.worsePathIncome()).isGreaterThan(result.buyAndHoldIncome());
        assertThat(result.sensitivity()).isNotEmpty();
    }

    @Test
    void rejectsCandidateWhenDrawdownGuardrailFailsEvenIfIncomeIsAttractive() {
        var service = service(oscillatingCandles());

        FixedGridAssessment result = service.assess(request("0", "1", "1"));

        assertThat(result.recommendation()).isEqualTo(GridAssessmentRecommendation.KEEP_OBSERVING_GRID);
        assertThat(result.maximumFirstLiveCapital()).isEmpty();
        assertThat(result.reasons()).anyMatch(reason -> reason.contains("最大回撤"));
    }

    @Test
    void rejectsAnyAppFeatureThatTheFixedGridSimulatorDoesNotModel() {
        GridConfigurationSnapshot unsupported = configuration(true, false, false, false, Optional.empty(), Optional.empty());
        var service = new FixedGridAssessmentService(
                (id, asOf) -> Optional.of(unsupported),
                (exchange, instrument, bar, from, to, asOf) -> oscillatingCandles(),
                new FixedGridBacktester());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.assess(request("1", "1", "1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("复投、自动赚币、移动网格、止盈止损全部关闭");
    }

    @Test
    void usesTheFirstEligibleMinuteAsTheSharedGridAndBenchmarkStart() {
        List<SpotCandle> candles = new ArrayList<>(oscillatingCandles());
        for (int i = 0; i < 10; i++) {
            SpotCandle original = candles.get(i);
            candles.set(i, new SpotCandle(
                    original.exchange(), original.instrumentId(), original.bar(), original.openTime(),
                    new BigDecimal("140"), new BigDecimal("141"), new BigDecimal("139"), new BigDecimal("140"),
                    original.baseVolume(), original.quoteVolume(), true, original.sourceEndpoint(),
                    original.sourcePayload(), original.payloadHash(), original.observedAt()));
        }

        FixedGridAssessment result = service(List.copyOf(candles)).assess(request("1", "1", "1"));

        assertThat(result.backtest().from()).isEqualTo(FROM.plusSeconds(600));
        BigDecimal activeDays = new BigDecimal(Duration.between(result.backtest().from(), TO).toSeconds())
                .divide(new BigDecimal("86400"), java.math.MathContext.DECIMAL128);
        BigDecimal expectedSimple = new BigDecimal("20000").multiply(new BigDecimal("0.035"))
                .multiply(activeDays).divide(new BigDecimal("365"), java.math.MathContext.DECIMAL128);
        assertThat(result.simpleEarnIncome()).isEqualByComparingTo(expectedSimple);
    }

    @Test
    void scenarioOverridesStaySeparateFromTheConfirmedOkxConfiguration() {
        var service = service(oscillatingCandles());
        GridScenarioParameters scenario = new GridScenarioParameters(
                new BigDecimal("100"), new BigDecimal("120"), 3,
                Optional.empty(), Optional.empty());

        FixedGridAssessment result = service.assessScenario(request("1", "1", "1"), scenario);

        assertThat(result.configurationSnapshot().lowerPrice()).isEqualByComparingTo("90");
        assertThat(result.configurationSnapshot().upperPrice()).isEqualByComparingTo("130");
        assertThat(result.scenario()).isEqualTo(scenario);
        assertThat(result.scenarioOverride()).isTrue();
        assertThat(result.backtest().request().lowerPrice()).isEqualByComparingTo("100");
        assertThat(result.backtest().request().upperPrice()).isEqualByComparingTo("120");
        assertThat(result.backtest().request().gridCount()).isEqualTo(3);
    }

    private static FixedGridAssessmentService service(List<SpotCandle> candles) {
        GridConfigurationSnapshot snapshot = configuration(
                false, false, false, false, Optional.empty(), Optional.empty());
        return new FixedGridAssessmentService(
                (id, asOf) -> id == 7 && !asOf.isBefore(snapshot.observedAt()) ? Optional.of(snapshot) : Optional.empty(),
                (exchange, instrument, bar, from, to, asOf) -> candles,
                new FixedGridBacktester());
    }

    private static GridConfigurationSnapshot configuration(
            boolean reinvest, boolean autoEarn, boolean trailingUp, boolean trailingDown,
            Optional<BigDecimal> takeProfit, Optional<BigDecimal> stopLoss) {
        return new GridConfigurationSnapshot(
                "OKX", "BTC-USDT", GridConfigurationSource.OKX_APP_MANUAL_CONFIRMATION, "3-6个月",
                new BigDecimal("90"), new BigDecimal("130"), 2, GridType.ARITHMETIC,
                Optional.of(new BigDecimal("0.2147")), Optional.of(new BigDecimal("0.0061")),
                Optional.of(new BigDecimal("0.0107")),
                reinvest, autoEarn, trailingUp, trailingDown, takeProfit, stopLoss,
                "OKX手机App复制AI参数", Instant.parse("2026-01-01T00:00:00Z"));
    }

    private static FixedGridAssessmentRequest request(String drawdown, String divergence, String btcRatio) {
        return new FixedGridAssessmentRequest(
                AS_OF, FROM, TO, 7, new BigDecimal("20000"), new BigDecimal("0.035"),
                new BigDecimal("0.0006"), new BigDecimal("0.0007"), new BigDecimal("5"),
                new BigDecimal(drawdown), new BigDecimal(divergence), new BigDecimal(btcRatio));
    }

    private static List<SpotCandle> oscillatingCandles() {
        List<SpotCandle> result = new ArrayList<>(129_600);
        for (int i = 0; i < 129_600; i++) {
            Instant time = FROM.plusSeconds(60L * i);
            result.add(new SpotCandle(
                    "OKX", "BTC-USDT", CandleBar.M1, time,
                    new BigDecimal("110"), new BigDecimal("130"), new BigDecimal("90"), new BigDecimal("110"),
                    BigDecimal.ONE, new BigDecimal("110"), true, "/history", "[]",
                    String.format("%064x", i + 1), AS_OF));
        }
        return List.copyOf(result);
    }
}
