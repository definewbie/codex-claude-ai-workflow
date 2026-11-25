package app.cryptoincome.shadow.application;

import java.util.List;

public record GridShadowDailyReport(
        String headline,
        String parameterAction,
        String parameterAdvice,
        String positionAction,
        String positionAdvice,
        String portfolioAction,
        String portfolioAdvice,
        String nextReview,
        List<String> boundaries) {}
