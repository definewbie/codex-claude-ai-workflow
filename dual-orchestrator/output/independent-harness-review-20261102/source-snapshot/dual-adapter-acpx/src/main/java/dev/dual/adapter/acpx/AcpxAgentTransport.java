package dev.dual.adapter.acpx;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

public class AcpxAgentTransport {
    private static final int RECOVERABLE_SIGNAL_EXIT_CODE = 4;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final long STDERR_SIGNAL_POLL_SECONDS = 2;
    private static final long DEFAULT_RECOVERABLE_SIGNAL_GRACE_SECONDS = 30;
    private static final long DEFAULT_AGENT_PROGRESS_TIMEOUT_SECONDS = 600;
    private static final long DEFAULT_QUEUE_TTL_SECONDS = 1800;
    private static final long DEFAULT_SESSION_COMMAND_TIMEOUT_SECONDS = 45;
    private static final String RECOVERABLE_SIGNAL_GRACE_ENV = "DUAL_ACPX_RECOVERABLE_SIGNAL_GRACE_SECONDS";
    private static final String AGENT_PROGRESS_TIMEOUT_ENV = "DUAL_ACPX_AGENT_PROGRESS_TIMEOUT_SECONDS";
    private static final String QUEUE_TTL_ENV = "DUAL_ACPX_QUEUE_TTL_SECONDS";
    private static final String SESSION_COMMAND_TIMEOUT_ENV = "DUAL_ACPX_SESSION_COMMAND_TIMEOUT_SECONDS";
    private static final String API_TIMEOUT_MS_ENV = "API_TIMEOUT_MS";
    private static final String ACPX_EXECUTABLE_ENV = "DUAL_ACPX_EXECUTABLE";
    private static final String ACPX_COMMAND_TIMEOUT_ENV = "DUAL_ACPX_COMMAND_TIMEOUT_SECONDS";

    private final String acpxExecutable;
    private final Duration recoverableSignalGrace;
    private final Duration agentProgressTimeout;
    private final Duration sessionCommandTimeout;
    private final Duration queueTtl;
    private final Duration commandTimeout;

    public AcpxAgentTransport() {
        this(configuredAcpxExecutable(), "claude");
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
        this(acpxExecutable, claudeExecutable, recoverableSignalGrace, agentProgressTimeout,
                sessionCommandTimeout, configuredCommandTimeout(System.getenv(ACPX_COMMAND_TIMEOUT_ENV)));
    }

    AcpxAgentTransport(
            String acpxExecutable,
            String claudeExecutable,
            Duration recoverableSignalGrace,
            Duration agentProgressTimeout,
            Duration sessionCommandTimeout,
            Duration commandTimeout
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
        if (commandTimeout == null || commandTimeout.isNegative()) {
            throw new IllegalArgumentException("commandTimeout must be non-negative");
        }
        this.acpxExecutable = acpxExecutable;
        this.recoverableSignalGrace = recoverableSignalGrace;
        this.agentProgressTimeout = agentProgressTimeout;
        this.sessionCommandTimeout = sessionCommandTimeout;
        this.queueTtl = configuredQueueTtl();
        this.commandTimeout = commandTimeout;
    }

    AcpxAgentTransport(
            String acpxExecutable,
            Duration recoverableSignalGrace,
            Duration agentProgressTimeout,
            Duration sessionCommandTimeout,
            Duration commandTimeout
    ) {
        this(acpxExecutable, "claude", recoverableSignalGrace, agentProgressTimeout,
                sessionCommandTimeout, commandTimeout);
    }

    private static String configuredAcpxExecutable() {
        var configured = System.getenv(ACPX_EXECUTABLE_ENV);
        return configured == null || configured.isBlank() ? "acpx" : configured.trim();
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
            validateExecutionPolicyBinding(agent, request);
            var identityFailure = reviewedBuildIdentityFailure(agent, request);
            if (identityFailure != null) {
                return identityFailure.toRunResult();
            }
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

    private SessionCommandResult reviewedBuildIdentityFailure(String agent, AcpxRunRequest request)
            throws InterruptedException {
        if (!"claude".equals(agent) || request.claudeExecutionPolicy() == null) {
            return null;
        }
        var command = List.of(acpxExecutable, "build-info", "--json");
        var result = observedSessionCommand(agent, request, command, "build_identity", false, false, sessionCommandTimeout);
        if (!result.success()) {
            return result.withOutput("NETWORK_AUTHORITY_NOT_CONFIGURED: patched acpx build-info is unavailable"
                    + (result.output().isBlank() ? "" : " - " + result.output()));
        }
        var verification = ReviewedAcpxBuildIdentity.verify(result.output());
        return verification.valid() ? null : result.asFailure(verification.message());
    }

    /** Sends a bounded contract-correction turn to the already established session. */
    AcpxRunResult runAgentResultCorrection(String agent, AcpxRunRequest request)
            throws IOException, InterruptedException {
        if (!"claude".equals(agent) && !"codex".equals(agent)) {
            throw new IllegalArgumentException("unsupported agent: " + agent);
        }
        validateExecutionPolicyBinding(agent, request);
        var identityFailure = reviewedBuildIdentityFailure(agent, request);
        if (identityFailure != null) {
            return identityFailure.toRunResult();
        }
        return run(agent, request);
    }

    private AcpxRunResult run(String agent, AcpxRunRequest request) throws IOException, InterruptedException {
        var compatibilityFailure = sessionCompatibilityFailure(agent, request);
        if (compatibilityFailure != null) return compatibilityFailure;
        var command = new ArrayList<String>();
        command.add(acpxExecutable);
        if (!commandTimeout.isZero()) { command.add("--timeout"); command.add(Long.toString(commandTimeout.toSeconds())); }
        command.add("--ttl"); command.add(Long.toString(queueTtl.toSeconds()));
        addPermissionArguments(command, request); addExecutionPolicyArguments(command, agent, request);
        command.addAll(List.of(agent, "-s", request.sessionName(), "-f", request.promptPath().toString()));
        var intent = commandIntent(agent, request, command, "prompt", false);
        var result = runPrompt(agent, request, command);
        commandResult(request, intent, Map.of("exit_code", result.exitCode(), "process", result.processResult(),
                "prompt_completion", result.promptCompletion()));
        return result;
    }

    /** Fix configured inputs, without interpreting npm/config or pinning native versions. */
    Map<String, Object> invocationInputs(String agent, Path workdir, String session) throws IOException {
        var environment = new java.util.TreeMap<String, String>();
        for (var key : List.of("PATH", agent.equals("claude") ? "CLAUDE_CODE_EXECUTABLE" : "CODEX_PATH"))
            environment.put(key, System.getenv(key) == null ? "" : System.getenv(key));
        return Map.of("schema", "dual.acpx.invocation.v1", "executable", acpxExecutable,
                "agent", agent, "cwd", workdir.toRealPath().toString(), "session", session,
                "launch_environment", environment);
    }

    private List<String> requestOptions(String agent, AcpxRunRequest request) throws IOException {
        var args = new ArrayList<String>();
        addPermissionArguments(args, request); addExecutionPolicyArguments(args, agent, request);
        return List.copyOf(args);
    }

    List<String> requestOptions(String agent, Path workdir, AcpxPermissionProfile profile, ClaudeExecutionPolicy.Binding policy) throws IOException {
        return requestOptions(agent, new AcpxRunRequest(workdir, "unused", Path.of("unused"),
                Path.of("unused"), Path.of("unused"), profile, null, policy));
    }

    private AcpxRunResult sessionCompatibilityFailure(String agent, AcpxRunRequest request) throws IOException, InterruptedException {
        var command = List.of(acpxExecutable, "--format", "json", agent, "sessions", "show", request.sessionName());
        var intent = commandIntent(agent, request, command, "session_compatibility", false);
        var result = runSessionCommand(command, request.workdir(), false, sessionCommandTimeout);
        if (!result.success()) {
            var summary = Map.<String, Object>of("available", false, "source", "acpx.sessions.show");
            commandResult(request, intent, Map.of("success", false, "timed_out", result.timedOut(),
                    "exit_code", result.exitCode(), "session_metadata", summary));
            var failure = result.withOutput("session metadata command failed; inspect the recorded command status");
            appendSessionCommandFailure(request.stderrPath(), "compatibility", agent, request.sessionName(), failure);
            return failure.toRunResult();
        }
        var assessment = AcpxSessionCompatibility.assess(result.output(), request.workdir(), request.sessionName());
        commandResult(request, intent, Map.of("success", true, "timed_out", false, "exit_code", 0,
                "session_metadata", assessment.observation()));
        if (assessment.compatible()) return null;
        var failure = result.asFailure(OBJECT_MAPPER.writeValueAsString(assessment.observation()));
        appendSessionCommandFailure(request.stderrPath(), "compatibility", agent, request.sessionName(), failure);
        return failure.toRunResult(AdapterSignalType.ADAPTER_INCOMPATIBLE);
    }

    private static String contentHash(byte[] bytes) {
        try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private Map<String, Object> commandIntent(String agent, AcpxRunRequest request, List<String> command, String purpose, boolean cleanup) throws IOException {
        var details = new java.util.LinkedHashMap<String, Object>();
        details.put("command_id", java.util.UUID.randomUUID().toString());
        details.put("purpose", purpose); details.put("cleanup", cleanup);
        details.put("command", List.copyOf(command)); details.put("session", request.sessionName());
        details.put("invocation", invocationInputs(agent, request.workdir(), request.sessionName()));
        // Cancelling the original session grants no execution authority. Invalidated policy must not prevent cleanup.
        details.put("request_options", cleanup ? List.of() : requestOptions(agent, request));
        if (purpose.equals("prompt")) {
            details.put("prompt", request.promptPath().toString());
            details.put("prompt_sha256", contentHash(Files.readAllBytes(request.promptPath())));
        }
        recordEvent(new ArrayList<>(), request, AcpxExecutionEvent.now(AgentExecutionTimelineEventKind.COMMAND_INTENT,
                "adapter", 0, Instant.now(), details));
        return details;
    }

    private void commandResult(AcpxRunRequest request, Map<String, Object> intent, Map<String, Object> observation) {
        var details = new java.util.LinkedHashMap<String, Object>(intent); details.put("observation", observation);
        recordEvent(new ArrayList<>(), request, AcpxExecutionEvent.now(AgentExecutionTimelineEventKind.COMMAND_RESULT,
                "adapter", 0, Instant.now(), details));
    }

    private SessionCommandResult observedSessionCommand(String agent, AcpxRunRequest request, List<String> command, String purpose,
                                                        boolean cleanup, boolean simple, Duration timeout) throws InterruptedException {
        final Map<String, Object> intent;
        try { intent = commandIntent(agent, request, command, purpose, cleanup); }
        catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
        var result = runSessionCommand(command, request.workdir(), simple, timeout);
        commandResult(request, intent, Map.of("success", result.success(), "timed_out", result.timedOut(),
                "exit_code", result.exitCode(), "output", result.output()));
        return result;
    }

    private AcpxRunResult runPrompt(String agent, AcpxRunRequest request, List<String> command) throws IOException, InterruptedException {
        var managedProcessBaseline = managedToolPids();
        var sessionStreamBaseline = sessionStreamBaseline(request);
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
            var waitResult = waitForExitOrRecoverableSignal(agent, process, request, sessionStreamBaseline);
            timeline.addAll(waitResult.executionTimeline());
            if (requiresSessionClosure(waitResult)) {
                waitResult = withSessionClosure(agent, request, waitResult, timeline, startedAt);
            }
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
                    waitResult.signals(),
                    waitResult.toolObservations()
            );
        } catch (InterruptedException failure) {
            Thread.interrupted();
            try {
                var closure = quiesceSession(agent, request);
                return transportExceptionResult(agent, request, startedAt, process.pid(), failure, true, closure);
            } finally {
                Thread.currentThread().interrupt();
            }
        } catch (NativePermissionBridgeException failure) {
            return transportExceptionResult(agent, request, startedAt, process.pid(), failure, false, quiesceSession(agent, request));
        } catch (ExecutionEventPersistenceException failure) {
            return transportExceptionResult(
                    agent,
                    request,
                    startedAt,
                    process.pid(),
                    failure,
                    false,
                    quiesceSession(agent, request)
            );
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
        return transportExceptionResult(agent, request, startedAt, pid, failure, interrupted, null);
    }

    private AcpxRunResult transportExceptionResult(
            String agent,
            AcpxRunRequest request,
            Instant startedAt,
            Long pid,
            Exception failure,
            boolean interrupted,
            SessionClosureResult closure
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
        var signals = new ArrayList<AdapterSignal>();
        if (!interrupted) {
            signals.add(new AdapterSignal(
                        failure instanceof NativePermissionBridgeException ? AdapterSignalType.NATIVE_PERMISSION_INTERRUPTED : AdapterSignalType.PROCESS_STALLED,
                        message,
                        0,
                        "transport",
                        "",
                        false
                ));
        }
        if (closure != null && !closure.success()) {
            signals.add(sessionClosureFailureSignal(closure, signals.size(), request.sessionName()));
        }
        var timeline = closure == null
                ? List.<AcpxExecutionEvent>of()
                : List.of(sessionClosureEvent(closure, startedAt, 0));
        return new AcpxRunResult(
                startedAt,
                finishedAt,
                command,
                timeline,
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
                List.copyOf(signals)
        );
    }

    long permissionBudgetMillis(AcpxPermissionProfile profile) {
        return profile.denyAll() || profile.simpleClaudeCode() ? 0 : commandTimeout.toMillis();
    }
    private boolean onlinePermissions(AcpxRunRequest request) {
        return request.executionEventSink().permissionRepliesEnabled() && permissionBudgetMillis(request.permissionProfile()) > 0;
    }
    List<String> requestOptions(String agent, Path workdir, AcpxPermissionProfile profile, ClaudeExecutionPolicy.Binding policy, boolean bridge) throws IOException {
        var preview = new AcpxExecutionEventSink() {
            public void accept(AcpxExecutionEvent event) {}
            public boolean permissionRepliesEnabled() { return bridge; }
        };
        return requestOptions(agent, new AcpxRunRequest(workdir, "unused", Path.of("unused"), Path.of("unused"), Path.of("unused"), profile, preview, policy));
    }

    private void addPermissionArguments(ArrayList<String> command, AcpxRunRequest request) {
        if (onlinePermissions(request)) { command.add("--format"); command.add("json"); command.add("--permission-replies"); }
        if (!onlinePermissions(request) && request.permissionProfile().promptRetries() > 0) {
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
            if (!request.permissionProfile().autoApprovedTools().isEmpty()) {
                command.add("--allowed-tools");
                command.add(request.permissionProfile().autoApprovedToolsArgument());
            }
            if (!request.permissionProfile().terminalEnabled()) {
                command.add("--no-terminal");
            }
        }
    }

    private void addExecutionPolicyArguments(
            ArrayList<String> command,
            String agent,
            AcpxRunRequest request
    ) throws IOException {
        validateExecutionPolicyBinding(agent, request);
        var binding = request.claudeExecutionPolicy();
        if (binding == null) {
            return;
        }
        command.add("--claude-execution-policy-ref");
        command.add(binding.ref());
        command.add("--claude-execution-policy-sha256");
        command.add(binding.sha256());
    }

    private void validateExecutionPolicyBinding(String agent, AcpxRunRequest request) throws IOException {
        var binding = request.claudeExecutionPolicy();
        if (!"claude".equals(agent)) {
            if (binding != null) {
                throw new IOException("Claude execution policy cannot be used by agent " + agent);
            }
            return;
        }
        if (binding == null) {
            return;
        }
        if (request.approveAll()) {
            throw new IOException("NETWORK_AUTHORITY_NOT_CONFIGURED: approveAll cannot be combined with E1 policy");
        }
        binding.verify(request.workdir());
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
            case PERMISSION_APPROVED, PERMISSION_DENIED, PERMISSION_CANCELLED, PERMISSION_UNRESOLVED ->
                    AgentExecutionTimelineEventKind.PERMISSION_DECISION;
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
                event.kind() == AgentLifecycleEvent.Kind.PERMISSION_APPROVED
                        || event.kind() == AgentLifecycleEvent.Kind.PERMISSION_DENIED
                        || event.kind() == AgentLifecycleEvent.Kind.PERMISSION_CANCELLED
                        || event.kind() == AgentLifecycleEvent.Kind.PERMISSION_UNRESOLVED
                        ? permissionTimelineDetails(event, request)
                        : Map.of("lifecycleSequence", event.sequence())
        ));
    }

    private Map<String, Object> permissionTimelineDetails(AgentLifecycleEvent event, AcpxRunRequest request) {
        var evidence = event.permissionEvidence();
        if (evidence == null) {
            return Map.of(
                    "lifecycleSequence", event.sequence(),
                    "sessionName", request.sessionName(),
                    "disposition", "UNAVAILABLE"
            );
        }
        var details = new LinkedHashMap<String, Object>();
        details.put("lifecycleSequence", event.sequence());
        details.put("sessionName", request.sessionName());
        details.put("toolCallId", evidence.toolCallId());
        details.put("toolKind", evidence.toolKind());
        details.put("toolName", evidence.toolName());
        details.put("title", evidence.title());
        details.put("resource", evidence.resource());
        details.put("requestedOptions", evidence.requestedOptions());
        details.put("disposition", evidence.disposition());
        details.put("authorityDisposition", request.permissionProfile().escalates(evidence.toolKind())
                ? "ESCALATION_REQUIRED"
                : evidence.disposition());
        return Map.copyOf(details);
    }

    private SessionCommandResult ensureSession(String agent, AcpxRunRequest request)
            throws IOException, InterruptedException {
        var command = new ArrayList<String>();
        command.add(acpxExecutable);
        // Both `sessions new` and the idempotent `sessions ensure` carry the current
        // request's permission/profile args. acpx 0.11.0 applies these only when a
        // fresh session is created (createClient); an existing persistent record is
        // returned unchanged on reuse, so reusing a session never alters its policy.
        addPermissionArguments(command, request);
        addExecutionPolicyArguments(command, agent, request);
        command.add(agent);
        command.add("sessions");
        command.add(request.permissionProfile().requiresFreshSession() ? "new" : "ensure");
        command.add("--name");
        command.add(request.sessionName());
        var result = observedSessionCommand(agent, request, command, "session_ensure", false, request.permissionProfile().simpleClaudeCode(), sessionCommandTimeout);
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

    private SessionCommandResult rebuildSession(String agent, AcpxRunRequest request)
            throws IOException, InterruptedException {
        var closed = closeSession(agent, request);
        if (!closed.success()) return closed;
        return ensureSession(agent, request);
    }

    private SessionCommandResult closeSession(String agent, AcpxRunRequest request) throws InterruptedException {
        var command = List.of(acpxExecutable, agent, "sessions", "close", request.sessionName());
        var result = observedSessionCommand(agent, request, command, "session_close", true, false, sessionCommandTimeout);
        if (!result.success()) {
            appendSessionCommandFailure(
                    request.stderrPath(),
                    "close",
                    agent,
                    request.sessionName(),
                    result
            );
        }
        return result;
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
        return runSessionCommand(command, workdir, simpleClaudeCode, sessionCommandTimeout);
    }

    private SessionCommandResult runSessionCommand(
            List<String> command,
            Path workdir,
            boolean simpleClaudeCode,
            Duration timeout
    ) throws InterruptedException {
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
            var completed = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
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
            String agent,
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
        var permissionWait = new NativePermissionWait(agent, request, eventReader, timeline, startedAt);
        var health = new AgentExecutionHealth(
                new AcpxFailureSignalCatalog(),
                recoverableSignalGrace,
                agentProgressTimeout,
                request.permissionProfile().escalateKinds());
        while (true) {
            if (process.waitFor(STDERR_SIGNAL_POLL_SECONDS, TimeUnit.SECONDS)) {
                var elapsed = Duration.between(startedAt, Instant.now());
                for (var event : eventReader.readNewEvents()) {
                    health.observe(event, elapsed);
                    recordLifecycleBoundary(timeline, request, event, startedAt);
                }
                permissionWait.end();
                var assessment = health.assess(elapsed);
                var exitCode = process.exitValue();
                var signals = new ArrayList<>(assessment.signals());
                var terminalSignal = assessment.signal();
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
                        false,
                        eventReader.toolObservations()
                );
            }
            var elapsed = Duration.between(startedAt, Instant.now());
            for (var event : eventReader.readNewEvents()) {
                health.observe(event, elapsed);
                recordLifecycleBoundary(timeline, request, event, startedAt);
            }
            boolean waiting = permissionWait.tick();
            var assessment = health.assess(elapsed, waiting);
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
                        true,
                        eventReader.toolObservations()
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
        if (onlinePermissions(request)) return false; // An ask must never turn into prompt replay.
        if (result.exitCode() != 4) {
            return false;
        }
        return hasActiveSignal(result, AdapterSignalType.SESSION_RESUME_FAILED);
    }

    private boolean isReconnectRequired(AcpxRunRequest request, AcpxRunResult result) {
        if (onlinePermissions(request)) return false;
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

    private Map<String, Long> sessionStreamBaseline(AcpxRunRequest request) {
        var sessionsDir = Path.of(System.getProperty("user.home"), ".acpx", "sessions");
        var indexPath = sessionsDir.resolve("index.json");
        if (!Files.isRegularFile(indexPath)) {
            return null;
        }
        try {
            var entries = OBJECT_MAPPER.readTree(indexPath.toFile()).path("entries");
            String selectedId = "";
            String selectedLastUsed = "";
            var expectedWorkdir = request.workdir().toAbsolutePath().normalize().toString();
            for (var entry : entries) {
                if (!request.sessionName().equals(entry.path("name").asText())
                        || entry.path("closed").asBoolean(false)
                        || !expectedWorkdir.equals(Path.of(entry.path("cwd").asText(".")).toAbsolutePath().normalize().toString())) {
                    continue;
                }
                var candidateId = entry.path("acpxRecordId").asText();
                var lastUsed = entry.path("lastUsedAt").asText();
                if (!candidateId.isBlank() && (selectedId.isBlank() || lastUsed.compareTo(selectedLastUsed) > 0)) {
                    selectedId = candidateId;
                    selectedLastUsed = lastUsed;
                }
            }
            if (selectedId.isBlank()) {
                return null;
            }
            var streamPath = sessionsDir.resolve(selectedId + ".stream.ndjson");
            return Map.of(selectedId, Files.isRegularFile(streamPath) ? Files.size(streamPath) : 0L);
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    private boolean requiresSessionClosure(ProcessWaitResult result) {
        return result.terminatedByHealth()
                || !result.promptCompletion().completed();
    }

    private ProcessWaitResult withSessionClosure(
            String agent,
            AcpxRunRequest request,
            ProcessWaitResult result,
            List<AcpxExecutionEvent> timeline,
            Instant startedAt
    ) throws InterruptedException {
        var closure = quiesceSession(agent, request);
        recordEvent(timeline, request, sessionClosureEvent(closure, startedAt, timeline.size()));
        var signals = new ArrayList<>(result.signals());
        var terminalSignal = result.signal();
        if (!closure.success()) {
            terminalSignal = sessionClosureFailureSignal(closure, signals.size(), request.sessionName());
            signals.add(terminalSignal);
        }
        return new ProcessWaitResult(
                result.exitCode(),
                result.executionTimeline(),
                result.promptCompletion(),
                signals,
                terminalSignal,
                result.terminatedByHealth(),
                result.toolObservations()
        );
    }

    /** Exact-session recovery stop. The supplied sink must persist both command intents and observations. */
    public boolean stopRecordedSession(String agent, AcpxRunRequest request) throws InterruptedException {
        if (!java.util.Set.of("claude", "codex").contains(agent) || request.sessionName() == null || request.sessionName().isBlank())
            throw new IllegalArgumentException("recorded provider and session are required");
        return quiesceSession(agent, request).disposition() != SessionClosureDisposition.FAILED;
    }

    private SessionClosureResult quiesceSession(String agent, AcpxRunRequest request) throws InterruptedException {
        var cancel = observedSessionCommand(agent, request,
                List.of(acpxExecutable, agent, "cancel", "-s", request.sessionName()),
                "session_cancel", true, false, closureCommandTimeout());
        if (cancel.success()) {
            return new SessionClosureResult(SessionClosureDisposition.CANCELLED, cancel, null);
        }
        var close = observedSessionCommand(agent, request,
                List.of(acpxExecutable, agent, "sessions", "close", request.sessionName()),
                "session_close", true, false, closureCommandTimeout());
        return new SessionClosureResult(
                close.success() ? SessionClosureDisposition.CLOSED_AFTER_CANCEL : SessionClosureDisposition.FAILED,
                cancel,
                close
        );
    }

    private AcpxExecutionEvent sessionClosureEvent(SessionClosureResult closure, Instant startedAt, long sequence) {
        return AcpxExecutionEvent.now(
                AgentExecutionTimelineEventKind.SESSION_CLOSURE,
                "adapter",
                sequence,
                startedAt,
                Map.of(
                        "disposition", closure.disposition().name(),
                        "cancelExitCode", closure.cancel().exitCode(),
                        "cancelTimedOut", closure.cancel().timedOut(),
                        "closeExitCode", closure.close() == null ? 0 : closure.close().exitCode(),
                        "closeTimedOut", closure.close() != null && closure.close().timedOut()
                )
        );
    }

    private AdapterSignal sessionClosureFailureSignal(
            SessionClosureResult closure,
            long sequence,
            String sessionName
    ) {
        return new AdapterSignal(
                AdapterSignalType.PROCESS_STALLED,
                "acpx session did not reach a quiescent boundary after cancel and close",
                sequence,
                "session_closure",
                sessionName,
                false
        );
    }

    private Duration closureCommandTimeout() {
        return sessionCommandTimeout.compareTo(Duration.ofSeconds(5)) <= 0
                ? sessionCommandTimeout
                : Duration.ofSeconds(5);
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

    static Duration configuredCommandTimeout(String value) {
        if (value == null || value.isBlank()) {
            return Duration.ZERO;
        }
        try {
            var seconds = Long.parseLong(value.trim());
            if (seconds < 0) {
                throw new IllegalArgumentException(ACPX_COMMAND_TIMEOUT_ENV + " must be a non-negative integer");
            }
            return Duration.ofSeconds(seconds);
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException(ACPX_COMMAND_TIMEOUT_ENV + " must be a non-negative integer", failure);
        }
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
        SessionCommandResult withOutput(String replacement) {
            return new SessionCommandResult(success, timedOut, exitCode, replacement, command, startedAt, finishedAt);
        }

        SessionCommandResult asFailure(String failure) {
            return new SessionCommandResult(false, false, RECOVERABLE_SIGNAL_EXIT_CODE,
                    failure, command, startedAt, finishedAt);
        }

        AcpxRunResult toRunResult() {
            return toRunResult(AdapterSignalType.SESSION_BOOTSTRAP_FAILED);
        }

        AcpxRunResult toRunResult(AdapterSignalType type) {
            var signal = new AdapterSignal(
                    type,
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
            boolean terminatedByHealth,
            List<dev.dual.core.execution.AgentExecutionEvidence.ToolObservation> toolObservations
    ) {
        ProcessWaitResult {
            signals = signals == null ? List.of() : List.copyOf(signals);
        }
    }

    private enum SessionClosureDisposition {
        CANCELLED,
        CLOSED_AFTER_CANCEL,
        FAILED
    }

    private record SessionClosureResult(
            SessionClosureDisposition disposition,
            SessionCommandResult cancel,
            SessionCommandResult close
    ) {
        boolean success() {
            return disposition != SessionClosureDisposition.FAILED;
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

    static ProcessUsability exitedUsability(
            int exitCode,
            AcpxPromptCompletion completion,
            List<String> command,
            Instant startedAt,
            Instant finishedAt
    ) {
        if (completion != null && completion.completed()) {
            return exitCode == 0
                    ? ProcessUsability.EXITED_ZERO
                    : ProcessUsability.EXITED_NON_ZERO_WITH_COMPLETE_OUTPUT;
        }
        if (reachedDeclaredTimeout(command, startedAt, finishedAt, Duration.ofSeconds(5))) {
            return ProcessUsability.TIMED_OUT;
        }
        return exitCode == 0
                ? ProcessUsability.EXITED_ZERO
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

    private final class NativePermissionWait {
        private final String agent;
        private final AcpxRunRequest request;
        private final AgentLifecycleEventReader reader;
        private final List<AcpxExecutionEvent> timeline;
        private final Instant started;
        private final Map<String, Map<String, Object>> pending = new java.util.LinkedHashMap<>();
        // Eligibility exists only in this original live call; never reconstructed from durable C.
        private final Map<String, Map<String, Object>> readyReplies = new java.util.LinkedHashMap<>();
        private final Set<String> externalAttempts = new java.util.HashSet<>();
        private final Map<String, Map<String, Object>> awaitingRecord = new java.util.LinkedHashMap<>();
        private final Map<String, Map<String, Object>> seen = new java.util.LinkedHashMap<>();
        private NativePermissionWait(String agent, AcpxRunRequest request, AgentLifecycleEventReader reader, List<AcpxExecutionEvent> timeline, Instant started) {
            this.agent = agent; this.request = request; this.reader = reader; this.timeline = timeline; this.started = started;
        }
        boolean tick() {
            if (!onlinePermissions(request)) return false;
            String sendingKey = null;
            try {
                if (request.executionEventSink().permissionRepliesClosed()) throw new IOException("permission submissions closed; original stop must be reconciled");
                for (var nativeRequest : reader.readPermissionRequests()) {
                    var key = String.valueOf(nativeRequest.get("requestKey"));
                    var prior = seen.putIfAbsent(key, nativeRequest);
                    if (prior != null && !prior.equals(nativeRequest)) throw new IOException("native request identity changed");
                    if (prior == null) awaitingRecord.put(key, nativeRequest);
                }
                for (var entry : new ArrayList<>(awaitingRecord.entrySet())) {
                    var nativeRequest = entry.getValue();
                    request.executionEventSink().permissionRequest(nativeRequest); // R first; uncommitted BUSY retains this frame.
                    pending.put(entry.getKey(), nativeRequest);
                    awaitingRecord.remove(entry.getKey());
                    recordEvent(timeline, request, AcpxExecutionEvent.now(AgentExecutionTimelineEventKind.NATIVE_PERMISSION_REQUEST,
                            "adapter", timeline.size(), started, nativeRequest));
                }
                if (Duration.between(started, Instant.now()).compareTo(commandTimeout) >= 0) throw new IOException("finite native permission/command budget elapsed");
                for (var entry : new ArrayList<>(pending.entrySet())) {
                    long deadline = ((Number) entry.getValue().get("deadlineMs")).longValue();
                    if (System.currentTimeMillis() >= deadline) throw new IOException("native permission request expired");
                    var key = entry.getKey();
                    if (!readyReplies.containsKey(key)) {
                        var committed = request.executionEventSink().permissionReply(key);
                        if (committed.isEmpty()) continue;
                        readyReplies.put(key, committed.orElseThrow());
                    }
                    sendingKey = key;
                    send(key, readyReplies.get(key), deadline);
                    pending.remove(key); readyReplies.remove(key); sendingKey = null;
                }
                return !pending.isEmpty();
            } catch (dev.dual.ledger.file.WorkstreamLockedException busy) {
                if (sendingKey != null && externalAttempts.contains(sendingKey))
                    throw new NativePermissionBridgeException("permission IPC was attempted; busy result recording is UNKNOWN, never resent", busy);
                // Before IPC, retain only this live call's fresh C while a local lock is briefly busy.
                // This is not a new qualification, another C, an IPC retry, or a durable outbox.
                if (Duration.between(started, Instant.now()).compareTo(commandTimeout) >= 0)
                    throw new NativePermissionBridgeException("local permission lock remained busy until the finite budget expired", busy);
                return true;
            } catch (IOException | RuntimeException error) {
                throw new NativePermissionBridgeException("native permission continuation stopped; do not resend or overlap the original execution", error);
            }
        }
        private void send(String key, Map<String, Object> reply, long deadline) throws IOException {
            var json = new com.fasterxml.jackson.databind.ObjectMapper();
            var command = List.of(acpxExecutable, "--format", "json", agent, "permission-reply", "-s", request.sessionName(), "--reply", json.writeValueAsString(reply));
            request.executionEventSink().permissionObservation(key, "send-intent", Map.of("reply", reply, "command", command,
                    "invocation", invocationInputs(agent, request.workdir(), request.sessionName()), "automatic_retry", false));
            if (request.executionEventSink().permissionRepliesClosed()) throw new IOException("C committed but X observed before sending");
            long remaining = Math.min(deadline - System.currentTimeMillis(), sessionCommandTimeout.toMillis());
            if (remaining <= 0) throw new IOException("C committed but live request expired before sending");
            if (!externalAttempts.add(key)) throw new IOException("permission IPC was already attempted; no resend");
            SessionCommandResult result;
            try { result = runSessionCommand(command, request.workdir(), false, Duration.ofMillis(remaining)); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IOException("permission IPC interrupted; delivery unknown", interrupted); }
            var ack = json.createObjectNode();
            try {
                var parsed = json.readTree(result.output());
                if (parsed != null && parsed.isObject()) ack = (com.fasterxml.jackson.databind.node.ObjectNode) parsed;
            } catch (IOException ignored) { /* A missing valid receipt is UNKNOWN, never successful delivery. */ }
            boolean identity = "dual.native.permission.reply.v1".equals(ack.path("schema").asText())
                    && key.equals(ack.path("requestKey").asText()) && ack.path("ownerGeneration").asLong() == ((Number) reply.get("ownerGeneration")).longValue();
            boolean accepted = identity && result.success() && ack.path("consumed").isBoolean() && ack.path("consumed").asBoolean();
            var observation = new java.util.LinkedHashMap<String, Object>();
            observation.put("delivery", accepted ? "ACKNOWLEDGED" : identity && ack.path("consumed").isBoolean() && !ack.path("consumed").asBoolean() ? "REJECTED" : "UNKNOWN");
            observation.put("ack", ack); observation.put("exit_code", result.exitCode()); observation.put("timed_out", result.timedOut());
            observation.put("business_completed", false); observation.put("automatic_retry", false);
            request.executionEventSink().permissionObservation(key, "send-result", observation);
            recordEvent(timeline, request, AcpxExecutionEvent.now(AgentExecutionTimelineEventKind.NATIVE_PERMISSION_REPLY,
                    "adapter", timeline.size(), started, observation));
            if (!accepted) throw new IOException("permission reply was rejected or delivery is unknown; no resend");
        }
        void end() {
            if (!onlinePermissions(request)) return;
            try {
                for (var key : pending.keySet()) request.executionEventSink().permissionObservation(key, "send-result",
                        Map.of("delivery", "REQUEST_ENDED", "attempted", false, "business_completed", false));
                pending.clear();
            } catch (IOException error) { throw new NativePermissionBridgeException("request ended but its final observation could not be recorded", error); }
        }
    }

    private static final class NativePermissionBridgeException extends RuntimeException {
        private NativePermissionBridgeException(String message, Throwable cause) { super(message, cause); }
    }

    private static final class ExecutionEventPersistenceException extends RuntimeException {
        private ExecutionEventPersistenceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
