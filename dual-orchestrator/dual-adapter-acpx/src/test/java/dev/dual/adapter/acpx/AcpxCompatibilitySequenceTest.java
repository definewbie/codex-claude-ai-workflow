package dev.dual.adapter.acpx;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dual.core.execution.AgentExecutionTimelineEventKind;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Local protocol fixture, no acpx/native/provider executable is launched. */
class AcpxCompatibilitySequenceTest {
    @TempDir Path root;
    private final ObjectMapper mapper = new ObjectMapper();

    @org.junit.jupiter.api.Test
    void configuredEntrypointsBelongToTheNewInvocationRatherThanTheFrozenDualBinary() throws Exception {
        var first = configuredIdentity("/not-launched/first", "/not-launched/codex-first");
        var next = configuredIdentity("/not-launched/next", "/not-launched/codex-next");
        assertEquals(first.path("runtime"), next.path("runtime"));
        assertNotEquals(first.path("invocation"), next.path("invocation"));
        assertEquals("/not-launched/next", next.path("invocation").path("executable").asText());
        assertEquals("/not-launched/codex-next", next.path("invocation").path("launch_environment").path("CODEX_PATH").asText());
        assertFalse(next.path("invocation").path("launch_environment").has("CLAUDE_CODE_EXECUTABLE"));
    }

    private com.fasterxml.jackson.databind.JsonNode configuredIdentity(String executable, String nativePath) throws Exception {
        var command = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin/java").toString(),
                "-cp", System.getProperty("java.class.path"), IdentityMain.class.getName(), root.toString());
        command.environment().put("DUAL_ACPX_EXECUTABLE", executable);
        command.environment().put("CODEX_PATH", nativePath);
        var process = command.start();
        assertTrue(process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS));
        assertEquals(0, process.exitValue(), new String(process.getErrorStream().readAllBytes()));
        return mapper.readTree(process.getInputStream().readAllBytes());
    }

    public static class IdentityMain {
        public static void main(String[] args) throws Exception {
            System.out.print(new ObjectMapper().writeValueAsString(Map.of("runtime", ReviewedAcpxBuildIdentity.runtimeBehavior(),
                    "invocation", new AcpxAgentTransport().invocationInputs("codex", Path.of(args[0]), "same-session"))));
        }
    }

    @ParameterizedTest @ValueSource(strings = {"claude", "codex"})
    void compatibleUpgradeAndUnknownVersionKeepPrimaryAndCorrectionReachable(String agent) throws Exception {
        var events = new ArrayList<AcpxExecutionEvent>();
        var transport = transport();
        for (var version : List.of("old-release", "future-release", "UNKNOWN")) {
            var metadata = metadata();
            if (!version.equals("UNKNOWN")) metadata.put("agentInfo", Map.of("version", version));
            metadata.put("messages", List.of("secret-history"));
            metadata.put("acpx", Map.of("env", Map.of("TOKEN", "do-not-save")));
            write(metadata);
            var request = request(events::add);
            run(transport, agent, request);
            transport.runAgentResultCorrection(agent, request);
        }
        assertEquals(6, Files.readAllLines(root.resolve("prompts")).size());
        var observed = events.stream().filter(e -> e.kind() == AgentExecutionTimelineEventKind.COMMAND_RESULT
                && e.details().get("purpose").equals("session_compatibility")).toList();
        assertEquals(6, observed.size());
        assertFalse(mapper.writeValueAsString(observed.stream().map(AcpxExecutionEvent::details).toList()).contains("secret-history"));
        assertFalse(mapper.writeValueAsString(observed.stream().map(AcpxExecutionEvent::details).toList()).contains("do-not-save"));
    }

    @ParameterizedTest @ValueSource(strings = {"claude", "codex"})
    void optionalEngineIdentityDoesNotBlockPrimaryOrCorrectionOrInventAnIdentity(String agent) throws Exception {
        var events = new ArrayList<AcpxExecutionEvent>();
        var transport = transport();
        for (var shape : List.of("absent", "null", "provided")) {
            // The actual W09 sessions-show shape omits agentSessionId, while both routing IDs exist.
            var metadata = metadata();
            metadata.remove("agentSessionId");
            if (shape.equals("null")) metadata.put("agentSessionId", null);
            if (shape.equals("provided")) metadata.put("agentSessionId", "engine-internal-one");
            write(metadata);
            var assessment = AcpxSessionCompatibility.assess(mapper.writeValueAsString(metadata), root, "exact-session");
            assertTrue(assessment.compatible(), assessment.observation().toString());
            assertEquals(shape.equals("provided") ? "PROVIDED" : "NOT_PROVIDED",
                    assessment.observation().get("agent_session_id_status"));
            assertEquals(shape.equals("provided") ? "engine-internal-one" : null,
                    assessment.observation().get("agentSessionId"));
            run(transport, agent, request(events::add));
            transport.runAgentResultCorrection(agent, request(events::add));
        }
        assertEquals(6, Files.readAllLines(root.resolve("prompts")).size());
        assertEquals(6, events.stream().filter(e -> e.kind() == AgentExecutionTimelineEventKind.COMMAND_RESULT
                && "session_compatibility".equals(e.details().get("purpose"))).count());
    }

    @ParameterizedTest @ValueSource(strings = {"protocol_missing", "protocol_unsupported", "session", "cwd", "closed", "record_id", "acp_id", "schema", "engine_blank", "engine_type"})
    void incompatibleSessionBlocksBothPromptEntrypointsBeforeTheirSideEffects(String fault) throws Exception {
        var metadata = metadata();
        switch (fault) {
            case "protocol_missing" -> metadata.remove("protocolVersion");
            case "protocol_unsupported" -> metadata.put("protocolVersion", 99);
            case "session" -> metadata.put("name", "another-session");
            case "cwd" -> metadata.put("cwd", root.resolve("another-workstream").toString());
            case "closed" -> metadata.put("closed", true);
            case "record_id" -> metadata.remove("acpxRecordId");
            case "acp_id" -> metadata.remove("acpSessionId");
            case "schema" -> metadata.put("schema", "acpx.session.v999");
            case "engine_blank" -> metadata.put("agentSessionId", " ");
            case "engine_type" -> metadata.put("agentSessionId", 7);
        }
        write(metadata);
        var transport = transport();
        var first = run(transport, "codex", request(event -> {}));
        var correction = transport.runAgentResultCorrection("codex", request(event -> {}));
        for (var result : List.of(first, correction))
            assertTrue(result.adapterSignals().stream().anyMatch(s -> s.type().name().equals("ADAPTER_INCOMPATIBLE")));
        assertFalse(Files.exists(root.resolve("prompts")));
    }

    @ParameterizedTest @ValueSource(strings = {"intent", "result"})
    void compatibilityRecordWriteFailureNeverAdvancesToPrompt(String cut) throws Exception {
        write(metadata());
        var transport = transport();
        var request = request(event -> {
            if (event.details().get("purpose").equals("session_compatibility")
                    && event.kind() == (cut.equals("intent") ? AgentExecutionTimelineEventKind.COMMAND_INTENT
                    : AgentExecutionTimelineEventKind.COMMAND_RESULT)) throw new IllegalStateException("persistence cut");
        });
        assertThrows(RuntimeException.class, () -> run(transport, "claude", request));
        assertFalse(Files.exists(root.resolve("prompts")));
        assertEquals(!cut.equals("intent"), Files.exists(root.resolve("reads")));
    }

    private Map<String, Object> metadata() {
        return new LinkedHashMap<>(Map.of("schema", "acpx.session.v1", "acpxRecordId", "record-one",
                "acpSessionId", "native-one", "agentSessionId", "engine-internal-one", "cwd", root.toString(),
                "name", "exact-session", "closed", false, "protocolVersion", 1, "agentCapabilities", Map.of()));
    }
    private void write(Map<String, Object> metadata) throws Exception { mapper.writeValue(root.resolve("session.json").toFile(), metadata); }
    private AcpxRunRequest request(AcpxExecutionEventSink sink) throws Exception {
        var prompt = root.resolve("prompt.md"); Files.writeString(prompt, "fixture prompt");
        return new AcpxRunRequest(root, "exact-session", prompt, root.resolve("stdout"), root.resolve("stderr"), AcpxPermissionProfile.implement(), sink);
    }
    private AcpxRunResult run(AcpxAgentTransport transport, String agent, AcpxRunRequest request) throws Exception {
        return agent.equals("claude") ? transport.runClaudeDesign(request) : transport.runCodexDesignReview(request);
    }
    private AcpxAgentTransport transport() throws Exception {
        var executable = root.resolve("fixture.sh");
        Files.writeString(executable, "#!/bin/sh\ncase \" $* \" in\n*' sessions show '*) printf read >> reads; cat session.json; exit 0;;\n*' -f '*) printf 'prompt\\n' >> prompts;;\nesac\nprintf '[done] end_turn\\n'\n");
        assertTrue(executable.toFile().setExecutable(true));
        return new AcpxAgentTransport(executable.toString());
    }
}
