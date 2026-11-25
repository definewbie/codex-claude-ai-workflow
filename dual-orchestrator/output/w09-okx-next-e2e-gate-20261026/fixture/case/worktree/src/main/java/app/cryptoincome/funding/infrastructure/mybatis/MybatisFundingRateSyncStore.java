package app.cryptoincome.funding.infrastructure.mybatis;

import app.cryptoincome.funding.domain.AppendResult;
import app.cryptoincome.funding.domain.FundingRateObservation;
import app.cryptoincome.funding.domain.FundingRateSyncStore;
import app.cryptoincome.funding.domain.FundingSyncAcquisition;
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
import java.util.List;

@Repository
public class MybatisFundingRateSyncStore implements FundingRateSyncStore {
    private final FundingSyncRequestMapper requestMapper;
    private final AnalysisRunMapper runMapper;
    private final FundingRateFactMapper factMapper;

    public MybatisFundingRateSyncStore(
            FundingSyncRequestMapper requestMapper,
            AnalysisRunMapper runMapper,
            FundingRateFactMapper factMapper
    ) {
        this.requestMapper = requestMapper;
        this.runMapper = runMapper;
        this.factMapper = factMapper;
    }

    @Override
    @Transactional
    public FundingSyncAcquisition acquire(String requestKey, String instrumentId, int limit) {
        FundingSyncRequestEntity candidate = new FundingSyncRequestEntity();
        candidate.setRequestKey(requestKey);
        candidate.setInstrumentId(instrumentId);
        candidate.setPageLimit(limit);
        requestMapper.insertOrGetId(candidate);

        FundingSyncRequestEntity request = requiredRequest(requestMapper.selectByKeyForUpdate(requestKey));
        if (!request.getInstrumentId().equals(instrumentId) || request.getPageLimit() != limit) {
            throw new IllegalArgumentException("相同 requestKey 不能用于不同的同步参数");
        }
        if ("SUCCEEDED".equals(request.getState())) {
            AnalysisRun run = requiredRun(request.getSuccessfulRunId());
            return new FundingSyncAcquisition(
                    request.getId(), FundingSyncDisposition.REPLAYED, run, factMapper.countLinked(run.id()));
        }
        if ("RUNNING".equals(request.getState())) {
            return new FundingSyncAcquisition(
                    request.getId(), FundingSyncDisposition.IN_PROGRESS,
                    requiredRun(request.getCurrentRunId()), 0);
        }
        if (!"READY".equals(request.getState())) {
            throw new IllegalStateException("未知 funding_sync_request 状态: " + request.getState());
        }

        int attemptNo = request.getNextAttempt();
        AnalysisRunEntity run = new AnalysisRunEntity();
        run.setSyncRequestId(request.getId());
        run.setAttemptNo(attemptNo);
        run.setIdempotencyKey("funding-sync:" + request.getId() + ":attempt:" + attemptNo);
        run.setRunType(AnalysisRunType.SYNC_MARKET.name());
        run.setMode(PortfolioMode.LIVE.name());
        runMapper.insertNew(run);
        requireChanged(runMapper.transitionToRunning(run.getId()), "无法启动资金费率同步 execution");
        requireChanged(requestMapper.markRunning(request.getId(), run.getId()), "无法占用资金费率同步请求");
        return new FundingSyncAcquisition(
                request.getId(), FundingSyncDisposition.ACQUIRED, requiredRun(run.getId()), 0);
    }

    @Override
    @Transactional
    public AppendResult complete(long requestId, long runId, List<FundingRateObservation> observations) {
        FundingSyncRequestEntity request = requiredRequest(requestMapper.selectByIdForUpdate(requestId));
        requireCurrentRun(request, runId);
        AppendResult result = FundingFactAppender.append(factMapper, runId, observations);
        requireChanged(runMapper.transitionToSucceeded(runId), "execution 无法进入 SUCCEEDED");
        requireChanged(requestMapper.markSucceeded(requestId, runId), "同步请求无法进入 SUCCEEDED");
        return result;
    }

    @Override
    @Transactional
    public void fail(long requestId, long runId, String failureCode, String failureDetail) {
        FundingSyncRequestEntity request = requiredRequest(requestMapper.selectByIdForUpdate(requestId));
        requireCurrentRun(request, runId);
        requireChanged(runMapper.transitionToFailed(runId, failureCode, failureDetail), "execution 无法进入 FAILED");
        requireChanged(requestMapper.markReady(requestId, runId), "同步请求无法释放为 READY");
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
        FundingSyncRequestEntity request = requestMapper.selectByKeyForUpdate(requestKey);
        if (request == null) return StaleRecoveryOutcome.NOT_FOUND;
        if (!"RUNNING".equals(request.getState())) return StaleRecoveryOutcome.NOT_RUNNING;
        if (request.getCurrentRunId() == null || request.getCurrentRunId() != expectedRunId) {
            return StaleRecoveryOutcome.RUN_MISMATCH;
        }
        int changed = runMapper.failStaleIfOlderThan(
                expectedRunId, StaleWindows.toExactMicros(olderThan),
                "STALE_RECOVERED", "人工恢复超过截止时间仍为 RUNNING 的 execution");
        if (changed != 1) return StaleRecoveryOutcome.NOT_STALE;
        requireChanged(requestMapper.markReady(request.getId(), expectedRunId), "过期同步请求无法释放为 READY");
        return StaleRecoveryOutcome.RECOVERED;
    }

    private AnalysisRun requiredRun(Long id) {
        if (id == null) throw new IllegalStateException("同步请求缺少 run 引用");
        AnalysisRunEntity entity = runMapper.selectById(id);
        if (entity == null) throw new IllegalStateException("analysis_run 不存在: " + id);
        return new AnalysisRun(
                entity.getId(), entity.getSyncRequestId(), entity.getBackfillRequestId(),
                entity.getAttemptNo(), entity.getIdempotencyKey(),
                AnalysisRunType.valueOf(entity.getRunType()), PortfolioMode.valueOf(entity.getMode()),
                AnalysisRunStatus.valueOf(entity.getStatus()), entity.getCreatedAt(), entity.getStartedAt(),
                entity.getCompletedAt(), entity.getFailureCode(), entity.getFailureDetail());
    }

    private static FundingSyncRequestEntity requiredRequest(FundingSyncRequestEntity request) {
        if (request == null) throw new IllegalStateException("funding_sync_request 不存在");
        return request;
    }

    private static void requireCurrentRun(FundingSyncRequestEntity request, long runId) {
        if (!"RUNNING".equals(request.getState())
                || request.getCurrentRunId() == null
                || request.getCurrentRunId() != runId) {
            throw new IllegalStateException("execution 已失去当前同步请求的执行权: " + runId);
        }
    }

    private static void requireChanged(int changed, String message) {
        if (changed != 1) throw new IllegalStateException(message);
    }
}
