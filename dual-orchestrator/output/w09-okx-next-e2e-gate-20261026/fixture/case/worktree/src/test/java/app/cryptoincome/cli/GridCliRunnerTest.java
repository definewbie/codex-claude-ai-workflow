package app.cryptoincome.cli;

import app.cryptoincome.grid.application.FixedGridAssessmentService;
import app.cryptoincome.grid.application.SpotCandleBackfillResult;
import app.cryptoincome.grid.application.SpotCandleBackfillService;
import app.cryptoincome.grid.domain.CandleBackfillCoverage;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GridCliRunnerTest {
    @Test
    void printsBoundaryStatusAndNeverClaimsItCreatedAStrategy() {
        SpotCandleBackfillService backfill = mock(SpotCandleBackfillService.class);
        when(backfill.backfill(eq("BTC-USDT"), any(), any(), any(), eq(300), eq(1000)))
                .thenReturn(new SpotCandleBackfillResult(
                        CandleBackfillCoverage.BOUNDARY_REACHED, 432, 129_600,
                        129_600, 129_600, 0, Instant.parse("2026-01-01T00:00:00Z")));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        GridCliRunner runner = new GridCliRunner(backfill, mock(FixedGridAssessmentService.class),
                new PrintStream(bytes, true, StandardCharsets.UTF_8));

        runner.run(new DefaultApplicationArguments(
                "grid", "backfill", "--from", "2026-01-01T00:00:00Z",
                "--to", "2026-04-01T00:00:00Z"));

        assertThat(runner.getExitCode()).isZero();
        assertThat(bytes.toString(StandardCharsets.UTF_8))
                .contains("公共只读接口")
                .contains("coverage=BOUNDARY_REACHED")
                .contains("新增事实=129600")
                .doesNotContain("创建策略成功");
    }

    @Test
    void rejectsNonNinetyDayWindowBeforeAnySourceCall() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        GridCliRunner runner = new GridCliRunner(
                mock(SpotCandleBackfillService.class), mock(FixedGridAssessmentService.class),
                new PrintStream(bytes, true, StandardCharsets.UTF_8));

        runner.run(new DefaultApplicationArguments(
                "grid", "assess", "--from", "bad"));

        assertThat(runner.getExitCode()).isEqualTo(GridCliRunner.EXIT_ARGS);
        assertThat(bytes.toString(StandardCharsets.UTF_8)).contains("缺少必填参数");
    }
}
