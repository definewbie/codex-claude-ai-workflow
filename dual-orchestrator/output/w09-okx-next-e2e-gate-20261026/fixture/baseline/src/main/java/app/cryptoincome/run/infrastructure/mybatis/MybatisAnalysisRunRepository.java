package app.cryptoincome.run.infrastructure.mybatis;

import app.cryptoincome.run.domain.AnalysisRun;
import app.cryptoincome.run.domain.AnalysisRunRepository;
import app.cryptoincome.run.domain.AnalysisRunStatus;
import app.cryptoincome.run.domain.AnalysisRunType;
import app.cryptoincome.run.domain.PortfolioMode;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class MybatisAnalysisRunRepository implements AnalysisRunRepository {

    private final AnalysisRunMapper mapper;

    public MybatisAnalysisRunRepository(AnalysisRunMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public AnalysisRun createOrGet(String idempotencyKey, AnalysisRunType type, PortfolioMode mode) {
        if (type == AnalysisRunType.SYNC_MARKET) {
            throw new IllegalArgumentException("SYNC_MARKET execution 必须由对应同步请求的原子 Store 创建");
        }
        AnalysisRunEntity entity = new AnalysisRunEntity();
        entity.setIdempotencyKey(idempotencyKey);
        entity.setRunType(type.name());
        entity.setMode(mode.name());
        mapper.insertOrGetId(entity);

        AnalysisRunEntity persisted = mapper.selectByIdempotencyKey(idempotencyKey);
        if (!persisted.getRunType().equals(type.name()) || !persisted.getMode().equals(mode.name())) {
            throw new IllegalArgumentException("相同 idempotencyKey 不能用于不同的运行类型或模式");
        }
        return toDomain(persisted);
    }

    @Override
    public Optional<AnalysisRun> findById(long id) {
        return Optional.ofNullable(mapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public Optional<AnalysisRun> findByIdempotencyKey(String idempotencyKey) {
        return Optional.ofNullable(mapper.selectByIdempotencyKey(idempotencyKey)).map(this::toDomain);
    }

    @Override
    public boolean transitionToRunning(long id) {
        rejectExternallyOwnedExecution(id);
        return mapper.transitionToRunning(id) == 1;
    }

    @Override
    public boolean transitionToSucceeded(long id) {
        rejectExternallyOwnedExecution(id);
        return mapper.transitionToSucceeded(id) == 1;
    }

    @Override
    public boolean transitionToFailed(long id, String failureCode, String failureDetail) {
        rejectExternallyOwnedExecution(id);
        return mapper.transitionToFailed(id, failureCode, failureDetail) == 1;
    }

    private void rejectExternallyOwnedExecution(long id) {
        AnalysisRunEntity entity = mapper.selectById(id);
        if (entity != null && (entity.getSyncRequestId() != null || entity.getBackfillRequestId() != null)) {
            throw new IllegalArgumentException("同步/回补 execution 的状态只能由对应请求的原子 Store 修改: " + id);
        }
    }

    private AnalysisRun toDomain(AnalysisRunEntity entity) {
        return new AnalysisRun(
                entity.getId(),
                entity.getSyncRequestId(),
                entity.getBackfillRequestId(),
                entity.getAttemptNo(),
                entity.getIdempotencyKey(),
                AnalysisRunType.valueOf(entity.getRunType()),
                PortfolioMode.valueOf(entity.getMode()),
                AnalysisRunStatus.valueOf(entity.getStatus()),
                entity.getCreatedAt(),
                entity.getStartedAt(),
                entity.getCompletedAt(),
                entity.getFailureCode(),
                entity.getFailureDetail()
        );
    }
}
