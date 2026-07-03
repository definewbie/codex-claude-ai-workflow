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
        var start = artifactStart >= 0
                ? formalArtifactStart(normalized, artifactStart)
                : controlStart;
        var artifact = artifactStart >= 0
                ? normalized.substring(start, end)
                : normalized.substring(controlStart, end);
        artifact = artifact
                .replace(DONE_MARKER, "")
                .strip();
        return artifact.isBlank() ? Optional.empty() : Optional.of(artifact + "\n");
    }

    private static int formalArtifactStart(String transcript, int artifactStart) {
        var boundary = latestTranscriptBoundary(transcript, artifactStart);
        var heading = firstMarkdownHeadingAtOrAfter(transcript, boundary, artifactStart);
        return heading >= 0 ? heading : artifactStart;
    }

    private static int latestTranscriptBoundary(String transcript, int artifactStart) {
        var boundary = 0;
        var previousControl = transcript.lastIndexOf(CONTROL_FENCE, artifactStart - 1);
        if (previousControl >= 0) {
            var previousControlEnd = transcript.indexOf("```", previousControl + CONTROL_FENCE.length());
            if (previousControlEnd >= 0 && previousControlEnd < artifactStart) {
                boundary = previousControlEnd + 3;
            }
        }
        var cursor = 0;
        while (cursor < artifactStart) {
            var lineEnd = transcript.indexOf('\n', cursor);
            if (lineEnd < 0 || lineEnd > artifactStart) {
                lineEnd = artifactStart;
            }
            var line = transcript.substring(cursor, lineEnd).stripLeading();
            if (line.startsWith("[client]") || line.startsWith("[tool]") || line.startsWith("[assistant]")) {
                boundary = lineEnd < artifactStart ? lineEnd + 1 : lineEnd;
            }
            cursor = lineEnd + 1;
        }
        return Math.min(boundary, artifactStart);
    }

    private static int firstMarkdownHeadingAtOrAfter(String transcript, int start, int end) {
        var cursor = Math.max(0, start);
        while (cursor < end) {
            var lineEnd = transcript.indexOf('\n', cursor);
            if (lineEnd < 0 || lineEnd > end) {
                lineEnd = end;
            }
            var line = transcript.substring(cursor, lineEnd);
            if (line.matches("#{1,6}\\s+.+")) {
                return cursor;
            }
            cursor = lineEnd + 1;
        }
        return -1;
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

}
