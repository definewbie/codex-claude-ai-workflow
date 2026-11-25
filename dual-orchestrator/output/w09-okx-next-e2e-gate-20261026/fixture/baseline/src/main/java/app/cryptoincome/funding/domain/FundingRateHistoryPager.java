package app.cryptoincome.funding.domain;

import java.time.Instant;
import java.util.List;

/**
 * OKX 资金费率历史分页端口: after 语义为"请求该 fundingTime 之前(更旧)的数据"。
 * 返回页按 fundingTime 倒序(最新在前); 单页上限 400。
 */
public interface FundingRateHistoryPager {

    List<FundingRateObservation> fetchPage(String instrumentId, int limit, Instant after);
}
