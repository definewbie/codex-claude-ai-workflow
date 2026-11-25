package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.dual.core.id.DualId;
import dev.dual.core.workflow.*;
import dev.dual.ledger.file.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Test adapter input recording; semantic identities are still resolved by production code. */
public final class AuthoredInputFixture {
    private static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule()).registerModule(new EdgeConditionModule());

    public static TaskHandler.TaskContext before(Path root, String ws, String target, String run) throws Exception {
        var fixture = new AuthoredStageSequenceTest.Fixture(root, "src/Main.java",
                AuthoredStageSequenceTest.PlanTopology.DIRECT_HUMAN, ws);
        var context = fixture.before(target);
        record(context, run);
        return context;
    }

    static void publish(TaskHandler.TaskContext context, Path source, String run) throws Exception {
        var root = context.workspaceRoot();
        var sidecar = AgentOutputContractSidecar.pathFor(source);
        var tree = (com.fasterxml.jackson.databind.node.ObjectNode) JSON.readTree(sidecar.toFile());
        tree.put("workstream_id", context.workstreamId());
        tree.put("run_id", run);
        tree.put("node_id", context.definition().id());
        tree.put("node_instance_id", context.instance().nodeInstanceId());
        tree.put("source_output_ref", root.relativize(source).toString());
        var manifest = ".agent/workstreams/" + context.workstreamId() + "/runs/" + run + "/prompts/prompt.md.manifest.json";
        tree.set("authored_context", JSON.valueToTree(AuthoredExecutionContext.reference(root, manifest)));
        tree.putArray("revision_responses");
        JSON.writeValue(sidecar.toFile(), tree);
        AgentOutputLineageFixture.publish(root, context.workstreamId(), sidecar,
                context.instance().iterationId(), context.instance().inputRefs());
    }

    static void record(TaskHandler.TaskContext context, String run) throws Exception {
        var snapshot = AuthoredExecutionContext.prepare(context, run).orElseThrow();
        var root = context.workspaceRoot();
        var prompts = root.resolve(".agent/workstreams/" + context.workstreamId() + "/runs/" + run + "/prompts");
        Files.createDirectories(prompts);
        var prompt = prompts.resolve("prompt.md");
        var manifest = prompts.resolve("prompt.md.manifest.json");
        var body = snapshot.render(root);
        Files.writeString(prompt, body);
        JSON.writeValue(manifest.toFile(), Map.of("authoredContext", snapshot, "workstreamId", context.workstreamId(),
                "runId", run, "nodeId", context.definition().id(), "promptPath", prompt.toString(),
                "sections", List.of(Map.of("name", AuthoredExecutionContext.SECTION_NAME,
                        "sourceRef", context.instance().nodeInstanceId(), "required", true,
                        "sha256", TaskContractArtifactWriter.sha256(body.getBytes(java.nio.charset.StandardCharsets.UTF_8))))));
        var runPath = prompts.getParent().resolve("run.json");
        var metadata = Files.exists(runPath) ? (com.fasterxml.jackson.databind.node.ObjectNode) JSON.readTree(runPath.toFile())
                : JSON.createObjectNode();
        metadata.put("promptManifestPath", root.relativize(manifest).toString());
        metadata.put("runId", run); metadata.put("workstreamId", context.workstreamId());
        metadata.set("processResult", JSON.valueToTree(dev.dual.core.execution.ProcessResult.exitedZero()));
        metadata.putArray("adapterSignals");
        JSON.writeValue(runPath.toFile(), metadata);
        AuthoredExecutionContext.verifyManifest(root, manifest, snapshot);
    }

    static void initialDesign(Path root, String ws, String nodeId, String run) throws Exception {
        var paths = new WorkstreamPaths(root, new DualId(ws));
        var author = new NodeDefinition("design.claude_design", NodeKind.TASK, "acpx.agent_run", Map.of(
                "expected_artifact", "design_result", "requires_approved_task_contract", true));
        if (!Files.exists(paths.workflowDefinitionJson())) {
            Files.createDirectories(paths.workflowDefinitionJson().getParent());
            JSON.writeValue(paths.workflowDefinitionJson().toFile(), new WorkflowDefinitionDocument(1, "scope-fixture", 1, author.id(),
                    List.of(author, new NodeDefinition("contract.compile", NodeKind.TASK, "contract.compile", Map.of()),
                            new NodeDefinition("gate.contract_review", NodeKind.GATE, "human.review", Map.of("gate_purpose", "contract"))),
                    List.of(new EdgeDefinition("contract.compile", "completed", "gate.contract_review"),
                            new EdgeDefinition("gate.contract_review", "approved", author.id()))));
        }
        Files.createDirectories(paths.iterationsDir());
        if (!Files.exists(paths.iterationJson("iter-001"))) JSON.writeValue(paths.iterationJson("iter-001").toFile(),
                WorkflowIterationDocument.initial(author.id(), Instant.parse("2026-09-05T00:00:00Z")));
        var instance = new NodeInstanceDocument(1, nodeId, author.id(), NodeKind.TASK, author.handlerType(), NodeStatus.RUNNING,
                "stage-design", "iter-001", List.of(), Map.of(), List.of(), Map.of(), null, "", Instant.now(), null);
        JSON.writeValue(paths.nodeJson(nodeId).toFile(), instance);
        record(new TaskHandler.TaskContext(root, ws, author, dev.dual.runtime.RuntimeTestLedger.ledger().loadCoreNode(paths, nodeId), dev.dual.runtime.RuntimeTestLedger.ledger()), run);
    }
}
