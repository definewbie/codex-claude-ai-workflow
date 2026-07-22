package dev.dual.adapter.acpx;

import java.time.Instant;
import java.util.List;

record AgentResultAcquisitionDocument(
        int schemaVersion,
        boolean correctionAttempted,
        int maxCorrectionAttempts,
        int selectedCandidateOccurrence,
        List<AgentResultOccurrenceDocument> occurrences
) {
    AgentResultAcquisitionDocument {
        occurrences = occurrences == null ? List.of() : List.copyOf(occurrences);
    }
}

record AgentResultOccurrenceDocument(
        int occurrence,
        String sessionName,
        String requestId,
        String promptPath,
        String outputPath,
        String transcriptPath,
        String stderrPath,
        Instant startedAt,
        Instant finishedAt,
        long durationMs,
        int exitCode,
        String stopReason,
        boolean contractDecodable,
        String parseErrorCode,
        AcpxUsage usage
) {
}
