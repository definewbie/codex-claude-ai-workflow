package dev.dual.runtime.handler;

import dev.dual.core.contract.TaskContractValidator;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** Validates proposed edit paths, not business completeness or actual Git operations. */
final class PlanScopeValidator {
    private PlanScopeValidator() {
    }

    static List<String> shapeErrors(AgentResultEnvelope.Plan plan) {
        if (plan == null || plan.editFiles() == null || plan.editFiles().isEmpty()) {
            return List.of("PLAN_SCOPE_MISSING: result.plan.edit_files is required and must not be empty");
        }
        var errors = new ArrayList<String>();
        var paths = new HashSet<String>();
        for (var path : plan.editFiles()) {
            if (!TaskContractValidator.isCanonicalPath(path)) {
                errors.add("PLAN_SCOPE_PATH_INVALID: " + path);
            }
            if (!paths.add(path)) {
                errors.add("PLAN_SCOPE_PATH_DUPLICATE: " + path);
            }
        }
        return List.copyOf(errors);
    }

    static List<String> validate(Path root, String workstreamId, AgentResultEnvelope.Plan plan) {
        var errors = shapeErrors(plan);
        if (!errors.isEmpty()) {
            return errors;
        }
        var resolution = TaskContractProjectionVerifier.resolve(root, workstreamId);
        if (resolution.failure() != null || resolution.qualified().isEmpty()) {
            return List.of("PLAN_CONTRACT_NOT_QUALIFIED: " + (resolution.failure() == null
                    ? "approved contract is absent" : resolution.failure().reason()));
        }
        var included = resolution.qualified().orElseThrow().contract().scope().included();
        return plan.editFiles().stream()
                .filter(path -> included.stream().noneMatch(entry -> entry.path().equals(path)))
                .map(path -> "PLAN_SCOPE_NOT_AUTHORIZED: " + path)
                .toList();
    }
}
