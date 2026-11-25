package app.cryptoincome.assessment.application;

import app.cryptoincome.assessment.domain.FundingRatePoint;

import java.time.Instant;
import java.util.List;

@FunctionalInterface
public interface FundingRateFactQuery {
    List<FundingRatePoint> findRealizedRates(
            String exchange, String instrumentId, Instant from, Instant to, Instant evaluationAsOf);
}
