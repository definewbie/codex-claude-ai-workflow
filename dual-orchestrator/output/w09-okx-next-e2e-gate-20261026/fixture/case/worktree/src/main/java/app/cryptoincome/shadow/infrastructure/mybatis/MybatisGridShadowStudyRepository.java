package app.cryptoincome.shadow.infrastructure.mybatis;

import app.cryptoincome.grid.domain.GridExitReason;
import app.cryptoincome.grid.domain.GridScenarioParameters;
import app.cryptoincome.shadow.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.baomidou.mybatisplus.core.toolkit.Wrappers.lambdaQuery;

@Repository
public class MybatisGridShadowStudyRepository implements GridShadowStudyRepository {
    private final GridShadowStudyMapper studyMapper;
    private final GridShadowDailyFactMapper factMapper;

    public MybatisGridShadowStudyRepository(GridShadowStudyMapper studyMapper, GridShadowDailyFactMapper factMapper) {
        this.studyMapper = studyMapper;
        this.factMapper = factMapper;
    }

    @Override
    @Transactional
    public long create(GridShadowStudy study) {
        GridShadowStudyEntity entity = toEntity(study);
        if (studyMapper.insert(entity) != 1 || entity.getId() == null) {
            throw new IllegalStateException("Shadow观察创建失败");
        }
        return entity.getId();
    }

    @Override
    public Optional<GridShadowStudy> find(long id) {
        if (id <= 0) return Optional.empty();
        return Optional.ofNullable(studyMapper.selectById(id)).map(MybatisGridShadowStudyRepository::fromEntity);
    }

    @Override
    public List<GridShadowStudy> findAll() {
        return studyMapper.selectList(lambdaQuery(GridShadowStudyEntity.class)
                        .orderByDesc(GridShadowStudyEntity::getCreatedAt)
                        .orderByDesc(GridShadowStudyEntity::getId))
                .stream().map(MybatisGridShadowStudyRepository::fromEntity).toList();
    }

    @Override
    public List<GridShadowDailyFact> findFacts(long studyId) {
        if (studyId <= 0) return List.of();
        return factMapper.findByStudyId(studyId).stream().map(MybatisGridShadowStudyRepository::fromEntity).toList();
    }

    @Override
    @Transactional
    public void appendFacts(List<GridShadowDailyFact> facts) {
        if (facts == null || facts.size() != 2
                || facts.stream().map(GridShadowDailyFact::role).distinct().count() != 2) {
            throw new IllegalArgumentException("每个估值时点必须同时提供候选与基准事实");
        }
        long studyId = facts.getFirst().studyId();
        if (facts.stream().anyMatch(fact -> fact.studyId() != studyId)) {
            throw new IllegalArgumentException("候选与基准事实必须属于同一个 Shadow 观察");
        }
        GridShadowStudyEntity owner = studyMapper.findByIdForUpdate(studyId);
        if (owner == null) throw new IllegalArgumentException("Shadow 观察不存在");
        if (!ShadowStudyState.ACTIVE.name().equals(owner.getState())) {
            throw new IllegalStateException("已停止的 Shadow 观察不能追加事实");
        }
        for (GridShadowDailyFact fact : facts) factMapper.insertIfAbsent(toEntity(fact));
    }

    @Override
    @Transactional
    public boolean stop(long id, Instant stoppedAt) {
        return studyMapper.stopActive(id, stoppedAt) == 1;
    }

    private static GridShadowStudyEntity toEntity(GridShadowStudy value) {
        GridShadowStudyEntity e = new GridShadowStudyEntity();
        e.setId(value.id()); e.setState(value.state().name());
        e.setConfigurationSnapshotId(value.configurationSnapshotId()); e.setStartAt(value.startAt());
        e.setTargetDays(value.targetDays()); e.setCapital(value.capital()); e.setSimpleEarnApr(value.simpleEarnApr());
        e.setMakerFeeRate(value.makerFeeRate()); e.setTakerFeeRate(value.takerFeeRate());
        e.setExitSlippageBps(value.exitSlippageBps()); e.setMaximumDrawdownRate(value.maximumDrawdownRate());
        e.setMaximumPathDivergenceRate(value.maximumPathDivergenceRate());
        e.setMaximumEndingBtcValueRatio(value.maximumEndingBtcValueRatio());
        e.setCandidateLowerPrice(value.candidate().lowerPrice()); e.setCandidateUpperPrice(value.candidate().upperPrice());
        e.setCandidateGridCount(value.candidate().gridCount()); e.setCandidateTakeProfitPrice(value.candidate().takeProfitPrice().orElse(null));
        e.setCandidateStopLossPrice(value.candidate().stopLossPrice().orElse(null));
        e.setBaselineLowerPrice(value.baseline().lowerPrice()); e.setBaselineUpperPrice(value.baseline().upperPrice());
        e.setBaselineGridCount(value.baseline().gridCount()); e.setEntryReferencePrice(value.entryReferencePrice());
        e.setEvidenceAsOf(value.evidenceAsOf()); e.setEvidenceReference(value.evidenceReference());
        e.setCreatedAt(value.createdAt()); e.setStoppedAt(value.stoppedAt());
        return e;
    }

    private static GridShadowStudy fromEntity(GridShadowStudyEntity e) {
        return new GridShadowStudy(e.getId(), ShadowStudyState.valueOf(e.getState()), e.getConfigurationSnapshotId(),
                e.getStartAt(), e.getTargetDays(), e.getCapital(), e.getSimpleEarnApr(), e.getMakerFeeRate(),
                e.getTakerFeeRate(), e.getExitSlippageBps(), e.getMaximumDrawdownRate(),
                e.getMaximumPathDivergenceRate(), e.getMaximumEndingBtcValueRatio(),
                new GridScenarioParameters(e.getCandidateLowerPrice(), e.getCandidateUpperPrice(), e.getCandidateGridCount(),
                        Optional.ofNullable(e.getCandidateTakeProfitPrice()), Optional.ofNullable(e.getCandidateStopLossPrice())),
                new GridScenarioParameters(e.getBaselineLowerPrice(), e.getBaselineUpperPrice(), e.getBaselineGridCount(),
                        Optional.empty(), Optional.empty()),
                e.getEntryReferencePrice(), e.getEvidenceAsOf(), e.getEvidenceReference(), e.getCreatedAt(), e.getStoppedAt());
    }

    private static GridShadowDailyFactEntity toEntity(GridShadowDailyFact value) {
        GridShadowDailyFactEntity e = new GridShadowDailyFactEntity();
        e.setId(value.id()); e.setShadowStudyId(value.studyId()); e.setVariantRole(value.role().name());
        e.setValuationAt(value.valuationAt()); e.setCandleCount(value.candleCount()); e.setNetIncome(value.netIncome());
        e.setSimpleEarnIncome(value.simpleEarnIncome()); e.setBuyHoldIncome(value.buyHoldIncome());
        e.setMaximumDrawdownRate(value.maximumDrawdownRate()); e.setPathDivergenceRate(value.pathDivergenceRate());
        e.setEndingBtcValueRatio(value.endingBtcValueRatio()); e.setCompletedCycles(value.completedCycles());
        e.setTotalFees(value.totalFees()); e.setExitReason(value.exitReason().name());
        return e;
    }

    private static GridShadowDailyFact fromEntity(GridShadowDailyFactEntity e) {
        return new GridShadowDailyFact(e.getId(), e.getShadowStudyId(), ShadowVariantRole.valueOf(e.getVariantRole()),
                e.getValuationAt(), e.getCandleCount(), e.getNetIncome(), e.getSimpleEarnIncome(), e.getBuyHoldIncome(),
                e.getMaximumDrawdownRate(), e.getPathDivergenceRate(), e.getEndingBtcValueRatio(),
                e.getCompletedCycles(), e.getTotalFees(), GridExitReason.valueOf(e.getExitReason()));
    }
}
