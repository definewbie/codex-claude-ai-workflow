package app.cryptoincome.grid.application;

import app.cryptoincome.grid.domain.GridDuration;
import app.cryptoincome.grid.domain.GridParameterSnapshot;
import app.cryptoincome.grid.domain.GridParameterSnapshotStore;
import app.cryptoincome.grid.domain.GridParameterSource;
import org.springframework.stereotype.Service;

@Service
public class GridParameterSnapshotService {
    public static final String V1_INSTRUMENT = "BTC-USDT";

    private final GridParameterSource source;
    private final GridParameterSnapshotStore store;

    public GridParameterSnapshotService(GridParameterSource source, GridParameterSnapshotStore store) {
        this.source = source;
        this.store = store;
    }

    /** 网络调用在事务外；只有已经转成强类型事实的响应才会进入追加式 Store。 */
    public CapturedGridParameter capture(String instrumentId, GridDuration duration) {
        if (!V1_INSTRUMENT.equals(instrumentId)) {
            throw new IllegalArgumentException("第一版只支持 BTC-USDT 现货网格");
        }
        if (duration == null) throw new IllegalArgumentException("duration 不能为空");
        GridParameterSnapshot snapshot = source.fetch(instrumentId, duration);
        long id = store.append(snapshot);
        return new CapturedGridParameter(id, snapshot);
    }
}
