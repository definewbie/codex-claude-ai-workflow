package dev.dual.runtime.handler;

import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    static Optional<ContractViolation> validateScopeExpansionRequest(Object rawValue) {
        if (rawValue == null) {
            return Optional.empty();
        }
        if (!(rawValue instanceof Map<?, ?> raw)) {
            return violation("SCOPE_EXPANSION_REQUEST_NOT_OBJECT",
                    "scope_expansion_request must be an object", Map.of());
        }
        var requested = stringList(raw.get("requested_files"));
        var read = stringList(raw.get("read_files"));
        var files = stringList(raw.get("files"));
        if (requested.invalidIndex() >= 0) {
            return invalidScopeExpansionList("requested_files", requested.invalidIndex());
        }
        if (read.invalidIndex() >= 0) {
            return invalidScopeExpansionList("read_files", read.invalidIndex());
        }
        if (files.invalidIndex() >= 0) {
            return invalidScopeExpansionList("files", files.invalidIndex());
        }
        if (requested.values().isEmpty() && read.values().isEmpty() && files.values().isEmpty()) {
            return violation("SCOPE_EXPANSION_REQUEST_FILES_REQUIRED",
                    "scope_expansion_request requires at least one path in requested_files, read_files, or files",
                    Map.of());
        }
        if (raw.containsKey("reason") && text(raw.get("reason")).isBlank()) {
            return violation("SCOPE_EXPANSION_REQUEST_REASON_BLANK",
                    "scope_expansion_request.reason is present but blank", Map.of());
        }
        if (raw.containsKey("evidence") && text(raw.get("evidence")).isBlank()) {
            return violation("SCOPE_EXPANSION_REQUEST_EVIDENCE_BLANK",
                    "scope_expansion_request.evidence is present but blank", Map.of());
        }
        return Optional.empty();
    }

    static Optional<ContractViolation> validateOutputContract(AgentOutputContractDocument document) {
        return StageOutcomeCompiler.validateEvidenceContract(
                "agent_output_contract",
                document.decision(),
                document.lifecycleStatus(),
                AgentEvidenceState.from(document),
                document.failure());
    }

    static void addSidecarFields(Map<String, Object> sidecar) {
        StageOutcomeCompiler.addSidecarFields(sidecar);
    }

    private static Optional<ContractViolation> invalidScopeExpansionList(String field, int index) {
        return violation("SCOPE_EXPANSION_REQUEST_PATH_INVALID",
                "scope_expansion_request." + field + "[" + index + "] must be a non-blank string",
                Map.of("field", field, "index", index));
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
