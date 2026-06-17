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
    void appliesWorkflowDefaultsToNodeConfigWhenNodeOmitsValues() throws Exception {
        var workflowPath = tempDir.resolve("workflow-defaults.json");
        Files.writeString(workflowPath, """
                {
                  "workflowId": "custom-lifecycle",
                  "version": 1,
                  "entryNodeId": "intake.claude_brainstorm",
                  "defaults": {
                    "sessions": {
                      "claude": "team-claude",
                      "codex": "team-codex"
                    },
                    "delivery": {
                      "base": "develop",
                      "draft": true
                    },
                    "revision": {
                      "max_revisions": 1
                    }
                  },
                  "nodes": [
                    {
                      "id": "intake.claude_brainstorm",
                      "kind": "TASK",
                      "handlerType": "acpx.agent_run",
                      "config": {
                        "agent": "claude",
                        "prompt_template": "intake-claude-brainstorm"
                      }
                    },
                    {
                      "id": "review.codex",
                      "kind": "TASK",
                      "handlerType": "acpx.agent_run",
                      "config": {
                        "agent": "codex",
                        "session": "explicit-codex",
                        "prompt_template": "intake-codex-product-review"
                      }
                    },
                    {
                      "id": "delivery.create_pr",
                      "kind": "TASK",
                      "handlerType": "delivery.create_pr",
                      "config": {}
                    },
                    {
                      "id": "design.decision",
                      "kind": "DECISION",
                      "handlerType": "review.decision",
                      "config": {}
                    },
                    {
                      "id": "gate.human_design_approval",
                      "kind": "GATE",
                      "handlerType": "human.review",
                      "config": {
                        "max_revisions": 3
                      }
                    }
                  ],
                  "edges": [
                    {
                      "from": "intake.claude_brainstorm",
                      "on": "completed",
                      "to": "review.codex"
                    },
                    {
                      "from": "review.codex",
                      "on": "completed",
                      "to": "delivery.create_pr"
                    }
                  ]
                }
                """);

        var definition = new WorkflowDefinitionLoader().load(workflowPath);

        assertEquals("team-claude", definition.node("intake.claude_brainstorm").config().get("session"));
        assertEquals("explicit-codex", definition.node("review.codex").config().get("session"));
        assertEquals("develop", definition.node("delivery.create_pr").config().get("base"));
        assertEquals(true, definition.node("delivery.create_pr").config().get("draft"));
        assertEquals(1, definition.node("design.decision").config().get("max_revisions"));
        assertEquals(3, definition.node("gate.human_design_approval").config().get("max_revisions"));
    }

    @Test
    void fallsBackToBuiltInWorkflowWhenPathIsNull() throws Exception {
        var definition = new WorkflowDefinitionLoader().loadOrDefault(null);

        assertEquals("agent-lifecycle-full", definition.workflowId());
        assertEquals(4, definition.version(), "default full workflow should be loaded from the bundled JSON resource");
        assertEquals("intake.claude_brainstorm", definition.entryNodeId());
    }

    @Test
    void bundledFullWorkflowLimitsWriteAccessToImplementationOwnedAgentNodes() throws Exception {
        var definition = new WorkflowDefinitionLoader().loadOrDefault(null);

        var writableNodes = definition.nodes().stream()
                .filter(node -> "acpx.agent_run".equals(node.handlerType()))
                .filter(node -> Boolean.TRUE.equals(node.config().get("approve_all")))
                .map(node -> node.id())
                .toList();

        assertEquals(java.util.List.of(
                "implement.claude_implement",
                "implement.claude_self_test_report"
        ), writableNodes);
    }

    @Test
    void bundledAgentNodesDeclarePermissionProfiles() throws Exception {
        var loader = new WorkflowDefinitionLoader();
        var allowedProfiles = java.util.Set.of("observe", "review", "implement");

        for (var variant : java.util.List.of("full", "contract")) {
            var definition = loader.loadOrDefault(null, variant);
            var missingOrInvalid = definition.nodes().stream()
                    .filter(node -> "acpx.agent_run".equals(node.handlerType()))
                    .filter(node -> !allowedProfiles.contains(String.valueOf(node.config().get("permission_profile"))))
                    .map(node -> variant + ":" + node.id())
                    .toList();

            assertEquals(java.util.List.of(), missingOrInvalid);
        }
    }

    @Test
    void bundledContractWorkflowIsLoadedFromClasspathResource() throws Exception {
        var definition = new WorkflowDefinitionLoader().loadOrDefault(null, "contract");

        assertEquals("agent-lifecycle-contract", definition.workflowId());
        assertEquals(3, definition.version());
        assertEquals("intake.claude_brainstorm", definition.entryNodeId());
        assertEquals(NodeKind.TASK, definition.node("contract.compile").kind());
        assertEquals(NodeKind.TASK, definition.node("acceptance.classify_failure").kind());
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
