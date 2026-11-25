package app.cryptoincome.shadow.application;

import app.cryptoincome.shadow.GridShadowDailyFact;
import app.cryptoincome.shadow.GridShadowStudy;
import app.cryptoincome.reporting.ReturnMetrics;

import java.util.List;

public record GridShadowView(
        GridShadowStudy study,
        GridShadowStatus status,
        long observedMinutes,
        List<GridShadowDailyFact> facts,
        GridShadowDailyFact latestCandidate,
        GridShadowDailyFact latestBaseline,
        ReturnMetrics candidateReturns,
        ReturnMetrics baselineReturns,
        ReturnMetrics simpleEarnReturns,
        ReturnMetrics buyHoldReturns,
        GridShadowDailyReport dailyReport) {}
