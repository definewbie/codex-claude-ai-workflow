package dev.dual.adapter.acpx;

import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeInstance;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeStatus;
import dev.dual.runtime.handler.TaskHandler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcpxAgentTaskHandlerTest {
    @TempDir
    Path tempDir;

    @Test
    void writesPromptAndReturnsPreparedWhenConfiguredForPrepareOnly() throws Exception {
        var handler = new AcpxAgentTaskHandler(new AcpxAgentTransport(), true);
        var definition = new NodeDefinition("design.claude_design", NodeKind.TASK, "acpx.agent_run", Map.of(
                "agent", "claude",
                "session", "claude-main",
                "prompt_template", "design-claude"
        ));
        var instance = new NodeInstance(
                "node-design",
                "design.claude_design",
                NodeKind.TASK,
                "acpx.agent_run",
                NodeStatus.READY,
                "stage-design",
                "iter-001",
                List.of(),
                List.of(),
                Map.of(),
                null,
                null,
                null
        );

        var result = handler.execute(new TaskHandler.TaskContext(tempDir, "ws-test", definition, instance));

        assertEquals(NodeStatus.COMPLETED, result.status());
        assertEquals("completed", result.outcome());
        assertEquals(true, result.result().get("prepared"));
        assertTrue(Files.walk(tempDir.resolve(".agent"))
                .anyMatch(path -> path.getFileName().toString().equals("design-claude.md")));
    }

    @Test
    void rejectsUnsupportedAgent() {
        var handler = new AcpxAgentTaskHandler(new AcpxAgentTransport(), true);
        var definition = new NodeDefinition("design.bad_agent", NodeKind.TASK, "acpx.agent_run", Map.of(
                "agent", "unknown",
                "session", "unknown-main",
                "prompt_template", "bad-agent"
        ));
        var instance = new NodeInstance(
                "node-bad",
                "design.bad_agent",
                NodeKind.TASK,
                "acpx.agent_run",
                NodeStatus.READY,
                "stage-design",
                "iter-001",
                List.of(),
                List.of(),
                Map.of(),
                null,
                null,
                null
        );

        var error = assertThrows(IllegalArgumentException.class, () ->
                handler.execute(new TaskHandler.TaskContext(tempDir, "ws-test", definition, instance)));
        assertTrue(error.getMessage().contains("unsupported agent"));
    }
}
