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
        AcpxStreamEvidence.TypedError streamError,
        PermissionEvidence permissionEvidence
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

    static AgentLifecycleEvent permissionRequested(long sequence, PermissionEvidence evidence) {
        return permissionEvent(sequence, Kind.PERMISSION_REQUESTED, evidence);
    }

    static AgentLifecycleEvent permissionApproved(long sequence, PermissionEvidence evidence) {
        return permissionEvent(sequence, Kind.PERMISSION_APPROVED, evidence);
    }

    static AgentLifecycleEvent permissionDenied(long sequence, PermissionEvidence evidence) {
        return permissionEvent(sequence, Kind.PERMISSION_DENIED, evidence);
    }

    static AgentLifecycleEvent permissionCancelled(long sequence, PermissionEvidence evidence) {
        return permissionEvent(sequence, Kind.PERMISSION_CANCELLED, evidence);
    }

    private static AgentLifecycleEvent textLine(long sequence, Source source, String text) {
        return new AgentLifecycleEvent(sequence, source, classifyText(source, text), text == null ? "" : text, null, null);
    }

    private static AgentLifecycleEvent streamEvent(
            long sequence,
            Kind kind,
            AcpxStreamEvidence.TypedError error
    ) {
        return streamEvent(sequence, kind, "", error);
    }

    private static AgentLifecycleEvent streamEvent(
            long sequence,
            Kind kind,
            String text,
            AcpxStreamEvidence.TypedError error
    ) {
        return new AgentLifecycleEvent(sequence, Source.ACPX_STREAM, kind, text == null ? "" : text, error, null);
    }

    private static AgentLifecycleEvent permissionEvent(long sequence, Kind kind, PermissionEvidence evidence) {
        var normalized = evidence == null ? PermissionEvidence.EMPTY : evidence;
        return new AgentLifecycleEvent(
                sequence,
                Source.ACPX_STREAM,
                kind,
                normalized.summary(),
                null,
                normalized
        );
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
                || trimmed.startsWith("no acpx session found"))) {
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

    record PermissionEvidence(
            String toolCallId,
            String toolKind,
            String toolName,
            String title,
            String resource,
            java.util.List<String> requestedOptions,
            String disposition
    ) {
        static final PermissionEvidence EMPTY = new PermissionEvidence(
                "", "", "", "", "", java.util.List.of(), "UNAVAILABLE");

        PermissionEvidence {
            toolCallId = toolCallId == null ? "" : toolCallId;
            toolKind = toolKind == null ? "" : toolKind;
            toolName = toolName == null ? "" : toolName;
            title = title == null ? "" : title;
            resource = resource == null ? "" : resource;
            requestedOptions = requestedOptions == null ? java.util.List.of() : java.util.List.copyOf(requestedOptions);
            disposition = disposition == null ? "UNAVAILABLE" : disposition;
        }

        PermissionEvidence withDisposition(String value) {
            return new PermissionEvidence(toolCallId, toolKind, toolName, title, resource, requestedOptions, value);
        }

        String summary() {
            if (!title.isBlank() && !resource.isBlank()) {
                return title + ": " + resource;
            }
            return !resource.isBlank() ? resource : (!title.isBlank() ? title : "permission request");
        }
    }

    enum Kind {
        OTHER,
        AGENT_ACTIVITY,
        SESSION_RESUME_FAILED,
        SESSION_NEW_REQUESTED,
        SESSION_NEW_CREATED,
        PROMPT_ACCEPTED,
        PROMPT_COMPLETED,
        PERMISSION_REQUESTED,
        PERMISSION_APPROVED,
        PERMISSION_DENIED,
        PERMISSION_CANCELLED,
        TOOL_RESULT,
        USAGE_UPDATE,
        RECONNECT_SIGNAL,
        PROVIDER_FAILURE
    }
}
