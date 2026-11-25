package app.cryptoincome.funding.domain;

import java.time.Instant;

/**
 * 一页已提交的回补进度(短事务内的结果快照)。
 * received 按回补窗口 [from,to) 拆分为 inWindowReceived/outOfWindowReceived,
 * 三种统计口径与 AppendResult 一致, 不得混用。
 */
public record BackfillPageCommit(
        PageOutcome outcome,
        BackfillCoverage coverage,
        Instant cursor,
        int received,
        int inWindowReceived,
        int outOfWindowReceived,
        int newGlobalFacts,
        int newObservationLinks,
        int duplicateObservationLinks,
        Instant earliestFactTime,
        Instant latestFactTime
) {
    public enum PageOutcome {
        CONTINUED,
        COMPLETE,
        SOURCE_EXHAUSTED,
        BUDGET_EXHAUSTED
    }
}
