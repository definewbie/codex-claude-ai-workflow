package dev.dual.cli;

import dev.dual.core.flow.BuiltInFlowOrchestrationKernel;
import dev.dual.core.id.DualId;
import dev.dual.core.workflow.EdgeDefinition;
import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeStatus;
import dev.dual.core.workflow.WorkflowDefinition;
import dev.dual.flow.liteflow.FlowKernelProvider;
import dev.dual.flow.liteflow.LiteFlowPrototypeKernel;
import dev.dual.ledger.file.WorkflowFileLedger;
import dev.dual.ledger.file.WorkstreamPaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CliLedgerFactoryTest {
    @TempDir Path root;

    @Test
    void actualFactorySelectsConfiguredKernelAndRoutesThroughLedger() throws Exception {
        var original = System.getProperty("dual.flow.kernel");
        try {
            for (String setting : new String[]{null, "liteflow", "builtin"}) {
                if (setting == null) System.clearProperty("dual.flow.kernel");
                else System.setProperty("dual.flow.kernel", setting);
                var ledger = CliLedgerFactory.create();
                // Inspect actual assembly, not a second call to the provider as a substitute.
                var routingField = WorkflowFileLedger.class.getDeclaredField("routingEngine");
                routingField.setAccessible(true);
                var routing = routingField.get(ledger);
                var kernelField = routing.getClass().getDeclaredField("flowKernel");
                kernelField.setAccessible(true);
                var kernel = kernelField.get(routing);
                var configured = setting == null ? System.getenv("DUAL_FLOW_KERNEL") : setting;
                var effective = FlowKernelProvider.normalize(configured);
                assertEquals(effective.equals("liteflow") ? LiteFlowPrototypeKernel.class
                        : BuiltInFlowOrchestrationKernel.class, kernel.getClass());
                System.out.println("D08 CliLedgerFactory: property=" + setting + ", environment="
                        + System.getenv("DUAL_FLOW_KERNEL") + ", effective=" + kernel.getClass().getName()
                        + ", loader=" + kernel.getClass().getClassLoader() + ", pid=" + ProcessHandle.current().pid());
                var id = "ws-" + (setting == null ? "default" : setting);
                var paths = new WorkstreamPaths(root, new DualId(id));
                var definition = new WorkflowDefinition("factory-route", 1, "source", List.of(
                        new NodeDefinition("source", NodeKind.TASK, "noop", Map.of()),
                        new NodeDefinition("next", NodeKind.TASK, "noop", Map.of())),
                        List.of(new EdgeDefinition("source", "DONE", "next")));
                CliTestLedger.initialize(ledger, paths, "wf-" + id, definition);
                var source = ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst();
                ledger.completeAndRoute(paths, definition, source, NodeStatus.COMPLETED, "DONE", List.of(), Map.of());
                var next = ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst();
                assertEquals("next", ledger.loadNode(paths, next).nodeDefinitionId());
                if (kernel instanceof AutoCloseable closeable) closeable.close();
            }
        } finally {
            if (original == null) System.clearProperty("dual.flow.kernel");
            else System.setProperty("dual.flow.kernel", original);
        }
    }

    @Test
    void unknownConfigurationNeverFallsBackToAnotherKernel() {
        var original = System.getProperty("dual.flow.kernel");
        try {
            System.setProperty("dual.flow.kernel", "unknown-kernel");
            assertThrows(IllegalArgumentException.class, CliLedgerFactory::create);
        } finally {
            if (original == null) System.clearProperty("dual.flow.kernel");
            else System.setProperty("dual.flow.kernel", original);
        }
    }
}
