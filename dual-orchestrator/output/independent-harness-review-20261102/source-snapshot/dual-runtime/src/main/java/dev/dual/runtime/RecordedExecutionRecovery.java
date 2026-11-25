package dev.dual.runtime;

import dev.dual.core.workflow.*;
import dev.dual.ledger.file.*;
import dev.dual.runtime.handler.AgentRunCommitProtocol;
import dev.dual.runtime.handler.ArtifactContractValidator;
import java.util.*;
import java.io.IOException;

/** Shared inline, worker and explicit-recovery reader of R01/R02/R03. Never invokes a provider. */
public final class RecordedExecutionRecovery {
    private RecordedExecutionRecovery() {}

    public static Optional<NodeExecutionDocument> active(WorkflowFileLedger ledger, WorkstreamPaths paths) throws IOException {
        return ledger.inspect(paths, (scoped, files) -> {
            var current = scoped.loadCurrent(paths);
            if (current.status().isTerminal()) return Optional.empty();
            for (var id : current.activeNodeInstanceIds()) {
                var execution = scoped.latestNodeExecution(paths, scoped.loadNode(paths, id));
                if (execution.isPresent() && (AgentRunCommitProtocol.hasPrepared(execution.get())
                        || AgentRunCommitProtocol.hasInputRejection(paths, execution.get()))) return execution;
            }
            return Optional.empty();
        });
    }

    public static WorkflowRuntimeResult recoverUnprepared(WorkflowFileLedger ledger, WorkstreamPaths paths,
            NodeExecutionDocument claim) throws Exception {
        if (AgentRunCommitProtocol.hasPrepared(claim)) return recover(ledger, paths, claim);
        if (!"acpx.agent_run".equals(claim.handlerType())) throw new IOException("not a native execution claim");
        if (AgentRunCommitProtocol.hasInputRejection(paths, claim)) return recover(ledger, paths, claim);
        try (var write = ledger.writeSession(paths, "recover interrupted preparation", claim.nodeExecutionId())) {
            var scoped = write.ledger();
            var actual = scoped.verifyExecutionClaim(paths, claim);
            if (AgentRunCommitProtocol.hasPrepared(actual)) throw new IOException("preparation was bound concurrently");
            if (AgentRunCommitProtocol.executorAlive(actual)) throw new IOException("original executor is still active");
            // R01 was never bound, so no productive native phase could acquire dispatch eligibility.
            var result = dev.dual.runtime.handler.AgentExecutionResultFacts.inputPreparationFailed(
                    "PREPARATION_INTERRUPTED", "PREPARATION_INTERRUPTED", List.of("Executor stopped before prepared input binding"));
            scoped.markAgentTaskFinished(paths, scoped.readAgentTask(paths, actual.agentTaskId()).orElseThrow(), result);
            scoped.finishNodeExecution(paths, actual, result);
            scoped.completeAndRoute(paths, scoped.loadDefinition(paths), actual.nodeInstanceId(), result.status(),
                    result.outcome(), result.outputRefs(), result.result());
            write.commit();
        }
        var current = ledger.loadCurrent(paths);
        return new WorkflowRuntimeResult(paths.workstreamId().value(), current.status(), claim.nodeDefinitionId(),
                "preparation was interrupted before external eligibility; explicit retry may prepare a new attempt");
    }

    public static WorkflowRuntimeResult recover(WorkflowFileLedger ledger, WorkstreamPaths paths,
                                                NodeExecutionDocument claim) throws Exception {
        var node = ledger.loadNode(paths, claim.nodeInstanceId());
        var current = ledger.loadCurrent(paths);
        var observation = AgentRunCommitProtocol.observe(paths, claim);
        if (observation == AgentRunCommitProtocol.Observation.COMPLETED) {
            AgentExecutionCompletion.commit(ledger, paths, claim, null);
        } else if (AgentRunCommitProtocol.executorOwnsInvocation(paths, claim)) {
            return new WorkflowRuntimeResult(paths.workstreamId().value(), current.status(), node.nodeDefinitionId(),
                    "execution is still owned by its original live executor");
        } else if (observation == AgentRunCommitProtocol.Observation.OUTCOME_RECORDED) {
            var result = ArtifactContractValidator.completeRecorded(ledger, paths.workspaceRoot(), paths, claim);
            result = AgentRunCommitProtocol.complete(paths.workspaceRoot(), ledger, paths, claim, result);
            AgentExecutionCompletion.commit(ledger, paths, claim, result);
        } else {
            var unknown = observation == AgentRunCommitProtocol.Observation.EXTERNAL_UNKNOWN;
            var reason = unknown ? "EXTERNAL_RESULT_UNKNOWN" : "PREPARATION_INTERRUPTED";
            var facts = Map.<String, Object>of("reason", reason, "external_effect", unknown ? "UNKNOWN" : "NOT_STARTED",
                    "provider_invocation", unknown ? "UNKNOWN" : "NOT_STARTED", "automatic_retry", false);
            var result = new NodeResult(reason, NodeStatus.FAILED, List.of(), facts,
                    unknown ? "External result is unknown; stop evidence and explicit adjudication are required."
                            : "Preparation stopped before external intent; a new attempt may prepare again.");
            try (var write = ledger.writeSession(paths, "classify stopped execution", claim.nodeExecutionId())) {
                var scoped = write.ledger();
                scoped.verifyExecutionClaim(paths, claim);
                // Repeat executor check in the accepting write session; a live owner cannot be silently superseded.
                if (AgentRunCommitProtocol.executorOwnsInvocation(paths, claim)) throw new IOException("executor became active");
                scoped.markAgentTaskFinished(paths, scoped.readAgentTask(paths, claim.agentTaskId()).orElseThrow(), result);
                scoped.finishNodeExecution(paths, claim, result);
                var nodeFacts = new LinkedHashMap<String, Object>(facts);
                nodeFacts.put("prepared_run", claim.result().get("prepared_run"));
                scoped.completeAndRoute(paths, scoped.loadDefinition(paths), node.nodeInstanceId(), result.status(),
                        result.outcome(), result.outputRefs(), nodeFacts);
                write.commit();
            }
        }
        var next = ledger.loadCurrent(paths);
        var active = next.activeNodeInstanceIds().isEmpty() ? "" : ledger.loadNode(paths, next.activeNodeInstanceIds().getFirst()).nodeDefinitionId();
        return new WorkflowRuntimeResult(paths.workstreamId().value(), next.status(), active,
                "recovered recorded execution state without dispatching a provider");
    }
}
