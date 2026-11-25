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
        if (plan.files() == null || plan.files().isEmpty()) errors.add("PLAN_FILES_MISSING");
        else {
            var declared = new HashSet<String>();
            for (var file : plan.files()) {
                if (file == null || !TaskContractValidator.isCanonicalPath(file.path()) || file.operation() == null)
                    errors.add("PLAN_FILE_INVALID");
                else if (!declared.add(file.path())) errors.add("PLAN_FILE_DUPLICATE: " + file.path());
            }
            if (!declared.equals(paths)) errors.add("PLAN_EDIT_FILES_MISMATCH");
        }
        if (plan.verificationBindings() == null || plan.verificationBindings().isEmpty())
            errors.add("PLAN_VERIFICATION_BINDINGS_MISSING");
        else for (var binding : plan.verificationBindings())
            errors.addAll(TaskContractValidator.verificationEvidenceErrors(binding));
        if (plan.authorityRefs() == null) errors.add("PLAN_AUTHORITY_REFS_MISSING");
        return List.copyOf(errors);
    }

    static List<String> validate(dev.dual.ledger.file.WorkflowFileLedger ledger, Path root, String workstreamId, AgentResultEnvelope.Plan plan) {
        var errors = shapeErrors(plan);
        if (!errors.isEmpty()) {
            return errors;
        }
        var resolution = TaskContractProjectionVerifier.resolve(ledger, root, workstreamId, dev.dual.core.contract.TaskContract.Phase.GOAL);
        if (resolution.failure() != null || resolution.qualified().isEmpty()) {
            return List.of("PLAN_CONTRACT_NOT_QUALIFIED: " + (resolution.failure() == null
                    ? "approved contract is absent" : resolution.failure().reason()));
        }
        return DesignScopeValidator.proposedScopeErrors(plan.files(), resolution.qualified().orElseThrow().contract());
    }
}
