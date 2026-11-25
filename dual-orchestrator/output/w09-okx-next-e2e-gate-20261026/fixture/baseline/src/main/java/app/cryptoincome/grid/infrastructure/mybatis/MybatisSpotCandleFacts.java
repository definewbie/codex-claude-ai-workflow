package app.cryptoincome.grid.infrastructure.mybatis;

import app.cryptoincome.grid.application.SpotCandleQuery;
import app.cryptoincome.grid.application.SpotCandleCoverageQuery;
import app.cryptoincome.grid.domain.CandleAppendResult;
import app.cryptoincome.grid.domain.CandleBar;
import app.cryptoincome.grid.domain.CandleWindowCoverage;
import app.cryptoincome.grid.domain.SpotCandle;
import app.cryptoincome.grid.domain.SpotCandleFactStore;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Repository
public class MybatisSpotCandleFacts implements SpotCandleFactStore, SpotCandleQuery, SpotCandleCoverageQuery {
    private final SpotCandleFactMapper mapper;

    public MybatisSpotCandleFacts(SpotCandleFactMapper mapper) { this.mapper = mapper; }

    @Override
    @Transactional
    public CandleAppendResult appendCompleted(List<SpotCandle> candles) {
        int inserted = 0;
        for (SpotCandle candle : candles) {
            if (!candle.confirmed()) throw new IllegalArgumentException("未完成 K 线不能写入事实表");
            inserted += mapper.insert(
                    candle.exchange(), candle.instrumentId(), candle.bar().providerValue(), candle.openTime(),
                    candle.open(), candle.high(), candle.low(), candle.close(),
                    candle.baseVolume(), candle.quoteVolume(), candle.sourceEndpoint(),
                    candle.sourcePayload(), candle.payloadHash(), candle.observedAt());
        }
        return new CandleAppendResult(candles.size(), inserted, candles.size() - inserted);
    }

    @Override
    public List<SpotCandle> findCompletedAsOf(
            String exchange, String instrumentId, CandleBar bar,
            Instant from, Instant to, Instant evaluationAsOf) {
        return mapper.selectCompletedAsOf(
                        exchange, instrumentId, bar.providerValue(), from, to, evaluationAsOf).stream()
                .map(row -> new SpotCandle(
                        row.getExchange(), row.getInstrumentId(), CandleBar.M1, row.getOpenTime(),
                        row.getOpenPrice(), row.getHighPrice(), row.getLowPrice(), row.getClosePrice(),
                        row.getBaseVolume(), row.getQuoteVolume(), true,
                        row.getSourceEndpoint(), row.getSourcePayload(), row.getPayloadHash(), row.getFirstObservedAt()))
                .toList();
    }

    @Override
    public CandleWindowCoverage inspect(
            String exchange, String instrumentId, CandleBar bar,
            Instant from, Instant to, Instant evaluationAsOf) {
        if (from == null || to == null || evaluationAsOf == null) {
            throw new IllegalArgumentException("K 线覆盖查询时间不能为空");
        }
        SpotCandleCoverageRow row = mapper.inspectCoverage(
                exchange, instrumentId, bar.providerValue(), from, to, evaluationAsOf);
        int expected = Math.toIntExact(java.time.Duration.between(from, to).toMinutes());
        int actual = row == null || row.getActualMinutes() == null ? 0 : Math.toIntExact(row.getActualMinutes());
        Instant first = row == null ? null : row.getFirstOpenTime();
        Instant last = row == null ? null : row.getLastOpenTime();
        boolean complete = actual == expected && first != null && last != null
                && first.equals(from) && last.plusSeconds(60).equals(to);
        return new CandleWindowCoverage(from, to, expected, actual, first, last, complete);
    }
}
