package dev.dual.adapter.acpx;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
    private static final long DEFAULT_RECOVERABLE_SIGNAL_GRACE_SECONDS = 30;
    private static final long TERMINAL_OUTPUT_GRACE_SECONDS = 2;
    private static final long DEFAULT_SESSION_COMMAND_TIMEOUT_SECONDS = 45;
    private static final String RECOVERABLE_SIGNAL_GRACE_ENV = "DUAL_ACPX_RECOVERABLE_SIGNAL_GRACE_SECONDS";
    private static final String SESSION_COMMAND_TIMEOUT_ENV = "DUAL_ACPX_SESSION_COMMAND_TIMEOUT_SECONDS";
    private static final String API_TIMEOUT_MS_ENV = "API_TIMEOUT_MS";

    private final AcpxFailureClassifier failureClassifier = new AcpxFailureClassifier();
    private final AgentTerminalContract terminalContract = new AgentTerminalContract();
    private final String acpxExecutable;
    private final Duration recoverableSignalGrace;
    private final Duration sessionCommandTimeout;

    public AcpxAgentTransport() {
        this("acpx");
    }

    AcpxAgentTransport(String acpxExecutable) {
        this(acpxExecutable, configuredRecoverableSignalGrace());
    }

    AcpxAgentTransport(String acpxExecutable, Duration recoverableSignalGrace) {
        this(acpxExecutable, recoverableSignalGrace, configuredSessionCommandTimeout());
    }

    AcpxAgentTransport(String acpxExecutable, Duration recoverableSignalGrace, Duration sessionCommandTimeout) {
        if (acpxExecutable == null || acpxExecutable.isBlank()) {
            throw new IllegalArgumentException("acpxExecutable is required");
        }
        if (recoverableSignalGrace == null || recoverableSignalGrace.isNegative()) {
            throw new IllegalArgumentException("recoverableSignalGrace must be zero or positive");
        }
        if (sessionCommandTimeout == null || sessionCommandTimeout.isNegative() || sessionCommandTimeout.isZero()) {
            throw new IllegalArgumentException("sessionCommandTimeout must be positive");
        }
        this.acpxExecutable = acpxExecutable;
        this.recoverableSignalGrace = recoverableSignalGrace;
        this.sessionCommandTimeout = sessionCommandTimeout;
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
            if (createSession("claude", request)) {
                result = run("claude", request);
            }
        } else if (isReconnectRequired(request, result)) {
            if (rebuildSession("claude", request)) {
                result = run("claude", request);
            }
        }
        return result;
    }

    public AcpxRunResult runCodexDesignReview(AcpxRunRequest request) throws IOException, InterruptedException {
        var result = run("codex", request);
        if (isMissingSession(request, result)) {
            if (createSession("codex", request)) {
                result = run("codex", request);
            }
        } else if (isReconnectRequired(request, result)) {
            if (rebuildSession("codex", request)) {
                result = run("codex", request);
            }
        }
        return result;
    }

    private AcpxRunResult run(String agent, AcpxRunRequest request) throws IOException, InterruptedException {
        var command = new ArrayList<String>();
        command.add(acpxExecutable);
        command.add("--timeout");
        command.add("600");
        if (request.permissionProfile().promptRetries() > 0) {
            command.add("--prompt-retries");
            command.add(Integer.toString(request.permissionProfile().promptRetries()));
        }
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

    private boolean createSession(String agent, AcpxRunRequest request) throws InterruptedException {
        var command = List.of(acpxExecutable, agent, "sessions", "new", "--name", request.sessionName());
        var result = runSessionCommand(command, request.workdir());
        if (result.success()) {
            return true;
        }
        appendSessionCommandFailure(
                request.stderrPath(),
                "create",
                agent,
                request.sessionName(),
                result
        );
        return false;
    }

    private boolean rebuildSession(String agent, AcpxRunRequest request) throws InterruptedException {
        closeSession(agent, request);
        return createSession(agent, request);
    }

    private void closeSession(String agent, AcpxRunRequest request) throws InterruptedException {
        var command = List.of(acpxExecutable, agent, "sessions", "close", request.sessionName());
        var result = runSessionCommand(command, request.workdir());
        if (!result.success()) {
            appendSessionCommandFailure(
                    request.stderrPath(),
                    "close",
                    agent,
                    request.sessionName(),
                    result
            );
        }
    }

    private SessionCommandResult runSessionCommand(List<String> command, Path workdir) throws InterruptedException {
        Path outputPath = null;
        try {
            outputPath = Files.createTempFile("dual-acpx-session-", ".log");
            var process = new ProcessBuilder(command)
                    .directory(workdir.toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(outputPath.toFile())
                    .start();
            var completed = process.waitFor(sessionCommandTimeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!completed) {
                terminateProcessTree(process.toHandle());
                return new SessionCommandResult(false, true, -1, readFile(outputPath), command);
            }
            var exitCode = process.exitValue();
            return new SessionCommandResult(exitCode == 0, false, exitCode, readFile(outputPath), command);
        } catch (IOException e) {
            return new SessionCommandResult(false, false, -1, e.getMessage(), command);
        } finally {
            if (outputPath != null) {
                try {
                    Files.deleteIfExists(outputPath);
                } catch (IOException ignored) {
                    // Temporary session command logs are copied into the run stderr before deletion.
                }
            }
        }
    }

    private void appendSessionCommandFailure(
            Path stderrPath,
            String action,
            String agent,
            String sessionName,
            SessionCommandResult result
    ) {
        var message = new StringBuilder()
                .append(System.lineSeparator())
                .append("[dual] acpx session ")
                .append(action)
                .append(" failed")
                .append(" agent=")
                .append(agent)
                .append(" session=")
                .append(sessionName)
                .append(" timed_out=")
                .append(result.timedOut())
                .append(" exit_code=")
                .append(result.exitCode())
                .append(System.lineSeparator());
        if (!result.output().isBlank()) {
            message.append(result.output().strip()).append(System.lineSeparator());
        }
        try {
            Files.writeString(
                    stderrPath,
                    message.toString(),
                    StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND
            );
        } catch (IOException ignored) {
            // The original acpx failure remains in stderr; session bootstrap details are best-effort.
        }
    }

    private int waitForExitOrRecoverableSignal(Process process, AcpxRunRequest request) throws InterruptedException {
        Instant terminalOutputSince = null;
        var startedAt = Instant.now();
        var eventReader = new AgentLifecycleEventReader(request.stdoutPath(), request.stderrPath());
        var health = new AgentExecutionHealth(new AcpxFailureSignalCatalog(), recoverableSignalGrace);
        while (true) {
            if (process.waitFor(STDERR_SIGNAL_POLL_SECONDS, TimeUnit.SECONDS)) {
                return process.exitValue();
            }
            var elapsed = Duration.between(startedAt, Instant.now());
            for (var event : eventReader.readNewEvents()) {
                health.observe(event, elapsed);
            }
            if (terminalContract.isComplete(readFile(request.stdoutPath()))) {
                if (terminalOutputSince == null) {
                    terminalOutputSince = Instant.now();
                    continue;
                }
                if (Duration.between(terminalOutputSince, Instant.now())
                        .compareTo(Duration.ofSeconds(TERMINAL_OUTPUT_GRACE_SECONDS)) >= 0) {
                    health.observe(AgentLifecycleEvent.terminalContractSeen(eventReader.nextSequence()), elapsed);
                    terminateProcessTree(process.toHandle());
                    return 0;
                }
            } else {
                terminalOutputSince = null;
            }
            var assessment = health.assess(elapsed);
            if (assessment.status() == AgentExecutionHealth.Status.RECOVERABLE_FAILURE) {
                appendAdapterDiagnostic(request.stderrPath(), assessment.failure(), startedAt, assessment.failure().interruptRunningProcess());
                terminateProcessTree(process.toHandle());
                return RECOVERABLE_SIGNAL_EXIT_CODE;
            }
        }
    }

    private String readFile(Path path) {
        try {
            return Files.exists(path) ? Files.readString(path) : "";
        } catch (IOException ignored) {
            return "";
        }
    }

    private boolean isMissingSession(AcpxRunRequest request, AcpxRunResult result) {
        if (result.exitCode() != 4) {
            return false;
        }
        var failure = failureClassifier.classify(request.stdoutPath(), request.stderrPath(), result.exitCode());
        return "RECOVERABLE_SESSION_MISSING".equals(failure.reason());
    }

    private boolean isReconnectRequired(AcpxRunRequest request, AcpxRunResult result) {
        if (result.exitCode() != 4) {
            return false;
        }
        if (request.permissionProfile().promptRetries() <= 0) {
            return false;
        }
        var failure = failureClassifier.classify(request.stdoutPath(), request.stderrPath(), result.exitCode());
        return AcpxFailureReason.AGENT_RECONNECT.code().equals(failure.reason())
                && !terminalContract.isComplete(readFile(request.stdoutPath()));
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

    private void appendAdapterDiagnostic(
            Path stderrPath,
            AcpxFailureClassifier.AcpxFailure failure,
            Instant signalSince,
            boolean interrupted
    ) {
        var elapsed = Duration.between(signalSince, Instant.now());
        var message = new StringBuilder()
                .append(System.lineSeparator())
                .append("[dual] acpx recoverable signal persisted")
                .append(" reason=")
                .append(failure.reason())
                .append(" interrupt_process=")
                .append(interrupted)
                .append(" grace_ms=")
                .append(recoverableSignalGrace.toMillis())
                .append(" elapsed_ms=")
                .append(elapsed.toMillis())
                .append(System.lineSeparator());
        try {
            Files.writeString(
                    stderrPath,
                    message.toString(),
                    StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND
            );
        } catch (IOException ignored) {
            // The original acpx stderr remains the authoritative external evidence.
        }
    }

    private static Duration configuredRecoverableSignalGrace() {
        var explicit = durationFromSecondsEnv(
                RECOVERABLE_SIGNAL_GRACE_ENV,
                Duration.ZERO
        );
        if (!explicit.isZero()) {
            return explicit;
        }
        var apiTimeoutMs = durationFromMillisEnv(API_TIMEOUT_MS_ENV, Duration.ZERO);
        if (!apiTimeoutMs.isZero()) {
            return apiTimeoutMs.plusSeconds(5);
        }
        return Duration.ofSeconds(DEFAULT_RECOVERABLE_SIGNAL_GRACE_SECONDS);
    }

    private static Duration configuredSessionCommandTimeout() {
        return durationFromSecondsEnv(
                SESSION_COMMAND_TIMEOUT_ENV,
                Duration.ofSeconds(DEFAULT_SESSION_COMMAND_TIMEOUT_SECONDS)
        );
    }

    private static Duration durationFromSecondsEnv(String name, Duration fallback) {
        var value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            var seconds = Long.parseLong(value.trim());
            if (seconds <= 0) {
                return fallback;
            }
            return Duration.ofSeconds(seconds);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static Duration durationFromMillisEnv(String name, Duration fallback) {
        var value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            var millis = Long.parseLong(value.trim());
            if (millis <= 0) {
                return fallback;
            }
            return Duration.ofMillis(millis);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private record SessionCommandResult(
            boolean success,
            boolean timedOut,
            int exitCode,
            String output,
            List<String> command
    ) {
    }
}
