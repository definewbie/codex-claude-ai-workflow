package dev.dual.runtime;

import dev.dual.ledger.file.WorkflowFileLedger;
import dev.dual.ledger.file.WorkstreamPaths;

/**
 * Performs stale execution closure before moving the node instance to recovery.
 */
public final class StaleRunningRecovery {
    private final WorkflowFileLedger ledger;

    public StaleRunningRecovery(WorkflowFileLedger ledger) {
        this.ledger = java.util.Objects.requireNonNull(ledger);
    }

    public Result recover(WorkstreamPaths paths, String note) throws Exception {
        if (recoverRecorded(paths)) return new Result(0, 0);
        recoverUnpreparedOrLocal(paths, note);
        return new Result(0, 0);
    }

    public Result terminateAndRecover(WorkstreamPaths paths, String note) throws Exception {
        // Exact native session stop is a separate typed operation. Never kill a process selected from mutable run.json.
        return recover(paths, note);
    }

    private void recoverUnpreparedOrLocal(WorkstreamPaths paths, String note) throws Exception {
        var execution = ledger.inspect(paths, (scoped, files) -> {
            var current = scoped.loadCurrent(paths);
            if (current.activeNodeInstanceIds().size() != 1) throw new java.io.IOException("recovery requires one active occurrence");
            return scoped.latestNodeExecution(paths, scoped.loadNode(paths, current.activeNodeInstanceIds().getFirst()));
        });
        if (execution.isPresent() && "acpx.agent_run".equals(execution.get().handlerType())) {
            RecordedExecutionRecovery.recoverUnprepared(ledger, paths, execution.get());
        } else {
            ledger.recoverStaleRunningNode(paths, note);
        }
    }

    private boolean recoverRecorded(WorkstreamPaths paths) throws Exception {
        try (var local = ledger.writeSession(paths, "explicit local recovery", java.util.UUID.randomUUID().toString())) { local.commit(); }
        var recorded = RecordedExecutionRecovery.active(ledger, paths);
        if (recorded.isEmpty()) return false;
        if (recorded.get().status() != dev.dual.ledger.file.NodeExecutionStatus.RUNNING)
            throw new IllegalStateException("recorded execution already classified; use its recovery decision, not stale reclassification");
        if (dev.dual.runtime.handler.AgentRunCommitProtocol.executorOwnsInvocation(paths, recorded.get()))
            throw new IllegalStateException("original executor still owns the invocation");
        RecordedExecutionRecovery.recover(ledger, paths, recorded.get());
        return true;
    }

    public record Result(int terminatedProcessTrees, int reconciledRuns) {
    }

}
