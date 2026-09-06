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
        for (var response : responses) {
            if (response == null || response.obligationId() == null
                    || !response.obligationId().matches("sha256:[0-9a-f]{64}#(?:must_fix:[0-9]+|human-note:0)")) {
                errors.add("AUTHORED_RESPONSE_ID_INVALID");
                continue;
            }
            if (!ids.add(response.obligationId())) errors.add("AUTHORED_RESPONSE_DUPLICATE");
            if (response.disposition() == null) errors.add("AUTHORED_RESPONSE_DISPOSITION_MISSING");
            if (response.evidence() == null || response.evidence().isBlank()) errors.add("AUTHORED_RESPONSE_EVIDENCE_MISSING");
        }
        return List.copyOf(errors);
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
