package dev.dual.runtime.handler;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.dual.core.contract.TaskContract;

import java.util.List;

record TaskContractProposal(
        @JsonProperty("artifact_type") String artifactType,
        @JsonProperty("schema_version") int schemaVersion,
        String objective,
        Scope scope,
        @JsonProperty("acceptance_criteria") List<ContractClause> acceptanceCriteria,
        @JsonProperty("test_requirements") List<ContractClause> testRequirements,
        List<ContractClause> constraints,
        @JsonProperty("non_goals") List<ContractClause> nonGoals,
        @JsonProperty("risk_areas") List<ContractClause> riskAreas,
        @JsonProperty("evidence_refs") List<String> evidenceRefs,
        @JsonProperty("open_questions") List<String> openQuestions,
        @JsonProperty("revision_responses") List<RevisionResponse> revisionResponses
) {
    static final int SCHEMA_VERSION = 3;

    record Scope(
            List<TaskContract.ScopeEntry> included,
            List<String> excluded
    ) {}

    record ContractClause(String id, String text) {}

    record RevisionResponse(
            @JsonProperty("review_obligation_id") String reviewObligationId,
            @JsonProperty("resolved_by_clause_ids") List<String> resolvedByClauseIds
    ) {}
}
