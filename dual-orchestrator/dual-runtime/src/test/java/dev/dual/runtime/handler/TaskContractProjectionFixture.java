package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.dual.core.contract.TaskContract;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeStatus;
import dev.dual.ledger.file.NodeInstanceDocument;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

final class TaskContractProjectionFixture {
    private static final ObjectMapper LEDGER_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private TaskContractProjectionFixture() {
    }

    static String seedCompileBinding(Path workspaceRoot, String workstreamId) throws Exception {
        var workstreamDir = workspaceRoot.resolve(".agent/workstreams").resolve(workstreamId);
        var contractPath = workstreamDir.resolve("artifacts/task-contract.json");
        var contract = TaskContractArtifactWriter.mapper().readValue(contractPath.toFile(), TaskContract.class);
        var contractRef = ".agent/workstreams/" + workstreamId + "/artifacts/task-contract.json";
        var nodeId = "node-contract-compile";
        var nodesDir = workstreamDir.resolve("nodes");
        Files.createDirectories(nodesDir);
        LEDGER_MAPPER.writeValue(nodesDir.resolve(nodeId + ".json").toFile(), new NodeInstanceDocument(
                1,
                nodeId,
                "contract.compile",
                NodeKind.TASK,
                "contract.compile",
                NodeStatus.COMPLETED,
                "contract",
                "iter-001",
                List.of(),
                Map.of(),
                List.of(contractRef),
                Map.of(
                        "contract_id", contract.id(),
                        "contract_status", contract.status().name(),
                        "contract_version", contract.version(),
                        "task_contract_sha256", TaskContractArtifactWriter.sha256(contractPath)),
                null,
                "",
                Instant.parse("2026-08-26T00:00:00Z"),
                Instant.parse("2026-08-26T00:00:01Z")));
        return ".agent/workstreams/" + workstreamId + "/nodes/" + nodeId + ".json";
    }
}
