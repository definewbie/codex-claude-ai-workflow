package app.cryptoincome.web;

import app.cryptoincome.assessment.application.PhaseOneAssessmentService;
import app.cryptoincome.assessment.application.SmartArbitrageAllocationCalculator;
import app.cryptoincome.grid.application.FixedGridAssessmentService;
import app.cryptoincome.grid.sync.*;
import app.cryptoincome.income.SimpleEarnIncomeService;
import app.cryptoincome.portfolio.application.PhaseOnePortfolioRecommendationService;
import app.cryptoincome.shadow.application.GridShadowStudyService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DecisionWorkbenchControllerTest {
    @Test
    void exposesBacktestAndShadowButDoesNotPretendLiveIsAvailable() {
        var modes = controller(mock(CandleSyncExecutionService.class)).capabilities().modes();
        assertThat(modes).extracting(DecisionWorkbenchController.ModeCapability::mode)
                .containsExactly("BACKTEST", "SHADOW", "LIVE");
        assertThat(modes).extracting(DecisionWorkbenchController.ModeCapability::available)
                .containsExactly(true, true, false);
    }

    @Test
    void rejectsAnInvalidScenarioBeforeCallingAnyAssessmentService() {
        FixedGridAssessmentService grid = mock(FixedGridAssessmentService.class);
        var controller = new DecisionWorkbenchController(grid, mock(GridShadowStudyService.class),
                mock(CandleSyncExecutionService.class), mock(PhaseOneAssessmentService.class),
                mock(SimpleEarnIncomeService.class), new SmartArbitrageAllocationCalculator(),
                new PhaseOnePortfolioRecommendationService());
        assertThatThrownBy(() -> controller.evaluateGrid(request(new BigDecimal("75000"), new BigDecimal("50000"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("upperPrice");
        verifyNoInteractions(grid);
    }

    @Test
    void rejectsAnInvalidPortfolioBeforeCallingAnyProductAssessment() {
        FixedGridAssessmentService grid = mock(FixedGridAssessmentService.class);
        PhaseOneAssessmentService smart = mock(PhaseOneAssessmentService.class);
        SimpleEarnIncomeService earn = mock(SimpleEarnIncomeService.class);
        var controller = new DecisionWorkbenchController(grid, mock(GridShadowStudyService.class),
                mock(CandleSyncExecutionService.class), smart, earn,
                new SmartArbitrageAllocationCalculator(), new PhaseOnePortfolioRecommendationService());

        assertThatThrownBy(() -> controller.evaluateGrid(request(
                new BigDecimal("50000"), new BigDecimal("75000"),
                new BigDecimal("0.70"), new BigDecimal("0.20"),
                new BigDecimal("0.05"), new BigDecimal("0.10"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("合计必须等于100%");

        verifyNoInteractions(grid, smart, earn);
    }

    @Test
    void startsAndThenReadsDurableCandleSyncProgress() {
        CandleSyncExecutionService service = mock(CandleSyncExecutionService.class);
        Instant from = Instant.parse("2026-05-22T00:00:00Z");
        Instant to = Instant.parse("2026-08-20T00:00:00Z");
        CandleSyncExecution running = new CandleSyncExecution(7, CandleSyncState.RUNNING, "BTC-USDT", "1m",
                from, to, null, 120, 36000, 36000, 1000, 35000,
                Instant.parse("2026-07-26T00:00:00Z"), null, null,
                Instant.parse("2026-08-20T01:00:00Z"), Instant.parse("2026-08-20T01:00:01Z"), null,
                Instant.parse("2026-08-20T01:00:30Z"));
        when(service.start(to)).thenReturn(running); when(service.get(7)).thenReturn(running);
        var controller = controller(service);

        var started = controller.syncCandles(new DecisionWorkbenchController.CandleSyncRequest(to));
        var polled = controller.candleSync(7);

        assertThat(started.id()).isEqualTo(7); assertThat(started.state()).isEqualTo("RUNNING");
        assertThat(started.completedInWindow()).isEqualTo(36000);
        assertThat(polled.cursor()).isEqualTo(Instant.parse("2026-07-26T00:00:00Z"));
        verify(service).start(to); verify(service).get(7);
    }

    private static DecisionWorkbenchController controller(CandleSyncExecutionService candle) {
        return new DecisionWorkbenchController(mock(FixedGridAssessmentService.class),
                mock(GridShadowStudyService.class), candle, mock(PhaseOneAssessmentService.class),
                mock(SimpleEarnIncomeService.class), new SmartArbitrageAllocationCalculator(),
                new PhaseOnePortfolioRecommendationService());
    }

    private static DecisionWorkbenchController.GridEvaluationRequest request(BigDecimal lower, BigDecimal upper) {
        return request(lower, upper, new BigDecimal("0.80"), new BigDecimal("0.20"),
                BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private static DecisionWorkbenchController.GridEvaluationRequest request(
            BigDecimal lower, BigDecimal upper, BigDecimal fixedRatio, BigDecimal liquidRatio,
            BigDecimal smartRatio, BigDecimal gridRatio) {
        Instant asOf = Instant.parse("2026-08-19T10:00:00Z");
        return new DecisionWorkbenchController.GridEvaluationRequest(asOf,
                Instant.parse("2026-05-21T00:00:00Z"), Instant.parse("2026-08-19T00:00:00Z"), 1L,
                new BigDecimal("20000"), new BigDecimal("0.035"), new BigDecimal("0.0006"),
                new BigDecimal("0.0007"), BigDecimal.TEN, new BigDecimal("0.15"),
                new BigDecimal("0.02"), new BigDecimal("0.60"), lower, upper, 40, null, null,
                asOf, new BigDecimal("19047.61"), asOf, new BigDecimal("36.001"),
                new BigDecimal("20.192"), BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("64150.7"), new BigDecimal("952.38"), new BigDecimal("200000"),
                fixedRatio, liquidRatio, smartRatio, gridRatio);
    }
}
