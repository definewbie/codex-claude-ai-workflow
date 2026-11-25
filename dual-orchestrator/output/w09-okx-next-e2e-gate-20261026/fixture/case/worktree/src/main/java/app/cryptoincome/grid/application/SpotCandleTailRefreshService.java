package app.cryptoincome.grid.application;

import app.cryptoincome.grid.domain.CandleAppendResult;
import app.cryptoincome.grid.domain.CandleBar;
import app.cryptoincome.grid.domain.SpotCandle;
import app.cryptoincome.grid.domain.SpotCandleFactStore;
import app.cryptoincome.grid.domain.SpotCandlePageSource;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class SpotCandleTailRefreshService {
    private static final int TAIL_LIMIT = 300;
    private final SpotCandlePageSource source;
    private final SpotCandleFactStore store;

    public SpotCandleTailRefreshService(SpotCandlePageSource source, SpotCandleFactStore store) {
        this.source = source;
        this.store = store;
    }

    public SpotCandleTailRefreshResult refresh(Instant toExclusive) {
        if (toExclusive == null || !toExclusive.equals(toExclusive.truncatedTo(ChronoUnit.MINUTES))) {
            throw new IllegalArgumentException("分钟 K 线刷新终点必须按分钟对齐");
        }
        List<SpotCandle> page = source.fetchPage("BTC-USDT", CandleBar.M1, TAIL_LIMIT, toExclusive);
        List<SpotCandle> completed = page.stream()
                .filter(SpotCandle::confirmed)
                .filter(candle -> candle.openTime().isBefore(toExclusive))
                .toList();
        CandleAppendResult appended = store.appendCompleted(completed);
        return new SpotCandleTailRefreshResult(toExclusive, page.size(), completed.size(),
                appended.newFacts(), appended.duplicateFacts());
    }
}
