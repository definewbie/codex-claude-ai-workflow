package dev.dual.core.agentoutput;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Typed syntax result for the dual-control markdown protocol. */
public record DualControlProtocolResult(
        String source,
        Block block,
        String errorCode,
        String message,
        Map<String, Object> details,
        List<SourceSpan> candidateSpans,
        SourceSpan controlSpan,
        SourceSpan terminalMarkerSpan
) {
    public DualControlProtocolResult {
        source = source == null ? "" : source;
        details = details == null ? Map.of() : Map.copyOf(details);
        candidateSpans = candidateSpans == null ? List.of() : List.copyOf(candidateSpans);
    }

    public boolean valid() {
        return block != null;
    }

    public boolean isMissing() {
        return block == null && "MISSING_DUAL_CONTROL".equals(errorCode);
    }

    public record Block(Map<String, String> values) {
        public Block {
            if (values == null || values.isEmpty()) {
                values = Map.of();
            } else {
                var copy = new LinkedHashMap<String, String>();
                values.forEach((key, value) -> {
                    if (key != null && !key.isBlank()) {
                        copy.put(key, value == null ? "" : value);
                    }
                });
                values = Collections.unmodifiableMap(copy);
            }
        }
    }

    public record SourceSpan(int start, int endExclusive) {
        public SourceSpan {
            if (start < 0 || endExclusive < start) {
                throw new IllegalArgumentException("invalid source span");
            }
        }
    }
}
