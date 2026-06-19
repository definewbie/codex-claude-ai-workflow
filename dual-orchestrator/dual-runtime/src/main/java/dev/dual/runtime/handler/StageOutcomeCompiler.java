package dev.dual.runtime.handler;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Compiles Agent-proposed status fields into a canonical stage outcome.
 *
 * This is the orchestration boundary between "the LLM said X" and "the
 * workflow may route as Y". Rules here are intentionally mechanical. A future
 * semantic judge can add recommendations, but the final canonical outcome must
 * still be produced by this compiler and recorded in the sidecar.
 */
final class StageOutcomeCompiler {
    private StageOutcomeCompiler() {
    }

    static StageOutcomeCompilation compile(String decision, String declaredStatus) {
        var derived = StageOutcome.fromDecision(decision);
        if (declaredStatus == null || declaredStatus.isBlank()) {
            return StageOutcomeCompilation.valid(derived, "decision");
        }
        var declared = StageOutcome.fromText(declaredStatus);
        if (declared == StageOutcome.UNKNOWN) {
            return StageOutcomeCompilation.invalid(
                    derived,
                    "STRUCTURED_ARTIFACT_UNKNOWN_STATUS",
                    "structured artifact lifecycle_status is not recognized: " + declaredStatus
            );
        }
        if (declared == derived) {
            return StageOutcomeCompilation.valid(declared, "declared_status");
        }
        if (derived == StageOutcome.REJECTED && declared == StageOutcome.INSUFFICIENT_EVIDENCE) {
            return StageOutcomeCompilation.valid(declared, "declared_status");
        }
        return StageOutcomeCompilation.invalid(
                derived,
                "STRUCTURED_ARTIFACT_STATUS_MISMATCH",
                "structured artifact lifecycle_status " + declared
                        + " conflicts with decision " + decision
        );
    }

    static void addSidecarFields(Map<String, Object> sidecar) {
        var declared = text(sidecar.get("lifecycle_status"));
        var compilation = compile(text(sidecar.get("decision")), declared);
        var outcome = compilation.outcome();
        var evidenceState = AgentEvidenceState.fromSidecar(sidecar);
        sidecar.put("stage_outcome", outcome.name());
        sidecar.put("stage_outcome_source", compilation.source());
        sidecar.put("lifecycle_status", outcome.name());
        sidecar.put("evidence_state", evidenceState.toMap());
    }

    static Optional<DualControlParseResult> validateEvidenceContract(
            String expectedType,
            String decision,
            String declaredStatus,
            AgentEvidenceState evidenceState,
            AgentOutputContractDocument.FailureFields failure
    ) {
        var compilation = compile(decision, declaredStatus);
        if (!compilation.valid()) {
            return violation(expectedType, compilation.errorCode(), compilation.message());
        }
        var outcome = compilation.outcome();
        if (outcome == StageOutcome.READY
                && evidenceState.hasBlockingDecisionIssues()) {
            return violation(
                    expectedType,
                    "POSITIVE_STATUS_EVIDENCE_CONFLICT",
                    "decision " + decision + " cannot include blocking=true or must_fix"
            );
        }
        if (outcome == StageOutcome.ACCEPTED
                && evidenceState.hasFinalAcceptanceConflicts()) {
            return violation(
                    expectedType,
                    "FINAL_ACCEPTANCE_EVIDENCE_CONFLICT",
                    "decision " + decision + " cannot include blocking=true, must_fix, or missing_evidence"
            );
        }
        if (outcome == StageOutcome.ACCEPTED_WITH_NOTES
                && evidenceState.hasBlockingDecisionIssues()) {
            return violation(
                    expectedType,
                    "NOTES_STATUS_BLOCKING_CONFLICT",
                    "decision " + decision + " cannot include blocking=true or must_fix; use REJECT for blocking issues"
            );
        }
        if ((outcome == StageOutcome.NEEDS_REVISION
                || outcome == StageOutcome.BLOCKED
                || outcome == StageOutcome.REJECTED
                || outcome == StageOutcome.INSUFFICIENT_EVIDENCE)
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

    record StageOutcomeCompilation(
            StageOutcome outcome,
            String source,
            boolean valid,
            String errorCode,
            String message
    ) {
        static StageOutcomeCompilation valid(StageOutcome outcome, String source) {
            return new StageOutcomeCompilation(outcome, source, true, "", "");
        }

        static StageOutcomeCompilation invalid(StageOutcome fallback, String errorCode, String message) {
            return new StageOutcomeCompilation(fallback, "invalid", false, errorCode, message);
        }

        Map<String, Object> toMap() {
            var map = new LinkedHashMap<String, Object>();
            map.put("outcome", outcome.name());
            map.put("source", source);
            map.put("valid", valid);
            if (!errorCode.isBlank()) {
                map.put("error_code", errorCode);
                map.put("message", message);
            }
            return map;
        }
    }
}
