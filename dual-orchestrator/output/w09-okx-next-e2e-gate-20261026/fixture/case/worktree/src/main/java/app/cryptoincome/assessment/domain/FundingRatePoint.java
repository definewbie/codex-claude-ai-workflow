package app.cryptoincome.assessment.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record FundingRatePoint(Instant fundingTime, BigDecimal realizedRate) {
    public FundingRatePoint {
        Objects.requireNonNull(fundingTime, "fundingTime 不能为空");
        Objects.requireNonNull(realizedRate, "realizedRate 不能为空");
    }
}
