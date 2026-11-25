package app.cryptoincome.funding.application;

import app.cryptoincome.funding.domain.BackfillCoverage;

import java.time.Instant;

/**
 * 回补执行结果: received 拆分窗口内外, 统计区分全局事实/观察关系/重复观察。
 * coverage=COMPLETE 仅代表分页游标已到达 from 边界(BOUNDARY_REACHED),
 * 不证明区间无缺口——完整性检查属后续工作。
 */
public record BackfillResult(
        long runId,
        int attemptNo,
        boolean replayed,
        BackfillCoverage coverage,
        int pagesThisRun,
        int totalPages,
        int receivedThisRun,
        int inWindowReceivedThisRun,
        int outOfWindowReceivedThisRun,
        int newGlobalFactsThisRun,
        int newObservationLinksThisRun,
        int duplicateObservationLinksThisRun,
        Instant earliestFactTime,
        Instant latestFactTime,
        Instant cursor
) {
}
