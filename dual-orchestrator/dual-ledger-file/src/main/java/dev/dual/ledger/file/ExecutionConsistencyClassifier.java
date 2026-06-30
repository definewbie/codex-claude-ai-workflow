package dev.dual.ledger.file;

import java.util.Map;

/**
 * Shared read-model classifier for legacy run status vs typed execution facts.
 *
 * <p>This is projection semantics, not a workflow transition owner. It exists so
 * CLI/API/report projections do not each invent their own consistency rules.</p>
 */
public final class ExecutionConsistencyClassifier {
    private ExecutionConsistencyClassifier() {
    }

    public static String classify(Map<String, Object> run) {
        var attempt = map(run.get("executionAttempt"));
        var outcome = map(run.get("executionOutcome"));
        if (attempt.isEmpty() && outcome.isEmpty()) {
            return "missing_attempt_and_outcome";
        }
        if (attempt.isEmpty()) {
            return "missing_attempt";
        }
        if (outcome.isEmpty()) {
            return "missing_outcome";
        }
        var legacyStatus = text(run.get("status"));
        var attemptStatus = text(attempt.get("status"));
        var outcomeStatus = text(outcome.get("status"));
        var nodeMaySucceed = Boolean.TRUE.equals(outcome.get("nodeMaySucceed"));
        if (nodeMaySucceed && !"COMPLETED".equals(attemptStatus)) {
            return "attempt_outcome_conflict";
        }
        if (!nodeMaySucceed && "COMPLETED".equals(attemptStatus)) {
            return "attempt_outcome_conflict";
        }
        if ("running".equals(legacyStatus) && isTerminalAttemptStatus(attemptStatus)) {
            return "stale_legacy_running";
        }
        if (nodeMaySucceed && !"completed".equals(legacyStatus)) {
            return "legacy_outcome_conflict";
        }
        if (!nodeMaySucceed && "completed".equals(legacyStatus)) {
            return "legacy_outcome_conflict";
        }
        return outcomeStatus.isBlank() ? "missing_outcome_status" : "consistent";
    }

    private static boolean isTerminalAttemptStatus(String value) {
        return !value.isBlank() && !"RUNNING".equals(value);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
