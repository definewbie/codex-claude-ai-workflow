package dev.dual.adapter.acpx;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.dual.core.id.DualId;
import dev.dual.core.workflow.*;
import dev.dual.ledger.file.*;
import dev.dual.runtime.*;
import dev.dual.runtime.handler.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Legal authored predecessors for adapter tests; only provider output is synthetic. */
final class AuthoredAdapterFixture {
    private static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule());

    static TaskHandler.TaskContext before(TaskHandler.TaskContext requested) throws Exception {
        var root = requested.workspaceRoot();
        var ws = requested.workstreamId();
        var paths = new WorkstreamPaths(root, new DualId(ws));
        var ledger = new WorkflowFileLedger();
        if (!Files.exists(paths.workflowDefinitionJson())) {
            var nodes = List.of(agent("design.claude_design", "design_result"),
                    new NodeDefinition("ctx.design", NodeKind.TASK, "context.build_package", Map.of(
                            "target_node", "design.codex_arch_review", "profile", "architecture_review",
                            "requires_approved_task_contract", true)),
                    agent("design.codex_arch_review", "agent_output_contract"),
                    new NodeDefinition("design.decision", NodeKind.DECISION, "review.decision", Map.of()),
                    gate("gate.human_design_approval", "design"), agent("plan.claude_plan", "plan_result"),
                    gate("gate.human_plan_approval", "plan"),
                    agent("implement.claude_implement", "agent_output_contract"),
                    agent("implement.claude_self_test_report", "self_test_report"),
                    new NodeDefinition("contract.compile", NodeKind.TASK, "contract.compile", Map.of()),
                    gate("gate.contract_review", "contract"));
            var definition = new WorkflowDefinition("adapter-authored-fixture", 1, "design.claude_design", nodes,
                    List.of(edge("contract.compile", "completed", "gate.contract_review"),
                            edge("gate.contract_review", "approved", "design.claude_design"),
                            edge("design.claude_design", "completed", "ctx.design"),
                            edge("ctx.design", "completed", "design.codex_arch_review"),
                            edge("design.codex_arch_review", "completed", "design.decision"),
                            edge("design.decision", "READY_FOR_ARCH_REVIEW", "gate.human_design_approval"),
                            edge("gate.human_design_approval", "approved", "plan.claude_plan"),
                            edge("plan.claude_plan", "completed", "gate.human_plan_approval"),
                            edge("gate.human_plan_approval", "approved", "implement.claude_implement"),
                            edge("implement.claude_implement", "completed", "implement.claude_self_test_report")));
            ledger.initialize(paths, definition.workflowId(), definition);
            DesignContractFixture.approve(root, ws);
            var current = (ObjectNode) JSON.readTree(paths.workflowCurrentJson().toFile());
            current.put("lastEventId", "evt-000001");
            JSON.writeValue(paths.workflowCurrentJson().toFile(), current);
        }
        var provider = new TaskHandler() {
            public String type() { return "acpx.agent_run"; }
            public NodeResult execute(TaskContext context) throws Exception {
                var prepared = new AcpxAgentTaskHandler(new AcpxAgentTransport(), true).execute(context);
                assertEquals(NodeStatus.COMPLETED, prepared.status(), prepared.result().toString());
                var run = (String) prepared.result().get("run_id");
                var decision = switch (context.definition().id()) {
                    case "plan.claude_plan" -> "READY_FOR_PLAN_REVIEW";
                    case "implement.claude_implement" -> "IMPLEMENTED";
                    default -> "READY_FOR_ARCH_REVIEW";
                };
                var rendered = AgentResultPromptContract.render(ws, run, context.definition().id(), decision, Set.of(decision));
                var envelope = (ObjectNode) JSON.readTree(rendered.substring(rendered.indexOf('{')));
                var result = (ObjectNode) envelope.get("result");
                if (result.has("must_fix")) result.putArray("must_fix");
                var scope = JSON.readTree(paths.workstreamDir().resolve("artifacts/task-contract.json").toFile())
                        .at("/scope/included/0");
                if (result.has("design")) {
                    var file = (ObjectNode) result.at("/design/planned_files/0");
                    file.put("path", scope.path("path").asText());
                    file.put("operation", scope.path("operation").asText());
                }
                if (result.has("plan")) ((ObjectNode) result.get("plan")).putArray("edit_files").add(scope.path("path").asText());
                var output = paths.workstreamDir().resolve("runs/" + run + "/outputs/fixture.json");
                Files.createDirectories(output.getParent());
                JSON.writeValue(output.toFile(), envelope);
                return NodeResult.completed("completed", List.of(root.relativize(output).toString()), Map.of("run_id", run, "exit_code", 0));
            }
        };
        var runtime = new WorkflowRuntime(ledger, new HandlerRegistry(List.of(provider, new ContextBuildPackageHandler()),
                List.of(new ReviewDecisionHandler()), List.of(new HumanReviewGateHandler())));
        for (int step = 0; step < 30; step++) {
            var current = ledger.loadCurrent(paths);
            var active = ledger.loadCoreNode(paths, current.activeNodeInstanceIds().getFirst());
            if (active.nodeDefinitionId().equals(requested.definition().id())) {
                // Preserve the test's instance id and permission facts, but retain real routed inputs.
                var wanted = requested.instance();
                var inputs = new LinkedHashSet<>(active.inputRefs());
                inputs.addAll(wanted.inputRefs());
                var instance = new NodeInstance(wanted.nodeInstanceId(), wanted.nodeDefinitionId(), wanted.kind(),
                        wanted.handlerType(), wanted.status(), wanted.stageId(), active.iterationId(), List.copyOf(inputs),
                        wanted.outputRefs(), wanted.result(), wanted.runRef(), wanted.startedAt(), wanted.finishedAt());
                var persisted = (ObjectNode) JSON.readTree(paths.nodeJson(active.nodeInstanceId()).toFile());
                persisted.put("nodeInstanceId", instance.nodeInstanceId());
                persisted.set("inputRefs", JSON.valueToTree(instance.inputRefs()));
                persisted.set("result", JSON.valueToTree(instance.result()));
                JSON.writeValue(paths.nodeJson(instance.nodeInstanceId()).toFile(), persisted);
                var currentTree = (ObjectNode) JSON.readTree(paths.workflowCurrentJson().toFile());
                currentTree.putArray("activeNodeInstanceIds").add(instance.nodeInstanceId());
                JSON.writeValue(paths.workflowCurrentJson().toFile(), currentTree);
                var events = new ArrayList<String>();
                for (var line : Files.readAllLines(paths.eventsJsonl())) {
                    var event = (ObjectNode) JSON.readTree(line);
                    if (active.nodeInstanceId().equals(event.at("/payload/node_instance_id").asText()))
                        ((ObjectNode) event.get("payload")).put("node_instance_id", instance.nodeInstanceId());
                    events.add(JSON.writeValueAsString(event));
                }
                Files.write(paths.eventsJsonl(), events);
                return new TaskHandler.TaskContext(root, ws, requested.definition(), instance);
            }
            if (current.status() == WorkflowStatus.WAITING_FOR_HUMAN)
                ledger.resolveOpenGate(paths, ledger.loadDefinition(paths), "approve", "Fixture approves this candidate");
            else {
                assertEquals(WorkflowStatus.RUNNING, current.status(), active.toString());
                runtime.resume(new WorkflowCommand(root, ws));
            }
        }
        throw new AssertionError("authored fixture did not reach " + requested.definition().id());
    }

    private static NodeDefinition agent(String id, String artifact) {
        return new NodeDefinition(id, NodeKind.TASK, "acpx.agent_run", Map.of(
                "expected_artifact", artifact, "requires_approved_task_contract", true,
                "agent", id.contains("codex") ? "codex" : "claude", "session", "fixture",
                "prompt_template", switch (id) {
                    case "design.claude_design" -> "design-claude";
                    case "design.codex_arch_review" -> "design-codex-review";
                    case "plan.claude_plan" -> "plan-claude";
                    case "implement.claude_self_test_report" -> "implement-claude-self-test";
                    default -> "implement-claude";
                }));
    }
    private static NodeDefinition gate(String id, String purpose) {
        return new NodeDefinition(id, NodeKind.GATE, "human.review", Map.of(
                "gate_purpose", purpose, "allowed_actions", List.of("approved", "revise")));
    }
    private static EdgeDefinition edge(String from, String outcome, String to) { return new EdgeDefinition(from, outcome, to); }
}
