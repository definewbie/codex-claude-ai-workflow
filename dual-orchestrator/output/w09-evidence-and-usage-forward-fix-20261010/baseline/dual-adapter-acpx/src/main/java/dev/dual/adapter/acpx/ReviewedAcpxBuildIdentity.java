package dev.dual.adapter.acpx;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

/** The reviewed acpx fork identity required by E1-bound Claude executions. */
public final class ReviewedAcpxBuildIdentity {
    public static final String VERSION = "0.13.2";
    public static final String UPSTREAM_COMMIT = "fd173f04aa1b56f9e3f5ca5190c034ddcae28792";
    public static final String PATCH_SHA256 = "1517f828d303ea720a7a153e6dc8c33265ba1ea100d2b9260c98327b5604468c";
    public static final String POLICY_SCHEMA = "claude_execution_policy.v2";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private ReviewedAcpxBuildIdentity() {
    }

    /** Non-secret behavior inputs only. These declared versions are not a measurement of installed client bytes. */
    public static Map<String, String> runtimeBehavior() {
        var values = new java.util.TreeMap<String, String>();
        values.put("acpx.upstream", UPSTREAM_COMMIT);
        values.put("acpx.patch", PATCH_SHA256);
        values.put("policy.schema", POLICY_SCHEMA);
        // The executable selector belongs to each R01 invocation, not the frozen Dual binary.
        for (var key : java.util.List.of("DUAL_ACPX_RECOVERABLE_SIGNAL_GRACE_SECONDS",
                "DUAL_ACPX_AGENT_PROGRESS_TIMEOUT_SECONDS", "DUAL_ACPX_QUEUE_TTL_SECONDS",
                "DUAL_ACPX_SESSION_COMMAND_TIMEOUT_SECONDS", "DUAL_ACPX_COMMAND_TIMEOUT_SECONDS", "API_TIMEOUT_MS",
                "DUAL_SESSION_COMPACTION_EXPERIMENT",
                "DUAL_SESSION_COMPACTION_STABILITY_CACHED_READ_TOKENS",
                "DUAL_SESSION_COMPACTION_IMPLEMENT_STABILITY_CACHED_READ_TOKENS",
                "DUAL_CONTEXT_MEMORY_ENABLED", "DUAL_CONTEXT_GOVERNANCE_ENABLED")) {
            values.put("env." + key, System.getenv().getOrDefault(key, ""));
        }
        for (var key : java.util.List.of("dual.context.memory.enabled", "dual.context.governance.enabled",
                "DUAL_SESSION_COMPACTION_EXPERIMENT", "DUAL_SESSION_COMPACTION_STABILITY_CACHED_READ_TOKENS",
                "DUAL_SESSION_COMPACTION_IMPLEMENT_STABILITY_CACHED_READ_TOKENS"))
            values.put("property." + key, System.getProperty(key, ""));
        return Map.copyOf(values);
    }

    public static Verification verify(String buildInfoJson) {
        try {
            var document = MAPPER.readValue(buildInfoJson, MAP_TYPE);
            if (!UPSTREAM_COMMIT.equals(text(document.get("upstream_commit")))
                    || !PATCH_SHA256.equals(text(document.get("patch_sha256")))
                    || !POLICY_SCHEMA.equals(text(document.get("policy_schema")))) {
                return Verification.failed("NETWORK_AUTHORITY_NOT_CONFIGURED: patched acpx build identity mismatch");
            }
            return Verification.valid("upstream=" + UPSTREAM_COMMIT + ", patch=" + PATCH_SHA256);
        } catch (Exception failure) {
            return Verification.failed("NETWORK_AUTHORITY_NOT_CONFIGURED: invalid patched acpx build-info: "
                    + failure.getMessage());
        }
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }

    public record Verification(boolean valid, String message) {
        private static Verification valid(String message) {
            return new Verification(true, message);
        }

        private static Verification failed(String message) {
            return new Verification(false, message);
        }
    }
}
