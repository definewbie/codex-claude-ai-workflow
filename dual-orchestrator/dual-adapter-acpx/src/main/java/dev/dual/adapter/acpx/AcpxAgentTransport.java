package dev.dual.adapter.acpx;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

public class AcpxAgentTransport {
    private final AcpxFailureClassifier failureClassifier = new AcpxFailureClassifier();

    public boolean isAvailable(Path workdir) {
        try {
            var process = new ProcessBuilder("acpx", "--help")
                    .directory(workdir.toFile())
                    .redirectErrorStream(true)
                    .start();
            return process.waitFor() == 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    public AcpxRunResult runClaudeDesign(AcpxRunRequest request) throws IOException, InterruptedException {
        var result = run("claude", request);
        if (isMissingSession(request, result)) {
            createSession("claude", request.workdir(), request.sessionName());
            result = run("claude", request);
        }
        return result;
    }

    public AcpxRunResult runCodexDesignReview(AcpxRunRequest request) throws IOException, InterruptedException {
        var result = run("codex", request);
        if (isMissingSession(request, result)) {
            createSession("codex", request.workdir(), request.sessionName());
            result = run("codex", request);
        }
        return result;
    }

    private AcpxRunResult run(String agent, AcpxRunRequest request) throws IOException, InterruptedException {
        var command = new ArrayList<String>();
        command.add("acpx");
        command.add("--timeout");
        command.add("600");
        if (request.approveAll()) {
            command.add("--approve-all");
        } else {
            command.add("--approve-reads");
            command.add("--non-interactive-permissions");
            command.add("fail");
        }
        command.add(agent);
        command.add("-s");
        command.add(request.sessionName());
        command.add("-f");
        command.add(request.promptPath().toString());

        var managedProcessBaseline = managedToolPids();
        var startedAt = Instant.now();
        var process = new ProcessBuilder(command)
                .directory(request.workdir().toFile())
                .redirectOutput(request.stdoutPath().toFile())
                .redirectError(request.stderrPath().toFile())
                .start();
        try {
            var exitCode = process.waitFor();
            return new AcpxRunResult(exitCode, startedAt, Instant.now(), List.copyOf(command));
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
            cleanupManagedToolsStartedByRun(managedProcessBaseline);
        }
    }

    private void createSession(String agent, Path workdir, String sessionName) throws IOException, InterruptedException {
        var process = new ProcessBuilder("acpx", agent, "sessions", "new", "--name", sessionName)
                .directory(workdir.toFile())
                .redirectErrorStream(true)
                .start();
        var output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        var exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("failed to create acpx " + agent + " session " + sessionName + ": " + output);
        }
    }

    private boolean isMissingSession(AcpxRunRequest request, AcpxRunResult result) {
        if (result.exitCode() != 4) {
            return false;
        }
        var failure = failureClassifier.classify(request.stdoutPath(), request.stderrPath(), result.exitCode());
        return "RECOVERABLE_SESSION_MISSING".equals(failure.reason());
    }

    private Set<Long> managedToolPids() {
        try {
            return ProcessHandle.allProcesses()
                    .map(this::processInfo)
                    .filter(process -> AcpxProcessCleanupPolicy.shouldCleanup(process, Set.of()))
                    .map(AcpxProcessCleanupPolicy.ProcessInfo::pid)
                    .collect(Collectors.toSet());
        } catch (RuntimeException ignored) {
            return Set.of();
        }
    }

    private void cleanupManagedToolsStartedByRun(Set<Long> knownBefore) {
        try {
            ProcessHandle.allProcesses()
                    .filter(handle -> AcpxProcessCleanupPolicy.shouldCleanup(processInfo(handle), knownBefore))
                    .forEach(this::terminateProcessTree);
        } catch (RuntimeException ignored) {
            // Process enumeration can be blocked in sandboxed runtimes. Cleanup is best-effort.
        }
    }

    private AcpxProcessCleanupPolicy.ProcessInfo processInfo(ProcessHandle handle) {
        return new AcpxProcessCleanupPolicy.ProcessInfo(
                handle.pid(),
                handle.info().commandLine().orElse("")
        );
    }

    private void terminateProcessTree(ProcessHandle handle) {
        handle.descendants().forEach(this::terminateProcess);
        terminateProcess(handle);
    }

    private void terminateProcess(ProcessHandle handle) {
        if (!handle.isAlive() || handle.pid() == ProcessHandle.current().pid()) {
            return;
        }
        handle.destroy();
        try {
            if (!handle.onExit().get(2, TimeUnit.SECONDS).isAlive()) {
                return;
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException ignored) {
            // Escalate to a forceful destroy below.
        }
        if (handle.isAlive()) {
            handle.destroyForcibly();
        }
    }
}
