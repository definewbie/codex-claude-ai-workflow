package app.cryptoincome.grid.application;

import app.cryptoincome.grid.domain.GridParameterSnapshot;

import java.time.Instant;
import java.util.Optional;

public interface GridParameterSnapshotQuery {
    Optional<GridParameterSnapshot> findByIdAsOf(long snapshotId, Instant evaluationAsOf);
}
