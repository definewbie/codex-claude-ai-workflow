package app.cryptoincome.funding.domain;

import java.util.List;

public interface FundingRateHistorySource {
    List<FundingRateObservation> fetchLatest(String instrumentId, int limit);
}
