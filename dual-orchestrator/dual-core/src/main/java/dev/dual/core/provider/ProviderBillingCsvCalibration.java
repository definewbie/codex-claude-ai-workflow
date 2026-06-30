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
            var exported = decimal(item.get("amount"));
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
            case "inputcachehittokens", "cachehittokens", "cachedreadtokens" -> "inputcachehittokens";
            case "inputcachemisstokens", "cachemisstokens", "inputtokens" -> "inputcachemisstokens";
            case "outputtokens", "completiontokens" -> "outputtokens";
            case "cachewritetokens", "cachedwritetokens" -> "cachewritetokens";
            case "cost", "amount", "fee" -> "amount";
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
}
