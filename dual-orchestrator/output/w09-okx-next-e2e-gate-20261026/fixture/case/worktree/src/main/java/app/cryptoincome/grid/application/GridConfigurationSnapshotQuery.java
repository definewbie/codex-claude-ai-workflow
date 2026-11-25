package app.cryptoincome.grid.application;

import app.cryptoincome.grid.domain.GridConfigurationSnapshot;

import java.time.Instant;
import java.util.Optional;

public interface GridConfigurationSnapshotQuery {
    Optional<GridConfigurationSnapshot> findByIdAsOf(long id, Instant evaluationAsOf);
}
