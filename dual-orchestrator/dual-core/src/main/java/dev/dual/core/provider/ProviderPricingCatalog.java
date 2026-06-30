package dev.dual.core.provider;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public final class ProviderPricingCatalog {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final List<ModelPrice> prices;

    private ProviderPricingCatalog(List<ModelPrice> prices) {
        this.prices = List.copyOf(prices);
    }

    public static ProviderPricingCatalog load(Path workspaceRoot, ObjectMapper objectMapper) {
        return load(workspaceRoot, objectMapper, System::getenv);
    }

    public static ProviderPricingCatalog load(
            Path workspaceRoot,
            ObjectMapper objectMapper,
            Function<String, String> lookup
    ) {
        var configured = lookup.apply("DUAL_PROVIDER_PRICING_PATH");
        var path = configured == null || configured.isBlank()
                ? workspaceRoot.resolve(".agent").resolve("provider-pricing.json")
                : Path.of(configured);
        if (!Files.isRegularFile(path)) {
            return new ProviderPricingCatalog(List.of());
        }
        try {
            var doc = objectMapper.readValue(path.toFile(), MAP_TYPE);
            var out = new ArrayList<ModelPrice>();
            for (var item : mapList(doc.get("models"))) {
                var providerId = text(item.get("providerId"));
                var model = text(item.get("model"));
                if (providerId.isBlank() || model.isBlank()) {
                    continue;
                }
                out.add(new ModelPrice(
                        providerId,
                        model,
                        nonBlank(text(item.get("currency")), "USD"),
                        decimal(item.get("inputCacheHitPer1M")),
                        decimal(item.get("inputCacheMissPer1M")),
                        decimal(item.get("outputPer1M")),
                        decimal(item.get("cacheWritePer1M")),
                        text(item.get("sourceUrl"))
                ));
            }
            return new ProviderPricingCatalog(out);
        } catch (IOException ignored) {
            return new ProviderPricingCatalog(List.of());
        }
    }

    public Optional<ModelPrice> priceFor(String providerId, String model) {
        for (var price : prices) {
            if (price.matches(providerId, model)) {
                return Optional.of(price);
            }
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> mapList(Object value) {
        return value instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
    }

    private static String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static BigDecimal decimal(Object value) {
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

    public record ModelPrice(
            String providerId,
            String model,
            String currency,
            BigDecimal inputCacheHitPer1M,
            BigDecimal inputCacheMissPer1M,
            BigDecimal outputPer1M,
            BigDecimal cacheWritePer1M,
            String sourceUrl
    ) {
        public boolean matches(String candidateProvider, String candidateModel) {
            return providerId.equalsIgnoreCase(candidateProvider)
                    && (model.equals(candidateModel)
                    || (model.endsWith("*") && candidateModel.startsWith(model.substring(0, model.length() - 1))));
        }

        public BigDecimal cost(long cacheHit, long cacheMiss, long output, long cacheWrite) {
            return BigDecimal.ZERO
                    .add(perMillion(cacheHit, inputCacheHitPer1M))
                    .add(perMillion(cacheMiss, inputCacheMissPer1M))
                    .add(perMillion(output, outputPer1M))
                    .add(perMillion(cacheWrite, cacheWritePer1M))
                    .setScale(6, RoundingMode.HALF_UP);
        }

        private BigDecimal perMillion(long tokens, BigDecimal price) {
            if (tokens <= 0 || price == null || price.signum() == 0) {
                return BigDecimal.ZERO;
            }
            return price.multiply(BigDecimal.valueOf(tokens))
                    .divide(BigDecimal.valueOf(1_000_000L), 12, RoundingMode.HALF_UP);
        }
    }
}
