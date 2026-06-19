package dev.dual.adapter.acpx;

import java.util.Optional;

/**
 * Extracts the business artifact from an ACP stdout transcript.
 *
 * acpx stdout is an execution transcript: client events, tool calls, tool
 * inputs/outputs, and the final agent answer are interleaved in one stream.
 * Only the final structured artifact/control block belongs in workflow
 * outputRefs. The raw transcript remains execution evidence and must not be
 * fed back into later context packages.
 */
final class AgentFinalArtifactExtractor {
    private static final String CONTROL_FENCE = "```dual-control";
    private static final String ARTIFACT_OPEN = "<dual-artifact";
    private static final String ARTIFACT_CLOSE = "</dual-artifact>";
    private static final String DONE_MARKER = "[done] end_turn";

    Optional<String> extract(String transcript) {
        if (transcript == null || transcript.isBlank()) {
            return Optional.empty();
        }
        var normalized = transcript.replace("\r\n", "\n");
        var controlStart = normalized.lastIndexOf(CONTROL_FENCE);
        if (controlStart < 0) {
            return Optional.empty();
        }
        var controlEnd = normalized.indexOf("```", controlStart + CONTROL_FENCE.length());
        var end = controlEnd < 0 ? normalized.length() : controlEnd + 3;
        var artifactStart = latestCompleteArtifactStartBeforeControl(normalized, controlStart);
        var start = artifactStart >= 0 ? artifactStart : controlStart;
        var artifact = normalized.substring(start, end)
                .replace(DONE_MARKER, "")
                .strip();
        return artifact.isBlank() ? Optional.empty() : Optional.of(artifact + "\n");
    }

    private static int latestCompleteArtifactStartBeforeControl(String transcript, int controlStart) {
        var artifactStart = transcript.lastIndexOf(ARTIFACT_OPEN, controlStart);
        if (artifactStart < 0) {
            return -1;
        }
        var artifactEnd = transcript.indexOf(ARTIFACT_CLOSE, artifactStart);
        if (artifactEnd < 0 || artifactEnd > controlStart) {
            return -1;
        }
        return artifactStart;
    }
}
