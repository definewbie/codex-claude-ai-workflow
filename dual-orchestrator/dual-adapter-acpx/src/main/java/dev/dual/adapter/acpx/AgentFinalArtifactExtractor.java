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

    /**
     * Dispatches artifact extraction by adapter protocol source. The session
     * protocol uses the {@code dual-control} fence; the native
     * {@code claude --print} protocol uses native-result.json. This keeps the
     * protocol/source distinction inside the extractor instead of branching in
     * the handler.
     */
    Optional<String> extract(String transcript, NativeClaudeTerminal nativeTerminal) {
        if (nativeTerminal != null) {
            return extractNativeArtifact(transcript);
        }
        return extract(transcript);
    }

    /**
     * Native {@code claude --print} artifact extraction. The native result text
     * may carry the same dual-control block and typed artifact envelope as the
     * session protocol, or only the typed artifact envelope. Prefer the full
     * contract block so native and session share one artifact contract; when the
     * session fence is absent, recognize the typed {@code <dual-artifact>}
     * envelope directly. Plain markdown with no typed envelope yields empty, so
     * the node fails at the artifact contract rather than being faked as success.
     */
    Optional<String> extractNativeArtifact(String result) {
        if (result == null || result.isBlank()) {
            return Optional.empty();
        }
        var fullContract = extract(result);
        if (fullContract.isPresent()) {
            return fullContract;
        }
        return extractEnvelope(result);
    }

    private static Optional<String> extractEnvelope(String transcript) {
        if (transcript == null || transcript.isBlank()) {
            return Optional.empty();
        }
        var normalized = transcript.replace("\r\n", "\n");
        var artifactStart = latestCanonicalArtifactStart(normalized);
        if (artifactStart < 0) {
            return Optional.empty();
        }
        var artifactEnd = normalized.indexOf(ARTIFACT_CLOSE, artifactStart);
        if (artifactEnd < 0) {
            return Optional.empty();
        }
        var artifact = normalized.substring(artifactStart, artifactEnd + ARTIFACT_CLOSE.length()).strip();
        return artifact.isBlank() ? Optional.empty() : Optional.of(artifact + "\n");
    }

    private static int latestCanonicalArtifactStart(String transcript) {
        var from = transcript.length();
        while (from > 0) {
            var artifactStart = transcript.lastIndexOf(ARTIFACT_OPEN, from - 1);
            if (artifactStart < 0) {
                return -1;
            }
            var artifactEnd = transcript.indexOf(ARTIFACT_CLOSE, artifactStart);
            if (artifactEnd >= 0 && isCanonicalEnvelopeStart(transcript, artifactStart)) {
                return artifactStart;
            }
            from = artifactStart;
        }
        return -1;
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
