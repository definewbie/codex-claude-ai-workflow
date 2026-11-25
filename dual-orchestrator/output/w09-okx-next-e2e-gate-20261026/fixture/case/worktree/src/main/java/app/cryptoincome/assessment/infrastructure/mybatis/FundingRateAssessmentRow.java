package app.cryptoincome.assessment.infrastructure.mybatis;

import java.math.BigDecimal;
import java.time.Instant;

public class FundingRateAssessmentRow {
    private Instant fundingTime;
    private BigDecimal realizedRate;

    public Instant getFundingTime() {
        return fundingTime;
    }

    public void setFundingTime(Instant fundingTime) {
        this.fundingTime = fundingTime;
    }

    public BigDecimal getRealizedRate() {
        return realizedRate;
    }

    public void setRealizedRate(BigDecimal realizedRate) {
        this.realizedRate = realizedRate;
    }
}
