package dev.dual.adapter.acpx;

/**
 * Typed terminal evidence for the native {@code claude --print --output-format json}
 * adapter protocol. The terminal source is native-result.json, distinct from the
 * session protocol's {@code dual-control} fence.
 *
 * <p>This records whether the external process terminated successfully at the native
 * protocol level: the result JSON is parseable, {@code is_error} is false, and the
 * stop reason is {@code end_turn} (or the subtype is {@code success}). A native
 * terminal success only means the adapter/process completed; it is NOT business
 * success. A missing typed artifact is still a contract failure, classified
 * separately from adapter failure.
 */
public record NativeClaudeTerminal(boolean parseable, boolean error, String stopReason, String subtype) {

    public boolean isNativeTerminalSuccess() {
        return parseable
                && !error
                && ("end_turn".equals(stopReason) || "success".equals(subtype));
    }
}
