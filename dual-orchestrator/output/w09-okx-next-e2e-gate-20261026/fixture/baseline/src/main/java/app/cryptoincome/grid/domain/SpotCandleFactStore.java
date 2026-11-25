package app.cryptoincome.grid.domain;

import java.util.List;

public interface SpotCandleFactStore {
    CandleAppendResult appendCompleted(List<SpotCandle> candles);
}
