package dev.dual.adapter.acpx;

import dev.dual.core.workflow.WorkflowDefinition;
import dev.dual.runtime.lifecycle.WorkflowDefinitionLoader;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class AcpxWorkflowHandlerRegistryFactoryTest {

    @Test
    void resolvesEveryBundledWorkflowHandler() throws Exception {
        var registry = AcpxWorkflowHandlerRegistryFactory.create();
        for (var definition : bundledDefinitions()) {
            for (var node : definition.nodes()) {
                assertDoesNotThrow(
                        () -> registry.resolve(node.kind(), node.handlerType()),
                        definition.workflowId() + " node " + node.id()
                                + " has no registered handler for " + node.kind()
                                + "/" + node.handlerType()
                );
            }
        }
    }

    private static List<WorkflowDefinition> bundledDefinitions() throws Exception {
        var loader = new WorkflowDefinitionLoader();
        return List.of(
                loader.loadOrDefault(null, "full"),
                loader.loadOrDefault(null, "contract"),
                loader.loadOrDefault(null, "terminal-delivery")
        );
    }
}
