package app.cryptoincome.grid.domain;

import java.time.Instant;
import java.util.List;

public interface SpotCandlePageSource {
    List<SpotCandle> fetchPage(String instrumentId, CandleBar bar, int limit, Instant after);
}
