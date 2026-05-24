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
    void repositoryWorkflowExampleMatchesBuiltInLifecycleShape() throws Exception {
        var expected = new LifecycleWorkflowDefinitionFactory().thinMainline();
        var actual = new WorkflowDefinitionLoader().load(Path.of("../workflows/agent-lifecycle.v1.json"));

        assertEquals(expected.workflowId(), actual.workflowId());
        assertEquals(expected.version(), actual.version());
        assertEquals(expected.entryNodeId(), actual.entryNodeId());
        assertEquals(expected.nodes().size(), actual.nodes().size());
        assertEquals(expected.edges().size(), actual.edges().size());
        assertEquals(expected.node("context.implement_claude").handlerType(), actual.node("context.implement_claude").handlerType());
        assertEquals(expected.node("acceptance.codex_verifier_review").config().get("prompt_template"),
                actual.node("acceptance.codex_verifier_review").config().get("prompt_template"));
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
