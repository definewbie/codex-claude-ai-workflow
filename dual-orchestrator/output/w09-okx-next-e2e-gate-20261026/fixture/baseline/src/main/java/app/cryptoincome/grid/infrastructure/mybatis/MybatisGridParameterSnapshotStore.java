package app.cryptoincome.grid.infrastructure.mybatis;

import app.cryptoincome.grid.domain.GridParameterSnapshot;
import app.cryptoincome.grid.domain.GridParameterSnapshotStore;
import app.cryptoincome.grid.application.GridParameterSnapshotQuery;
import app.cryptoincome.grid.domain.GridDuration;
import app.cryptoincome.grid.domain.GridType;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Repository
public class MybatisGridParameterSnapshotStore implements GridParameterSnapshotStore, GridParameterSnapshotQuery {
    private final GridParameterSnapshotMapper mapper;

    public MybatisGridParameterSnapshotStore(GridParameterSnapshotMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public long append(GridParameterSnapshot snapshot) {
        GridParameterSnapshotEntity entity = toEntity(snapshot);
        if (mapper.insert(entity) != 1 || entity.getId() == null) {
            throw new IllegalStateException("网格参数快照写入失败");
        }
        return entity.getId();
    }

    @Override
    public Optional<GridParameterSnapshot> findByIdAsOf(long snapshotId, Instant evaluationAsOf) {
        if (snapshotId <= 0 || evaluationAsOf == null) return Optional.empty();
        return Optional.ofNullable(mapper.findByIdAsOf(snapshotId, evaluationAsOf)).map(MybatisGridParameterSnapshotStore::fromEntity);
    }

    private static GridParameterSnapshotEntity toEntity(GridParameterSnapshot snapshot) {
        GridParameterSnapshotEntity entity = new GridParameterSnapshotEntity();
        entity.setExchange(snapshot.exchange());
        entity.setInstrumentId(snapshot.instrumentId());
        entity.setDuration(snapshot.duration().providerValue());
        entity.setGridType(snapshot.gridType().name());
        entity.setLowerPrice(snapshot.lowerPrice());
        entity.setUpperPrice(snapshot.upperPrice());
        entity.setGridCount(snapshot.gridCount());
        entity.setAnnualizedRate(snapshot.annualizedRate());
        entity.setMinimumPerGridProfitRate(snapshot.minimumPerGridProfitRate());
        entity.setMaximumPerGridProfitRate(snapshot.maximumPerGridProfitRate());
        entity.setMinimumInvestment(snapshot.minimumInvestment());
        entity.setInvestmentCurrency(snapshot.investmentCurrency());
        entity.setSourceEndpoint(snapshot.sourceEndpoint());
        entity.setSourcePayload(snapshot.sourcePayload());
        entity.setPayloadHash(snapshot.payloadHash());
        entity.setObservedAt(snapshot.observedAt());
        return entity;
    }

    private static GridParameterSnapshot fromEntity(GridParameterSnapshotEntity entity) {
        return new GridParameterSnapshot(
                entity.getExchange(), entity.getInstrumentId(), GridDuration.fromProvider(entity.getDuration()),
                GridType.valueOf(entity.getGridType()), entity.getLowerPrice(), entity.getUpperPrice(),
                entity.getGridCount(), entity.getAnnualizedRate(), entity.getMinimumPerGridProfitRate(),
                entity.getMaximumPerGridProfitRate(), entity.getMinimumInvestment(), entity.getInvestmentCurrency(),
                entity.getSourceEndpoint(), entity.getSourcePayload(), entity.getPayloadHash(), entity.getObservedAt());
    }
}
