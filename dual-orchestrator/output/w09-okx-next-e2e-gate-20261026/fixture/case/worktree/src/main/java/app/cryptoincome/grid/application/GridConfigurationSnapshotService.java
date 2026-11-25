package app.cryptoincome.grid.application;

import app.cryptoincome.grid.domain.GridConfigurationSnapshot;
import app.cryptoincome.grid.domain.GridConfigurationSnapshotStore;
import org.springframework.stereotype.Service;

@Service
public class GridConfigurationSnapshotService {
    private final GridConfigurationSnapshotStore store;

    public GridConfigurationSnapshotService(GridConfigurationSnapshotStore store) {
        this.store = store;
    }

    public RecordedGridConfiguration record(GridConfigurationSnapshot snapshot) {
        if (!"OKX".equals(snapshot.exchange()) || !"BTC-USDT".equals(snapshot.instrumentId())) {
            throw new IllegalArgumentException("第一期只接受 OKX BTC-USDT 人工确认配置");
        }
        return new RecordedGridConfiguration(store.append(snapshot), snapshot);
    }
}
