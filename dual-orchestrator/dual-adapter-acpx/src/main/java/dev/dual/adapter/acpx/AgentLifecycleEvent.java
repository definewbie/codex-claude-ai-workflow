package dev.dual.adapter.acpx;

/**
 * One ordered runtime observation from an Agent execution.
 *
 * The sequence is the authoritative ordering because acpx stdout/stderr and
 * session stream lines do not provide stable timestamps. Wall-clock time is
 * useful for diagnostics, but execution health must be derived from consumed
 * event order plus the polling elapsed time.
 */
record AgentLifecycleEvent(
        long sequence,
        Source source,
        Kind kind,
        String text
) {
    static AgentLifecycleEvent stderrLine(long sequence, String text) {
        return line(sequence, Source.STDERR, text);
    }

    static AgentLifecycleEvent stdoutLine(long sequence, String text) {
        return line(sequence, Source.STDOUT, text);
    }

    static AgentLifecycleEvent streamLine(long sequence, String text) {
        return line(sequence, Source.ACPX_STREAM, text);
    }

    static AgentLifecycleEvent terminalContractSeen(long sequence) {
        return new AgentLifecycleEvent(sequence, Source.STDOUT, Kind.TERMINAL_CONTRACT_SEEN, "");
    }

    private static AgentLifecycleEvent line(long sequence, Source source, String text) {
        return new AgentLifecycleEvent(sequence, source, classify(text), text == null ? "" : text);
    }

    private static Kind classify(String text) {
        var lower = text == null ? "" : text.toLowerCase();
        if (lower.contains("session/new") && lower.contains("result")) {
            return Kind.SESSION_NEW_CREATED;
        }
        if (lower.contains("\"result\"") && lower.contains("sessionid")) {
            return Kind.SESSION_NEW_CREATED;
        }
        if (lower.contains("session/new")) {
            return Kind.SESSION_NEW_REQUESTED;
        }
        if (lower.contains("session/resume") && lower.contains("error")) {
            return Kind.SESSION_RESUME_FAILED;
        }
        if (lower.contains("resource not found")) {
            return Kind.SESSION_RESUME_FAILED;
        }
        if (lower.contains("session/prompt/failed")) {
            return Kind.PROVIDER_FAILURE;
        }
        if (lower.contains("session/prompt")) {
            return Kind.PROMPT_ACCEPTED;
        }
        if (lower.contains("usage_update")
                || lower.contains("usage/update")
                || lower.contains("\"usage\"")) {
            return Kind.USAGE_UPDATE;
        }
        if (lower.contains("tool_result")
                || lower.contains("tool result")
                || lower.contains("tool_use_result")
                || lower.contains("\"toolresult\"")) {
            return Kind.TOOL_RESULT;
        }
        if (lower.contains("agent needs reconnect")) {
            return Kind.RECONNECT_SIGNAL;
        }
        if (lower.contains("api error:")
                || lower.contains("failed to authenticate")
                || lower.contains("tool permission request failed")
                || lower.contains("tool use aborted")
                || lower.contains("tool_use_error")) {
            return Kind.PROVIDER_FAILURE;
        }
        if (sourceLikeActivity(lower)) {
            return Kind.AGENT_ACTIVITY;
        }
        return Kind.OTHER;
    }

    private static boolean sourceLikeActivity(String lower) {
        return lower.contains("[thinking]")
                || lower.contains("[assistant]")
                || lower.contains("agent_thought_chunk")
                || lower.contains("tool_call_update")
                || lower.contains("session/update")
                || lower.contains("available_commands_update");
    }

    enum Source {
        STDOUT,
        STDERR,
        ACPX_STREAM
    }

    enum Kind {
        OTHER,
        AGENT_ACTIVITY,
        SESSION_RESUME_FAILED,
        SESSION_NEW_REQUESTED,
        SESSION_NEW_CREATED,
        PROMPT_ACCEPTED,
        TOOL_RESULT,
        USAGE_UPDATE,
        RECONNECT_SIGNAL,
        PROVIDER_FAILURE,
        TERMINAL_CONTRACT_SEEN
    }
}
