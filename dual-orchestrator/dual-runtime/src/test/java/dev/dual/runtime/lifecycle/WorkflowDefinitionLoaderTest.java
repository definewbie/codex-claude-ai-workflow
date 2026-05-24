package dev.dual.runtime.lifecycle;

import dev.dual.core.workflow.NodeKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowDefinitionLoaderTest {
    @TempDir
    Path tempDir;

    @Test
    void loadsWorkflowDefinitionFromJsonFile() throws Exception {
        var workflowPath = tempDir.resolve("workflow.json");
        Files.writeString(workflowPath, """
                {
                  "workflowId": "custom-lifecycle",
                  "version": 1,
                  "entryNodeId": "intake.claude_brainstorm",
                  "nodes": [
                    {
                      "id": "intake.claude_brainstorm",
                      "kind": "TASK",
                      "handlerType": "acpx.agent_run",
                      "config": {
                        "agent": "claude",
                        "session": "claude-main",
                        "prompt_template": "intake-claude-brainstorm"
                      }
                    },
                    {
                      "id": "gate.human_requirement_review",
                      "kind": "GATE",
                      "handlerType": "human.review",
                      "config": {
                        "allowed_actions": ["approve", "revise"]
                      }
                    }
                  ],
                  "edges": [
                    {
                      "from": "intake.claude_brainstorm",
                      "on": "completed",
                      "to": "gate.human_requirement_review"
                    }
                  ]
                }
                """);

        var definition = new WorkflowDefinitionLoader().load(workflowPath);

        assertEquals("custom-lifecycle", definition.workflowId());
        assertEquals(1, definition.version());
        assertEquals("intake.claude_brainstorm", definition.entryNodeId());
        assertEquals(NodeKind.TASK, definition.node("intake.claude_brainstorm").kind());
        assertEquals("acpx.agent_run", definition.node("intake.claude_brainstorm").handlerType());
        assertEquals("claude", definition.node("intake.claude_brainstorm").config().get("agent"));
        assertEquals("gate.human_requirement_review", definition.edgesFrom("intake.claude_brainstorm").get(0).to());
    }

    @Test
    void fallsBackToBuiltInWorkflowWhenPathIsNull() throws Exception {
        var definition = new WorkflowDefinitionLoader().loadOrDefault(null);

        assertEquals("agent-lifecycle", definition.workflowId());
        assertEquals("intake.claude_brainstorm", definition.entryNodeId());
    }

    @Test
    void failsWhenWorkflowReferencesMissingNode() throws Exception {
        var workflowPath = tempDir.resolve("broken-workflow.json");
        Files.writeString(workflowPath, """
                {
                  "workflowId": "broken",
                  "version": 1,
                  "entryNodeId": "missing.entry",
                  "nodes": [],
                  "edges": []
                }
                """);

        var error = assertThrows(Exception.class, () -> new WorkflowDefinitionLoader().load(workflowPath));

        assertTrue(error.getMessage().contains("entry node does not exist"));
    }
}
