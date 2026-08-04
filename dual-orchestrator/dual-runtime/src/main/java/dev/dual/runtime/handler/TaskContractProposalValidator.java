package dev.dual.runtime.handler;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;

import dev.dual.core.contract.TaskContract;
import dev.dual.core.contract.TaskContractValidator;

final class TaskContractProposalValidator {
    List<String> validate(TaskContractProposal proposal, Path workspaceRoot) {
        var errors = new ArrayList<String>();
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
                    validateWorkspacePathRef(ref, workspaceRoot, errors);
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
        if (entries == null || entries.isEmpty()) {
            errors.add("scope.included must not be empty");
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
        if (ref.operation() == TaskContract.ScopeOperation.MODIFY && !Files.exists(path)) {
            errors.add("scope.included MODIFY path does not exist: " + ref.path());
        }
        if (ref.operation() == TaskContract.ScopeOperation.CREATE && Files.exists(path)) {
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
}
