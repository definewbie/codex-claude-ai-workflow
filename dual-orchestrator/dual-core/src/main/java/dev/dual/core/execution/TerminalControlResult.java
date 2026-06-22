package dev.dual.core.execution;

/**
 * Parsed terminal control block from agent output.
 *
 * <p>A valid terminal control block is necessary but not sufficient for success.
 * Required structured artifacts must also pass validation.</p>
 */
public record TerminalControlResult(
        boolean present,
        boolean valid,
        String decision,
        String summary,
        String parseError
) {
    public TerminalControlResult {
        decision = decision == null ? "" : decision;
        summary = summary == null ? "" : summary;
        parseError = parseError == null ? "" : parseError;
        if (valid && !present) {
            throw new IllegalArgumentException("valid terminal control requires present=true");
        }
    }

    public static TerminalControlResult valid(String decision) {
        return new TerminalControlResult(true, true, decision, "", "");
    }

    public static TerminalControlResult missing() {
        return new TerminalControlResult(false, false, "", "", "terminal control block is missing");
    }

    public static TerminalControlResult invalid(String parseError) {
        return new TerminalControlResult(true, false, "", "", parseError);
    }
}
