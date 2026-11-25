package app.cryptoincome.assessment;

import app.cryptoincome.assessment.application.PhaseOneAssessmentService;
import app.cryptoincome.assessment.domain.EvidenceStatus;
import app.cryptoincome.assessment.domain.FundingRatePoint;
import app.cryptoincome.assessment.domain.OpeningCost;
import app.cryptoincome.assessment.domain.PhaseOneAssessmentRequest;
import app.cryptoincome.assessment.domain.Recommendation;
import app.cryptoincome.assessment.domain.SimpleEarnQuote;
import app.cryptoincome.assessment.domain.SmartArbitrageCosts;
import app.cryptoincome.assessment.domain.TerminalPolicy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PhaseOneAssessmentServiceTest {

    private static final Instant FROM = Instant.parse("2026-05-20T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-08-18T00:00:00Z");
    private static final Instant AS_OF = Instant.parse("2026-08-18T01:00:00Z");

    @Test
    void calculatesSimpleEarnAndFundingIncomeFromActualSettlements() {
        PhaseOneAssessmentService service = service(List.of(
                point("2026-05-20T08:00:00Z", "0.010000"),
                point("2026-05-20T16:00:00Z", "-0.002000"),
                point("2026-05-21T03:00:00Z", "0.004000")));

        var result = service.assess(completeRequest());

        assertThat(result.simpleEarnIncome().setScale(2, RoundingMode.HALF_UP)).isEqualByComparingTo("172.60");
        assertThat(result.fundingHistory().holdingReturn()).isEqualByComparingTo("0.012000");
        assertThat(result.fundingHistory().fundingIncome()).isEqualByComparingTo("228.57132");
        assertThat(result.fundingHistory().settlementCount()).isEqualTo(3);
        assertThat(result.fundingHistory().positiveSettlements()).isEqualTo(2);
        assertThat(result.fundingHistory().negativeSettlements()).isEqualTo(1);
        assertThat(result.estimatedFundingOnlyNetIncome().orElseThrow()).isEqualByComparingTo("162.37832");
        assertThat(result.incrementalIncomeVsSimpleEarn().orElseThrow().setScale(2, RoundingMode.HALF_UP))
                .isEqualByComparingTo("-10.22");
        assertThat(result.recommendation()).isEqualTo(Recommendation.PREFER_SIMPLE_EARN);
    }

    @Test
    void recommendsOnlyMicroValidationWhenCompleteConservativeInputsBeatBenchmark() {
        PhaseOneAssessmentService service = service(List.of(
                point("2026-05-20T08:00:00Z", "0.012000"),
                point("2026-06-20T08:00:00Z", "0.008000")));

        var result = service.assess(completeRequest());

        assertThat(result.evidenceStatus()).isEqualTo(EvidenceStatus.SUFFICIENT_FOR_MICRO_VALIDATION);
        assertThat(result.recommendation()).isEqualTo(Recommendation.MICRO_VALIDATE_ARBITRAGE);
        assertThat(result.maximumFirstLiveCapital()).contains(new BigDecimal("5000"));
        assertThat(result.incrementalIncomeVsSimpleEarn().orElseThrow()).isPositive();
    }

    @Test
    void unknownCostsAreNotTreatedAsZeroAndKeepRecommendationAtWatch() {
        SmartArbitrageCosts incomplete = new SmartArbitrageCosts(
                AS_OF, "OKX预览+用户假设",
                Optional.of(new OpeningCost.Aggregate(new BigDecimal("36.001"))),
                Optional.empty(), Optional.empty(), Optional.of(BigDecimal.ZERO), Optional.of(BigDecimal.ZERO));
        PhaseOneAssessmentRequest request = request(incomplete, TerminalPolicy.EXIT_TO_USDT, true);

        var result = service(List.of(point("2026-05-20T08:00:00Z", "0.020000"))).assess(request);

        assertThat(result.knownCost()).isEqualByComparingTo("36.001");
        assertThat(result.estimatedFundingOnlyNetIncome()).isEmpty();
        assertThat(result.incrementalIncomeVsSimpleEarn()).isEmpty();
        assertThat(result.unknowns()).contains("退出成本", "滑点成本");
        assertThat(result.evidenceStatus()).isEqualTo(EvidenceStatus.INSUFFICIENT);
        assertThat(result.recommendation()).isEqualTo(Recommendation.WATCH_SMART_ARBITRAGE);
    }

    @Test
    void unconfirmedSimpleEarnTermsOrOpenTerminalPolicyCannotProduceActionableAdvice() {
        var unconfirmed = service(List.of(point("2026-05-20T08:00:00Z", "0.020000")))
                .assess(request(completeCosts(), TerminalPolicy.EXIT_TO_USDT, false));
        var keptOpen = service(List.of(point("2026-05-20T08:00:00Z", "0.020000")))
                .assess(request(completeCosts(), TerminalPolicy.KEEP_STRATEGY_OPEN, true));

        assertThat(unconfirmed.recommendation()).isEqualTo(Recommendation.INSUFFICIENT_EVIDENCE);
        assertThat(keptOpen.recommendation()).isEqualTo(Recommendation.WATCH_SMART_ARBITRAGE);
        assertThat(keptOpen.estimatedFundingOnlyNetIncome()).isEmpty();
    }

    @Test
    void openingAggregateAndComponentsHaveOneMutuallyExclusiveTypedAmount() {
        OpeningCost aggregate = new OpeningCost.Aggregate(new BigDecimal("36.001"));
        OpeningCost components = new OpeningCost.Components(
                new BigDecimal("15.810"), new BigDecimal("13.334"), new BigDecimal("6.858"));

        assertThat(aggregate.amount()).isEqualByComparingTo("36.001");
        assertThat(components.amount()).isEqualByComparingTo("36.002");
    }

    @Test
    void rejectsDifferentHorizonOrInvalidMoneyBeforeReadingFacts() {
        PhaseOneAssessmentService service = new PhaseOneAssessmentService((exchange, instrument, from, to, asOf) -> {
            throw new AssertionError("非法请求不应读取事实");
        });
        assertThatThrownBy(() -> new PhaseOneAssessmentRequest(
                AS_OF, FROM, TO.minusSeconds(1), new BigDecimal("20000"),
                new SimpleEarnQuote(new BigDecimal("0.035"), 90, AS_OF, "OKX确认页", true),
                new BigDecimal("19047.61"), completeCosts(), TerminalPolicy.EXIT_TO_USDT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("90 天");
    }

    @Test
    void rejectsCostSnapshotFromAfterEvaluationAsOf() {
        SmartArbitrageCosts futureCosts = new SmartArbitrageCosts(
                AS_OF.plusSeconds(1), "未来快照",
                Optional.of(new OpeningCost.Aggregate(BigDecimal.ZERO)),
                Optional.of(BigDecimal.ZERO), Optional.of(BigDecimal.ZERO),
                Optional.of(BigDecimal.ZERO), Optional.of(BigDecimal.ZERO));

        assertThatThrownBy(() -> request(futureCosts, TerminalPolicy.EXIT_TO_USDT, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("成本快照时间");
    }

    private static PhaseOneAssessmentService service(List<FundingRatePoint> points) {
        return new PhaseOneAssessmentService((exchange, instrument, from, to, asOf) -> points);
    }

    private static PhaseOneAssessmentRequest completeRequest() {
        return request(completeCosts(), TerminalPolicy.EXIT_TO_USDT, true);
    }

    private static PhaseOneAssessmentRequest request(
            SmartArbitrageCosts costs, TerminalPolicy terminalPolicy, boolean termsConfirmed) {
        return new PhaseOneAssessmentRequest(
                AS_OF, FROM, TO, new BigDecimal("20000"),
                new SimpleEarnQuote(new BigDecimal("0.035"), 90, AS_OF, "OKX确认页", termsConfirmed),
                new BigDecimal("19047.61"), costs, terminalPolicy);
    }

    private static SmartArbitrageCosts completeCosts() {
        return new SmartArbitrageCosts(
                AS_OF, "OKX预览+用户假设",
                Optional.of(new OpeningCost.Aggregate(new BigDecimal("36.001"))),
                Optional.of(new BigDecimal("20.192")),
                Optional.of(new BigDecimal("10")),
                Optional.of(BigDecimal.ZERO),
                Optional.of(BigDecimal.ZERO));
    }

    private static FundingRatePoint point(String fundingTime, String realizedRate) {
        return new FundingRatePoint(Instant.parse(fundingTime), new BigDecimal(realizedRate));
    }
}
