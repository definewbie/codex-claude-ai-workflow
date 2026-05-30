package dev.dual.runtime.lifecycle;

import dev.dual.core.workflow.WorkflowDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class WorkflowVariantSelectorTest {

    private final LifecycleWorkflowDefinitionFactory factory = new LifecycleWorkflowDefinitionFactory();

    @Test
    void forVariant_null_returnsThinMainline() {
        WorkflowDefinition wf = factory.forVariant(null);
        assertEquals("agent-lifecycle", wf.workflowId());
        assertEquals(1, wf.version());
    }

    @Test
    void forVariant_blank_returnsThinMainline() {
        assertEquals("agent-lifecycle", factory.forVariant("").workflowId());
        assertEquals("agent-lifecycle", factory.forVariant("   ").workflowId());
    }

    @Test
    void forVariant_thin_returnsThinMainline() {
        WorkflowDefinition wf = factory.forVariant("thin");
        assertEquals("agent-lifecycle", wf.workflowId());
    }

    @Test
    void forVariant_p5_returnsP5Mainline() {
        WorkflowDefinition wf = factory.forVariant("p5");
        assertEquals("agent-lifecycle-p5", wf.workflowId());
        assertEquals(2, wf.version());
    }

    @Test
    void forVariant_p6_returnsP6Mainline() {
        WorkflowDefinition wf = factory.forVariant("p6");
        assertEquals("agent-lifecycle-p6", wf.workflowId());
        assertEquals(3, wf.version());
    }

    @Test
    void forVariant_normalizesCaseAndWhitespace() {
        assertEquals("agent-lifecycle-p6", factory.forVariant("  P6  ").workflowId());
        assertEquals("agent-lifecycle-p5", factory.forVariant("P5").workflowId());
        assertEquals("agent-lifecycle", factory.forVariant(" THIN ").workflowId());
    }

    @Test
    void forVariant_unknown_throws() {
        var ex = assertThrows(IllegalArgumentException.class, () -> factory.forVariant("p7"));
        assertTrue(ex.getMessage().contains("unknown workflow variant"));
        assertTrue(ex.getMessage().contains("p7"));
    }

    // --- WorkflowDefinitionLoader variant tests ---

    private final WorkflowDefinitionLoader loader = new WorkflowDefinitionLoader();

    @Test
    void loadOrDefault_nullPathP6Variant_returnsP6Mainline() throws Exception {
        WorkflowDefinition wf = loader.loadOrDefault(null, "p6");
        assertEquals("agent-lifecycle-p6", wf.workflowId());
    }

    @Test
    void loadOrDefault_nullPathNullVariant_returnsThinMainline() throws Exception {
        WorkflowDefinition wf = loader.loadOrDefault(null, null);
        assertEquals("agent-lifecycle", wf.workflowId());
    }

    @Test
    void loadOrDefault_nonExistentPath_fallsBackToVariant() throws Exception {
        Path nonExistent = Path.of("/tmp/does-not-exist-workflow.json");
        WorkflowDefinition wf = loader.loadOrDefault(nonExistent, "p5");
        assertEquals("agent-lifecycle-p5", wf.workflowId());
    }

    @Test
    void loadOrDefault_existingPath_ignoresVariant(@TempDir Path tempDir) throws Exception {
        // Write a minimal workflow JSON
        var json = """
                {
                  "workflowId": "custom-workflow",
                  "version": 1,
                  "entryNodeId": "node-a",
                  "nodes": [
                    {"id": "node-a", "kind": "TASK", "handlerType": "artifact.validate", "config": {}}
                  ],
                  "edges": []
                }
                """;
        var path = tempDir.resolve("custom.json");
        Files.writeString(path, json, StandardCharsets.UTF_8);

        WorkflowDefinition wf = loader.loadOrDefault(path, "p6");
        assertEquals("custom-workflow", wf.workflowId(),
                "Should load from file, ignoring variant");
    }

    @Test
    void loadOrDefault_oldOverload_stillWorks() throws Exception {
        WorkflowDefinition wf = loader.loadOrDefault(null);
        assertEquals("agent-lifecycle", wf.workflowId(),
                "Old loadOrDefault(Path) should still return thinMainline");
    }
}
