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
    void forVariant_null_returnsFullVariant() {
        WorkflowDefinition wf = factory.forVariant(null);
        assertEquals("agent-lifecycle-full", wf.workflowId());
        assertEquals(7, wf.version());
    }

    @Test
    void forVariant_blank_returnsFullVariant() {
        assertEquals("agent-lifecycle-full", factory.forVariant("").workflowId());
        assertEquals("agent-lifecycle-full", factory.forVariant("   ").workflowId());
    }

    @Test
    void forVariant_baseline_throws() {
        var error = assertThrows(IllegalArgumentException.class, () -> factory.forVariant("baseline"));
        assertTrue(error.getMessage().contains("baseline"));
    }

    @Test
    void forVariant_contract_returnsContractVariant() {
        WorkflowDefinition wf = factory.forVariant("contract");
        assertEquals("agent-lifecycle-contract", wf.workflowId());
        assertEquals(6, wf.version());
    }

    @Test
    void forVariant_full_returnsFullVariant() {
        WorkflowDefinition wf = factory.forVariant("full");
        assertEquals("agent-lifecycle-full", wf.workflowId());
        assertEquals(7, wf.version());
    }

    @Test
    void forVariant_terminalDelivery_returnsTerminalDeliveryVariant() {
        WorkflowDefinition wf = factory.forVariant("terminal-delivery");
        assertEquals("agent-lifecycle-terminal-delivery", wf.workflowId());
        assertEquals(2, wf.version());
    }

    @Test
    void contractVariantsDeclareProposalReviewAndCompileSubjectBoundary() {
        for (var variant : java.util.List.of("full", "contract", "terminal-delivery")) {
            var workflow = factory.forVariant(variant);
            assertEquals("task_contract_proposal",
                    workflow.node("intake.codex_product_review").config().get("review_subject_artifact"));
            assertEquals("approved_task_contract_proposal",
                    workflow.node("contract.compile").config().get("required_subject"));
        }
    }

    @Test
    void forVariant_professionalAliases_returnExpectedVariants() {
        assertEquals("agent-lifecycle-contract", factory.forVariant("contract").workflowId());
        assertEquals("agent-lifecycle-full", factory.forVariant("full").workflowId());
        assertEquals("agent-lifecycle-terminal-delivery",
                factory.forVariant("terminal-delivery").workflowId());
    }

    @Test
    void forVariant_normalizesCaseAndWhitespace() {
        assertEquals("agent-lifecycle-full", factory.forVariant("  Full  ").workflowId());
        assertEquals("agent-lifecycle-full", factory.forVariant("  FULL  ").workflowId());
        assertEquals("agent-lifecycle-contract", factory.forVariant("Contract").workflowId());
        assertEquals("agent-lifecycle-terminal-delivery",
                factory.forVariant(" Terminal-Delivery ").workflowId());
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
    void loadOrDefault_nullPathFullVariant_returnsFullVariant() throws Exception {
        WorkflowDefinition wf = loader.loadOrDefault(null, "full");
        assertEquals("agent-lifecycle-full", wf.workflowId());
    }

    @Test
    void loadOrDefault_nullPathNullVariant_returnsFullVariant() throws Exception {
        WorkflowDefinition wf = loader.loadOrDefault(null, null);
        assertEquals("agent-lifecycle-full", wf.workflowId());
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
        assertEquals("agent-lifecycle-full", wf.workflowId(),
                "Old loadOrDefault(Path) should return the full default lifecycle");
    }
}
