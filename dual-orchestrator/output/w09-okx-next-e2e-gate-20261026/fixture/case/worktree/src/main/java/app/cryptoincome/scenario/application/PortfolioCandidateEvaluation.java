package app.cryptoincome.scenario.application;

import app.cryptoincome.scenario.domain.*;

import java.math.BigDecimal;
import java.util.List;

record PortfolioCandidateEvaluation(
        String candidateId,
        List<PortfolioAllocation> allocations,
        List<PortfolioPathOutcome> pathOutcomes,
        TargetAttainment targetAttainment,
        BigDecimal bearWorstIncome,
        BigDecimal minimumRepresentativeIncome,
        BigDecimal bullRepresentativeIncome,
        BigDecimal maximumConservativeBtcExposureRate,
        BigDecimal liquidRatio
) {
    PortfolioCandidateEvaluation {
        allocations = List.copyOf(allocations);
        pathOutcomes = List.copyOf(pathOutcomes);
    }
}
