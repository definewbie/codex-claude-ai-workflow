package dev.dual.adapter.acpx;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeStatus;
import dev.dual.ledger.file.GateDocument;
import dev.dual.ledger.file.NodeInstanceDocument;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

final class DesignContractFixture {
    private static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule());

    static void approve(Path root, String workstream) throws Exception {
        var dir = root.resolve(".agent/workstreams").resolve(workstream);
        for (var child : List.of("artifacts", "nodes", "gates")) {
            Files.createDirectories(dir.resolve(child));
        }
        var contract = dir.resolve("artifacts/task-contract.json");
        if (!Files.exists(contract)) Files.writeString(contract, """
                {"id":"tc-design","version":1,"created_at":"2026-09-05T00:00:00Z",
                 "objective":"Design a file conversion tool","status":"READY_FOR_REVIEW",
                 "scope":{"included":[{"path":"repo/relative/new.ext","operation":"CREATE"}],"excluded":[]},
                 "acceptance_criteria":["conversion works","scope preserved"],"test_requirements":["run tests"],
                 "constraints":["Keep scope narrow"],"non_goals":[],"risk_areas":[],
                 "ambiguity_notes":[],"human_questions":[],"compilation_pass":1,
                 "source":{"raw_request_ref":"requirement.md","created_by":"test"}}
                """);
        var contractJson = JSON.readTree(contract.toFile());
        var resolution = dir.resolve("artifacts/contract-resolution.json");
        Files.writeString(resolution, "{\"decision\":\"approved\"}");
        var refs = List.of(root.relativize(contract).toString(), root.relativize(resolution).toString());
        var now = Instant.parse("2026-09-05T00:00:00Z");
        var source = "node-contract-compile";
        var gateNode = "node-contract-gate";
        var gateId = "gate-contract";
        JSON.writeValue(dir.resolve("nodes/" + source + ".json").toFile(), new NodeInstanceDocument(
                1, source, "contract.compile", NodeKind.TASK, "contract.compile", NodeStatus.COMPLETED,
                "contract", "iter-001", List.of(), Map.of(), refs, Map.of(
                        "contract_id", contractJson.path("id").asText(), "contract_version", contractJson.path("version").asInt(), "contract_status", "READY_FOR_REVIEW",
                        "task_contract_sha256", sha(contract), "contract_resolution_sha256", sha(resolution)),
                null, "", now, now));
        var result = Map.<String, Object>of("source_node_instance_id", source, "human_action", "approved", "note", "reviewed");
        JSON.writeValue(dir.resolve("nodes/" + gateNode + ".json").toFile(), new NodeInstanceDocument(
                1, gateNode, "gate.contract_review", NodeKind.GATE, "human.review", NodeStatus.COMPLETED,
                "contract", "iter-001", refs, Map.of(), refs, result, null, "", now, now));
        JSON.writeValue(dir.resolve("gates/" + gateId + ".json").toFile(), new GateDocument(
                1, gateId, gateNode, "human.review", "resolved", List.of("approve", "clarify", "reject"), now, now,
                Map.of("source_node_instance_id", source, "subject_input_refs", refs, "human_action", "approved", "note", "reviewed")));
        Files.writeString(dir.resolve("events.jsonl"), JSON.writeValueAsString(Map.of(
                "event_id", "evt-000001", "event_type", "node_routed", "payload", Map.of(
                        "from", "contract.compile", "to", "gate.contract_review", "outcome", "completed",
                        "node_instance_id", gateNode, "gate_id", gateId, "source_node_instance_id", source))) + "\n");
    }

    private static String sha(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
}
