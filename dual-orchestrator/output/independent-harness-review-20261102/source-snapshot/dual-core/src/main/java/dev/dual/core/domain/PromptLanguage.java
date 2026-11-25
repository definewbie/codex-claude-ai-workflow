package dev.dual.core.domain;

public record PromptLanguage(String value) {
    public PromptLanguage {
        value = normalize(value);
    }

    public static PromptLanguage of(String value) {
        return new PromptLanguage(value);
    }

    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "auto";
        }
        return switch (value.trim().toLowerCase()) {
            case "auto" -> "auto";
            case "en", "en-us", "english" -> "en";
            case "zh", "zh-cn", "chinese", "中文", "简体中文" -> "zh-CN";
            default -> throw new IllegalArgumentException(
                    "unsupported prompt language: " + value + ". Use auto, en, or zh-CN.");
        };
    }
}
