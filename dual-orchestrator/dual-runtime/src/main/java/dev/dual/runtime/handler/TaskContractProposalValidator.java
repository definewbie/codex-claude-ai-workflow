package dev.dual.runtime.handler;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

final class TaskContractProposalValidator {
    private static final Pattern FILE_REF = Pattern.compile("(?i).+\\.(md|txt|json|ya?ml|vue|ts|js|java|py|sh)$");

    List<String> validate(TaskContractProposal proposal, Path workspaceRoot) {
        var errors = new ArrayList<String>();
        if (!"task_contract_proposal".equals(proposal.artifactType())) {
            errors.add("artifact_type must be task_contract_proposal");
        }
        if (proposal.schemaVersion() != 1) {
            errors.add("schema_version must be 1");
        }
        if (isBlank(proposal.objective())) {
            errors.add("objective must not be blank");
        }
        if (proposal.scope() == null) {
            errors.add("scope must not be null");
        } else {
            validateList("scope.included", proposal.scope().included(), true, errors);
            validateList("scope.excluded", proposal.scope().excluded(), false, errors);
            if (proposal.scope().included() != null) {
                for (var ref : proposal.scope().included()) {
                    validateFileRef(ref, workspaceRoot, errors);
                }
            }
        }
        validateList("acceptance_criteria", proposal.acceptanceCriteria(), true, errors);
        validateList("constraints", proposal.constraints(), false, errors);
        validateList("non_goals", proposal.nonGoals(), false, errors);
        validateList("risk_areas", proposal.riskAreas(), false, errors);
        validateList("test_requirements", proposal.testRequirements(), false, errors);
        validateList("evidence_refs", proposal.evidenceRefs(), false, errors);
        if (proposal.openQuestions() != null && !proposal.openQuestions().isEmpty()) {
            errors.add("open_questions must be empty before canonical contract generation");
        }
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
        for (var value : values) {
            if (containsMetadataNoise(value)) {
                errors.add(name + " contains ledger/context metadata: " + value);
            }
        }
    }

    private static void validateFileRef(String ref, Path workspaceRoot, List<String> errors) {
        if (isBlank(ref)) {
            errors.add("scope.included contains blank file ref");
            return;
        }
        if (containsMetadataNoise(ref)) {
            errors.add("scope.included contains ledger/context metadata: " + ref);
            return;
        }
        if (!FILE_REF.matcher(ref).matches()) {
            errors.add("scope.included is not a supported file path: " + ref);
            return;
        }
        var path = workspaceRoot.resolve(ref).normalize();
        if (!path.startsWith(workspaceRoot.toAbsolutePath().normalize()) && workspaceRoot.isAbsolute()) {
            errors.add("scope.included escapes workspace root: " + ref);
            return;
        }
        if (!Files.exists(path) && !ref.startsWith("new:")) {
            errors.add("scope.included file does not exist and is not marked as new: " + ref);
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
