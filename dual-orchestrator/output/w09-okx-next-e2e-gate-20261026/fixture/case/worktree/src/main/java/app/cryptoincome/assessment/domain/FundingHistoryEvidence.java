package app.cryptoincome.assessment.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

public record FundingHistoryEvidence(
        BigDecimal holdingReturn,
        BigDecimal fundingIncome,
        int settlementCount,
        int positiveSettlements,
        int negativeSettlements,
        Optional<Instant> earliestFundingTime,
        Optional<Instant> latestFundingTime
) {
}
