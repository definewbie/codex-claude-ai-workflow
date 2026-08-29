package dev.dual.adapter.acpx;

import dev.dual.core.workflow.WorkflowDefinition;
import dev.dual.core.workflow.NodeKind;
import dev.dual.runtime.lifecycle.WorkflowDefinitionLoader;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    @Test
    void legacyDeliverySideEffectHandlersAreNotResolvable() {
        var registry = AcpxWorkflowHandlerRegistryFactory.create();

        assertThrows(IllegalArgumentException.class,
                () -> registry.resolve(NodeKind.TASK, "delivery.git_commit"));
        assertThrows(IllegalArgumentException.class,
                () -> registry.resolve(NodeKind.TASK, "delivery.create_pr"));
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
