package dev.dual.runtime.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.dual.core.contract.TaskContract;
import dev.dual.core.contract.TaskContractValidator;
import dev.dual.core.workflow.NodeStatus;
import dev.dual.ledger.file.NodeInstanceDocument;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Proves that an acceptance projection is bound to a durable contract.compile occurrence. */
final class TaskContractProjectionVerifier {
    static final String FAILURE_OUTCOME = "TASK_CONTRACT_PROJECTION_QUALIFICATION_FAILED";
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
        var compileNodes = nodes.nodes().stream()
                .filter(node -> "contract.compile".equals(node.nodeDefinitionId()))
                .filter(node -> node.status() == NodeStatus.COMPLETED)
                .toList();
        for (var node : compileNodes) {
            if (node.finishedAt() == null) {
                return Resolution.failed(Failure.of(
                        "COMPILE_NODE_INVALID", "completed contract.compile node has no finishedAt"));
            }
        }

        var contractPath = workstreamDir.resolve("artifacts/task-contract.json");
        if (!Files.isRegularFile(contractPath)) {
            return compileNodes.isEmpty()
                    ? Resolution.absent()
                    : Resolution.failed(Failure.of(
                            "ARTIFACT_MISSING", "completed contract.compile has no task-contract.json"));
        }
        if (compileNodes.isEmpty()) {
            return Resolution.failed(Failure.of(
                    "COMPILE_BINDING_MISSING", "task-contract.json has no completed contract.compile binding"));
        }

        final byte[] bytes;
        final TaskContract contract;
        final Map<String, Object> document;
        try {
            bytes = Files.readAllBytes(contractPath);
            contract = TaskContractArtifactWriter.mapper().readValue(bytes, TaskContract.class);
            document = OBJECT_MAPPER.readValue(bytes, MAP_TYPE);
        } catch (Exception failure) {
            return Resolution.failed(Failure.of("CONTRACT_INVALID", "task-contract.json cannot be parsed"));
        }
        var errors = TaskContractValidator.validate(contract);
        if (!errors.isEmpty()) {
            return Resolution.failed(Failure.of(
                    "CONTRACT_INVALID", "task-contract.json is invalid: " + String.join("; ", errors)));
        }

        var ordered = compileNodes.stream()
                .sorted(Comparator.comparing(NodeInstanceDocument::finishedAt).reversed())
                .toList();
        var latestAt = ordered.getFirst().finishedAt();
        var latest = ordered.stream().filter(node -> latestAt.equals(node.finishedAt())).toList();
        if (latest.size() != 1) {
            return Resolution.failed(Failure.of(
                    "AMBIGUOUS_COMPILE_BINDING", "multiple latest contract.compile nodes share finishedAt " + latestAt));
        }
        var compileNode = latest.getFirst();
        var expectedRef = ".agent/workstreams/" + workstreamId + "/artifacts/task-contract.json";
        if (!compileNode.outputRefs().contains(expectedRef)) {
            return Resolution.failed(Failure.of(
                    "OUTPUT_REF_MISMATCH", "latest contract.compile does not reference task-contract.json"));
        }
        var sha256 = TaskContractArtifactWriter.sha256(bytes);
        var expectedSha = text(compileNode.result().get("task_contract_sha256"));
        if (expectedSha.isBlank()) {
            return Resolution.failed(Failure.of(
                    "COMPILE_BINDING_MISSING", "latest contract.compile has no task_contract_sha256"));
        }
        if (!sha256.equals(expectedSha)) {
            return Resolution.failed(Failure.of("HASH_MISMATCH", "task-contract.json SHA-256 does not match compile result"));
        }
        if (!contract.id().equals(text(compileNode.result().get("contract_id")))
                || !contract.status().name().equals(text(compileNode.result().get("contract_status")))
                || contract.version() != intValue(compileNode.result().get("contract_version"))) {
            return Resolution.failed(Failure.of(
                    "IDENTITY_MISMATCH", "task-contract.json identity does not match compile result"));
        }
        var nodeRef = ".agent/workstreams/" + workstreamId + "/nodes/"
                + compileNode.nodeInstanceId() + ".json";
        return Resolution.qualified(new QualifiedTaskContract(
                new String(bytes, StandardCharsets.UTF_8), contract, Map.copyOf(document), sha256,
                compileNode.nodeInstanceId(), nodeRef));
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
            String compileNodeInstanceId,
            String compileNodeRef
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
