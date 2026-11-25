package app.cryptoincome.grid.application;

import app.cryptoincome.grid.domain.CandleAppendResult;
import app.cryptoincome.grid.domain.CandleBackfillCoverage;
import app.cryptoincome.grid.domain.CandleBar;
import app.cryptoincome.grid.domain.GridSourceException;
import app.cryptoincome.grid.domain.SpotCandle;
import app.cryptoincome.grid.domain.SpotCandleFactStore;
import app.cryptoincome.grid.domain.SpotCandlePageSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

@Service
public class SpotCandleBackfillService {
    public static final Duration REQUIRED_WINDOW = Duration.ofDays(90);
    private static final Duration PAGE_PACING = Duration.ofMillis(110);
    private final SpotCandlePageSource source;
    private final SpotCandleFactStore store;
    private final Consumer<Duration> pacer;

    @Autowired
    public SpotCandleBackfillService(SpotCandlePageSource source, SpotCandleFactStore store) {
        this(source, store, SpotCandleBackfillService::sleep);
    }

    public SpotCandleBackfillService(
            SpotCandlePageSource source, SpotCandleFactStore store, Consumer<Duration> pacer) {
        this.source = source;
        this.store = store;
        this.pacer = pacer;
    }

    /** 可重跑但不创建通用 execution；每页事实由 Store 独立短事务提交。 */
    public SpotCandleBackfillResult backfill(
            String instrumentId, CandleBar bar, Instant from, Instant to, int limit, int maxPages) {
        return backfill(instrumentId, bar, from, to, limit, maxPages, ignored -> {});
    }

    public SpotCandleBackfillResult backfill(
            String instrumentId, CandleBar bar, Instant from, Instant to, int limit, int maxPages,
            Consumer<SpotCandleBackfillProgress> progress) {
        validate(instrumentId, bar, from, to, limit, maxPages);
        if (progress == null) throw new IllegalArgumentException("progress 不能为空");
        Instant after = to;
        int pages = 0;
        int received = 0;
        int completedInWindow = 0;
        int newFacts = 0;
        int duplicates = 0;

        while (pages < maxPages) {
            List<SpotCandle> page = source.fetchPage(instrumentId, bar, limit, after);
            pages++;
            if (page.isEmpty()) {
                progress.accept(snapshot(pages, received, completedInWindow, newFacts, duplicates, after));
                return new SpotCandleBackfillResult(
                        CandleBackfillCoverage.SOURCE_EXHAUSTED, pages, received, completedInWindow,
                        newFacts, duplicates, after);
            }
            validatePage(page, instrumentId, bar, after);
            received += page.size();
            List<SpotCandle> eligible = page.stream()
                    .filter(SpotCandle::confirmed)
                    .filter(candle -> !candle.openTime().isBefore(from) && candle.openTime().isBefore(to))
                    .toList();
            CandleAppendResult appended = store.appendCompleted(eligible);
            completedInWindow += eligible.size();
            newFacts += appended.newFacts();
            duplicates += appended.duplicateFacts();

            Instant oldest = page.getLast().openTime();
            progress.accept(snapshot(pages, received, completedInWindow, newFacts, duplicates, oldest));
            if (!oldest.isAfter(from)) {
                return new SpotCandleBackfillResult(
                        CandleBackfillCoverage.BOUNDARY_REACHED, pages, received, completedInWindow,
                        newFacts, duplicates, oldest);
            }
            after = oldest;
            if (pages < maxPages) pacer.accept(PAGE_PACING);
        }
        return new SpotCandleBackfillResult(
                CandleBackfillCoverage.BUDGET_EXHAUSTED, pages, received, completedInWindow,
                newFacts, duplicates, after);
    }

    private static SpotCandleBackfillProgress snapshot(
            int pages, int received, int completedInWindow, int newFacts, int duplicates, Instant cursor) {
        return new SpotCandleBackfillProgress(
                pages, received, completedInWindow, newFacts, duplicates, cursor);
    }

    private static void validate(
            String instrumentId, CandleBar bar, Instant from, Instant to, int limit, int maxPages) {
        if (!"BTC-USDT".equals(instrumentId)) throw new IllegalArgumentException("第一版只支持 BTC-USDT");
        if (bar != CandleBar.M1) throw new IllegalArgumentException("第一版只支持 1 分钟 K 线");
        if (from == null || to == null || !Duration.between(from, to).equals(REQUIRED_WINDOW)) {
            throw new IllegalArgumentException("K 线回补窗口必须恰好为 90 天");
        }
        if (from.getNano() % 1_000_000 != 0 || to.getNano() % 1_000_000 != 0) {
            throw new IllegalArgumentException("窗口时间精度不能低于 1 毫秒");
        }
        if (limit < 1 || limit > 300) throw new IllegalArgumentException("limit 必须在 1 到 300 之间");
        if (maxPages < 1 || maxPages > 1000) throw new IllegalArgumentException("maxPages 必须在 1 到 1000 之间");
    }

    private static void validatePage(List<SpotCandle> page, String instrumentId, CandleBar bar, Instant after) {
        Instant previous = null;
        for (SpotCandle candle : page) {
            if (!"OKX".equals(candle.exchange()) || !instrumentId.equals(candle.instrumentId()) || candle.bar() != bar) {
                throw new IllegalArgumentException("K 线页面含非请求来源、标的或周期");
            }
            if (!candle.openTime().isBefore(after)) {
                throw new IllegalArgumentException("K 线游标未严格向过去推进");
            }
            if (previous != null && !candle.openTime().isBefore(previous)) {
                throw new IllegalArgumentException("K 线页面必须按 openTime 严格倒序");
            }
            previous = candle.openTime();
        }
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new GridSourceException(
                    GridSourceException.Category.INTERRUPTED, null, true,
                    "K 线回补节流等待被中断", interrupted);
        }
    }
}
