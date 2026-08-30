package dev.dual.adapter.acpx;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaudeExecutionPolicyTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void writesFixedFailClosedPolicyAndVerifiesItsLineage() throws Exception {
        var runDir = Files.createDirectories(tempDir.resolve(".agent/workstreams/ws-1/runs/run-1"));

        var binding = ClaudeExecutionPolicy.write(tempDir, "ws-1", "node-1", "run-1", runDir);
        binding.verify(tempDir);

        Map<String, Object> document = MAPPER.readValue(
                tempDir.resolve(binding.ref()).toFile(), new TypeReference<>() {
                });
        assertEquals(ClaudeExecutionPolicy.SCHEMA, document.get("schema_version"));
        assertEquals(List.of(), document.get("setting_sources"));
        var sandbox = map(document.get("sandbox"));
        assertEquals(true, sandbox.get("enabled"));
        assertEquals(true, sandbox.get("fail_if_unavailable"));
        assertEquals(false, sandbox.get("allow_unsandboxed_commands"));
        assertEquals(true, sandbox.get("auto_allow_sandboxed_bash"));
        var network = map(sandbox.get("network"));
        assertEquals(true, network.get("strict_allowlist"));
        assertEquals(List.of(), network.get("allowed_domains"));
        assertEquals(false, network.get("allow_local_binding"));
        assertEquals(List.of(), network.get("allow_unix_sockets"));
        var serialized = Files.readString(tempDir.resolve(binding.ref()));
        assertFalse(serialized.contains("secret-value"));
        assertTrue(serialized.contains("ANTHROPIC_AUTH_TOKEN"));
    }

    @Test
    void rejectsDeletionTamperingAndLineageDrift() throws Exception {
        var runDir = Files.createDirectories(tempDir.resolve(".agent/workstreams/ws-1/runs/run-1"));
        var binding = ClaudeExecutionPolicy.write(tempDir, "ws-1", "node-1", "run-1", runDir);
        var policy = tempDir.resolve(binding.ref());

        Files.writeString(policy, "{}\n", StandardCharsets.UTF_8);
        assertThrows(IOException.class, () -> binding.verify(tempDir));

        var restored = ClaudeExecutionPolicy.write(tempDir, "ws-1", "node-1", "run-1", runDir);
        Files.delete(tempDir.resolve(restored.ref()));
        assertThrows(IOException.class, () -> restored.verify(tempDir));

        var valid = ClaudeExecutionPolicy.write(tempDir, "ws-1", "node-1", "run-1", runDir);
        var wrongLineage = new ClaudeExecutionPolicy.Binding(
                valid.ref(), valid.sha256(), "ws-1", "node-2", "run-1");
        assertThrows(IOException.class, () -> wrongLineage.verify(tempDir));
    }

    @Test
    void rejectsRunDirectoryOutsideWorkspace() throws Exception {
        var outside = Files.createDirectories(tempDir.getParent().resolve("outside-policy-run"));
        try {
            assertThrows(IOException.class,
                    () -> ClaudeExecutionPolicy.write(tempDir, "ws-1", "node-1", "run-1", outside));
        } finally {
            Files.deleteIfExists(outside);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }
}
