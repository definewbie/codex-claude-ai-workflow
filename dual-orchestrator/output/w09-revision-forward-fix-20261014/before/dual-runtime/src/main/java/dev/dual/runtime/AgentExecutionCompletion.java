package dev.dual.runtime;

import dev.dual.core.workflow.NodeResult;
import dev.dual.ledger.file.*;
import dev.dual.runtime.handler.AgentRunCommitProtocol;
import java.io.IOException;

/** Shared inline/worker/recovery L03 consumer of a fixed R03 result. */
final class AgentExecutionCompletion {
    private AgentExecutionCompletion() {}

    /** A local exception is a failure, never proof that external work succeeded or was absent.
     * A prepared completion wins: readers refuse pending bytes and a later writer rechecks the claim. */
    static void recordHandlerFailure(WorkflowFileLedger ledger, WorkstreamPaths paths,
                                     NodeExecutionDocument expected, Exception failure) throws IOException {
        // Do not let error handling itself replay and then overwrite a pending successful result.
        try (var read = ledger.readSession(paths)) {
            var observed = read.files().read(paths.nodeExecutionJson(expected.nodeExecutionId()), NodeExecutionDocument.class);
            if (observed.status() != NodeExecutionStatus.RUNNING) return;
        }
        try (var write = ledger.writeSession(paths, "record handler exception", expected.nodeExecutionId())) {
            var scoped = write.ledger();
            var actual = write.files().read(paths.nodeExecutionJson(expected.nodeExecutionId()), NodeExecutionDocument.class);
            if (actual.status() != NodeExecutionStatus.RUNNING) return;
            scoped.verifyExecutionClaim(paths, expected);
            var task = scoped.readAgentTask(paths, actual.agentTaskId())
                    .orElseThrow(() -> new IOException("execution task missing"));
            scoped.markAgentTaskFailed(paths, task, failure);
            var failed = scoped.failNodeExecution(paths, actual, failure);
            scoped.completeAndRoute(paths, scoped.loadDefinition(paths), actual.nodeInstanceId(),
                    failed.nodeStatus(), failed.outcome(), failed.outputRefs(), failed.result());
            write.commit();
        }
    }

    static NodeResult commit(WorkflowFileLedger ledger, WorkstreamPaths paths, NodeExecutionDocument expected,
                             NodeResult expectedResult) throws IOException {
        try (var write = ledger.writeSession(paths, "consume fixed execution completion", expected.nodeExecutionId())) {
            var scoped = write.ledger();
            var actual = write.files().read(paths.nodeExecutionJson(expected.nodeExecutionId()), NodeExecutionDocument.class);
            if (actual.status() != NodeExecutionStatus.RUNNING) {
                var fixed = AgentRunCommitProtocol.readCompletion(paths, actual);
                if (!java.util.Objects.equals(fixed.result().get("completion_ref"), actual.result().get("completion_ref"))
                        || !java.util.Objects.equals(fixed.result().get("completion_sha256"), actual.result().get("completion_sha256"))
                        || (expectedResult != null && !java.util.Objects.equals(fixed.result().get("completion_sha256"), expectedResult.result().get("completion_sha256"))))
                    throw new IOException("execution already consumed a different or unidentifiable completion");
                return new NodeResult(actual.outcome(), actual.nodeStatus(), actual.outputRefs(), actual.result(), actual.errorMessage());
            }
            var result = AgentRunCommitProtocol.consume(scoped, paths, expected);
            if (expectedResult != null && (!result.result().get("completion_ref").equals(expectedResult.result().get("completion_ref"))
                    || !result.result().get("completion_sha256").equals(expectedResult.result().get("completion_sha256"))))
                throw new IOException("completion changed before local consumption");
            var task = scoped.readAgentTask(paths, actual.agentTaskId()).orElseThrow(() -> new IOException("execution task missing"));
            scoped.markAgentTaskFinished(paths, task, result);
            scoped.finishNodeExecution(paths, actual, result);
            WorkflowRuntime.emitDomainEvents(scoped, paths, actual.nodeDefinitionId(), actual.handlerType(), actual.nodeInstanceId(), result);
            scoped.completeAndRoute(paths, scoped.loadDefinition(paths), actual.nodeInstanceId(), result.status(),
                    result.outcome(), result.outputRefs(), result.result());
            write.commit();
            return result;
        }
    }
}
