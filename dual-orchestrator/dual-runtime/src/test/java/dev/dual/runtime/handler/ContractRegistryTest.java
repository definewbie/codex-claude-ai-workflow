package dev.dual.runtime.handler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.WorkflowDefinition;
import dev.dual.runtime.lifecycle.WorkflowDefinitionLoader;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContractRegistryTest {

    @Test
    void allDecisionNodesHaveContracts() {
        var decisionNodes = List.of("intake.decision", "design.decision", "plan.decision", "acceptance.decision");
        for (var nodeId : decisionNodes) {
            assertTrue(ContractRegistry.hasContract(nodeId), nodeId + " missing from ContractRegistry");
        }
    }

    @Test
    void allTaskNodesHaveContracts() {
        var taskNodes = List.of(
                "intake.claude_brainstorm", "intake.codex_product_review",
                "design.claude_design", "design.codex_arch_review",
                "plan.claude_plan", "plan.codex_plan_review",
                "implement.claude_implement", "implement.claude_self_test_report",
                "acceptance.codex_verifier_review", "acceptance.classify_failure", "stage.claude_handoff"
        );
        for (var nodeId : taskNodes) {
            assertTrue(ContractRegistry.hasContract(nodeId), nodeId + " missing from ContractRegistry");
        }
    }

    @Test
    void forNodeThrowsOnUnknownNode() {
        assertThrows(IllegalArgumentException.class, () -> ContractRegistry.forNode("nonexistent.node"));
    }

    @Test
    void hasContractReturnsFalseForUnknown() {
        assertFalse(ContractRegistry.hasContract("nonexistent.node"));
    }

    @Test
    void everyContractRequiresDecisionAndSummary() {
        for (var nodeId : ContractRegistry.allNodeIds()) {
            var contract = ContractRegistry.forNode(nodeId);
            assertTrue(contract.requiredKeys().contains("decision"),
                    nodeId + " contract missing 'decision' in requiredKeys");
            assertTrue(contract.requiredKeys().contains("summary"),
                    nodeId + " contract missing 'summary' in requiredKeys");
        }
    }

    static Stream<Object[]> validDecisionCases() {
        return Stream.of(
                new Object[]{"intake.claude_brainstorm", "READY_FOR_PRODUCT_REVIEW"},
                new Object[]{"intake.codex_product_review", "READY_FOR_PRODUCT_REVIEW"},
                new Object[]{"intake.codex_product_review", "NEEDS_REVISION"},
                new Object[]{"intake.codex_product_review", "BLOCKED"},
                new Object[]{"design.claude_design", "READY_FOR_ARCH_REVIEW"},
                new Object[]{"design.codex_arch_review", "READY_FOR_ARCH_REVIEW"},
                new Object[]{"plan.claude_plan", "READY_FOR_PLAN_REVIEW"},
                new Object[]{"plan.codex_plan_review", "READY_TO_IMPLEMENT"},
                new Object[]{"implement.claude_implement", "IMPLEMENTED"},
                new Object[]{"implement.claude_self_test_report", "SELF_TEST_COMPLETE"},
                new Object[]{"acceptance.codex_verifier_review", "ACCEPT"},
                new Object[]{"acceptance.codex_verifier_review", "ACCEPT_WITH_NOTES"},
                new Object[]{"acceptance.codex_verifier_review", "REJECT"},
                new Object[]{"stage.claude_handoff", "HANDOFF_READY"},
                new Object[]{"intake.decision", "READY_FOR_PRODUCT_REVIEW"},
                new Object[]{"intake.decision", "NEEDS_REVISION"},
                new Object[]{"design.decision", "READY_FOR_ARCH_REVIEW"},
                new Object[]{"plan.decision", "READY_TO_IMPLEMENT"},
                new Object[]{"acceptance.decision", "ACCEPT"},
                new Object[]{"acceptance.decision", "BLOCKED"}
        );
    }

    @ParameterizedTest
    @MethodSource("validDecisionCases")
    void contractAcceptsValidDecision(String nodeId, String decisionValue) {
        var contract = ContractRegistry.forNode(nodeId);
        var block = new DualControlBlock(Map.of("decision", decisionValue, "summary", "test"));
        assertDoesNotThrow(() -> contract.validate(block));
    }

    static Stream<Object[]> invalidDecisionCases() {
        return Stream.of(
                new Object[]{"intake.claude_brainstorm", "PASS"},
                new Object[]{"intake.claude_brainstorm", "NEEDS_REVISION"},
                new Object[]{"intake.codex_product_review", "PASS"},
                new Object[]{"design.claude_design", "PASS"},
                new Object[]{"design.codex_arch_review", "PASS"},
                new Object[]{"plan.claude_plan", "PASS"},
                new Object[]{"implement.claude_implement", "PASS"},
                new Object[]{"implement.claude_self_test_report", "PASS"},
                new Object[]{"acceptance.codex_verifier_review", "PASS"},
                new Object[]{"stage.claude_handoff", "PASS"},
                new Object[]{"intake.decision", "PASS"},
                new Object[]{"design.decision", "PASS"},
                new Object[]{"acceptance.decision", "PASS"},
                new Object[]{"plan.decision", "PASS"}
        );
    }

    @ParameterizedTest
    @MethodSource("invalidDecisionCases")
    void contractRejectsInvalidDecision(String nodeId, String badValue) {
        var contract = ContractRegistry.forNode(nodeId);
        assertFalse(contract.allowedDecisions().contains(badValue),
                nodeId + " should not accept '" + badValue + "'");
        var block = new DualControlBlock(Map.of("decision", badValue, "summary", "test"));
        assertThrows(IllegalArgumentException.class, () -> contract.validate(block));
    }

    @Test
    void contractRejectsMissingSummary() {
        var contract = ContractRegistry.forNode("design.decision");
        var block = new DualControlBlock(Map.of("decision", "READY_FOR_ARCH_REVIEW"));
        assertThrows(IllegalArgumentException.class, () -> contract.validate(block));
    }

    @Test
    void contractRejectsBlankSummary() {
        var contract = ContractRegistry.forNode("design.decision");
        var block = new DualControlBlock(Map.of("decision", "READY_FOR_ARCH_REVIEW", "summary", "  "));
        assertThrows(IllegalArgumentException.class, () -> contract.validate(block));
    }

    @Test
    void intakeBrainstormAcceptsOptionalNeedsHumanInput() {
        var contract = ContractRegistry.forNode("intake.claude_brainstorm");
        assertTrue(contract.optionalKeys().contains("needs_human_input"));
        var block = new DualControlBlock(Map.of(
                "decision", "READY_FOR_PRODUCT_REVIEW",
                "summary", "done",
                "needs_human_input", "true"
        ));
        assertDoesNotThrow(() -> contract.validate(block));
    }

    @Test
    void intakeBrainstormDoesNotRequireNeedsHumanInput() {
        var contract = ContractRegistry.forNode("intake.claude_brainstorm");
        var block = new DualControlBlock(Map.of(
                "decision", "READY_FOR_PRODUCT_REVIEW",
                "summary", "done"
        ));
        assertDoesNotThrow(() -> contract.validate(block));
    }

    @Test
    void decisionNodeContractsMatchEdgeRouting() {
        var decisionContracts = Map.of(
                "intake.decision", Set.of("READY_FOR_PRODUCT_REVIEW", "NEEDS_REVISION", "BLOCKED"),
                "design.decision", Set.of("READY_FOR_ARCH_REVIEW", "NEEDS_REVISION", "BLOCKED"),
                "plan.decision", Set.of("READY_TO_IMPLEMENT", "NEEDS_REVISION", "BLOCKED"),
                "acceptance.decision", Set.of("ACCEPT", "ACCEPT_WITH_NOTES", "REJECT", "BLOCKED")
        );
        for (var entry : decisionContracts.entrySet()) {
            var contract = ContractRegistry.forNode(entry.getKey());
            assertEquals(entry.getValue(), contract.allowedDecisions(),
                    entry.getKey() + " allowedDecisions mismatch");
        }
    }

    @Test
    void registryEntriesPointToBundledWorkflowNodes() throws Exception {
        var bundledNodeIds = new HashSet<String>();
        for (var definition : bundledDefinitions()) {
            definition.nodes().stream().map(NodeDefinition::id).forEach(bundledNodeIds::add);
        }

        for (var nodeId : ContractRegistry.allNodeIds()) {
            assertTrue(bundledNodeIds.contains(nodeId),
                    nodeId + " exists in ContractRegistry but no bundled workflow defines it");
        }
    }

    @Test
    void bundledWorkflowNodesThatNeedContractsHaveContracts() throws Exception {
        for (var definition : bundledDefinitions()) {
            for (var node : definition.nodes()) {
                if (requiresContract(node)) {
                    assertTrue(ContractRegistry.hasContract(node.id()),
                            definition.workflowId() + " node " + node.id()
                                    + " uses " + node.handlerType()
                                    + " but has no output contract");
                }
            }
        }
    }

    @Test
    void decisionNodeRoutingOutcomesMatchAllowedContractDecisions() throws Exception {
        for (var definition : bundledDefinitions()) {
            for (var node : definition.nodes()) {
                if (node.kind() != NodeKind.DECISION || !ContractRegistry.hasContract(node.id())) {
                    continue;
                }
                var contract = ContractRegistry.forNode(node.id());
                var edgeOutcomes = definition.edgesFrom(node.id()).stream()
                        .map(edge -> edge.on())
                        .collect(java.util.stream.Collectors.toSet());

                assertTrue(contract.allowedDecisions().containsAll(edgeOutcomes),
                        definition.workflowId() + " decision node " + node.id()
                                + " has edge outcomes not allowed by contract: " + edgeOutcomes
                                + " vs " + contract.allowedDecisions());
                assertTrue(edgeOutcomes.containsAll(
                                contract.allowedDecisions().stream()
                                        .filter(decision -> !"BLOCKED".equals(decision))
                                        .collect(java.util.stream.Collectors.toSet())),
                        definition.workflowId() + " decision node " + node.id()
                                + " has contract decisions with no edge. Only BLOCKED may stop without routing.");
            }
        }
    }

    @Test
    void failureClassifierRoutingOutcomesMatchAllowedContractDecisions() throws Exception {
        for (var definition : bundledDefinitions()) {
            for (var node : definition.nodes()) {
                if (!"acceptance.failure_classify".equals(node.handlerType())) {
                    continue;
                }
                var contract = ContractRegistry.forNode(node.id());
                var edgeOutcomes = definition.edgesFrom(node.id()).stream()
                        .map(edge -> edge.on())
                        .collect(java.util.stream.Collectors.toSet());

                assertEquals(contract.allowedDecisions(), edgeOutcomes,
                        definition.workflowId() + " classifier node " + node.id()
                                + " must keep its contract decisions exactly aligned with routing edges");
            }
        }
    }

    @Test
    void allKnownKeysIncludesOptional() {
        var contract = ContractRegistry.forNode("intake.claude_brainstorm");
        assertTrue(contract.allKnownKeys().contains("needs_human_input"));
        assertTrue(contract.allKnownKeys().contains("decision"));
        assertTrue(contract.allKnownKeys().contains("summary"));
    }

    private static List<WorkflowDefinition> bundledDefinitions() throws Exception {
        var loader = new WorkflowDefinitionLoader();
        return List.of(
                loader.loadOrDefault(null, "full"),
                loader.loadOrDefault(null, "contract")
        );
    }

    private static boolean requiresContract(NodeDefinition node) {
        return "acpx.agent_run".equals(node.handlerType())
                || "review.decision".equals(node.handlerType())
                || "contract.compile".equals(node.handlerType())
                || "acceptance.failure_classify".equals(node.handlerType());
    }
}
