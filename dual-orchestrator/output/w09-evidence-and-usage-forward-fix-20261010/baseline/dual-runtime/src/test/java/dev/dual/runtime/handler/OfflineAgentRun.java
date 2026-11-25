package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.*;

/** Explicit offline transport fixture: real qualification/R01/R02, simulated process facts. */
public final class OfflineAgentRun {
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    final TaskHandler.TaskContext context;
    final String runId;
    final Path dir;
    final ObjectNode metadata;
    final AgentRunGovernanceContext.PreparedContext governance;
    final AgentRunCommitProtocol.Prepared prepared;
    final VerificationEvidence.Baseline baseline;
    final List<dev.dual.core.execution.AgentExecutionEvidence.ToolObservation> observations = new ArrayList<>();

    private OfflineAgentRun(TaskHandler.TaskContext context, String runId) throws Exception {
        this.context = context; this.runId = runId;
        governance = AgentRunGovernanceContext.ensureQualified(context, runId);
        dir = context.workspaceRoot().resolve(".agent/workstreams/" + context.workstreamId() + "/runs/" + runId);
        Files.createDirectories(dir.resolve("outputs")); Files.createDirectories(dir.resolve("prompts"));
        metadata = JSON.createObjectNode();
        metadata.put("schemaVersion", 3).put("runId", runId).put("workstreamId", context.workstreamId())
                .put("nodeId", context.definition().id()).put("agent", "offline-fixture")
                .put("session", "offline-" + runId).put("status", "prepared");
        var prompt = dir.resolve("prompts/prompt.md");
        var inputs = new ArrayList<Path>(); inputs.add(prompt);
        if (governance.authoredContext().isPresent()) {
            var snapshot = governance.authoredContext().orElseThrow(); var body = snapshot.render(context.workspaceRoot());
            Files.writeString(prompt, body);
            var manifest = dir.resolve("prompts/prompt.md.manifest.json");
            JSON.writeValue(manifest.toFile(), Map.of("authoredContext", snapshot, "workstreamId", context.workstreamId(),
                    "runId", runId, "nodeId", context.definition().id(), "promptPath", prompt.toString(),
                    "sections", List.of(Map.of("name", AuthoredExecutionContext.SECTION_NAME,
                            "sourceRef", context.instance().nodeInstanceId(), "required", true,
                            "sha256", TaskContractArtifactWriter.sha256(body.getBytes(java.nio.charset.StandardCharsets.UTF_8))))));
            metadata.put("promptManifestPath", manifest.toString()); inputs.add(manifest);
        } else Files.writeString(prompt, "Offline deterministic transport for " + context.definition().id());
        JSON.writeValue(dir.resolve("run.json").toFile(), metadata);
        var facts = new LinkedHashMap<String, Object>(); facts.put("run", metadata);
        if (governance.qualifiedTaskContract().isPresent()) {
            facts.put("contract_ref", governance.taskContractRef()); facts.put("contract_sha256", governance.taskContractSha256());
        }
        baseline = governance.qualifiedTaskContract().filter(c -> c.phase() == dev.dual.core.contract.TaskContract.Phase.EXECUTION)
                .isPresent() ? VerificationEvidence.prepare(context) : null;
        if (baseline != null) facts.put("verification_source", baseline);
        prepared = AgentRunCommitProtocol.prepare(context, dir, inputs, facts);
        AgentRunCommitProtocol.qualifyDispatch(context, prepared);
        AgentRunCommitProtocol.beginExternalCall(context, prepared);
    }
    public static OfflineAgentRun start(TaskHandler.TaskContext context, String runId) throws Exception { return new OfflineAgentRun(context, runId); }
    /** Explicit fixture action: read a real local file; does not prove a native client ran tests. */
    public void observeRead(ObjectNode result, String ref) throws Exception {
        var content = Files.readString(context.workspaceRoot().resolve(ref));
        observations.add(new dev.dual.core.execution.AgentExecutionEvidence.ToolObservation("offline-" + runId, "read-file", "fixture.read", "read",
                JSON.valueToTree(Map.of("path", ref)), JSON.valueToTree(Map.of("content", content)), "completed", null, null, "", false, List.of()));
        result.set("verification_checks", JSON.valueToTree(governance.qualifiedTaskContract().orElseThrow().verificationBindings().stream()
                .map(b -> new AgentResultEnvelope.VerificationCheck(b.criterionId(), "Inspected the fixture material", "CURRENT", List.of("read-file"), List.of())).toList()));
    }
    public dev.dual.core.workflow.NodeResult candidate(List<String> outputRefs) throws Exception {
        metadata.put("status", "process_exited");
        metadata.set("processResult", JSON.valueToTree(Map.of("exitCode", 0, "usability", "EXITED_ZERO", "durationMs", 1)));
        metadata.putArray("adapterSignals"); JSON.writeValue(dir.resolve("run.json").toFile(), metadata);
        var binding = governance.repositoryFactBinding();
        var refs = new ArrayList<>(outputRefs); refs.add(binding.ref());
        var result = dev.dual.core.workflow.NodeResult.completed("completed", refs, Map.of("run_id", runId, "exit_code", 0,
                "run_metadata", context.workspaceRoot().relativize(dir.resolve("run.json")).toString(),
                "repository_fact_binding_ref", binding.ref(), "repository_fact_binding_sha256", binding.sha256()));
        return VerificationEvidence.record(context, prepared, baseline, "offline-fixture", observations, result);
    }
    public dev.dual.core.workflow.NodeResult exited(List<String> outputRefs) throws Exception {
        var result = candidate(outputRefs);
        var parsed = new AgentResultEnvelopeParser().parse(Files.readString(context.workspaceRoot().resolve(outputRefs.getFirst())));
        if (!parsed.valid()) throw new IllegalArgumentException("offline fixture emitted an invalid envelope: " + parsed.message());
        return AgentExecutionResultFacts.attach(result,
                dev.dual.core.execution.AgentResultContractResult.valid(parsed.envelope().result().decision()), List.of(), null);
    }
}
