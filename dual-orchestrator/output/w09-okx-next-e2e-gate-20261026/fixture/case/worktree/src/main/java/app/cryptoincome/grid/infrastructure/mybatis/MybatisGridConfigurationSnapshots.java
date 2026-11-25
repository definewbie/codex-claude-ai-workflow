package app.cryptoincome.grid.infrastructure.mybatis;

import app.cryptoincome.grid.application.GridConfigurationSnapshotQuery;
import app.cryptoincome.grid.domain.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Repository
public class MybatisGridConfigurationSnapshots
        implements GridConfigurationSnapshotStore, GridConfigurationSnapshotQuery {
    private final GridConfigurationSnapshotMapper mapper;

    public MybatisGridConfigurationSnapshots(GridConfigurationSnapshotMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public long append(GridConfigurationSnapshot snapshot) {
        GridConfigurationSnapshotEntity entity = toEntity(snapshot);
        if (mapper.insert(entity) != 1 || entity.getId() == null) {
            throw new IllegalStateException("网格人工确认配置写入失败");
        }
        return entity.getId();
    }

    @Override
    public Optional<GridConfigurationSnapshot> findByIdAsOf(long id, Instant evaluationAsOf) {
        if (id <= 0 || evaluationAsOf == null) return Optional.empty();
        return Optional.ofNullable(mapper.findByIdAsOf(id, evaluationAsOf)).map(MybatisGridConfigurationSnapshots::fromEntity);
    }

    private static GridConfigurationSnapshotEntity toEntity(GridConfigurationSnapshot value) {
        GridConfigurationSnapshotEntity entity = new GridConfigurationSnapshotEntity();
        entity.setExchange(value.exchange());
        entity.setInstrumentId(value.instrumentId());
        entity.setSourceType(value.source().name());
        entity.setRecommendationHorizonLabel(value.recommendationHorizonLabel());
        entity.setLowerPrice(value.lowerPrice());
        entity.setUpperPrice(value.upperPrice());
        entity.setGridCount(value.gridCount());
        entity.setGridType(value.gridType().name());
        entity.setDisplayedAnnualizedRate(value.displayedAnnualizedRate().orElse(null));
        entity.setDisplayedMinimumPerGridRate(value.displayedMinimumPerGridRate().orElse(null));
        entity.setDisplayedMaximumPerGridRate(value.displayedMaximumPerGridRate().orElse(null));
        entity.setProfitReinvestmentEnabled(value.profitReinvestmentEnabled());
        entity.setAutoEarnEnabled(value.autoEarnEnabled());
        entity.setTrailingUpEnabled(value.trailingUpEnabled());
        entity.setTrailingDownEnabled(value.trailingDownEnabled());
        entity.setTakeProfitPrice(value.takeProfitPrice().orElse(null));
        entity.setStopLossPrice(value.stopLossPrice().orElse(null));
        entity.setSourceReference(value.sourceReference());
        entity.setObservedAt(value.observedAt());
        return entity;
    }

    private static GridConfigurationSnapshot fromEntity(GridConfigurationSnapshotEntity value) {
        return new GridConfigurationSnapshot(
                value.getExchange(), value.getInstrumentId(), GridConfigurationSource.valueOf(value.getSourceType()),
                value.getRecommendationHorizonLabel(), value.getLowerPrice(), value.getUpperPrice(), value.getGridCount(),
                GridType.valueOf(value.getGridType()), Optional.ofNullable(value.getDisplayedAnnualizedRate()),
                Optional.ofNullable(value.getDisplayedMinimumPerGridRate()), Optional.ofNullable(value.getDisplayedMaximumPerGridRate()),
                value.getProfitReinvestmentEnabled(), value.getAutoEarnEnabled(),
                value.getTrailingUpEnabled(), value.getTrailingDownEnabled(), Optional.ofNullable(value.getTakeProfitPrice()),
                Optional.ofNullable(value.getStopLossPrice()), value.getSourceReference(), value.getObservedAt());
    }
}
