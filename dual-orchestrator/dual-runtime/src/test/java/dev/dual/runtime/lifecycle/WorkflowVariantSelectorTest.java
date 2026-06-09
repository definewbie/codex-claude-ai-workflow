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
    void forVariant_null_returnsBaselineMainline() {
        WorkflowDefinition wf = factory.forVariant(null);
        assertEquals("agent-lifecycle", wf.workflowId());
        assertEquals(1, wf.version());
    }

    @Test
    void forVariant_blank_returnsBaselineMainline() {
        assertEquals("agent-lifecycle", factory.forVariant("").workflowId());
        assertEquals("agent-lifecycle", factory.forVariant("   ").workflowId());
    }

    @Test
    void forVariant_thin_returnsBaselineMainline() {
        WorkflowDefinition wf = factory.forVariant("baseline");
        assertEquals("agent-lifecycle", wf.workflowId());
    }

    @Test
    void forVariant_contract_returnsContractMainline() {
        WorkflowDefinition wf = factory.forVariant("contract");
        assertEquals("agent-lifecycle-contract", wf.workflowId());
        assertEquals(2, wf.version());
    }

    @Test
    void forVariant_full_returnsFullMainline() {
        WorkflowDefinition wf = factory.forVariant("full");
        assertEquals("agent-lifecycle-full", wf.workflowId());
        assertEquals(3, wf.version());
    }

    @Test
    void forVariant_professionalAliases_returnExpectedMainlines() {
        assertEquals("agent-lifecycle", factory.forVariant("baseline").workflowId());
        assertEquals("agent-lifecycle-contract", factory.forVariant("contract").workflowId());
        assertEquals("agent-lifecycle-full", factory.forVariant("full").workflowId());
    }

    @Test
    void forVariant_normalizesCaseAndWhitespace() {
        assertEquals("agent-lifecycle-full", factory.forVariant("  Full  ").workflowId());
        assertEquals("agent-lifecycle-full", factory.forVariant("  FULL  ").workflowId());
        assertEquals("agent-lifecycle-contract", factory.forVariant("Contract").workflowId());
        assertEquals("agent-lifecycle", factory.forVariant(" BASELINE ").workflowId());
    }

    @Test
    void forVariant_unknown_throws() {
        var ex = assertThrows(IllegalArgumentException.class, () -> factory.forVariant("unknown"));
        assertTrue(ex.getMessage().contains("unknown workflow variant"));
        assertTrue(ex.getMessage().contains("unknown"));
    }

    // --- WorkflowDefinitionLoader variant tests ---

    private final WorkflowDefinitionLoader loader = new WorkflowDefinitionLoader();

    @Test
    void loadOrDefault_nullPathFullVariant_returnsFullMainline() throws Exception {
        WorkflowDefinition wf = loader.loadOrDefault(null, "full");
        assertEquals("agent-lifecycle-full", wf.workflowId());
    }

    @Test
    void loadOrDefault_nullPathNullVariant_returnsBaselineMainline() throws Exception {
        WorkflowDefinition wf = loader.loadOrDefault(null, null);
        assertEquals("agent-lifecycle", wf.workflowId());
    }

    @Test
    void loadOrDefault_nonExistentPath_fallsBackToVariant() throws Exception {
        Path nonExistent = Path.of("/tmp/does-not-exist-workflow.json");
        WorkflowDefinition wf = loader.loadOrDefault(nonExistent, "contract");
        assertEquals("agent-lifecycle-contract", wf.workflowId());
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

        WorkflowDefinition wf = loader.loadOrDefault(path, "full");
        assertEquals("custom-workflow", wf.workflowId(),
                "Should load from file, ignoring variant");
    }

    @Test
    void loadOrDefault_oldOverload_stillWorks() throws Exception {
        WorkflowDefinition wf = loader.loadOrDefault(null);
        assertEquals("agent-lifecycle", wf.workflowId(),
                "Old loadOrDefault(Path) should still return baselineMainline");
    }
}
