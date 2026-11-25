package app.cryptoincome.grid.domain;

public interface GridConfigurationSnapshotStore {
    long append(GridConfigurationSnapshot snapshot);
}
