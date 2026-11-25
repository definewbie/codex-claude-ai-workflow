package dev.dual.flow.liteflow;

import dev.dual.core.flow.FlowAdvanceResult;
import dev.dual.core.flow.FlowExecutionSnapshot;
import dev.dual.core.flow.FlowSignal;
import dev.dual.core.workflow.WorkflowDefinition;
import dev.dual.core.workflow.EdgeDefinition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public final class LiteFlowKernelContext {
    private final WorkflowDefinition definition;
    private final FlowExecutionSnapshot snapshot;
    private final FlowSignal signal;
    private final Map<String, EdgeDefinition> candidateEdgesByComponentId;
    private final List<String> executedStepIds = new ArrayList<>();
    private final CopyOnWriteArrayList<String> acceptedCandidateComponentIds = new CopyOnWriteArrayList<>();
    private FlowAdvanceResult advanceResult;

    public LiteFlowKernelContext(WorkflowDefinition definition, FlowExecutionSnapshot snapshot, FlowSignal signal) {
        this(definition, snapshot, signal, Map.of());
    }

    public LiteFlowKernelContext(
            WorkflowDefinition definition,
            FlowExecutionSnapshot snapshot,
            FlowSignal signal,
            Map<String, EdgeDefinition> candidateEdgesByComponentId
    ) {
        if (definition == null) {
            throw new IllegalArgumentException("workflow definition is required");
        }
        if (snapshot == null) {
            throw new IllegalArgumentException("flow execution snapshot is required");
        }
        if (signal == null) {
            throw new IllegalArgumentException("flow signal is required");
        }
        this.definition = definition;
        this.snapshot = snapshot;
        this.signal = signal;
        this.candidateEdgesByComponentId = copyCandidateEdges(candidateEdgesByComponentId);
    }

    public WorkflowDefinition definition() {
        return definition;
    }

    public FlowExecutionSnapshot snapshot() {
        return snapshot;
    }

    public FlowSignal signal() {
        return signal;
    }

    public void recordStep(String stepId) {
        executedStepIds.add(stepId);
    }

    public List<String> executedStepIds() {
        return List.copyOf(executedStepIds);
    }

    public FlowAdvanceResult advanceResult() {
        return advanceResult;
    }

    public void setAdvanceResult(FlowAdvanceResult advanceResult) {
        this.advanceResult = advanceResult;
    }

    public EdgeDefinition candidateEdge(String componentId) {
        return candidateEdgesByComponentId.get(componentId);
    }

    public List<String> acceptedCandidateComponentIds() {
        return List.copyOf(acceptedCandidateComponentIds);
    }

    public void acceptCandidate(String componentId) {
        acceptedCandidateComponentIds.add(componentId);
    }

    private static Map<String, EdgeDefinition> copyCandidateEdges(Map<String, EdgeDefinition> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        var copy = new LinkedHashMap<String, EdgeDefinition>();
        for (var entry : source.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) {
                throw new IllegalArgumentException("candidate component id is required");
            }
            if (entry.getValue() == null) {
                throw new IllegalArgumentException("candidate edge is required");
            }
            copy.put(entry.getKey(), entry.getValue());
        }
        return Map.copyOf(copy);
    }
}
