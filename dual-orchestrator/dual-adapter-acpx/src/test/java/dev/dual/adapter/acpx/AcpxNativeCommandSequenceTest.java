package dev.dual.adapter.acpx;

import dev.dual.core.execution.AgentExecutionTimelineEventKind;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Offline adapter command boundary: the executable is a local shell fixture, never a provider. */
class AcpxNativeCommandSequenceTest {
    @TempDir Path root;

    @ParameterizedTest @ValueSource(strings = {"claude", "codex"})
    void abnormalPromptAndExplicitRecoveryRecordBeforeEachCommandAndPairOneResult(String agent) throws Exception {
        var events = new ArrayList<AcpxExecutionEvent>();
        var script = executable();
        var transport = new AcpxAgentTransport(script.toString());
        var request = request(event -> {
            events.add(event);
            if (event.kind() == AgentExecutionTimelineEventKind.COMMAND_INTENT) {
                try { Files.writeString(root.resolve("intent"), String.valueOf(event.details().get("command_id"))); }
                catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
            }
        });
        if (agent.equals("claude")) transport.runClaudeDesign(request); else transport.runCodexDesignReview(request);
        assertTrue(transport.stopRecordedSession(agent, request));
        var intents = events.stream().filter(e -> e.kind() == AgentExecutionTimelineEventKind.COMMAND_INTENT).toList();
        var results = events.stream().filter(e -> e.kind() == AgentExecutionTimelineEventKind.COMMAND_RESULT).toList();
        assertEquals(List.of("session_ensure", "session_compatibility", "prompt", "session_cancel", "session_close", "session_cancel", "session_close"),
                intents.stream().map(e -> e.details().get("purpose")).toList());
        assertEquals(intents.size(), results.size());
        assertEquals(intents.stream().map(e -> e.details().get("command_id")).toList(), Files.readAllLines(root.resolve("executed")));
        for (var intent : intents) {
            var matching = results.stream().filter(r -> r.details().get("command_id").equals(intent.details().get("command_id"))).toList();
            assertEquals(1, matching.size(), "pair by identity, including stop commands nested inside prompt completion");
            var result = matching.getFirst();
            var pair = new LinkedHashMap<>(result.details());
            assertNotNull(pair.remove("observation"));
            assertEquals(intent.details(), pair);
            assertTrue(events.indexOf(intent) < events.indexOf(result));
        }
    }

    @ParameterizedTest @ValueSource(strings = {"intent", "result"})
    void failedPersistenceCannotAdvanceToTheNextExternalPhase(String cut) throws Exception {
        var transport = new AcpxAgentTransport(executable().toString());
        var request = request(event -> {
            if (event.kind() == AgentExecutionTimelineEventKind.COMMAND_INTENT) {
                if (cut.equals("intent")) throw new IllegalStateException("intent persistence failed");
                try { Files.writeString(root.resolve("intent"), String.valueOf(event.details().get("command_id"))); }
                catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
            }
            if (event.kind() == AgentExecutionTimelineEventKind.COMMAND_RESULT) throw new IllegalStateException("result persistence failed");
        });
        assertThrows(RuntimeException.class, () -> transport.runCodexDesignReview(request));
        assertEquals(cut.equals("intent") ? 0 : 1, Files.exists(root.resolve("executed")) ? Files.readAllLines(root.resolve("executed")).size() : 0);
    }

    private AcpxRunRequest request(AcpxExecutionEventSink sink) throws Exception {
        var prompt = root.resolve("prompt.md"); Files.writeString(prompt, "Fixture prompt");
        return new AcpxRunRequest(root, "exact-session", prompt, root.resolve("stdout"), root.resolve("stderr"), AcpxPermissionProfile.implement(), sink);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void revokingPolicyStopsProgressButDoesNotBlockExactSessionCleanup(boolean corrupted) throws Exception {
        var policy = ClaudeExecutionPolicy.write(root, "ws", "node", "run", Files.createDirectories(root.resolve("run")));
        if (corrupted) Files.writeString(root.resolve(policy.ref()), "revoked policy");
        var events = new ArrayList<AcpxExecutionEvent>();
        var initial = request(event -> {
            events.add(event);
            if (event.kind() == AgentExecutionTimelineEventKind.COMMAND_INTENT) {
                try { Files.writeString(root.resolve("intent"), String.valueOf(event.details().get("command_id"))); }
                catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
            }
        });
        var bound = new AcpxRunRequest(root, initial.sessionName(), initial.promptPath(), initial.stdoutPath(), initial.stderrPath(),
                initial.permissionProfile(), initial.executionEventSink(), policy);
        assertTrue(new AcpxAgentTransport(executable().toString()).stopRecordedSession("claude", bound));
        assertEquals(List.of("session_cancel", "session_close"), events.stream()
                .filter(e -> e.kind() == AgentExecutionTimelineEventKind.COMMAND_INTENT).map(e -> e.details().get("purpose")).toList());
        assertTrue(events.stream().allMatch(e -> Boolean.TRUE.equals(e.details().get("cleanup"))
                && List.of().equals(e.details().get("request_options"))));
    }
    private Path executable() throws Exception {
        var script = root.resolve("fixture.sh");
        Files.writeString(script, "#!/bin/sh\n[ -s intent ] || exit 93\ncat intent >> executed\nprintf '\\n' >> executed\nrm intent\nfor arg in \"$@\"; do [ \"$arg\" = cancel ] && exit 1; done\nprintf '[done] end_turn\\n'\nexit 0\n");
        Files.writeString(script, Files.readString(script).replace("for arg in", SessionMetadataFixture.RESPONSE + "for arg in"));
        assertTrue(script.toFile().setExecutable(true)); return script;
    }
}
