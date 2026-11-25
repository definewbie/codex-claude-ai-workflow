package dev.dual.runtime.handler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** Enforces patch-wide uniqueness for mutation targets across review obligations. */
final class TaskContractRevisionPatchTargetValidator {
    private TaskContractRevisionPatchTargetValidator() {
    }

    static List<String> validate(AgentResultEnvelope.TaskContractRevisionPatchResult patch) {
        if (patch == null || patch.obligationPatches() == null) {
            return List.of();
        }
        var errors = new ArrayList<String>();
        var clauseTargets = new HashSet<String>();
        var scopeTargets = new HashSet<String>();
        var evidenceTargets = new HashSet<String>();
        for (var obligationPatch : patch.obligationPatches()) {
            if (obligationPatch == null) {
                continue;
            }
            for (var change : safe(obligationPatch.clauseChanges())) {
                if (change != null && change.id() != null
                        && !clauseTargets.add(change.id().trim())) {
                    errors.add("DUPLICATE_CLAUSE_PATCH_TARGET: " + change.id().trim());
                }
            }
            for (var entry : safe(obligationPatch.scopeAdditions())) {
                if (entry != null && !scopeTargets.add(entry.path())) {
                    errors.add("DUPLICATE_SCOPE_PATCH_TARGET: " + entry.path());
                }
            }
            for (var ref : safe(obligationPatch.evidenceRefAdditions())) {
                if (ref != null && !evidenceTargets.add(ref)) {
                    errors.add("DUPLICATE_EVIDENCE_PATCH_TARGET: " + ref);
                }
            }
        }
        return List.copyOf(errors);
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }
}
