package dev.dual.runtime.handler;

import dev.dual.core.contract.TaskContract;
import dev.dual.core.contract.TaskContractValidator;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** Checks declared file scope, never business relevance or actual Git changes. */
final class DesignScopeValidator {
    private DesignScopeValidator() {
    }

    static List<String> shapeErrors(AgentResultEnvelope.Design design) {
        if (design == null || design.plannedFiles() == null) {
            return List.of("DESIGN_SCOPE_MISSING: result.design.planned_files is required");
        }
        var errors = new ArrayList<String>();
        var paths = new HashSet<String>();
        for (var file : design.plannedFiles()) {
            if (file == null) {
                errors.add("DESIGN_SCOPE_ENTRY_INVALID: null entry");
                continue;
            }
            if (!TaskContractValidator.isCanonicalPath(file.path())) {
                errors.add("DESIGN_SCOPE_PATH_INVALID: " + file.path());
            }
            if (!paths.add(file.path())) {
                errors.add("DESIGN_SCOPE_PATH_DUPLICATE: " + file.path());
            }
            if (file.operation() == null) {
                errors.add("DESIGN_SCOPE_OPERATION_MISSING: " + file.path());
            }
            if (file.responsibility() == null || file.responsibility().isBlank()) {
                errors.add("DESIGN_SCOPE_RESPONSIBILITY_MISSING: " + file.path());
            }
        }
        return List.copyOf(errors);
    }

    static List<String> validate(Path root, String workstreamId, AgentResultEnvelope.Design design) {
        var errors = shapeErrors(design);
        if (!errors.isEmpty()) {
            return errors;
        }
        var resolution = TaskContractProjectionVerifier.resolve(root, workstreamId);
        if (resolution.failure() != null || resolution.qualified().isEmpty()) {
            return List.of("DESIGN_CONTRACT_NOT_QUALIFIED: " + (resolution.failure() == null
                    ? "approved contract is absent" : resolution.failure().reason()));
        }
        return scopeErrors(design, resolution.qualified().orElseThrow().contract());
    }

    private static List<String> scopeErrors(AgentResultEnvelope.Design design, TaskContract contract) {
        var errors = new ArrayList<String>();
        for (var file : design.plannedFiles()) {
            var authorized = contract.scope().included().stream()
                    .filter(entry -> entry.path().equals(file.path())).findFirst();
            if (authorized.isEmpty()) {
                errors.add("DESIGN_SCOPE_NOT_AUTHORIZED: " + file.path());
            } else if (authorized.get().operation() != file.operation()) {
                errors.add("DESIGN_SCOPE_OPERATION_MISMATCH: " + file.path());
            }
        }
        return List.copyOf(errors);
    }
}
