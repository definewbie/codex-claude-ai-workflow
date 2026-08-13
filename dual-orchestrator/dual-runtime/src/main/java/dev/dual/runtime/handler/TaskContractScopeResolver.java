package dev.dual.runtime.handler;

import dev.dual.core.contract.TaskContract;
import dev.dual.core.contract.TaskContractValidator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** Resolves Agent-declared path intent into deterministic durable scope operations. */
final class TaskContractScopeResolver {
    Resolution resolve(List<AgentResultEnvelope.ProposalScopePath> declaredPaths, Path workspaceRoot) {
        var entries = new ArrayList<TaskContract.ScopeEntry>();
        var errors = new ArrayList<String>();
        var seen = new HashSet<String>();
        var root = workspaceRoot.toAbsolutePath().normalize();

        if (declaredPaths == null || declaredPaths.isEmpty()) {
            return new Resolution(List.of(), List.of("scope.included must not be empty"));
        }
        for (var declared : declaredPaths) {
            if (declared == null || declared.path() == null || declared.path().isBlank()) {
                errors.add("scope.included contains blank path");
                continue;
            }
            var relative = declared.path();
            if (!seen.add(relative)) {
                errors.add("scope.included contains duplicate path: " + relative);
                continue;
            }
            if (relative.startsWith("new:")) {
                errors.add("scope.included legacy new: prefix is not allowed: " + relative);
                continue;
            }
            if (!TaskContractValidator.isCanonicalPath(relative)) {
                errors.add("scope.included is not a canonical relative file path: " + relative);
                continue;
            }
            var path = root.resolve(relative).normalize();
            if (!path.startsWith(root)) {
                errors.add("scope.included escapes workspace root: " + relative);
                continue;
            }
            if (Files.isDirectory(path)) {
                errors.add("scope.included must be an exact file path, not a directory: " + relative);
                continue;
            }
            entries.add(new TaskContract.ScopeEntry(
                    relative,
                    Files.exists(path)
                            ? TaskContract.ScopeOperation.MODIFY
                            : TaskContract.ScopeOperation.CREATE));
        }
        return new Resolution(List.copyOf(entries), List.copyOf(errors));
    }

    record Resolution(List<TaskContract.ScopeEntry> entries, List<String> errors) {
        boolean valid() {
            return errors.isEmpty();
        }
    }
}
