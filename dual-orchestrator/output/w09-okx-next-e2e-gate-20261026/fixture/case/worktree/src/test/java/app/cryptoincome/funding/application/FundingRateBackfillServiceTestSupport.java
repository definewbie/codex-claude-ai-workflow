package app.cryptoincome.funding.application;

import app.cryptoincome.funding.domain.DerivativeInstrumentType;
import app.cryptoincome.funding.domain.FundingFormulaType;
import app.cryptoincome.funding.domain.FundingRateHistoryPager;
import app.cryptoincome.funding.domain.FundingRateObservation;
import app.cryptoincome.funding.domain.FundingSettlementMethod;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** 脚本化分页源: 按 after 毫秒值返回预置页, 可选在第 N 次调用后抛出来源异常。 */
public final class FundingRateBackfillServiceTestSupport {

    private FundingRateBackfillServiceTestSupport() {
    }

    public static final class ScriptedPager implements FundingRateHistoryPager {
        /** 每个实例独立盐, 避免不同测试的事实身份互相碰撞导致顺序依赖。 */
        private static final AtomicLong SALT = new AtomicLong();

        public final AtomicInteger calls = new AtomicInteger();
        private final Map<Long, List<FundingRateObservation>> pages = new HashMap<>();
        private final String salt = Long.toHexString(SALT.incrementAndGet());
        private volatile int failAfterCalls = Integer.MAX_VALUE;
        private volatile RuntimeException failure;

        @SafeVarargs
        public ScriptedPager(Instant windowEnd, List<FundingRateObservation>... chunks) {
            this(windowEnd, Arrays.asList(chunks));
        }

        public ScriptedPager(Instant windowEnd, List<List<FundingRateObservation>> chunks) {
            this(windowEnd, List.of(), false);
            Instant after = windowEnd;
            for (List<FundingRateObservation> chunk : chunks) {
                pages.put(after.toEpochMilli(), chunk);
                after = chunk.get(chunk.size() - 1).fundingTime();
            }
        }

        private ScriptedPager(Instant windowEnd, List<List<Instant>> timeChunks, boolean byTime) {
            Instant after = windowEnd;
            for (List<Instant> chunk : timeChunks) {
                pages.put(after.toEpochMilli(), chunk.stream().map(t -> observation(t, windowEnd)).toList());
                after = chunk.get(chunk.size() - 1);
            }
        }

        /** 按 pageSize 将 descTimes 切页, 首页 after=windowEnd, 之后 after=上一页最旧记录。 */
        public static ScriptedPager pagesOver(Instant windowEnd, int pageSize, Instant... descTimes) {
            List<List<Instant>> chunks = new ArrayList<>();
            List<Instant> times = Arrays.asList(descTimes);
            for (int i = 0; i < times.size(); i += pageSize) {
                chunks.add(new ArrayList<>(times.subList(i, Math.min(i + pageSize, times.size()))));
            }
            return new ScriptedPager(windowEnd, chunks, true);
        }

        public static ScriptedPager empty() {
            return new ScriptedPager(Instant.parse("2026-08-01T00:00:00Z"), List.of());
        }

        public void failAfterCalls(int callsBeforeFailure, RuntimeException failure) {
            this.failAfterCalls = callsBeforeFailure;
            this.failure = failure;
        }

        public void clearFailure() {
            this.failAfterCalls = Integer.MAX_VALUE;
            this.failure = null;
        }

        @Override
        public List<FundingRateObservation> fetchPage(String instrumentId, int limit, Instant after) {
            int call = calls.incrementAndGet();
            if (call > failAfterCalls && failure != null) {
                throw failure;
            }
            return pages.getOrDefault(after.toEpochMilli(), List.of());
        }

        private FundingRateObservation observation(Instant fundingTime, Instant observedAt) {
            return new FundingRateObservation(
                    "OKX", DerivativeInstrumentType.SWAP, "BTC-USDT-SWAP",
                    FundingFormulaType.WITH_RATE, new BigDecimal("0.0001"), new BigDecimal("0.0001"),
                    fundingTime, FundingSettlementMethod.CURRENT_PERIOD,
                    "/api/v5/public/funding-rate-history", "{\"fixture\":true}",
                    paddedHash(fundingTime), observedAt);
        }

        private String paddedHash(Instant time) {
            return (Long.toHexString(time.toEpochMilli()) + salt + "0".repeat(64)).substring(0, 64);
        }
    }
}
