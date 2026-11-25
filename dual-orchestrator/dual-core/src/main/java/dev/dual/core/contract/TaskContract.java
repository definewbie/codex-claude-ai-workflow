package dev.dual.core.contract;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

public record TaskContract(
        @JsonProperty("schema_version") int schemaVersion,
        Phase phase,
        long epoch,
        String id,
        int version,
        @JsonProperty("created_at") Instant createdAt,
        String objective,
        Scope scope,
        @JsonProperty("acceptance_criteria") List<Clause> acceptanceCriteria,
        @JsonProperty("test_requirements") List<Clause> testRequirements,
        List<String> constraints,
        @JsonProperty("non_goals") List<String> nonGoals,
        @JsonProperty("risk_areas") List<String> riskAreas,
        @JsonProperty("evidence_refs") List<String> evidenceRefs,
        @JsonProperty("ambiguity_notes") List<String> ambiguityNotes,
        @JsonProperty("human_questions") List<String> humanQuestions,
        ContractStatus status,
        @JsonProperty("compilation_pass") int compilationPass,
        Source source,
        @JsonProperty("goal_contract") Reference goalContract,
        @JsonProperty("reviewed_candidates") List<Reference> reviewedCandidates,
        @JsonProperty("verification_bindings") List<VerificationBinding> verificationBindings,
        @JsonProperty("authority_refs") List<Reference> authorityRefs,
        @JsonProperty("nonessential_operation_declarations") List<NonessentialOperationDeclaration> nonessentialOperationDeclarations
) {

    /** An absent declaration grants no permission-failure downgrade. */
    public TaskContract(
        @JsonProperty("schema_version") int schemaVersion,
        Phase phase,
        long epoch,
        String id,
        int version,
        @JsonProperty("created_at") Instant createdAt,
        String objective,
        Scope scope,
        @JsonProperty("acceptance_criteria") List<Clause> acceptanceCriteria,
        @JsonProperty("test_requirements") List<Clause> testRequirements,
        List<String> constraints,
        @JsonProperty("non_goals") List<String> nonGoals,
        @JsonProperty("risk_areas") List<String> riskAreas,
        @JsonProperty("evidence_refs") List<String> evidenceRefs,
        @JsonProperty("ambiguity_notes") List<String> ambiguityNotes,
        @JsonProperty("human_questions") List<String> humanQuestions,
        ContractStatus status,
        @JsonProperty("compilation_pass") int compilationPass,
        Source source,
        @JsonProperty("goal_contract") Reference goalContract,
        @JsonProperty("reviewed_candidates") List<Reference> reviewedCandidates,
        @JsonProperty("verification_bindings") List<VerificationBinding> verificationBindings,
        @JsonProperty("authority_refs") List<Reference> authorityRefs
    ) {
        this(schemaVersion, phase, epoch, id, version, createdAt, objective, scope, acceptanceCriteria, testRequirements, constraints, nonGoals, riskAreas, evidenceRefs, ambiguityNotes, humanQuestions, status, compilationPass, source, goalContract, reviewedCandidates, verificationBindings, authorityRefs, List.of());
    }

    public TaskContract {
        if (schemaVersion != 2) throw new IllegalArgumentException("unsupported task contract schema: " + schemaVersion);
        if (phase == null) throw new IllegalArgumentException("contract phase is required");
        if (epoch < 1) throw new IllegalArgumentException("authorization epoch must be positive");
        reviewedCandidates = List.copyOf(reviewedCandidates == null ? List.of() : reviewedCandidates);
        verificationBindings = List.copyOf(verificationBindings == null ? List.of() : verificationBindings);
        authorityRefs = List.copyOf(authorityRefs == null ? List.of() : authorityRefs);
        nonessentialOperationDeclarations = List.copyOf(nonessentialOperationDeclarations == null ? List.of() : nonessentialOperationDeclarations);
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
        evidenceRefs = evidenceRefs == null ? List.of() : List.copyOf(evidenceRefs);
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

    public enum Phase { GOAL, EXECUTION }

    public record Clause(String id, String text) {}

    /** Identity of existing immutable bytes, never a grant by itself. */
    public record Reference(String ref, String sha256) {
        public Reference {
            if (ref == null || ref.isBlank() || !ref.equals(ref.trim()) || ref.contains("\\")
                    || ref.contains("\0") || ref.startsWith("/") || ref.matches("^[A-Za-z]:.*")
                    || ref.endsWith("/") || !java.nio.file.Path.of(ref).normalize().toString().equals(ref)
                    || java.util.Arrays.asList(ref.split("/")).contains("..")) {
                throw new IllegalArgumentException("reference must be a canonical relative path");
            }
            if (sha256 == null || !sha256.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("reference SHA-256 is required");
            }
        }
    }

    public enum Executor { AGENT, OPERATOR }
    public enum EvidenceKind { PROGRAM_TOOL_EXECUTION_AND_ASSERTIONS, OPERATOR_DECLARATION_WITH_MATERIAL }

    public record VerificationBinding(
            @JsonProperty("criterion_id") String criterionId,
            String method,
            Executor executor,
            @JsonProperty("environment_refs") List<Reference> environmentRefs,
            @JsonProperty("authority_refs") List<Reference> authorityRefs,
            @JsonProperty("evidence_kind") EvidenceKind evidenceKind,
            @JsonProperty("feasibility_refs") List<Reference> feasibilityRefs,
            @JsonProperty("detail_owner") String detailOwner,
            @JsonProperty("actual_exit_required") Boolean actualExitRequired,
            @JsonProperty("expected_exit_code") Integer expectedExitCode
    ) {
        public VerificationBinding {
            environmentRefs = List.copyOf(environmentRefs == null ? List.of() : environmentRefs);
            authorityRefs = List.copyOf(authorityRefs == null ? List.of() : authorityRefs);
            feasibilityRefs = List.copyOf(feasibilityRefs == null ? List.of() : feasibilityRefs);
        }
    }

    public record NonessentialOperationDeclaration(
            String id,
            @JsonProperty("node_definition_id") String nodeDefinitionId,
            @JsonProperty("node_contract_ref") Reference nodeContractRef,
            @JsonProperty("operation_kind") String operationKind,
            @JsonProperty("operation_parameters_sha256") String operationParametersSha256,
            @JsonProperty("criterion_ids") List<String> criterionIds,
            String rationale,
            @JsonProperty("rule_identity") String ruleIdentity) {
        public NonessentialOperationDeclaration {
            criterionIds = List.copyOf(criterionIds == null ? List.of() : criterionIds);
        }
    }

    public record Source(
            @JsonProperty("raw_request_ref") String rawRequestRef,
            @JsonProperty("created_by") String createdBy,
            Reference proposal,
            @JsonProperty("goal_approval") Reference goalApproval
    ) {}
}
