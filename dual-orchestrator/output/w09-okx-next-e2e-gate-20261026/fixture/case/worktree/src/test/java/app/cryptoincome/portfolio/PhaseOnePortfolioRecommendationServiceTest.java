package app.cryptoincome.portfolio;

import app.cryptoincome.assessment.domain.Recommendation;
import app.cryptoincome.grid.domain.GridAssessmentRecommendation;
import app.cryptoincome.portfolio.application.PhaseOnePortfolioRecommendationService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PhaseOnePortfolioRecommendationServiceTest {
    private final PhaseOnePortfolioRecommendationService service = new PhaseOnePortfolioRecommendationService();

    @Test
    void keepsTheUserPlanButDoesNotCallUnsupportedSmartOrGridWeightsRecommended() {
        var result = service.recommend(new BigDecimal("200000"), new BigDecimal("20000"),
                new BigDecimal("0.65"), new BigDecimal("0.20"), new BigDecimal("0.05"),
                new BigDecimal("0.10"), GridAssessmentRecommendation.MICRO_VALIDATE_GRID,
                Recommendation.PREFER_SIMPLE_EARN, Duration.ofDays(90),
                new BigDecimal("172.60"), new BigDecimal("129.95"),
                new BigDecimal("117.57"), new BigDecimal("717.24"));

        assertThat(result.proposedAllocatedCapitalUsdt()).isEqualByComparingTo("200000");
        assertThat(result.recommendedAllocatedCapitalUsdt()).isEqualByComparingTo("200000");
        assertThat(result.allocations()).extracting(row -> row.productCode())
                .containsExactly("FIXED_EARN_90D", "LIQUID_USDT", "SMART_ARBITRAGE", "SPOT_GRID");
        assertThat(result.allocations().get(2).proposedAmountUsdt()).isEqualByComparingTo("10000");
        assertThat(result.allocations().get(2).recommendedAmountUsdt()).isZero();
        assertThat(result.allocations().get(2).evidenceStatus()).isEqualTo("已纳入回测·未通过实盘门槛");
        assertThat(result.allocations().get(3).proposedAmountUsdt()).isEqualByComparingTo("20000");
        assertThat(result.allocations().get(3).recommendedAmountUsdt()).isZero();
    }

    @Test
    void calculatesThePortfolioUsingTheUserAdjustedProductWeights() {
        var result = service.recommend(new BigDecimal("200000"), new BigDecimal("20000"),
                new BigDecimal("0.60"), BigDecimal.ZERO, new BigDecimal("0.10"),
                new BigDecimal("0.30"), GridAssessmentRecommendation.MICRO_VALIDATE_GRID,
                Recommendation.PREFER_SIMPLE_EARN, Duration.ofDays(90),
                new BigDecimal("172.60"), new BigDecimal("129.95"),
                new BigDecimal("117.57"), new BigDecimal("717.24"));

        assertThat(result.allocations().get(0).proposedProjectedNetIncomeUsdt())
                .isEqualByComparingTo("1035.60");
        assertThat(result.allocations().get(2).proposedProjectedNetIncomeUsdt())
                .isEqualByComparingTo("117.57");
        assertThat(result.allocations().get(3).proposedProjectedNetIncomeUsdt())
                .isEqualByComparingTo("2151.72");
        assertThat(result.proposedProjectedNetIncomeUsdt()).isEqualByComparingTo("3304.89");
        assertThat(result.proposedProjectedReturnRate()).isEqualByComparingTo("0.01652445");
        assertThat(result.proposedSimpleAnnualizedReturnRate())
                .isEqualByComparingTo("0.067015825");
        assertThat(result.recommendedProjectedNetIncomeUsdt()).isEqualByComparingTo("1640.70");
        assertThat(result.projectedIncrementalVsRecommendedUsdt()).isEqualByComparingTo("1664.19");
        assertThat(result.evaluationStatus()).isEqualTo("COMPLETE");
    }

    @Test
    void keepsUnknownNonZeroProductAsPartialInsteadOfRejectingThePlan() {
        var result = service.recommend(new BigDecimal("200000"), new BigDecimal("20000"),
                new BigDecimal("0.60"), BigDecimal.ZERO, new BigDecimal("0.10"),
                new BigDecimal("0.30"), GridAssessmentRecommendation.MICRO_VALIDATE_GRID,
                Recommendation.INSUFFICIENT_EVIDENCE, Duration.ofDays(90),
                new BigDecimal("172.60"), new BigDecimal("129.95"), null,
                new BigDecimal("717.24"));

        assertThat(result.evaluationStatus()).isEqualTo("PARTIAL");
        assertThat(result.proposedProjectedNetIncomeUsdt()).isNull();
        assertThat(result.allocations().get(2).proposedProjectedNetIncomeUsdt()).isNull();
        assertThat(result.allocations().get(2).evidenceStatus()).isEqualTo("收益未知·组合不完整");
        assertThat(result.allocations().get(3).proposedProjectedNetIncomeUsdt())
                .isEqualByComparingTo("2151.72");
    }

    @Test
    void unknownProductWithZeroWeightDoesNotBlockThePortfolioTotal() {
        var result = service.recommend(new BigDecimal("200000"), new BigDecimal("20000"),
                new BigDecimal("0.80"), new BigDecimal("0.20"), BigDecimal.ZERO,
                BigDecimal.ZERO, GridAssessmentRecommendation.INSUFFICIENT_EVIDENCE,
                Recommendation.INSUFFICIENT_EVIDENCE, Duration.ofDays(90),
                new BigDecimal("172.60"), new BigDecimal("129.95"), null, null);

        assertThat(result.evaluationStatus()).isEqualTo("COMPLETE");
        assertThat(result.proposedProjectedNetIncomeUsdt()).isEqualByComparingTo("1640.70");
        assertThat(result.allocations().get(2).evidenceStatus()).isEqualTo("未分配");
        assertThat(result.allocations().get(3).evidenceStatus()).isEqualTo("未分配");
    }

    @Test
    void rejectsRatiosThatDoNotAllocateExactlyOnePortfolio() {
        assertThatThrownBy(() -> service.recommend(new BigDecimal("200000"), new BigDecimal("20000"),
                new BigDecimal("0.80"), new BigDecimal("0.20"), BigDecimal.ZERO,
                new BigDecimal("0.10"), GridAssessmentRecommendation.MICRO_VALIDATE_GRID,
                Recommendation.PREFER_SIMPLE_EARN, Duration.ofDays(90), BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("合计必须等于100%");
    }

    @Test
    void rejectsShadowCapitalLargerThanTheRealPortfolio() {
        assertThatThrownBy(() -> service.recommend(new BigDecimal("10000"), new BigDecimal("20000"),
                new BigDecimal("0.80"), new BigDecimal("0.20"), BigDecimal.ZERO, BigDecimal.ZERO,
                GridAssessmentRecommendation.MICRO_VALIDATE_GRID, Recommendation.PREFER_SIMPLE_EARN,
                Duration.ofDays(90), BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能超过真实组合");
    }
}
