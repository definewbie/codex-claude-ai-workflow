package dev.dual.runtime.lifecycle;

import dev.dual.core.workflow.WorkflowDefinition;

import java.io.IOException;

public final class LifecycleWorkflowDefinitionFactory {
    private final WorkflowDefinitionLoader loader;

    public LifecycleWorkflowDefinitionFactory() {
        this(new WorkflowDefinitionLoader());
    }

    LifecycleWorkflowDefinitionFactory(WorkflowDefinitionLoader loader) {
        this.loader = loader;
    }

    public WorkflowDefinition forVariant(String variant) {
        return loadBundled(variant);
    }

    private WorkflowDefinition loadBundled(String variant) {
        try {
            return loader.loadBundledVariant(variant);
        } catch (IOException e) {
            throw new IllegalStateException("failed to load bundled workflow definition: " + variant, e);
        }
    }
}
