package dev.dual.runtime.lifecycle;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.dual.core.workflow.WorkflowDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class AcceptanceRepairWorkflowDefinitionTest {
    static final List<String> RESOURCES = List.of("full", "contract", "terminal-delivery",
            "small-plan-human-review-experiment");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    @TempDir Path root;

    @Test
    void allFourDefinitionsDeclareBoundedContractRepairViaProposalContext() throws Exception {
        for (var variant : RESOURCES) {
            var definition = loadResource(root, variant);
            assertEquals(3, definition.node("acceptance.classify_failure").config().get("max_revisions"));
            for (var outcome : List.of("REJECT_CONTRACT_GAP", "REJECT_CONTRACT_AMBIGUITY")) {
                var edges = definition.edgesFrom("acceptance.classify_failure").stream()
                        .filter(edge -> outcome.equals(edge.on())).toList();
                assertEquals(1, edges.size());
                assertEquals("context.intake_claude_brainstorm", edges.getFirst().to());
                assertEquals("loop", edges.getFirst().metadata().transitionType());
            }
            assertEquals("intake.claude_brainstorm",
                    definition.edgesFrom("context.intake_claude_brainstorm").getFirst().to());
            assertEquals("task_contract_proposal",
                    definition.node("intake.claude_brainstorm").config().get("expected_artifact"));
            assertFalse(Boolean.TRUE.equals(definition.node("context.intake_claude_brainstorm")
                    .config().get("requires_approved_task_contract")));
        }
    }

    @Test
    void customWorkflowIdentityDoesNotNeedABundledName() throws Exception {
        var json = resource("full");
        json.put("workflowId", "custom-documentation-workflow");
        assertEquals("custom-documentation-workflow", load(json).workflowId());
    }

    @Test
    void rejectsInvalidContractRepairSourcesTargetsAndProducers() throws Exception {
        List<Consumer<ObjectNode>> mutations = List.of(
                json -> repair(json).put("from", "acceptance.decision"),
                json -> node(json, "acceptance.classify_failure").put("handlerType", "noop.task"),
                json -> node(json, "acceptance.classify_failure").put("kind", "DECISION"),
                json -> config(json, "acceptance.classify_failure").put("requires_approved_task_contract", false),
                json -> repair(json).put("to", "contract.compile"),
                json -> repair(json).put("to", "intake.claude_brainstorm"),
                json -> repair(json).put("to", "context.intake_codex_product_review"),
                json -> repair(json).withObject("metadata").put("transitionType", "normal"),
                json -> repair(json).withObject("metadata").put("branchGroupId", "fan-out"),
                json -> repair(json).withObject("metadata").put("required", false),
                json -> repair(json).putObject("condition").put("type", "config_equals")
                        .putObject("params").put("key", "x").put("value", "y"),
                json -> node(json, "context.intake_claude_brainstorm").put("handlerType", "noop.task"),
                json -> config(json, "context.intake_claude_brainstorm").put("target_node", "contract.compile"),
                json -> edge(json, "context.intake_claude_brainstorm", "completed").put("to", "contract.compile"),
                json -> config(json, "intake.claude_brainstorm").put("expected_artifact", "agent_output_contract"),
                json -> config(json, "intake.claude_brainstorm").put("artifact_compiler", "agent_output_contract_compiler"),
                json -> node(json, "intake.claude_brainstorm").put("handlerType", "noop.task"),
                json -> json.withArray("edges").add(repair(json).deepCopy()),
                json -> json.put("entryNodeId", "acceptance.classify_failure")
        );
        for (int i = 0; i < mutations.size(); i++) {
            var json = resource("full");
            mutations.get(i).accept(json);
            assertThrows(IOException.class, () -> load(json), "mutation " + i);
        }
    }

    @Test
    void otherOutcomesCannotReuseNegotiationOrCompileEntry() throws Exception {
        for (var target : List.of("context.intake_claude_brainstorm", "intake.claude_brainstorm",
                "contract.compile", "gate.human_requirement_review")) {
            var json = resource("full");
            edge(json, "acceptance.classify_failure", "REJECT_IMPLEMENTATION_BUG").put("to", target);
            assertThrows(IOException.class, () -> load(json), target);
        }
    }

    static WorkflowDefinition loadResource(Path root, String variant) throws Exception {
        var path = root.resolve(variant + ".json");
        MAPPER.writeValue(path.toFile(), resource(variant));
        return new WorkflowDefinitionLoader().load(path);
    }

    private WorkflowDefinition load(ObjectNode json) throws Exception {
        var path = root.resolve("custom.json");
        MAPPER.writeValue(path.toFile(), json);
        return new WorkflowDefinitionLoader().load(path);
    }

    private static ObjectNode resource(String variant) throws Exception {
        try (var stream = AcceptanceRepairWorkflowDefinitionTest.class.getResourceAsStream(
                "/dev/dual/runtime/lifecycle/agent-lifecycle." + variant + ".json")) {
            assertNotNull(stream, variant);
            return (ObjectNode) MAPPER.readTree(stream);
        }
    }

    private static ObjectNode repair(ObjectNode json) {
        return edge(json, "acceptance.classify_failure", "REJECT_CONTRACT_GAP");
    }

    private static ObjectNode edge(ObjectNode json, String from, String outcome) {
        for (var edge : json.withArray("edges")) {
            if (from.equals(edge.path("from").asText()) && outcome.equals(edge.path("on").asText())) {
                return (ObjectNode) edge;
            }
        }
        throw new AssertionError(from + ":" + outcome);
    }

    private static ObjectNode node(ObjectNode json, String id) {
        for (var node : json.withArray("nodes")) {
            if (id.equals(node.path("id").asText())) return (ObjectNode) node;
        }
        throw new AssertionError(id);
    }

    private static ObjectNode config(ObjectNode json, String id) {
        return node(json, id).withObject("config");
    }
}
