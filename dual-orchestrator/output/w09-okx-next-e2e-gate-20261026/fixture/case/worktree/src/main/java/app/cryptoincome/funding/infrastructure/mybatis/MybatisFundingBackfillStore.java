package app.cryptoincome.funding.infrastructure.mybatis;

import app.cryptoincome.funding.domain.BackfillAcquisition;
import app.cryptoincome.funding.domain.BackfillCoverage;
import app.cryptoincome.funding.domain.BackfillPageCommit;
import app.cryptoincome.funding.domain.FundingBackfillCursorStalledException;
import app.cryptoincome.funding.domain.FundingBackfillStore;
import app.cryptoincome.funding.domain.FundingRateObservation;
import app.cryptoincome.funding.domain.FundingRateSyncInProgressException;
import app.cryptoincome.funding.domain.FundingSyncDisposition;
import app.cryptoincome.funding.domain.StaleExecution;
import app.cryptoincome.funding.domain.StaleRecoveryOutcome;
import app.cryptoincome.funding.domain.StaleWindows;
import app.cryptoincome.run.domain.AnalysisRun;
import app.cryptoincome.run.domain.AnalysisRunStatus;
import app.cryptoincome.run.domain.AnalysisRunType;
import app.cryptoincome.run.domain.PortfolioMode;
import app.cryptoincome.run.infrastructure.mybatis.AnalysisRunEntity;
import app.cryptoincome.run.infrastructure.mybatis.AnalysisRunMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 回补存储实现: 每个公共方法都是独立短事务。
 * 页面提交在请求行锁内校验执行权(fencing); coverage 只在真正到达窗口边界时置为 COMPLETE。
 */
@Repository
public class MybatisFundingBackfillStore implements FundingBackfillStore {

    private final FundingBackfillRequestMapper requestMapper;
    private final FundingBackfillPageMapper pageMapper;
    private final AnalysisRunMapper runMapper;
    private final FundingRateFactMapper factMapper;

    public MybatisFundingBackfillStore(
            FundingBackfillRequestMapper requestMapper,
            FundingBackfillPageMapper pageMapper,
            AnalysisRunMapper runMapper,
            FundingRateFactMapper factMapper
    ) {
        this.requestMapper = requestMapper;
        this.pageMapper = pageMapper;
        this.runMapper = runMapper;
        this.factMapper = factMapper;
    }

    @Override
    @Transactional
    public BackfillAcquisition begin(String requestKey, String instrumentId, Instant from, Instant to, int limit) {
        FundingBackfillRequestEntity candidate = new FundingBackfillRequestEntity();
        candidate.setRequestKey(requestKey);
        candidate.setInstrumentId(instrumentId);
        candidate.setWindowStart(from);
        candidate.setWindowEnd(to);
        candidate.setPageLimit(limit);
        requestMapper.insertOrGetId(candidate);

        FundingBackfillRequestEntity request = requiredRequest(requestMapper.selectByKeyForUpdate(requestKey));
        if (!request.getInstrumentId().equals(instrumentId)
                || !request.getWindowStart().equals(from)
                || !request.getWindowEnd().equals(to)
                || request.getPageLimit() != limit) {
            throw new IllegalArgumentException("相同 requestKey 不能用于不同的回补参数");
        }
        if ("COMPLETE".equals(request.getState())) {
            return new BackfillAcquisition(
                    FundingSyncDisposition.REPLAYED, request.getId(),
                    requiredRun(lastRunId(request.getId())),
                    request.getCursorTs(), request.getPagesFetched(),
                    BackfillCoverage.COMPLETE, request.getEarliestFactTime(), request.getLatestFactTime());
        }
        if ("RUNNING".equals(request.getState())) {
            throw new FundingRateSyncInProgressException(request.getCurrentRunId() == null ? -1 : request.getCurrentRunId());
        }

        int attemptNo = request.getNextAttempt();
        AnalysisRunEntity run = new AnalysisRunEntity();
        run.setBackfillRequestId(request.getId());
        run.setAttemptNo(attemptNo);
        run.setIdempotencyKey("backfill:" + request.getId() + ":attempt:" + attemptNo);
        run.setRunType(AnalysisRunType.SYNC_MARKET.name());
        run.setMode(PortfolioMode.LIVE.name());
        runMapper.insertBackfillRun(run);
        requireChanged(runMapper.transitionToRunning(run.getId()), "无法启动回补 execution");
        requireChanged(requestMapper.markRunning(request.getId(), run.getId()), "无法占用回补请求");
        Instant cursor = request.getCursorTs() != null ? request.getCursorTs() : request.getWindowEnd();
        return new BackfillAcquisition(
                FundingSyncDisposition.ACQUIRED, request.getId(), requiredRun(run.getId()),
                cursor, request.getPagesFetched(),
                BackfillCoverage.valueOf(request.getCoverageState()),
                request.getEarliestFactTime(), request.getLatestFactTime());
    }

    @Override
    @Transactional
    public BackfillPageCommit commitPage(
            long requestId, long runId, int pageNo, Instant afterCursor,
            List<FundingRateObservation> observations,
            boolean budgetExhaustedAfterPage
    ) {
        FundingBackfillRequestEntity request = requiredRequest(requestMapper.selectByIdForUpdate(requestId));
        requireCurrentRun(request, runId);
        if (pageNo != request.getPagesFetched() + 1) {
            throw new IllegalStateException("回补页号不连续: 期望 " + (request.getPagesFetched() + 1) + " 实际 " + pageNo);
        }
        int received = observations.size();
        if (received == 0) {
            pageMapper.insertPage(requestId, runId, pageNo, afterCursor, 0, 0, 0, 0, 0, null, null, null);
            BackfillCoverage coverage = !afterCursor.isAfter(request.getWindowStart())
                    ? BackfillCoverage.COMPLETE
                    : BackfillCoverage.SOURCE_EXHAUSTED;
            finishExecution(requestId, runId, coverage);
            return new BackfillPageCommit(outcomeOf(coverage), coverage, afterCursor, 0, 0, 0, 0, 0, 0,
                    request.getEarliestFactTime(), request.getLatestFactTime());
        }

        Instant oldest = observations.get(received - 1).fundingTime();
        Instant newest = observations.get(0).fundingTime();
        if (!oldest.isBefore(afterCursor)) {
            throw new FundingBackfillCursorStalledException(pageNo, afterCursor, oldest);
        }
        int inWindow = countInWindow(observations, request.getWindowStart(), request.getWindowEnd());
        int outOfWindow = received - inWindow;
        var appended = FundingFactAppender.append(factMapper, runId, observations);
        pageMapper.insertPage(requestId, runId, pageNo, afterCursor, received, inWindow, outOfWindow,
                appended.newObservationLinks(), appended.newGlobalFacts(), oldest, newest, oldest);
        Instant earliest = earlier(request.getEarliestFactTime(), oldest);
        Instant latest = later(request.getLatestFactTime(), newest);
        requireChanged(requestMapper.advanceCursor(requestId, runId, oldest, earliest, latest),
                "无法推进回补游标");

        if (!oldest.isAfter(request.getWindowStart())) {
            finishExecution(requestId, runId, BackfillCoverage.COMPLETE);
            return new BackfillPageCommit(BackfillPageCommit.PageOutcome.COMPLETE, BackfillCoverage.COMPLETE,
                    oldest, received, inWindow, outOfWindow,
                    appended.newGlobalFacts(), appended.newObservationLinks(), appended.duplicateObservationLinks(),
                    earliest, latest);
        }
        if (budgetExhaustedAfterPage) {
            finishExecution(requestId, runId, BackfillCoverage.BUDGET_EXHAUSTED);
            return new BackfillPageCommit(BackfillPageCommit.PageOutcome.BUDGET_EXHAUSTED,
                    BackfillCoverage.BUDGET_EXHAUSTED, oldest, received, inWindow, outOfWindow,
                    appended.newGlobalFacts(), appended.newObservationLinks(), appended.duplicateObservationLinks(),
                    earliest, latest);
        }
        return new BackfillPageCommit(BackfillPageCommit.PageOutcome.CONTINUED, BackfillCoverage.IN_PROGRESS,
                oldest, received, inWindow, outOfWindow,
                appended.newGlobalFacts(), appended.newObservationLinks(), appended.duplicateObservationLinks(),
                earliest, latest);
    }

    /** 窗口判断严格使用 [windowStart, windowEnd); 一页跨过 from 时窗口内外分别记账。 */
    private static int countInWindow(
            List<FundingRateObservation> observations, Instant windowStart, Instant windowEnd) {
        int inWindow = 0;
        for (FundingRateObservation observation : observations) {
            Instant time = observation.fundingTime();
            if (!time.isBefore(windowStart) && time.isBefore(windowEnd)) {
                inWindow++;
            }
        }
        return inWindow;
    }

    @Override
    @Transactional
    public void fail(long requestId, long runId, String failureCode, String failureDetail) {
        FundingBackfillRequestEntity request = requiredRequest(requestMapper.selectByIdForUpdate(requestId));
        requireCurrentRun(request, runId);
        requireChanged(runMapper.transitionToFailed(runId, failureCode, failureDetail), "回补 execution 无法进入 FAILED");
        requireChanged(requestMapper.releaseRunning(requestId, runId), "回补请求无法释放为 IDLE");
    }

    @Override
    @Transactional(readOnly = true)
    public List<StaleExecution> listStale(Duration olderThan) {
        return requestMapper.selectStaleRunning(StaleWindows.toExactMicros(olderThan)).stream()
                .map(row -> new StaleExecution(
                        row.getRequestKey(), row.getRequestId(), row.getRunId(),
                        row.getInstrumentId(), row.getPageLimit(), row.getStartedAt()))
                .toList();
    }

    @Override
    @Transactional
    public StaleRecoveryOutcome recoverStale(String requestKey, long expectedRunId, Duration olderThan) {
        FundingBackfillRequestEntity request = requestMapper.selectByKeyForUpdate(requestKey);
        if (request == null) return StaleRecoveryOutcome.NOT_FOUND;
        if (!"RUNNING".equals(request.getState())) return StaleRecoveryOutcome.NOT_RUNNING;
        if (request.getCurrentRunId() == null || request.getCurrentRunId() != expectedRunId) {
            return StaleRecoveryOutcome.RUN_MISMATCH;
        }
        int changed = runMapper.failStaleIfOlderThan(
                expectedRunId, StaleWindows.toExactMicros(olderThan),
                "STALE_RECOVERED", "人工恢复超过截止时间仍为 RUNNING 的回补 execution");
        if (changed != 1) return StaleRecoveryOutcome.NOT_STALE;
        requireChanged(requestMapper.releaseRunning(request.getId(), expectedRunId), "过期回补请求无法释放为 IDLE");
        return StaleRecoveryOutcome.RECOVERED;
    }

    private void finishExecution(long requestId, long runId, BackfillCoverage coverage) {
        requireChanged(runMapper.transitionToSucceeded(runId), "回补 execution 无法进入 SUCCEEDED");
        String state = coverage == BackfillCoverage.COMPLETE ? "COMPLETE" : "IDLE";
        requireChanged(requestMapper.finishExecution(requestId, runId, state, coverage.name()),
                "回补请求无法终结");
    }

    private Long lastRunId(long requestId) {
        AnalysisRunEntity last = runMapper.selectLastByBackfillRequest(requestId);
        return last == null ? null : last.getId();
    }

    private AnalysisRun requiredRun(Long id) {
        if (id == null) throw new IllegalStateException("回补请求缺少 run 引用");
        AnalysisRunEntity entity = runMapper.selectById(id);
        if (entity == null) throw new IllegalStateException("analysis_run 不存在: " + id);
        return new AnalysisRun(
                entity.getId(), entity.getSyncRequestId(), entity.getBackfillRequestId(), entity.getAttemptNo(),
                entity.getIdempotencyKey(),
                AnalysisRunType.valueOf(entity.getRunType()), PortfolioMode.valueOf(entity.getMode()),
                AnalysisRunStatus.valueOf(entity.getStatus()), entity.getCreatedAt(), entity.getStartedAt(),
                entity.getCompletedAt(), entity.getFailureCode(), entity.getFailureDetail());
    }

    private static FundingBackfillRequestEntity requiredRequest(FundingBackfillRequestEntity request) {
        if (request == null) throw new IllegalStateException("funding_backfill_request 不存在");
        return request;
    }

    private static void requireCurrentRun(FundingBackfillRequestEntity request, long runId) {
        if (!"RUNNING".equals(request.getState())
                || request.getCurrentRunId() == null
                || request.getCurrentRunId() != runId) {
            throw new IllegalStateException("execution 已失去当前回补请求的执行权: " + runId);
        }
    }

    private static BackfillPageCommit.PageOutcome outcomeOf(BackfillCoverage coverage) {
        return switch (coverage) {
            case COMPLETE -> BackfillPageCommit.PageOutcome.COMPLETE;
            case SOURCE_EXHAUSTED -> BackfillPageCommit.PageOutcome.SOURCE_EXHAUSTED;
            case BUDGET_EXHAUSTED -> BackfillPageCommit.PageOutcome.BUDGET_EXHAUSTED;
            case IN_PROGRESS -> BackfillPageCommit.PageOutcome.CONTINUED;
        };
    }

    private static Instant earlier(Instant current, Instant candidate) {
        return current == null || candidate.isBefore(current) ? candidate : current;
    }

    private static Instant later(Instant current, Instant candidate) {
        return current == null || candidate.isAfter(current) ? candidate : current;
    }

    private static void requireChanged(int changed, String message) {
        if (changed != 1) throw new IllegalStateException(message);
    }
}
