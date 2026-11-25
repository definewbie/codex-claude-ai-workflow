package dev.dual.runtime.handler;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

record ContractResolutionDocument(
        @JsonProperty("schema_version") int schemaVersion,
        String decision,
        @JsonProperty("resolved_questions") List<ResolvedQuestion> resolvedQuestions,
        List<String> assumptions,
        @JsonProperty("accepted_risks") List<String> acceptedRisks,
        @JsonProperty("gate_ref") String gateRef,
        @JsonProperty("approved_by") String approvedBy,
        @JsonProperty("created_at") Instant createdAt
) {
    ContractResolutionDocument {
        resolvedQuestions = resolvedQuestions == null ? List.of() : List.copyOf(resolvedQuestions);
        assumptions = assumptions == null ? List.of() : List.copyOf(assumptions);
        acceptedRisks = acceptedRisks == null ? List.of() : List.copyOf(acceptedRisks);
    }

    record ResolvedQuestion(
            String question,
            String resolution,
            String source
    ) {}
}
