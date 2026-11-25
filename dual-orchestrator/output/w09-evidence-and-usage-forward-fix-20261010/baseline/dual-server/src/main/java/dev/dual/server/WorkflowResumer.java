package dev.dual.server;

import dev.dual.runtime.WorkflowCommand;
import dev.dual.runtime.WorkflowRuntimeResult;

@FunctionalInterface
interface WorkflowResumer {
    WorkflowRuntimeResult resume(WorkflowCommand command) throws Exception;
}
