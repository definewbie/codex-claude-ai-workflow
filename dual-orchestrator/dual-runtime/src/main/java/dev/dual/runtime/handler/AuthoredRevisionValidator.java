package dev.dual.runtime.handler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** Checks response shape and exact coverage, never whether the claimed repair is correct. */
final class AuthoredRevisionValidator {
    private AuthoredRevisionValidator() { }

    static List<String> shapeErrors(List<AgentResultEnvelope.AuthoredRevisionResponse> responses) {
        var errors = new ArrayList<String>();
        if (responses == null) {
            return List.of("AUTHORED_RESPONSES_MISSING: result.revision_responses must be explicit");
        }
        var ids = new HashSet<String>();
        for (int index = 0; index < responses.size(); index++) {
            var response = responses.get(index);
            if (response == null || !validId(response.obligationId())) {
                errors.add(invalidIdMessage(index, response == null ? null : response.obligationId()));
                continue;
            }
            if (!ids.add(response.obligationId())) errors.add("AUTHORED_RESPONSE_DUPLICATE");
            if (response.disposition() == null) errors.add("AUTHORED_RESPONSE_DISPOSITION_MISSING");
            if (response.evidence() == null || response.evidence().isBlank()) errors.add("AUTHORED_RESPONSE_EVIDENCE_MISSING");
        }
        return List.copyOf(errors);
    }

    static String invalidIdMessage(int index, String id) {
        return "AUTHORED_RESPONSE_ID_INVALID: index=" + index + ", length=" + (id == null ? "missing" : id.length())
                + ", expected sha256:<64 lowercase hex>#must_fix:<index> or #human-note:0";
    }

    private static boolean validId(String id) {
        return id != null && id.matches("sha256:[0-9a-f]{64}#(?:must_fix:[0-9]+|human-note:0)");
    }

    static List<Integer> repairableReferenceIndexes(List<AgentResultEnvelope.AuthoredRevisionResponse> responses,
                                                    List<String> activeIds) {
        if (responses == null || responses.isEmpty() || activeIds == null || responses.size() != activeIds.size()
                || activeIds.stream().anyMatch(id -> !validId(id))
                || new HashSet<>(activeIds).size() != activeIds.size()) return List.of();
        var seen = new HashSet<String>();
        var indexes = new ArrayList<Integer>();
        for (int index = 0; index < responses.size(); index++) {
            var response = responses.get(index);
            if (response == null || response.obligationId() == null || response.obligationId().isBlank()
                    || response.disposition() == null || response.evidence() == null || response.evidence().isBlank()
                    || !seen.add(response.obligationId())) return List.of();
            if (!validId(response.obligationId())) indexes.add(index);
            else if (!activeIds.contains(response.obligationId())) return List.of();
        }
        return List.copyOf(indexes);
    }

    static List<String> validate(List<AgentResultEnvelope.AuthoredRevisionResponse> responses, List<String> activeIds) {
        var errors = new ArrayList<>(shapeErrors(responses));
        if (!errors.isEmpty()) return List.copyOf(errors);
        var actual = new HashSet<String>();
        responses.forEach(response -> actual.add(response.obligationId()));
        if (!actual.equals(new HashSet<>(activeIds))) errors.add("AUTHORED_RESPONSE_COVERAGE_MISMATCH");
        return List.copyOf(errors);
    }
}
