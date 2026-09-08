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
        return resolve(declaredPaths, workspaceRoot, null);
    }

    Resolution resolve(List<AgentResultEnvelope.ProposalScopePath> declaredPaths, Path workspaceRoot,
                       String originalBaseCommit) {
        var entries = new ArrayList<TaskContract.ScopeEntry>();
        var errors = new ArrayList<String>();
        var seen = new HashSet<String>();
        var root = workspaceRoot.toAbsolutePath().normalize();
        java.util.Set<String> originalPaths = null;
        if (originalBaseCommit != null) {
            try {
                originalPaths = originalPaths(root, originalBaseCommit);
            } catch (java.io.IOException failure) {
                return new Resolution(List.of(), List.of("original workspace base is invalid: " + failure.getMessage()));
            }
        }

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
            if (originalPaths != null && Files.exists(path) && !originalPaths.contains(relative)) {
                errors.add("scope addition already exists but is absent from original workspace base: " + relative);
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

    static java.util.Set<String> originalPaths(Path root, String commit) throws java.io.IOException {
        if (commit == null || !commit.matches("[0-9a-f]{40}|[0-9a-f]{64}")) {
            throw new java.io.IOException("full base commit identity is required");
        }
        var process = new ProcessBuilder("git", "ls-tree", "-r", "--name-only", "-z", commit)
                .directory(root.toFile()).start();
        var bytes = process.getInputStream().readAllBytes();
        var stderr = process.getErrorStream().readAllBytes();
        try {
            if (process.waitFor() != 0) {
                throw new java.io.IOException("cannot read base tree: "
                        + new String(stderr, java.nio.charset.StandardCharsets.UTF_8));
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new java.io.IOException("base tree read interrupted", interrupted);
        }
        var paths = new HashSet<String>();
        var text = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        for (var path : text.split(String.valueOf((char) 0))) {
            if (!path.isEmpty()) paths.add(path);
        }
        return java.util.Set.copyOf(paths);
    }

    record Resolution(List<TaskContract.ScopeEntry> entries, List<String> errors) {
        boolean valid() {
            return errors.isEmpty();
        }
    }
}
