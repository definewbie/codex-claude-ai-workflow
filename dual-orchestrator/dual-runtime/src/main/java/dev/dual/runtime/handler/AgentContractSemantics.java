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
        if ("acceptance_review".equals(expectedType)) {
            return validateAcceptanceReview(expectedType, document);
        }
        return StageOutcomeCompiler.validateEvidenceContract(
                expectedType,
                document.decision(),
                document.lifecycleStatus(),
                AgentEvidenceState.from(document),
                document.failure()
        );
    }

    static Optional<DualControlParseResult> validateOutputContract(AgentOutputContractDocument document) {
        return StageOutcomeCompiler.validateEvidenceContract(
                "agent_output_contract",
                document.decision(),
                document.lifecycleStatus(),
                AgentEvidenceState.from(document),
                document.failure()
        );
    }

    static void addSidecarFields(Map<String, Object> sidecar) {
        StageOutcomeCompiler.addSidecarFields(sidecar);
    }

    private static Optional<DualControlParseResult> validateAcceptanceReview(
            String expectedType,
            AgentStructuredArtifactDocument document
    ) {
        var evidenceState = AgentEvidenceState.from(document);
        var commonViolation = StageOutcomeCompiler.validateEvidenceContract(
                expectedType,
                document.decision(),
                document.lifecycleStatus(),
                evidenceState,
                document.failure()
        );
        if (commonViolation.isPresent()) {
            return commonViolation;
        }
        return Optional.empty();
    }
}
