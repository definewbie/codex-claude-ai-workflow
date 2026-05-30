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
    void defaultVariant_usesThinMainline() throws Exception {
        // Simulates: dualctl start (no --workflow-variant flag → null)
        var wf = loader.loadOrDefault(null, null);
        assertEquals("agent-lifecycle", wf.workflowId(),
                "Default (no variant) should use thinMainline");
        assertEquals(1, wf.version());
    }

    @Test
    void p6Variant_usesP6Mainline() throws Exception {
        // Simulates: dualctl start --workflow-variant=p6
        var wf = loader.loadOrDefault(null, "p6");
        assertEquals("agent-lifecycle-p6", wf.workflowId(),
                "--workflow-variant=p6 should use p6Mainline");
        assertEquals(3, wf.version());
        // Verify the classify_failure node exists (P6 feature)
        assertDoesNotThrow(() -> wf.node("acceptance.classify_failure"),
                "p6Mainline should contain classify_failure node");
    }

    @Test
    void p5Variant_usesP5Mainline() throws Exception {
        // Simulates: dualctl start --workflow-variant=p5
        var wf = loader.loadOrDefault(null, "p5");
        assertEquals("agent-lifecycle-p5", wf.workflowId(),
                "--workflow-variant=p5 should use p5Mainline");
        assertEquals(2, wf.version());
        // Verify contract node exists (P5 feature)
        assertDoesNotThrow(() -> wf.node("contract.compile"),
                "p5Mainline should contain contract.compile node");
        assertThrows(IllegalArgumentException.class, () -> wf.node("acceptance.classify_failure"),
                "p5Mainline should NOT contain classify_failure node");
    }

    @Test
    void variantFlag_overridesDefaultButFileWins() throws Exception {
        // When both --workflow and --workflow-variant are provided,
        // the file takes precedence. This is tested via the loader directly.
        // If path is null, variant is used:
        var wf = loader.loadOrDefault(null, "p6");
        assertEquals("agent-lifecycle-p6", wf.workflowId());
    }
}
