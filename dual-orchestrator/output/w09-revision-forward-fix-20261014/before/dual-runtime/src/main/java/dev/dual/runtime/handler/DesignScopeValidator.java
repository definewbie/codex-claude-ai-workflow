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

    static List<String> validate(dev.dual.ledger.file.WorkflowFileLedger ledger, Path root, String workstreamId, AgentResultEnvelope.Design design) {
        var errors = shapeErrors(design);
        if (!errors.isEmpty()) {
            return errors;
        }
        var resolution = TaskContractProjectionVerifier.resolve(ledger, root, workstreamId, dev.dual.core.contract.TaskContract.Phase.GOAL);
        if (resolution.failure() != null || resolution.qualified().isEmpty()) {
            return List.of("DESIGN_CONTRACT_NOT_QUALIFIED: " + (resolution.failure() == null
                    ? "approved contract is absent" : resolution.failure().reason()));
        }
        return scopeErrors(design, resolution.qualified().orElseThrow().contract());
    }

    static List<String> scopeErrors(AgentResultEnvelope.Design design, TaskContract contract) {
        return proposedScopeErrors(design.plannedFiles().stream()
                .map(file -> new TaskContract.ScopeEntry(file.path(), file.operation())).toList(), contract);
    }

    static List<String> proposedScopeErrors(List<TaskContract.ScopeEntry> files, TaskContract contract) {
        var errors = new ArrayList<String>();
        for (var file : files) {
            if (file == null || !TaskContractValidator.isCanonicalPath(file.path()) || file.operation() == null) {
                errors.add("PROPOSED_SCOPE_INVALID");
                continue;
            }
            if (file.path().equals(".agent") || file.path().startsWith(".agent/")
                    || file.path().equals(".git") || file.path().startsWith(".git/")
                    || contract.scope().excluded().stream().anyMatch(excluded -> file.path().equals(excluded)
                        || (TaskContractValidator.isCanonicalPath(excluded) && file.path().startsWith(excluded + "/")))) {
                errors.add("PROPOSED_SCOPE_EXCLUDED: " + file.path());
            }
            if (contract.phase() == TaskContract.Phase.EXECUTION
                    && !contract.scope().included().contains(file)) {
                errors.add("PROPOSED_SCOPE_REQUIRES_NEW_APPROVAL: " + file.path());
            }
        }
        return List.copyOf(errors);
    }
}
