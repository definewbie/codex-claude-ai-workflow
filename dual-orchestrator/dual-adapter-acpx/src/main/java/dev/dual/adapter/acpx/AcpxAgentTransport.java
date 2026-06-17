package dev.dual.adapter.acpx;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

public class AcpxAgentTransport {
    private static final int RECOVERABLE_SIGNAL_EXIT_CODE = 4;
    private static final long STDERR_SIGNAL_POLL_SECONDS = 2;
    private static final long RECOVERABLE_SIGNAL_GRACE_SECONDS = 30;

    private final AcpxFailureClassifier failureClassifier = new AcpxFailureClassifier();
    private final String acpxExecutable;
    private final Duration recoverableSignalGrace;

    public AcpxAgentTransport() {
        this("acpx", Duration.ofSeconds(RECOVERABLE_SIGNAL_GRACE_SECONDS));
    }

    AcpxAgentTransport(String acpxExecutable) {
        this(acpxExecutable, Duration.ofSeconds(RECOVERABLE_SIGNAL_GRACE_SECONDS));
    }

    AcpxAgentTransport(String acpxExecutable, Duration recoverableSignalGrace) {
        if (acpxExecutable == null || acpxExecutable.isBlank()) {
            throw new IllegalArgumentException("acpxExecutable is required");
        }
        if (recoverableSignalGrace == null || recoverableSignalGrace.isNegative()) {
            throw new IllegalArgumentException("recoverableSignalGrace must be zero or positive");
        }
        this.acpxExecutable = acpxExecutable;
        this.recoverableSignalGrace = recoverableSignalGrace;
    }

    public boolean isAvailable(Path workdir) {
        try {
            var process = new ProcessBuilder(acpxExecutable, "--help")
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
        command.add(acpxExecutable);
        command.add("--timeout");
        command.add("600");
        if (request.approveAll()) {
            command.add("--approve-all");
        } else {
            command.add("--approve-reads");
            command.add("--non-interactive-permissions");
            command.add("fail");
            if (!request.permissionProfile().permissionPolicyJson().isBlank()) {
                command.add("--permission-policy");
                command.add(request.permissionProfile().permissionPolicyJson());
            }
            if (!request.permissionProfile().allowedTools().isEmpty()) {
                command.add("--allowed-tools");
                command.add(request.permissionProfile().allowedToolsArgument());
            }
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
            var exitCode = waitForExitOrRecoverableSignal(process, request);
            return new AcpxRunResult(exitCode, startedAt, Instant.now(), List.copyOf(command));
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
            cleanupManagedToolsStartedByRun(managedProcessBaseline);
        }
    }

    private void createSession(String agent, Path workdir, String sessionName) throws IOException, InterruptedException {
        var process = new ProcessBuilder(acpxExecutable, agent, "sessions", "new", "--name", sessionName)
                .directory(workdir.toFile())
                .redirectErrorStream(true)
                .start();
        var output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        var exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("failed to create acpx " + agent + " session " + sessionName + ": " + output);
        }
    }

    private int waitForExitOrRecoverableSignal(Process process, AcpxRunRequest request) throws InterruptedException {
        Instant recoverableSignalSince = null;
        while (true) {
            if (process.waitFor(STDERR_SIGNAL_POLL_SECONDS, TimeUnit.SECONDS)) {
                return process.exitValue();
            }
            var failure = failureClassifier.classify(
                    request.stdoutPath(),
                    request.stderrPath(),
                    RECOVERABLE_SIGNAL_EXIT_CODE
            );
            if (!failure.recoverable()) {
                recoverableSignalSince = null;
                continue;
            }
            if (!failure.interruptRunningProcess()) {
                recoverableSignalSince = null;
                continue;
            }
            if (recoverableSignalSince == null) {
                recoverableSignalSince = Instant.now();
                continue;
            }
            if (java.time.Duration.between(recoverableSignalSince, Instant.now())
                    .compareTo(recoverableSignalGrace) < 0) {
                continue;
            }
            terminateProcessTree(process.toHandle());
            return RECOVERABLE_SIGNAL_EXIT_CODE;
        }
    }

    private long outputBytes(AcpxRunRequest request) {
        return fileSize(request.stdoutPath()) + fileSize(request.stderrPath());
    }

    private long fileSize(Path path) {
        try {
            return Files.exists(path) ? Files.size(path) : 0L;
        } catch (IOException ignored) {
            return 0L;
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
        try {
            handle.descendants().forEach(this::terminateProcess);
        } catch (RuntimeException ignored) {
            // Some sandboxed runtimes forbid descendant enumeration. The direct
            // acpx process is still the adapter-owned boundary and must be
            // terminated so the workflow can enter recovery instead of hanging.
        }
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
