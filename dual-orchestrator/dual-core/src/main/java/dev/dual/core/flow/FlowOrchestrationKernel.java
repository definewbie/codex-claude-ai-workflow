package dev.dual.core.flow;

import dev.dual.core.workflow.WorkflowDefinition;

public interface FlowOrchestrationKernel {
    FlowValidationResult validate(WorkflowDefinition definition);

    FlowAdvanceResult advance(
            WorkflowDefinition definition,
            FlowExecutionSnapshot snapshot,
            FlowSignal signal
    );
}
