package dev.dual.runtime.handler;

import java.util.List;
import java.util.Map;
import java.util.Optional;

final class AgentContractSemantics {
    private AgentContractSemantics() {
    }

    /**
     * Validates the optional {@code reread_reasons} field from the raw artifact
     * envelope. Each entry must be an object with a non-blank {@code path} and a
     * {@code reason} belonging to {@link ReadReuseContractReasons}; optional
     * {@code stage}/{@code evidence}, if present, must not be blank.
     * Returns the first violation, or empty if absent/valid.
     */
    static Optional<DualControlParseResult> validateRereadReasons(Object rawValue) {
        if (!(rawValue instanceof List<?> list) || list.isEmpty()) {
            return Optional.empty();
        }
        for (int i = 0; i < list.size(); i++) {
            var item = list.get(i);
            if (!(item instanceof Map<?, ?> raw)) {
                return Optional.of(DualControlParseResult.invalid(
                        "REREAD_REASON_ENTRY_NOT_OBJECT",
                        "reread_reasons[" + i + "] must be an object",
                        Map.of("index", i)));
            }
            if (text(raw.get("path")).isBlank()) {
                return Optional.of(DualControlParseResult.invalid(
                        "REREAD_REASON_PATH_REQUIRED",
                        "reread_reasons[" + i + "] requires a non-blank 'path'",
                        Map.of("index", i)));
            }
            var reason = text(raw.get("reason"));
            if (!ReadReuseContractReasons.isAllowed(reason)) {
                return Optional.of(DualControlParseResult.invalid(
                        "REREAD_REASON_REASON_NOT_ALLOWED",
                        "reread_reasons[" + i + "] reason is not in the allowed catalog: " + reason,
                        Map.of("index", i, "reason", reason)));
            }
            if (raw.containsKey("stage") && text(raw.get("stage")).isBlank()) {
                return Optional.of(DualControlParseResult.invalid(
                        "REREAD_REASON_STAGE_BLANK",
                        "reread_reasons[" + i + "] 'stage' is present but blank",
                        Map.of("index", i)));
            }
            if (raw.containsKey("evidence") && text(raw.get("evidence")).isBlank()) {
                return Optional.of(DualControlParseResult.invalid(
                        "REREAD_REASON_EVIDENCE_BLANK",
                        "reread_reasons[" + i + "] 'evidence' is present but blank",
                        Map.of("index", i)));
            }
        }
        return Optional.empty();
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
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
