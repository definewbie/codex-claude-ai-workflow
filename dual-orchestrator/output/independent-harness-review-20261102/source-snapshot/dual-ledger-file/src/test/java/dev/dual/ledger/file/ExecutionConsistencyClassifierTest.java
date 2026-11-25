package dev.dual.ledger.file;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExecutionConsistencyClassifierTest {
    @Test
    void marksStaleLegacyRunningWhenTypedAttemptIsTerminal() {
        var run = Map.<String, Object>of(
                "status", "running",
                "executionAttempt", Map.of(
                        "status", "STALLED",
                        "processStatus", "PROCESS_STALLED",
                        "failureSignal", "AGENT_PROCESS_STALLED"
                ),
                "executionOutcome", Map.of(
                        "status", "RUNTIME_FAILED",
                        "primaryReason", "AGENT_PROCESS_STALLED",
                        "nodeMaySucceed", false
                )
        );

        assertEquals("stale_legacy_running", ExecutionConsistencyClassifier.classify(run));
    }

    @Test
    void marksAttemptOutcomeConflictBeforeLegacyStatusConflict() {
        var run = Map.<String, Object>of(
                "status", "running",
                "executionAttempt", Map.of("status", "RECOVERABLE_FAILED"),
                "executionOutcome", Map.of(
                        "status", "SUCCESS",
                        "primaryReason", "completed",
                        "nodeMaySucceed", true
                )
        );

        assertEquals("attempt_outcome_conflict", ExecutionConsistencyClassifier.classify(run));
    }
}
