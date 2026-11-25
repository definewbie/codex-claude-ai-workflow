package dev.dual.adapter.acpx;

import java.util.ArrayList;
import java.util.ArrayDeque;
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
    private final Map<String, ArrayDeque<String>> pendingRequests = new HashMap<>();
    private final List<AcpxStreamEvidence.TypedError> errors = new ArrayList<>();
    private boolean sessionReady;
    private String promptStopReason = "";
    private String currentPromptRequestId = "";
    private final StringBuilder ungroupedAssistantDeltas = new StringBuilder();
    private final Map<String, String> assistantMessages = new LinkedHashMap<>();
    private AcpxPromptCompletion promptCompletion = AcpxPromptCompletion.EMPTY;
    private final Map<String, AgentLifecycleEvent.PermissionEvidence> permissionDetails = new HashMap<>();
    private final Map<String, String> toolNamesByCallId = new HashMap<>();
    private String currentSession = "";
    private boolean promptActive;
    private final Map<String, dev.dual.core.execution.AdapterSignal.OperationRef> permissionOperations = new HashMap<>();
    private final Map<String, Map<String,String>> permissionOptionKinds = new HashMap<>();
    private final java.util.Set<String> seenPermissionIds = new java.util.HashSet<>();
    private final java.util.Set<String> invalidPermissionIds = new java.util.HashSet<>();
    private final AcpxToolObservations toolObservations = new AcpxToolObservations();

    List<dev.dual.core.execution.AgentExecutionEvidence.ToolObservation> toolObservations() {
        return toolObservations.values();
    }

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
        if (("session/prompt".equals(wire.method()) || "session/request_permission".equals(wire.method()))
                && !currentObservation) {
            return Optional.empty();
        }
        pendingRequests.computeIfAbsent(wire.requestId(), ignored -> new ArrayDeque<>())
                .addLast(wire.method());
        if (isControlRequest(wire.method())) {
            promptStopReason = "";
        }
        if ("session/prompt".equals(wire.method())) {
            startPrompt(wire.requestId());
            currentSession = wire.request().sessionId(); promptActive = currentSession != null && !currentSession.isBlank();
            permissionDetails.clear(); permissionOperations.clear(); permissionOptionKinds.clear();
            toolNamesByCallId.clear(); toolObservations.start(currentSession, currentPromptRequestId);
        }
        if ("session/new".equals(wire.method()) || "session/resume".equals(wire.method())) {
            sessionReady = false;
        }
        if ("session/request_permission".equals(wire.method())) {
            var request = wire.request();
            if (!seenPermissionIds.add(wire.requestId())) invalidPermissionIds.add(wire.requestId());
            var evidence = permissionEvidence(request);
            permissionDetails.put(wire.requestId(), evidence);
            // A current but uncorrelated request still produces a blocking permission fact.
            // It cannot acquire an operation identity eligible for nonessential assessment.
            if (!promptActive || !currentSession.equals(request.sessionId()))
                return Optional.of(AgentLifecycleEvent.permissionRequested(sequence, evidence));
            permissionOptionKinds.put(wire.requestId(), request.permissionOptionKinds());
            permissionOperations.put(wire.requestId(), new dev.dual.core.execution.AdapterSignal.OperationRef(
                    currentSession, currentPromptRequestId, wire.requestId(), request.permissionToolCallId(), sequence, -1,
                    request.permissionToolKind(), dev.dual.core.execution.AdapterSignal.parametersHash(request.permissionRawInput()),
                    request.permissionRequestJson(), dev.dual.core.execution.AdapterSignal.digest(request.permissionRequestJson()), "", ""));
            return Optional.of(AgentLifecycleEvent.permissionRequested(sequence, evidence));
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
                    finalAssistantMessage(),
                    currentSession
            );
            toolObservations.finish(); promptActive = false;
            return Optional.of(AgentLifecycleEvent.promptCompleted(sequence));
        }
        if (currentObservation && "session/request_permission".equals(method)) {
            var evidence = permissionDetails.remove(wire.requestId());
            if (evidence == null) {
                return Optional.empty();
            }
            var operation = permissionOperations.remove(wire.requestId());
            var options = permissionOptionKinds.remove(wire.requestId());
            var outcome = wire.result() == null ? "" : wire.result().permissionOutcome();
            var optionId = wire.result() == null ? "" : wire.result().permissionOptionId();
            var optionKind = options == null ? "" : options.getOrDefault(optionId, "");
            boolean current = promptActive && operation != null && currentSession.equals(operation.sessionId())
                    && !invalidPermissionIds.contains(wire.requestId());
            if (current && "selected".equals(outcome) && ("reject_once".equals(optionKind) || "reject_always".equals(optionKind))) {
                var response = wire.result().responseJson();
                operation = new dev.dual.core.execution.AdapterSignal.OperationRef(operation.sessionId(), operation.promptId(),
                        operation.requestId(), operation.toolCallId(), operation.requestSequence(), sequence, operation.kind(),
                        operation.parametersSha256(), operation.requestJson(), operation.requestSha256(), response,
                        dev.dual.core.execution.AdapterSignal.digest(response));
                return Optional.of(AgentLifecycleEvent.permissionDenied(sequence, evidence.withDisposition("DENIED").withOperation(operation)));
            }
            if (current && "selected".equals(outcome) && ("allow_once".equals(optionKind) || "allow_always".equals(optionKind)))
                return Optional.of(AgentLifecycleEvent.permissionApproved(sequence, evidence.withDisposition("APPROVED")));
            if (current && "cancelled".equals(outcome))
                return Optional.of(AgentLifecycleEvent.permissionCancelled(sequence, evidence.withDisposition("CANCELLED")));
            return Optional.of(AgentLifecycleEvent.permissionUnresolved(sequence, evidence.withDisposition("UNKNOWN")));
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
        if (currentObservation && notification != null) toolObservations.accept(notification);
        var updateKind = notification == null ? "" : notification.updateKind();
        if (notification != null
                && !notification.toolCallId().isBlank()
                && !notification.toolName().isBlank()) {
            toolNamesByCallId.put(notification.toolCallId(), notification.toolName());
        }
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
        var requests = pendingRequests.get(wire.requestId());
        var correlated = requests == null ? null : requests.pollLast();
        if (requests != null && requests.isEmpty()) {
            pendingRequests.remove(wire.requestId());
        }
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
        return pendingRequests.values().stream()
                .flatMap(java.util.Collection::stream)
                .anyMatch(this::isControlRequest);
    }

    private boolean isControlRequest(String method) {
        return "session/new".equals(method)
                || "session/resume".equals(method)
                || "session/prompt".equals(method);
    }

    private AgentLifecycleEvent.PermissionEvidence permissionEvidence(AcpxStreamWireLine.Request request) {
        if (request == null) {
            return AgentLifecycleEvent.PermissionEvidence.EMPTY;
        }
        return new AgentLifecycleEvent.PermissionEvidence(
                crop(request.permissionToolCallId(), 160),
                crop(request.permissionToolKind(), 80),
                crop(toolNamesByCallId.get(request.permissionToolCallId()), 80),
                crop(request.permissionTitle(), 240),
                crop(request.permissionCommand(), 512),
                request.permissionOptions(),
                "REQUESTED"
        );
    }

    private String crop(String value, int maxLength) {
        var normalized = value == null ? "" : value.replaceAll("\\s+", " ").strip();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength) + "...";
    }
}
