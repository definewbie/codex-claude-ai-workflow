package dev.dual.adapter.acpx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
    private boolean sessionReady;
    private String promptStopReason = "";
    private String currentPromptRequestId = "";
    private final StringBuilder ungroupedAssistantDeltas = new StringBuilder();
    private final Map<String, String> assistantMessages = new LinkedHashMap<>();
    private AcpxPromptCompletion promptCompletion = AcpxPromptCompletion.EMPTY;

    Optional<AgentLifecycleEvent> accept(long sequence, AcpxStreamWireLine wire) {
        return accept(sequence, wire, true);
    }

    Optional<AgentLifecycleEvent> accept(
            long sequence,
            AcpxStreamWireLine wire,
            boolean currentObservation
    ) {
        return switch (wire.kind()) {
            case JSONRPC_REQUEST -> request(sequence, wire, currentObservation);
            case JSONRPC_OK -> success(sequence, wire, currentObservation);
            case JSONRPC_ERROR, TYPED_FAILURE_EVENT -> failure(sequence, wire);
            case NOTIFICATION -> notification(sequence, wire, currentObservation);
            case MALFORMED -> Optional.empty();
        };
    }

    AcpxStreamEvidence evidence() {
        if (errors.isEmpty()
                && !sessionReady
                && promptStopReason.isEmpty()
                && !hasPendingControlRequest()) {
            return AcpxStreamEvidence.EMPTY;
        }
        return new AcpxStreamEvidence(
                List.copyOf(errors),
                sessionReady,
                promptStopReason,
                hasPendingControlRequest()
        );
    }

    AcpxPromptCompletion promptCompletion() {
        return promptCompletion;
    }

    private Optional<AgentLifecycleEvent> request(
            long sequence,
            AcpxStreamWireLine wire,
            boolean currentObservation
    ) {
        if ("session/prompt".equals(wire.method()) && !currentObservation) {
            return Optional.empty();
        }
        pendingRequests.put(wire.requestId(), wire.method());
        if (isControlRequest(wire.method())) {
            promptStopReason = "";
        }
        if ("session/prompt".equals(wire.method())) {
            startPrompt(wire.requestId());
        }
        if ("session/new".equals(wire.method()) || "session/resume".equals(wire.method())) {
            sessionReady = false;
        }
        return switch (wire.method()) {
            case "session/new" -> Optional.of(AgentLifecycleEvent.sessionNewRequested(sequence));
            case "session/prompt" -> Optional.of(AgentLifecycleEvent.promptAccepted(sequence));
            default -> Optional.empty();
        };
    }

    private Optional<AgentLifecycleEvent> success(
            long sequence,
            AcpxStreamWireLine wire,
            boolean currentObservation
    ) {
        var method = completedMethodFor(wire);
        if ("session/new".equals(method) || "session/resume".equals(method)) {
            sessionReady = true;
        }
        if ("session/new".equals(method)) {
            errors.removeIf(error -> "session/resume".equals(error.method()));
            return Optional.of(AgentLifecycleEvent.sessionNewCreated(sequence));
        }
        if (currentObservation
                && "session/prompt".equals(method)
                && !currentPromptRequestId.isBlank()
                && currentPromptRequestId.equals(wire.requestId())) {
            promptStopReason = wire.result() == null ? "" : wire.result().stopReason();
            var requestId = wire.requestId().isBlank() ? currentPromptRequestId : wire.requestId();
            promptCompletion = new AcpxPromptCompletion(
                    requestId,
                    promptStopReason,
                    finalAssistantMessage()
            );
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

    private Optional<AgentLifecycleEvent> notification(
            long sequence,
            AcpxStreamWireLine wire,
            boolean currentObservation
    ) {
        if (!"session/update".equals(wire.method())) {
            return Optional.empty();
        }
        var notification = wire.notification();
        var updateKind = notification == null ? "" : notification.updateKind();
        // A bounded tail may start after session/new. A structurally complete
        // typed session/update still proves that the named session was active;
        // malformed/bare notifications contribute no readiness fact.
        if (notification != null
                && !notification.sessionId().isBlank()
                && !updateKind.isBlank()) {
            sessionReady = true;
        }
        if (currentObservation
                && notification != null
                && "agent_message_chunk".equals(updateKind)
                && "text".equals(notification.contentType())) {
            appendAssistantMessage(notification.messageId(), notification.text());
        }
        return Optional.of("usage_update".equals(updateKind)
                ? AgentLifecycleEvent.usageUpdate(sequence)
                : AgentLifecycleEvent.agentActivity(sequence));
    }

    private void startPrompt(String requestId) {
        currentPromptRequestId = requestId == null ? "" : requestId;
        ungroupedAssistantDeltas.setLength(0);
        assistantMessages.clear();
        promptCompletion = AcpxPromptCompletion.EMPTY;
    }

    private void appendAssistantMessage(String messageId, String text) {
        var value = text == null ? "" : text;
        if (messageId == null || messageId.isBlank()) {
            ungroupedAssistantDeltas.append(value);
            return;
        }
        var current = assistantMessages.getOrDefault(messageId, "");
        if (value.equals(current) || (!current.isEmpty() && value.startsWith(current))) {
            assistantMessages.put(messageId, value);
            return;
        }
        assistantMessages.put(messageId, current + value);
    }

    private String finalAssistantMessage() {
        if (!assistantMessages.isEmpty()) {
            String latest = "";
            for (var value : assistantMessages.values()) {
                latest = value;
            }
            return latest;
        }
        return ungroupedAssistantDeltas.toString();
    }

    private String completedMethodFor(AcpxStreamWireLine wire) {
        var correlated = pendingRequests.remove(wire.requestId());
        if (correlated != null && !correlated.isBlank()) {
            return correlated;
        }
        if (wire.method() != null && !wire.method().isBlank()) {
            return wire.method();
        }
        if (wire.result() != null && !wire.result().sessionId().isBlank()) {
            return "session/new";
        }
        return "";
    }

    private boolean hasPendingControlRequest() {
        return pendingRequests.values().stream().anyMatch(this::isControlRequest);
    }

    private boolean isControlRequest(String method) {
        return "session/new".equals(method)
                || "session/resume".equals(method)
                || "session/prompt".equals(method);
    }
}
