package dev.dual.runtime.handler;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
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
        validateList("acceptance_criteria", proposal.acceptanceCriteria(), true, errors);
        if (proposal.acceptanceCriteria() != null && nonBlankCount(proposal.acceptanceCriteria()) < 2) {
            errors.add("acceptance_criteria requires at least two observable items");
        }
        validateList("constraints", proposal.constraints(), false, errors);
        validateList("non_goals", proposal.nonGoals(), false, errors);
        if (nonBlankCount(proposal.constraints()) == 0 && nonBlankCount(proposal.nonGoals()) == 0) {
            errors.add("constraints or non_goals must define task boundaries");
        }
        validateList("risk_areas", proposal.riskAreas(), false, errors);
        validateList("test_requirements", proposal.testRequirements(), true, errors);
        validateList("evidence_refs", proposal.evidenceRefs(), false, errors);
        validateList("open_questions", proposal.openQuestions(), false, errors);
        return errors;
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

    private static int nonBlankCount(List<String> values) {
        if (values == null) {
            return 0;
        }
        return (int) values.stream().filter(value -> !isBlank(value)).count();
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
