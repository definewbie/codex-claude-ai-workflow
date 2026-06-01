package dev.dual.runtime.handler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DualControlBlockParser {
    // (?m) makes ^ match line start; ^ anchors prevent matching inside blockquotes
    // or indented contexts where ``` is not at the beginning of a line.
    private static final Pattern FENCED_BLOCK = Pattern.compile(
            "(?m)^```dual-control\\s*\\R(?<body>.*?)\\R^```",
            Pattern.DOTALL
    );

    public DualControlParseResult parse(String markdown, NodeOutputContract contract) {
        var result = parse(markdown);
        if (!result.valid()) {
            return result;
        }
        try {
            contract.validate(result.block());
        } catch (IllegalArgumentException e) {
            return DualControlParseResult.invalid(
                    "CONTRACT_VIOLATION",
                    e.getMessage(),
                    Map.of("node_id", contract.nodeId())
            );
        }
        return result;
    }

    public DualControlParseResult parse(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return DualControlParseResult.missing();
        }

        // Find all dual-control blocks and their end positions
        var matcher = FENCED_BLOCK.matcher(markdown);
        List<MatchedBlock> blocks = new ArrayList<>();
        while (matcher.find()) {
            blocks.add(new MatchedBlock(matcher.group("body"), matcher.end()));
        }

        if (blocks.isEmpty()) {
            return DualControlParseResult.missing();
        }

        // Terminal control block rule: only the last block is the control block.
        // Earlier blocks are treated as prose/quotes (e.g., user input quoted by the agent).
        var terminal = blocks.get(blocks.size() - 1);

        // The terminal block must be at the end of the markdown (only whitespace after it).
        String afterTerminal = markdown.substring(terminal.endPosition()).trim();
        if (!isAllowedTrailingSentinel(afterTerminal)) {
            return DualControlParseResult.invalid(
                    "NON_TERMINAL_CONTROL_BLOCK",
                    "dual-control block must be the last content in the output, but non-whitespace content follows it.",
                    Map.of("trailing_length", afterTerminal.length())
            );
        }

        // Validate the terminal block's content
        return parseBody(terminal.body());
    }

    private boolean isAllowedTrailingSentinel(String trailing) {
        if (trailing.isEmpty()) {
            return true;
        }
        for (var line : trailing.split("\\R")) {
            var trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (!"[done] end_turn".equals(trimmed)) {
                return false;
            }
        }
        return true;
    }

    private DualControlParseResult parseBody(String body) {
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

    private record MatchedBlock(String body, int endPosition) {}
}
