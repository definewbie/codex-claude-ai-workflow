package app.cryptoincome.grid.domain;

public interface GridParameterSnapshotStore {
    long append(GridParameterSnapshot snapshot);
}
