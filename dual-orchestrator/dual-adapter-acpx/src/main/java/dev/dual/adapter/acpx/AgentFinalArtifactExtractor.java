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
    private static final String JSON_FENCE = "```json";
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
        var artifact = artifactStart >= 0
                ? normalized.substring(artifactStart, end)
                : jsonFenceArtifactBeforeControl(normalized, controlStart)
                        .map(json -> canonicalEnvelope(json, normalized.substring(controlStart, end)))
                        .orElseGet(() -> normalized.substring(controlStart, end));
        artifact = artifact
                .replace(DONE_MARKER, "")
                .strip();
        return artifact.isBlank() ? Optional.empty() : Optional.of(artifact + "\n");
    }

    private static int latestCompleteArtifactStartBeforeControl(String transcript, int controlStart) {
        var searchFrom = controlStart;
        while (searchFrom >= 0) {
            var artifactStart = transcript.lastIndexOf(ARTIFACT_OPEN, searchFrom);
            if (artifactStart < 0) {
                return -1;
            }
            var artifactEnd = transcript.indexOf(ARTIFACT_CLOSE, artifactStart);
            if (artifactEnd >= 0 && artifactEnd <= controlStart && isCanonicalEnvelopeStart(transcript, artifactStart)) {
                return artifactStart;
            }
            searchFrom = artifactStart - 1;
        }
        return -1;
    }

    private static boolean isCanonicalEnvelopeStart(String transcript, int artifactStart) {
        if (artifactStart > 0) {
            var previous = transcript.charAt(artifactStart - 1);
            if (previous != '\n' && previous != '\r') {
                return false;
            }
        }
        var tagEnd = transcript.indexOf('>', artifactStart);
        if (tagEnd < 0) {
            return false;
        }
        var tag = transcript.substring(artifactStart, tagEnd + 1);
        return tag.contains("type=\"") && !tag.contains("\\\"");
    }

    private static Optional<JsonFenceArtifact> jsonFenceArtifactBeforeControl(String transcript, int controlStart) {
        var jsonStart = transcript.lastIndexOf(JSON_FENCE, controlStart);
        if (jsonStart < 0) {
            return Optional.empty();
        }
        var contentStart = jsonStart + JSON_FENCE.length();
        var jsonEnd = transcript.indexOf("```", contentStart);
        if (jsonEnd < 0 || jsonEnd > controlStart) {
            return Optional.empty();
        }
        var body = transcript.substring(contentStart, jsonEnd).strip();
        var type = artifactType(body);
        if (type.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new JsonFenceArtifact(type.get(), body));
    }

    private static Optional<String> artifactType(String jsonBody) {
        var marker = "\"artifact_type\"";
        var key = jsonBody.indexOf(marker);
        if (key < 0) {
            return Optional.empty();
        }
        var colon = jsonBody.indexOf(':', key + marker.length());
        if (colon < 0) {
            return Optional.empty();
        }
        var firstQuote = jsonBody.indexOf('"', colon + 1);
        if (firstQuote < 0) {
            return Optional.empty();
        }
        var secondQuote = jsonBody.indexOf('"', firstQuote + 1);
        if (secondQuote < 0) {
            return Optional.empty();
        }
        var type = jsonBody.substring(firstQuote + 1, secondQuote).strip();
        return type.isBlank() ? Optional.empty() : Optional.of(type);
    }

    private static String canonicalEnvelope(JsonFenceArtifact artifact, String controlBlock) {
        return """
                <dual-artifact type="%s" version="1">
                %s
                </dual-artifact>

                %s
                """.formatted(artifact.type(), artifact.jsonBody(), controlBlock.strip());
    }

    private record JsonFenceArtifact(String type, String jsonBody) {
    }
}
