package app.cryptoincome.assessment.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record SimpleEarnQuote(BigDecimal apr, int termDays, Instant asOf, String source, boolean termsConfirmed) {
    public SimpleEarnQuote {
        Objects.requireNonNull(apr, "apr 不能为空");
        Objects.requireNonNull(asOf, "asOf 不能为空");
        source = requireText(source, "source");
        if (apr.signum() < 0) {
            throw new IllegalArgumentException("apr 不能为负数");
        }
        if (termDays <= 0) {
            throw new IllegalArgumentException("termDays 必须大于 0");
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " 不能为空");
        return value;
    }
}
