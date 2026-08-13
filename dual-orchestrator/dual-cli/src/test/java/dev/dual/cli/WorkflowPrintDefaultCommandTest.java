package dev.dual.cli;

import dev.dual.runtime.lifecycle.WorkflowDefinitionLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowPrintDefaultCommandTest {
    @TempDir
    Path tempDir;

    @Test
    void writesSerializableWorkflowJsonWithEdgeConditions() throws Exception {
        var output = tempDir.resolve("workflow.json");
        var command = new WorkflowPrintDefaultCommand();
        command.outputPath = output;

        var exitCode = command.call();

        assertEquals(0, exitCode);
        var json = Files.readString(output);
        assertTrue(json.contains("\"workflowId\" : \"agent-lifecycle-full\""));
        assertTrue(json.contains("\"condition\""));
        var reloaded = new WorkflowDefinitionLoader().load(output);
        assertEquals("agent-lifecycle-full", reloaded.workflowId());
        assertEquals(9, reloaded.version());
        assertTrue(reloaded.edges().stream().anyMatch(edge -> "unconditional".equals(edge.condition().type())));
    }
}
