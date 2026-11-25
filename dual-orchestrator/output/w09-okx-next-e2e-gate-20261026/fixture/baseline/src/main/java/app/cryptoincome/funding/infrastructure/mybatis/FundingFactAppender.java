package app.cryptoincome.funding.infrastructure.mybatis;

import app.cryptoincome.funding.domain.AppendResult;
import app.cryptoincome.funding.domain.FundingRateObservation;

import java.util.List;

/** 追加式事实写入: 同步与回补共用的短事务内页写入逻辑。 */
final class FundingFactAppender {

    private FundingFactAppender() {
    }

    static AppendResult append(FundingRateFactMapper factMapper, long runId, List<FundingRateObservation> observations) {
        int newGlobalFacts = 0;
        int newObservationLinks = 0;
        int duplicateObservationLinks = 0;
        for (FundingRateObservation observation : observations) {
            // useAffectedRows=true 下: 1 = 新插入的全局事实, 0 = 事实已存在(命中唯一键且未变更)
            int inserted = factMapper.insertOrResolveId(
                    observation.exchange(), observation.instrumentType().name(), observation.instrumentId(),
                    observation.formulaType().name(), observation.predictedRate(), observation.realizedRate(),
                    observation.fundingTime(), observation.settlementMethod().name(), observation.sourceEndpoint(),
                    observation.sourcePayload(), observation.payloadHash(), observation.observedAt());
            long factId = factMapper.lastInsertId();
            if (inserted == 1) {
                newGlobalFacts++;
            }
            if (factMapper.insertObservation(runId, factId, observation.observedAt()) == 1) {
                newObservationLinks++;
            } else {
                duplicateObservationLinks++;
            }
        }
        return new AppendResult(newGlobalFacts, newObservationLinks, duplicateObservationLinks);
    }
}
