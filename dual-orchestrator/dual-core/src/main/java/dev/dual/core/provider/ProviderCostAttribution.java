package dev.dual.core.provider;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

public final class ProviderCostAttribution {
    private ProviderCostAttribution() {
    }

    public static Map<String, Object> calculate(
            ProviderPricingCatalog pricing,
            Map<String, Object> usage,
            String agent,
            Function<String, String> env
    ) {
        var result = new LinkedHashMap<String, Object>();
        var agentKey = agent == null ? "" : agent.trim().toUpperCase();
        var providerId = nonBlank(
                text(usage.get("providerId")),
                nonBlank(env.apply("DUAL_PROVIDER_ID_" + agentKey), nonBlank(env.apply("DUAL_PROVIDER_ID"), ""))
        );
        var model = nonBlank(
                text(usage.get("model")),
                nonBlank(env.apply("DUAL_MODEL_" + agentKey), nonBlank(env.apply("DUAL_MODEL"), ""))
        );
        var billingMode = normalizeBillingMode(nonBlank(text(usage.get("billingMode")), env.apply("DUAL_BILLING_MODE")));
        result.put("providerId", providerId);
        result.put("model", model);
        result.put("billingMode", billingMode);
        result.put("providerMetadataSource", text(usage.get("providerMetadataSource")));
        result.put("inputCacheHitTokens", longValue(usage.get("cachedReadTokens")));
        result.put("inputCacheMissTokens", longValue(usage.get("inputTokens")));
        result.put("outputTokens", longValue(usage.get("outputTokens")));
        result.put("cacheWriteTokens", longValue(usage.get("cachedWriteTokens")));
        result.put("clientReportedCost", decimalValue(usage.get("costAmount")));
        result.put("clientReportedCurrency", text(usage.get("costCurrency")));
        var type = text(usage.get("measurementType"));
        if (!"PROVIDER_RUN_USAGE".equals(type)) {
            result.put("cacheSignalReliability", "not_applicable");
            result.put("costEstimateReliability", "not_applicable");
            result.put("providerCacheDecision", "not_applicable");
            result.put("reason", "not provider run usage");
            return result;
        }
        if (providerId.isBlank() || model.isBlank()) {
            result.put("cacheSignalReliability", "pricing_unknown");
            result.put("costEstimateReliability", "pricing_unknown");
            result.put("providerCacheDecision", "measure_only");
            result.put("reason", "provider/model missing");
            return result;
        }
        var price = pricing.priceFor(providerId, model).orElse(null);
        if (price == null) {
            if (isSubscriptionTokenBilling(billingMode)) {
                result.put("cacheSignalReliability", longValue(usage.get("cachedReadTokens")) > 0
                        ? "provider_cache_tokens_reported"
                        : "provider_usage_tokens_reported");
                result.put("costEstimateReliability", "subscription_unpriced");
                result.put("providerCacheDecision", "token_policy_active");
                result.put("providerCacheDecisionReason",
                        "provider/model and cache token signal are known; amount is subscription-unpriced");
                result.put("reason", "subscription or bundled billing; token usage is attributable but no per-token cost is visible");
                return result;
            }
            result.put("cacheSignalReliability", "pricing_unknown");
            result.put("costEstimateReliability", "pricing_unknown");
            result.put("providerCacheDecision", "measure_only");
            result.put("reason", "no pricing entry for provider/model");
            return result;
        }
        var policy = ProviderCachePolicy.decide(price.inputCacheHitPer1M(), price.inputCacheMissPer1M());
        result.put("providerCacheDecision", policy.recommendation());
        result.put("providerCacheDecisionReason", policy.reason());
        result.put("cacheHitToMissRatio", policy.cacheHitToMissRatio());
        var calculated = price.cost(
                longValue(usage.get("cachedReadTokens")),
                longValue(usage.get("inputTokens")),
                longValue(usage.get("outputTokens")),
                longValue(usage.get("cachedWriteTokens"))
        );
        var reported = decimalValue(usage.get("costAmount"));
        result.put("officialCalculatedCost", calculated);
        result.put("officialCalculatedCurrency", price.currency());
        result.put("officialPricingSourceUrl", price.sourceUrl());
        result.put("dualCalculatedCost", calculated);
        result.put("dualCalculatedCurrency", price.currency());
        result.put("cacheSignalReliability", longValue(usage.get("cachedReadTokens")) > 0
                ? "provider_cache_tokens_reported"
                : "provider_usage_tokens_reported");
        if ("TOKENS_ONLY".equalsIgnoreCase(price.currency())) {
            result.put("providerCacheDecision", "token_policy_active");
            result.put("providerCacheDecisionReason",
                    "provider/model and cache token signal are known; amount is token-only");
            result.put("costEstimateReliability", "token_only");
            result.put("reason", "provider/model has no visible per-token billing; token usage only");
            return result;
        }
        result.put("costEstimateReliability", "official_pricing");
        if (reported.signum() <= 0) {
            result.put("reason", "client reported cost missing; using official pricing calculation");
            return result;
        }
        var drift = calculated.subtract(reported).abs()
                .divide(calculated.max(reported), 4, RoundingMode.HALF_UP);
        result.put("clientCostDriftRatio", drift);
        if (drift.compareTo(new BigDecimal("0.15")) <= 0) {
            result.put("clientCostConsistency", "matches_official_pricing");
        } else {
            result.put("clientCostConsistency", "differs_from_official_pricing");
        }
        return result;
    }

    public static String normalizeBillingMode(String billingMode) {
        if ("subscription_tokens".equalsIgnoreCase(billingMode) || "token_only".equalsIgnoreCase(billingMode)) {
            return "subscription_token_only";
        }
        return billingMode == null ? "" : billingMode;
    }

    static boolean isSubscriptionTokenBilling(String billingMode) {
        return "subscription_token_only".equalsIgnoreCase(billingMode)
                || "subscription_tokens".equalsIgnoreCase(billingMode)
                || "token_only".equalsIgnoreCase(billingMode);
    }

    static long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value != null) {
            try {
                return Long.parseLong(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    static BigDecimal decimalValue(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        if (value != null) {
            try {
                return new BigDecimal(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return BigDecimal.ZERO;
            }
        }
        return BigDecimal.ZERO;
    }

    private static String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
