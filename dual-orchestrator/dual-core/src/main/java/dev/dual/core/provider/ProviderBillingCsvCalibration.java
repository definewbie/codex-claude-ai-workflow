package dev.dual.core.provider;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ProviderBillingCsvCalibration {
    private ProviderBillingCsvCalibration() {
    }

    public static Report parse(String csv, ProviderPricingCatalog pricing) {
        var lines = csv == null ? List.<String>of() : csv.lines()
                .filter(line -> !line.isBlank())
                .toList();
        if (lines.size() < 2) {
            return new Report(List.of(), BigDecimal.ZERO, BigDecimal.ZERO, "empty");
        }
        var headers = splitCsv(lines.get(0)).stream()
                .map(ProviderBillingCsvCalibration::normalizeHeader)
                .toList();
        if (headers.contains("type") && headers.contains("price") && headers.contains("amount")) {
            return parseUsageAmountRows(lines, headers, pricing);
        }
        if (headers.contains("cost") && !headers.contains("type") && !hasTokenBreakdown(headers)) {
            return parseCostSummaryRows(lines, headers);
        }
        var rows = new ArrayList<Row>();
        var exportedTotal = BigDecimal.ZERO;
        var calculatedTotal = BigDecimal.ZERO;
        for (int i = 1; i < lines.size(); i++) {
            var values = splitCsv(lines.get(i));
            var item = new LinkedHashMap<String, String>();
            for (int j = 0; j < headers.size() && j < values.size(); j++) {
                item.put(headers.get(j), values.get(j).trim());
            }
            var providerId = text(item, "providerid");
            var model = text(item, "model");
            var currency = text(item, "currency");
            var exported = exportedCost(item);
            var price = pricing.priceFor(providerId, model).orElse(null);
            if (price == null) {
                rows.add(new Row(providerId, model, currency, exported, BigDecimal.ZERO, "pricing_unknown"));
                exportedTotal = exportedTotal.add(exported);
                continue;
            }
            var calculated = price.cost(
                    number(item.get("inputcachehittokens")),
                    number(item.get("inputcachemisstokens")),
                    number(item.get("outputtokens")),
                    number(item.get("cachewritetokens"))
            );
            rows.add(new Row(providerId, model, price.currency(), exported, calculated, consistency(exported, calculated)));
            exportedTotal = exportedTotal.add(exported);
            calculatedTotal = calculatedTotal.add(calculated);
        }
        return new Report(rows, exportedTotal.setScale(6, RoundingMode.HALF_UP),
                calculatedTotal.setScale(6, RoundingMode.HALF_UP),
                consistency(exportedTotal, calculatedTotal));
    }

    private static boolean hasTokenBreakdown(List<String> headers) {
        return headers.contains("inputcachehittokens")
                || headers.contains("inputcachemisstokens")
                || headers.contains("outputtokens")
                || headers.contains("cachewritetokens");
    }

    private static Report parseCostSummaryRows(List<String> lines, List<String> headers) {
        var rows = new ArrayList<Row>();
        var exportedTotal = BigDecimal.ZERO;
        for (int i = 1; i < lines.size(); i++) {
            var values = splitCsv(lines.get(i));
            var item = new LinkedHashMap<String, String>();
            for (int j = 0; j < headers.size() && j < values.size(); j++) {
                item.put(headers.get(j), values.get(j).trim());
            }
            var exported = decimal(item.get("cost"));
            rows.add(new Row("", text(item, "model"), text(item, "currency"), exported, BigDecimal.ZERO,
                    "cost_summary_only"));
            exportedTotal = exportedTotal.add(exported);
        }
        return new Report(rows, exportedTotal.setScale(6, RoundingMode.HALF_UP),
                BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP),
                rows.isEmpty() ? "empty" : "cost_summary_only");
    }

    private static Report parseUsageAmountRows(
            List<String> lines,
            List<String> headers,
            ProviderPricingCatalog pricing
    ) {
        var aggregates = new LinkedHashMap<String, UsageAggregate>();
        for (int i = 1; i < lines.size(); i++) {
            var values = splitCsv(lines.get(i));
            var item = new LinkedHashMap<String, String>();
            for (int j = 0; j < headers.size() && j < values.size(); j++) {
                item.put(headers.get(j), values.get(j).trim());
            }
            var model = text(item, "model");
            var type = text(item, "type");
            if (model.isBlank() || type.isBlank() || "request_count".equals(type)) {
                continue;
            }
            var key = text(item, "date") + "\t" + model + "\t" + text(item, "apikeyname") + "\t" + text(item, "apikey");
            aggregates.computeIfAbsent(key, ignored -> new UsageAggregate(model))
                    .add(type, number(item.get("amount")), decimal(item.get("price")));
        }

        var rows = new ArrayList<Row>();
        var exportedTotal = BigDecimal.ZERO;
        var calculatedTotal = BigDecimal.ZERO;
        for (var aggregate : aggregates.values()) {
            var price = selectPrice(pricing, aggregate);
            if (price == null) {
                var exported = aggregate.exportedCost();
                rows.add(new Row("", aggregate.model, "", exported, BigDecimal.ZERO, "pricing_unknown"));
                exportedTotal = exportedTotal.add(exported);
                continue;
            }
            var exported = aggregate.exportedCost();
            var calculated = price.cost(
                    aggregate.inputCacheHitTokens,
                    aggregate.inputCacheMissTokens,
                    aggregate.outputTokens,
                    aggregate.cacheWriteTokens
            );
            rows.add(new Row(price.providerId(), aggregate.model, price.currency(), exported, calculated,
                    consistency(exported, calculated)));
            exportedTotal = exportedTotal.add(exported);
            calculatedTotal = calculatedTotal.add(calculated);
        }
        return new Report(rows, exportedTotal.setScale(6, RoundingMode.HALF_UP),
                calculatedTotal.setScale(6, RoundingMode.HALF_UP),
                consistency(exportedTotal, calculatedTotal));
    }

    private static ProviderPricingCatalog.ModelPrice selectPrice(
            ProviderPricingCatalog pricing,
            UsageAggregate aggregate
    ) {
        var candidates = pricing.pricesForModel(aggregate.model);
        if (candidates.size() == 1) {
            return candidates.get(0);
        }
        return candidates.stream()
                .filter(price -> aggregate.matchesObservedPrices(price))
                .findFirst()
                .orElse(null);
    }

    private static String consistency(BigDecimal exported, BigDecimal calculated) {
        if (calculated.signum() <= 0) {
            return exported.signum() <= 0 ? "matches_official_pricing" : "pricing_unknown";
        }
        var drift = calculated.subtract(exported).abs()
                .divide(calculated.max(exported), 4, RoundingMode.HALF_UP);
        return drift.compareTo(new BigDecimal("0.15")) <= 0
                ? "matches_official_pricing"
                : "differs_from_official_pricing";
    }

    private static String text(Map<String, String> row, String key) {
        return row.getOrDefault(key, "").trim();
    }

    private static BigDecimal exportedCost(Map<String, String> row) {
        var amount = decimal(row.get("amount"));
        return amount.signum() != 0 ? amount : decimal(row.get("cost"));
    }

    private static long number(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return new BigDecimal(value.trim()).longValue();
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static BigDecimal decimal(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException ignored) {
            return BigDecimal.ZERO;
        }
    }

    private static String normalizeHeader(String value) {
        var normalized = value == null ? "" : value
                .trim()
                .replace("\uFEFF", "")
                .replace("_", "")
                .replace("-", "")
                .replace(" ", "")
                .toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "provider", "providerid", "providername" -> "providerid";
            case "model", "modelid", "modelname" -> "model";
            case "utcdate", "date", "day" -> "date";
            case "apikeyname" -> "apikeyname";
            case "apikey" -> "apikey";
            case "type", "tokentype", "usagetype" -> "type";
            case "price", "unitprice" -> "price";
            case "inputcachehittokens", "cachehittokens", "cachedreadtokens" -> "inputcachehittokens";
            case "inputcachemisstokens", "cachemisstokens", "inputtokens" -> "inputcachemisstokens";
            case "outputtokens", "completiontokens" -> "outputtokens";
            case "cachewritetokens", "cachedwritetokens" -> "cachewritetokens";
            case "cost", "fee" -> "cost";
            case "amount" -> "amount";
            case "currency" -> "currency";
            default -> normalized;
        };
    }

    private static List<String> splitCsv(String line) {
        var out = new ArrayList<String>();
        var current = new StringBuilder();
        var quoted = false;
        for (int i = 0; i < line.length(); i++) {
            var ch = line.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (ch == ',' && !quoted) {
                out.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        out.add(current.toString());
        return out;
    }

    public record Row(
            String providerId,
            String model,
            String currency,
            BigDecimal exportedCost,
            BigDecimal calculatedCost,
            String consistency
    ) {
    }

    public record Report(
            List<Row> rows,
            BigDecimal exportedTotal,
            BigDecimal calculatedTotal,
            String consistency
    ) {
        public Report {
            rows = List.copyOf(rows);
        }
    }

    private static final class UsageAggregate {
        private final String model;
        private long inputCacheHitTokens;
        private long inputCacheMissTokens;
        private long outputTokens;
        private long cacheWriteTokens;
        private BigDecimal inputCacheHitPrice = BigDecimal.ZERO;
        private BigDecimal inputCacheMissPrice = BigDecimal.ZERO;
        private BigDecimal outputPrice = BigDecimal.ZERO;
        private BigDecimal cacheWritePrice = BigDecimal.ZERO;

        private UsageAggregate(String model) {
            this.model = model;
        }

        private void add(String type, long amount, BigDecimal price) {
            switch (type) {
                case "input_cache_hit_tokens" -> {
                    inputCacheHitTokens += amount;
                    inputCacheHitPrice = nonZero(price, inputCacheHitPrice);
                }
                case "input_cache_miss_tokens" -> {
                    inputCacheMissTokens += amount;
                    inputCacheMissPrice = nonZero(price, inputCacheMissPrice);
                }
                case "output_tokens" -> {
                    outputTokens += amount;
                    outputPrice = nonZero(price, outputPrice);
                }
                case "cache_write_tokens" -> {
                    cacheWriteTokens += amount;
                    cacheWritePrice = nonZero(price, cacheWritePrice);
                }
                default -> {
                    // Unknown usage types do not participate in token cost calibration.
                }
            }
        }

        private BigDecimal exportedCost() {
            return BigDecimal.ZERO
                    .add(inputCacheHitPrice.multiply(BigDecimal.valueOf(inputCacheHitTokens)))
                    .add(inputCacheMissPrice.multiply(BigDecimal.valueOf(inputCacheMissTokens)))
                    .add(outputPrice.multiply(BigDecimal.valueOf(outputTokens)))
                    .add(cacheWritePrice.multiply(BigDecimal.valueOf(cacheWriteTokens)))
                    .setScale(6, RoundingMode.HALF_UP);
        }

        private boolean matchesObservedPrices(ProviderPricingCatalog.ModelPrice price) {
            return matches(inputCacheHitPrice, price.inputCacheHitPer1M())
                    && matches(inputCacheMissPrice, price.inputCacheMissPer1M())
                    && matches(outputPrice, price.outputPer1M())
                    && matches(cacheWritePrice, price.cacheWritePer1M());
        }

        private static boolean matches(BigDecimal observedPerToken, BigDecimal catalogPerMillion) {
            if (observedPerToken.signum() <= 0) {
                return true;
            }
            var expected = catalogPerMillion.divide(BigDecimal.valueOf(1_000_000L), 18, RoundingMode.HALF_UP);
            return observedPerToken.compareTo(expected.stripTrailingZeros()) == 0
                    || observedPerToken.setScale(18, RoundingMode.HALF_UP)
                    .compareTo(expected.setScale(18, RoundingMode.HALF_UP)) == 0;
        }

        private static BigDecimal nonZero(BigDecimal value, BigDecimal fallback) {
            return value.signum() > 0 ? value : fallback;
        }
    }
}
