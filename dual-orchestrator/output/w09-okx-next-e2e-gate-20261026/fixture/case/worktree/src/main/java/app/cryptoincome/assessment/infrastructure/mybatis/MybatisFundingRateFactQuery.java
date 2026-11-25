package app.cryptoincome.assessment.infrastructure.mybatis;

import app.cryptoincome.assessment.application.FundingRateFactQuery;
import app.cryptoincome.assessment.domain.FundingRatePoint;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public class MybatisFundingRateFactQuery implements FundingRateFactQuery {

    private final FundingRateAssessmentMapper mapper;

    public MybatisFundingRateFactQuery(FundingRateAssessmentMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<FundingRatePoint> findRealizedRates(
            String exchange, String instrumentId, Instant from, Instant to, Instant evaluationAsOf) {
        return mapper.selectRealizedRatesAsOf(exchange, instrumentId, from, to, evaluationAsOf).stream()
                .map(row -> new FundingRatePoint(row.getFundingTime(), row.getRealizedRate()))
                .toList();
    }
}
