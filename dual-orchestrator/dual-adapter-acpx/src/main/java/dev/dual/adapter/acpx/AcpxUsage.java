package dev.dual.adapter.acpx;

import java.math.BigDecimal;

/**
 * 一次 acpx agent run 的 token usage 观测事实。
 *
 * <p>三种 measurementType 必须严格区分，不能互相伪装：</p>
 * <ul>
 *     <li>{@link #PROVIDER_RUN_USAGE}：来自 stream 中 {@code result.usage} 的本次 provider
 *     用量，允许聚合进 metrics 总量。</li>
 *     <li>{@link #SESSION_USED_SNAPSHOT}：来自 {@code usage_update.used} 的会话累计/上下文
 *     快照，<b>不允许</b>当作单次 run 用量加总，只能单独展示用于判断 session 是否滚雪球。</li>
 *     <li>{@code ARTIFACT_ESTIMATE}（由 ledger 侧按文件大小估算，不在此 record 构造）：
 *     仅作 fallback，不进入总量。</li>
 * </ul>
 *
 * <p>{@link #attribution} 进一步说明 provider_run_usage 是如何归因到本次 run 的，避免把
 * “最近一条 prompt 的 result”伪装成精确匹配。</p>
 */
record AcpxUsage(
        long inputTokens,
        long outputTokens,
        long cachedReadTokens,
        long cachedWriteTokens,
        long totalTokens,
        BigDecimal costAmount,
        String costCurrency,
        String source,
        String measurementType,
        String attribution,
        boolean cumulative,
        long contextUsedTokens,
        BigDecimal sessionCostAmount,
        String sessionCostCurrency,
        String providerId,
        String model,
        String billingMode,
        String providerMetadataSource,
        String unavailableReason
) {
    static final String PROVIDER_RUN_USAGE = "PROVIDER_RUN_USAGE";
    static final String SESSION_USED_SNAPSHOT = "SESSION_USED_SNAPSHOT";
    static final String UNAVAILABLE = "UNAVAILABLE";

    /** result.usage 精确匹配到本次 prompt 文本。 */
    static final String ATTR_EXACT_PROMPT_MATCH = "EXACT_PROMPT_MATCH";
    /** 无法精确匹配 prompt 文本，按 request id 取会话内最近一条 prompt 的 result.usage。 */
    static final String ATTR_LATEST_PROMPT_IN_SESSION = "LATEST_PROMPT_IN_SESSION";
    /** 只有 usage_update，没有 result.usage，记录为会话累计快照。 */
    static final String ATTR_SESSION_USAGE_UPDATE = "SESSION_USAGE_UPDATE";

    AcpxUsage {
        if (measurementType == null || measurementType.isBlank()) {
            measurementType = PROVIDER_RUN_USAGE;
        }
        if (attribution == null) {
            attribution = "";
        }
        if (costCurrency == null) {
            costCurrency = "";
        }
        if (sessionCostCurrency == null) {
            sessionCostCurrency = "";
        }
        if (providerId == null) {
            providerId = "";
        }
        if (model == null) {
            model = "";
        }
        if (billingMode == null) {
            billingMode = "";
        }
        if (providerMetadataSource == null) {
            providerMetadataSource = "";
        }
        if (unavailableReason == null) {
            unavailableReason = "";
        }
    }

    static AcpxUsage unavailable(String reason) {
        return new AcpxUsage(
                0,
                0,
                0,
                0,
                0,
                null,
                "",
                "not_available",
                UNAVAILABLE,
                "",
                false,
                0,
                null,
                "",
                "",
                "",
                "",
                "",
                reason == null ? "" : reason
        );
    }

    AcpxUsage withProviderMetadata(AcpxProviderMetadata metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return this;
        }
        return new AcpxUsage(
                inputTokens,
                outputTokens,
                cachedReadTokens,
                cachedWriteTokens,
                totalTokens,
                costAmount,
                costCurrency,
                source,
                measurementType,
                attribution,
                cumulative,
                contextUsedTokens,
                sessionCostAmount,
                sessionCostCurrency,
                firstNonBlank(providerId, metadata.providerId()),
                firstNonBlank(model, metadata.model()),
                firstNonBlank(billingMode, metadata.billingMode()),
                firstNonBlank(providerMetadataSource, metadata.source()),
                unavailableReason
        );
    }

    private static String firstNonBlank(String current, String fallback) {
        return current == null || current.isBlank() ? (fallback == null ? "" : fallback) : current;
    }
}
