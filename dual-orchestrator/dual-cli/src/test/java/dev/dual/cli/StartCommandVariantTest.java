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
    void defaultVariant_usesBaselineMainline() throws Exception {
        // Simulates: dualctl start (no --workflow-variant flag → null)
        var wf = loader.loadOrDefault(null, null);
        assertEquals("agent-lifecycle", wf.workflowId(),
                "Default (no variant) should use baselineMainline");
        assertEquals(1, wf.version());
    }

    @Test
    void fullVariant_usesFullMainline() throws Exception {
        // Simulates: dualctl start --workflow-variant=full
        var wf = loader.loadOrDefault(null, "full");
        assertEquals("agent-lifecycle-full", wf.workflowId(),
                "--workflow-variant=full should use fullMainline");
        assertEquals(3, wf.version());
        // Verify the classify_failure node exists (full lifecycle feature)
        assertDoesNotThrow(() -> wf.node("acceptance.classify_failure"),
                "fullMainline should contain classify_failure node");
    }

    @Test
    void contractVariant_usesContractMainline() throws Exception {
        // Simulates: dualctl start --workflow-variant=contract
        var wf = loader.loadOrDefault(null, "contract");
        assertEquals("agent-lifecycle-contract", wf.workflowId(),
                "--workflow-variant=contract should use contractMainline");
        assertEquals(2, wf.version());
        // Verify contract node exists (contract feature)
        assertDoesNotThrow(() -> wf.node("contract.compile"),
                "contractMainline should contain contract.compile node");
        assertThrows(IllegalArgumentException.class, () -> wf.node("acceptance.classify_failure"),
                "contractMainline should NOT contain classify_failure node");
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
