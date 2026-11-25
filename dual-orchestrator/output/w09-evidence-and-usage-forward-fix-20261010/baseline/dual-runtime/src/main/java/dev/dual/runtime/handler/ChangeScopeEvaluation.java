package dev.dual.runtime.handler;

import dev.dual.core.contract.TaskContract;
import dev.dual.core.contract.TaskContractValidator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;

record ChangeScopeEvaluation(
        int schemaVersion,
        Status status,
        List<ActualChange> actualChanges,
        List<String> matchedPaths,
        List<TaskContract.ScopeEntry> missingDeclared,
        List<Violation> violations,
        String error
) {
    ChangeScopeEvaluation {
        actualChanges = List.copyOf(actualChanges == null ? List.of() : actualChanges);
        matchedPaths = List.copyOf(matchedPaths == null ? List.of() : matchedPaths);
        missingDeclared = List.copyOf(missingDeclared == null ? List.of() : missingDeclared);
        violations = List.copyOf(violations == null ? List.of() : violations);
    }

    boolean passed() {
        return status == Status.PASS;
    }

    static ChangeScopeEvaluation unavailable(String error) {
        return new ChangeScopeEvaluation(1, Status.UNAVAILABLE, List.of(), List.of(), List.of(), List.of(), error);
    }

    enum Status {
        PASS,
        FAIL,
        UNAVAILABLE
    }

    enum ActualOperation {
        CREATE,
        MODIFY,
        DELETE,
        RENAME,
        UNKNOWN
    }

    record ActualChange(
            String path,
            ActualOperation operation,
            String previousPath,
            String rawStatus
    ) {}

    record Violation(
            String code,
            String path,
            TaskContract.ScopeOperation declaredOperation,
            ActualOperation actualOperation,
            String previousPath
    ) {}
}

final class ChangeScopeEvaluator {
    private ChangeScopeEvaluator() {}

    static List<ChangeScopeEvaluation.ActualChange> parseNameStatus(String nameStatus) {
        return parseNameStatus(0, nameStatus);
    }

    static List<ChangeScopeEvaluation.ActualChange> parseNameStatus(int exitCode, String nameStatus) {
        if (exitCode != 0) {
            throw new IllegalArgumentException("git name-status command failed with exit code " + exitCode);
        }
        if (nameStatus == null || nameStatus.isBlank()) {
            return List.of();
        }
        var fields = nameStatus.split(String.valueOf('\0'), -1);
        if (fields.length < 2 || !fields[fields.length - 1].isEmpty()) {
            throw new IllegalArgumentException("git name-status output is not NUL terminated");
        }
        var changes = new ArrayList<ChangeScopeEvaluation.ActualChange>();
        for (var index = 0; index < fields.length - 1;) {
            var rawStatus = fields[index++].trim();
            if (rawStatus.isEmpty()) {
                throw new IllegalArgumentException("git name-status output contains an empty status");
            }
            var code = rawStatus.isEmpty() ? '\0' : rawStatus.charAt(0);
            var operation = switch (code) {
                case 'A' -> ChangeScopeEvaluation.ActualOperation.CREATE;
                case 'M' -> ChangeScopeEvaluation.ActualOperation.MODIFY;
                case 'D' -> ChangeScopeEvaluation.ActualOperation.DELETE;
                case 'R' -> ChangeScopeEvaluation.ActualOperation.RENAME;
                default -> ChangeScopeEvaluation.ActualOperation.UNKNOWN;
            };
            var twoPathStatus = code == 'R' || code == 'C';
            var requiredFields = twoPathStatus ? 2 : 1;
            if (index + requiredFields > fields.length - 1) {
                throw new IllegalArgumentException("git name-status output is truncated for status " + rawStatus);
            }
            var previousPath = twoPathStatus ? fields[index++] : null;
            var path = fields[index++];
            if (path.isEmpty() || (twoPathStatus && previousPath.isEmpty())) {
                throw new IllegalArgumentException("git name-status output contains an empty path");
            }
            changes.add(new ChangeScopeEvaluation.ActualChange(path, operation, previousPath, rawStatus));
        }
        return List.copyOf(changes);
    }

    static ChangeScopeEvaluation evaluate(
            TaskContract contract,
            List<ChangeScopeEvaluation.ActualChange> actualChanges
    ) {
        var declaredByPath = new LinkedHashMap<String, TaskContract.ScopeEntry>();
        for (var entry : contract.scope().included()) {
            declaredByPath.put(entry.path(), entry);
        }

        var matchedPaths = new ArrayList<String>();
        var observedPaths = new LinkedHashSet<String>();
        var violations = new ArrayList<ChangeScopeEvaluation.Violation>();
        for (var actual : actualChanges) {
            observedPaths.add(actual.path());
            var declared = declaredByPath.get(actual.path());
            if (!TaskContractValidator.isCanonicalPath(actual.path())) {
                violations.add(violation("INVALID_ACTUAL_PATH", actual, declared));
                continue;
            }
            if (actual.operation() == ChangeScopeEvaluation.ActualOperation.DELETE
                    || actual.operation() == ChangeScopeEvaluation.ActualOperation.RENAME
                    || actual.operation() == ChangeScopeEvaluation.ActualOperation.UNKNOWN) {
                violations.add(violation("UNSUPPORTED_ACTUAL_OPERATION", actual, declared));
                continue;
            }
            if (declared == null) {
                violations.add(violation("UNEXPECTED_ACTUAL_CHANGE", actual, null));
                continue;
            }
            if (expectedActualOperation(declared.operation()) != actual.operation()) {
                violations.add(violation("OPERATION_MISMATCH", actual, declared));
                continue;
            }
            matchedPaths.add(actual.path());
        }

        var missingDeclared = contract.scope().included().stream()
                .filter(entry -> !observedPaths.contains(entry.path()))
                .toList();
        return new ChangeScopeEvaluation(
                1,
                violations.isEmpty() ? ChangeScopeEvaluation.Status.PASS : ChangeScopeEvaluation.Status.FAIL,
                actualChanges,
                matchedPaths,
                missingDeclared,
                violations,
                null
        );
    }

    private static ChangeScopeEvaluation.ActualOperation expectedActualOperation(
            TaskContract.ScopeOperation operation
    ) {
        return switch (operation) {
            case CREATE -> ChangeScopeEvaluation.ActualOperation.CREATE;
            case MODIFY -> ChangeScopeEvaluation.ActualOperation.MODIFY;
        };
    }

    private static ChangeScopeEvaluation.Violation violation(
            String code,
            ChangeScopeEvaluation.ActualChange actual,
            TaskContract.ScopeEntry declared
    ) {
        return new ChangeScopeEvaluation.Violation(
                code,
                actual.path(),
                declared == null ? null : declared.operation(),
                actual.operation(),
                actual.previousPath()
        );
    }
}
