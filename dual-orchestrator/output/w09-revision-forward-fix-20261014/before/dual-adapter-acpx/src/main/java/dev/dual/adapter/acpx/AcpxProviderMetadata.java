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
import java.util.function.Function;
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
    static final String SOURCE_ENV = "env";
    static final String SOURCE_CLAUDE_SETTINGS_ENV = "claude_settings_env";
    static final String BILLING_METERED = "metered";
    static final String BILLING_SUBSCRIPTION_TOKENS = "subscription_token_only";

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

    static AcpxProviderMetadata runtimeDefault() {
        var fromEnv = fromEnvironment(System::getenv, SOURCE_ENV);
        if (!fromEnv.isEmpty()) {
            return fromEnv;
        }
        var settingsPath = Path.of(
                firstNonBlank(System.getenv("CLAUDE_CONFIG_DIR"), Path.of(System.getProperty("user.home"), ".claude").toString()),
                "settings.json"
        );
        return fromClaudeSettings(settingsPath);
    }

    static AcpxProviderMetadata fromClaudeSettings(Path settingsJson) {
        if (settingsJson == null || !Files.isRegularFile(settingsJson)) {
            return empty();
        }
        try {
            var envNode = MAPPER.readTree(settingsJson.toFile()).get("env");
            return fromEnvironment(name -> text(envNode == null ? null : envNode.get(name)), SOURCE_CLAUDE_SETTINGS_ENV);
        } catch (IOException ignored) {
            return empty();
        }
    }

    static AcpxProviderMetadata fromEnvironment(Function<String, String> env, String source) {
        if (env == null) {
            return empty();
        }
        var explicitProvider = normalizeProvider(env.apply("DUAL_CLAUDE_PROVIDER"));
        var model = firstNonBlank(
                env.apply("DUAL_CLAUDE_MODEL"),
                env.apply("ANTHROPIC_MODEL"),
                env.apply("CLAUDE_CODE_MODEL"),
                env.apply("ANTHROPIC_DEFAULT_SONNET_MODEL"),
                env.apply("ANTHROPIC_DEFAULT_OPUS_MODEL"),
                env.apply("ANTHROPIC_DEFAULT_HAIKU_MODEL")
        );
        var providerFromModel = providerForModel(model);
        var providerFromBaseUrl = providerForBaseUrl(firstNonBlank(
                env.apply("DUAL_CLAUDE_BASE_URL"),
                env.apply("ANTHROPIC_BASE_URL")
        ));
        var provider = firstNonBlank(explicitProvider, providerFromModel, providerFromBaseUrl);
        var normalizedModel = normalizeModel(model);
        if (provider.isBlank() && normalizedModel.isBlank()) {
            return empty();
        }
        var billing = firstNonBlank(
                env.apply("DUAL_CLAUDE_BILLING_MODE"),
                billingForProvider(provider),
                billingForModel(normalizedModel)
        );
        return new AcpxProviderMetadata(provider, normalizedModel, billing, source);
    }

    static AcpxProviderMetadata fromJson(JsonNode root) {
        var model = configuredModel(root)
                .or(() -> currentModel(root))
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
        var raw = firstFieldText(root, "current_model_id");
        var normalized = normalizeModel(raw);
        if (normalized.isBlank() || "default".equalsIgnoreCase(normalized) || "auto".equalsIgnoreCase(normalized)) {
            return Optional.empty();
        }
        return Optional.of(normalized);
    }

    private static Optional<String> configuredModel(JsonNode root) {
        var options = root == null ? null : root.at("/acpx/config_options");
        if (options == null || !options.isArray()) {
            return Optional.empty();
        }
        for (var config : options) {
            if (!"model".equals(text(config.get("id")))) {
                continue;
            }
            var currentValue = text(config.get("currentValue"));
            var selected = selectedOption(config.get("options"), currentValue);
            if (selected.isEmpty()) {
                return Optional.empty();
            }
            var descriptionModel = modelFromDescription(text(selected.get().get("description")));
            if (descriptionModel.isPresent()) {
                return descriptionModel;
            }
            var name = normalizeModel(text(selected.get().get("name")));
            if (!name.isBlank() && PROVIDER_RULES.stream().anyMatch(rule -> rule.matches(name))) {
                return Optional.of(name);
            }
        }
        return Optional.empty();
    }

    private static Optional<JsonNode> selectedOption(JsonNode options, String currentValue) {
        if (options == null || !options.isArray()) {
            return Optional.empty();
        }
        for (var option : options) {
            if (text(option.get("value")).equals(currentValue)) {
                return Optional.of(option);
            }
        }
        return Optional.empty();
    }

    private static Optional<String> defaultModelFromDescription(JsonNode root) {
        for (var value : allText(root)) {
            var model = modelFromDescription(value);
            if (model.isPresent()) {
                return model;
            }
        }
        return Optional.empty();
    }

    private static Optional<String> modelFromDescription(String value) {
        var matcher = DEFAULT_MODEL_PATTERN.matcher(value == null ? "" : value);
        if (!matcher.find()) {
            return Optional.empty();
        }
        var model = normalizeModel(matcher.group(1));
        return model.isBlank() ? Optional.empty() : Optional.of(model);
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

    private static String normalizeProvider(String raw) {
        if (raw == null) {
            return "";
        }
        var value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.equals("glm") || value.equals("zhipuai") || value.equals("bigmodel")) {
            return "zhipu";
        }
        return value;
    }

    private static String providerForModel(String model) {
        var normalized = normalizeModel(model);
        return PROVIDER_RULES.stream()
                .filter(rule -> rule.matches(normalized))
                .findFirst()
                .map(ProviderRule::providerId)
                .orElse("");
    }

    private static String providerForBaseUrl(String baseUrl) {
        if (baseUrl == null) {
            return "";
        }
        var value = baseUrl.toLowerCase(Locale.ROOT);
        if (value.contains("bigmodel") || value.contains("zhipu") || value.contains("glm")) {
            return "zhipu";
        }
        if (value.contains("deepseek")) {
            return "deepseek";
        }
        if (value.contains("openai")) {
            return "openai";
        }
        if (value.contains("anthropic")) {
            return "anthropic";
        }
        return "";
    }

    private static String billingForProvider(String provider) {
        return switch (provider == null ? "" : provider) {
            case "zhipu" -> BILLING_SUBSCRIPTION_TOKENS;
            case "deepseek", "openai", "anthropic" -> BILLING_METERED;
            default -> "";
        };
    }

    private static String billingForModel(String model) {
        return PROVIDER_RULES.stream()
                .filter(rule -> rule.matches(model))
                .findFirst()
                .map(ProviderRule::billingMode)
                .orElse("");
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (var value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private static String text(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? "" : node.asText();
    }

    private static String firstFieldText(JsonNode node, String fieldName) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        if (node.isObject()) {
            var direct = node.get(fieldName);
            if (direct != null && !direct.isNull()) {
                return direct.asText();
            }
            var fields = node.fields();
            while (fields.hasNext()) {
                var value = firstFieldText(fields.next().getValue(), fieldName);
                if (!value.isBlank()) {
                    return value;
                }
            }
        } else if (node.isArray()) {
            for (var child : node) {
                var value = firstFieldText(child, fieldName);
                if (!value.isBlank()) {
                    return value;
                }
            }
        }
        return "";
    }

    private record ProviderRule(String modelPrefix, String providerId, String billingMode) {
        boolean matches(String model) {
            return model != null && model.toLowerCase(Locale.ROOT).startsWith(modelPrefix);
        }
    }
}
