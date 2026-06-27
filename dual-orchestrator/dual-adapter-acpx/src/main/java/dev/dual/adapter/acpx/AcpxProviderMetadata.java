package dev.dual.adapter.acpx;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * acpx session-level provider metadata.
 *
 * <p>This is adapter metadata, not workflow state. The workflow never routes on model names, but
 * usage/cost projections need this metadata to avoid downgrading valid token usage to
 * {@code pricing_unknown}.</p>
 */
record AcpxProviderMetadata(String providerId, String model, String billingMode, String source) {
    static final String SOURCE_ACPX_SESSION_JSON = "acpx_session_json";
    static final String BILLING_METERED = "metered";
    static final String BILLING_SUBSCRIPTION_TOKENS = "subscription_tokens";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern DEFAULT_MODEL_PATTERN = Pattern.compile(
            "(?i)currently\\s+([A-Za-z0-9._/-]+(?:\\[[^\\]]+\\])?)"
    );
    private static final List<ProviderRule> PROVIDER_RULES = List.of(
            new ProviderRule("glm-", "zhipu", BILLING_SUBSCRIPTION_TOKENS),
            new ProviderRule("deepseek", "deepseek", BILLING_METERED),
            new ProviderRule("gpt-", "openai", BILLING_METERED),
            new ProviderRule("chatgpt", "openai", BILLING_METERED)
    );

    AcpxProviderMetadata {
        providerId = providerId == null ? "" : providerId;
        model = model == null ? "" : model;
        billingMode = billingMode == null ? "" : billingMode;
        source = source == null ? "" : source;
    }

    static AcpxProviderMetadata empty() {
        return new AcpxProviderMetadata("", "", "", "");
    }

    static AcpxProviderMetadata read(Path sessionJson) {
        if (sessionJson == null || !Files.isRegularFile(sessionJson)) {
            return empty();
        }
        try {
            return fromJson(MAPPER.readTree(sessionJson.toFile()));
        } catch (IOException ignored) {
            return empty();
        }
    }

    static AcpxProviderMetadata fromJson(JsonNode root) {
        var model = currentModel(root)
                .or(() -> defaultModelFromDescription(root))
                .or(() -> firstKnownModelMention(root))
                .orElse("");
        if (model.isBlank()) {
            return empty();
        }
        return PROVIDER_RULES.stream()
                .filter(rule -> rule.matches(model))
                .findFirst()
                .map(rule -> new AcpxProviderMetadata(rule.providerId(), model, rule.billingMode(), SOURCE_ACPX_SESSION_JSON))
                .orElse(new AcpxProviderMetadata("", model, "", SOURCE_ACPX_SESSION_JSON));
    }

    boolean isEmpty() {
        return providerId.isBlank() && model.isBlank() && billingMode.isBlank();
    }

    private static Optional<String> currentModel(JsonNode root) {
        var raw = text(root == null ? null : root.get("current_model_id"));
        var normalized = normalizeModel(raw);
        if (normalized.isBlank() || "default".equalsIgnoreCase(normalized) || "auto".equalsIgnoreCase(normalized)) {
            return Optional.empty();
        }
        return Optional.of(normalized);
    }

    private static Optional<String> defaultModelFromDescription(JsonNode root) {
        for (var value : allText(root)) {
            var matcher = DEFAULT_MODEL_PATTERN.matcher(value);
            if (matcher.find()) {
                var model = normalizeModel(matcher.group(1));
                if (!model.isBlank()) {
                    return Optional.of(model);
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<String> firstKnownModelMention(JsonNode root) {
        for (var value : allText(root)) {
            var model = normalizeModel(value);
            if (PROVIDER_RULES.stream().anyMatch(rule -> rule.matches(model))) {
                return Optional.of(model);
            }
        }
        return Optional.empty();
    }

    private static List<String> allText(JsonNode root) {
        var out = new ArrayList<String>();
        collectText(root, out);
        return out;
    }

    private static void collectText(JsonNode node, List<String> out) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        if (node.isTextual()) {
            out.add(node.asText());
            return;
        }
        if (node.isContainerNode()) {
            node.elements().forEachRemaining(child -> collectText(child, out));
        }
    }

    private static String normalizeModel(String raw) {
        if (raw == null) {
            return "";
        }
        var value = raw.trim();
        var bracket = value.indexOf('[');
        if (bracket >= 0) {
            value = value.substring(0, bracket);
        }
        return value.replaceAll("[,.;:]+$", "").trim();
    }

    private static String text(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? "" : node.asText();
    }

    private record ProviderRule(String modelPrefix, String providerId, String billingMode) {
        boolean matches(String model) {
            return model != null && model.toLowerCase(Locale.ROOT).startsWith(modelPrefix);
        }
    }
}
