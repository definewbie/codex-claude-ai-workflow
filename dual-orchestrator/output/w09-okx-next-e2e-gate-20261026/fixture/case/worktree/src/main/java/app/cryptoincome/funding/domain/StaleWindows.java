package app.cryptoincome.funding.domain;

import java.time.Duration;

/** stale 判定窗口的统一策略边界: 过短会误杀正常执行, 过长则失去恢复意义。 */
public final class StaleWindows {

    public static final Duration MIN = Duration.ofMinutes(1);
    public static final Duration MAX = Duration.ofDays(30);

    private StaleWindows() {
    }

    public static void requireWithin(Duration olderThan) {
        if (olderThan == null) {
            throw new IllegalArgumentException("olderThan 不能为空");
        }
        if (olderThan.compareTo(MIN) < 0 || olderThan.compareTo(MAX) > 0) {
            throw new IllegalArgumentException("olderThan 必须在 PT1M 到 P30D 之间");
        }
        if (olderThan.getNano() % 1_000 != 0) {
            throw new IllegalArgumentException("olderThan 精度不能低于 1 微秒");
        }
    }

    /** 精确换算为微秒: 拒绝亚微秒精度, 溢出直接抛出而不是回绕。 */
    public static long toExactMicros(Duration olderThan) {
        if (olderThan.getNano() % 1_000 != 0) {
            throw new IllegalArgumentException("olderThan 精度不能低于 1 微秒");
        }
        return Math.addExact(
                Math.multiplyExact(olderThan.getSeconds(), 1_000_000L),
                olderThan.getNano() / 1_000);
    }
}
