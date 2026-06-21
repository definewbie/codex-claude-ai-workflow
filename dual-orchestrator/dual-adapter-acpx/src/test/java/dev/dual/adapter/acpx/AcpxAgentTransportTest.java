package dev.dual.adapter.acpx;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcpxAgentTransportTest {
    @TempDir
    Path tempDir;

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
                if [ "$2" = "sessions" ]; then
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
                if [ "$2" = "sessions" ] && [ "$3" = "close" ]; then
                  echo "closed" >> "__STATE__"
                  exit 0
                fi
                if [ "$2" = "sessions" ] && [ "$3" = "new" ]; then
                  echo "new" >> "__STATE__"
                  exit 0
                fi
                if [ ! -f "__STATE__" ]; then
                  echo "agent needs reconnect" >&2
                  echo "[client] initialize (running)"
                  echo "[client] session/new (running)"
                  echo "[done] cancelled"
                  echo "[error] RUNTIME: Interrupted"
                  touch "__STATE__"
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
        assertTrue(args.contains("codex sessions new --name codex-reviewer"));
    }

    @Test
    void doesNotHangWhenSessionCreationDoesNotReturn() throws Exception {
        var fakeAcpx = tempDir.resolve("acpx-session-new-hangs.sh");
        Files.writeString(fakeAcpx, """
                #!/bin/sh
                if [ "$1" = "--help" ]; then
                  exit 0
                fi
                if [ "$2" = "sessions" ] && [ "$3" = "new" ]; then
                  echo "creating session"
                  sleep 20
                  exit 0
                fi
                echo "⚠ No acpx session found" >&2
                exit 4
                """, StandardCharsets.UTF_8);
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
        assertTrue(elapsedMs < 8_000, "session bootstrap must be bounded");
        var stderrText = Files.readString(stderr);
        assertTrue(stderrText.contains("No acpx session found"));
        assertTrue(stderrText.contains("[dual] acpx session create failed"));
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
                if [ "$2" = "sessions" ]; then
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
    void stopsAfterTerminalContractEvenWhenAcpxProcessKeepsRunning() throws Exception {
        var fakeAcpx = tempDir.resolve("acpx-terminal-contract.sh");
        Files.writeString(fakeAcpx, """
                #!/bin/sh
                if [ "$1" = "--help" ]; then
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
                sleep 20
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
                false
        ));
        var elapsedMs = (System.nanoTime() - started) / 1_000_000;

        assertEquals(0, result.exitCode());
        assertTrue(elapsedMs < 8_000, "terminal contract should end the adapter wait without waiting for process sleep");
        assertTrue(Files.readString(stdout).contains("[done] end_turn"));
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
}
