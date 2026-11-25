package dev.dual.runtime.handler;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import dev.dual.core.arbitration.FailureCategory;

public final class AgentContractSemantics {
    private AgentContractSemantics() {
    }

    static Optional<ContractViolation> validateRereadReasons(Object rawValue) {
        if (!(rawValue instanceof List<?> list) || list.isEmpty()) {
            return Optional.empty();
        }
        for (int i = 0; i < list.size(); i++) {
            var item = list.get(i);
            if (!(item instanceof Map<?, ?> raw)) {
                return violation("REREAD_REASON_ENTRY_NOT_OBJECT",
                        "reread_reasons[" + i + "] must be an object", Map.of("index", i));
            }
            if (text(raw.get("path")).isBlank()) {
                return violation("REREAD_REASON_PATH_REQUIRED",
                        "reread_reasons[" + i + "] requires a non-blank 'path'", Map.of("index", i));
            }
            var reason = text(raw.get("reason"));
            if (!ReadReuseContractReasons.isAllowed(reason)) {
                return violation("REREAD_REASON_REASON_NOT_ALLOWED",
                        "reread_reasons[" + i + "] reason is not in the allowed catalog: " + reason,
                        Map.of("index", i, "reason", reason));
            }
            if (raw.containsKey("stage") && text(raw.get("stage")).isBlank()) {
                return violation("REREAD_REASON_STAGE_BLANK",
                        "reread_reasons[" + i + "] 'stage' is present but blank", Map.of("index", i));
            }
            if (raw.containsKey("evidence") && text(raw.get("evidence")).isBlank()) {
                return violation("REREAD_REASON_EVIDENCE_BLANK",
                        "reread_reasons[" + i + "] 'evidence' is present but blank", Map.of("index", i));
            }
        }
        return Optional.empty();
    }

    static Optional<ContractViolation> validateOutputContract(AgentOutputContractDocument document) {
        if ("self_test_report".equals(document.sourceArtifactType())
                && "SELF_TEST_COMPLETE".equals(document.decision())
                && (!document.mustFix().isEmpty() || !document.missingEvidence().isEmpty())) {
            return violation("SELF_TEST_COMPLETION_CONFLICT",
                    "SELF_TEST_COMPLETE cannot include must_fix or missing_evidence", Map.of());
        }
        if (isAcceptanceReview(document)) {
            var failure = document.failure();
            var classification = validateAcceptanceClassification(document.decision(), failure.categoryHint(),
                    failure.verificationRemediation(), failure.contractGaps(), failure.outOfScopeFiles(),
                    failure.missingTests(), failure.contextGaps(), failure.reason(),
                    document.mustFix(), document.missingEvidence());
            if (classification.isPresent()) return classification;
        }
        return StageOutcomeCompiler.validateEvidenceContract(
                "agent_output_contract",
                document.decision(),
                document.lifecycleStatus(),
                AgentEvidenceState.from(document),
                document.failure());
    }

    static List<String> acceptanceCategories() {
        return java.util.Arrays.stream(FailureCategory.values()).map(Enum::name).toList();
    }

    static boolean isAcceptanceReview(AgentOutputContractDocument document) {
        return "acceptance_review".equals(document.sourceArtifactType())
                || "acceptance.codex_verifier_review".equals(document.nodeId());
    }

    static Optional<ContractViolation> validateAcceptanceClassification(
            String decision, String categoryHint, AgentResultEnvelope.VerificationRemediation remediation,
            List<String> contractGaps, List<String> outOfScope, List<String> missingTests,
            List<String> contextGaps, String reason, List<String> mustFix, List<String> missingEvidence) {
        boolean reject = "REJECT".equals(decision);
        boolean tests = nonempty(missingTests);
        boolean activeRemediation = remediation != null
                && remediation != AgentResultEnvelope.VerificationRemediation.NONE;
        if (reject && tests && !activeRemediation)
            return classificationViolation("result.failure.verification_remediation is required when REJECT lists missing_tests");
        if (!(reject && tests) && activeRemediation)
            return classificationViolation("result.failure.verification_remediation is only allowed when REJECT lists missing_tests");
        if (!reject) {
            return categoryHint == null || categoryHint.isEmpty() ? Optional.empty()
                    : classificationViolation("accepted review must not declare a failure category");
        }
        final FailureCategory category;
        try { category = FailureCategory.valueOf(categoryHint == null ? "" : categoryHint); }
        catch (IllegalArgumentException failure) {
            return classificationViolation("REJECT requires exact failure.category_hint from " + acceptanceCategories());
        }
        boolean contract = category == FailureCategory.CONTRACT_GAP || category == FailureCategory.CONTRACT_AMBIGUITY;
        boolean testChange = remediation == AgentResultEnvelope.VerificationRemediation.TEST_CODE_CHANGE;
        if (contract != nonempty(contractGaps)) {
            return classificationViolation("contract categories require contract_gaps; other categories forbid them");
        }
        boolean valid = switch (category) {
            case CONTRACT_GAP, CONTRACT_AMBIGUITY -> true;
            case IMPLEMENTATION_BUG -> nonempty(mustFix) || nonempty(outOfScope) || testChange;
            case VERIFICATION_INSUFFICIENT -> (tests || nonempty(missingEvidence))
                    && !nonempty(outOfScope) && !nonempty(contextGaps) && !testChange;
            case CONTEXT_INSUFFICIENT -> nonempty(contextGaps) && !nonempty(outOfScope) && !testChange;
            case TOOL_LIMITATION -> reason != null && !reason.isBlank() && !nonempty(outOfScope) && !testChange;
            case REVIEW_NOISE -> reason != null && !reason.isBlank() && !nonempty(outOfScope)
                    && !tests && !nonempty(contextGaps) && !nonempty(missingEvidence)
                    && !nonempty(mustFix) && !activeRemediation;
        };
        return valid ? Optional.empty() : classificationViolation("failure evidence contradicts category " + category);
    }

    private static boolean nonempty(List<?> values) { return values != null && !values.isEmpty(); }

    private static Optional<ContractViolation> classificationViolation(String message) {
        return violation("ACCEPTANCE_CLASSIFICATION_INVALID", message, Map.of());
    }

    static void addSidecarFields(Map<String, Object> sidecar) {
        StageOutcomeCompiler.addSidecarFields(sidecar);
    }

    private static Optional<ContractViolation> violation(String code, String message, Map<String, Object> details) {
        return Optional.of(ContractViolation.of(code, message, details));
    }

    private static StringListValidation stringList(Object rawValue) {
        if (rawValue == null) {
            return new StringListValidation(List.of(), -1);
        }
        if (!(rawValue instanceof List<?> list)) {
            return new StringListValidation(List.of(), 0);
        }
        var values = new java.util.ArrayList<String>();
        for (int i = 0; i < list.size(); i++) {
            var value = list.get(i);
            if (!(value instanceof String item) || item.trim().isBlank()) {
                return new StringListValidation(List.of(), i);
            }
            values.add(item.trim());
        }
        return new StringListValidation(List.copyOf(values), -1);
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private record StringListValidation(List<String> values, int invalidIndex) {
    }
}
