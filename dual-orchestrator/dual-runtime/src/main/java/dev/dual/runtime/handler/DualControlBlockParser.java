package dev.dual.runtime.handler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

final class DualControlBlockParser {
    private static final Pattern FENCED_BLOCK = Pattern.compile(
            "```dual-control\\s*\\R(?<body>.*?)\\R```",
            Pattern.DOTALL
    );

    DualControlParseResult parse(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return DualControlParseResult.missing();
        }
        var matcher = FENCED_BLOCK.matcher(markdown);
        if (!matcher.find()) {
            return DualControlParseResult.missing();
        }
        var body = matcher.group("body");
        if (matcher.find()) {
            return DualControlParseResult.invalid(
                    "MULTIPLE_DUAL_CONTROL_BLOCKS",
                    "Expected exactly one dual-control block, but found more than one.",
                    Map.of()
            );
        }
        var values = new LinkedHashMap<String, String>();
        var malformedLines = new ArrayList<String>();
        var duplicateKeys = new ArrayList<String>();
        for (var line : body.split("\\R")) {
            var trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            var separator = trimmed.indexOf(':');
            if (separator <= 0) {
                malformedLines.add(trimmed);
                continue;
            }
            var key = trimmed.substring(0, separator).trim();
            var value = trimmed.substring(separator + 1).trim();
            if (key.isBlank()) {
                malformedLines.add(trimmed);
                continue;
            }
            if (values.containsKey(key)) {
                duplicateKeys.add(key);
                continue;
            }
            values.put(key, value);
        }
        if (!malformedLines.isEmpty()) {
            return DualControlParseResult.invalid(
                    "MALFORMED_DUAL_CONTROL_LINE",
                    "dual-control contains lines that are not key: value pairs.",
                    Map.of("malformed_lines", List.copyOf(malformedLines))
            );
        }
        if (!duplicateKeys.isEmpty()) {
            return DualControlParseResult.invalid(
                    "DUPLICATE_DUAL_CONTROL_KEY",
                    "dual-control contains duplicate keys.",
                    Map.of("duplicate_keys", List.copyOf(duplicateKeys))
            );
        }
        if (values.isEmpty()) {
            return DualControlParseResult.invalid(
                    "EMPTY_DUAL_CONTROL",
                    "dual-control block is empty.",
                    Map.of()
            );
        }
        return DualControlParseResult.valid(new DualControlBlock(values));
    }
}

record DualControlParseResult(
        DualControlBlock block,
        String errorCode,
        String message,
        Map<String, Object> details
) {
    DualControlParseResult {
        details = details == null ? Map.of() : Map.copyOf(details);
    }

    static DualControlParseResult valid(DualControlBlock block) {
        return new DualControlParseResult(block, "", "", Map.of());
    }

    static DualControlParseResult missing() {
        return new DualControlParseResult(null, "MISSING_DUAL_CONTROL", "No dual-control block found.", Map.of());
    }

    static DualControlParseResult invalid(String errorCode, String message, Map<String, Object> details) {
        return new DualControlParseResult(null, errorCode, message, details);
    }

    boolean valid() {
        return block != null;
    }

    boolean isMissing() {
        return block == null && "MISSING_DUAL_CONTROL".equals(errorCode);
    }
}
