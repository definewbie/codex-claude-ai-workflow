package app.cryptoincome.assessment.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public record PhaseOneAssessment(
        PhaseOneAssessmentRequest request,
        BigDecimal simpleEarnIncome,
        FundingHistoryEvidence fundingHistory,
        BigDecimal knownCost,
        Optional<BigDecimal> estimatedFundingOnlyNetIncome,
        Optional<BigDecimal> incrementalIncomeVsSimpleEarn,
        EvidenceStatus evidenceStatus,
        Recommendation recommendation,
        Optional<BigDecimal> maximumFirstLiveCapital,
        List<String> reasons,
        List<String> blockers,
        List<String> unknowns,
        List<String> manualOkxChecklist
) {
    public PhaseOneAssessment {
        reasons = List.copyOf(reasons);
        blockers = List.copyOf(blockers);
        unknowns = List.copyOf(unknowns);
        manualOkxChecklist = List.copyOf(manualOkxChecklist);
    }
}
