package dev.dual.runtime.handler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DualControlBlockParser {
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

        var blocks = topLevelControlBlocks(markdown);

        if (blocks.isEmpty()) {
            return DualControlParseResult.missing();
        }

        // Status protocol rule: only the last top-level block is the control block.
        // Earlier blocks are treated as prose/quotes (e.g., user input quoted by the agent).
        var terminal = blocks.get(blocks.size() - 1);

        return parseBody(terminal.body(), blocks.size());
    }

    private static List<MatchedBlock> topLevelControlBlocks(String markdown) {
        var lines = markdown.lines().toList();
        var blocks = new ArrayList<MatchedBlock>();
        boolean insideOtherFence = false;
        String otherFenceMarker = "";
        for (int i = 0; i < lines.size(); i++) {
            var line = lines.get(i);
            if (insideOtherFence) {
                if (line.startsWith(otherFenceMarker)) {
                    insideOtherFence = false;
                    otherFenceMarker = "";
                }
                continue;
            }
            if (isTopLevelControlFence(line)) {
                var body = new StringBuilder();
                for (int j = i + 1; j < lines.size(); j++) {
                    var candidate = lines.get(j);
                    if (candidate.startsWith("```")) {
                        blocks.add(new MatchedBlock(body.toString().stripTrailing()));
                        i = j;
                        break;
                    }
                    body.append(candidate).append("\n");
                    if (j == lines.size() - 1) {
                        i = j;
                    }
                }
                continue;
            }
            if (isTopLevelNonControlFence(line)) {
                insideOtherFence = true;
                otherFenceMarker = fenceMarker(line);
            }
        }
        return List.copyOf(blocks);
    }

    private static boolean isTopLevelControlFence(String line) {
        return line != null && line.matches("^```dual-control\\s*$");
    }

    private static boolean isTopLevelNonControlFence(String line) {
        return line != null
                && line.matches("^`{3,}.*$")
                && !isTopLevelControlFence(line);
    }

    private static String fenceMarker(String line) {
        var count = 0;
        while (count < line.length() && line.charAt(count) == '`') {
            count++;
        }
        return "`".repeat(Math.max(3, count));
    }

    private DualControlParseResult parseBody(String body, int blockCount) {
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
        return DualControlParseResult.valid(new DualControlBlock(values), Map.of(
                "control_source", Map.of(
                        "block_selection", "LAST_TOP_LEVEL_DUAL_CONTROL",
                        "candidate_count", blockCount
                )
        ));
    }

    private record MatchedBlock(String body) {}
}
