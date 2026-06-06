package dev.dual.runtime.handler;

import java.util.Map;
import java.util.Optional;

final class AgentContractSemantics {
    private AgentContractSemantics() {
    }

    static Optional<DualControlParseResult> validateStructuredArtifact(
            String expectedType,
            AgentStructuredArtifactDocument document
    ) {
        var derivedStatus = AgentLifecycleStatus.fromDecision(document.decision());
        var declaredStatus = AgentLifecycleStatus.fromText(document.lifecycleStatus());
        if (!document.lifecycleStatus().isBlank() && declaredStatus == AgentLifecycleStatus.UNKNOWN) {
            return violation(
                    expectedType,
                    "STRUCTURED_ARTIFACT_UNKNOWN_STATUS",
                    "structured artifact lifecycle_status is not recognized: " + document.lifecycleStatus()
            );
        }
        if (!document.lifecycleStatus().isBlank() && declaredStatus != derivedStatus) {
            return violation(
                    expectedType,
                    "STRUCTURED_ARTIFACT_STATUS_MISMATCH",
                    "structured artifact lifecycle_status " + declaredStatus
                            + " conflicts with decision " + document.decision()
            );
        }

        if ("acceptance_review".equals(expectedType)) {
            return validateAcceptanceReview(expectedType, document, derivedStatus);
        }
        return Optional.empty();
    }

    static void addSidecarFields(Map<String, Object> sidecar) {
        var status = AgentLifecycleStatus.fromDecision(text(sidecar.get("decision")));
        var evidenceState = AgentEvidenceState.fromSidecar(sidecar);
        sidecar.put("lifecycle_status", status.name());
        sidecar.put("evidence_state", evidenceState.toMap());
    }

    private static Optional<DualControlParseResult> validateAcceptanceReview(
            String expectedType,
            AgentStructuredArtifactDocument document,
            AgentLifecycleStatus status
    ) {
        var evidenceState = AgentEvidenceState.from(document);
        if ((status == AgentLifecycleStatus.ACCEPTED || status == AgentLifecycleStatus.ACCEPTED_WITH_NOTES)
                && evidenceState.hasBlockingIssues()) {
            return violation(
                    expectedType,
                    "ACCEPTANCE_STATUS_EVIDENCE_CONFLICT",
                    "acceptance decision " + document.decision()
                            + " cannot include blocking=true, must_fix, or missing_evidence"
            );
        }
        if (status == AgentLifecycleStatus.REJECTED && !evidenceState.hasBlockingIssues()
                && document.failure().isEmpty()) {
            return violation(
                    expectedType,
                    "REJECTION_MISSING_BLOCKING_EVIDENCE",
                    "rejected acceptance artifact must include blocking evidence, must_fix, missing_evidence, or failure reason"
            );
        }
        return Optional.empty();
    }

    private static Optional<DualControlParseResult> violation(
            String expectedType,
            String code,
            String message
    ) {
        return Optional.of(DualControlParseResult.invalid(
                "STRUCTURED_ARTIFACT_SEMANTIC_VIOLATION",
                code + ": " + message,
                Map.of("required_artifact_type", expectedType, "semantic_code", code)
        ));
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
