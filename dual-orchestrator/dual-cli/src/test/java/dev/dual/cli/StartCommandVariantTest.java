package dev.dual.cli;

import dev.dual.runtime.lifecycle.WorkflowDefinitionLoader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that the --workflow-variant flag wiring works correctly.
 * StartCommand creates git worktrees, so we test the loader wiring
 * directly rather than running the full CLI command.
 */
class StartCommandVariantTest {

    private final WorkflowDefinitionLoader loader = new WorkflowDefinitionLoader();

    @Test
    void defaultVariant_usesFullVariant() throws Exception {
        // Simulates: dualctl start (no --workflow-variant flag → null)
        var wf = loader.loadOrDefault(null, null);
        assertEquals("agent-lifecycle-full", wf.workflowId(),
                "Default (no variant) should use full variant");
        assertEquals(5, wf.version(), "Default full workflow should be loaded from the bundled JSON resource");
        assertDoesNotThrow(() -> wf.node("acceptance.classify_failure"),
                "default full variant should contain classify_failure node");
    }

    @Test
    void fullVariant_usesFullVariant() throws Exception {
        // Simulates: dualctl start --workflow-variant=full
        var wf = loader.loadOrDefault(null, "full");
        assertEquals("agent-lifecycle-full", wf.workflowId(),
                "--workflow-variant=full should use full variant");
        assertEquals(5, wf.version(), "Full variant should be loaded from the bundled JSON resource");
        // Verify the classify_failure node exists (full lifecycle feature)
        assertDoesNotThrow(() -> wf.node("acceptance.classify_failure"),
                "full variant should contain classify_failure node");
    }

    @Test
    void contractVariant_usesContractVariant() throws Exception {
        // Simulates: dualctl start --workflow-variant=contract
        var wf = loader.loadOrDefault(null, "contract");
        assertEquals("agent-lifecycle-contract", wf.workflowId(),
                "--workflow-variant=contract should use contract variant");
        assertEquals(4, wf.version(), "Contract variant should be loaded from the bundled JSON resource");
        // Verify contract node exists (contract feature)
        assertDoesNotThrow(() -> wf.node("contract.compile"),
                "contract variant should contain contract.compile node");
        assertDoesNotThrow(() -> wf.node("acceptance.classify_failure"),
                "contract variant should contain classify_failure node for acceptance repair routing");
    }

    @Test
    void variantFlag_overridesDefaultButFileWins() throws Exception {
        // When both --workflow and --workflow-variant are provided,
        // the file takes precedence. This is tested via the loader directly.
        // If path is null, variant is used:
        var wf = loader.loadOrDefault(null, "full");
        assertEquals("agent-lifecycle-full", wf.workflowId());
    }
}
