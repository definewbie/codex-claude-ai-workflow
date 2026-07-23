package dev.dual.core.contract;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class TaskContractValidator {
    private static final Pattern FILE_REF = Pattern.compile("(?i).+\\.(md|txt|json|ya?ml|vue|ts|js|java|py|sh)$");

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
            if (contract.scope() != null && contract.scope().included().isEmpty()) {
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
        validateList("acceptanceCriteria", contract.acceptanceCriteria(), errors);
        validateList("testRequirements", contract.testRequirements(), errors);
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
            if (!FILE_REF.matcher(ref.path()).matches()) {
                errors.add("scope.included is not a supported file path: " + ref.path());
            }
            if (containsMetadataNoise(ref.path())) {
                errors.add("scope.included contains ledger/context metadata: " + ref.path());
            }
            if (!seen.add(normalized(ref.path()))) {
                errors.add("scope.included contains duplicate path: " + ref.path());
            }
        }
    }

    private static void validateScopeOverlap(TaskContract.Scope scope, List<String> errors) {
        var excluded = new HashSet<String>();
        for (var value : scope.excluded()) {
            excluded.add(normalized(value));
        }
        for (var included : scope.included()) {
            if (included != null && excluded.contains(normalized(included.path()))) {
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

    private static String normalized(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
