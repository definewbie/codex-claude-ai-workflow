package dev.dual.runtime.handler;

import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.regex.Pattern;

final class DualControlBlockParser {
    private static final Pattern FENCED_BLOCK = Pattern.compile(
            "```dual-control\\s*\\R(?<body>.*?)\\R```",
            Pattern.DOTALL
    );

    Optional<DualControlBlock> parse(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return Optional.empty();
        }
        var matcher = FENCED_BLOCK.matcher(markdown);
        if (!matcher.find()) {
            return Optional.empty();
        }
        var values = new LinkedHashMap<String, String>();
        for (var line : matcher.group("body").split("\\R")) {
            var trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            var separator = trimmed.indexOf(':');
            if (separator <= 0) {
                continue;
            }
            var key = trimmed.substring(0, separator).trim();
            var value = trimmed.substring(separator + 1).trim();
            if (!key.isBlank()) {
                values.put(key, value);
            }
        }
        return Optional.of(new DualControlBlock(values));
    }
}
