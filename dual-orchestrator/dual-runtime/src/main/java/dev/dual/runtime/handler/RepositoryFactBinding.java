package dev.dual.runtime.handler;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable identity binding for one repository-governance occurrence. */
public final class RepositoryFactBinding {
    public static final int SCHEMA_VERSION = 1;
    private static final String BINDING_ROOT = "artifacts/context-governance/occurrences/";

    private RepositoryFactBinding() {
    }

    static Verified write(
            Path workspaceRoot,
            Path bindingPath,
            String workstreamId,
            String nodeInstanceId,
            String runId,
            String inventoryHead,
            String inventorySha256,
            int inventoryPathCount,
            Map<String, Path> artifacts,
            ObjectMapper objectMapper
    ) throws IOException {
        var artifactRefs = new LinkedHashMap<String, ArtifactRef>();
        for (var entry : artifacts.entrySet()) {
            artifactRefs.put(entry.getKey(), artifactRef(workspaceRoot, entry.getValue()));
        }
        var document = new Document(
                SCHEMA_VERSION,
                workstreamId,
                nodeInstanceId,
                blankToEmpty(runId),
                inventoryHead,
                inventorySha256,
                inventoryPathCount,
                artifactRefs
        );
        Files.createDirectories(bindingPath.getParent());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(bindingPath.toFile(), document);
        var ref = workspaceRef(workspaceRoot, bindingPath);
        return new Verified(ref, sha256(bindingPath), document, bindingPath.toAbsolutePath().normalize());
    }

    public static Verification verify(
            Path workspaceRoot,
            String workstreamId,
            String bindingRef,
            String expectedBindingSha256,
            String expectedNodeInstanceId,
            String expectedRunId,
            ObjectMapper objectMapper
    ) {
        var errors = new ArrayList<String>();
        if (!canonicalBindingRef(workstreamId, bindingRef)) {
            return Verification.failure("repository fact binding ref is not canonical for the workstream");
        }
        var root = workspaceRoot.toAbsolutePath().normalize();
        var bindingPath = root.resolve(bindingRef).normalize();
        if (!bindingPath.startsWith(root) || !Files.isRegularFile(bindingPath)) {
            return Verification.failure("repository fact binding is missing");
        }
        var bindingSha = safeSha256(bindingPath, errors, "repository fact binding");
        if (!expectedBindingSha256.isBlank() && !expectedBindingSha256.equals(bindingSha)) {
            errors.add("repository fact binding sha256 does not match the lineage anchor");
        }
        final Document document;
        try {
            document = objectMapper.readValue(bindingPath.toFile(), Document.class);
        } catch (IOException | RuntimeException failure) {
            errors.add("repository fact binding cannot be parsed: " + failure.getMessage());
            return new Verification(null, List.copyOf(errors));
        }
        if (document.schemaVersion() != SCHEMA_VERSION) {
            errors.add("unsupported repository fact binding schemaVersion");
        }
        if (!workstreamId.equals(document.workstreamId())) {
            errors.add("repository fact binding workstreamId does not match");
        }
        if (!expectedNodeInstanceId.isBlank() && !expectedNodeInstanceId.equals(document.nodeInstanceId())) {
            errors.add("repository fact binding nodeInstanceId does not match");
        }
        if (!expectedRunId.isBlank() && !expectedRunId.equals(document.runId())) {
            errors.add("repository fact binding runId does not match");
        }
        if (!document.inventorySha256().isBlank()
                && !document.inventorySha256().matches("[0-9a-f]{64}")) {
            errors.add("repository fact binding inventorySha256 is invalid");
        }
        if (document.inventoryPathCount() < 0) {
            errors.add("repository fact binding inventoryPathCount is invalid");
        }
        for (var entry : document.artifacts().entrySet()) {
            verifyArtifact(root, workstreamId, entry.getKey(), entry.getValue(), errors);
        }
        verifyRepoMapIdentity(root, document, objectMapper, errors);
        if (!errors.isEmpty()) {
            return new Verification(null, List.copyOf(errors));
        }
        return new Verification(
                new Verified(bindingRef, bindingSha, document, bindingPath),
                List.of());
    }

    public static Verification verifyReference(
            Path workspaceRoot,
            String workstreamId,
            Reference reference,
            String expectedNodeInstanceId,
            String expectedRunId,
            ObjectMapper objectMapper
    ) {
        if (reference == null) {
            return Verification.failure("repository fact binding reference is missing");
        }
        return verify(workspaceRoot, workstreamId, reference.artifactRef(), reference.sha256(),
                expectedNodeInstanceId, expectedRunId, objectMapper);
    }

    public static Reference reference(Verified verified) {
        return new Reference(verified.ref(), verified.sha256());
    }

    private static ArtifactRef artifactRef(Path workspaceRoot, Path path) throws IOException {
        var root = workspaceRoot.toAbsolutePath().normalize();
        var normalized = path.toAbsolutePath().normalize();
        if (!normalized.startsWith(root) || !Files.isRegularFile(normalized)) {
            throw new IOException("repository fact artifact is outside the workspace or missing: " + path);
        }
        return new ArtifactRef(root.relativize(normalized).toString(), sha256(normalized));
    }

    private static void verifyArtifact(
            Path root,
            String workstreamId,
            String name,
            ArtifactRef artifact,
            List<String> errors
    ) {
        if (artifact == null || artifact.ref() == null || artifact.ref().isBlank()) {
            errors.add("repository fact binding artifact ref is missing: " + name);
            return;
        }
        var normalizedRef = artifact.ref().replace('\\', '/');
        var expectedPrefix = ".agent/workstreams/" + workstreamId + "/artifacts/context-governance/occurrences/";
        if (!normalizedRef.startsWith(expectedPrefix) || normalizedRef.contains("../")) {
            errors.add("repository fact binding artifact ref is not occurrence-scoped: " + name);
            return;
        }
        var path = root.resolve(artifact.ref()).normalize();
        if (!path.startsWith(root) || !Files.isRegularFile(path)) {
            errors.add("repository fact binding artifact is missing: " + name);
            return;
        }
        var actualSha = safeSha256(path, errors, "repository fact artifact " + name);
        if (!artifact.sha256().equals(actualSha)) {
            errors.add("repository fact binding artifact sha256 does not match: " + name);
        }
    }

    @SuppressWarnings("unchecked")
    private static void verifyRepoMapIdentity(
            Path root,
            Document document,
            ObjectMapper objectMapper,
            List<String> errors
    ) {
        var repoMapRef = document.artifacts().get("repoMap");
        if (repoMapRef == null) {
            errors.add("repository fact binding has no repoMap artifact");
            return;
        }
        try {
            var repoMap = objectMapper.readValue(root.resolve(repoMapRef.ref()).toFile(), Map.class);
            if (!document.inventoryHead().equals(text(repoMap.get("inventoryHead")))
                    || !document.inventorySha256().equals(text(repoMap.get("inventorySha256")))
                    || document.inventoryPathCount() != intValue(repoMap.get("inventoryPathCount"))) {
                errors.add("repository fact binding inventory identity does not match repoMap");
            }
        } catch (IOException | RuntimeException failure) {
            errors.add("repository fact repoMap cannot be read: " + failure.getMessage());
        }
    }

    private static String safeSha256(Path path, List<String> errors, String label) {
        try {
            return sha256(path);
        } catch (IOException failure) {
            errors.add(label + " sha256 cannot be computed: " + failure.getMessage());
            return "";
        }
    }

    public static String sha256(Path path) throws IOException {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(path)) {
                var buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static boolean canonicalBindingRef(String workstreamId, String ref) {
        if (ref == null || ref.isBlank()) {
            return false;
        }
        var normalized = ref.replace('\\', '/');
        return normalized.startsWith(".agent/workstreams/" + workstreamId + "/" + BINDING_ROOT)
                && normalized.endsWith("/binding.json")
                && !normalized.contains("../");
    }

    private static String workspaceRef(Path workspaceRoot, Path path) {
        return workspaceRoot.toAbsolutePath().normalize()
                .relativize(path.toAbsolutePath().normalize()).toString();
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value;
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
            return -1;
        }
    }

    public record Document(
            @JsonProperty("schema_version") int schemaVersion,
            @JsonProperty("workstream_id") String workstreamId,
            @JsonProperty("node_instance_id") String nodeInstanceId,
            @JsonProperty("run_id") String runId,
            @JsonProperty("inventory_head") String inventoryHead,
            @JsonProperty("inventory_sha256") String inventorySha256,
            @JsonProperty("inventory_path_count") int inventoryPathCount,
            @JsonProperty("artifacts") Map<String, ArtifactRef> artifacts
    ) {
        public Document {
            artifacts = artifacts == null
                    ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(artifacts));
            workstreamId = blankToEmpty(workstreamId);
            nodeInstanceId = blankToEmpty(nodeInstanceId);
            runId = blankToEmpty(runId);
            inventoryHead = blankToEmpty(inventoryHead);
            inventorySha256 = blankToEmpty(inventorySha256);
        }
    }

    public record ArtifactRef(String ref, String sha256) {
        public ArtifactRef {
            ref = blankToEmpty(ref);
            sha256 = blankToEmpty(sha256);
        }
    }

    public record Reference(String artifactRef, String sha256) {
        public Reference {
            artifactRef = blankToEmpty(artifactRef);
            sha256 = blankToEmpty(sha256);
        }

        public Map<String, Object> toMap() {
            return Map.of("artifact_ref", artifactRef, "sha256", sha256);
        }

        public static Reference fromMap(Map<String, Object> values) {
            if (values == null || values.isEmpty()) {
                return null;
            }
            return new Reference(text(values.get("artifact_ref")), text(values.get("sha256")));
        }
    }

    public record Verified(String ref, String sha256, Document document, Path path) {
    }

    public record Verification(Verified verified, List<String> errors) {
        public Verification {
            errors = errors == null ? List.of() : List.copyOf(errors);
        }

        public boolean valid() {
            return verified != null && errors.isEmpty();
        }

        static Verification failure(String error) {
            return new Verification(null, List.of(error));
        }
    }
}
