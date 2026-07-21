package dev.dual.adapter.acpx;

import java.util.List;

/**
 * Normalized, typed evidence derived from an acpx session stream.
 *
 * <p>This is the second layer of the adapter boundary: it carries only real
 * top-level JSON-RPC errors (correlated to their request method) plus the
 * ordered session-ready and prompt-stop facts needed to arbitrate wrapper
 * diagnostics. It never carries agent prose, usage metadata, tool results, or
 * recovered {@code session/resume} errors. The
 * {@link #normalizedErrorText()} projection is the only stream content that
 * reaches provider-error matching.
 */
record AcpxStreamEvidence(
        List<TypedError> errors,
        boolean sessionReady,
        String promptStopReason,
        boolean pendingControlRequest
) {

    static final AcpxStreamEvidence EMPTY = new AcpxStreamEvidence(List.of(), false, "", false);

    AcpxStreamEvidence {
        errors = errors == null ? List.of() : List.copyOf(errors);
        promptStopReason = promptStopReason == null ? "" : promptStopReason;
    }

    AcpxStreamEvidence(List<TypedError> errors) {
        this(errors, false, "", false);
    }

    /**
     * One real JSON-RPC error: the method it answered (when correlatable via
     * request id), the error code, the top-level message, and the nested
     * {@code error.data.message} / {@code error.data.details} where the real
     * provider error (HTTP status / type) typically lives for Codex ACP.
     */
    record TypedError(String method, int code, String message, String dataMessage, String dataDetails) {
        TypedError {
            method = method == null ? "" : method;
            message = message == null ? "" : message;
            dataMessage = dataMessage == null ? "" : dataMessage;
            dataDetails = dataDetails == null ? "" : dataDetails;
        }
    }

    boolean isEmpty() {
        return errors.isEmpty();
    }

    boolean hasFacts() {
        return !errors.isEmpty() || sessionReady || !promptStopReason.isEmpty() || pendingControlRequest;
    }

    boolean provesRecoveredPromptCompletion() {
        return errors.isEmpty()
                && sessionReady
                && !pendingControlRequest
                && ("end_turn".equals(promptStopReason) || "completed".equals(promptStopReason));
    }

    /**
     * Joined typed error fields only: method/code context plus the top-level
     * message and the nested {@code data.message}/{@code data.details}, so the
     * catalog can classify the failure type from the real provider error
     * wherever it is nested — never from raw stream lines or agent prose.
     */
    String normalizedErrorText() {
        if (errors.isEmpty()) {
            return "";
        }
        var builder = new StringBuilder();
        for (var error : errors) {
            if (!error.method().isEmpty()) {
                builder.append(error.method()).append(' ');
            }
            builder.append("error ").append(error.code()).append(": ").append(error.message());
            if (!error.dataMessage().isEmpty()) {
                builder.append(" | data: ").append(error.dataMessage());
            }
            if (!error.dataDetails().isEmpty()) {
                builder.append(" | details: ").append(error.dataDetails());
            }
            builder.append('\n');
        }
        return builder.toString();
    }
}
