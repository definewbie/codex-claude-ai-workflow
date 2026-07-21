package dev.dual.adapter.acpx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Ordered semantic projection of decoded acpx session-stream messages.
 *
 * <p>This class owns request-id correlation and session recovery ordering. It
 * never parses raw JSON and never applies workflow or retry policy.</p>
 */
final class AcpxSessionStreamProjector {
    private final Map<String, String> pendingRequests = new HashMap<>();
    private final List<AcpxStreamEvidence.TypedError> errors = new ArrayList<>();

    Optional<AgentLifecycleEvent> accept(long sequence, AcpxStreamWireLine wire) {
        return switch (wire.kind()) {
            case JSONRPC_REQUEST -> request(sequence, wire);
            case JSONRPC_OK -> success(sequence, wire);
            case JSONRPC_ERROR, TYPED_FAILURE_EVENT -> failure(sequence, wire);
            case NOTIFICATION -> notification(sequence, wire);
            case MALFORMED -> Optional.empty();
        };
    }

    AcpxStreamEvidence evidence() {
        return errors.isEmpty() ? AcpxStreamEvidence.EMPTY : new AcpxStreamEvidence(List.copyOf(errors));
    }

    private Optional<AgentLifecycleEvent> request(long sequence, AcpxStreamWireLine wire) {
        pendingRequests.put(wire.requestId(), wire.method());
        return switch (wire.method()) {
            case "session/new" -> Optional.of(AgentLifecycleEvent.sessionNewRequested(sequence));
            case "session/prompt" -> Optional.of(AgentLifecycleEvent.promptAccepted(sequence));
            default -> Optional.empty();
        };
    }

    private Optional<AgentLifecycleEvent> success(long sequence, AcpxStreamWireLine wire) {
        var method = completedMethodFor(wire);
        if ("session/new".equals(method)) {
            errors.removeIf(error -> "session/resume".equals(error.method()));
            return Optional.of(AgentLifecycleEvent.sessionNewCreated(sequence));
        }
        if (wire.result() != null && wire.result().usage() != null) {
            return Optional.of(AgentLifecycleEvent.usageUpdate(sequence));
        }
        return Optional.empty();
    }

    private Optional<AgentLifecycleEvent> failure(long sequence, AcpxStreamWireLine wire) {
        var method = completedMethodFor(wire);
        var error = wire.error();
        var typed = new AcpxStreamEvidence.TypedError(
                method,
                error == null || error.code() == null ? 0 : error.code(),
                error == null ? "" : error.message(),
                error == null ? "" : error.dataMessage(),
                error == null ? "" : error.dataDetails()
        );
        errors.add(typed);
        return Optional.of("session/resume".equals(method)
                ? AgentLifecycleEvent.sessionResumeFailed(sequence, typed)
                : AgentLifecycleEvent.providerFailure(sequence, typed));
    }

    private Optional<AgentLifecycleEvent> notification(long sequence, AcpxStreamWireLine wire) {
        if (!"session/update".equals(wire.method())) {
            return Optional.empty();
        }
        var updateKind = wire.notification() == null ? "" : wire.notification().updateKind();
        return Optional.of("usage_update".equals(updateKind)
                ? AgentLifecycleEvent.usageUpdate(sequence)
                : AgentLifecycleEvent.agentActivity(sequence));
    }

    private String completedMethodFor(AcpxStreamWireLine wire) {
        var correlated = pendingRequests.remove(wire.requestId());
        return correlated == null || correlated.isBlank() ? wire.method() : correlated;
    }
}
