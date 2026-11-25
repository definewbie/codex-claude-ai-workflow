package dev.dual.adapter.acpx;

/**
 * Typed completion fact for one ACP {@code session/prompt} occurrence.
 *
 * <p>The stop reason is execution evidence. The final assistant message is an
 * untrusted candidate workflow result until the runtime validates its strict
 * {@code AgentResultEnvelope} contract.</p>
 */
public record AcpxPromptCompletion(
        String requestId,
        String stopReason,
        String finalAssistantMessage
) {
    public static final AcpxPromptCompletion EMPTY = new AcpxPromptCompletion("", "", "");

    public AcpxPromptCompletion {
        requestId = requestId == null ? "" : requestId;
        stopReason = stopReason == null ? "" : stopReason;
        finalAssistantMessage = finalAssistantMessage == null ? "" : finalAssistantMessage;
    }

    public boolean completed() {
        return ("end_turn".equals(stopReason) || "completed".equals(stopReason))
                && !finalAssistantMessage.isBlank();
    }
}
