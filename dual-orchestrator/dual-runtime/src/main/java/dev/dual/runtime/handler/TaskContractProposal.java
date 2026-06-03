package dev.dual.runtime.handler;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

record TaskContractProposal(
        @JsonProperty("artifact_type") String artifactType,
        @JsonProperty("schema_version") int schemaVersion,
        String objective,
        Scope scope,
        @JsonProperty("acceptance_criteria") List<String> acceptanceCriteria,
        @JsonProperty("test_requirements") List<String> testRequirements,
        List<String> constraints,
        @JsonProperty("non_goals") List<String> nonGoals,
        @JsonProperty("risk_areas") List<String> riskAreas,
        @JsonProperty("evidence_refs") List<String> evidenceRefs,
        @JsonProperty("open_questions") List<String> openQuestions
) {
    record Scope(
            List<String> included,
            List<String> excluded
    ) {}
}
