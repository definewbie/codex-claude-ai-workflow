package dev.dual.adapter.acpx;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import dev.dual.core.id.DualId;
import dev.dual.ledger.file.ExecutionNetworkGrantStore;
import dev.dual.ledger.file.WorkstreamPaths;

final class ClaudeExecutionPolicy {
    static final String SCHEMA = "claude_execution_policy.v2";
    static final String NETWORK_TRANSPORT_OWNER = "claude_sandbox";
    private static final List<String> PROVIDER_DENIED_ENV_NAMES = List.of(
            "ANTHROPIC_API_KEY",
            "ANTHROPIC_AUTH_TOKEN",
            "ANTHROPIC_BASE_URL"
    );
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    private ClaudeExecutionPolicy() {
    }

    static Binding write(
            Path workspaceRoot,
            String workstreamId,
            String nodeInstanceId,
            String runId,
            Path runDir
    ) throws IOException {
        return write(workspaceRoot, workstreamId, nodeInstanceId, runId, runDir, false, false, null);
    }

    static Binding write(
            Path workspaceRoot,
            String workstreamId,
            String nodeInstanceId,
            String runId,
            Path runDir,
            AcpxPermissionProfile permissionProfile
    ) throws IOException {
        if (permissionProfile == null) {
            throw new IllegalArgumentException("permissionProfile is required");
        }
        return write(
                workspaceRoot,
                workstreamId,
                nodeInstanceId,
                runId,
                runDir,
                permissionProfile.allowsLocalBinding(), permissionProfile.autoAllowsSandboxedBash(), null);
    }

    static Binding write(Path workspaceRoot, String workstreamId, String nodeInstanceId, String runId,
                         Path runDir, AcpxPermissionProfile profile, ExecutionNetworkGrantStore.Claim claim) throws IOException {
        if (profile == null || claim != null && !profile.allowsLocalBinding()) {
            throw new IOException("network grant requires implement profile");
        }
        if (claim != null) ExecutionNetworkGrantStore.verifyClaim(
                new WorkstreamPaths(workspaceRoot.toRealPath(), new DualId(workstreamId)), claim, nodeInstanceId, runId);
        return write(workspaceRoot, workstreamId, nodeInstanceId, runId, runDir,
                profile.allowsLocalBinding(), profile.autoAllowsSandboxedBash(), claim);
    }

    private static Binding write(
            Path workspaceRoot,
            String workstreamId,
            String nodeInstanceId,
            String runId,
            Path runDir,
            boolean allowLocalBinding,
            boolean autoAllowSandboxedBash,
            ExecutionNetworkGrantStore.Claim claim
    ) throws IOException {
        var root = workspaceRoot.toRealPath();
        var realRunDir = runDir.toRealPath();
        if (!realRunDir.startsWith(root)) {
            throw new IOException("Claude execution policy run directory is outside workspace");
        }
        var scratch = Path.of(System.getProperty("java.io.tmpdir")).toRealPath();
        var policyPath = runDir.resolve("execution-authority").resolve("claude-execution-policy.json");
        Files.createDirectories(policyPath.getParent());

        var network = new LinkedHashMap<String, Object>();
        network.put("strict_allowlist", true);
        network.put("allowed_domains", claim == null ? List.of() : claim.domains());
        network.put("allow_local_binding", allowLocalBinding);
        network.put("allow_unix_sockets", List.of());

        var filesystem = new LinkedHashMap<String, Object>();
        filesystem.put("allow_read", List.of(root.toString(), scratch.toString()));
        filesystem.put("allow_write", List.of(root.toString(), scratch.toString()));
        filesystem.put("deny_read", List.of());
        var controlRoot = root.resolve(".agent/workstreams").resolve(requireIdentifier(workstreamId, "workstreamId"));
        filesystem.put("deny_write", List.of(controlRoot.resolve("nodes").toString(),
                controlRoot.resolve("execution-authority").toString()));

        var sandbox = new LinkedHashMap<String, Object>();
        sandbox.put("enabled", true);
        sandbox.put("fail_if_unavailable", true);
        sandbox.put("allow_unsandboxed_commands", false);
        sandbox.put("auto_allow_sandboxed_bash", autoAllowSandboxedBash);
        sandbox.put("network", network);
        sandbox.put("filesystem", filesystem);

        var document = new LinkedHashMap<String, Object>();
        document.put("schema_version", SCHEMA);
        document.put("workstream_id", requireIdentifier(workstreamId, "workstreamId"));
        document.put("node_instance_id", requireIdentifier(nodeInstanceId, "nodeInstanceId"));
        document.put("run_id", requireIdentifier(runId, "runId"));
        document.put("network_transport_owner", NETWORK_TRANSPORT_OWNER);
        document.put("setting_sources", List.of());
        document.put("sandbox", sandbox);
        document.put("provider_subprocess_denied_env_names", PROVIDER_DENIED_ENV_NAMES);
        document.put("generated_at", Instant.now().toString());

        var bytes = MAPPER.writeValueAsBytes(document);
        writeAtomically(policyPath, bytes);
        var ref = root.relativize(policyPath.toRealPath()).toString();
        var binding = new Binding(ref, sha256(bytes), workstreamId, nodeInstanceId, runId, claim);
        binding.verify(root);
        return binding;
    }

    record Binding(
            String ref,
            String sha256,
            String workstreamId,
            String nodeInstanceId,
            String runId,
            ExecutionNetworkGrantStore.Claim networkGrant
    ) {
        Binding(String ref, String sha256, String workstreamId, String nodeInstanceId, String runId) {
            this(ref, sha256, workstreamId, nodeInstanceId, runId, null);
        }
        Binding {
            requireText(ref, "policy ref");
            if (!sha256.matches("[a-f0-9]{64}")) {
                throw new IllegalArgumentException("policy SHA-256 is invalid");
            }
            requireIdentifier(workstreamId, "workstreamId");
            requireIdentifier(nodeInstanceId, "nodeInstanceId");
            requireIdentifier(runId, "runId");
        }

        void verify(Path workspaceRoot) throws IOException {
            var root = workspaceRoot.toRealPath();
            var resolved = root.resolve(ref).normalize();
            if (!resolved.startsWith(root) || !Files.isRegularFile(resolved)) {
                throw new IOException("Claude execution policy is missing or outside workspace");
            }
            var realPolicy = resolved.toRealPath();
            if (!realPolicy.startsWith(root)) {
                throw new IOException("Claude execution policy resolves outside workspace");
            }
            var bytes = Files.readAllBytes(realPolicy);
            if (!sha256.equals(ClaudeExecutionPolicy.sha256(bytes))) {
                throw new IOException("Claude execution policy SHA-256 mismatch");
            }
            Map<String, Object> document = MAPPER.readValue(bytes, new TypeReference<>() {
            });
            requireEquals(document, "schema_version", SCHEMA);
            requireEquals(document, "workstream_id", workstreamId);
            requireEquals(document, "node_instance_id", nodeInstanceId);
            requireEquals(document, "run_id", runId);
            requireEquals(document, "network_transport_owner", NETWORK_TRANSPORT_OWNER);
            if (networkGrant != null) ExecutionNetworkGrantStore.verifyClaim(
                    new WorkstreamPaths(root, new DualId(workstreamId)), networkGrant, nodeInstanceId, runId);
        }

        Map<String, Object> metadata(String disposition) {
            var metadata = new LinkedHashMap<String, Object>(Map.of(
                    "schema", SCHEMA,
                    "policyRef", ref,
                    "policySha256", sha256,
                    "transportDisposition", disposition
            ));
            if (networkGrant != null) metadata.put("networkGrant", networkGrant.metadata());
            return Map.copyOf(metadata);
        }
    }

    private static void requireEquals(Map<String, Object> document, String key, String expected) throws IOException {
        if (!expected.equals(document.get(key))) {
            throw new IOException("Claude execution policy lineage mismatch: " + key);
        }
    }

    private static String requireIdentifier(String value, String label) {
        requireText(value, label);
        if (!value.matches("[A-Za-z0-9._-]+")) {
            throw new IllegalArgumentException(label + " must be a canonical identifier");
        }
        return value;
    }

    private static void requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static void writeAtomically(Path path, byte[] bytes) throws IOException {
        var temp = path.resolveSibling(path.getFileName() + ".tmp");
        Files.write(temp, bytes);
        try {
            Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
