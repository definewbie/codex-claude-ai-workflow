package app.cryptoincome.grid.sync.infrastructure;

import app.cryptoincome.grid.application.SpotCandleBackfillProgress;
import app.cryptoincome.grid.application.SpotCandleBackfillResult;
import app.cryptoincome.grid.domain.CandleBackfillCoverage;
import app.cryptoincome.grid.sync.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Repository
public class MybatisCandleSyncExecutionRepository implements CandleSyncExecutionRepository {
    private final CandleSyncExecutionMapper mapper;

    public MybatisCandleSyncExecutionRepository(CandleSyncExecutionMapper mapper) { this.mapper = mapper; }

    @Override @Transactional
    public long create(Instant from, Instant to, Instant now) {
        CandleSyncExecutionEntity entity = new CandleSyncExecutionEntity();
        entity.setState(CandleSyncState.QUEUED.name()); entity.setInstrumentId("BTC-USDT"); entity.setBar("1m");
        entity.setWindowFrom(from); entity.setWindowTo(to); entity.setPages(0); entity.setReceived(0);
        entity.setCompletedInWindow(0); entity.setNewFacts(0); entity.setDuplicateFacts(0);
        entity.setCreatedAt(now); entity.setUpdatedAt(now);
        if (mapper.insert(entity) != 1 || entity.getId() == null) throw new IllegalStateException("K线同步执行创建失败");
        return entity.getId();
    }

    @Override public Optional<CandleSyncExecution> find(long id) {
        return Optional.ofNullable(mapper.selectById(id)).map(MybatisCandleSyncExecutionRepository::toDomain);
    }
    @Override public Optional<CandleSyncExecution> findActive() {
        return Optional.ofNullable(mapper.findActive()).map(MybatisCandleSyncExecutionRepository::toDomain);
    }
    @Override public Optional<CandleSyncExecution> findLatest() {
        return Optional.ofNullable(mapper.findLatest()).map(MybatisCandleSyncExecutionRepository::toDomain);
    }
    @Override public boolean markRunning(long id, Instant now) { return mapper.markRunning(id, now) == 1; }
    @Override public boolean updateProgress(long id, SpotCandleBackfillProgress p, Instant now) {
        return mapper.updateProgress(id, p.pages(), p.received(), p.completedInWindow(), p.newFacts(),
                p.duplicateFacts(), p.cursor(), now) == 1;
    }
    @Override public boolean succeed(long id, SpotCandleBackfillResult r, Instant now) {
        return mapper.succeed(id, r.coverage().name(), r.pages(), r.received(), r.completedInWindow(),
                r.newFacts(), r.duplicateFacts(), r.cursor(), now) == 1;
    }
    @Override public boolean fail(long id, String category, String message, Instant now) {
        return mapper.fail(id, category, bounded(message), now) == 1;
    }
    @Override public int failStale(Instant olderThan, Instant now) { return mapper.failStale(olderThan, now); }

    private static CandleSyncExecution toDomain(CandleSyncExecutionEntity e) {
        return new CandleSyncExecution(e.getId(), CandleSyncState.valueOf(e.getState()), e.getInstrumentId(), e.getBar(),
                e.getWindowFrom(), e.getWindowTo(), e.getCoverage() == null ? null : CandleBackfillCoverage.valueOf(e.getCoverage()),
                e.getPages(), e.getReceived(), e.getCompletedInWindow(), e.getNewFacts(), e.getDuplicateFacts(),
                e.getCursorTs(), e.getFailureCategory(), e.getFailureMessage(), e.getCreatedAt(), e.getStartedAt(),
                e.getCompletedAt(), e.getUpdatedAt());
    }
    private static String bounded(String value) {
        if (value == null || value.isBlank()) return "未知错误";
        return value.substring(0, Math.min(500, value.length()));
    }
}
