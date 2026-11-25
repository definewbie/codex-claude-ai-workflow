package app.cryptoincome.grid.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public record FixedGridAssessment(
        FixedGridAssessmentRequest request,
        GridConfigurationSnapshot configurationSnapshot,
        GridScenarioParameters scenario,
        boolean scenarioOverride,
        DualPathGridBacktest backtest,
        BigDecimal simpleEarnIncome,
        BigDecimal buyAndHoldIncome,
        BigDecimal worsePathIncome,
        BigDecimal pathDivergenceRate,
        List<GridSensitivityResult> sensitivity,
        GridAssessmentRecommendation recommendation,
        Optional<BigDecimal> maximumFirstLiveCapital,
        List<String> reasons
) {}
