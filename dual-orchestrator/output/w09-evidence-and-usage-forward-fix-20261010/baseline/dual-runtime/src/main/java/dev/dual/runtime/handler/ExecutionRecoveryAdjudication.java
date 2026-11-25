package dev.dual.runtime.handler;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dual.core.contract.TaskContract;
import dev.dual.ledger.file.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** D07: an operator decision authorizes a new attempt, never rewrites an unknown effect as not executed. */
public final class ExecutionRecoveryAdjudication {
    private static final ObjectMapper JSON = new AtomicJsonWriter().objectMapper();
    private ExecutionRecoveryAdjudication() {}

    public record Request(String schema, @JsonProperty("request_id") String requestId,
            String workstream, String node, String task, String execution, String attempt,
            @JsonProperty("frozen_identity") Map<String, String> frozenIdentity,
            @JsonProperty("input_epoch") long inputEpoch,
            @JsonProperty("last_event") String lastEvent,
            @JsonProperty("node_sha256") String nodeSha256,
            TaskContract.Reference prepared,
            String disposition, @JsonProperty("evidence_refs") List<TaskContract.Reference> evidenceRefs,
            @JsonProperty("accept_duplicate_effect_risk") boolean acceptDuplicateEffectRisk,
            @JsonProperty("operator_note") String operatorNote) {}

    /** Read-only proposal: fields are bound, but neither a disposition nor risk acceptance is preselected. */
    public static Map<String, Object> describe(WorkflowFileLedger ledger, WorkstreamPaths paths) throws IOException {
        return ledger.inspect(paths, (scoped, files) -> {
            var current = scoped.loadCurrent(paths);
            if (current.activeNodeInstanceIds().size() != 1) return Map.of();
            var node = scoped.loadNode(paths, current.activeNodeInstanceIds().getFirst());
            if (!"UNKNOWN".equals(node.result().get("external_effect"))) return Map.of();
            var execution = scoped.latestNodeExecution(paths, node).orElseThrow(() -> new IOException("unknown effect without execution"));
            var prepared = AgentRunCommitProtocol.boundPreparation(paths, execution);
            var binding = JSON.readTree(prepared.path().toFile()).path("binding");
            var contract = originalContract(scoped, paths, node, binding);
            var request = new Request("dual.execution-recovery.v1", "", paths.workstreamId().value(), node.nodeInstanceId(),
                    execution.agentTaskId(), execution.nodeExecutionId(), binding.path("run").path("runId").asText(),
                    ledger.frozenRuntimeIdentity(), contract == null ? 0 : contract.epoch(), current.lastEventId(), hash(files.readBytes(paths.nodeJson(node.nodeInstanceId()))),
                    new TaskContract.Reference(prepared.reference().get("ref"), prepared.reference().get("sha256")), "", List.of(), false, "");
            return Map.of("decision_template", request, "executor_owns_invocation", AgentRunCommitProtocol.executorOwnsInvocation(paths, execution),
                    "external_effect", "UNKNOWN", "risk", "A new attempt can repeat effects; a successful stop does not prove they never happened.");
        });
    }

    public static void apply(WorkflowFileLedger ledger, WorkstreamPaths paths, Request request) throws IOException {
        require(request != null && "dual.execution-recovery.v1".equals(request.schema())
                && request.requestId() != null && request.requestId().matches("[A-Za-z0-9._-]{1,80}")
                && request.execution() != null && request.execution().matches("[A-Za-z0-9_-]{1,100}")
                && Set.of("AUTHORIZE_NEW_ATTEMPT", "ABORT", "ADOPT_VERIFIED_RESULT").contains(request.disposition())
                && request.operatorNote() != null && !request.operatorNote().isBlank(), "invalid recovery decision");
        require(paths.workstreamId().value().equals(request.workstream())
                && ledger.frozenRuntimeIdentity().equals(request.frozenIdentity()), "wrong workstream or frozen identity");
        var requestJson = JSON.readTree(JSON.writeValueAsBytes(request));
        try (var write = ledger.writeSession(paths, "adjudicate unknown execution", request.requestId())) {
            var scoped = write.ledger();
            var execution = write.files().read(paths.nodeExecutionJson(request.execution()), NodeExecutionDocument.class);
            var previous = JSON.valueToTree(execution.result()).path("recovery_decisions").path(request.requestId());
            if (!previous.isMissingNode()) {
                require(previous.path("request").equals(requestJson), "request id was used with different content");
                return;
            }
            var current = scoped.loadCurrent(paths);
            require(current.lastEventId().equals(request.lastEvent())
                    && current.activeNodeInstanceIds().equals(List.of(request.node())), "recovery observation is stale");
            var node = scoped.loadNode(paths, request.node());
            require(execution.nodeInstanceId().equals(node.nodeInstanceId()) && execution.agentTaskId().equals(request.task())
                    && node.latestExecutionId().equals(execution.nodeExecutionId())
                    && hash(write.files().readBytes(paths.nodeJson(node.nodeInstanceId()))).equals(request.nodeSha256()),
                    "node, task or execution identity changed");
            var prepared = AgentRunCommitProtocol.boundPreparation(paths, execution);
            require(request.prepared() != null && prepared.reference().get("ref").equals(request.prepared().ref())
                    && prepared.reference().get("sha256").equals(request.prepared().sha256()), "prepared reference mismatch");
            var input = JSON.readTree(prepared.path().toFile());
            var binding = input.path("binding");
            require(binding.path("run").path("runId").asText().equals(request.attempt()), "attempt mismatch");
            require(!AgentRunCommitProtocol.executorOwnsInvocation(paths, execution), "original executor is still active");
            var contractRef = binding.path("contract_ref").asText();
            var contractSha = binding.path("contract_sha256").asText();
            var contract = originalContract(scoped, paths, node, binding);
            require((contract == null ? 0 : contract.epoch()) == request.inputEpoch(), "input epoch mismatch");
            if (request.disposition().equals("ADOPT_VERIFIED_RESULT")) {
                var result = AgentRunCommitProtocol.readCompletion(paths, execution);
                var completion = new TaskContract.Reference(String.valueOf(result.result().get("completion_ref")),
                        String.valueOf(result.result().get("completion_sha256")));
                require(request.evidenceRefs() != null && request.evidenceRefs().equals(List.of(completion)),
                        "adoption requires the original fixed completion, not a pasted result");
                require(!request.acceptDuplicateEffectRisk(), "adoption is not a new-attempt authorization");
                var decision = JSON.convertValue(request, new TypeReference<LinkedHashMap<String, Object>>() {});
                decision.put("request", requestJson);
                decision.put("executor_stopped", true);
                decision.put("executor_check", "original invocation is quiescent; fixed R03 process/result verified; no new dispatch");
                decision.put("completion_ref", completion.ref()); decision.put("completion_sha256", completion.sha256());
                scoped.adjudicateUnknownExecution(paths, node.nodeInstanceId(), execution.nodeExecutionId(), decision, result);
                dev.dual.runtime.WorkflowRuntime.emitDomainEvents(scoped, paths, node.nodeDefinitionId(), node.handlerType(), node.nodeInstanceId(), result);
                write.commit();
                return;
            }
            require(request.evidenceRefs() != null && !request.evidenceRefs().isEmpty(), "native stop evidence is required");
            long lastProductiveIntent = 0;
            try (var entries = Files.list(prepared.path().getParent())) {
                for (var entry : entries.filter(p -> p.getFileName().toString().startsWith(execution.nodeExecutionId() + "-command-")
                        && p.getFileName().toString().endsWith("-intent.json")).toList()) {
                    var intent = JSON.readTree(entry.toFile());
                    if (!intent.path("details").path("cleanup").asBoolean(false))
                        lastProductiveIntent = Math.max(lastProductiveIntent, intent.path("ordinal").asLong());
                }
            }
            boolean stopped = false;
            for (var ref : request.evidenceRefs()) {
                var bytes = verified(paths.workspaceRoot(), ref);
                var path = paths.workspaceRoot().resolve(ref.ref()).toAbsolutePath().normalize();
                require(path.getParent().equals(prepared.path().getParent())
                        && path.getFileName().toString().startsWith(execution.nodeExecutionId() + "-command-"), "foreign command evidence");
                var evidence = JSON.readTree(bytes);
                var details = evidence.path("details");
                require(evidence.path("schema").asText().equals("dual.run.command.v1")
                        && evidence.path("kind").asText().equals("command_result")
                        && evidence.path("prepared").equals(JSON.valueToTree(prepared.reference()))
                        && details.path("session").asText().equals(binding.path("run").path("session").asText()),
                        "stop evidence identity mismatch");
                var commandId = details.path("command_id").asText();
                require(commandId.matches("[a-f0-9-]{36}") && path.getFileName().toString().equals(
                        execution.nodeExecutionId() + "-command-" + commandId + "-result.json"), "stop receipt path mismatch");
                var intentPath = prepared.path().getParent().resolve(execution.nodeExecutionId() + "-command-" + commandId + "-intent.json");
                var intent = JSON.readTree(intentPath.toFile());
                var withoutObservation = ((com.fasterxml.jackson.databind.node.ObjectNode) details).deepCopy();
                withoutObservation.remove("observation");
                require(intent.path("schema").equals(evidence.path("schema"))
                        && "command_intent".equals(intent.path("kind").asText())
                        && intent.path("prepared").equals(evidence.path("prepared"))
                        && intent.path("ordinal").isIntegralNumber() && intent.path("ordinal").asLong() > 0
                        && intent.path("ordinal").equals(evidence.path("ordinal"))
                        && intent.path("details").equals(withoutObservation), "stop receipt does not match its original command");
                stopped |= evidence.path("ordinal").asLong() > lastProductiveIntent
                        && details.path("cleanup").asBoolean(false)
                        && Set.of("session_cancel", "session_close").contains(details.path("purpose").asText())
                        && details.path("observation").path("success").asBoolean(false)
                        && !details.path("observation").path("timed_out").asBoolean(true);
            }
            require(stopped, "native client has not acknowledged a stop; parent PID absence is insufficient");
            if (request.disposition().equals("AUTHORIZE_NEW_ATTEMPT")) {
                require(request.acceptDuplicateEffectRisk(), "explicit duplicate-effect risk acceptance is required");
                if (contract != null) {
                    var resolution = TaskContractProjectionVerifier.resolve(scoped, paths.workspaceRoot(), request.workstream(), contract.phase());
                    var qualified = resolution.qualified().orElseThrow(() -> new IOException("current execution is not qualified"));
                    require(qualified.contractRef().equals(contractRef) && qualified.sha256().equals(contractSha),
                            "scope or permission contract changed; new approval is required");
                }
            }
            var decision = JSON.convertValue(request, new TypeReference<LinkedHashMap<String, Object>>() {});
            decision.put("request", requestJson);
            decision.put("executor_stopped", true);
            decision.put("executor_check", "invocation released or original PID/start absent; native stop acknowledged");
            decision.put("external_effect", "UNKNOWN");
            scoped.adjudicateUnknownExecution(paths, node.nodeInstanceId(), execution.nodeExecutionId(), decision);
            write.commit();
        }
    }

    private static TaskContract originalContract(WorkflowFileLedger ledger, WorkstreamPaths paths,
            NodeInstanceDocument node, com.fasterxml.jackson.databind.JsonNode binding) throws IOException {
        var ref = binding.path("contract_ref").asText();
        var sha = binding.path("contract_sha256").asText();
        var definition = ledger.loadDefinition(paths).node(node.nodeDefinitionId());
        if (ref.isBlank() && sha.isBlank()) {
            require(!TaskContractProjectionVerifier.requiresContract(definition), "required prepared contract binding is missing");
            return null; // Pre-GOAL exploration has no contract epoch; the exact R01 and claim remain required.
        }
        require(!ref.isBlank() && !sha.isBlank(), "incomplete prepared contract binding");
        var contract = JSON.readValue(verified(paths.workspaceRoot(), new TaskContract.Reference(ref, sha)), TaskContract.class);
        require(contract.phase() == TaskContractProjectionVerifier.requiredPhase(definition), "prepared contract phase mismatch");
        return contract;
    }

    private static byte[] verified(Path root, TaskContract.Reference ref) throws IOException {
        var base = root.toAbsolutePath().normalize();
        var path = base.resolve(ref.ref()).normalize();
        require(!Path.of(ref.ref()).isAbsolute() && path.startsWith(base) && path.toRealPath().startsWith(base.toRealPath()), "evidence escapes workspace");
        var bytes = Files.readAllBytes(path);
        require(hash(bytes).equals(ref.sha256()), "evidence bytes changed");
        return bytes;
    }
    private static String hash(byte[] bytes) { return TaskContractArtifactWriter.sha256(bytes); }
    private static void require(boolean accepted, String message) throws IOException { if (!accepted) throw new IOException(message); }
}
