package dev.dual.runtime.handler;

import java.util.Map;

public record DualControlParseResult(
        DualControlBlock block,
        String errorCode,
        String message,
        Map<String, Object> details
) {
    public DualControlParseResult {
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

    public boolean valid() {
        return block != null;
    }

    public boolean isMissing() {
        return block == null && "MISSING_DUAL_CONTROL".equals(errorCode);
    }
}
