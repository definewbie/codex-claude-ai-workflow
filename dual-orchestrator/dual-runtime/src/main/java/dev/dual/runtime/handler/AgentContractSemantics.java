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
        return validateEvidenceContract(expectedType, document.decision(), derivedStatus, AgentEvidenceState.from(document), document.failure());
    }

    static Optional<DualControlParseResult> validateOutputContract(AgentOutputContractDocument document) {
        var status = AgentLifecycleStatus.fromDecision(document.decision());
        return validateEvidenceContract(
                "agent_output_contract",
                document.decision(),
                status,
                AgentEvidenceState.from(document),
                document.failure()
        );
    }

    static void addSidecarFields(Map<String, Object> sidecar) {
        var status = AgentLifecycleStatus.fromDecision(text(sidecar.get("decision")));
        var evidenceState = AgentEvidenceState.fromSidecar(sidecar);
        sidecar.put("lifecycle_status", status.name());
        sidecar.put("evidence_state", evidenceState.toMap());
    }

    private static Optional<DualControlParseResult> validateEvidenceContract(
            String expectedType,
            String decision,
            AgentLifecycleStatus status,
            AgentEvidenceState evidenceState,
            AgentOutputContractDocument.FailureFields failure
    ) {
        if (status == AgentLifecycleStatus.READY
                && evidenceState.hasBlockingDecisionIssues()) {
            return violation(
                    expectedType,
                    "POSITIVE_STATUS_EVIDENCE_CONFLICT",
                    "decision " + decision + " cannot include blocking=true or must_fix"
            );
        }
        if (status == AgentLifecycleStatus.ACCEPTED
                && evidenceState.hasFinalAcceptanceConflicts()) {
            return violation(
                    expectedType,
                    "FINAL_ACCEPTANCE_EVIDENCE_CONFLICT",
                    "decision " + decision + " cannot include blocking=true, must_fix, or missing_evidence"
            );
        }
        if (status == AgentLifecycleStatus.ACCEPTED_WITH_NOTES
                && evidenceState.hasBlockingDecisionIssues()) {
            return violation(
                    expectedType,
                    "NOTES_STATUS_BLOCKING_CONFLICT",
                    "decision " + decision + " cannot include blocking=true or must_fix; use REJECT for blocking issues"
            );
        }
        if ((status == AgentLifecycleStatus.NEEDS_REVISION
                || status == AgentLifecycleStatus.BLOCKED
                || status == AgentLifecycleStatus.REJECTED)
                && !evidenceState.hasBlockingIssues()
                && failure.isEmpty()) {
            return violation(
                    expectedType,
                    "NEGATIVE_STATUS_MISSING_EVIDENCE",
                    "decision " + decision + " must include blocking=true, must_fix, missing_evidence, or failure reason"
            );
        }
        return Optional.empty();
    }

    private static Optional<DualControlParseResult> validateAcceptanceReview(
            String expectedType,
            AgentStructuredArtifactDocument document,
            AgentLifecycleStatus status
    ) {
        var evidenceState = AgentEvidenceState.from(document);
        var commonViolation = validateEvidenceContract(
                expectedType,
                document.decision(),
                status,
                evidenceState,
                document.failure()
        );
        if (commonViolation.isPresent()) {
            return commonViolation;
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
