package dev.dual.adapter.acpx;

import dev.dual.core.execution.AdapterSignalType;
import dev.dual.core.execution.AgentExecutionTimelineEventKind;
import dev.dual.core.execution.ProcessUsability;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcpxAgentTransportTest {
    @TempDir
    Path tempDir;

    @Test
    void processStartFailureReturnsTransportOwnedTypedFacts() throws Exception {
        var prompt = tempDir.resolve("prompt.md");
        var stdout = tempDir.resolve("stdout.md");
        var stderr = tempDir.resolve("stderr.log");
        Files.writeString(prompt, "# Prompt\n", StandardCharsets.UTF_8);

        var result = new AcpxAgentTransport(tempDir.resolve("missing-acpx").toString())
                .runClaudeDesign(new AcpxRunRequest(
                        tempDir,
                        "claude-main",
                        prompt,
                        stdout,
                        stderr,
                        AcpxPermissionProfile.implement()
                ));

        // Proactive ensure runs before the prompt: a missing acpx executable fails
        // session bootstrap first, forming a typed SESSION_BOOTSTRAP_FAILED result
        // rather than a process-stall guess. The prompt process never starts.
        assertEquals(ProcessUsability.EXITED_NON_ZERO_WITH_INCOMPLETE_OUTPUT,
                result.processResult().usability());
        assertEquals(4, result.exitCode());
        assertEquals(1, result.activeAdapterSignals().size());
        assertEquals(AdapterSignalType.SESSION_BOOTSTRAP_FAILED, result.activeAdapterSignals().get(0).type());
        assertTrue(Files.readString(stderr).contains("[dual] acpx session ensure failed"));
    }

    @Test
    void codexEnsuresNamedSessionBeforePromptRun() throws Exception {
        var invocationLog = tempDir.resolve("codex-invocations.log");
        var fakeAcpx = tempDir.resolve("acpx-codex-ensure.sh");
        Files.writeString(fakeAcpx, """
                #!/bin/sh
                echo "$@" >> "__LOG__"
                if [ "$1" = "--help" ]; then exit 0; fi
                if printf '%s ' "$@" | grep -qF ' sessions '; then exit 0; fi
                exit 0
                """.replace("__LOG__", invocationLog.toString()), StandardCharsets.UTF_8);
        assertTrue(fakeAcpx.toFile().setExecutable(true));

        var prompt = tempDir.resolve("codex-prompt.md");
        var stdout = tempDir.resolve("codex-stdout.md");
        var stderr = tempDir.resolve("codex-stderr.log");
        Files.writeString(prompt, "# Prompt\n", StandardCharsets.UTF_8);

        new AcpxAgentTransport(fakeAcpx.toString()).runCodexDesignReview(new AcpxRunRequest(
                tempDir, "codex-reviewer", prompt, stdout, stderr, AcpxPermissionProfile.implement()
        ));

        var log = Files.readString(invocationLog);
        var ensureIdx = log.indexOf("codex sessions ensure --name codex-reviewer");
        var promptIdx = log.indexOf(" -f ");
        assertTrue(ensureIdx >= 0, "Codex must ensure the named session before the prompt: " + log);
        assertTrue(promptIdx > ensureIdx, "session ensure must precede the prompt run: " + log);
    }

    @Test
    void observeProfileEnsureCommandCarriesScopedPermissions() throws Exception {
        var invocationLog = tempDir.resolve("observe-invocations.log");
        var fakeAcpx = tempDir.resolve("acpx-observe.sh");
        Files.writeString(fakeAcpx, """
                #!/bin/sh
                echo "$@" >> "__LOG__"
                exit 0
                """.replace("__LOG__", invocationLog.toString()), StandardCharsets.UTF_8);
        assertTrue(fakeAcpx.toFile().setExecutable(true));

        var prompt = tempDir.resolve("observe-prompt.md");
        var stdout = tempDir.resolve("observe-stdout.md");
        var stderr = tempDir.resolve("observe-stderr.log");
        Files.writeString(prompt, "# Prompt\n", StandardCharsets.UTF_8);

        new AcpxAgentTransport(fakeAcpx.toString()).runClaudeDesign(new AcpxRunRequest(
                tempDir, "claude-observe", prompt, stdout, stderr, AcpxPermissionProfile.observe()
        ));

        var log = Files.readString(invocationLog);
        // Command-boundary assertion only: observe uses `sessions ensure` (not `new`) and
        // carries its scoped permission args. This fake does NOT prove acpx reuses an
        // existing session — that reuse-ignore semantics is established from the acpx
        // 0.11.0 source (ensureSession returns the existing record unchanged), not here.
        var ensureLine = log.lines().filter(line -> line.contains(" sessions ensure ")).findFirst().orElse("");
        assertTrue(ensureLine.contains("sessions ensure --name claude-observe"),
                "observe must use sessions ensure: " + log);
        assertFalse(ensureLine.contains(" sessions new "), "observe must not force a fresh session: " + log);
        assertTrue(ensureLine.contains("--approve-reads"), "observe ensure must carry read approval: " + ensureLine);
        assertTrue(ensureLine.contains("--allowed-tools"), "observe ensure must carry allowed-tools: " + ensureLine);
        assertTrue(ensureLine.contains("--permission-policy"), "observe ensure must carry permission policy: " + ensureLine);
    }

    @Test
    void implementProfileEnsureCommandCarriesImplementationPermissions() throws Exception {
        var invocationLog = tempDir.resolve("implement-invocations.log");
        var fakeAcpx = tempDir.resolve("acpx-implement.sh");
        Files.writeString(fakeAcpx, """
                #!/bin/sh
                echo "$@" >> "__LOG__"
                exit 0
                """.replace("__LOG__", invocationLog.toString()), StandardCharsets.UTF_8);
        assertTrue(fakeAcpx.toFile().setExecutable(true));

        var prompt = tempDir.resolve("implement-prompt.md");
        var stdout = tempDir.resolve("implement-stdout.md");
        var stderr = tempDir.resolve("implement-stderr.log");
        Files.writeString(prompt, "# Prompt\n", StandardCharsets.UTF_8);

        new AcpxAgentTransport(fakeAcpx.toString()).runCodexDesignReview(new AcpxRunRequest(
                tempDir, "codex-implement", prompt, stdout, stderr, AcpxPermissionProfile.implement()
        ));

        var log = Files.readString(invocationLog);
        // Command-boundary assertion only (see observe test): reuse-ignore semantics is
        // proven from acpx 0.11.0 source, not from this fake.
        var ensureLine = log.lines().filter(line -> line.contains(" sessions ensure ")).findFirst().orElse("");
        assertTrue(ensureLine.contains("sessions ensure --name codex-implement"),
                "implement must use sessions ensure: " + log);
        assertFalse(ensureLine.contains(" sessions new "), "implement must not force a fresh session: " + log);
        assertTrue(ensureLine.contains("--approve-all"), "implement ensure must carry approve-all: " + ensureLine);
    }

    @Test
    void contractCorrectionUsesExistingSessionWithoutBootstrapCommand() throws Exception {
        var invocationLog = tempDir.resolve("invocations.log");
        var fakeAcpx = tempDir.resolve("acpx-correction.sh");
        Files.writeString(fakeAcpx, """
                #!/bin/sh
                echo "$@" >> "__LOG__"
                exit 0
                """.replace("__LOG__", invocationLog.toString()), StandardCharsets.UTF_8);
        assertTrue(fakeAcpx.toFile().setExecutable(true));

        var prompt = tempDir.resolve("correction.md");
        var stdout = tempDir.resolve("correction.stdout");
        var stderr = tempDir.resolve("correction.stderr");
        Files.writeString(prompt, "Return strict JSON only.\n", StandardCharsets.UTF_8);
        var request = new AcpxRunRequest(
                tempDir,
                "existing-session",
                prompt,
                stdout,
                stderr,
                AcpxPermissionProfile.contractCorrection()
        );

        var result = new AcpxAgentTransport(fakeAcpx.toString())
                .runAgentResultCorrection("claude", request);

        assertEquals(0, result.exitCode());
        var invocation = Files.readString(invocationLog);
        assertTrue(invocation.contains("claude -s existing-session -f"));
        assertTrue(invocation.contains("--deny-all"));
        assertTrue(invocation.contains("--no-terminal"));
        assertFalse(invocation.contains("sessions new"));
        assertFalse(invocation.contains("sessions ensure"));
    }

    @Test
    void doesNotExitEarlyWhenReconnectSignalAppearsBeforeProcessExit() throws Exception {
        var fakeAcpx = tempDir.resolve("acpx-fake.sh");
        Files.writeString(fakeAcpx, """
                #!/bin/sh
                if [ "$1" = "--help" ]; then
                  exit 0
                fi
                echo "agent needs reconnect" >&2
                sleep 3
                exit 0
                """, StandardCharsets.UTF_8);
        assertTrue(fakeAcpx.toFile().setExecutable(true));

        var prompt = tempDir.resolve("prompt.md");
        var stdout = tempDir.resolve("stdout.md");
        var stderr = tempDir.resolve("stderr.log");
        Files.writeString(prompt, "# Prompt\n", StandardCharsets.UTF_8);

        var transport = new AcpxAgentTransport(fakeAcpx.toString(), Duration.ofSeconds(5));
        var started = System.nanoTime();
        var result = transport.runClaudeDesign(new AcpxRunRequest(
                tempDir,
                "claude-main",
                prompt,
                stdout,
                stderr,
                AcpxPermissionProfile.implement()
        ));
        var elapsedMs = (System.nanoTime() - started) / 1_000_000;

        assertEquals(0, result.exitCode());
        assertTrue(elapsedMs >= 2_500, "reconnect is a recoverable diagnostic, not a process-interrupt signal");
        assertTrue(Files.readString(stderr).contains("agent needs reconnect"));
    }

    @Test
    void stopsWhenReconnectSignalPersistsBeforeTerminalContract() throws Exception {
        var fakeAcpx = tempDir.resolve("acpx-reconnect-stuck.sh");
        Files.writeString(fakeAcpx, """
                #!/bin/sh
                if [ "$1" = "--help" ]; then
                  exit 0
                fi
                if printf '%s ' "$@" | grep -qF ' sessions '; then
                  exit 0
                fi
                echo "agent needs reconnect" >&2
                echo "[client] initialize (running)"
                echo
                echo "[client] session/load (running)"
                sleep 20
                exit 0
                """, StandardCharsets.UTF_8);
        assertTrue(fakeAcpx.toFile().setExecutable(true));

        var prompt = tempDir.resolve("prompt.md");
        var stdout = tempDir.resolve("stdout.md");
        var stderr = tempDir.resolve("stderr.log");
        Files.writeString(prompt, "# Prompt\n", StandardCharsets.UTF_8);

        var transport = new AcpxAgentTransport(fakeAcpx.toString(), Duration.ofSeconds(1));
        var started = System.nanoTime();
        var result = transport.runCodexDesignReview(new AcpxRunRequest(
                tempDir,
                "codex-reviewer",
                prompt,
                stdout,
                stderr,
                AcpxPermissionProfile.implement()
        ));
        var elapsedMs = (System.nanoTime() - started) / 1_000_000;

        assertEquals(4, result.exitCode());
        assertTrue(elapsedMs < 12_000, "stuck reconnect should rebuild once, then enter recovery before acpx timeout");
        assertTrue(Files.readString(stdout).contains("session/load"));
        assertTrue(Files.readString(stderr).contains("agent needs reconnect"));
    }

    @Test
    void doesNotKillWhenSessionStreamShowsReconnectRecovered() throws Exception {
        var previousHome = System.getProperty("user.home");
        var fakeHome = tempDir.resolve("home");
        var sessionsDir = fakeHome.resolve(".acpx").resolve("sessions");
        var sessionId = "11111111-2222-3333-4444-555555555555";
        Files.createDirectories(sessionsDir);
        var fakeAcpx = tempDir.resolve("acpx-reconnect-recovered-stream.sh");
        Files.writeString(fakeAcpx, """
                #!/bin/sh
                if [ "$1" = "--help" ]; then
                  exit 0
                fi
                echo "[acpx] session claude-main (__SESSION_ID__) · agent needs reconnect" >&2
                cat > "__STREAM__" <<'EOF'
                {"jsonrpc":"2.0","id":1,"method":"session/new","params":{}}
                {"jsonrpc":"2.0","result":{"sessionId":"fresh-session"},"id":1}
                {"jsonrpc":"2.0","id":2,"method":"session/prompt","params":{}}
                EOF
                sleep 3
                echo "ACPX_RECOVERED_AND_COMPLETED"
                exit 0
                """
                .replace("__SESSION_ID__", sessionId)
                .replace("__STREAM__", sessionsDir.resolve(sessionId + ".stream.ndjson").toString()), StandardCharsets.UTF_8);
        assertTrue(fakeAcpx.toFile().setExecutable(true));

        var prompt = tempDir.resolve("prompt.md");
        var stdout = tempDir.resolve("stdout.md");
        var stderr = tempDir.resolve("stderr.log");
        Files.writeString(prompt, "# Prompt\n", StandardCharsets.UTF_8);

        try {
            System.setProperty("user.home", fakeHome.toString());
            var transport = new AcpxAgentTransport(fakeAcpx.toString(), Duration.ofSeconds(1));
            var started = System.nanoTime();
            var result = transport.runClaudeDesign(new AcpxRunRequest(
                    tempDir,
                    "claude-main",
                    prompt,
                    stdout,
                    stderr,
                    false
            ));
            var elapsedMs = (System.nanoTime() - started) / 1_000_000;

            assertEquals(0, result.exitCode());
            assertTrue(elapsedMs >= 2_500, "recovered reconnect should not be killed by stale stderr");
            assertTrue(Files.readString(stdout).contains("ACPX_RECOVERED_AND_COMPLETED"));
        } finally {
            System.setProperty("user.home", previousHome);
        }
    }

    @Test
    void sessionUpdateBodyWithFailureWordsCannotTerminateTransport() throws Exception {
        var previousHome = System.getProperty("user.home");
        var fakeHome = tempDir.resolve("body-words-home");
        var sessionsDir = fakeHome.resolve(".acpx").resolve("sessions");
        var sessionId = "33333333-4444-5555-6666-777777777777";
        Files.createDirectories(sessionsDir);
        var fakeAcpx = tempDir.resolve("acpx-body-words.sh");
        Files.writeString(fakeAcpx, """
                #!/bin/sh
                if [ "$1" = "--help" ]; then
                  exit 0
                fi
                if printf '%s ' "$@" | grep -qF ' sessions '; then
                  exit 0
                fi
                echo "[acpx] session codex-reviewer (__SESSION_ID__) · agent connected" >&2
                cat > "__STREAM__" <<'EOF'
                {"jsonrpc":"2.0","method":"session/update","params":{"update":{"sessionUpdate":"agent_message_chunk","content":{"type":"text","text":"Discuss quota, rate limit, agent needs reconnect, api error: 429 and tool_use_error as plain review text."}}}}
                {"jsonrpc":"2.0","method":"session/update","params":{"update":{"sessionUpdate":"tool_call_update","rawOutput":{"is_error":false,"text":"Request rejected (429) was only a fixture."}}}}
                EOF
                sleep 3
                echo "ACPX_BODY_WORDS_COMPLETED"
                exit 0
                """
                .replace("__SESSION_ID__", sessionId)
                .replace("__STREAM__", sessionsDir.resolve(sessionId + ".stream.ndjson").toString()), StandardCharsets.UTF_8);
        assertTrue(fakeAcpx.toFile().setExecutable(true));

        var prompt = tempDir.resolve("body-words-prompt.md");
        var stdout = tempDir.resolve("body-words-stdout.md");
        var stderr = tempDir.resolve("body-words-stderr.log");
        Files.writeString(prompt, "# Prompt\n", StandardCharsets.UTF_8);

        try {
            System.setProperty("user.home", fakeHome.toString());
            var transport = new AcpxAgentTransport(fakeAcpx.toString(), Duration.ofSeconds(1));
            var started = System.nanoTime();
            var result = transport.runCodexDesignReview(new AcpxRunRequest(
                    tempDir,
                    "codex-reviewer",
                    prompt,
                    stdout,
                    stderr,
                    AcpxPermissionProfile.review()
            ));
            var elapsedMs = (System.nanoTime() - started) / 1_000_000;

            assertEquals(0, result.exitCode());
            assertTrue(elapsedMs >= 2_500, "payload words must not terminate the process early");
            assertTrue(Files.readString(stdout).contains("ACPX_BODY_WORDS_COMPLETED"));
            assertFalse(result.executionTimeline().stream()
                    .anyMatch(event -> event.kind() == AgentExecutionTimelineEventKind.RECOVERABLE_SIGNAL_DETECTED));
        } finally {
            System.setProperty("user.home", previousHome);
        }
    }

    @Test
    void returnsCurrentPromptFinalAssistantJsonFromTypedSessionStream() throws Exception {
        var previousHome = System.getProperty("user.home");
        var fakeHome = tempDir.resolve("prompt-completion-home");
        var sessionsDir = fakeHome.resolve(".acpx").resolve("sessions");
        var sessionId = "55555555-6666-7777-8888-999999999999";
        Files.createDirectories(sessionsDir);
        var fakeAcpx = tempDir.resolve("acpx-prompt-completion.sh");
        Files.writeString(fakeAcpx, """
                #!/bin/sh
                if [ "$1" = "--help" ]; then
                  exit 0
                fi
                if printf '%s ' "$@" | grep -qF ' sessions '; then
                  exit 0
                fi
                echo "[acpx] session codex-reviewer (__SESSION_ID__) · agent connected" >&2
                cat > "__STREAM__" <<'EOF'
                {"jsonrpc":"2.0","id":11,"method":"session/prompt","params":{}}
                {"jsonrpc":"2.0","method":"session/update","params":{"update":{"sessionUpdate":"tool_call_update","messageId":"tool-1","content":{"type":"text","text":"{\\"decision\\":\\"WRONG\\"}"}}}}
                {"jsonrpc":"2.0","method":"session/update","params":{"update":{"sessionUpdate":"agent_message_chunk","messageId":"assistant-1","content":{"type":"text","text":"{\\"schema_version\\":1,\\"run_id\\":\\"run-1\\"}"}}}}
                {"jsonrpc":"2.0","id":11,"result":{"stopReason":"end_turn"}}
                EOF
                sleep 3
                exit 0
                """
                .replace("__SESSION_ID__", sessionId)
                .replace("__STREAM__", sessionsDir.resolve(sessionId + ".stream.ndjson").toString()), StandardCharsets.UTF_8);
        assertTrue(fakeAcpx.toFile().setExecutable(true));

        var prompt = tempDir.resolve("prompt-completion.md");
        var stdout = tempDir.resolve("prompt-completion.stdout");
        var stderr = tempDir.resolve("prompt-completion.stderr");
        Files.writeString(prompt, "# Prompt\n", StandardCharsets.UTF_8);

        try {
            System.setProperty("user.home", fakeHome.toString());
            var transport = new AcpxAgentTransport(fakeAcpx.toString(), Duration.ofSeconds(30));
            var durableEvents = new ArrayList<AcpxExecutionEvent>();
            var result = transport.runCodexDesignReview(new AcpxRunRequest(
                    tempDir,
                    "codex-reviewer",
                    prompt,
                    stdout,
                    stderr,
                    AcpxPermissionProfile.review(),
                    durableEvents::add
            ));

            assertEquals(0, result.exitCode());
            assertTrue(result.promptCompletion().completed());
            assertEquals("11", result.promptCompletion().requestId());
            assertEquals("end_turn", result.promptCompletion().stopReason());
            assertEquals("{\"schema_version\":1,\"run_id\":\"run-1\"}",
                    result.promptCompletion().finalAssistantMessage());
            assertTrue(durableEvents.stream()
                    .anyMatch(event -> event.kind() == AgentExecutionTimelineEventKind.PROMPT_ACCEPTED));
            assertTrue(durableEvents.stream()
                    .anyMatch(event -> event.kind() == AgentExecutionTimelineEventKind.PROMPT_COMPLETED));
        } finally {
            System.setProperty("user.home", previousHome);
        }
    }

    @Test
    void executionEventPersistenceFailureStopsTheRunAsTypedTransportFailure() throws Exception {
        var fakeAcpx = tempDir.resolve("acpx-event-persistence-failure.sh");
        Files.writeString(fakeAcpx, """
                #!/bin/sh
                if printf '%s ' "$@" | grep -qF ' sessions '; then exit 0; fi
                sleep 30
                """, StandardCharsets.UTF_8);
        assertTrue(fakeAcpx.toFile().setExecutable(true));
        var prompt = tempDir.resolve("event-failure-prompt.md");
        var stdout = tempDir.resolve("event-failure.stdout");
        var stderr = tempDir.resolve("event-failure.stderr");
        Files.writeString(prompt, "# Prompt\n", StandardCharsets.UTF_8);

        var result = new AcpxAgentTransport(fakeAcpx.toString()).runClaudeDesign(new AcpxRunRequest(
                tempDir,
                "claude-main",
                prompt,
                stdout,
                stderr,
                AcpxPermissionProfile.observe(),
                event -> {
                    if (event.kind() == AgentExecutionTimelineEventKind.PROCESS_STARTED) {
                        throw new IllegalStateException("simulated durable event write failure");
                    }
                }
        ));

        assertEquals(ProcessUsability.EXITED_NON_ZERO_WITH_INCOMPLETE_OUTPUT,
                result.processResult().usability());
        assertTrue(result.processResult().pid() != null && result.processResult().pid() > 0,
                "the typed transport failure must retain the launched process pid");
        assertEquals(AdapterSignalType.PROCESS_STALLED, result.activeAdapterSignals().get(0).type());
        assertTrue(result.activeAdapterSignals().get(0).message().contains("durable event write failure"));
        var pid = result.processResult().pid();
        ProcessHandle.of(pid).ifPresent(handle -> assertDoesNotThrow(
                () -> handle.onExit().get(2, TimeUnit.SECONDS),
                "event persistence failure must terminate the launched provider process"
        ));
        assertFalse(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false),
                "the launched provider process must not survive an execution-event persistence failure");
    }

    @Test
    void processStartBaselineRejectsStalePromptResponseUntilCurrentRequestCompletes() throws Exception {
        var previousHome = System.getProperty("user.home");
        var fakeHome = tempDir.resolve("prompt-occurrence-home");
        var sessionsDir = fakeHome.resolve(".acpx").resolve("sessions");
        var sessionId = "56565656-6767-7878-8989-909090909090";
        var stream = sessionsDir.resolve(sessionId + ".stream.ndjson");
        Files.createDirectories(sessionsDir);
        Files.writeString(stream, """
                {"jsonrpc":"2.0","id":20,"method":"session/prompt","params":{}}
                {"jsonrpc":"2.0","method":"session/update","params":{"update":{"sessionUpdate":"agent_message_chunk","messageId":"old","content":{"type":"text","text":"{\\\"run_id\\\":\\\"old\\\"}"}}}}
                {"jsonrpc":"2.0","id":20,"result":{"stopReason":"end_turn"}}
                """, StandardCharsets.UTF_8);
        var exactResponseWritten = tempDir.resolve("current-exact-response-written");
        var fakeAcpx = tempDir.resolve("acpx-prompt-occurrence.sh");
        Files.writeString(fakeAcpx, """
                #!/bin/sh
                if [ "$1" = "--help" ]; then
                  exit 0
                fi
                if printf '%s ' "$@" | grep -qF ' sessions '; then
                  exit 0
                fi
                echo "[acpx] session codex-reviewer (__SESSION_ID__) · agent connected" >&2
                cat >> "__STREAM__" <<'EOF'
                {"jsonrpc":"2.0","id":21,"method":"session/prompt","params":{}}
                {"jsonrpc":"2.0","id":20,"result":{"stopReason":"end_turn"}}
                EOF
                sleep 3
                cat >> "__STREAM__" <<'EOF'
                {"jsonrpc":"2.0","method":"session/update","params":{"update":{"sessionUpdate":"agent_message_chunk","messageId":"new","content":{"type":"text","text":"{\\\"run_id\\\":\\\"new\\\"}"}}}}
                {"jsonrpc":"2.0","id":21,"result":{"stopReason":"end_turn"}}
                EOF
                touch "__SENTINEL__"
                sleep 3
                exit 0
                """
                .replace("__SESSION_ID__", sessionId)
                .replace("__STREAM__", stream.toString())
                .replace("__SENTINEL__", exactResponseWritten.toString()), StandardCharsets.UTF_8);
        assertTrue(fakeAcpx.toFile().setExecutable(true));

        var prompt = tempDir.resolve("prompt-occurrence.md");
        var stdout = tempDir.resolve("prompt-occurrence.stdout");
        var stderr = tempDir.resolve("prompt-occurrence.stderr");
        Files.writeString(prompt, "# Prompt\n", StandardCharsets.UTF_8);

        try {
            System.setProperty("user.home", fakeHome.toString());
            var result = new AcpxAgentTransport(fakeAcpx.toString(), Duration.ofSeconds(30))
                    .runCodexDesignReview(new AcpxRunRequest(
                            tempDir,
                            "codex-reviewer",
                            prompt,
                            stdout,
                            stderr,
                            AcpxPermissionProfile.review()));

            assertEquals(0, result.exitCode());
            assertTrue(Files.exists(exactResponseWritten),
                    "the stale pre-baseline response must not terminate the wrapper before id=21 completes");
            assertTrue(result.promptCompletion().completed());
            assertEquals("21", result.promptCompletion().requestId());
            assertEquals("{\"run_id\":\"new\"}", result.promptCompletion().finalAssistantMessage());
        } finally {
            System.setProperty("user.home", previousHome);
        }
    }

    @Test
    void processStartBaselineAndStaleResponseCannotCompleteCurrentPrompt() throws Exception {
        var previousHome = System.getProperty("user.home");
        var fakeHome = tempDir.resolve("stale-only-home");
        var sessionsDir = fakeHome.resolve(".acpx").resolve("sessions");
        var sessionId = "57575757-6868-7979-8080-919191919191";
        var stream = sessionsDir.resolve(sessionId + ".stream.ndjson");
        Files.createDirectories(sessionsDir);
        Files.writeString(stream, """
                {"jsonrpc":"2.0","id":20,"method":"session/prompt","params":{}}
                {"jsonrpc":"2.0","method":"session/update","params":{"update":{"sessionUpdate":"agent_message_chunk","messageId":"old","content":{"type":"text","text":"{\\\"run_id\\\":\\\"old\\\"}"}}}}
                {"jsonrpc":"2.0","id":20,"result":{"stopReason":"end_turn"}}
                """, StandardCharsets.UTF_8);
        var fakeAcpx = tempDir.resolve("acpx-stale-only.sh");
        Files.writeString(fakeAcpx, """
                #!/bin/sh
                if [ "$1" = "--help" ]; then
                  exit 0
                fi
                if printf '%s ' "$@" | grep -qF ' sessions '; then
                  exit 0
                fi
                echo "[acpx] session codex-reviewer (__SESSION_ID__) · agent connected" >&2
                cat >> "__STREAM__" <<'EOF'
                {"jsonrpc":"2.0","id":21,"method":"session/prompt","params":{}}
                {"jsonrpc":"2.0","id":20,"result":{"stopReason":"end_turn"}}
                EOF
                sleep 3
                exit 0
                """
                .replace("__SESSION_ID__", sessionId)
                .replace("__STREAM__", stream.toString()), StandardCharsets.UTF_8);
        assertTrue(fakeAcpx.toFile().setExecutable(true));
        var prompt = tempDir.resolve("stale-only-prompt.md");
        var stdout = tempDir.resolve("stale-only.stdout");
        var stderr = tempDir.resolve("stale-only.stderr");
        Files.writeString(prompt, "# Prompt\n", StandardCharsets.UTF_8);

        try {
            System.setProperty("user.home", fakeHome.toString());
            var result = new AcpxAgentTransport(fakeAcpx.toString(), Duration.ofSeconds(30))
                    .runCodexDesignReview(new AcpxRunRequest(
                            tempDir,
                            "codex-reviewer",
                            prompt,
                            stdout,
                            stderr,
                            AcpxPermissionProfile.review()));

            assertEquals(0, result.exitCode());
            assertFalse(result.promptCompletion().completed());
            assertNotEquals("20", result.promptCompletion().requestId());
        } finally {
            System.setProperty("user.home", previousHome);
        }
    }

    @Test
    void topLevelPromptRateLimitTerminatesThroughTypedStreamError() throws Exception {
        var previousHome = System.getProperty("user.home");
        var fakeHome = tempDir.resolve("typed-429-home");
        var sessionsDir = fakeHome.resolve(".acpx").resolve("sessions");
        var sessionId = "44444444-5555-6666-7777-888888888888";
        Files.createDirectories(sessionsDir);
        var fakeAcpx = tempDir.resolve("acpx-typed-429.sh");
        var secondHalfWritten = tempDir.resolve("typed-429-second-half-written");
        Files.writeString(fakeAcpx, """
                #!/bin/sh
                if [ "$1" = "--help" ]; then
                  exit 0
                fi
                if printf '%s ' "$@" | grep -qF ' sessions '; then
                  exit 0
                fi
                echo "[acpx] session codex-reviewer (__SESSION_ID__) · agent connected" >&2
                cat > "__STREAM__" <<'EOF'
                {"jsonrpc":"2.0","id":9,"method":"session/prompt","params":{}}
                EOF
                sleep 2
                printf '%s' '{"jsonrpc":"2.0","id":9,"error":{"code":-32603,"message":"Internal error","data":{"message":"status=429 rate_' >> "__STREAM__"
                sleep 2
                printf '%s\n' 'limit_exceeded: too many requests"}}}' >> "__STREAM__"
                : > "__SECOND_HALF_WRITTEN__"
                sleep 20
                echo "MUST_NOT_COMPLETE"
                exit 0
                """
                .replace("__SESSION_ID__", sessionId)
                .replace("__SECOND_HALF_WRITTEN__", secondHalfWritten.toString())
                .replace("__STREAM__", sessionsDir.resolve(sessionId + ".stream.ndjson").toString()), StandardCharsets.UTF_8);
        assertTrue(fakeAcpx.toFile().setExecutable(true));

        var prompt = tempDir.resolve("typed-429-prompt.md");
        var stdout = tempDir.resolve("typed-429-stdout.md");
        var stderr = tempDir.resolve("typed-429-stderr.log");
        Files.writeString(prompt, "# Prompt\n", StandardCharsets.UTF_8);

        try {
            System.setProperty("user.home", fakeHome.toString());
            var transport = new AcpxAgentTransport(fakeAcpx.toString(), Duration.ofSeconds(1));
            var result = transport.runCodexDesignReview(new AcpxRunRequest(
                    tempDir,
                    "codex-reviewer",
                    prompt,
                    stdout,
                    stderr,
                    AcpxPermissionProfile.review()
            ));

            assertEquals(4, result.exitCode());
            assertTrue(Files.isRegularFile(secondHalfWritten), "the partial line must not terminate before completion");
            assertFalse(Files.readString(stdout).contains("MUST_NOT_COMPLETE"));
            assertTrue(result.executionTimeline().stream()
                    .filter(event -> event.kind() == AgentExecutionTimelineEventKind.RECOVERABLE_SIGNAL_DETECTED)
                    .map(event -> event.details().get("failureReason"))
                    .anyMatch(AdapterSignalType.RATE_LIMITED.name()::equals));
            assertTrue(result.activeAdapterSignals().stream()
                    .anyMatch(signal -> signal.type() == AdapterSignalType.RATE_LIMITED));
        } finally {
            System.setProperty("user.home", previousHome);
        }
    }

    @Test
    void stopsWhenSessionPromptStallsAfterReconnectRecovery() throws Exception {
        var previousHome = System.getProperty("user.home");
        var fakeHome = tempDir.resolve("home");
        var sessionsDir = fakeHome.resolve(".acpx").resolve("sessions");
        var sessionId = "22222222-3333-4444-5555-666666666666";
        Files.createDirectories(sessionsDir);
        var fakeAcpx = tempDir.resolve("acpx-reconnect-prompt-stalls.sh");
        Files.writeString(fakeAcpx, """
                #!/bin/sh
                if [ "$1" = "--help" ]; then
                  exit 0
                fi
                if printf '%s ' "$@" | grep -qF ' sessions '; then
                  exit 0
                fi
                echo "[acpx] session codex-reviewer (__SESSION_ID__) · agent needs reconnect" >&2
                cat > "__STREAM__" <<'EOF'
                {"jsonrpc":"2.0","id":1,"method":"session/new","params":{}}
                {"jsonrpc":"2.0","result":{"sessionId":"fresh-session"},"id":1}
                {"jsonrpc":"2.0","id":2,"method":"session/prompt","params":{}}
                EOF
                echo "[client] initialize (running)"
                echo
                echo "[client] session/new (running)"
                sleep 20
                exit 0
                """
                .replace("__SESSION_ID__", sessionId)
                .replace("__STREAM__", sessionsDir.resolve(sessionId + ".stream.ndjson").toString()), StandardCharsets.UTF_8);
        assertTrue(fakeAcpx.toFile().setExecutable(true));

        var prompt = tempDir.resolve("prompt.md");
        var stdout = tempDir.resolve("stdout.md");
        var stderr = tempDir.resolve("stderr.log");
        Files.writeString(prompt, "# Prompt\n", StandardCharsets.UTF_8);

        try {
            System.setProperty("user.home", fakeHome.toString());
            var transport = new AcpxAgentTransport(
                    fakeAcpx.toString(),
                    Duration.ofSeconds(30),
                    Duration.ofSeconds(1),
                    Duration.ofSeconds(1)
            );
            var started = System.nanoTime();
            var result = transport.runCodexDesignReview(new AcpxRunRequest(
                    tempDir,
                    "codex-reviewer",
                    prompt,
                    stdout,
                    stderr,
                    AcpxPermissionProfile.review()
            ));
            var elapsedMs = (System.nanoTime() - started) / 1_000_000;

            assertEquals(4, result.exitCode());
            assertTrue(elapsedMs < 8_000, "prompt accepted without trusted progress must enter recovery");
            var stderrText = Files.readString(stderr);
            assertTrue(stderrText.contains("reason=PROCESS_STALLED"), stderrText);
            assertTrue(stderrText.contains("progress_timeout_ms=1000"), stderrText);
            assertEquals(ProcessUsability.STALLED, result.processResult().usability());
            assertTrue(result.activeAdapterSignals().stream()
                    .anyMatch(signal -> signal.type() == AdapterSignalType.PROCESS_STALLED));
        } finally {
            System.setProperty("user.home", previousHome);
        }
    }

    @Test
    void rebuildsReadOnlySessionOnceWhenReconnectRunDoesNotCompleteTerminalContract() throws Exception {
        var fakeAcpx = tempDir.resolve("acpx-reconnect-rebuild.sh");
        var state = tempDir.resolve("state.txt");
        var argsLog = tempDir.resolve("args.log");
        Files.writeString(fakeAcpx, """
                #!/bin/sh
                if [ "$1" = "--help" ]; then
                  exit 0
                fi
                printf '%s\\n' "$*" >> "__ARGS_LOG__"
                if printf '%s ' "$@" | grep -qF ' sessions close '; then
                  echo "closed" >> "__STATE__"
                  exit 0
                fi
                if printf '%s ' "$@" | grep -qF ' sessions ensure '; then
                  echo "ensured" >> "__STATE__"
                  exit 0
                fi
                if ! grep -q "failed" "__STATE__" 2>/dev/null; then
                  echo "agent needs reconnect" >&2
                  echo "[client] initialize (running)"
                  echo "[client] session/new (running)"
                  echo "[done] cancelled"
                  echo "[error] RUNTIME: Interrupted"
                  echo "failed" >> "__STATE__"
                  exit 4
                fi
                echo "ACPX_AFTER_RECONNECT"
                exit 0
                """
                .replace("__ARGS_LOG__", argsLog.toString())
                .replace("__STATE__", state.toString()), StandardCharsets.UTF_8);
        assertTrue(fakeAcpx.toFile().setExecutable(true));

        var prompt = tempDir.resolve("prompt.md");
        var stdout = tempDir.resolve("stdout.md");
        var stderr = tempDir.resolve("stderr.log");
        Files.writeString(prompt, "# Prompt\n", StandardCharsets.UTF_8);

        var transport = new AcpxAgentTransport(fakeAcpx.toString(), Duration.ofSeconds(1));
        var result = transport.runCodexDesignReview(new AcpxRunRequest(
                tempDir,
                "codex-reviewer",
                prompt,
                stdout,
                stderr,
                false
        ));

        assertEquals(0, result.exitCode());
        assertTrue(Files.readString(stdout).contains("ACPX_AFTER_RECONNECT"));
        var args = Files.readString(argsLog);
        assertTrue(args.contains("codex sessions close codex-reviewer"));
        assertTrue(args.contains("codex sessions ensure --name codex-reviewer"));
    }

    @Test
    void boundsSessionBootstrapAfterSessionMissing() throws Exception {
        var fakeAcpx = tempDir.resolve("acpx-session-new-hangs.sh");
        var state = tempDir.resolve("state.txt");
        Files.writeString(fakeAcpx, """
                #!/bin/sh
                if [ "$1" = "--help" ]; then
                  exit 0
                fi
                if printf '%s ' "$@" | grep -qF ' sessions ensure '; then
                  echo "ensuring session"
                  sleep 20
                  exit 0
                fi
                if [ -f "__STATE__" ]; then
                  echo "SECOND_MAIN_AGENT_SHOULD_NOT_RUN" >&2
                  exit 4
                fi
                touch "__STATE__"
                echo "No acpx session found" >&2
                exit 4
                """.replace("__STATE__", state.toString()), StandardCharsets.UTF_8);
        assertTrue(fakeAcpx.toFile().setExecutable(true));

        var prompt = tempDir.resolve("prompt.md");
        var stdout = tempDir.resolve("stdout.md");
        var stderr = tempDir.resolve("stderr.log");
        Files.writeString(prompt, "# Prompt\n", StandardCharsets.UTF_8);

        var transport = new AcpxAgentTransport(
                fakeAcpx.toString(),
                Duration.ofSeconds(1),
                Duration.ofSeconds(1)
        );
        var started = System.nanoTime();
        var result = transport.runClaudeDesign(new AcpxRunRequest(
                tempDir,
                "claude-main",
                prompt,
                stdout,
                stderr,
                AcpxPermissionProfile.implement()
        ));
        var elapsedMs = (System.nanoTime() - started) / 1_000_000;

        assertEquals(4, result.exitCode());
        assertEquals(AdapterSignalType.SESSION_BOOTSTRAP_FAILED,
                result.activeAdapterSignals().get(0).type());
        assertTrue(elapsedMs < 8_000, "proactive session bootstrap must be bounded");
        assertFalse(Files.exists(state), "prompt side effect must not occur when bootstrap fails");
        var stderrText = Files.readString(stderr);
        assertFalse(stderrText.contains("No acpx session found"),
                "ensure is proactive; the prompt-run session-missing stderr must not appear");
        assertFalse(stderrText.contains("SECOND_MAIN_AGENT_SHOULD_NOT_RUN"), stderrText);
        assertTrue(stderrText.contains("[dual] acpx session ensure failed"));
        assertTrue(stderrText.contains("timed_out=true"));
    }

    @Test
    void rebuildsSessionWhenReconnectRunHasOnlyThinkingFrame() throws Exception {
        var fakeAcpx = tempDir.resolve("acpx-reconnect-thinking.sh");
        var state = tempDir.resolve("state.txt");
        Files.writeString(fakeAcpx, """
                #!/bin/sh
                if [ "$1" = "--help" ]; then
                  exit 0
                fi
                if printf '%s ' "$@" | grep -qF ' sessions '; then
                  exit 0
                fi
                if [ ! -f "__STATE__" ]; then
                  echo "agent needs reconnect" >&2
                  echo "[client] initialize (running)"
                  echo
                  echo "[client] session/load (running)"
                  echo
                  echo "[thinking] analyzing previous review"
                  echo "           must fix one"
                  echo "           must fix two"
                  echo
                  echo "[done] cancelled"
                  echo "[error] RUNTIME: Interrupted"
                  touch "__STATE__"
                  exit 4
                fi
                echo "ACPX_AFTER_THINKING_RECONNECT"
                exit 0
                """.replace("__STATE__", state.toString()), StandardCharsets.UTF_8);
        assertTrue(fakeAcpx.toFile().setExecutable(true));

        var prompt = tempDir.resolve("prompt.md");
        var stdout = tempDir.resolve("stdout.md");
        var stderr = tempDir.resolve("stderr.log");
        Files.writeString(prompt, "# Prompt\n", StandardCharsets.UTF_8);

        var transport = new AcpxAgentTransport(fakeAcpx.toString(), Duration.ofSeconds(1));
        var result = transport.runClaudeDesign(new AcpxRunRequest(
                tempDir,
                "claude-main-run-001",
                prompt,
                stdout,
                stderr,
                false
        ));

        assertEquals(0, result.exitCode());
        assertTrue(Files.readString(stdout).contains("ACPX_AFTER_THINKING_RECONNECT"));
    }

    @Test
    void allowsTransientRecoverableStderrSignalToCompleteWithinGracePeriod() throws Exception {
        var fakeAcpx = tempDir.resolve("acpx-transient.sh");
        Files.writeString(fakeAcpx, """
                #!/bin/sh
                if [ "$1" = "--help" ]; then
                  exit 0
                fi
                echo "agent needs reconnect" >&2
                sleep 1
                echo "ACPX_PROBE_OK"
                exit 0
                """, StandardCharsets.UTF_8);
        assertTrue(fakeAcpx.toFile().setExecutable(true));

        var prompt = tempDir.resolve("prompt.md");
        var stdout = tempDir.resolve("stdout.md");
        var stderr = tempDir.resolve("stderr.log");
        Files.writeString(prompt, "# Prompt\n", StandardCharsets.UTF_8);

        var transport = new AcpxAgentTransport(fakeAcpx.toString(), Duration.ofSeconds(5));
        var result = transport.runClaudeDesign(new AcpxRunRequest(
                tempDir,
                "claude-main",
                prompt,
                stdout,
                stderr,
                false
        ));

        assertEquals(0, result.exitCode());
        assertTrue(Files.readString(stdout).contains("ACPX_PROBE_OK"));
        assertTrue(Files.readString(stderr).contains("agent needs reconnect"));
    }

    @Test
    void passesAllowedToolsWhenPermissionProfileIsReadOnlyObservation() throws Exception {
        var fakeAcpx = tempDir.resolve("acpx-args.sh");
        var argsLog = tempDir.resolve("args.log");
        Files.writeString(fakeAcpx, """
                #!/bin/sh
                if [ "$1" = "--help" ]; then
                  exit 0
                fi
                printf '%s\\n' "$@" > "__ARGS_LOG__"
                printf 'CLAUDE_CODE_SIMPLE=%s\\n' "$CLAUDE_CODE_SIMPLE" >> "__ARGS_LOG__"
                echo "ACPX_DONE"
                exit 0
                """.replace("__ARGS_LOG__", argsLog.toString()), StandardCharsets.UTF_8);
        assertTrue(fakeAcpx.toFile().setExecutable(true));

        var prompt = tempDir.resolve("prompt.md");
        var stdout = tempDir.resolve("stdout.md");
        var stderr = tempDir.resolve("stderr.log");
        Files.writeString(prompt, "# Prompt\n", StandardCharsets.UTF_8);

        var transport = new AcpxAgentTransport(fakeAcpx.toString(), Duration.ofSeconds(1));
        var result = transport.runClaudeDesign(new AcpxRunRequest(
                tempDir,
                "claude-main-run-001",
                prompt,
                stdout,
                stderr,
                AcpxPermissionProfile.observe()
        ));

        assertEquals(0, result.exitCode());
        var args = Files.readString(argsLog);
        assertTrue(args.contains("claude\n-s\nclaude-main-run-001\n-f\n" + prompt));
        assertTrue(args.contains("--approve-reads"));
        assertTrue(args.contains("--non-interactive-permissions"));
        assertTrue(args.contains("--permission-policy"));
        assertTrue(args.contains("\"autoApprove\":[\"read\",\"search\"]"));
        assertTrue(args.contains("--allowed-tools"));
        assertTrue(args.contains("Read,Grep,Glob"));
        assertFalse(args.contains("Bash(git *)"));
        assertFalse(args.contains("\"execute\""));
    }

    @Test
    void runsContractDiscoveryThroughAcpxSessionWithHardToolBoundary() throws Exception {
        var fakeAcpx = tempDir.resolve("acpx-contract-discovery.sh");
        var acpxLog = tempDir.resolve("acpx.log");
        Files.writeString(fakeAcpx, """
                #!/bin/sh
                printf '%s\\n' "$@" >> "__ACPX_LOG__"
                printf 'CLAUDE_CODE_SIMPLE=%s\\n' "$CLAUDE_CODE_SIMPLE" >> "__ACPX_LOG__"
                if printf '%s ' "$@" | grep -qF ' sessions new '; then
                  exit 0
                fi
                echo "# Intake"
                echo
                echo '```dual-control'
                echo 'decision: READY_FOR_PRODUCT_REVIEW'
                echo 'summary: acpx restricted runner completed'
                echo '```'
                exit 0
                """.replace("__ACPX_LOG__", acpxLog.toString()), StandardCharsets.UTF_8);
        assertTrue(fakeAcpx.toFile().setExecutable(true));

        var prompt = tempDir.resolve("prompt.md");
        var stdout = tempDir.resolve("stdout.md");
        var stderr = tempDir.resolve("stderr.log");
        Files.writeString(prompt, "# Prompt\nUse orchestrator seed only.\n", StandardCharsets.UTF_8);

        var transport = new AcpxAgentTransport(
                fakeAcpx.toString(),
                Duration.ofSeconds(1)
        );
        var result = transport.runClaudeDesign(new AcpxRunRequest(
                tempDir,
                "claude-main-run-001",
                prompt,
                stdout,
                stderr,
                AcpxPermissionProfile.contractDiscovery()
        ));

        assertEquals(0, result.exitCode());
        var args = Files.readString(acpxLog);
        assertTrue(args.contains("--deny-all"), args);
        assertTrue(args.contains("--non-interactive-permissions\nfail"), args);
        assertTrue(args.contains("--permission-policy"), args);
        assertTrue(args.contains("\"autoApprove\":[\"search\"]"), args);
        assertTrue(args.contains("--allowed-tools\nGrep,Glob"), args);
        assertTrue(args.contains("--no-terminal"), args);
        assertTrue(args.contains("claude\nsessions\nnew\n--name\nclaude-main-run-001"), args);
        assertTrue(args.contains("claude\n-s\nclaude-main-run-001\n-f\n" + prompt), args);
        assertTrue(args.contains("CLAUDE_CODE_SIMPLE=1"), args);
        assertTrue(Files.readString(stdout).contains("READY_FOR_PRODUCT_REVIEW"));
        assertFalse(args.contains("--print"), "contract discovery must not use native claude --print");
    }

    @Test
    void addsPromptRetryForReadOnlyProfilesOnly() throws Exception {
        var fakeAcpx = tempDir.resolve("acpx-readonly-retry-args.sh");
        var argsLog = tempDir.resolve("args.log");
        Files.writeString(fakeAcpx, """
                #!/bin/sh
                if [ "$1" = "--help" ]; then
                  exit 0
                fi
                printf '%s\\n' "$@" > "__ARGS_LOG__"
                echo "ACPX_DONE"
                exit 0
                """.replace("__ARGS_LOG__", argsLog.toString()), StandardCharsets.UTF_8);
        assertTrue(fakeAcpx.toFile().setExecutable(true));

        var prompt = tempDir.resolve("prompt.md");
        var stdout = tempDir.resolve("stdout.md");
        var stderr = tempDir.resolve("stderr.log");
        Files.writeString(prompt, "# Prompt\n", StandardCharsets.UTF_8);

        var transport = new AcpxAgentTransport(fakeAcpx.toString(), Duration.ofSeconds(1));
        var result = transport.runCodexDesignReview(new AcpxRunRequest(
                tempDir,
                "codex-reviewer-run-001",
                prompt,
                stdout,
                stderr,
                AcpxPermissionProfile.review()
        ));

        assertEquals(0, result.exitCode());
        var args = Files.readString(argsLog);
        assertTrue(args.contains("codex\n-s\ncodex-reviewer-run-001\n-f\n" + prompt));
        assertTrue(args.contains("--prompt-retries\n1\n"));
    }

    @Test
    void doesNotAddPromptRetryForImplementationProfile() throws Exception {
        var fakeAcpx = tempDir.resolve("acpx-implement-retry-args.sh");
        var argsLog = tempDir.resolve("args.log");
        Files.writeString(fakeAcpx, """
                #!/bin/sh
                if [ "$1" = "--help" ]; then
                  exit 0
                fi
                printf '%s\\n' "$@" > "__ARGS_LOG__"
                echo "ACPX_DONE"
                exit 0
                """.replace("__ARGS_LOG__", argsLog.toString()), StandardCharsets.UTF_8);
        assertTrue(fakeAcpx.toFile().setExecutable(true));

        var prompt = tempDir.resolve("prompt.md");
        var stdout = tempDir.resolve("stdout.md");
        var stderr = tempDir.resolve("stderr.log");
        Files.writeString(prompt, "# Prompt\n", StandardCharsets.UTF_8);

        var transport = new AcpxAgentTransport(fakeAcpx.toString(), Duration.ofSeconds(1));
        var result = transport.runClaudeDesign(new AcpxRunRequest(
                tempDir,
                "claude-main-run-001",
                prompt,
                stdout,
                stderr,
                AcpxPermissionProfile.implement()
        ));

        assertEquals(0, result.exitCode());
        var args = Files.readString(argsLog);
        assertTrue(args.contains("claude\n-s\nclaude-main-run-001\n-f\n" + prompt));
        assertFalse(args.contains("--prompt-retries"));
        assertTrue(args.contains("--approve-all"));
    }

    @Test
    void stopsWhenReconnectSignalPersistsEvenIfOutputKeepsGrowing() throws Exception {
        var fakeAcpx = tempDir.resolve("acpx-progress.sh");
        Files.writeString(fakeAcpx, """
                #!/bin/sh
                if [ "$1" = "--help" ]; then
                  exit 0
                fi
                echo "agent needs reconnect" >&2
                sleep 1
                echo "thinking"
                sleep 20
                echo "ACPX_DONE"
                exit 0
                """, StandardCharsets.UTF_8);
        assertTrue(fakeAcpx.toFile().setExecutable(true));

        var prompt = tempDir.resolve("prompt.md");
        var stdout = tempDir.resolve("stdout.md");
        var stderr = tempDir.resolve("stderr.log");
        Files.writeString(prompt, "# Prompt\n", StandardCharsets.UTF_8);

        var transport = new AcpxAgentTransport(fakeAcpx.toString(), Duration.ofSeconds(1));
        var result = transport.runClaudeDesign(new AcpxRunRequest(
                tempDir,
                "claude-main",
                prompt,
                stdout,
                stderr,
                AcpxPermissionProfile.implement()
        ));

        assertEquals(4, result.exitCode());
        var output = Files.readString(stdout);
        assertTrue(output.contains("thinking"));
        assertFalse(output.contains("ACPX_DONE"), output);
        assertTrue(Files.readString(stderr).contains("agent needs reconnect"));
    }

    @Test
    void dualControlLookingStdoutCannotStopTheProcess() throws Exception {
        var fakeAcpx = tempDir.resolve("acpx-terminal-contract.sh");
        Files.writeString(fakeAcpx, """
                #!/bin/sh
                if [ "$1" = "--help" ]; then
                  exit 0
                fi
                if printf '%s ' "$@" | grep -qF ' sessions '; then
                  exit 0
                fi
                cat <<'EOF'
                # Done

                ```dual-control
                decision: READY_FOR_PRODUCT_REVIEW
                summary: terminal contract is complete
                ```

                [done] end_turn
                EOF
                sleep 3
                exit 0
                """, StandardCharsets.UTF_8);
        assertTrue(fakeAcpx.toFile().setExecutable(true));

        var prompt = tempDir.resolve("prompt.md");
        var stdout = tempDir.resolve("stdout.md");
        var stderr = tempDir.resolve("stderr.log");
        Files.writeString(prompt, "# Prompt\n", StandardCharsets.UTF_8);

        var transport = new AcpxAgentTransport(fakeAcpx.toString(), Duration.ofSeconds(30));
        var started = System.nanoTime();
        var result = transport.runClaudeDesign(new AcpxRunRequest(
                tempDir,
                "claude-main",
                prompt,
                stdout,
                stderr,
                AcpxPermissionProfile.fromName("", false)
        ));
        var elapsedMs = (System.nanoTime() - started) / 1_000_000;

        assertEquals(0, result.exitCode());
        assertTrue(elapsedMs >= 2_500, "stdout Markdown must not bypass the ACP completion boundary");
        assertTrue(Files.readString(stdout).contains("[done] end_turn"));
        assertFalse(result.promptCompletion().completed());
    }

    @Test
    void malformedTerminalControlCannotStopProcessEarly() throws Exception {
        var fakeAcpx = tempDir.resolve("acpx-malformed-terminal-contract.sh");
        Files.writeString(fakeAcpx, """
                #!/bin/sh
                if [ "$1" = "--help" ]; then
                  exit 0
                fi
                if printf '%s ' "$@" | grep -qF ' sessions '; then
                  exit 0
                fi
                cat <<'EOF'
                ```dual-control
                decision: READY
                decision: PASS
                summary: duplicate key must not stop the process
                ```
                [done] end_turn
                EOF
                sleep 3
                exit 0
                """, StandardCharsets.UTF_8);
        assertTrue(fakeAcpx.toFile().setExecutable(true));

        var prompt = tempDir.resolve("prompt.md");
        var stdout = tempDir.resolve("stdout.md");
        var stderr = tempDir.resolve("stderr.log");
        Files.writeString(prompt, "# Prompt\n", StandardCharsets.UTF_8);

        var transport = new AcpxAgentTransport(fakeAcpx.toString(), Duration.ofSeconds(30));
        var started = System.nanoTime();
        var result = transport.runClaudeDesign(new AcpxRunRequest(
                tempDir,
                "claude-main",
                prompt,
                stdout,
                stderr,
                AcpxPermissionProfile.fromName("", false)
        ));
        var elapsedMs = (System.nanoTime() - started) / 1_000_000;

        assertEquals(0, result.exitCode());
        assertTrue(elapsedMs >= 2_500, "invalid control must wait for the real process exit");
    }

    @Test
    void stopsWhenPermissionBlockedSignalPersistsEvenIfOutputKeepsGrowing() throws Exception {
        var fakeAcpx = tempDir.resolve("acpx-permission-blocked.sh");
        Files.writeString(fakeAcpx, """
                #!/bin/sh
                if [ "$1" = "--help" ]; then
                  exit 0
                fi
                echo "Tool permission request failed: Error: Tool use aborted"
                i=0
                while [ "$i" -lt 20 ]; do
                  echo "thinking $i"
                  i=$((i + 1))
                  sleep 1
                done
                exit 0
                """, StandardCharsets.UTF_8);
        assertTrue(fakeAcpx.toFile().setExecutable(true));

        var prompt = tempDir.resolve("prompt.md");
        var stdout = tempDir.resolve("stdout.md");
        var stderr = tempDir.resolve("stderr.log");
        Files.writeString(prompt, "# Prompt\n", StandardCharsets.UTF_8);

        var transport = new AcpxAgentTransport(fakeAcpx.toString(), Duration.ofSeconds(1));
        var result = transport.runClaudeDesign(new AcpxRunRequest(
                tempDir,
                "claude-main",
                prompt,
                stdout,
                stderr,
                false
        ));

        assertEquals(4, result.exitCode());
        assertTrue(Files.readString(stdout).contains("Tool permission request failed"));
    }

    @Test
    void acpxPermissionDeniedExitProjectsTypedBlockingSignal() throws Exception {
        var fakeAcpx = tempDir.resolve("acpx-permission-denied-exit.sh");
        Files.writeString(fakeAcpx, """
                #!/bin/sh
                if [ "$1" = "--help" ]; then
                  exit 0
                fi
                if printf '%s ' "$@" | grep -qF ' sessions '; then
                  exit 0
                fi
                printf '%s\n' '{"incomplete":"candidate"}'
                exit 5
                """, StandardCharsets.UTF_8);
        assertTrue(fakeAcpx.toFile().setExecutable(true));

        var prompt = tempDir.resolve("permission-prompt.md");
        var stdout = tempDir.resolve("permission-stdout.md");
        var stderr = tempDir.resolve("permission-stderr.log");
        Files.writeString(prompt, "# Prompt\n", StandardCharsets.UTF_8);

        var result = new AcpxAgentTransport(fakeAcpx.toString()).runClaudeDesign(new AcpxRunRequest(
                tempDir,
                "claude-permission",
                prompt,
                stdout,
                stderr,
                AcpxPermissionProfile.observe()
        ));

        assertEquals(5, result.exitCode());
        assertEquals(ProcessUsability.EXITED_NON_ZERO_WITH_INCOMPLETE_OUTPUT, result.processResult().usability());
        var signal = result.activeAdapterSignals().stream()
                .filter(candidate -> candidate.type() == AdapterSignalType.PERMISSION_BLOCKED)
                .findFirst()
                .orElseThrow();
        assertEquals("acpx_exit", signal.source());
        assertEquals("claude-permission", signal.sessionId());
        assertFalse(signal.superseded());
    }
}
