package dev.dual.adapter.acpx;

import java.time.Instant;
import java.util.List;

record AgentResultAcquisitionDocument(
        int schemaVersion,
        boolean correctionAttempted,
        int maxCorrectionAttempts,
        int selectedCandidateOccurrence,
        String correctionReason,
        AcpxAgentResultAcquirer.CorrectionDisposition correctionDisposition,
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
        boolean contractSchemaValid,
        String contractCorrectionDisposition,
        List<String> contractValidationErrors,
        AcpxUsage usage
) {
    AgentResultOccurrenceDocument {
        contractValidationErrors = contractValidationErrors == null
                ? List.of()
                : List.copyOf(contractValidationErrors);
    }
}
