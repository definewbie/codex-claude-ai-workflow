package dev.dual.runtime.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeStatus;
import dev.dual.ledger.file.NodeExecutionDocument;
import dev.dual.ledger.file.NodeExecutionStatus;
import dev.dual.ledger.file.NodeInstanceDocument;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class AgentOutputLineageFixture {
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private AgentOutputLineageFixture() {
    }

    public static void completeAndPublish(Path workspaceRoot, String workstreamId, Path sidecarPath) throws Exception {
        completeAndPublish(workspaceRoot, workstreamId, sidecarPath, "iteration-fixture", List.of());
    }

    public static void completeAndPublish(
            Path workspaceRoot,
            String workstreamId,
            Path sidecarPath,
            String iterationId,
            List<String> inputRefs
    ) throws Exception {
        var tree = MAPPER.readTree(sidecarPath.toFile());
        if (!(tree instanceof ObjectNode sidecar)) {
            throw new IllegalArgumentException("agent output sidecar must be a JSON object");
        }
        var runId = runId(sidecarPath);
        var source = sourcePath(sidecarPath);
        sidecar.put("workstream_id", workstreamId);
        sidecar.put("run_id", runId);
        sidecar.put("node_instance_id", "node-fixture-" + runId + "-"
                + sidecarPath.getFileName().toString().replaceAll("[^A-Za-z0-9._-]", "-"));
        sidecar.put("source_output_ref", relative(workspaceRoot, source));
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(sidecarPath.toFile(), sidecar);
        publish(workspaceRoot, workstreamId, sidecarPath, iterationId, inputRefs);
    }

    public static void publish(Path workspaceRoot, String workstreamId, Path sidecarPath) throws Exception {
        publish(workspaceRoot, workstreamId, sidecarPath, "iteration-fixture");
    }

    public static void publish(
            Path workspaceRoot,
            String workstreamId,
            Path sidecarPath,
            String iterationId
    ) throws Exception {
        publish(workspaceRoot, workstreamId, sidecarPath, iterationId, List.of());
    }

    public static void publish(
            Path workspaceRoot,
            String workstreamId,
            Path sidecarPath,
            String iterationId,
            List<String> inputRefs
    ) throws Exception {
        var sidecar = MAPPER.readValue(sidecarPath.toFile(), MAP_TYPE);
        var runId = text(sidecar.get("run_id"));
        var nodeId = text(sidecar.get("node_id"));
        var nodeInstanceId = text(sidecar.get("node_instance_id"));
        var sourceRef = text(sidecar.get("source_output_ref"));
        if (runId.isBlank() || nodeId.isBlank() || nodeInstanceId.isBlank() || sourceRef.isBlank()) {
            return;
        }
        var source = workspaceRoot.resolve(sourceRef).normalize();
        Files.createDirectories(source.getParent());
        if (!Files.exists(source)) {
            Files.writeString(source, "{}");
        }
        var sidecarRef = relative(workspaceRoot, sidecarPath);
        var decision = text(sidecar.get("decision"));
        var outputs = List.of(sourceRef, sidecarRef);
        var result = Map.<String, Object>of(
                "run_id", runId,
                "decision", decision,
                "contract_sidecar_sha256", AgentOutputContractSidecar.sha256(sidecarPath));
        var executionId = "nxe-" + nodeInstanceId.replaceAll("[^A-Za-z0-9._-]", "-");
        var workstreamDir = workspaceRoot.resolve(".agent/workstreams").resolve(workstreamId);
        var nodesDir = workstreamDir.resolve("nodes");
        var executionsDir = workstreamDir.resolve("executions/nodes");
        Files.createDirectories(nodesDir);
        Files.createDirectories(executionsDir);
        var now = Instant.parse("2026-07-21T00:00:00Z");
        MAPPER.writeValue(nodesDir.resolve(nodeInstanceId + ".json").toFile(), new NodeInstanceDocument(
                1, nodeInstanceId, nodeId, NodeKind.TASK, "acpx.agent_run", NodeStatus.COMPLETED,
                "stage-fixture", iterationId, inputRefs, Map.of(), outputs,
                result, runId, executionId, now, now));
        MAPPER.writeValue(executionsDir.resolve(executionId + ".json").toFile(), new NodeExecutionDocument(
                1, executionId, "agent-task-fixture", "workflow-execution-fixture",
                nodeInstanceId, nodeId, "acpx.agent_run", NodeExecutionStatus.SUCCEEDED,
                1L, "completed", NodeStatus.COMPLETED, outputs,
                result, "",
                runId, "", "fixture-agent", "fixture-session", 0, now, now, 1L));
    }

    private static String relative(Path root, Path path) {
        return root.toAbsolutePath().normalize().relativize(path.toAbsolutePath().normalize()).toString();
    }

    private static String runId(Path sidecarPath) {
        for (var index = 0; index + 1 < sidecarPath.getNameCount(); index++) {
            if ("runs".equals(sidecarPath.getName(index).toString())) {
                return sidecarPath.getName(index + 1).toString();
            }
        }
        throw new IllegalArgumentException("sidecar is not under a run: " + sidecarPath);
    }

    private static Path sourcePath(Path sidecarPath) {
        var name = sidecarPath.getFileName().toString();
        if (!name.endsWith(".contract.json")) {
            throw new IllegalArgumentException("not an agent output sidecar: " + sidecarPath);
        }
        var stem = name.substring(0, name.length() - ".contract.json".length());
        var json = sidecarPath.resolveSibling(stem + ".json");
        var markdown = sidecarPath.resolveSibling(stem + ".md");
        if (Files.exists(json)) {
            return json;
        }
        return Files.exists(markdown) ? markdown : json;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
