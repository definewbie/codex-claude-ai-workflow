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
        String text,
        AcpxStreamEvidence.TypedError streamError
) {
    static AgentLifecycleEvent stderrLine(long sequence, String text) {
        return textLine(sequence, Source.STDERR, text);
    }

    static AgentLifecycleEvent stdoutLine(long sequence, String text) {
        return textLine(sequence, Source.STDOUT, text);
    }

    static AgentLifecycleEvent sessionNewRequested(long sequence) {
        return streamEvent(sequence, Kind.SESSION_NEW_REQUESTED, null);
    }

    static AgentLifecycleEvent sessionNewCreated(long sequence) {
        return streamEvent(sequence, Kind.SESSION_NEW_CREATED, null);
    }

    static AgentLifecycleEvent promptAccepted(long sequence) {
        return streamEvent(sequence, Kind.PROMPT_ACCEPTED, null);
    }

    static AgentLifecycleEvent promptCompleted(long sequence) {
        return streamEvent(sequence, Kind.PROMPT_COMPLETED, null);
    }

    static AgentLifecycleEvent usageUpdate(long sequence) {
        return streamEvent(sequence, Kind.USAGE_UPDATE, null);
    }

    static AgentLifecycleEvent agentActivity(long sequence) {
        return streamEvent(sequence, Kind.AGENT_ACTIVITY, null);
    }

    static AgentLifecycleEvent sessionResumeFailed(long sequence, AcpxStreamEvidence.TypedError error) {
        return streamEvent(sequence, Kind.SESSION_RESUME_FAILED, error);
    }

    static AgentLifecycleEvent providerFailure(long sequence, AcpxStreamEvidence.TypedError error) {
        return streamEvent(sequence, Kind.PROVIDER_FAILURE, error);
    }

    private static AgentLifecycleEvent textLine(long sequence, Source source, String text) {
        return new AgentLifecycleEvent(sequence, source, classifyText(source, text), text == null ? "" : text, null);
    }

    private static AgentLifecycleEvent streamEvent(
            long sequence,
            Kind kind,
            AcpxStreamEvidence.TypedError error
    ) {
        return new AgentLifecycleEvent(sequence, Source.ACPX_STREAM, kind, "", error);
    }

    /** Wrapper stdout/stderr compatibility only; ACP stream lines are decoded structurally. */
    private static Kind classifyText(Source source, String text) {
        var lower = text == null ? "" : text.toLowerCase();
        var trimmed = lower.stripLeading();
        if (source == Source.STDERR && lower.contains("agent needs reconnect")) {
            return Kind.RECONNECT_SIGNAL;
        }
        if (source == Source.STDERR && (lower.contains("api error:")
                || lower.contains("failed to authenticate")
                || lower.contains("unable to connect to api"))) {
            return Kind.PROVIDER_FAILURE;
        }
        if ((source == Source.STDOUT || source == Source.STDERR) && (trimmed.startsWith("[error]")
                || trimmed.startsWith("runtime:")
                || trimmed.startsWith("no acpx session found")
                || trimmed.startsWith("tool permission request failed")
                || trimmed.startsWith("tool use aborted"))) {
            return Kind.PROVIDER_FAILURE;
        }
        if (sourceLikeWrapperActivity(lower)) {
            return Kind.AGENT_ACTIVITY;
        }
        return Kind.OTHER;
    }

    private static boolean sourceLikeWrapperActivity(String lower) {
        return lower.contains("[thinking]")
                || lower.contains("[assistant]")
                || lower.contains("[tool]");
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
        PROMPT_COMPLETED,
        TOOL_RESULT,
        USAGE_UPDATE,
        RECONNECT_SIGNAL,
        PROVIDER_FAILURE
    }
}
