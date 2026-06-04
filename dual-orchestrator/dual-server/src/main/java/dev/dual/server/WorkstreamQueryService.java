package dev.dual.server;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dual.core.id.DualId;
import dev.dual.core.workflow.NodeKind;
import dev.dual.ledger.file.FileLedgerStore;
import dev.dual.ledger.file.WorkflowFileLedger;
import dev.dual.ledger.file.WorkstreamPaths;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Service
public class WorkstreamQueryService {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final FileLedgerStore legacyStore;
    private final WorkflowFileLedger workflowLedger;

    public WorkstreamQueryService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.legacyStore = new FileLedgerStore();
        this.workflowLedger = new WorkflowFileLedger();
    }

    public Map<String, Object> status(Path workspaceRoot, String workstreamId) throws IOException {
        var root = workspaceRoot.toAbsolutePath().normalize();
        var paths = new WorkstreamPaths(root, new DualId(workstreamId));
        var legacy = legacyStore.loadStatus(root, workstreamId);

        var response = new LinkedHashMap<String, Object>();
        response.put("workstreamId", legacy.workstream().workstreamId());
        response.put("ledger", root.relativize(paths.workstreamDir()).toString());
        response.put("workspace", Map.of(
                "effectiveWorkdir", legacy.workspace().effectiveWorkdir(),
                "branch", legacy.workspace().branch(),
                "baseBranch", legacy.workspace().baseBranch(),
                "baseCommit", legacy.workspace().baseCommit()
        ));

        if (Files.exists(paths.workflowCurrentJson())) {
            var current = workflowLedger.loadCurrent(paths);
            var activeNodes = current.activeNodeInstanceIds().stream()
                    .map(nodeId -> activeNode(paths, nodeId))
                    .toList();
            response.put("workflow", Map.of(
                    "workflowId", current.workflowId(),
                    "definitionVersion", current.definitionVersion(),
                    "status", current.status().name(),
                    "activeNodeInstanceIds", current.activeNodeInstanceIds(),
                    "openGateIds", current.openGateIds(),
                    "activeNodes", activeNodes,
                    "updatedAt", current.updatedAt()
            ));
        }
        response.put("artifactContracts", artifactContracts(root, paths));

        var deliveryJson = paths.workstreamDir().resolve("delivery").resolve("delivery.json");
        if (Files.exists(deliveryJson)) {
            response.put("delivery", objectMapper.readValue(deliveryJson.toFile(), MAP_TYPE));
        }
        return response;
    }

    public Map<String, Object> list(Path workspaceRoot) throws IOException {
        var root = workspaceRoot.toAbsolutePath().normalize();
        var workstreamsDir = root.resolve(".agent").resolve("workstreams");
        var items = new ArrayList<Map<String, Object>>();
        if (Files.exists(workstreamsDir)) {
            try (var stream = Files.list(workstreamsDir)) {
                for (var dir : stream.filter(Files::isDirectory).sorted(Comparator.comparing(Path::toString)).toList()) {
                    var workstreamJson = dir.resolve("workstream.json");
                    if (Files.exists(workstreamJson)) {
                        items.add(workstreamSummary(root, dir, workstreamJson));
                    }
                }
            }
        }
        var response = new LinkedHashMap<String, Object>();
        response.put("workdir", root.toString());
        response.put("workstreams", items);
        return response;
    }

    public Map<String, Object> timeline(Path workspaceRoot, String workstreamId) throws IOException {
        var root = workspaceRoot.toAbsolutePath().normalize();
        var paths = new WorkstreamPaths(root, new DualId(workstreamId));
        if (!Files.exists(paths.timelineJson())) {
            throw new IOException("timeline projection not found: " + paths.timelineJson());
        }
        return objectMapper.readValue(paths.timelineJson().toFile(), MAP_TYPE);
    }

    public Map<String, Object> currentReview(Path workspaceRoot, String workstreamId) throws IOException {
        var root = workspaceRoot.toAbsolutePath().normalize();
        var paths = new WorkstreamPaths(root, new DualId(workstreamId));
        var current = workflowLedger.loadCurrent(paths);
        if (current.activeNodeInstanceIds().isEmpty()) {
            throw new IOException("no active node for workstream: " + workstreamId);
        }
        var node = workflowLedger.loadNode(paths, current.activeNodeInstanceIds().get(0));
        if (node.kind() != NodeKind.GATE) {
            throw new IOException("active node is not a human gate: " + node.nodeDefinitionId());
        }
        var packetRef = ".agent/workstreams/" + workstreamId + "/reviews/" + node.nodeInstanceId() + ".md";
        var packetJsonRef = ".agent/workstreams/" + workstreamId + "/reviews/" + node.nodeInstanceId() + ".json";
        var packetPath = root.resolve(packetRef).normalize();
        var packetJsonPath = root.resolve(packetJsonRef).normalize();
        if (!packetPath.startsWith(root) || !Files.exists(packetPath)) {
            throw new IOException("review packet not found: " + packetRef);
        }

        var response = new LinkedHashMap<String, Object>();
        response.put("workstreamId", workstreamId);
        response.put("gateNodeInstanceId", node.nodeInstanceId());
        response.put("gateNodeDefinitionId", node.nodeDefinitionId());
        response.put("packetPath", packetRef);
        response.put("packetMarkdown", Files.readString(packetPath, StandardCharsets.UTF_8));
        if (packetJsonPath.startsWith(root) && Files.exists(packetJsonPath)) {
            response.put("packet", objectMapper.readValue(packetJsonPath.toFile(), MAP_TYPE));
        }
        return response;
    }

    public Map<String, Object> currentRecovery(Path workspaceRoot, String workstreamId) throws IOException {
        var root = workspaceRoot.toAbsolutePath().normalize();
        var paths = new WorkstreamPaths(root, new DualId(workstreamId));
        var failurePath = paths.currentFailureMarkdown();
        var failureRef = root.relativize(failurePath.toAbsolutePath().normalize()).toString();

        var response = new LinkedHashMap<String, Object>();
        response.put("workstreamId", workstreamId);
        response.put("recoveryPath", failureRef);
        if (!Files.isRegularFile(failurePath)) {
            response.put("available", false);
            response.put("message", "No current failure summary found.");
            response.put("markdown", "");
            return response;
        }
        response.put("available", true);
        response.put("message", "Current failure summary is available.");
        response.put("markdown", Files.readString(failurePath, StandardCharsets.UTF_8));
        return response;
    }

    private Map<String, Object> workstreamSummary(Path root, Path dir, Path workstreamJson) throws IOException {
        var workstream = objectMapper.readValue(workstreamJson.toFile(), MAP_TYPE);
        var item = new LinkedHashMap<String, Object>();
        item.put("workstreamId", text(workstream.get("workstreamId")));
        item.put("title", text(workstream.get("title")));
        item.put("status", text(workstream.get("status")));
        item.put("updatedAt", text(workstream.get("updatedAt")));
        item.put("ledger", root.relativize(dir).toString());

        var currentJson = dir.resolve("workflow").resolve("current.json");
        if (Files.exists(currentJson)) {
            var current = objectMapper.readValue(currentJson.toFile(), MAP_TYPE);
            item.put("workflowId", text(current.get("workflowId")));
            item.put("workflowStatus", text(current.get("status")));
            item.put("activeNodeCount", sizeOf(current.get("activeNodeInstanceIds")));
            item.put("openGateCount", sizeOf(current.get("openGateIds")));
        }
        return item;
    }

    private Map<String, Object> activeNode(WorkstreamPaths paths, String nodeId) {
        try {
            var node = workflowLedger.loadNode(paths, nodeId);
            return Map.of(
                    "nodeInstanceId", node.nodeInstanceId(),
                    "nodeDefinitionId", node.nodeDefinitionId(),
                    "kind", node.kind().name(),
                    "handlerType", node.handlerType(),
                    "status", node.status().name(),
                    "iterationId", node.iterationId(),
                    "outputRefs", node.outputRefs() == null ? List.of() : node.outputRefs()
            );
        } catch (IOException e) {
            throw new WorkstreamQueryException(e);
        }
    }

    private List<Map<String, Object>> artifactContracts(Path root, WorkstreamPaths paths) throws IOException {
        if (!Files.exists(paths.runsDir())) {
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(paths.runsDir())) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".contract.json"))
                    .sorted(Comparator.comparing(WorkstreamQueryService::lastModified).reversed())
                    .limit(12)
                    .map(path -> artifactContract(root, path))
                    .toList();
        }
    }

    private Map<String, Object> artifactContract(Path root, Path path) {
        var source = readJson(path);
        var item = new LinkedHashMap<String, Object>();
        for (var key : List.of(
                "artifact_type",
                "node_id",
                "handler_type",
                "decision",
                "summary",
                "must_fix",
                "open_risks",
                "changed_files",
                "verification_summary",
                "source_output_ref",
                "generated_at"
        )) {
            item.put(key, source.getOrDefault(key, defaultValueFor(key)));
        }
        item.put("sidecar_ref", root.relativize(path.toAbsolutePath().normalize()).toString());
        return item;
    }

    private Map<String, Object> readJson(Path path) {
        try {
            return objectMapper.readValue(path.toFile(), MAP_TYPE);
        } catch (IOException e) {
            return Map.of();
        }
    }

    private static Object defaultValueFor(String key) {
        return switch (key) {
            case "must_fix", "open_risks", "changed_files" -> List.of();
            default -> "";
        };
    }

    private static Instant lastModified(Path path) {
        try {
            return Files.readAttributes(path, BasicFileAttributes.class).lastModifiedTime().toInstant();
        } catch (IOException ignored) {
            return Instant.EPOCH;
        }
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static int sizeOf(Object value) {
        return value instanceof List<?> list ? list.size() : 0;
    }

    static final class WorkstreamQueryException extends RuntimeException {
        WorkstreamQueryException(IOException cause) {
            super(cause);
        }
    }
}
