package dev.dual.core.execution;

import java.time.Instant;
import java.util.List;

/**
 * Aggregate root for one external agent invocation.
 *
 * <p>This object owns runtime facts produced by an agent adapter. Workflow and
 * node state should consume {@link #outcome()} instead of reinterpreting raw
 * process output, adapter text, assistant prose, or artifact files.</p>
 */
public record AgentExecution(
        String id,
        String workstreamId,
        String workflowExecutionId,
        String nodeExecutionId,
        String agentTaskId,
        String agent,
        String session,
        String promptRef,
        String outputRef,
        Instant startedAt,
        Instant finishedAt,
        ProcessResult processResult,
        List<AdapterSignal> adapterSignals,
        AgentResultContractResult agentResultContractResult,
        List<ArtifactContractResult> artifactContractResults,
        Object toolUsage,
        Object tokenUsage,
        AgentExecutionOutcome outcome
) {
    public AgentExecution {
        id = requireText("id", id);
        workstreamId = requireText("workstreamId", workstreamId);
        nodeExecutionId = blankToEmpty(nodeExecutionId);
        workflowExecutionId = blankToEmpty(workflowExecutionId);
        agentTaskId = blankToEmpty(agentTaskId);
        agent = blankToEmpty(agent);
        session = blankToEmpty(session);
        promptRef = blankToEmpty(promptRef);
        outputRef = blankToEmpty(outputRef);
        if (processResult == null) {
            throw new IllegalArgumentException("processResult is required");
        }
        adapterSignals = adapterSignals == null ? List.of() : List.copyOf(adapterSignals);
        if (agentResultContractResult == null) {
            throw new IllegalArgumentException("agentResultContractResult is required");
        }
        artifactContractResults = artifactContractResults == null ? List.of() : List.copyOf(artifactContractResults);
        if (outcome == null) {
            throw new IllegalArgumentException("outcome is required");
        }
        if (finishedAt != null && startedAt != null && finishedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("finishedAt cannot be before startedAt");
        }
    }

    private static String requireText(String field, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value;
    }
}
