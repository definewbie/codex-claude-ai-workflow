package dev.dual.core.agentoutput;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** The single grammar owner for a dual-control block in agent markdown output. */
public final class DualControlProtocolParser {
    public static final String CONTROL_FENCE = "```dual-control";
    public static final String DONE_MARKER = "[done] end_turn";

    private static final Pattern CONTROL_OPEN = Pattern.compile("^```dual-control\\s*$");
    private static final Pattern FENCE_CLOSE = Pattern.compile("^```\\s*$");
    private static final Pattern OTHER_FENCE = Pattern.compile("^`{3,}.*$");

    public DualControlProtocolResult parse(String markdown) {
        var source = normalize(markdown);
        if (source.isBlank()) {
            return missing(source, List.of());
        }
        var lines = indexedLines(source);
        var candidates = findCandidates(source, lines);
        if (candidates.isEmpty()) {
            return missing(source, List.of());
        }

        var spans = candidates.stream().map(Candidate::span).toList();
        var terminal = candidates.get(candidates.size() - 1);
        var terminalMarker = terminal.closed()
                ? findTerminalMarker(lines, terminal.span().endExclusive())
                : null;
        if (!terminal.closed()) {
            return invalid(
                    source,
                    "UNCLOSED_DUAL_CONTROL",
                    "The terminal dual-control fence is not closed.",
                    Map.of(),
                    spans,
                    terminal.span(),
                    terminalMarker
            );
        }
        return parseBody(source, terminal, spans, terminalMarker);
    }

    private DualControlProtocolResult parseBody(
            String source,
            Candidate terminal,
            List<DualControlProtocolResult.SourceSpan> spans,
            DualControlProtocolResult.SourceSpan terminalMarker
    ) {
        var values = new LinkedHashMap<String, String>();
        var malformedLines = new ArrayList<String>();
        var duplicateKeys = new ArrayList<String>();
        var body = source.substring(terminal.bodyStart(), terminal.bodyEnd());
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
            } else if (values.containsKey(key)) {
                duplicateKeys.add(key);
            } else {
                values.put(key, value);
            }
        }
        if (!malformedLines.isEmpty()) {
            return invalid(source, "MALFORMED_DUAL_CONTROL_LINE",
                    "dual-control contains lines that are not key: value pairs.",
                    Map.of("malformed_lines", List.copyOf(malformedLines)), spans, terminal.span(), terminalMarker);
        }
        if (!duplicateKeys.isEmpty()) {
            return invalid(source, "DUPLICATE_DUAL_CONTROL_KEY",
                    "dual-control contains duplicate keys.",
                    Map.of("duplicate_keys", List.copyOf(duplicateKeys)), spans, terminal.span(), terminalMarker);
        }
        if (values.isEmpty()) {
            return invalid(source, "EMPTY_DUAL_CONTROL", "dual-control block is empty.",
                    Map.of(), spans, terminal.span(), terminalMarker);
        }
        return new DualControlProtocolResult(
                source,
                new DualControlProtocolResult.Block(values),
                "",
                "",
                Map.of("control_source", Map.of(
                        "block_selection", "LAST_TOP_LEVEL_DUAL_CONTROL",
                        "candidate_count", spans.size()
                )),
                spans,
                terminal.span(),
                terminalMarker
        );
    }

    private static List<Candidate> findCandidates(String source, List<Line> lines) {
        var candidates = new ArrayList<Candidate>();
        boolean insideOtherFence = false;
        String otherFenceMarker = "";
        for (int i = 0; i < lines.size(); i++) {
            var line = lines.get(i);
            if (insideOtherFence) {
                if (line.text().startsWith(otherFenceMarker)) {
                    insideOtherFence = false;
                    otherFenceMarker = "";
                }
                continue;
            }
            if (CONTROL_OPEN.matcher(line.text()).matches()) {
                var closeIndex = -1;
                for (int j = i + 1; j < lines.size(); j++) {
                    if (FENCE_CLOSE.matcher(lines.get(j).text()).matches()) {
                        closeIndex = j;
                        break;
                    }
                }
                if (closeIndex < 0) {
                    candidates.add(new Candidate(
                            new DualControlProtocolResult.SourceSpan(line.start(), source.length()),
                            line.endWithNewline(),
                            source.length(),
                            false
                    ));
                    break;
                }
                var close = lines.get(closeIndex);
                candidates.add(new Candidate(
                        new DualControlProtocolResult.SourceSpan(line.start(), close.end()),
                        line.endWithNewline(),
                        close.start(),
                        true
                ));
                i = closeIndex;
                continue;
            }
            if (isOrphanFenceBeforeControl(lines, i)) {
                continue;
            }
            if (OTHER_FENCE.matcher(line.text()).matches()) {
                insideOtherFence = true;
                otherFenceMarker = fenceMarker(line.text());
            }
        }
        return List.copyOf(candidates);
    }

    private static DualControlProtocolResult.SourceSpan findTerminalMarker(List<Line> lines, int after) {
        for (var line : lines) {
            if (line.start() >= after && DONE_MARKER.equals(line.text())) {
                return new DualControlProtocolResult.SourceSpan(line.start(), line.end());
            }
        }
        return null;
    }

    private static boolean isOrphanFenceBeforeControl(List<Line> lines, int index) {
        if (!FENCE_CLOSE.matcher(lines.get(index).text()).matches()) {
            return false;
        }
        for (int i = index + 1; i < lines.size(); i++) {
            if (!lines.get(i).text().isBlank()) {
                return CONTROL_OPEN.matcher(lines.get(i).text()).matches();
            }
        }
        return false;
    }

    private static String fenceMarker(String line) {
        var count = 0;
        while (count < line.length() && line.charAt(count) == '`') {
            count++;
        }
        return "`".repeat(Math.max(3, count));
    }

    private static List<Line> indexedLines(String source) {
        var result = new ArrayList<Line>();
        var start = 0;
        while (start <= source.length()) {
            var newline = source.indexOf('\n', start);
            if (newline < 0) {
                result.add(new Line(source.substring(start), start, source.length(), source.length()));
                break;
            }
            result.add(new Line(source.substring(start, newline), start, newline, newline + 1));
            start = newline + 1;
        }
        return List.copyOf(result);
    }

    private static String normalize(String markdown) {
        return markdown == null ? "" : markdown.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static DualControlProtocolResult missing(
            String source,
            List<DualControlProtocolResult.SourceSpan> spans
    ) {
        return new DualControlProtocolResult(source, null, "MISSING_DUAL_CONTROL",
                "No dual-control block found.", Map.of(), spans, null, null);
    }

    private static DualControlProtocolResult invalid(
            String source,
            String errorCode,
            String message,
            Map<String, Object> details,
            List<DualControlProtocolResult.SourceSpan> spans,
            DualControlProtocolResult.SourceSpan controlSpan,
            DualControlProtocolResult.SourceSpan terminalMarker
    ) {
        return new DualControlProtocolResult(source, null, errorCode, message, details,
                spans, controlSpan, terminalMarker);
    }

    private record Line(String text, int start, int end, int endWithNewline) {
    }

    private record Candidate(
            DualControlProtocolResult.SourceSpan span,
            int bodyStart,
            int bodyEnd,
            boolean closed
    ) {
    }
}
