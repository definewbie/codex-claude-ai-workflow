package dev.dual.adapter.acpx;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        assertTrue(elapsedMs >= 2_500, "reconnect is a recoverable diagnostic, not a process-interrupt signal");
        assertTrue(Files.readString(stderr).contains("agent needs reconnect"));
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
                "claude-main",
                prompt,
                stdout,
                stderr,
                AcpxPermissionProfile.observe()
        ));

        assertEquals(0, result.exitCode());
        var args = Files.readString(argsLog);
        assertTrue(args.contains("--approve-reads"));
        assertTrue(args.contains("--non-interactive-permissions"));
        assertTrue(args.contains("--permission-policy"));
        assertTrue(args.contains("\"autoApprove\":[\"read\",\"search\",\"execute\"]"));
        assertTrue(args.contains("--allowed-tools"));
        assertTrue(args.contains("Bash(git *)"));
        assertTrue(args.contains("Bash(rg *)"));
    }

    @Test
    void keepsRunningWhenReconnectSignalIsFollowedByOutputProgress() throws Exception {
        var fakeAcpx = tempDir.resolve("acpx-progress.sh");
        Files.writeString(fakeAcpx, """
                #!/bin/sh
                if [ "$1" = "--help" ]; then
                  exit 0
                fi
                echo "agent needs reconnect" >&2
                sleep 1
                echo "thinking"
                sleep 4
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
                false
        ));

        assertEquals(0, result.exitCode());
        var output = Files.readString(stdout);
        assertTrue(output.contains("thinking"));
        assertTrue(output.contains("ACPX_DONE"));
        assertTrue(Files.readString(stderr).contains("agent needs reconnect"));
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
