package app.cryptoincome.grid.domain;

import java.time.Duration;
import java.time.Instant;

public record CandleWindowCoverage(
        Instant from,
        Instant to,
        int expectedMinutes,
        int actualMinutes,
        Instant firstOpenTime,
        Instant lastOpenTime,
        boolean complete
) {
    public CandleWindowCoverage {
        if (from == null || to == null || !from.isBefore(to)) {
            throw new IllegalArgumentException("K 线覆盖窗口不合法");
        }
        long expected = Duration.between(from, to).toMinutes();
        if (expected > Integer.MAX_VALUE || expectedMinutes != expected) {
            throw new IllegalArgumentException("K 线预期分钟数与窗口不一致");
        }
        if (actualMinutes < 0 || actualMinutes > expectedMinutes) {
            throw new IllegalArgumentException("K 线实际分钟数不合法");
        }
        boolean endpointsComplete = firstOpenTime != null && lastOpenTime != null
                && firstOpenTime.equals(from) && lastOpenTime.plusSeconds(60).equals(to);
        if (complete != (actualMinutes == expectedMinutes && endpointsComplete)) {
            throw new IllegalArgumentException("K 线完整标志与数据库证据不一致");
        }
    }

    public int missingMinutes() {
        return expectedMinutes - actualMinutes;
    }

    public boolean onlyTrailingMinutesMissing(int maximumTrailingMinutes) {
        if (maximumTrailingMinutes < 1) throw new IllegalArgumentException("尾部等待分钟数必须大于0");
        if (complete || actualMinutes == 0 || firstOpenTime == null || lastOpenTime == null
                || !firstOpenTime.equals(from)) return false;
        long trailingMinutes = Duration.between(lastOpenTime.plusSeconds(60), to).toMinutes();
        return trailingMinutes > 0
                && trailingMinutes <= maximumTrailingMinutes
                && trailingMinutes == missingMinutes();
    }
}
