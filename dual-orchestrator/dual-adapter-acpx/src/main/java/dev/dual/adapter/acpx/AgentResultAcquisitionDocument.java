package dev.dual.adapter.acpx;

import dev.dual.core.execution.AdapterSignal;
import dev.dual.core.execution.ProcessResult;

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
        AcpxUsage usage,
        ProcessResult processResult,
        List<AdapterSignal> adapterSignals
) {
    AgentResultOccurrenceDocument {
        contractValidationErrors = contractValidationErrors == null
                ? List.of()
                : List.copyOf(contractValidationErrors);
        if (processResult == null) {
            throw new IllegalArgumentException("processResult is required");
        }
        adapterSignals = adapterSignals == null ? List.of() : List.copyOf(adapterSignals);
    }
}
