package dev.dual.runtime.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import dev.dual.core.contract.TaskContract;
import dev.dual.core.contract.TaskContractValidator;

final class TaskContractProposalValidator {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    List<String> validate(TaskContractProposal proposal, Path workspaceRoot) {
        return validate(proposal, workspaceRoot, null, null);
    }

    List<String> validate(TaskContractProposal proposal, Path workspaceRoot,
                          TaskContractProposal approvedScopeBaseline, String originalBaseCommit) {
        return validate(proposal, workspaceRoot, approvedScopeBaseline, originalBaseCommit, List.of());
    }

    List<String> validate(TaskContractProposal proposal, Path workspaceRoot,
                          TaskContractProposal approvedScopeBaseline, String originalBaseCommit,
                          List<TaskContract.ScopeEntry> scopeReviewCandidates) {
        var errors = new ArrayList<String>();
        if (!scopeReviewCandidates.isEmpty()) {
            if (approvedScopeBaseline == null) return List.of("scope review requires its approved baseline");
            if (!Objects.equals(proposal.objective(), approvedScopeBaseline.objective())
                    || !Objects.equals(proposal.acceptanceCriteria(), approvedScopeBaseline.acceptanceCriteria())
                    || !Objects.equals(proposal.testRequirements(), approvedScopeBaseline.testRequirements())
                    || !Objects.equals(proposal.constraints(), approvedScopeBaseline.constraints())
                    || !Objects.equals(proposal.nonGoals(), approvedScopeBaseline.nonGoals())
                    || !Objects.equals(proposal.riskAreas(), approvedScopeBaseline.riskAreas())
                    || !Objects.equals(proposal.openQuestions(), approvedScopeBaseline.openQuestions())
                    || !Objects.equals(proposal.evidenceRefs(), approvedScopeBaseline.evidenceRefs())
                    || proposal.scope() == null
                    || !Objects.equals(proposal.scope().excluded(), approvedScopeBaseline.scope().excluded())
                    || proposal.scope().included() == null
                    || !proposal.scope().included().containsAll(approvedScopeBaseline.scope().included())) {
                errors.add("scope review must preserve the approved contract outside candidate additions");
            } else {
                for (var entry : proposal.scope().included()) {
                    if (entry == null || (!approvedScopeBaseline.scope().included().contains(entry) && !scopeReviewCandidates.contains(entry)))
                        errors.add("scope review addition is not in the bound evaluation: " + entry);
                }
            }
        }
        java.util.Set<String> originalPaths = java.util.Set.of();
        if (approvedScopeBaseline != null) {
            try {
                originalPaths = TaskContractScopeResolver.originalPaths(workspaceRoot, originalBaseCommit);
            } catch (IOException failure) {
                return List.of("approved scope workspace base is invalid: " + failure.getMessage());
            }
        }
        if (!"task_contract_proposal".equals(proposal.artifactType())) {
            errors.add("artifact_type must be task_contract_proposal");
        }
        if (proposal.schemaVersion() != TaskContractProposal.SCHEMA_VERSION) {
            errors.add("schema_version must be " + TaskContractProposal.SCHEMA_VERSION);
        }
        if (isBlank(proposal.objective())) {
            errors.add("objective must not be blank");
        }
        if (proposal.scope() == null) {
            errors.add("scope must not be null");
        } else {
            validateScopeEntries(proposal.scope().included(), errors);
            validateList("scope.excluded", proposal.scope().excluded(), false, errors);
            if (proposal.scope().included() != null) {
                for (var ref : proposal.scope().included()) {
                    var inheritedCreate = ref != null && ref.operation() == TaskContract.ScopeOperation.CREATE
                            && approvedScopeBaseline != null
                            && (approvedScopeBaseline.scope().included().contains(ref)
                                || scopeReviewCandidates.contains(ref));
                    validateWorkspacePathRef(ref, workspaceRoot, inheritedCreate, errors);
                    if (approvedScopeBaseline != null && ref != null
                            && ref.operation() == TaskContract.ScopeOperation.MODIFY
                            && !originalPaths.contains(ref.path())) {
                        errors.add("scope.included MODIFY path is absent from original workspace base: " + ref.path());
                    }
                }
            }
            validateScopeOverlap(proposal.scope(), errors);
        }
        var clauseIds = new HashSet<String>();
        validateClauses("acceptance_criteria", proposal.acceptanceCriteria(), true, clauseIds, errors);
        if (proposal.acceptanceCriteria() != null && nonBlankClauseCount(proposal.acceptanceCriteria()) < 2) {
            errors.add("acceptance_criteria requires at least two observable items");
        }
        validateClauses("constraints", proposal.constraints(), false, clauseIds, errors);
        validateClauses("non_goals", proposal.nonGoals(), false, clauseIds, errors);
        if (nonBlankClauseCount(proposal.constraints()) == 0 && nonBlankClauseCount(proposal.nonGoals()) == 0) {
            errors.add("constraints or non_goals must define task boundaries");
        }
        validateClauses("risk_areas", proposal.riskAreas(), false, clauseIds, errors);
        validateClauses("test_requirements", proposal.testRequirements(), true, clauseIds, errors);
        validateList("evidence_refs", proposal.evidenceRefs(), false, errors);
        validateList("open_questions", proposal.openQuestions(), false, errors);
        validateRevisionResponses(proposal.revisionResponses(), clauseIds, errors);
        return errors;
    }

    List<String> validateInitialIteration(TaskContractProposal proposal) {
        if (proposal.revisionResponses() == null || proposal.revisionResponses().isEmpty()) {
            return List.of();
        }
        return List.of("revision_responses must be empty for the initial workflow iteration");
    }

    List<String> validateEvidenceTraceability(
            TaskContractProposal proposal,
            Path workspaceRoot,
            RepositoryFactBinding.Verified repositoryFactBinding
    ) {
        var errors = new ArrayList<String>();
        if (repositoryFactBinding == null) {
            return List.of("tracked inventory occurrence binding is required before task contract proposal validation");
        }
        var repoMapArtifact = repositoryFactBinding.document().artifacts().get("repoMap");
        if (repoMapArtifact == null) {
            return List.of("tracked inventory occurrence binding has no repoMap artifact");
        }
        var repoMapPath = workspaceRoot.toAbsolutePath().normalize().resolve(repoMapArtifact.ref()).normalize();
        if (!Files.isRegularFile(repoMapPath)) {
            return List.of("tracked inventory is required before task contract proposal validation");
        }
        final Map<String, Object> repoMap;
        try {
            repoMap = OBJECT_MAPPER.readValue(repoMapPath.toFile(), MAP_TYPE);
        } catch (IOException failure) {
            return List.of("tracked inventory cannot be read: " + failure.getMessage());
        }
        if ("git_tracked_inventory".equals(String.valueOf(repoMap.get("source")))) {
            try {
                var current = GitTrackedPathInventory.capture(workspaceRoot);
                var expectedHead = repositoryFactBinding.document().inventoryHead();
                var expectedSha = repositoryFactBinding.document().inventorySha256();
                var expectedCount = repositoryFactBinding.document().inventoryPathCount();
                if (!current.head().equals(expectedHead)
                        || !current.sha256().equals(expectedSha)
                        || current.paths().size() != expectedCount) {
                    errors.add("tracked inventory identity changed after context projection");
                }
            } catch (IOException failure) {
                errors.add("tracked inventory identity cannot be revalidated: " + failure.getMessage());
            }
        }
        var inventoryPaths = new LinkedHashSet<String>();
        if (repoMap.get("files") instanceof List<?> files) {
            for (var item : files) {
                if (item instanceof Map<?, ?> file) {
                    var value = file.get("path");
                    var path = value == null ? "" : String.valueOf(value).trim();
                    if (!path.isBlank()) {
                        inventoryPaths.add(path);
                    }
                }
            }
        }
        var evidenceRefs = proposal.evidenceRefs() == null ? List.<String>of() : proposal.evidenceRefs();
        var tracedRepositoryFact = false;
        for (var evidenceRef : evidenceRefs) {
            if (isBlank(evidenceRef)) {
                continue;
            }
            var ref = evidenceRef.trim();
            if (!TaskContractValidator.isCanonicalPath(ref)) {
                errors.add("evidence_refs contains a non-canonical path: " + ref);
            } else if (inventoryPaths.contains(ref) && !Files.isRegularFile(workspaceRoot.resolve(ref).normalize())) {
                errors.add("evidence_refs does not resolve to an existing file: " + ref);
            } else if (inventoryPaths.contains(ref)) {
                tracedRepositoryFact = true;
            }
        }
        if (!inventoryPaths.isEmpty() && !tracedRepositoryFact) {
            errors.add("evidence_refs requires at least one fact from the tracked inventory");
        }
        return List.copyOf(errors);
    }

    List<String> validateRevisionCoverage(
            TaskContractProposal proposal,
            List<AgentResultEnvelope.ReviewObligation> obligations
    ) {
        var errors = new ArrayList<String>();
        if (obligations == null || obligations.isEmpty()) {
            errors.add("revision iteration requires verified contract obligations");
            return errors;
        }
        var expected = new LinkedHashMap<String, AgentResultEnvelope.ReviewObligation>();
        for (var obligation : obligations) {
            if (obligation == null || isBlank(obligation.id()) || isBlank(obligation.text())) {
                errors.add("verified contract obligation must contain nonblank id and text");
                continue;
            }
            if (expected.putIfAbsent(obligation.id().trim(), obligation) != null) {
                errors.add("verified contract obligation id is duplicated: " + obligation.id().trim());
            }
        }
        var actual = new LinkedHashMap<String, TaskContractProposal.RevisionResponse>();
        if (proposal.revisionResponses() == null || proposal.revisionResponses().isEmpty()) {
            errors.add("revision_responses is required for a revision iteration");
            return errors;
        }
        for (var response : proposal.revisionResponses()) {
            if (response == null || isBlank(response.reviewObligationId())) {
                continue;
            }
            var id = response.reviewObligationId().trim();
            if (actual.putIfAbsent(id, response) != null) {
                errors.add("revision_responses contains duplicate obligation id: " + id);
            }
        }
        for (var id : expected.keySet()) {
            if (!actual.containsKey(id)) {
                errors.add("revision_responses does not cover obligation: " + id);
            }
        }
        for (var id : actual.keySet()) {
            if (!expected.containsKey(id)) {
                errors.add("revision_responses refers to unknown obligation: " + id);
            }
        }
        return errors;
    }

    /** Bounded full revision: goal/exclusions may change, ordinary fields retain their patch limits. */
    public List<String> validateGoalReproposal(TaskContractProposal previous, TaskContractProposal revised) {
        var errors = new ArrayList<String>();
        if (previous == null || revised == null) return List.of("goal re-proposal baseline absent");
        if (previous.objective().trim().equals(revised.objective().trim())
                && new HashSet<>(previous.scope().excluded()).equals(new HashSet<>(revised.scope().excluded())))
            errors.add("goal re-proposal requires actual objective or exclusion change");
        if (!revised.scope().included().containsAll(previous.scope().included())) errors.add("scope removal/change is not allowed");
        if (!revised.evidenceRefs().containsAll(previous.evidenceRefs())) errors.add("evidence removal is not allowed");
        if (!revised.openQuestions().equals(previous.openQuestions())) errors.add("open-question change is not allowed");
        var before=clauseSnapshot(previous);var after=clauseSnapshot(revised);
        for(var id:before.keySet()) if(after.containsKey(id) && !before.get(id).section().equals(after.get(id).section()))
            errors.add("clause section move is not allowed: " + id);
        errors.addAll(validateRevisionDelta(previous,revised).stream().map(RevisionDeltaError::message).toList());
        return List.copyOf(errors);
    }

    List<RevisionDeltaError> validateRevisionDelta(
            TaskContractProposal previous,
            TaskContractProposal current
    ) {
        var errors = new ArrayList<RevisionDeltaError>();
        if (previous == null) {
            return List.of(new RevisionDeltaError("REVISION_BASELINE_UNRESOLVABLE", ""));
        }
        var previousClauses = clauseSnapshot(previous);
        var currentClauses = clauseSnapshot(current);
        var modified = new LinkedHashSet<String>();
        var added = new LinkedHashSet<String>();
        var deleted = new LinkedHashSet<String>();
        for (var entry : currentClauses.entrySet()) {
            var baseline = previousClauses.get(entry.getKey());
            if (baseline == null) {
                added.add(entry.getKey());
            } else if (!baseline.equals(entry.getValue())) {
                modified.add(entry.getKey());
            }
        }
        for (var id : previousClauses.keySet()) {
            if (!currentClauses.containsKey(id)) {
                deleted.add(id);
            }
        }

        var declared = new LinkedHashSet<String>();
        if (current.revisionResponses() != null) {
            for (var response : current.revisionResponses()) {
                if (response != null && response.resolvedByClauseIds() != null) {
                    response.resolvedByClauseIds().stream()
                            .filter(id -> !isBlank(id))
                            .map(String::trim)
                            .forEach(declared::add);
                }
            }
        }
        modified.stream()
                .filter(id -> !declared.contains(id))
                .forEach(id -> errors.add(
                        new RevisionDeltaError("UNDECLARED_CONTRACT_CLAUSE_CHANGE", id)));
        added.stream()
                .filter(id -> !declared.contains(id))
                .forEach(id -> errors.add(
                        new RevisionDeltaError("UNDECLARED_CONTRACT_CLAUSE_ADDITION", id)));
        deleted.forEach(id -> errors.add(
                new RevisionDeltaError("CONTRACT_CLAUSE_DELETION_NOT_ALLOWED", id)));

        return List.copyOf(errors);
    }

    private static Map<String, ClauseSnapshot> clauseSnapshot(TaskContractProposal proposal) {
        var clauses = new LinkedHashMap<String, ClauseSnapshot>();
        addClauseSnapshots(clauses, "acceptance_criteria", proposal.acceptanceCriteria());
        addClauseSnapshots(clauses, "test_requirements", proposal.testRequirements());
        addClauseSnapshots(clauses, "constraints", proposal.constraints());
        addClauseSnapshots(clauses, "non_goals", proposal.nonGoals());
        addClauseSnapshots(clauses, "risk_areas", proposal.riskAreas());
        return Map.copyOf(clauses);
    }

    private static void addClauseSnapshots(
            Map<String, ClauseSnapshot> target,
            String section,
            List<TaskContractProposal.ContractClause> clauses
    ) {
        if (clauses == null) {
            return;
        }
        for (var clause : clauses) {
            if (clause != null && !isBlank(clause.id()) && !isBlank(clause.text())) {
                target.put(clause.id().trim(), new ClauseSnapshot(section, clause.text().trim()));
            }
        }
    }

    private static void validateClauses(
            String name,
            List<TaskContractProposal.ContractClause> clauses,
            boolean required,
            HashSet<String> ids,
            List<String> errors
    ) {
        if (clauses == null) {
            if (required) {
                errors.add(name + " must not be null");
            }
            return;
        }
        if (required && clauses.stream().noneMatch(clause -> clause != null && !isBlank(clause.text()))) {
            errors.add(name + " must not be empty");
        }
        var texts = new HashSet<String>();
        for (var clause : clauses) {
            if (clause == null) {
                errors.add(name + " contains null clause");
                continue;
            }
            if (isBlank(clause.id())) {
                errors.add(name + " contains blank clause id");
            } else if (!ids.add(clause.id().trim())) {
                errors.add("contract clause id is duplicated: " + clause.id().trim());
            }
            if (isBlank(clause.text())) {
                errors.add(name + " contains blank clause text");
            } else {
                if (containsMetadataNoise(clause.text())) {
                    errors.add(name + " contains ledger/context metadata: " + clause.text());
                }
                if (!texts.add(clause.text().trim().toLowerCase())) {
                    errors.add(name + " contains duplicate item: " + clause.text());
                }
            }
        }
    }

    private static void validateRevisionResponses(
            List<TaskContractProposal.RevisionResponse> responses,
            HashSet<String> clauseIds,
            List<String> errors
    ) {
        if (responses == null) {
            errors.add("revision_responses must not be null");
            return;
        }
        var obligationIds = new HashSet<String>();
        for (var response : responses) {
            if (response == null) {
                errors.add("revision_responses contains null response");
                continue;
            }
            if (isBlank(response.reviewObligationId())) {
                errors.add("revision_responses contains blank obligation id");
            } else if (!obligationIds.add(response.reviewObligationId().trim())) {
                errors.add("revision_responses contains duplicate obligation id: "
                        + response.reviewObligationId().trim());
            }
            if (response.resolvedByClauseIds() == null || response.resolvedByClauseIds().isEmpty()) {
                errors.add("revision_responses resolved_by_clause_ids must not be empty");
                continue;
            }
            var targets = new HashSet<String>();
            for (var clauseId : response.resolvedByClauseIds()) {
                if (isBlank(clauseId)) {
                    errors.add("revision_responses contains blank clause id");
                } else if (!targets.add(clauseId.trim())) {
                    errors.add("revision_responses contains duplicate clause target: " + clauseId.trim());
                } else if (!clauseIds.contains(clauseId.trim())) {
                    errors.add("revision_responses refers to unknown clause id: " + clauseId.trim());
                }
            }
        }
    }

    private static void validateList(String name, List<String> values, boolean required, List<String> errors) {
        if (values == null) {
            if (required) {
                errors.add(name + " must not be null");
            }
            return;
        }
        if (required && values.stream().allMatch(TaskContractProposalValidator::isBlank)) {
            errors.add(name + " must not be empty");
        }
        var seen = new HashSet<String>();
        for (var value : values) {
            if (containsMetadataNoise(value)) {
                errors.add(name + " contains ledger/context metadata: " + value);
            }
            if (!isBlank(value) && !seen.add(value.trim().toLowerCase())) {
                errors.add(name + " contains duplicate item: " + value);
            }
        }
    }

    private static void validateScopeOverlap(TaskContractProposal.Scope scope, List<String> errors) {
        if (scope.included() == null || scope.excluded() == null) {
            return;
        }
        var excluded = new HashSet<String>();
        for (var value : scope.excluded()) {
            if (!isBlank(value)) {
                excluded.add(value.trim());
            }
        }
        for (var included : scope.included()) {
            if (included != null
                    && !isBlank(included.path())
                    && excluded.contains(included.path())) {
                errors.add("scope.included overlaps scope.excluded: " + included.path());
            }
        }
    }

    private static void validateScopeEntries(
            List<TaskContract.ScopeEntry> entries,
            List<String> errors
    ) {
        if (entries == null) {
            errors.add("scope.included must be present (GOAL may propose no files)");
            return;
        }
        var seen = new HashSet<String>();
        for (var entry : entries) {
            if (entry == null) {
                errors.add("scope.included contains null entry");
                continue;
            }
            if (isBlank(entry.path())) {
                errors.add("scope.included contains blank path");
                continue;
            }
            if (entry.operation() == null) {
                errors.add("scope.included operation is required: " + entry.path());
            }
            if (!seen.add(entry.path())) {
                errors.add("scope.included contains duplicate path: " + entry.path());
            }
        }
    }

    private static int nonBlankClauseCount(List<TaskContractProposal.ContractClause> values) {
        if (values == null) {
            return 0;
        }
        return (int) values.stream()
                .filter(value -> value != null && !isBlank(value.text()))
                .count();
    }

    private static void validateWorkspacePathRef(
            TaskContract.ScopeEntry ref,
            Path workspaceRoot,
            boolean inheritedCreate,
            List<String> errors
    ) {
        if (ref == null || isBlank(ref.path())) {
            errors.add("scope.included contains blank file ref");
            return;
        }
        if (containsMetadataNoise(ref.path())) {
            errors.add("scope.included contains ledger/context metadata: " + ref.path());
            return;
        }
        if (ref.path().startsWith("new:")) {
            errors.add("scope.included legacy new: prefix is not allowed: " + ref.path());
            return;
        }
        if (!TaskContractValidator.isCanonicalPath(ref.path())) {
            errors.add("scope.included is not a canonical relative file path: " + ref.path());
            return;
        }
        var path = workspaceRoot.resolve(ref.path()).normalize();
        if (!path.startsWith(workspaceRoot.toAbsolutePath().normalize()) && workspaceRoot.isAbsolute()) {
            errors.add("scope.included escapes workspace root: " + ref.path());
            return;
        }
        if (Files.isDirectory(path)) {
            errors.add("scope.included must be an exact file path, not a directory: " + ref.path());
            return;
        }
        // Resolution and this durable guard run synchronously against the same effective worktree.
        if (ref.operation() == TaskContract.ScopeOperation.MODIFY && !Files.exists(path)) {
            errors.add("scope.included MODIFY path does not exist: " + ref.path());
        }
        if (ref.operation() == TaskContract.ScopeOperation.CREATE && Files.exists(path) && !inheritedCreate) {
            errors.add("scope.included CREATE path already exists: " + ref.path());
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean containsMetadataNoise(String value) {
        if (value == null) {
            return false;
        }
        var lower = value.trim().toLowerCase();
        return lower.startsWith("workstream:")
                || lower.startsWith("builder node:")
                || lower.startsWith("target node:")
                || lower.startsWith("generated at:")
                || lower.startsWith("profile:")
                || lower.startsWith("total:")
                || lower.contains("context package")
                || lower.contains("context governance");
    }

    record RevisionDeltaError(String code, String targetId) {
        String message() {
            return targetId == null || targetId.isBlank() ? code : code + ": " + targetId;
        }
    }

    private record ClauseSnapshot(String section, String text) {}
}
