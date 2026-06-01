package dev.dual.server;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dual.core.id.DualId;
import dev.dual.ledger.file.FileLedgerStore;
import dev.dual.ledger.file.WorkflowFileLedger;
import dev.dual.ledger.file.WorkstreamPaths;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

        var deliveryJson = paths.workstreamDir().resolve("delivery").resolve("delivery.json");
        if (Files.exists(deliveryJson)) {
            response.put("delivery", objectMapper.readValue(deliveryJson.toFile(), MAP_TYPE));
        }
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

    static final class WorkstreamQueryException extends RuntimeException {
        WorkstreamQueryException(IOException cause) {
            super(cause);
        }
    }
}
