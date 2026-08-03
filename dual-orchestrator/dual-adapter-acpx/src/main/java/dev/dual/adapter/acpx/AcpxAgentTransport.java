package dev.dual.adapter.acpx;

import dev.dual.core.execution.AgentExecutionTimelineEventKind;
import dev.dual.core.execution.AdapterSignal;
import dev.dual.core.execution.AdapterSignalType;
import dev.dual.core.execution.ProcessResult;
import dev.dual.core.execution.ProcessUsability;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

public class AcpxAgentTransport {
    private static final int RECOVERABLE_SIGNAL_EXIT_CODE = 4;
    private static final int ACPX_PERMISSION_DENIED_EXIT_CODE = 5;
    private static final long STDERR_SIGNAL_POLL_SECONDS = 2;
    private static final long DEFAULT_RECOVERABLE_SIGNAL_GRACE_SECONDS = 30;
    private static final long DEFAULT_ACPX_COMMAND_TIMEOUT_SECONDS = 600;
    private static final long DEFAULT_AGENT_PROGRESS_TIMEOUT_SECONDS = DEFAULT_ACPX_COMMAND_TIMEOUT_SECONDS;
    private static final long DEFAULT_QUEUE_TTL_SECONDS = 1800;
    private static final long DEFAULT_SESSION_COMMAND_TIMEOUT_SECONDS = 45;
    private static final String RECOVERABLE_SIGNAL_GRACE_ENV = "DUAL_ACPX_RECOVERABLE_SIGNAL_GRACE_SECONDS";
    private static final String AGENT_PROGRESS_TIMEOUT_ENV = "DUAL_ACPX_AGENT_PROGRESS_TIMEOUT_SECONDS";
    private static final String QUEUE_TTL_ENV = "DUAL_ACPX_QUEUE_TTL_SECONDS";
    private static final String SESSION_COMMAND_TIMEOUT_ENV = "DUAL_ACPX_SESSION_COMMAND_TIMEOUT_SECONDS";
    private static final String API_TIMEOUT_MS_ENV = "API_TIMEOUT_MS";

    private final String acpxExecutable;
    private final Duration recoverableSignalGrace;
    private final Duration agentProgressTimeout;
    private final Duration sessionCommandTimeout;
    private final Duration queueTtl;

    public AcpxAgentTransport() {
        this("acpx", "claude");
    }

    AcpxAgentTransport(String acpxExecutable) {
        this(acpxExecutable, "claude");
    }

    AcpxAgentTransport(String acpxExecutable, String claudeExecutable) {
        this(acpxExecutable, claudeExecutable, configuredRecoverableSignalGrace());
    }

    AcpxAgentTransport(String acpxExecutable, Duration recoverableSignalGrace) {
        this(acpxExecutable, "claude", recoverableSignalGrace);
    }

    AcpxAgentTransport(String acpxExecutable, String claudeExecutable, Duration recoverableSignalGrace) {
        this(acpxExecutable, claudeExecutable, recoverableSignalGrace, configuredSessionCommandTimeout());
    }

    AcpxAgentTransport(String acpxExecutable, Duration recoverableSignalGrace, Duration sessionCommandTimeout) {
        this(acpxExecutable, "claude", recoverableSignalGrace, sessionCommandTimeout);
    }

    AcpxAgentTransport(
            String acpxExecutable,
            String claudeExecutable,
            Duration recoverableSignalGrace,
            Duration sessionCommandTimeout
    ) {
        this(acpxExecutable, claudeExecutable, recoverableSignalGrace, configuredAgentProgressTimeout(), sessionCommandTimeout);
    }

    AcpxAgentTransport(
            String acpxExecutable,
            Duration recoverableSignalGrace,
            Duration agentProgressTimeout,
            Duration sessionCommandTimeout
    ) {
        this(acpxExecutable, "claude", recoverableSignalGrace, agentProgressTimeout, sessionCommandTimeout);
    }

    AcpxAgentTransport(
            String acpxExecutable,
            String claudeExecutable,
            Duration recoverableSignalGrace,
            Duration agentProgressTimeout,
            Duration sessionCommandTimeout
    ) {
        if (acpxExecutable == null || acpxExecutable.isBlank()) {
            throw new IllegalArgumentException("acpxExecutable is required");
        }
        if (claudeExecutable == null || claudeExecutable.isBlank()) {
            throw new IllegalArgumentException("claudeExecutable is required");
        }
        if (recoverableSignalGrace == null || recoverableSignalGrace.isNegative()) {
            throw new IllegalArgumentException("recoverableSignalGrace must be zero or positive");
        }
        if (agentProgressTimeout == null || agentProgressTimeout.isNegative() || agentProgressTimeout.isZero()) {
            throw new IllegalArgumentException("agentProgressTimeout must be positive");
        }
        if (sessionCommandTimeout == null || sessionCommandTimeout.isNegative() || sessionCommandTimeout.isZero()) {
            throw new IllegalArgumentException("sessionCommandTimeout must be positive");
        }
        this.acpxExecutable = acpxExecutable;
        this.recoverableSignalGrace = recoverableSignalGrace;
        this.agentProgressTimeout = agentProgressTimeout;
        this.sessionCommandTimeout = sessionCommandTimeout;
        this.queueTtl = configuredQueueTtl();
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
        return runWithSession("claude", request);
    }

    public AcpxRunResult runCodexDesignReview(AcpxRunRequest request) throws IOException, InterruptedException {
        return runWithSession("codex", request);
    }

    /**
     * Agent-neutral session-aware run path. The named session is ensured before the
     * prompt process starts, so first-run session creation is owned by transport and
     * never depends on a reactive signal or stderr text. A bootstrap failure forms a
     * typed {@code SESSION_BOOTSTRAP_FAILED} (via {@code SessionCommandResult.toRunResult()})
     * before any prompt side effect. After the run, the typed {@code isMissingSession} and
     * {@code isReconnectRequired} judgments remain the only reconnect triggers: a missing
     * session is re-ensured (idempotent {@code sessions ensure}) and a reconnect signal
     * rebuilds the session once. Bounded correction turns bypass this path and reuse the
     * already established session (see {@link #runAgentResultCorrection(String, AcpxRunRequest)}).
     */
    private AcpxRunResult runWithSession(String agent, AcpxRunRequest request) throws IOException, InterruptedException {
        try {
            var bootstrap = ensureSession(agent, request);
            if (!bootstrap.success()) {
                return bootstrap.toRunResult();
            }
            var result = run(agent, request);
            if (isMissingSession(request, result)) {
                var reensure = ensureSession(agent, request);
                if (reensure.success()) {
                    return run(agent, request);
                }
                return reensure.toRunResult();
            }
            if (isReconnectRequired(request, result)) {
                var rebuild = rebuildSession(agent, request);
                if (rebuild.success()) {
                    return run(agent, request);
                }
                return rebuild.toRunResult();
            }
            return result;
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            return transportExceptionResult(agent, request, Instant.now(), null, failure, true);
        }
    }

    /** Sends a bounded contract-correction turn to the already established session. */
    AcpxRunResult runAgentResultCorrection(String agent, AcpxRunRequest request)
            throws IOException, InterruptedException {
        if (!"claude".equals(agent) && !"codex".equals(agent)) {
            throw new IllegalArgumentException("unsupported agent: " + agent);
        }
        return run(agent, request);
    }

    private AcpxRunResult run(String agent, AcpxRunRequest request) throws IOException, InterruptedException {
        var command = new ArrayList<String>();
        command.add(acpxExecutable);
        command.add("--timeout");
        command.add(Long.toString(DEFAULT_ACPX_COMMAND_TIMEOUT_SECONDS));
        command.add("--ttl");
        command.add(Long.toString(queueTtl.toSeconds()));
        addPermissionArguments(command, request);
        command.add(agent);
        command.add("-s");
        command.add(request.sessionName());
        command.add("-f");
        command.add(request.promptPath().toString());

        var managedProcessBaseline = managedToolPids();
        var sessionStreamBaseline = sessionStreamBaseline();
        var startedAt = Instant.now();
        var processBuilder = new ProcessBuilder(command)
                .directory(request.workdir().toFile())
                .redirectOutput(request.stdoutPath().toFile())
                .redirectError(request.stderrPath().toFile());
        if (request.permissionProfile().simpleClaudeCode()) {
            processBuilder.environment().put("CLAUDE_CODE_SIMPLE", "1");
        }
        Process process;
        try {
            process = processBuilder.start();
        } catch (IOException failure) {
            return transportExceptionResult(agent, request, startedAt, null, failure, false);
        }
        try {
            var timeline = new ArrayList<AcpxExecutionEvent>();
            recordEvent(timeline, request, AcpxExecutionEvent.now(
                    AgentExecutionTimelineEventKind.PROCESS_STARTED,
                    "adapter",
                    0,
                    startedAt,
                    Map.of("pid", process.pid())
            ));
            var waitResult = waitForExitOrRecoverableSignal(process, request, sessionStreamBaseline);
            timeline.addAll(waitResult.executionTimeline());
            recordEvent(timeline, request, AcpxExecutionEvent.now(
                    AgentExecutionTimelineEventKind.PROCESS_EXITED,
                    "adapter",
                    timeline.size(),
                    startedAt,
                    Map.of("exitCode", waitResult.exitCode())
            ));
            var finishedAt = Instant.now();
            var usability = waitResult.terminatedByHealth()
                    ? terminatedUsability(waitResult.signal())
                    : exitedUsability(
                            waitResult.exitCode(),
                            waitResult.promptCompletion(),
                            command,
                            startedAt,
                            finishedAt
                    );
            var processResult = new ProcessResult(
                    String.join(" ", command),
                    process.pid(),
                    waitResult.exitCode(),
                    usability,
                    Math.max(0, Duration.between(startedAt, finishedAt).toMillis()),
                    relative(request.workdir(), request.stdoutPath()),
                    relative(request.workdir(), request.stderrPath()),
                    usability == ProcessUsability.INTERRUPTED,
                    usability == ProcessUsability.TIMED_OUT
            );
            return new AcpxRunResult(
                    startedAt,
                    finishedAt,
                    List.copyOf(command),
                    timeline,
                    null,
                    waitResult.promptCompletion(),
                    processResult,
                    waitResult.signals()
            );
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            return transportExceptionResult(agent, request, startedAt, process.pid(), failure, true);
        } catch (ExecutionEventPersistenceException failure) {
            return transportExceptionResult(agent, request, startedAt, process.pid(), failure, false);
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
            cleanupManagedToolsStartedByRun(managedProcessBaseline);
        }
    }

    private AcpxRunResult transportExceptionResult(
            String agent,
            AcpxRunRequest request,
            Instant startedAt,
            Long pid,
            Exception failure,
            boolean interrupted
    ) {
        var finishedAt = Instant.now();
        var command = List.of(acpxExecutable, agent, "transport");
        var message = "acpx transport failed: " + (failure.getMessage() == null
                ? failure.getClass().getSimpleName()
                : failure.getMessage());
        try {
            Files.createDirectories(request.stderrPath().getParent());
            Files.writeString(
                    request.stderrPath(),
                    message + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND
            );
        } catch (IOException ignored) {
            // The typed result remains authoritative when diagnostic persistence fails.
        }
        var signals = interrupted
                ? List.<AdapterSignal>of()
                : List.of(new AdapterSignal(
                        AdapterSignalType.PROCESS_STALLED,
                        message,
                        0,
                        "transport",
                        "",
                        false
                ));
        return new AcpxRunResult(
                startedAt,
                finishedAt,
                command,
                List.of(),
                null,
                AcpxPromptCompletion.EMPTY,
                new ProcessResult(
                        String.join(" ", command),
                        pid,
                        interrupted ? null : 1,
                        interrupted
                                ? ProcessUsability.INTERRUPTED
                                : ProcessUsability.EXITED_NON_ZERO_WITH_INCOMPLETE_OUTPUT,
                        Math.max(0, Duration.between(startedAt, finishedAt).toMillis()),
                        relative(request.workdir(), request.stdoutPath()),
                        relative(request.workdir(), request.stderrPath()),
                        interrupted,
                        false
                ),
                signals
        );
    }

    private void addPermissionArguments(ArrayList<String> command, AcpxRunRequest request) {
        if (request.permissionProfile().promptRetries() > 0) {
            command.add("--prompt-retries");
            command.add(Integer.toString(request.permissionProfile().promptRetries()));
        }
        if (request.approveAll()) {
            command.add("--approve-all");
        } else {
            if (request.permissionProfile().denyAll()) {
                command.add("--deny-all");
            }
            if (request.permissionProfile().approveReads()) {
                command.add("--approve-reads");
            }
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
            if (!request.permissionProfile().terminalEnabled()) {
                command.add("--no-terminal");
            }
        }
    }

    private void recordEvent(List<AcpxExecutionEvent> timeline, AcpxRunRequest request, AcpxExecutionEvent event) {
        timeline.add(event);
        try {
            request.executionEventSink().accept(event);
        } catch (RuntimeException failure) {
            var detail = failure.getMessage() == null
                    ? failure.getClass().getSimpleName()
                    : failure.getMessage();
            throw new ExecutionEventPersistenceException(
                    "failed to persist " + event.kind().wireName() + ": " + detail,
                    failure
            );
        }
    }

    private void recordLifecycleBoundary(
            List<AcpxExecutionEvent> timeline,
            AcpxRunRequest request,
            AgentLifecycleEvent event,
            Instant startedAt
    ) {
        if (event.source() != AgentLifecycleEvent.Source.ACPX_STREAM) {
            return;
        }
        var kind = switch (event.kind()) {
            case PROMPT_ACCEPTED -> AgentExecutionTimelineEventKind.PROMPT_ACCEPTED;
            case PROMPT_COMPLETED -> AgentExecutionTimelineEventKind.PROMPT_COMPLETED;
            default -> null;
        };
        if (kind == null) {
            return;
        }
        recordEvent(timeline, request, AcpxExecutionEvent.now(
                kind,
                "acpx_stream",
                event.sequence(),
                startedAt,
                Map.of("lifecycleSequence", event.sequence())
        ));
    }

    private SessionCommandResult ensureSession(String agent, AcpxRunRequest request) throws InterruptedException {
        var command = new ArrayList<String>();
        command.add(acpxExecutable);
        // Both `sessions new` and the idempotent `sessions ensure` carry the current
        // request's permission/profile args. acpx 0.11.0 applies these only when a
        // fresh session is created (createClient); an existing persistent record is
        // returned unchanged on reuse, so reusing a session never alters its policy.
        addPermissionArguments(command, request);
        command.add(agent);
        command.add("sessions");
        command.add(request.permissionProfile().requiresFreshSession() ? "new" : "ensure");
        command.add("--name");
        command.add(request.sessionName());
        var result = runSessionCommand(command, request.workdir(), request.permissionProfile().simpleClaudeCode());
        if (result.success()) {
            return result;
        }
        appendSessionCommandFailure(
                request.stderrPath(),
                "ensure",
                agent,
                request.sessionName(),
                result
        );
        return result;
    }

    private SessionCommandResult rebuildSession(String agent, AcpxRunRequest request) throws InterruptedException {
        closeSession(agent, request);
        return ensureSession(agent, request);
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

    /**
     * Close an acpx provider session by name. P0 session-lifecycle cleanup entry
     * point for the workflow/service layer: <b>never throws</b>, returns false on
     * any failure so callers can treat cleanup as best-effort without affecting
     * workflow status. Does not delete on-disk session files; only invokes
     * {@code acpx <agent> sessions close <sessionName>}.
     */
    public boolean closeSession(String agent, Path workdir, String sessionName) {
        if (agent == null || agent.isBlank() || sessionName == null || sessionName.isBlank()) {
            return false;
        }
        try {
            return runSessionCommand(
                    List.of(acpxExecutable, agent, "sessions", "close", sessionName),
                    workdir
            ).success();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private SessionCommandResult runSessionCommand(List<String> command, Path workdir) throws InterruptedException {
        return runSessionCommand(command, workdir, false);
    }

    private SessionCommandResult runSessionCommand(List<String> command, Path workdir, boolean simpleClaudeCode) throws InterruptedException {
        Path outputPath = null;
        var startedAt = Instant.now();
        try {
            outputPath = Files.createTempFile("dual-acpx-session-", ".log");
            var processBuilder = new ProcessBuilder(command)
                    .directory(workdir.toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(outputPath.toFile());
            if (simpleClaudeCode) {
                processBuilder.environment().put("CLAUDE_CODE_SIMPLE", "1");
            }
            var process = processBuilder.start();
            var completed = process.waitFor(sessionCommandTimeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!completed) {
                terminateProcessTree(process.toHandle());
                return new SessionCommandResult(false, true, -1, readFile(outputPath), command, startedAt, Instant.now());
            }
            var exitCode = process.exitValue();
            return new SessionCommandResult(exitCode == 0, false, exitCode, readFile(outputPath), command, startedAt, Instant.now());
        } catch (IOException e) {
            return new SessionCommandResult(false, false, -1, e.getMessage(), command, startedAt, Instant.now());
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

    private ProcessWaitResult waitForExitOrRecoverableSignal(
            Process process,
            AcpxRunRequest request,
            Map<String, Long> sessionStreamBaseline
    ) throws InterruptedException {
        var startedAt = Instant.now();
        var timeline = new ArrayList<AcpxExecutionEvent>();
        recordEvent(timeline, request, AcpxExecutionEvent.now(
                AgentExecutionTimelineEventKind.PROCESS_WAIT_STARTED,
                "adapter",
                timeline.size(),
                startedAt,
                Map.of("pid", process.pid())
        ));
        var eventReader = new AgentLifecycleEventReader(
                request.stdoutPath(),
                request.stderrPath(),
                Path.of(System.getProperty("user.home"), ".acpx", "sessions"),
                sessionStreamBaseline);
        var health = new AgentExecutionHealth(new AcpxFailureSignalCatalog(), recoverableSignalGrace, agentProgressTimeout);
        while (true) {
            if (process.waitFor(STDERR_SIGNAL_POLL_SECONDS, TimeUnit.SECONDS)) {
                var elapsed = Duration.between(startedAt, Instant.now());
                for (var event : eventReader.readNewEvents()) {
                    health.observe(event, elapsed);
                    recordLifecycleBoundary(timeline, request, event, startedAt);
                }
                var assessment = health.assess(elapsed);
                var exitCode = process.exitValue();
                var signals = new ArrayList<>(assessment.signals());
                var terminalSignal = assessment.signal();
                if (exitCode == ACPX_PERMISSION_DENIED_EXIT_CODE
                        && signals.stream().noneMatch(signal -> signal.type() == AdapterSignalType.PERMISSION_BLOCKED
                                && !signal.superseded())) {
                    terminalSignal = new AdapterSignal(
                            AdapterSignalType.PERMISSION_BLOCKED,
                            "acpx exited with PERMISSION_DENIED (5)",
                            eventReader.nextSequence(),
                            "acpx_exit",
                            request.sessionName(),
                            false
                    );
                    signals.add(terminalSignal);
                }
                recordEvent(timeline, request, AcpxExecutionEvent.now(
                        AgentExecutionTimelineEventKind.PROCESS_WAIT_COMPLETED,
                        "adapter",
                        eventReader.nextSequence(),
                        startedAt,
                        Map.of("exitCode", exitCode, "waitMode", "process_exit")
                ));
                return new ProcessWaitResult(
                        exitCode,
                        List.copyOf(timeline),
                        eventReader.promptCompletion(),
                        signals,
                        terminalSignal,
                        false
                );
            }
            var elapsed = Duration.between(startedAt, Instant.now());
            for (var event : eventReader.readNewEvents()) {
                health.observe(event, elapsed);
                recordLifecycleBoundary(timeline, request, event, startedAt);
            }
            var assessment = health.assess(elapsed);
            if (assessment.status() == AgentExecutionHealth.Status.RECOVERABLE_FAILURE) {
                appendAdapterDiagnostic(request.stderrPath(), assessment.signal(), startedAt, true);
                recordEvent(timeline, request, AcpxExecutionEvent.now(
                        AgentExecutionTimelineEventKind.RECOVERABLE_SIGNAL_DETECTED,
                        "adapter",
                        eventReader.nextSequence(),
                        startedAt,
                        Map.of(
                                "exitCode", RECOVERABLE_SIGNAL_EXIT_CODE,
                                "waitMode", "recoverable_signal",
                                "action", "terminate_process_tree",
                                "failureReason", assessment.signal().type().name()
                        )
                ));
                terminateProcessTree(process.toHandle());
                return new ProcessWaitResult(
                        RECOVERABLE_SIGNAL_EXIT_CODE,
                        List.copyOf(timeline),
                        eventReader.promptCompletion(),
                        assessment.signals(),
                        assessment.signal(),
                        true
                );
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
        return hasActiveSignal(result, AdapterSignalType.SESSION_RESUME_FAILED);
    }

    private boolean isReconnectRequired(AcpxRunRequest request, AcpxRunResult result) {
        if (result.exitCode() != 4) {
            return false;
        }
        if (request.permissionProfile().promptRetries() <= 0) {
            return false;
        }
        return hasActiveSignal(result, AdapterSignalType.AGENT_RECONNECT_REQUIRED)
                && !result.promptCompletion().completed();
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

    private Map<String, Long> sessionStreamBaseline() {
        var sessionsDir = Path.of(System.getProperty("user.home"), ".acpx", "sessions");
        if (!Files.isDirectory(sessionsDir)) {
            return Map.of();
        }
        var baseline = new java.util.LinkedHashMap<String, Long>();
        try (var paths = Files.list(sessionsDir)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".stream.ndjson"))
                    .forEach(path -> {
                        var name = path.getFileName().toString();
                        var sessionId = name.substring(0, name.length() - ".stream.ndjson".length());
                        try {
                            baseline.put(sessionId, Files.size(path));
                        } catch (IOException ignored) {
                            baseline.put(sessionId, 0L);
                        }
                    });
        } catch (IOException ignored) {
            return Map.of();
        }
        return Map.copyOf(baseline);
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
        List<ProcessHandle> descendants = List.of();
        try {
            descendants = handle.descendants().toList();
        } catch (RuntimeException ignored) {
            // Some sandboxed runtimes forbid descendant enumeration. The direct
            // acpx process is still the adapter-owned boundary and must be
            // terminated so the workflow can enter recovery instead of hanging.
        }
        terminateProcess(handle);
        descendants.forEach(this::terminateProcess);
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
            AdapterSignal signal,
            Instant signalSince,
            boolean interrupted
    ) {
        var elapsed = Duration.between(signalSince, Instant.now());
        var message = new StringBuilder()
                .append(System.lineSeparator())
                .append("[dual] acpx recoverable signal persisted")
                .append(" reason=")
                .append(signal == null ? "UNKNOWN" : signal.type().name())
                .append(" interrupt_process=")
                .append(interrupted)
                .append(" grace_ms=")
                .append(recoverableSignalGrace.toMillis())
                .append(" progress_timeout_ms=")
                .append(agentProgressTimeout.toMillis())
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

    private static Duration configuredAgentProgressTimeout() {
        return durationFromSecondsEnv(
                AGENT_PROGRESS_TIMEOUT_ENV,
                Duration.ofSeconds(DEFAULT_AGENT_PROGRESS_TIMEOUT_SECONDS)
        );
    }

    private static Duration configuredQueueTtl() {
        return durationFromSecondsEnv(
                QUEUE_TTL_ENV,
                Duration.ofSeconds(DEFAULT_QUEUE_TTL_SECONDS)
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
            List<String> command,
            Instant startedAt,
            Instant finishedAt
    ) {
        AcpxRunResult toRunResult() {
            var signal = new AdapterSignal(
                    AdapterSignalType.SESSION_BOOTSTRAP_FAILED,
                    output,
                    0,
                    "session_command",
                    "",
                    false
            );
            return new AcpxRunResult(
                    startedAt,
                    finishedAt,
                    command,
                    List.of(),
                    null,
                    AcpxPromptCompletion.EMPTY,
                    new ProcessResult(
                            String.join(" ", command),
                            null,
                            RECOVERABLE_SIGNAL_EXIT_CODE,
                            timedOut ? ProcessUsability.TIMED_OUT : ProcessUsability.EXITED_NON_ZERO_WITH_INCOMPLETE_OUTPUT,
                            Math.max(0, Duration.between(startedAt, finishedAt).toMillis()),
                            "",
                            "",
                            false,
                            timedOut
                    ),
                    List.of(signal)
            );
        }
    }

    private record ProcessWaitResult(
            int exitCode,
            List<AcpxExecutionEvent> executionTimeline,
            AcpxPromptCompletion promptCompletion,
            List<AdapterSignal> signals,
            AdapterSignal signal,
            boolean terminatedByHealth
    ) {
        ProcessWaitResult {
            signals = signals == null ? List.of() : List.copyOf(signals);
        }
    }

    private static boolean hasActiveSignal(AcpxRunResult result, AdapterSignalType type) {
        return result.activeAdapterSignals().stream().anyMatch(signal -> signal.type() == type);
    }

    private static ProcessUsability terminatedUsability(AdapterSignal signal) {
        return signal != null && signal.type() == AdapterSignalType.PROCESS_STALLED
                ? ProcessUsability.STALLED
                : ProcessUsability.KILLED_AFTER_RECOVERABLE_SIGNAL;
    }

    private static ProcessUsability exitedUsability(
            int exitCode,
            AcpxPromptCompletion completion,
            List<String> command,
            Instant startedAt,
            Instant finishedAt
    ) {
        if (exitCode == 0) {
            return ProcessUsability.EXITED_ZERO;
        }
        if (completion != null && completion.completed()) {
            return ProcessUsability.EXITED_NON_ZERO_WITH_COMPLETE_OUTPUT;
        }
        return reachedDeclaredTimeout(command, startedAt, finishedAt, Duration.ofSeconds(5))
                ? ProcessUsability.INTERRUPTED
                : ProcessUsability.EXITED_NON_ZERO_WITH_INCOMPLETE_OUTPUT;
    }

    private static boolean reachedDeclaredTimeout(
            List<String> command,
            Instant startedAt,
            Instant finishedAt,
            Duration grace
    ) {
        for (int index = 0; index < command.size() - 1; index++) {
            if (!"--timeout".equals(command.get(index))) {
                continue;
            }
            try {
                var timeout = Duration.ofSeconds(Long.parseLong(command.get(index + 1)));
                var threshold = Math.max(0, timeout.minus(grace).toMillis());
                return Duration.between(startedAt, finishedAt).toMillis() >= threshold;
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        return false;
    }

    private static String relative(Path root, Path path) {
        try {
            return root.toAbsolutePath().normalize().relativize(path.toAbsolutePath().normalize()).toString();
        } catch (RuntimeException ignored) {
            return path == null ? "" : path.toString();
        }
    }

    private static final class ExecutionEventPersistenceException extends RuntimeException {
        private ExecutionEventPersistenceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
