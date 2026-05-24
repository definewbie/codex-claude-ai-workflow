package dev.dual.adapter.acpx;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentPromptRendererTest {
    @TempDir
    Path tempDir;

    @Test
    void rendersTemplateWithWorkstreamAndRequirement() throws Exception {
        var workstreamDir = tempDir.resolve(".agent").resolve("workstreams").resolve("ws-test");
        Files.createDirectories(workstreamDir);
        Files.writeString(workstreamDir.resolve("requirement.md"), "Build a safer agent workflow.\n");

        var prompt = new AgentPromptRenderer()
                .render(tempDir, "ws-test", "intake.claude_brainstorm", "intake-claude-brainstorm");

        assertTrue(prompt.contains("Workstream: ws-test"));
        assertTrue(prompt.contains("Node: intake.claude_brainstorm"));
        assertTrue(prompt.contains("Build a safer agent workflow."));
        assertTrue(prompt.contains("decision: READY_FOR_PRODUCT_REVIEW"));
    }

    @Test
    void failsClearlyWhenTemplateIsMissing() {
        var error = assertThrows(IllegalArgumentException.class, () ->
                new AgentPromptRenderer().render(tempDir, "ws-test", "bad.node", "missing-template"));

        assertTrue(error.getMessage().contains("missing prompt template resource"));
    }
}
