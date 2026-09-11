package dev.dual.runtime.lifecycle;

import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.HumanGateActionSemantics;
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
                    }
                  ]
                }
                """);

        var definition = new WorkflowDefinitionLoader().load(workflowPath);

        assertEquals("team-claude", definition.node("intake.claude_brainstorm").config().get("session"));
        assertEquals("explicit-codex", definition.node("review.codex").config().get("session"));
        assertEquals(1, definition.node("design.decision").config().get("max_revisions"));
        assertEquals(3, definition.node("gate.human_design_approval").config().get("max_revisions"));
    }

    @Test
    void fallsBackToBuiltInWorkflowWhenPathIsNull() throws Exception {
        var definition = new WorkflowDefinitionLoader().loadOrDefault(null);

        assertEquals("agent-lifecycle-full", definition.workflowId());
        assertEquals(10, definition.version(), "default full workflow should be loaded from the bundled JSON resource");
        assertEquals("context.intake_claude_brainstorm", definition.entryNodeId());
        assertEquals("context.build_package", definition.node("context.intake_claude_brainstorm").handlerType());
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
        var allowedProfiles = java.util.Set.of("contract_discovery", "observe", "review", "implement");

        for (var variant : java.util.List.of("full", "contract", "terminal-delivery")) {
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
    void bundledIntakeClaudeUsesContractDiscoveryPermissionProfile() throws Exception {
        var loader = new WorkflowDefinitionLoader();

        for (var variant : java.util.List.of("full", "contract", "terminal-delivery")) {
            var definition = loader.loadOrDefault(null, variant);

            assertEquals(
                    "contract_discovery",
                    definition.node("intake.claude_brainstorm").config().get("permission_profile"),
                    variant + " intake must not use the broad observe profile"
            );
        }
    }

    @Test
    void bundledContractWorkflowIsLoadedFromClasspathResource() throws Exception {
        var definition = new WorkflowDefinitionLoader().loadOrDefault(null, "contract");

        assertEquals("agent-lifecycle-contract", definition.workflowId());
        assertEquals(9, definition.version());
        assertEquals("context.intake_claude_brainstorm", definition.entryNodeId());
        assertEquals("context.build_package", definition.node("context.intake_claude_brainstorm").handlerType());
        assertEquals(NodeKind.TASK, definition.node("contract.compile").kind());
        assertEquals(NodeKind.TASK, definition.node("acceptance.classify_failure").kind());
    }

    @Test
    void bundledTerminalDeliveryWorkflowIsLoadedFromClasspathResource() throws Exception {
        var definition = new WorkflowDefinitionLoader().loadOrDefault(null, "terminal-delivery");

        assertEquals("agent-lifecycle-terminal-delivery", definition.workflowId());
        assertEquals(5, definition.version());
        assertEquals("context.intake_claude_brainstorm", definition.entryNodeId());
        assertEquals(31, definition.nodes().size());
        assertEquals(56, definition.edges().size());
    }

    @Test
    void bundledGateEdgesAreReachableFromVisibleActions() throws Exception {
        var loader = new WorkflowDefinitionLoader();

        for (var variant : java.util.List.of("full", "contract", "terminal-delivery")) {
            var definition = loader.loadOrDefault(null, variant);
            for (var gate : definition.nodes().stream().filter(node -> node.kind() == NodeKind.GATE).toList()) {
                var outgoingEdges = definition.edgesFrom(gate.id());
                var visibleActions = HumanGateActionSemantics.allowedActions(gate);
                for (var edge : outgoingEdges) {
                    assertTrue(visibleActions.stream().anyMatch(action -> edge.on().equals(
                                    HumanGateActionSemantics.canonicalAction(outgoingEdges, action))),
                            variant + ":" + gate.id() + " hides edge action " + edge.on());
                }
            }
        }
    }

    @Test
    void rejectsCustomWorkflowWithHiddenGateEdge() throws Exception {
        var workflowPath = tempDir.resolve("hidden-gate-action.json");
        Files.writeString(workflowPath, """
                {
                  "workflowId": "hidden-gate-action",
                  "version": 1,
                  "entryNodeId": "gate.review",
                  "nodes": [
                    {
                      "id": "gate.review",
                      "kind": "GATE",
                      "handlerType": "human.review",
                      "config": {"allowed_actions": ["approve"]}
                    },
                    {"id": "next", "kind": "TASK", "handlerType": "noop.task", "config": {}}
                  ],
                  "edges": [
                    {"from": "gate.review", "on": "revise", "to": "next"}
                  ]
                }
                """);

        var error = assertThrows(java.io.IOException.class,
                () -> new WorkflowDefinitionLoader().load(workflowPath));

        assertTrue(error.getMessage().contains("gate.review"));
        assertTrue(error.getMessage().contains("revise"));
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

    @Test
    void rejectsContractWorkflowWhoseApprovedSuccessorIsReachableBeforeApproval() throws Exception {
        var path = tempDir.resolve("overlap.json");
        Files.writeString(path, contractWorkflow("""
                {"from":"entry","on":"bypass","to":"post"},
                {"from":"entry","on":"completed","to":"contract-gate"},
                {"from":"contract-gate","on":"approved","to":"post"}
                """, "true"));

        var error = assertThrows(Exception.class, () -> new WorkflowDefinitionLoader().load(path));

        assertTrue(error.getMessage().contains("overlaps"), error.getMessage());
    }

    @Test
    void rejectsDeeperPostContractNodeThatIsAlsoReachableBeforeApproval() throws Exception {
        var path = tempDir.resolve("deep-overlap.json");
        Files.writeString(path, """
                {
                  "workflowId":"contract-boundary-test","version":1,"entryNodeId":"entry",
                  "nodes":[
                    {"id":"entry","kind":"TASK","handlerType":"noop.task","config":{}},
                    {"id":"contract-gate","kind":"GATE","handlerType":"human.review",
                     "config":{"gate_purpose":"contract","allowed_actions":["approve"]}},
                    {"id":"post-one","kind":"TASK","handlerType":"noop.task",
                     "config":{"requires_approved_task_contract":true}},
                    {"id":"post-two","kind":"TASK","handlerType":"noop.task",
                     "config":{"requires_approved_task_contract":true}}
                  ],
                  "edges":[
                    {"from":"entry","on":"bypass","to":"post-two"},
                    {"from":"entry","on":"completed","to":"contract-gate"},
                    {"from":"contract-gate","on":"approved","to":"post-one"},
                    {"from":"post-one","on":"completed","to":"post-two"}
                  ]
                }
                """);

        var error = assertThrows(Exception.class, () -> new WorkflowDefinitionLoader().load(path));

        assertTrue(error.getMessage().contains("overlaps"), error.getMessage());
        assertTrue(error.getMessage().contains("post-two"), error.getMessage());
    }

    @Test
    void rejectsMissingOrNonBooleanPostContractQualificationDeclaration() throws Exception {
        for (var value : java.util.List.of("null", "\"true\"")) {
            var path = tempDir.resolve("invalid-qualification-" + value.hashCode() + ".json");
            Files.writeString(path, contractWorkflow("""
                    {"from":"entry","on":"completed","to":"contract-gate"},
                    {"from":"contract-gate","on":"approved","to":"post"}
                    """, value));

            assertThrows(Exception.class, () -> new WorkflowDefinitionLoader().load(path));
        }
    }

    private static String contractWorkflow(String edges, String qualificationValue) {
        var postConfig = "null".equals(qualificationValue)
                ? "{}"
                : "{\"requires_approved_task_contract\":" + qualificationValue + "}";
        return """
                {
                  "workflowId":"contract-boundary-test","version":1,"entryNodeId":"entry",
                  "nodes":[
                    {"id":"entry","kind":"TASK","handlerType":"noop.task","config":{}},
                    {"id":"contract-gate","kind":"GATE","handlerType":"human.review",
                     "config":{"gate_purpose":"contract","allowed_actions":["approve"]}},
                    {"id":"post","kind":"TASK","handlerType":"noop.task","config":%s}
                  ],
                  "edges":[%s]
                }
                """.formatted(postConfig, edges);
    }
}
