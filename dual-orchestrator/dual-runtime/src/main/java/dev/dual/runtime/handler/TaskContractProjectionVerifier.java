package dev.dual.runtime.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.dual.core.contract.TaskContract;
import dev.dual.core.contract.TaskContractValidator;
import dev.dual.core.workflow.NodeStatus;
import dev.dual.ledger.file.GateDocument;
import dev.dual.ledger.file.NodeInstanceDocument;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/** Resolves the last human-approved contract.compile occurrence into one immutable snapshot. */
final class TaskContractProjectionVerifier {
    static final String FAILURE_OUTCOME = "TASK_CONTRACT_PROJECTION_QUALIFICATION_FAILED";
    private static final Pattern WORKFLOW_EVENT_ID = Pattern.compile("evt-(\\d{6,})");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private TaskContractProjectionVerifier() {
    }

    static Resolution resolve(Path workspaceRoot, String workstreamId) {
        var workstreamDir = workspaceRoot.resolve(".agent/workstreams").resolve(workstreamId);
        var nodes = readNodes(workstreamDir.resolve("nodes"));
        if (nodes.failure() != null) {
            return Resolution.failed(nodes.failure());
        }
        var occurrenceRead = lastContractGateOccurrence(workstreamDir.resolve("events.jsonl"));
        if (occurrenceRead.failure() != null) {
            return Resolution.failed(occurrenceRead.failure());
        }
        var contractPath = workstreamDir.resolve("artifacts/task-contract.json");
        var resolutionPath = workstreamDir.resolve("artifacts/contract-resolution.json");
        if (occurrenceRead.occurrence() == null) {
            return Files.isRegularFile(contractPath) || Files.isRegularFile(resolutionPath)
                    ? Resolution.failed(Failure.of(
                            "APPROVAL_BINDING_MISSING", "contract artifacts have no routed contract approval gate"))
                    : Resolution.absent();
        }
        var occurrence = occurrenceRead.occurrence();
        var gateNode = node(nodes.nodes(), occurrence.nodeInstanceId());
        if (gateNode == null || !"gate.contract_review".equals(gateNode.nodeDefinitionId())) {
            return Resolution.failed(Failure.of("GATE_NODE_MISSING", "contract approval gate node is missing"));
        }
        if (gateNode.status() != NodeStatus.COMPLETED) {
            return Resolution.failed(Failure.of("GATE_NOT_COMPLETED", "latest contract approval gate is not completed"));
        }
        var gateRead = readGate(workstreamDir.resolve("gates").resolve(occurrence.gateId() + ".json"));
        if (gateRead.failure() != null) {
            return Resolution.failed(gateRead.failure());
        }
        var gate = gateRead.gate();
        if (!gate.nodeInstanceId().equals(gateNode.nodeInstanceId()) || !"resolved".equals(gate.status())) {
            return Resolution.failed(Failure.of("GATE_LINEAGE_INVALID", "latest contract gate lineage or status is invalid"));
        }
        var gateAction = text(gate.result().get("human_action"));
        var nodeAction = text(gateNode.result().get("human_action"));
        if (!"approved".equals(gateAction) || !gateAction.equals(nodeAction)) {
            return Resolution.failed(Failure.of(
                    "CONTRACT_NOT_APPROVED", "latest contract gate action is not approved: " + gateAction));
        }
        if (!text(gate.result().get("note")).equals(text(gateNode.result().get("note")))) {
            return Resolution.failed(Failure.of("GATE_RESULT_MISMATCH", "contract gate note does not match node result"));
        }
        var gateRefs = textList(gate.result().get("subject_input_refs"));
        if (!gateRefs.equals(gateNode.inputRefs())) {
            return Resolution.failed(Failure.of("GATE_SUBJECT_MISMATCH", "contract gate subject refs do not match node input refs"));
        }
        var sourceId = text(gate.result().get("source_node_instance_id"));
        if (sourceId.isBlank()
                || !sourceId.equals(text(gateNode.result().get("source_node_instance_id")))
                || !sourceId.equals(occurrence.sourceNodeInstanceId())) {
            return Resolution.failed(Failure.of("GATE_SOURCE_MISMATCH", "contract gate source occurrence identity is missing or inconsistent"));
        }
        var compileNode = node(nodes.nodes(), sourceId);
        if (compileNode == null
                || !"contract.compile".equals(compileNode.nodeDefinitionId())
                || compileNode.status() != NodeStatus.COMPLETED) {
            return Resolution.failed(Failure.of("COMPILE_BINDING_MISSING", "approved source is not a completed contract.compile node"));
        }
        if (!compileNode.nodeDefinitionId().equals(occurrence.fromNodeDefinitionId())) {
            return Resolution.failed(Failure.of("EVENT_SOURCE_MISMATCH", "routing event source does not match approved compile node"));
        }
        var newerCompile = newerCompletedCompile(nodes.nodes(), compileNode);
        if (newerCompile != null) {
            return Resolution.failed(Failure.of(
                    "UNAPPROVED_RECOMPILE", "completed contract.compile is newer than the approved source: "
                            + newerCompile.nodeInstanceId()));
        }
        if (!Files.isRegularFile(contractPath) || !Files.isRegularFile(resolutionPath)) {
            return Resolution.failed(Failure.of("ARTIFACT_MISSING", "approved contract or requirement resolution artifact is missing"));
        }
        var contractRef = ".agent/workstreams/" + workstreamId + "/artifacts/task-contract.json";
        var resolutionRef = ".agent/workstreams/" + workstreamId + "/artifacts/contract-resolution.json";
        if (!compileNode.outputRefs().contains(contractRef)
                || !compileNode.outputRefs().contains(resolutionRef)
                || !gateRefs.contains(contractRef)
                || !gateRefs.contains(resolutionRef)) {
            return Resolution.failed(Failure.of("OUTPUT_REF_MISMATCH", "approved compile/gate refs do not bind both contract artifacts"));
        }

        final byte[] contractBytes;
        final byte[] resolutionBytes;
        final TaskContract contract;
        final Map<String, Object> document;
        final Map<String, Object> resolutionDocument;
        try {
            contractBytes = Files.readAllBytes(contractPath);
            resolutionBytes = Files.readAllBytes(resolutionPath);
            contract = TaskContractArtifactWriter.mapper().readValue(contractBytes, TaskContract.class);
            document = OBJECT_MAPPER.readValue(contractBytes, MAP_TYPE);
            resolutionDocument = OBJECT_MAPPER.readValue(resolutionBytes, MAP_TYPE);
        } catch (Exception failure) {
            return Resolution.failed(Failure.of("CONTRACT_INVALID", "approved contract artifacts cannot be parsed"));
        }
        var errors = TaskContractValidator.validate(contract);
        if (!errors.isEmpty()) {
            return Resolution.failed(Failure.of(
                    "CONTRACT_INVALID", "task-contract.json is invalid: " + String.join("; ", errors)));
        }

        var sha256 = TaskContractArtifactWriter.sha256(contractBytes);
        var resolutionSha256 = TaskContractArtifactWriter.sha256(resolutionBytes);
        var expectedSha = text(compileNode.result().get("task_contract_sha256"));
        var expectedResolutionSha = text(compileNode.result().get("contract_resolution_sha256"));
        if (expectedSha.isBlank() || expectedResolutionSha.isBlank()) {
            return Resolution.failed(Failure.of(
                    "COMPILE_BINDING_MISSING", "approved contract.compile has incomplete artifact SHA binding"));
        }
        if (!sha256.equals(expectedSha) || !resolutionSha256.equals(expectedResolutionSha)) {
            return Resolution.failed(Failure.of("HASH_MISMATCH", "approved contract artifact SHA-256 does not match compile result"));
        }
        if (!contract.id().equals(text(compileNode.result().get("contract_id")))
                || !contract.status().name().equals(text(compileNode.result().get("contract_status")))
                || contract.version() != intValue(compileNode.result().get("contract_version"))) {
            return Resolution.failed(Failure.of(
                    "IDENTITY_MISMATCH", "task-contract.json identity does not match compile result"));
        }
        var nodeRef = ".agent/workstreams/" + workstreamId + "/nodes/"
                + compileNode.nodeInstanceId() + ".json";
        var gateRef = ".agent/workstreams/" + workstreamId + "/gates/" + occurrence.gateId() + ".json";
        return Resolution.qualified(new QualifiedTaskContract(
                new String(contractBytes, StandardCharsets.UTF_8), contract, Map.copyOf(document), sha256,
                contractRef, new String(resolutionBytes, StandardCharsets.UTF_8), Map.copyOf(resolutionDocument),
                resolutionSha256, resolutionRef, compileNode.nodeInstanceId(), nodeRef, gateRef, occurrence.eventId()));
    }

    static LegacyRead readLegacyProjection(Path workspaceRoot, String workstreamId) {
        var workstreamDir = workspaceRoot.resolve(".agent/workstreams").resolve(workstreamId);
        var contractPath = workstreamDir.resolve("artifacts/task-contract.json");
        var resolutionPath = workstreamDir.resolve("artifacts/contract-resolution.json");
        var hasContract = Files.isRegularFile(contractPath);
        var hasResolution = Files.isRegularFile(resolutionPath);
        if (!hasContract && !hasResolution) {
            return LegacyRead.absent();
        }
        try {
            byte[] contractBytes = hasContract ? Files.readAllBytes(contractPath) : new byte[0];
            var contract = hasContract
                    ? TaskContractArtifactWriter.mapper().readValue(contractBytes, TaskContract.class)
                    : null;
            Map<String, Object> contractDocument = hasContract
                    ? OBJECT_MAPPER.readValue(contractBytes, MAP_TYPE)
                    : Map.of();
            Map<String, Object> resolutionDocument = Map.of();
            String rawResolution = "";
            if (hasResolution) {
                var resolutionBytes = Files.readAllBytes(resolutionPath);
                resolutionDocument = OBJECT_MAPPER.readValue(resolutionBytes, MAP_TYPE);
                rawResolution = new String(resolutionBytes, StandardCharsets.UTF_8);
            }
            return LegacyRead.present(new LegacyProjection(
                    contract,
                    java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(contractDocument)),
                    new String(contractBytes, StandardCharsets.UTF_8),
                    java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(resolutionDocument)),
                    rawResolution,
                    ".agent/workstreams/" + workstreamId + "/artifacts/task-contract.json",
                    ".agent/workstreams/" + workstreamId + "/artifacts/contract-resolution.json"));
        } catch (Exception failure) {
            return LegacyRead.failed(Failure.of(
                    "CONTRACT_INVALID", "legacy task contract projection cannot be read"));
        }
    }

    static Optional<String> trustedWrappedPayload(
            Path workspaceRoot,
            String workstreamId,
            String nodeId,
            List<String> inputRefs
    ) {
        var binding = AcceptanceContextBinding.fromRefs(workspaceRoot, workstreamId, nodeId, inputRefs, OBJECT_MAPPER);
        if (binding.isEmpty()) {
            return Optional.empty();
        }
        var resolution = resolve(workspaceRoot, workstreamId);
        if (resolution.failure() != null || resolution.qualified().isEmpty()) {
            throw new IllegalArgumentException(resolution.failure() == null
                    ? "qualified task contract is required"
                    : resolution.failure().reason() + ": " + resolution.failure().message());
        }
        var qualified = resolution.qualified().orElseThrow();
        var mismatch = binding.get().validate(qualified);
        if (mismatch.isPresent()) {
            throw new IllegalArgumentException(mismatch.get().reason() + ": " + mismatch.get().message());
        }
        return Optional.of(qualified.wrappedPayload());
    }

    static Resolution resolveRoutedAcceptance(
            Path workspaceRoot,
            String workstreamId,
            String nodeId,
            List<String> inputRefs
    ) {
        final Optional<AcceptanceContextBinding> binding;
        try {
            binding = AcceptanceContextBinding.fromRefs(
                    workspaceRoot, workstreamId, nodeId, inputRefs, OBJECT_MAPPER);
        } catch (IllegalArgumentException failure) {
            return Resolution.failed(Failure.of("CONTEXT_BINDING_INVALID", failure.getMessage()));
        }
        if (binding.isEmpty()) {
            return Resolution.absent();
        }
        var resolution = resolve(workspaceRoot, workstreamId);
        if (resolution.failure() != null || resolution.qualified().isEmpty()) {
            return resolution.failure() == null
                    ? Resolution.failed(Failure.of("COMPILE_BINDING_MISSING", "qualified task contract is required"))
                    : resolution;
        }
        var mismatch = binding.get().validate(resolution.qualified().orElseThrow());
        return mismatch.map(Resolution::failed).orElse(resolution);
    }

    private static OccurrenceRead lastContractGateOccurrence(Path eventsPath) {
        if (!Files.isRegularFile(eventsPath)) {
            return new OccurrenceRead(null, null);
        }
        ContractGateOccurrence last = null;
        long previous = -1;
        try {
            for (var line : Files.readAllLines(eventsPath, StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }
                final Map<String, Object> event;
                try {
                    event = OBJECT_MAPPER.readValue(line, MAP_TYPE);
                } catch (Exception failure) {
                    return new OccurrenceRead(null, Failure.of(
                            "EVENT_HISTORY_INVALID", "workflow event history contains invalid JSON"));
                }
                var eventId = text(event.get("event_id"));
                var matcher = WORKFLOW_EVENT_ID.matcher(eventId);
                if (!matcher.matches()) {
                    if ("node_routed".equals(text(event.get("event_type")))) {
                        return new OccurrenceRead(null, Failure.of(
                                "EVENT_HISTORY_INVALID", "routing event does not have a workflow event id"));
                    }
                    continue;
                }
                var sequence = Long.parseLong(matcher.group(1));
                if (sequence <= previous) {
                    return new OccurrenceRead(null, Failure.of(
                            "EVENT_HISTORY_INVALID", "workflow event history is not strictly ordered"));
                }
                previous = sequence;
                if (!"node_routed".equals(text(event.get("event_type")))) {
                    continue;
                }
                var payload = map(event.get("payload"));
                if (!"gate.contract_review".equals(text(payload.get("to")))) {
                    continue;
                }
                var gateId = text(payload.get("gate_id"));
                var nodeInstanceId = text(payload.get("node_instance_id"));
                var sourceNodeInstanceId = text(payload.get("source_node_instance_id"));
                var from = text(payload.get("from"));
                if (gateId.isBlank() || nodeInstanceId.isBlank()
                        || sourceNodeInstanceId.isBlank() || from.isBlank()) {
                    return new OccurrenceRead(null, Failure.of(
                            "GATE_SOURCE_MISSING", "contract gate routing event has incomplete source lineage"));
                }
                last = new ContractGateOccurrence(eventId, gateId, nodeInstanceId, sourceNodeInstanceId, from);
            }
            return new OccurrenceRead(last, null);
        } catch (IOException failure) {
            return new OccurrenceRead(null, Failure.of(
                    "EVENT_HISTORY_UNREADABLE", "cannot read workflow event history"));
        }
    }

    private static GateRead readGate(Path path) {
        if (!Files.isRegularFile(path)) {
            return new GateRead(null, Failure.of("GATE_DOCUMENT_MISSING", "contract approval gate document is missing"));
        }
        try {
            return new GateRead(OBJECT_MAPPER.readValue(path.toFile(), GateDocument.class), null);
        } catch (Exception failure) {
            return new GateRead(null, Failure.of("GATE_DOCUMENT_INVALID", "contract approval gate document cannot be parsed"));
        }
    }

    private static NodeInstanceDocument node(List<NodeInstanceDocument> nodes, String nodeInstanceId) {
        var matches = nodes.stream().filter(node -> node.nodeInstanceId().equals(nodeInstanceId)).toList();
        return matches.size() == 1 ? matches.getFirst() : null;
    }

    private static NodeInstanceDocument newerCompletedCompile(
            List<NodeInstanceDocument> nodes,
            NodeInstanceDocument approved
    ) {
        if (approved.finishedAt() == null) {
            return approved;
        }
        return nodes.stream()
                .filter(node -> !node.nodeInstanceId().equals(approved.nodeInstanceId()))
                .filter(node -> "contract.compile".equals(node.nodeDefinitionId()))
                .filter(node -> node.status() == NodeStatus.COMPLETED)
                .filter(node -> node.finishedAt() == null || !node.finishedAt().isBefore(approved.finishedAt()))
                .findFirst()
                .orElse(null);
    }

    private static Nodes readNodes(Path nodesDir) {
        if (!Files.isDirectory(nodesDir)) {
            return new Nodes(List.of(), null);
        }
        var nodes = new ArrayList<NodeInstanceDocument>();
        try (var paths = Files.list(nodesDir)) {
            for (var path : paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted().toList()) {
                try {
                    nodes.add(OBJECT_MAPPER.readValue(path.toFile(), NodeInstanceDocument.class));
                } catch (Exception failure) {
                    return new Nodes(List.of(), Failure.of(
                            "NODE_LEDGER_UNREADABLE", "cannot read node ledger document " + path.getFileName()));
                }
            }
            return new Nodes(List.copyOf(nodes), null);
        } catch (IOException failure) {
            return new Nodes(List.of(), Failure.of("NODE_LEDGER_UNREADABLE", "cannot list node ledger"));
        }
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> raw ? (Map<String, Object>) raw : Map.of();
    }

    private static List<String> textList(Object value) {
        return value instanceof List<?> list ? list.stream().map(String::valueOf).toList() : List.of();
    }

    private static int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(text(value));
        } catch (NumberFormatException ignored) {
            return Integer.MIN_VALUE;
        }
    }

    record QualifiedTaskContract(
            String rawJson,
            TaskContract contract,
            Map<String, Object> document,
            String sha256,
            String contractRef,
            String rawResolutionJson,
            Map<String, Object> resolutionDocument,
            String resolutionSha256,
            String resolutionRef,
            String compileNodeInstanceId,
            String compileNodeRef,
            String approvalGateRef,
            String approvalEventId
    ) {
        String wrappedPayload() {
            return TypedContextPayloadBoundary.wrap("```json\n" + rawJson + "\n```");
        }
    }

    record Failure(String reason, String message) {
        static Failure of(String reason, String message) {
            return new Failure(reason, message);
        }
    }

    record LegacyProjection(
            TaskContract contract,
            Map<String, Object> document,
            String rawJson,
            Map<String, Object> resolutionDocument,
            String rawResolutionJson,
            String contractRef,
            String resolutionRef
    ) {
    }

    record LegacyRead(Optional<LegacyProjection> projection, Failure failure) {
        static LegacyRead absent() {
            return new LegacyRead(Optional.empty(), null);
        }

        static LegacyRead present(LegacyProjection projection) {
            return new LegacyRead(Optional.of(projection), null);
        }

        static LegacyRead failed(Failure failure) {
            return new LegacyRead(Optional.empty(), failure);
        }
    }

    record Resolution(Optional<QualifiedTaskContract> qualified, Failure failure) {
        static Resolution absent() {
            return new Resolution(Optional.empty(), null);
        }

        static Resolution qualified(QualifiedTaskContract contract) {
            return new Resolution(Optional.of(contract), null);
        }

        static Resolution failed(Failure failure) {
            return new Resolution(Optional.empty(), failure);
        }
    }

    private record Nodes(List<NodeInstanceDocument> nodes, Failure failure) {
    }

    private record ContractGateOccurrence(
            String eventId,
            String gateId,
            String nodeInstanceId,
            String sourceNodeInstanceId,
            String fromNodeDefinitionId
    ) {
    }

    private record OccurrenceRead(ContractGateOccurrence occurrence, Failure failure) {
    }

    private record GateRead(GateDocument gate, Failure failure) {
    }

    private record AcceptanceContextBinding(String sha256, String compileNodeRef) {
        static Optional<AcceptanceContextBinding> fromRefs(
                Path workspaceRoot,
                String workstreamId,
                String nodeId,
                List<String> inputRefs,
                ObjectMapper objectMapper
        ) {
            var root = workspaceRoot.toAbsolutePath().normalize();
            var contextDir = root.resolve(".agent/workstreams").resolve(workstreamId).resolve("context").normalize();
            var matches = new ArrayList<Map<String, Object>>();
            for (var ref : inputRefs == null ? List.<String>of() : inputRefs) {
                var path = root.resolve(ref).normalize();
                if (!path.startsWith(contextDir)
                        || !contextDir.equals(path.getParent())
                        || !path.getFileName().toString().endsWith(".json")
                        || !Files.isRegularFile(path)
                        || Files.isSymbolicLink(path)) {
                    continue;
                }
                try {
                    var document = objectMapper.readValue(path.toFile(), MAP_TYPE);
                    if ("acceptance".equals(text(document.get("profile")))
                            && nodeId.equals(text(document.get("targetNode")))) {
                        matches.add(document);
                    }
                } catch (IOException failure) {
                    throw new IllegalArgumentException("cannot read routed acceptance context metadata", failure);
                }
            }
            if (matches.isEmpty()) {
                return Optional.empty();
            }
            if (matches.size() != 1) {
                throw new IllegalArgumentException("multiple routed acceptance context metadata documents");
            }
            var document = matches.getFirst();
            var sha256 = text(document.get("taskContractSha256"));
            var compileNodeRef = text(document.get("taskContractCompileNodeRef"));
            if (sha256.isBlank() || compileNodeRef.isBlank()) {
                throw new IllegalArgumentException("routed acceptance context metadata has no task contract binding");
            }
            return Optional.of(new AcceptanceContextBinding(sha256, compileNodeRef));
        }

        Optional<Failure> validate(QualifiedTaskContract qualified) {
            if (!sha256.equals(qualified.sha256())) {
                return Optional.of(Failure.of(
                        "CONTEXT_BINDING_HASH_MISMATCH", "routed context SHA does not match current task contract"));
            }
            if (!compileNodeRef.equals(qualified.compileNodeRef())) {
                return Optional.of(Failure.of(
                        "CONTEXT_BINDING_LINEAGE_MISMATCH", "routed context compile ref does not match current binding"));
            }
            return Optional.empty();
        }
    }
}
