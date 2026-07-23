package dev.dual.runtime.handler;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.dual.core.contract.TaskContract;

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
    static final int SCHEMA_VERSION = 2;

    record Scope(
            List<TaskContract.ScopeEntry> included,
            List<String> excluded
    ) {}
}
