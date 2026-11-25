package dev.dual.core.contract;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;

public final class TaskContractValidator {
    private TaskContractValidator() {}

    public static List<String> validate(TaskContract contract) {
        List<String> errors = new ArrayList<>();

        // objective must not be blank (compact constructor already enforces, but defensive)
        if (contract.objective() == null || contract.objective().isBlank()) {
            errors.add("objective must not be blank");
        }

        // scope must not be null
        if (contract.scope() == null) {
            errors.add("scope must not be null");
        } else {
            validateIncludedRefs(contract.scope().included(), errors);
            validateList("scope.excluded", contract.scope().excluded(), errors);
            validateScopeOverlap(contract.scope(), errors);
        }

        // READY_FOR_REVIEW requires non-empty acceptanceCriteria
        if (contract.status() == ContractStatus.READY_FOR_REVIEW) {
            if (contract.acceptanceCriteria().isEmpty()) {
                errors.add("READY_FOR_REVIEW requires non-empty acceptanceCriteria");
            }
            if (contract.phase() == TaskContract.Phase.EXECUTION
                    && contract.scope() != null && contract.scope().included().isEmpty()) {
                errors.add("READY_FOR_REVIEW requires non-empty scope.included");
            }
            if (contract.acceptanceCriteria().size() < 2) {
                errors.add("READY_FOR_REVIEW requires at least two acceptance criteria");
            }
            if (contract.testRequirements().isEmpty()) {
                errors.add("READY_FOR_REVIEW requires non-empty testRequirements");
            }
            if (contract.constraints().isEmpty() && contract.nonGoals().isEmpty()) {
                errors.add("READY_FOR_REVIEW requires constraints or nonGoals to define boundaries");
            }
        }
        var clauseIds = new HashSet<String>();
        validateClauses("acceptanceCriteria", contract.acceptanceCriteria(), clauseIds, errors);
        validateClauses("testRequirements", contract.testRequirements(), clauseIds, errors);
        validatePhase(contract, errors);
        validateList("constraints", contract.constraints(), errors);
        validateList("nonGoals", contract.nonGoals(), errors);
        validateList("riskAreas", contract.riskAreas(), errors);

        // NEEDS_CLARIFICATION requires non-empty humanQuestions
        if (contract.status() == ContractStatus.NEEDS_CLARIFICATION) {
            if (contract.humanQuestions().isEmpty()) {
                errors.add("NEEDS_CLARIFICATION requires non-empty humanQuestions");
            }
        }

        return errors;
    }

    private static void validatePhase(TaskContract contract, List<String> errors) {
        var source = contract.source();
        if (source == null || source.proposal() == null || source.goalApproval() == null
                || source.rawRequestRef() == null || source.rawRequestRef().isBlank()
                || source.createdBy() == null || source.createdBy().isBlank()) {
            errors.add("source must bind the proposal and actual goal approval");
        }
        if (contract.phase() == TaskContract.Phase.GOAL) {
            if (contract.scope() != null && !contract.scope().included().isEmpty())
                errors.add("GOAL cannot grant product writes through scope.included");
            if (contract.goalContract() != null || !contract.reviewedCandidates().isEmpty()
                    || !contract.verificationBindings().isEmpty() || !contract.nonessentialOperationDeclarations().isEmpty())
                errors.add("GOAL cannot masquerade as an EXECUTION contract");
            return;
        }
        if (contract.goalContract() == null) errors.add("EXECUTION requires the approved GOAL reference");
        if (contract.reviewedCandidates().size() != 2
                || new HashSet<>(contract.reviewedCandidates()).size() != 2)
            errors.add("EXECUTION requires distinct reviewed design and plan references");
        var required = new HashSet<String>();
        for (var clause : contract.acceptanceCriteria()) if (clause != null) required.add(clause.id());
        var covered = new HashSet<String>();
        for (var binding : contract.verificationBindings()) {
            errors.addAll(verificationEvidenceErrors(binding));
            if (binding == null) continue;
            if (!required.contains(binding.criterionId())) errors.add("verification refers to an unapproved criterion");
            if (!covered.add(binding.criterionId())) errors.add("duplicate verification criterion: " + binding.criterionId());
            if (binding.method() == null || binding.method().isBlank()
                    || binding.detailOwner() == null || binding.detailOwner().isBlank())
                errors.add("verification method and detail_owner are required");
            if (binding.environmentRefs().isEmpty() || binding.feasibilityRefs().isEmpty())
                errors.add("verification requires environment and feasibility evidence references");
            if (binding.executor() == null || binding.evidenceKind() == null
                    || (binding.executor() == TaskContract.Executor.AGENT
                        && binding.evidenceKind() != TaskContract.EvidenceKind.PROGRAM_TOOL_EXECUTION_AND_ASSERTIONS)
                    || (binding.executor() == TaskContract.Executor.OPERATOR
                        && binding.evidenceKind() != TaskContract.EvidenceKind.OPERATOR_DECLARATION_WITH_MATERIAL))
                errors.add("verification executor and evidence kind must agree");
        }
        if (!covered.equals(required)) errors.add("every approved acceptance criterion requires a verification exit");
        var declarationIds = new HashSet<String>();
        for (var declaration : contract.nonessentialOperationDeclarations()) {
            if (declaration.id() == null || declaration.id().isBlank() || !declarationIds.add(declaration.id())
                    || declaration.nodeDefinitionId() == null || declaration.nodeDefinitionId().isBlank()
                    || declaration.nodeContractRef() == null
                    || !List.of("read", "search").contains(declaration.operationKind())
                    || declaration.operationParametersSha256() == null
                    || !declaration.operationParametersSha256().matches("[0-9a-f]{64}")
                    || declaration.rationale() == null || declaration.rationale().isBlank()
                    || !"permission_nonessential.v1".equals(declaration.ruleIdentity())
                    || declaration.criterionIds().isEmpty()
                    || !required.containsAll(declaration.criterionIds())
                    || new HashSet<>(declaration.criterionIds()).size() != declaration.criterionIds().size())
                errors.add("invalid nonessential operation declaration: " + declaration.id());
        }

    }

    /** Evidence requirements are approved with the plan, never inferred from observed results. */
    public static List<String> verificationEvidenceErrors(TaskContract.VerificationBinding binding) {
        if (binding == null) return List.of("verification binding is required");
        var errors = new ArrayList<String>();
        if (binding.actualExitRequired() == null)
            errors.add("actual_exit_required must be explicitly declared for " + binding.criterionId());
        if (binding.expectedExitCode() != null && !Boolean.TRUE.equals(binding.actualExitRequired()))
            errors.add("expected_exit_code requires actual_exit_required=true for " + binding.criterionId());
        if (Boolean.TRUE.equals(binding.actualExitRequired())
                && binding.evidenceKind() != TaskContract.EvidenceKind.PROGRAM_TOOL_EXECUTION_AND_ASSERTIONS)
            errors.add("actual process exit requires program execution evidence for " + binding.criterionId());
        return List.copyOf(errors);
    }

    private static void validateClauses(String name, List<TaskContract.Clause> clauses,
                                        java.util.Set<String> allIds, List<String> errors) {
        var texts = new ArrayList<String>();
        for (var clause : clauses) {
            if (clause == null || clause.id() == null || clause.id().isBlank()
                    || !clause.id().equals(clause.id().trim()) || !allIds.add(clause.id())) {
                errors.add(name + " contains missing or duplicate clause id");
            }
            texts.add(clause == null ? null : clause.text());
        }
        validateList(name, texts, errors);
    }

    private static void validateIncludedRefs(List<TaskContract.ScopeEntry> refs, List<String> errors) {
        var seen = new HashSet<String>();
        for (var ref : refs) {
            if (ref == null) {
                errors.add("scope.included contains null entry");
                continue;
            }
            if (ref.path() == null || ref.path().isBlank()) {
                errors.add("scope.included contains blank path");
                continue;
            }
            if (ref.operation() == null) {
                errors.add("scope.included operation is required: " + ref.path());
            }
            if (ref.path().startsWith("new:")) {
                errors.add("scope.included legacy new: prefix is not allowed: " + ref.path());
            }
            if (!isCanonicalPath(ref.path())) {
                errors.add("scope.included is not a canonical relative file path: " + ref.path());
            }
            if (containsMetadataNoise(ref.path())) {
                errors.add("scope.included contains ledger/context metadata: " + ref.path());
            }
            if (!seen.add(ref.path())) {
                errors.add("scope.included contains duplicate path: " + ref.path());
            }
        }
    }

    private static void validateScopeOverlap(TaskContract.Scope scope, List<String> errors) {
        var excluded = new HashSet<String>();
        for (var value : scope.excluded()) {
            excluded.add(value == null ? "" : value.trim());
        }
        for (var included : scope.included()) {
            if (included != null && included.path() != null
                    && excluded.contains(included.path().trim())) {
                errors.add("scope.included overlaps scope.excluded: " + included.path());
            }
        }
    }

    private static void validateList(String name, List<String> values, List<String> errors) {
        var seen = new HashSet<String>();
        for (var value : values) {
            if (value == null || value.isBlank()) {
                errors.add(name + " contains blank item");
                continue;
            }
            if (containsMetadataNoise(value)) {
                errors.add(name + " contains ledger/context metadata: " + value);
            }
            var normalized = normalized(value);
            if (!seen.add(normalized)) {
                errors.add(name + " contains duplicate item: " + value);
            }
        }
    }

    private static boolean containsMetadataNoise(String value) {
        var lower = value.trim().toLowerCase(Locale.ROOT);
        return lower.startsWith("workstream:")
                || lower.startsWith("builder node:")
                || lower.startsWith("target node:")
                || lower.startsWith("generated at:")
                || lower.startsWith("profile:")
                || lower.startsWith("total:")
                || lower.contains("context package")
                || lower.contains("context governance");
    }

    /**
     * Strict canonical path check for scope.included entries. Uses canonical
     * representation equality: the input must equal its own normalized Path form.
     * Rejects non-canonical input rather than silently normalizing — B2 comparison
     * requires byte-identical path identity between TaskContract scope and Git
     * changed-files.
     *
     * Accepted: {@code src/a.py}, {@code Dockerfile}, {@code docs/API contract.md}
     * Rejected: {@code ./src/a.py}, {@code src/./a.py}, {@code src//a.py},
     *           {@code src/a/} (trailing slash), {@code src/a.py } (whitespace),
     *           absolute, traversal, backslash.
     */
    public static boolean isCanonicalPath(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        if (value.indexOf('\0') >= 0 || value.contains("\\")) {
            return false;
        }
        if (value.trim().length() != value.length()) {
            return false;
        }
        if (value.endsWith("/") || value.matches("^[A-Za-z]:/.*")) {
            return false;
        }
        try {
            var path = Path.of(value);
            if (path.isAbsolute()) {
                return false;
            }
            for (var segment : path) {
                var name = segment.toString();
                if ("..".equals(name) || ".".equals(name)
                        || ".agent".equals(name) || ".git".equals(name)) {
                    return false;
                }
            }
            var canonical = new StringJoiner("/");
            for (var segment : path.normalize()) {
                canonical.add(segment.toString());
            }
            return path.getNameCount() > 0 && canonical.toString().equals(value);
        } catch (InvalidPathException ignored) {
            return false;
        }
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
