package dev.dual.runtime.handler;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Immutable identity and lineage for one validated task-contract proposal sidecar. */
public record TaskContractProposalReference(
        @JsonProperty("schema_version") int schemaVersion,
        @JsonProperty("proposal_id") String proposalId,
        @JsonProperty("workstream_id") String workstreamId,
        @JsonProperty("producer_node_id") String producerNodeId,
        @JsonProperty("producer_node_instance_id") String producerNodeInstanceId,
        @JsonProperty("artifact_ref") String artifactRef,
        @JsonProperty("sha256") String sha256
) {
    private static final int SCHEMA_VERSION = 1;

    public TaskContractProposalReference {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("unsupported proposal reference schema_version: " + schemaVersion);
        }
        requireText(proposalId, "proposal_id");
        requireText(workstreamId, "workstream_id");
        requireText(producerNodeId, "producer_node_id");
        requireText(producerNodeInstanceId, "producer_node_instance_id");
        requireText(artifactRef, "artifact_ref");
        if (sha256 == null || !sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("sha256 must be 64 lowercase hexadecimal characters");
        }
    }

    static Published publishInitial(
            Path workspaceRoot,
            String workstreamId,
            Path sidecarPath,
            AgentOutputContractDocument document,
            ObjectMapper objectMapper
    ) throws Exception {
        if (!"task_contract_proposal".equals(document.sourceArtifactType())
                || document.proposalDerivation() != null) {
            throw new IllegalArgumentException("direct task-contract proposal lineage is required");
        }
        return publishVerified(workspaceRoot, workstreamId, sidecarPath, document, objectMapper);
    }

    static Published publishRevisionDerived(
            Path workspaceRoot,
            String workstreamId,
            Path sidecarPath,
            AgentOutputContractDocument document,
            ObjectMapper objectMapper
    ) throws Exception {
        if (!"task_contract_revision_patch".equals(document.sourceArtifactType())
                || document.proposalDerivation() == null) {
            throw new IllegalArgumentException("revision-derived task-contract proposal lineage is required");
        }
        return publishVerified(workspaceRoot, workstreamId, sidecarPath, document, objectMapper);
    }

    static Published preparePublication(Path workspaceRoot, String workstreamId, Path sidecarPath,
                                         AgentOutputContractDocument document, byte[] bytes, ObjectMapper mapper) throws Exception {
        var root = workspaceRoot.toAbsolutePath().normalize();
        var sidecar = sidecarPath.toAbsolutePath().normalize();
        if (!sidecar.startsWith(root) || !document.hasValidatedLineage(workstreamId, root, sidecar)
                || !"intake.claude_brainstorm".equals(document.nodeId()) || document.taskContractProposal() == null
                || !validProposalSource(root, workstreamId, document, mapper))
            throw new IllegalArgumentException("validated proposal lineage required before publication");
        var digest = TaskContractArtifactWriter.sha256(bytes);
        var reference = new TaskContractProposalReference(SCHEMA_VERSION, document.nodeInstanceId() + ":" + digest.substring(0, 16),
                workstreamId, document.nodeId(), document.nodeInstanceId(), root.relativize(sidecar).toString(), digest);
        var path = root.resolve(".agent/workstreams").resolve(workstreamId).resolve("artifacts/task-contract-proposals")
                .resolve(safeFileName(document.nodeInstanceId()) + "-" + digest.substring(0, 16) + ".ref.json");
        return new Published(reference, root.relativize(path).toString());
    }

    private static Published publishVerified(
            Path workspaceRoot,
            String workstreamId,
            Path sidecarPath,
            AgentOutputContractDocument document,
            ObjectMapper objectMapper
    ) throws Exception {
        var root = workspaceRoot.toAbsolutePath().normalize();
        var sidecar = sidecarPath.toAbsolutePath().normalize();
        if (!sidecar.startsWith(root)
                || !Files.isRegularFile(sidecar)
                || !document.hasValidatedLineage(workstreamId, root, sidecar)
                || !"intake.claude_brainstorm".equals(document.nodeId())
                || document.taskContractProposal() == null
                || !validProposalSource(root, workstreamId, document, objectMapper)) {
            throw new IllegalArgumentException("validated task-contract proposal lineage is required");
        }
        var digest = sha256(sidecar);
        var proposalId = document.nodeInstanceId() + ":" + digest.substring(0, 16);
        var reference = new TaskContractProposalReference(
                SCHEMA_VERSION,
                proposalId,
                workstreamId,
                document.nodeId(),
                document.nodeInstanceId(),
                root.relativize(sidecar).toString(),
                digest
        );
        var referencePath = root.resolve(".agent/workstreams")
                .resolve(workstreamId)
                .resolve("artifacts/task-contract-proposals")
                .resolve(safeFileName(document.nodeInstanceId()) + "-" + digest.substring(0, 16) + ".ref.json")
                .normalize();
        Files.createDirectories(referencePath.getParent());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(referencePath.toFile(), reference);
        return new Published(reference, root.relativize(referencePath).toString());
    }

    private static boolean validProposalSource(
            Path root,
            String workstreamId,
            AgentOutputContractDocument document,
            ObjectMapper objectMapper
    ) {
        if (document.repositoryFactBinding() != null
                && !RepositoryFactBinding.verifyReference(
                        root,
                        workstreamId,
                        document.repositoryFactBinding(),
                        document.nodeInstanceId(),
                        document.runId(),
                        objectMapper).valid()) {
            return false;
        }
        if ("task_contract_proposal".equals(document.sourceArtifactType())) {
            return document.proposalDerivation() == null;
        }
        if (!"task_contract_revision_patch".equals(document.sourceArtifactType())) {
            return false;
        }
        var derivation = document.proposalDerivation();
        if (derivation == null
                || !derivation.validFor(derivation.baseProposalReference(), document.sourceOutputRef())) {
            return false;
        }
        try {
            return derivation.baseProposalReference()
                    .resolveArtifact(root, workstreamId, objectMapper).isPresent();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    static Optional<TaskContractProposalReference> readVerified(
            Path workspaceRoot,
            String expectedWorkstreamId,
            String referenceRef,
            ObjectMapper objectMapper
    ) {
        try {
            var root = workspaceRoot.toAbsolutePath().normalize();
            var referencePath = root.resolve(referenceRef).normalize();
            if (!referencePath.startsWith(root) || !Files.isRegularFile(referencePath)) {
                return Optional.empty();
            }
            var reference = objectMapper.readValue(referencePath.toFile(), TaskContractProposalReference.class);
            if (!expectedWorkstreamId.equals(reference.workstreamId())) {
                return Optional.empty();
            }
            if (reference.resolveArtifact(root, expectedWorkstreamId, objectMapper).isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(reference);
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    static Optional<Resolved> resolveFromRefs(
            Path workspaceRoot,
            String expectedWorkstreamId,
            Iterable<String> refs,
            ObjectMapper objectMapper
    ) {
        Resolved resolved = null;
        for (var ref : refs) {
            var candidate = readVerified(workspaceRoot, expectedWorkstreamId, ref, objectMapper);
            if (candidate.isEmpty()) {
                continue;
            }
            if (resolved != null && !resolved.reference().proposalId().equals(candidate.get().proposalId())) {
                return Optional.empty();
            }
            resolved = new Resolved(candidate.get(), ref);
        }
        return Optional.ofNullable(resolved);
    }

    public static Optional<ResolvedProposal> resolveProposalFromRefs(
            Path workspaceRoot,
            String expectedWorkstreamId,
            Iterable<String> refs,
            ObjectMapper objectMapper
    ) {
        var resolved = resolveFromRefs(workspaceRoot, expectedWorkstreamId, refs, objectMapper);
        if (resolved.isEmpty()) {
            return Optional.empty();
        }
        var reference = resolved.get().reference();
        var proposalArtifact = reference.resolveProposalArtifact(
                workspaceRoot, expectedWorkstreamId, objectMapper);
        if (proposalArtifact.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ResolvedProposal(
                reference,
                resolved.get().referenceRef(),
                proposalArtifact.get().path(),
                proposalArtifact.get().proposal()));
    }

    /** Resolves the exact proposal carried by this verified immutable reference. */
    Optional<TaskContractProposal> resolveVerifiedProposal(
            Path workspaceRoot,
            String expectedWorkstreamId,
            ObjectMapper objectMapper
    ) {
        return resolveProposalArtifact(workspaceRoot, expectedWorkstreamId, objectMapper)
                .map(ProposalArtifact::proposal);
    }

    private Optional<ProposalArtifact> resolveProposalArtifact(
            Path workspaceRoot,
            String expectedWorkstreamId,
            ObjectMapper objectMapper
    ) {
        var artifactPath = resolveArtifact(workspaceRoot, expectedWorkstreamId, objectMapper);
        if (artifactPath.isEmpty()) {
            return Optional.empty();
        }
        var document = AgentOutputContractDocument.read(artifactPath.get(), objectMapper);
        if (document.isEmpty()
                || document.get().taskContractProposal() == null
                || document.get().taskContractProposal().schemaVersion() != TaskContractProposal.SCHEMA_VERSION) {
            return Optional.empty();
        }
        return Optional.of(new ProposalArtifact(artifactPath.get(), document.get().taskContractProposal()));
    }

    Optional<Path> resolveArtifact(Path workspaceRoot, String expectedWorkstreamId, ObjectMapper objectMapper) {
        try {
            var root = workspaceRoot.toAbsolutePath().normalize();
            if (!expectedWorkstreamId.equals(workstreamId)) {
                return Optional.empty();
            }
            var artifactPath = root.resolve(artifactRef).normalize();
            if (!artifactPath.startsWith(root)
                    || !Files.isRegularFile(artifactPath)
                    || !sha256.equals(sha256(artifactPath))) {
                return Optional.empty();
            }
            var document = AgentOutputContractDocument.read(artifactPath, objectMapper);
            if (document.isEmpty()
                    || !document.get().hasValidatedLineage(expectedWorkstreamId, root, artifactPath)
                    || !producerNodeId.equals(document.get().nodeId())
                    || !producerNodeInstanceId.equals(document.get().nodeInstanceId())
                    || !validProposalSource(root, expectedWorkstreamId, document.get(), objectMapper)) {
                return Optional.empty();
            }
            return Optional.of(artifactPath);
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    Map<String, Object> toMap() {
        var map = new LinkedHashMap<String, Object>();
        map.put("schema_version", schemaVersion);
        map.put("proposal_id", proposalId);
        map.put("workstream_id", workstreamId);
        map.put("producer_node_id", producerNodeId);
        map.put("producer_node_instance_id", producerNodeInstanceId);
        map.put("artifact_ref", artifactRef);
        map.put("sha256", sha256);
        return Map.copyOf(map);
    }

    static Optional<TaskContractProposalReference> fromMap(Map<String, Object> map) {
        try {
            return Optional.of(new TaskContractProposalReference(
                    integer(map.get("schema_version")),
                    text(map.get("proposal_id")),
                    text(map.get("workstream_id")),
                    text(map.get("producer_node_id")),
                    text(map.get("producer_node_instance_id")),
                    text(map.get("artifact_ref")),
                    text(map.get("sha256"))
            ));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private static String sha256(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }

    private static String safeFileName(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static int integer(Object value) {
        return value instanceof Number number ? number.intValue() : Integer.parseInt(text(value));
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }

    record Published(TaskContractProposalReference reference, String referenceRef) {}

    record Resolved(TaskContractProposalReference reference, String referenceRef) {}

    public record ResolvedProposal(
            TaskContractProposalReference reference,
            String referenceRef,
            Path artifactPath,
            TaskContractProposal proposal
    ) {}

    private record ProposalArtifact(Path path, TaskContractProposal proposal) {}
}
