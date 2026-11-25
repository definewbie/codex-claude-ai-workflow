package app.cryptoincome.income;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;

@Service
public class SimpleEarnIncomeService {
    private static final BigDecimal DAYS = new BigDecimal("365");
    private static final BigDecimal USER_SHARE = new BigDecimal("0.85");
    private final SimpleEarnMarketRateSource source;

    public SimpleEarnIncomeService(SimpleEarnMarketRateSource source) { this.source = source; }

    public SimpleEarnIncomeAssessment assess(BigDecimal capital, BigDecimal fixedApr, Instant fixedQuoteAt) {
        requireRate(capital, fixedApr, fixedQuoteAt);
        SimpleEarnMarketRate market = source.fetchUsdtFlexible();
        BigDecimal flexibleNetApr = market.estimatedNextBorrowApr().multiply(USER_SHARE);
        return new SimpleEarnIncomeAssessment(capital,
                new EarnOption("FLEXIBLE", flexibleNetApr, daily(capital, flexibleNetApr),
                        daily(capital, flexibleNetApr).multiply(new BigDecimal("90")), market.observedAt(),
                        "OKX公共市场下一期预估借款APR×85%用户分成", false),
                new EarnOption("FIXED_90D", fixedApr, daily(capital, fixedApr),
                        daily(capital, fixedApr).multiply(new BigDecimal("90")), fixedQuoteAt,
                        "OKX 90天定期确认页（人工录入）", true),
                market.averageBorrowApr24h(), market.previousBorrowApr(), market.estimatedNextBorrowApr());
    }

    private static BigDecimal daily(BigDecimal capital, BigDecimal apr) {
        return capital.multiply(apr).divide(DAYS, MathContext.DECIMAL128);
    }
    private static void requireRate(BigDecimal capital, BigDecimal fixedApr, Instant quoteAt) {
        if (capital == null || capital.signum() <= 0) throw new IllegalArgumentException("资金必须大于0");
        if (fixedApr == null || fixedApr.signum() < 0 || fixedApr.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("90天定期 APR 必须在0到1之间");
        }
        if (quoteAt == null) throw new IllegalArgumentException("90天定期报价时间不能为空");
    }

    public record SimpleEarnIncomeAssessment(
            BigDecimal capital, EarnOption flexible, EarnOption fixed90Days,
            BigDecimal marketAverageBorrowApr24h, BigDecimal marketPreviousBorrowApr,
            BigDecimal marketEstimatedNextBorrowApr) {}
    public record EarnOption(String type, BigDecimal apr, BigDecimal dailyIncome, BigDecimal income90Days,
                             Instant quoteAt, String source, boolean manuallyQuoted) {}
}
