package dev.dual.adapter.acpx;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeStatus;
import dev.dual.ledger.file.NodeInstanceDocument;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

final class AcceptanceContractProjectionFixture {
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private AcceptanceContractProjectionFixture() {
    }

    static Fixture create(Path root, String workstreamId, String targetNode) throws Exception {
        var workstreamDir = root.resolve(".agent/workstreams").resolve(workstreamId);
        var artifactsDir = workstreamDir.resolve("artifacts");
        var nodesDir = workstreamDir.resolve("nodes");
        var contextDir = workstreamDir.resolve("context");
        Files.createDirectories(artifactsDir);
        Files.createDirectories(nodesDir);
        Files.createDirectories(contextDir);
        Files.writeString(workstreamDir.resolve("requirement.md"), "Build authentication.");
        var rawContract = """
                {
                  "id":"tc-acceptance","version":1,"created_at":"2026-08-26T00:00:00Z",
                  "objective":"Project the complete authentication contract","status":"READY_FOR_REVIEW",
                  "scope":{"included":[{"path":"src/Auth.java","operation":"CREATE"}],"excluded":[]},
                  "acceptance_criteria":["registration works","login works"],
                  "test_requirements":["Run auth tests"],"constraints":["Keep scope narrow"],
                  "non_goals":[],"risk_areas":[],"evidence_refs":["src/Auth.java"],
                  "ambiguity_notes":[],"human_questions":[],"compilation_pass":1,
                  "source":{"raw_request_ref":"requirement.md","created_by":"contract_compiler"}
                }
                """;
        var contractPath = artifactsDir.resolve("task-contract.json");
        Files.writeString(contractPath, rawContract, StandardCharsets.UTF_8);
        var sha256 = sha256(Files.readAllBytes(contractPath));
        var contractRef = ".agent/workstreams/" + workstreamId + "/artifacts/task-contract.json";
        var compileNodeRef = ".agent/workstreams/" + workstreamId + "/nodes/node-contract-compile.json";
        MAPPER.writeValue(nodesDir.resolve("node-contract-compile.json").toFile(), new NodeInstanceDocument(
                1, "node-contract-compile", "contract.compile", NodeKind.TASK, "contract.compile",
                NodeStatus.COMPLETED, "contract", "iter-001", List.of(), Map.of(), List.of(contractRef),
                Map.of(
                        "contract_id", "tc-acceptance",
                        "contract_status", "READY_FOR_REVIEW",
                        "contract_version", 1,
                        "task_contract_sha256", sha256),
                null, "", Instant.parse("2026-08-26T00:00:00Z"), Instant.parse("2026-08-26T00:00:01Z")));
        var contextPath = contextDir.resolve("context-acceptance.md");
        Files.writeString(contextPath, "# Acceptance Context\n\n## TaskContract\n\n"
                + "<!-- dual:typed-payload:start -->\n```json\n" + rawContract
                + "\n```\n<!-- dual:typed-payload:end -->\n");
        var metadataPath = contextDir.resolve("context-acceptance.json");
        MAPPER.writeValue(metadataPath.toFile(), Map.of(
                "schemaVersion", 1,
                "profile", "acceptance",
                "targetNode", targetNode,
                "taskContractSha256", sha256,
                "taskContractCompileNodeRef", compileNodeRef));
        return new Fixture(
                root.relativize(contextPath).toString(),
                root.relativize(metadataPath).toString(),
                rawContract,
                metadataPath);
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    record Fixture(String contextRef, String metadataRef, String rawContract, Path metadataPath) {
        List<String> inputRefs() {
            return List.of(contextRef, metadataRef);
        }
    }
}
