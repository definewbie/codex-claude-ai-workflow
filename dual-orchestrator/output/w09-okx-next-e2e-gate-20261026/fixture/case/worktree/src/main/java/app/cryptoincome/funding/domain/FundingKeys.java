package app.cryptoincome.funding.domain;

import java.util.Locale;
import java.util.regex.Pattern;

/** 同步/回补共用的幂等键与标的校验。 */
public final class FundingKeys {

    private static final Pattern OKX_SWAP = Pattern.compile("^[A-Z0-9]+-[A-Z0-9]+-SWAP$");
    private static final Pattern SAFE_SYNC_KEY = Pattern.compile("^[a-z0-9._-]+$");
    private static final Pattern PRINTABLE_REQUEST_KEY = Pattern.compile("^[\\x21-\\x7E]+$");

    private FundingKeys() {
    }

    public static String normalizeInstrument(String instrumentId) {
        String normalized = requireText(instrumentId, "instrumentId", 64).toUpperCase(Locale.ROOT);
        if (!OKX_SWAP.matcher(normalized).matches()) {
            throw new IllegalArgumentException("首期仅支持 OKX 永续合约标识，例如 BTC-USDT-SWAP");
        }
        return normalized;
    }

    /**
     * syncKey 统一转小写并限制为安全字符: 排除控制字符(防止 stale-list 输出被注入伪造行)、
     * 空白、冒号(破坏组合键结构)与任意 Unicode。大小写归一让 "Manual-01" 与 "manual-01"
     * 显式指向同一逻辑请求, 而不是依赖数据库 ci 排序规则的隐式行为。
     */
    public static String safeSyncKey(String value) {
        String normalized = requireText(value, "syncKey", 64).toLowerCase(Locale.ROOT);
        if (!SAFE_SYNC_KEY.matcher(normalized).matches()) {
            throw new IllegalArgumentException("syncKey 只能包含小写字母、数字与 . _ -（输入会统一转小写）");
        }
        return normalized;
    }

    public static String printableRequestKey(String value) {
        String normalized = requireText(value, "requestKey", 128);
        if (!PRINTABLE_REQUEST_KEY.matcher(normalized).matches()) {
            throw new IllegalArgumentException("requestKey 含有不可打印或空白字符");
        }
        return normalized;
    }

    public static String requireText(String value, String name, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(name + " 长度不能超过 " + maxLength);
        }
        return normalized;
    }
}
