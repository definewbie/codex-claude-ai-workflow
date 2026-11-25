package app.cryptoincome.assessment.domain;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record PhaseOneAssessmentRequest(
        Instant evaluationAsOf,
        Instant from,
        Instant to,
        BigDecimal candidateCapital,
        SimpleEarnQuote simpleEarnQuote,
        BigDecimal hedgedShortNotional,
        SmartArbitrageCosts costs,
        TerminalPolicy terminalPolicy
) {
    public static final Duration REQUIRED_HORIZON = Duration.ofDays(90);

    public PhaseOneAssessmentRequest {
        Objects.requireNonNull(evaluationAsOf, "evaluationAsOf 不能为空");
        Objects.requireNonNull(from, "from 不能为空");
        Objects.requireNonNull(to, "to 不能为空");
        Objects.requireNonNull(candidateCapital, "candidateCapital 不能为空");
        Objects.requireNonNull(simpleEarnQuote, "simpleEarnQuote 不能为空");
        Objects.requireNonNull(hedgedShortNotional, "hedgedShortNotional 不能为空");
        Objects.requireNonNull(costs, "costs 不能为空");
        Objects.requireNonNull(terminalPolicy, "terminalPolicy 不能为空");
        if (!to.isAfter(from) || !Duration.between(from, to).equals(REQUIRED_HORIZON)) {
            throw new IllegalArgumentException("第一期比较窗口必须恰好为 90 天");
        }
        if (evaluationAsOf.isBefore(to)) {
            throw new IllegalArgumentException("evaluationAsOf 不能早于比较窗口结束时间");
        }
        if (candidateCapital.signum() <= 0) {
            throw new IllegalArgumentException("candidateCapital 必须大于 0");
        }
        if (hedgedShortNotional.signum() <= 0) {
            throw new IllegalArgumentException("hedgedShortNotional 必须大于 0");
        }
        if (simpleEarnQuote.termDays() != 90) {
            throw new IllegalArgumentException("简单赚币期限必须为 90 天");
        }
        if (simpleEarnQuote.asOf().isAfter(evaluationAsOf)) {
            throw new IllegalArgumentException("简单赚币报价时间不能晚于 evaluationAsOf");
        }
        if (costs.asOf().isAfter(evaluationAsOf)) {
            throw new IllegalArgumentException("成本快照时间不能晚于 evaluationAsOf");
        }
    }
}
