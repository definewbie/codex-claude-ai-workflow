package app.cryptoincome.cli;

import app.cryptoincome.assessment.application.PhaseOneAssessmentService;
import app.cryptoincome.assessment.domain.FundingRatePoint;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AssessIncomeCliRunnerTest {

    @Test
    void printsTypedChineseRecommendationAndNeverSuggestsMoreThanMicroValidation() {
        var invocation = invoke(completeArguments());

        assertThat(invocation.exitCode()).isZero();
        assertThat(invocation.output())
                .contains("第一期收益评估")
                .contains("简单赚币90天收益=172.60 USDT")
                .contains("简单赚币报价来源=OKX确认页")
                .contains("成本输入来源=OKX预览+用户假设")
                .contains("建议=仅小额验证智能套利")
                .contains("首次实盘上限=5000.00 USDT")
                .contains("区间完整性尚未证明")
                .doesNotContain("200000 USDT");
    }

    @Test
    void missingCostsRemainUnknownAndDoNotPrintExactNetIncome() {
        String[] withoutExitAndSlippage = Arrays.stream(completeArguments())
                .filter(value -> !value.equals("--exit-cost") && !value.equals("20.192")
                        && !value.equals("--slippage-cost") && !value.equals("10"))
                .toArray(String[]::new);

        var invocation = invoke(withoutExitAndSlippage);

        assertThat(invocation.exitCode()).isZero();
        assertThat(invocation.output())
                .contains("建议=继续观察智能套利")
                .contains("未知项=退出成本、滑点成本")
                .contains("资金费率净收益=无法精确计算")
                .doesNotContain("首次实盘上限");
    }

    @Test
    void rejectsAggregateAndComponentOpeningCostsTogether() {
        String[] invalid = Arrays.copyOf(completeArguments(), completeArguments().length + 6);
        int start = completeArguments().length;
        invalid[start] = "--opening-spread-cost";
        invalid[start + 1] = "15.810";
        invalid[start + 2] = "--spot-entry-fee";
        invalid[start + 3] = "13.334";
        invalid[start + 4] = "--perp-entry-fee";
        invalid[start + 5] = "6.858";

        var invocation = invoke(invalid);

        assertThat(invocation.exitCode()).isEqualTo(AssessIncomeCliRunner.EXIT_ARGS);
        assertThat(invocation.output()).contains("不能同时提供").doesNotContain("Exception");
    }

    @Test
    void runnerDependsOnApplicationServiceNotInfrastructureMapper() {
        assertThat(Arrays.stream(AssessIncomeCliRunner.class.getDeclaredFields())
                .map(field -> field.getType().getName()))
                .noneMatch(type -> type.contains("Mapper") || type.contains("infrastructure"));
    }

    private static Invocation invoke(String... arguments) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PhaseOneAssessmentService service = new PhaseOneAssessmentService(
                (exchange, instrument, from, to, asOf) -> List.of(
                        new FundingRatePoint(Instant.parse("2026-05-20T08:00:00Z"), new BigDecimal("0.012")),
                        new FundingRatePoint(Instant.parse("2026-06-20T08:00:00Z"), new BigDecimal("0.008"))));
        AssessIncomeCliRunner runner = new AssessIncomeCliRunner(
                service, new PrintStream(bytes, true, StandardCharsets.UTF_8));
        runner.run(new DefaultApplicationArguments(arguments));
        return new Invocation(runner.getExitCode(), bytes.toString(StandardCharsets.UTF_8));
    }

    private static String[] completeArguments() {
        return new String[]{
                "assess-income", "phase-one",
                "--as-of", "2026-08-18T01:00:00Z",
                "--from", "2026-05-20T00:00:00Z",
                "--to", "2026-08-18T00:00:00Z",
                "--capital", "20000",
                "--simple-apr", "0.035",
                "--simple-quote-as-of", "2026-08-18T00:00:00Z",
                "--simple-source", "OKX确认页",
                "--simple-terms-confirmed", "true",
                "--hedged-notional", "19047.61",
                "--cost-as-of", "2026-08-18T00:00:00Z",
                "--cost-source", "OKX预览+用户假设",
                "--opening-cost", "36.001",
                "--exit-cost", "20.192",
                "--slippage-cost", "10",
                "--rebalance-cost", "0",
                "--service-cost", "0",
                "--terminal-policy", "exit-to-usdt"
        };
    }

    private record Invocation(int exitCode, String output) {
    }
}
