package dev.dual.core.contract;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

public record TaskContract(
        String id,
        int version,
        @JsonProperty("created_at") Instant createdAt,
        String objective,
        Scope scope,
        @JsonProperty("acceptance_criteria") List<String> acceptanceCriteria,
        @JsonProperty("test_requirements") List<String> testRequirements,
        List<String> constraints,
        @JsonProperty("non_goals") List<String> nonGoals,
        @JsonProperty("risk_areas") List<String> riskAreas,
        @JsonProperty("ambiguity_notes") List<String> ambiguityNotes,
        @JsonProperty("human_questions") List<String> humanQuestions,
        ContractStatus status,
        @JsonProperty("compilation_pass") int compilationPass,
        Source source
) {

    public TaskContract {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id is required");
        }
        if (version < 1) {
            throw new IllegalArgumentException("version must be >= 1");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt is required");
        }
        if (objective == null || objective.isBlank()) {
            throw new IllegalArgumentException("objective is required");
        }
        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }
        acceptanceCriteria = acceptanceCriteria == null ? List.of() : List.copyOf(acceptanceCriteria);
        testRequirements = testRequirements == null ? List.of() : List.copyOf(testRequirements);
        constraints = constraints == null ? List.of() : List.copyOf(constraints);
        nonGoals = nonGoals == null ? List.of() : List.copyOf(nonGoals);
        riskAreas = riskAreas == null ? List.of() : List.copyOf(riskAreas);
        ambiguityNotes = ambiguityNotes == null ? List.of() : List.copyOf(ambiguityNotes);
        humanQuestions = humanQuestions == null ? List.of() : List.copyOf(humanQuestions);
    }

    public record Scope(
            List<ScopeEntry> included,
            List<String> excluded
    ) {
        public Scope {
            included = included == null ? List.of() : List.copyOf(included);
            excluded = excluded == null ? List.of() : List.copyOf(excluded);
        }
    }

    public record ScopeEntry(
            String path,
            ScopeOperation operation
    ) {}

    public enum ScopeOperation {
        MODIFY,
        CREATE
    }

    public record Source(
            @JsonProperty("raw_request_ref")
            @JsonAlias("rawRequestRef")
            String rawRequestRef,
            @JsonProperty("created_by")
            @JsonAlias("createdBy")
            String createdBy
    ) {}
}
