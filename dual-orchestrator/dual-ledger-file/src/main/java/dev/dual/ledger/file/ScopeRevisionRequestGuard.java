package dev.dual.ledger.file;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dual.core.contract.TaskContractValidator;
import dev.dual.core.workflow.NodeStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;

/** Pre-mutation integrity check for a human request, not a scope approval. */
final class ScopeRevisionRequestGuard {
    private ScopeRevisionRequestGuard() {}

    static void verify(WorkstreamPaths paths, NodeInstanceDocument gateNode, GateDocument gate,
                       ObjectMapper mapper) throws IOException {
        var root = paths.workstreamDir().toAbsolutePath().normalize().getParent().getParent().getParent();
        var sourceId = String.valueOf(gate.result().get("source_node_instance_id"));
        require(sourceId.matches("[A-Za-z0-9._-]+"), "scope source identity is missing");
        var source = mapper.readValue(paths.nodeJson(sourceId).toFile(), NodeInstanceDocument.class);
        require(sourceId.equals(source.nodeInstanceId()) && source.status() == NodeStatus.COMPLETED
                && source.nodeDefinitionId().equals("implement.artifact_validation")
                && source.handlerType().equals("artifact.validate")
                && source.iterationId().equals(gateNode.iterationId())
                && "FAIL".equals(source.result().get("change_scope_status"))
                && "CHANGE_SCOPE_VIOLATION".equals(source.result().get("change_scope_disposition")),
                "scope source is not a completed violation");
        var binding = mapper.valueToTree(source.result().get("scope_rework_baseline"));
        require(binding != null && binding.isObject(), "scope rework baseline is missing");
        var ref = binding.path("evaluation_ref").asText();
        var sha = binding.path("evaluation_sha256").asText();
        require(ref.equals(source.result().get("change_scope_evaluation_ref"))
                && sha.equals(source.result().get("change_scope_evaluation_sha256"))
                && gateNode.inputRefs().contains(ref) && source.outputRefs().contains(ref)
                && mapper.valueToTree(gateNode.inputRefs()).equals(mapper.valueToTree(gate.result().get("subject_input_refs"))),
                "scope evaluation is not the gate subject");
        var doc = mapper.readTree(verifiedPath(root, paths, ref, sha).toFile());
        require(doc.path("sourceNodeInstanceId").asText().equals(sourceId)
                && doc.path("schemaVersion").asInt() == 1, "scope evaluation identity is invalid");
        var evaluation = doc.path("evaluation");
        require("FAIL".equals(evaluation.path("status").asText()) && evaluation.path("violations").isArray()
                && !evaluation.path("violations").isEmpty(), "scope evaluation has no violations");
        var seen = new HashSet<String>();
        for (var violation : evaluation.path("violations")) {
            var path = violation.path("path").asText();
            require("UNEXPECTED_ACTUAL_CHANGE".equals(violation.path("code").asText())
                    && "CREATE".equals(violation.path("actualOperation").asText())
                    && violation.path("declaredOperation").isNull() && violation.path("previousPath").isNull()
                    && TaskContractValidator.isCanonicalPath(path) && seen.add(path),
                    "scope revision only supports ordinary new file violations");
        }
        for (var field : new String[]{"proposal_review", "contract_gate", "requirement_gate"}) {
            verifiedPath(root, paths, binding.path(field + "_ref").asText(), binding.path(field + "_sha256").asText());
        }
        var proposal = binding.path("proposal_reference");
        verifiedPath(root, paths, proposal.path("artifact_ref").asText(), proposal.path("sha256").asText());
        var artifactPrefix = root.relativize(paths.workstreamDir().toAbsolutePath().normalize()).toString() + "/artifacts/";
        verifiedPath(root, paths, artifactPrefix + "task-contract.json", binding.path("task_contract_sha256").asText());
        verifiedPath(root, paths, artifactPrefix + "contract-resolution.json", binding.path("contract_resolution_sha256").asText());
        var compileId = binding.path("compile_node_instance_id").asText();
        require(compileId.matches("[A-Za-z0-9._-]+"), "scope compile identity is invalid");
        var compile = mapper.readValue(paths.nodeJson(compileId).toFile(), NodeInstanceDocument.class);
        require(compile.status() == NodeStatus.COMPLETED && compile.nodeDefinitionId().equals("contract.compile")
                && binding.path("task_contract_sha256").asText().equals(compile.result().get("task_contract_sha256"))
                && binding.path("contract_resolution_sha256").asText().equals(compile.result().get("contract_resolution_sha256")),
                "scope compile baseline changed");
        var workspaceId = binding.path("workspace_id").asText();
        require(workspaceId.matches("[A-Za-z0-9._-]+"), "scope workspace is missing");
        var workspace = mapper.readTree(paths.workstreamDir().resolve("workspaces/" + workspaceId + ".json").toFile());
        require(binding.path("base_commit").asText().matches("[0-9a-f]{40}|[0-9a-f]{64}")
                && binding.path("base_commit").equals(workspace.path("baseCommit")), "scope original base changed");
    }

    private static Path verifiedPath(Path root, WorkstreamPaths paths, String ref, String sha) throws IOException {
        var target = root.resolve(ref).normalize();
        require(!ref.isBlank() && !Path.of(ref).isAbsolute() && root.relativize(target).toString().equals(ref)
                && target.startsWith(paths.workstreamDir().toAbsolutePath().normalize())
                && Files.isRegularFile(target) && target.toRealPath().equals(root.toRealPath().resolve(ref))
                && sha.matches("[0-9a-f]{64}"), "scope baseline reference is missing or noncanonical");
        try {
            require(sha.equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(target)))),
                    "scope baseline hash mismatch");
        } catch (NoSuchAlgorithmException impossible) { throw new IOException(impossible); }
        return target;
    }

    private static void require(boolean condition, String reason) throws IOException {
        if (!condition) throw new IOException(reason);
    }
}
