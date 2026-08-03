package dev.dual.runtime;

import dev.dual.ledger.file.WorkflowFileLedger;
import dev.dual.ledger.file.WorkstreamPaths;
import dev.dual.runtime.handler.AgentExecutionAttemptReconciler;
import dev.dual.runtime.handler.AgentExecutionAttemptTimeoutPolicy;

/**
 * Performs stale execution closure before moving the node instance to recovery.
 */
public final class StaleRunningRecovery {
    private final WorkflowFileLedger ledger;
    private final AgentExecutionAttemptReconciler reconciler;

    public StaleRunningRecovery(WorkflowFileLedger ledger) {
        this(ledger, new AgentExecutionAttemptReconciler(new AgentExecutionAttemptTimeoutPolicy().timeout()));
    }

    StaleRunningRecovery(WorkflowFileLedger ledger, AgentExecutionAttemptReconciler reconciler) {
        if (ledger == null || reconciler == null) {
            throw new IllegalArgumentException("ledger and reconciler are required");
        }
        this.ledger = ledger;
        this.reconciler = reconciler;
    }

    public Result recover(WorkstreamPaths paths, String note) throws Exception {
        var reconciled = reconciler.reconcileRuns(paths.runsDir());
        assertExecutionsQuiescent(paths);
        ledger.recoverStaleRunningNode(paths, note);
        return new Result(0, reconciled);
    }

    public Result terminateAndRecover(WorkstreamPaths paths, String note) throws Exception {
        var terminated = reconciler.terminateInFlightProcesses(paths.runsDir());
        var reconciled = reconciler.reconcileRuns(paths.runsDir());
        assertExecutionsQuiescent(paths);
        ledger.recoverStaleRunningNode(paths, note);
        return new Result(terminated, reconciled);
    }

    public record Result(int terminatedProcessTrees, int reconciledRuns) {
    }

    private void assertExecutionsQuiescent(WorkstreamPaths paths) throws Exception {
        var inFlight = reconciler.countInFlightRuns(paths.runsDir());
        if (inFlight > 0) {
            throw new IllegalStateException(
                    "stale recovery refused because " + inFlight + " agent run(s) remain in-flight");
        }
    }
}
