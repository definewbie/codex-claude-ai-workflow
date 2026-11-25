package app.cryptoincome.shadow.application;

import app.cryptoincome.grid.application.FixedGridBacktester;
import app.cryptoincome.grid.application.SpotCandleQuery;
import app.cryptoincome.grid.domain.*;
import app.cryptoincome.shadow.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GridShadowStudyServiceTest {
    private static final Instant START = Instant.parse("2026-08-19T00:00:00Z");
    private static final Instant AS_OF = Instant.parse("2026-08-20T00:10:00Z");

    @Test
    void derivesBaselineFromConfirmedSnapshotAndFreezesBothDefinitions() {
        InMemoryRepository repository = new InMemoryRepository();
        GridShadowStudyService service = service(repository, candles(START.minusSeconds(60), 1), new AtomicInteger());

        GridShadowView result = service.create(command());

        assertThat(result.study().candidate().gridCount()).isEqualTo(30);
        assertThat(result.study().baseline().gridCount()).isEqualTo(50);
        assertThat(result.study().baseline().lowerPrice()).isEqualByComparingTo("90");
        assertThat(result.study().entryReferencePrice()).isEqualByComparingTo("110");
        assertThat(result.status()).isEqualTo(GridShadowStatus.WAITING_FOR_DATA);
    }

    @Test
    void appendsCandidateAndBaselineFactsForTheSameValuationAndReplaysIdempotently() {
        InMemoryRepository repository = new InMemoryRepository();
        List<SpotCandle> all = new ArrayList<>(candles(START.minusSeconds(60), 1));
        all.addAll(candles(START, 1_440));
        GridShadowStudyService service = service(repository, all, new AtomicInteger());
        long id = service.create(command()).study().id();

        GridShadowView first = service.refresh(id, START.plusSeconds(86_400), AS_OF);
        GridShadowView replay = service.refresh(id, START.plusSeconds(86_400), AS_OF);

        assertThat(first.status()).isEqualTo(GridShadowStatus.OBSERVING);
        assertThat(first.facts()).extracting(GridShadowDailyFact::role)
                .containsExactly(ShadowVariantRole.BASELINE, ShadowVariantRole.CANDIDATE);
        assertThat(replay.facts()).hasSize(2);
        assertThat(replay.latestCandidate().valuationAt()).isEqualTo(replay.latestBaseline().valuationAt());
        assertThat(replay.dailyReport().parameterAction()).isEqualTo("KEEP_FROZEN");
        assertThat(replay.candidateReturns().cumulativeReturnRate()).isNotNull();
        assertThat(replay.candidateReturns().simpleAnnualizedReturnRate()).isNull();
        assertThat(replay.candidateReturns().minimumAnnualizationDays()).isEqualTo(7);
    }

    @Test
    void listsReadableStudySummariesFromDefinitionsAndFacts() {
        InMemoryRepository repository = new InMemoryRepository();
        List<SpotCandle> all = new ArrayList<>(candles(START.minusSeconds(60), 1));
        all.addAll(candles(START, 1_440));
        GridShadowStudyService service = service(repository, all, new AtomicInteger());
        long id = service.create(command()).study().id();
        service.refresh(id, START.plusSeconds(86_400), AS_OF);

        List<GridShadowSummary> summaries = service.list();

        assertThat(summaries).hasSize(1);
        assertThat(summaries.getFirst().id()).isEqualTo(id);
        assertThat(summaries.getFirst().status()).isEqualTo(GridShadowStatus.OBSERVING);
        assertThat(summaries.getFirst().observedMinutes()).isEqualTo(1_440);
        assertThat(summaries.getFirst().candidate().gridCount()).isEqualTo(30);
        assertThat(summaries.getFirst().baseline().gridCount()).isEqualTo(50);
    }

    @Test
    void terminalGuardPreventsCandleReadAndFactAppendAfterStop() {
        InMemoryRepository repository = new InMemoryRepository();
        AtomicInteger reads = new AtomicInteger();
        GridShadowStudyService service = service(repository, candles(START.minusSeconds(60), 1), reads);
        long id = service.create(command()).study().id();
        service.stop(id, START.plusSeconds(60));
        int readsBeforeRefresh = reads.get();

        assertThatThrownBy(() -> service.refresh(id, START.plusSeconds(86_400), AS_OF))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("已停止");
        assertThat(reads.get()).isEqualTo(readsBeforeRefresh);
        assertThat(repository.facts).isEmpty();
    }

    @Test
    void incompleteWindowWaitsWithoutCreatingPartialFacts() {
        InMemoryRepository repository = new InMemoryRepository();
        List<SpotCandle> all = new ArrayList<>(candles(START.minusSeconds(60), 1));
        all.addAll(candles(START, 10));
        GridShadowStudyService service = service(repository, all, new AtomicInteger());
        long id = service.create(command()).study().id();

        GridShadowView result = service.refresh(id, START.plusSeconds(86_400), AS_OF);

        assertThat(result.status()).isEqualTo(GridShadowStatus.WAITING_FOR_DATA);
        assertThat(result.facts()).isEmpty();
    }

    @Test
    void rejectsIntradayValuationWithoutReadingCandlesOrWritingFacts() {
        InMemoryRepository repository = new InMemoryRepository();
        AtomicInteger reads = new AtomicInteger();
        GridShadowStudyService service = service(repository, candles(START.minusSeconds(60), 1), reads);
        long id = service.create(command()).study().id();
        int readsAfterCreate = reads.get();

        assertThatThrownBy(() -> service.refresh(id, START.plusSeconds(3_600), AS_OF))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("每日估值");
        assertThat(reads.get()).isEqualTo(readsAfterCreate);
        assertThat(repository.facts).isEmpty();
    }

    @Test
    void targetReportRejectsCandidateThatDoesNotBeatAllBenchmarks() {
        InMemoryRepository repository = new InMemoryRepository();
        List<SpotCandle> all = new ArrayList<>(candles(START.minusSeconds(60), 1));
        all.addAll(flatCandles(START, 7 * 1_440));
        GridShadowStudyService service = service(repository, all, new AtomicInteger());
        long id = service.create(command()).study().id();

        GridShadowView result = service.refresh(id, START.plusSeconds(7 * 86_400L), AS_OF.plusSeconds(7 * 86_400L));

        assertThat(result.status()).isEqualTo(GridShadowStatus.TARGET_REACHED);
        assertThat(result.dailyReport().parameterAction()).isEqualTo("REJECT_CANDIDATE");
        assertThat(result.dailyReport().positionAction()).isEqualTo("NO_INCREASE");
    }

    private static GridShadowStudyService service(InMemoryRepository repository,
                                                  List<SpotCandle> candles, AtomicInteger reads) {
        SpotCandleQuery query = (exchange, instrument, bar, from, to, asOf) -> {
            reads.incrementAndGet();
            return candles.stream().filter(c -> !c.openTime().isBefore(from) && c.openTime().isBefore(to)).toList();
        };
        return new GridShadowStudyService(repository, (id, asOf) -> Optional.of(configuration()), query,
                new FixedGridBacktester(), Clock.fixed(AS_OF, ZoneOffset.UTC));
    }

    private static GridShadowCreateCommand command() {
        return new GridShadowCreateCommand(7, START, 7, new BigDecimal("20000"), new BigDecimal("0.035"),
                new BigDecimal("0.0006"), new BigDecimal("0.0007"), new BigDecimal("10"),
                new BigDecimal("0.15"), new BigDecimal("0.02"), new BigDecimal("0.60"),
                new GridScenarioParameters(new BigDecimal("95"), new BigDecimal("125"), 30,
                        Optional.empty(), Optional.empty()), AS_OF, "OKX参数页+本地K线");
    }

    private static GridConfigurationSnapshot configuration() {
        return new GridConfigurationSnapshot("OKX", "BTC-USDT", GridConfigurationSource.OKX_APP_MANUAL_CONFIRMATION,
                "3-6个月", new BigDecimal("90"), new BigDecimal("130"), 50, GridType.ARITHMETIC,
                Optional.empty(), Optional.empty(), Optional.empty(), false, false, false, false,
                Optional.empty(), Optional.empty(), "OKX手机App", START.minusSeconds(60));
    }

    private static List<SpotCandle> candles(Instant from, int count) {
        List<SpotCandle> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Instant time = from.plusSeconds(i * 60L);
            result.add(new SpotCandle("OKX", "BTC-USDT", CandleBar.M1, time,
                    new BigDecimal("110"), new BigDecimal("121"), new BigDecimal("99"), new BigDecimal("110"),
                    BigDecimal.ONE, new BigDecimal("110"), true, "/history", "[]",
                    String.format("%064x", i + 1), AS_OF));
        }
        return List.copyOf(result);
    }

    private static List<SpotCandle> flatCandles(Instant from, int count) {
        List<SpotCandle> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Instant time = from.plusSeconds(i * 60L);
            result.add(new SpotCandle("OKX", "BTC-USDT", CandleBar.M1, time,
                    new BigDecimal("110"), new BigDecimal("110"), new BigDecimal("110"), new BigDecimal("110"),
                    BigDecimal.ONE, new BigDecimal("110"), true, "/history", "[]",
                    String.format("%064x", i + 100_000), AS_OF));
        }
        return List.copyOf(result);
    }

    private static final class InMemoryRepository implements GridShadowStudyRepository {
        private GridShadowStudy study;
        private final Map<String, GridShadowDailyFact> facts = new TreeMap<>();

        @Override public long create(GridShadowStudy value) {
            study = copy(value, 1L, value.state(), value.stoppedAt()); return 1L;
        }
        @Override public Optional<GridShadowStudy> find(long id) { return id == 1 ? Optional.ofNullable(study) : Optional.empty(); }
        @Override public List<GridShadowStudy> findAll() { return study == null ? List.of() : List.of(study); }
        @Override public List<GridShadowDailyFact> findFacts(long id) { return List.copyOf(facts.values()); }
        @Override public void appendFacts(List<GridShadowDailyFact> values) {
            values.forEach(v -> facts.putIfAbsent(v.valuationAt() + ":" + v.role(), v));
        }
        @Override public boolean stop(long id, Instant at) {
            if (study == null || study.state() != ShadowStudyState.ACTIVE) return false;
            study = copy(study, study.id(), ShadowStudyState.STOPPED, at); return true;
        }
        private static GridShadowStudy copy(GridShadowStudy v, Long id, ShadowStudyState state, Instant stoppedAt) {
            return new GridShadowStudy(id, state, v.configurationSnapshotId(), v.startAt(), v.targetDays(), v.capital(),
                    v.simpleEarnApr(), v.makerFeeRate(), v.takerFeeRate(), v.exitSlippageBps(),
                    v.maximumDrawdownRate(), v.maximumPathDivergenceRate(), v.maximumEndingBtcValueRatio(),
                    v.candidate(), v.baseline(), v.entryReferencePrice(), v.evidenceAsOf(), v.evidenceReference(),
                    v.createdAt(), stoppedAt);
        }
    }
}
