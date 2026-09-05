package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.dual.core.contract.TaskContract;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeStatus;
import dev.dual.ledger.file.GateDocument;
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
        var resolutionPath = workstreamDir.resolve("artifacts/contract-resolution.json");
        if (!Files.isRegularFile(resolutionPath)) {
            Files.writeString(resolutionPath, "{\"decision\":\"approved\"}");
        }
        var resolutionRef = ".agent/workstreams/" + workstreamId + "/artifacts/contract-resolution.json";
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
                List.of(contractRef, resolutionRef),
                Map.of(
                        "contract_id", contract.id(),
                        "contract_status", contract.status().name(),
                        "contract_version", contract.version(),
                        "task_contract_sha256", TaskContractArtifactWriter.sha256(contractPath),
                        "contract_resolution_sha256", TaskContractArtifactWriter.sha256(resolutionPath)),
                null,
                "",
                Instant.parse("2026-08-26T00:00:00Z"),
                Instant.parse("2026-08-26T00:00:01Z")));
        var gateNodeId = "node-gate-contract-review";
        var gateId = "gate-" + gateNodeId;
        var refs = List.of(contractRef, resolutionRef);
        LEDGER_MAPPER.writeValue(nodesDir.resolve(gateNodeId + ".json").toFile(), new NodeInstanceDocument(
                1,
                gateNodeId,
                "gate.contract_review",
                NodeKind.GATE,
                "human.review",
                NodeStatus.COMPLETED,
                "gate",
                "iter-001",
                refs,
                Map.of(),
                refs,
                Map.of(
                        "source_node_instance_id", nodeId,
                        "human_action", "approved",
                        "requested_action", "approve",
                        "note", "approved"),
                null,
                "",
                Instant.parse("2026-08-26T00:00:02Z"),
                Instant.parse("2026-08-26T00:00:03Z")));
        var gatesDir = workstreamDir.resolve("gates");
        Files.createDirectories(gatesDir);
        LEDGER_MAPPER.writeValue(gatesDir.resolve(gateId + ".json").toFile(), new GateDocument(
                1,
                gateId,
                gateNodeId,
                "human.review",
                "resolved",
                List.of("approved", "clarify", "reject"),
                Instant.parse("2026-08-26T00:00:02Z"),
                Instant.parse("2026-08-26T00:00:03Z"),
                Map.of(
                        "subject_input_refs", refs,
                        "source_outcome", "completed",
                        "source_node_instance_id", nodeId,
                        "human_action", "approved",
                        "note", "approved")));
        var eventsPath = workstreamDir.resolve("events.jsonl");
        var eventSequence = nextEventSequence(eventsPath);
        Files.writeString(eventsPath, LEDGER_MAPPER.writeValueAsString(Map.of(
                "event_id", "evt-%06d".formatted(eventSequence),
                "ts", "2026-08-26T00:00:02Z",
                "event_type", "node_routed",
                "payload", Map.of(
                        "from", "contract.compile",
                        "outcome", "completed",
                        "to", "gate.contract_review",
                        "node_instance_id", gateNodeId,
                        "gate_id", gateId,
                        "source_node_instance_id", nodeId))) + System.lineSeparator(),
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        return ".agent/workstreams/" + workstreamId + "/nodes/" + nodeId + ".json";
    }

    private static int nextEventSequence(Path eventsPath) throws Exception {
        if (!Files.isRegularFile(eventsPath)) {
            return 1;
        }
        var max = 0;
        for (var line : Files.readAllLines(eventsPath)) {
            if (line.isBlank()) {
                continue;
            }
            var eventId = LEDGER_MAPPER.readTree(line).path("event_id").asText("");
            if (eventId.matches("evt-[0-9]{6,}")) {
                max = Math.max(max, Integer.parseInt(eventId.substring(4)));
            }
        }
        return max + 1;
    }
}
