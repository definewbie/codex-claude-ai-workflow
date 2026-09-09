package dev.dual.adapter.acpx;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

/** The reviewed acpx fork identity required by E1-bound Claude executions. */
public final class ReviewedAcpxBuildIdentity {
    public static final String VERSION = "0.13.2";
    public static final String UPSTREAM_COMMIT = "fd173f04aa1b56f9e3f5ca5190c034ddcae28792";
    public static final String PATCH_SHA256 = "4f7b7b8e81adf63b7145907cde01ac26f690113862fd9b933226a7eed4d47c8e";
    public static final String POLICY_SCHEMA = "claude_execution_policy.v2";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private ReviewedAcpxBuildIdentity() {
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
