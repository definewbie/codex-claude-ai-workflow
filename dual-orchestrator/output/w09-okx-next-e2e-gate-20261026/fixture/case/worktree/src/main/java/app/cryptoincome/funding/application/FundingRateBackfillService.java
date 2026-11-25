package app.cryptoincome.funding.application;

import app.cryptoincome.funding.domain.BackfillAcquisition;
import app.cryptoincome.funding.domain.BackfillCoverage;
import app.cryptoincome.funding.domain.BackfillPageCommit;
import app.cryptoincome.funding.domain.FundingBackfillCursorStalledException;
import app.cryptoincome.funding.domain.FundingBackfillStore;
import app.cryptoincome.funding.domain.FundingKeys;
import app.cryptoincome.funding.domain.FundingRateHistoryPager;
import app.cryptoincome.funding.domain.FundingRateObservation;
import app.cryptoincome.funding.domain.FundingRateSourceException;
import app.cryptoincome.funding.domain.FundingSyncDisposition;
import app.cryptoincome.funding.domain.StaleExecution;
import app.cryptoincome.funding.domain.StaleRecoveryOutcome;
import app.cryptoincome.funding.domain.StaleWindows;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.Duration;
import java.util.List;

/**
 * 资金费率历史游标回补: 区间 [from, to), 首次 after=to, 之后以本页最旧 fundingTime 向过去推进。
 * 页循环(含 OKX HTTP)在数据库事务之外; 每页独立短事务提交 事实+观察关系+checkpoint。
 * coverage 只有真正到达 from 边界才 COMPLETE; 预算用尽/来源无数据/游标停滞各有明确状态。
 */
@Service
public class FundingRateBackfillService {

    public static final String REQUEST_KEY_PREFIX = "backfill:";
    private static final int MAX_PAGES_PER_EXECUTION = 1_000;

    private final FundingRateHistoryPager pager;
    private final FundingBackfillStore store;

    public FundingRateBackfillService(FundingRateHistoryPager pager, FundingBackfillStore store) {
        this.pager = pager;
        this.store = store;
    }

    public BackfillResult backfill(
            String instrumentId, Instant from, Instant to, int limit, int maxPages, String syncKey) {
        String instrument = FundingKeys.normalizeInstrument(instrumentId);
        requireWindow(from, to);
        requireLimit(limit);
        if (maxPages < 1 || maxPages > MAX_PAGES_PER_EXECUTION) {
            throw new IllegalArgumentException("max-pages 必须在 1 到 " + MAX_PAGES_PER_EXECUTION + " 之间");
        }
        String requestKey = requestKeyFor(instrument, from, to, limit, syncKey);

        BackfillAcquisition acquisition = store.begin(requestKey, instrument, from, to, limit);
        if (acquisition.disposition() == FundingSyncDisposition.REPLAYED) {
            return new BackfillResult(
                    acquisition.run() == null ? -1 : acquisition.run().id(),
                    acquisition.run() == null ? 0 : acquisition.run().attemptNo(),
                    true, acquisition.coverage(), 0, acquisition.pagesFetched(),
                    0, 0, 0, 0, 0, 0,
                    acquisition.earliestFactTime(), acquisition.latestFactTime(), acquisition.cursor());
        }
        try {
            return execute(acquisition, instrument, limit, maxPages);
        } catch (FundingBackfillCursorStalledException stalled) {
            failPreservingOriginal(acquisition, "CURSOR_STALLED", stalled);
            throw stalled;
        } catch (FundingRateSourceException sourceFailure) {
            failPreservingOriginal(acquisition, "SOURCE_" + sourceFailure.category().name(), sourceFailure);
            throw sourceFailure;
        } catch (RuntimeException persistenceOrUnexpectedFailure) {
            failPreservingOriginal(acquisition, "PERSISTENCE_OR_INTERNAL_ERROR", persistenceOrUnexpectedFailure);
            throw persistenceOrUnexpectedFailure;
        }
    }

    public List<StaleExecution> listStale(Duration olderThan) {
        StaleWindows.requireWithin(olderThan);
        return store.listStale(olderThan);
    }

    public StaleRecoveryOutcome recoverStale(String requestKey, long expectedRunId, Duration olderThan) {
        StaleWindows.requireWithin(olderThan);
        if (expectedRunId <= 0) {
            throw new IllegalArgumentException("expectedRunId 必须为正数");
        }
        return store.recoverStale(FundingKeys.printableRequestKey(requestKey), expectedRunId, olderThan);
    }

    /** 组合回补幂等键: 同一标的/窗口/页大小/人工键 唯一确定一个逻辑回补请求。 */
    public String requestKeyFor(String instrumentId, Instant from, Instant to, int limit, String syncKey) {
        String instrument = FundingKeys.normalizeInstrument(instrumentId);
        requireWindow(from, to);
        requireLimit(limit);
        String requestKey = REQUEST_KEY_PREFIX + instrument + ":" + limit + ":"
                + from.toEpochMilli() + ":" + to.toEpochMilli() + ":"
                + FundingKeys.safeSyncKey(syncKey);
        if (requestKey.length() > 128) {
            throw new IllegalArgumentException("组合后的幂等键长度不能超过 128");
        }
        return requestKey;
    }

    private BackfillResult execute(BackfillAcquisition acquisition, String instrument, int limit, int maxPages) {
        Instant cursor = acquisition.cursor();
        int pageNo = acquisition.pagesFetched();
        int pagesThisRun = 0;
        int receivedThisRun = 0;
        int inWindowThisRun = 0;
        int outOfWindowThisRun = 0;
        int newGlobalFactsThisRun = 0;
        int newObservationLinksThisRun = 0;
        int duplicateObservationLinksThisRun = 0;
        BackfillCoverage coverage = acquisition.coverage();
        Instant earliest = acquisition.earliestFactTime();
        Instant latest = acquisition.latestFactTime();

        while (pagesThisRun < maxPages) {
            List<FundingRateObservation> page = pager.fetchPage(instrument, limit, cursor);
            requireDescending(page, pageNo + 1);
            boolean lastByBudget = pagesThisRun + 1 == maxPages;
            BackfillPageCommit commit = store.commitPage(
                    acquisition.requestId(), acquisition.run().id(), pageNo + 1, cursor, page, lastByBudget);
            pageNo++;
            pagesThisRun++;
            receivedThisRun += commit.received();
            inWindowThisRun += commit.inWindowReceived();
            outOfWindowThisRun += commit.outOfWindowReceived();
            newGlobalFactsThisRun += commit.newGlobalFacts();
            newObservationLinksThisRun += commit.newObservationLinks();
            duplicateObservationLinksThisRun += commit.duplicateObservationLinks();
            cursor = commit.cursor();
            earliest = commit.earliestFactTime();
            latest = commit.latestFactTime();
            if (commit.outcome() != BackfillPageCommit.PageOutcome.CONTINUED) {
                coverage = commit.coverage();
                break;
            }
        }
        return new BackfillResult(acquisition.run().id(), acquisition.run().attemptNo(), false,
                coverage, pagesThisRun, pageNo, receivedThisRun,
                inWindowThisRun, outOfWindowThisRun,
                newGlobalFactsThisRun, newObservationLinksThisRun, duplicateObservationLinksThisRun,
                earliest, latest, cursor);
    }

    /** OKX 分页按 fundingTime 倒序返回; 顺序破坏说明来源行为异常, 拒绝入库。 */
    private static void requireDescending(List<FundingRateObservation> page, int pageNo) {
        for (int i = 1; i < page.size(); i++) {
            if (page.get(i).fundingTime().isAfter(page.get(i - 1).fundingTime())) {
                throw new FundingRateSourceException(
                        FundingRateSourceException.Category.INVALID_RESPONSE, null, false,
                        "OKX 分页顺序异常: 第 " + pageNo + " 页 fundingTime 未按时间倒序");
            }
        }
    }

    private static void requireWindow(Instant from, Instant to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("回补区间 from/to 不能为空");
        }
        if (!from.isBefore(to)) {
            throw new IllegalArgumentException("回补区间必须 from < to, 且区间为 [from, to)");
        }
        if (from.getNano() % 1_000_000 != 0 || to.getNano() % 1_000_000 != 0) {
            throw new IllegalArgumentException("from/to 精度不能低于 1 毫秒");
        }
    }

    private static void requireLimit(int limit) {
        if (limit < 1 || limit > 400) {
            throw new IllegalArgumentException("limit 必须在 1 到 400 之间");
        }
    }

    private void failPreservingOriginal(
            BackfillAcquisition acquisition, String failureCode, RuntimeException original) {
        try {
            store.fail(acquisition.requestId(), acquisition.run().id(), failureCode, boundedMessage(original));
        } catch (RuntimeException markingFailure) {
            original.addSuppressed(markingFailure);
        }
    }

    private static String boundedMessage(RuntimeException failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            message = failure.getClass().getSimpleName();
        }
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }
}
