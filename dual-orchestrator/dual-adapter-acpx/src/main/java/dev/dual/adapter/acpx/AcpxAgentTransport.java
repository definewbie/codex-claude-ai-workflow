package dev.dual.adapter.acpx;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dual.core.execution.AgentExecutionTimelineEventKind;

import java.io.IOException;
import java.math.BigDecimal;
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
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int RECOVERABLE_SIGNAL_EXIT_CODE = 4;
    private static final long STDERR_SIGNAL_POLL_SECONDS = 2;
    private static final long DEFAULT_RECOVERABLE_SIGNAL_GRACE_SECONDS = 30;
    private static final long DEFAULT_ACPX_COMMAND_TIMEOUT_SECONDS = 600;
    private static final long DEFAULT_AGENT_PROGRESS_TIMEOUT_SECONDS = DEFAULT_ACPX_COMMAND_TIMEOUT_SECONDS;
    private static final long DEFAULT_QUEUE_TTL_SECONDS = 1800;
    private static final long TERMINAL_OUTPUT_GRACE_SECONDS = 2;
    private static final long DEFAULT_SESSION_COMMAND_TIMEOUT_SECONDS = 45;
    private static final String RECOVERABLE_SIGNAL_GRACE_ENV = "DUAL_ACPX_RECOVERABLE_SIGNAL_GRACE_SECONDS";
    private static final String AGENT_PROGRESS_TIMEOUT_ENV = "DUAL_ACPX_AGENT_PROGRESS_TIMEOUT_SECONDS";
    private static final String QUEUE_TTL_ENV = "DUAL_ACPX_QUEUE_TTL_SECONDS";
    private static final String SESSION_COMMAND_TIMEOUT_ENV = "DUAL_ACPX_SESSION_COMMAND_TIMEOUT_SECONDS";
    private static final String API_TIMEOUT_MS_ENV = "API_TIMEOUT_MS";

    private final AcpxFailureClassifier failureClassifier = new AcpxFailureClassifier();
    private final AgentTerminalContract terminalContract = new AgentTerminalContract();
    private final String acpxExecutable;
    private final String claudeExecutable;
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
        this.claudeExecutable = claudeExecutable;
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
        if (request.permissionProfile().requiresNativeClaudeRunner()) {
            return runNativeClaudeOneShot(request);
        }
        if (request.permissionProfile().requiresFreshSession()) {
            var bootstrap = ensureSession("claude", request);
            if (!bootstrap.success()) {
                return bootstrap.toRunResult();
            }
        }
        var result = run("claude", request);
        if (isMissingSession(request, result)) {
            var bootstrap = ensureSession("claude", request);
            if (bootstrap.success()) {
                result = run("claude", request);
            } else {
                return bootstrap.toRunResult();
            }
        } else if (isReconnectRequired(request, result)) {
            var bootstrap = rebuildSession("claude", request);
            if (bootstrap.success()) {
                result = run("claude", request);
            } else {
                return bootstrap.toRunResult();
            }
        }
        return result;
    }

    private AcpxRunResult runNativeClaudeOneShot(AcpxRunRequest request) throws IOException, InterruptedException {
        var command = new ArrayList<String>();
        command.add(claudeExecutable);
        command.add("--print");
        command.add("--input-format");
        command.add("text");
        command.add("--output-format");
        command.add("json");
        command.add("--bare");
        command.add("--safe-mode");
        command.add("--no-session-persistence");
        command.add("--permission-mode");
        command.add("dontAsk");
        command.add("--tools");
        command.add(request.permissionProfile().allowedToolsArgument());
        command.add("--allowedTools");
        command.add(request.permissionProfile().allowedToolsArgument());
        command.add("--disallowedTools");
        command.add("Bash,Read,Edit,Write,NotebookEdit,WebFetch,WebSearch");

        var managedProcessBaseline = managedToolPids();
        var startedAt = Instant.now();
        var rawJsonPath = nativeClaudeResultPath(request.stdoutPath());
        var processBuilder = new ProcessBuilder(command)
                .directory(request.workdir().toFile())
                .redirectInput(request.promptPath().toFile())
                .redirectOutput(rawJsonPath.toFile())
                .redirectError(request.stderrPath().toFile());
        processBuilder.environment().put("CLAUDE_CODE_SIMPLE", "1");
        processBuilder.environment().put("CLAUDE_CODE_SAFE_MODE", "1");
        var process = processBuilder.start();
        try {
            var timeline = new ArrayList<AcpxExecutionEvent>();
            recordEvent(timeline, request, AcpxExecutionEvent.now(
                    AgentExecutionTimelineEventKind.PROCESS_STARTED,
                    "adapter",
                    0,
                    startedAt
            ));
            var waitResult = waitForExitOrRecoverableSignal(process, request);
            timeline.addAll(waitResult.executionTimeline());
            recordEvent(timeline, request, AcpxExecutionEvent.now(
                    AgentExecutionTimelineEventKind.PROCESS_EXITED,
                    "adapter",
                    timeline.size(),
                    startedAt,
                    Map.of("exitCode", waitResult.exitCode())
            ));
            var nativeResult = readNativeClaudeResult(rawJsonPath, request.stdoutPath());
            return new AcpxRunResult(
                    waitResult.exitCode(),
                    startedAt,
                    Instant.now(),
                    List.copyOf(command),
                    timeline,
                    nativeResult.usage(),
                    nativeResult.toTerminal()
            );
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
            cleanupManagedToolsStartedByRun(managedProcessBaseline);
        }
    }

    private static Path nativeClaudeResultPath(Path transcriptPath) {
        var fileName = transcriptPath.getFileName().toString();
        return transcriptPath.resolveSibling(fileName + ".native-result.json");
    }

    private NativeClaudeResult readNativeClaudeResult(Path rawJsonPath, Path transcriptPath) throws IOException {
        if (!Files.exists(rawJsonPath)) {
            return NativeClaudeResult.missing();
        }
        var raw = Files.readString(rawJsonPath, StandardCharsets.UTF_8);
        if (raw.isBlank()) {
            Files.writeString(transcriptPath, "", StandardCharsets.UTF_8);
            return NativeClaudeResult.missing();
        }
        try {
            var root = OBJECT_MAPPER.readTree(raw);
            var resultText = text(root.get("result"));
            Files.writeString(
                    transcriptPath,
                    resultText.isBlank() ? raw : resultText,
                    StandardCharsets.UTF_8
            );
            return new NativeClaudeResult(
                    nativeClaudeUsage(root),
                    true,
                    root.path("is_error").asBoolean(false),
                    text(root.get("stop_reason")),
                    text(root.get("subtype"))
            );
        } catch (IOException parseFailure) {
            Files.writeString(transcriptPath, raw, StandardCharsets.UTF_8);
            return NativeClaudeResult.missing();
        }
    }

    private AcpxUsage nativeClaudeUsage(JsonNode root) {
        if (root != null && root.path("is_error").asBoolean(false)) {
            return null;
        }
        var usage = root == null ? null : root.get("usage");
        if (usage == null || !usage.isObject()) {
            return null;
        }
        var inputTokens = number(usage.get("input_tokens"));
        var outputTokens = number(usage.get("output_tokens"));
        var cachedReadTokens = number(usage.get("cache_read_input_tokens"));
        var cachedWriteTokens = number(usage.get("cache_creation_input_tokens"));
        var totalTokens = inputTokens + outputTokens + cachedReadTokens + cachedWriteTokens;
        var metadata = AcpxProviderMetadata.runtimeDefault();
        var rawCostAmount = decimal(root.get("total_cost_usd"));
        var costAmount = AcpxProviderMetadata.BILLING_SUBSCRIPTION_TOKENS.equals(metadata.billingMode())
                ? null
                : rawCostAmount;
        return new AcpxUsage(
                inputTokens,
                outputTokens,
                cachedReadTokens,
                cachedWriteTokens,
                totalTokens,
                costAmount,
                costAmount == null ? "" : "USD",
                "claude_json_result",
                AcpxUsage.PROVIDER_RUN_USAGE,
                AcpxUsage.ATTR_CLAUDE_JSON_RESULT,
                false,
                0,
                costAmount,
                costAmount == null ? "" : "USD",
                "",
                "",
                "",
                "",
                ""
        ).withProviderMetadata(metadata);
    }

    private record NativeClaudeResult(
            AcpxUsage usage,
            boolean parseable,
            boolean error,
            String stopReason,
            String subtype
    ) {
        static NativeClaudeResult missing() {
            return new NativeClaudeResult(null, false, false, null, null);
        }

        NativeClaudeTerminal toTerminal() {
            return new NativeClaudeTerminal(parseable, error, stopReason, subtype);
        }
    }

    private static String text(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? "" : node.asText();
    }

    private static long number(JsonNode node) {
        return node == null || !node.isNumber() ? 0 : node.asLong();
    }

    private static BigDecimal decimal(JsonNode node) {
        return node == null || !node.isNumber() ? null : node.decimalValue();
    }

    public AcpxRunResult runCodexDesignReview(AcpxRunRequest request) throws IOException, InterruptedException {
        var result = run("codex", request);
        if (isMissingSession(request, result)) {
            var bootstrap = ensureSession("codex", request);
            if (bootstrap.success()) {
                result = run("codex", request);
            } else {
                return bootstrap.toRunResult();
            }
        } else if (isReconnectRequired(request, result)) {
            var bootstrap = rebuildSession("codex", request);
            if (bootstrap.success()) {
                result = run("codex", request);
            } else {
                return bootstrap.toRunResult();
            }
        }
        return result;
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
        var startedAt = Instant.now();
        var processBuilder = new ProcessBuilder(command)
                .directory(request.workdir().toFile())
                .redirectOutput(request.stdoutPath().toFile())
                .redirectError(request.stderrPath().toFile());
        if (request.permissionProfile().simpleClaudeCode()) {
            processBuilder.environment().put("CLAUDE_CODE_SIMPLE", "1");
        }
        var process = processBuilder.start();
        try {
            var timeline = new ArrayList<AcpxExecutionEvent>();
            recordEvent(timeline, request, AcpxExecutionEvent.now(
                    AgentExecutionTimelineEventKind.PROCESS_STARTED,
                    "adapter",
                    0,
                    startedAt
            ));
            var waitResult = waitForExitOrRecoverableSignal(process, request);
            timeline.addAll(waitResult.executionTimeline());
            recordEvent(timeline, request, AcpxExecutionEvent.now(
                    AgentExecutionTimelineEventKind.PROCESS_EXITED,
                    "adapter",
                    timeline.size(),
                    startedAt,
                    Map.of("exitCode", waitResult.exitCode())
            ));
            return new AcpxRunResult(waitResult.exitCode(), startedAt, Instant.now(), List.copyOf(command), timeline);
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
            cleanupManagedToolsStartedByRun(managedProcessBaseline);
        }
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
        } catch (RuntimeException ignored) {
            // Timeline persistence is a read-model aid. Adapter execution facts still
            // return in the final AcpxRunResult and must not be corrupted by a sink failure.
        }
    }

    private SessionCommandResult ensureSession(String agent, AcpxRunRequest request) throws InterruptedException {
        var command = new ArrayList<String>();
        command.add(acpxExecutable);
        if (request.permissionProfile().requiresFreshSession()) {
            addPermissionArguments(command, request);
        }
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

    private ProcessWaitResult waitForExitOrRecoverableSignal(Process process, AcpxRunRequest request) throws InterruptedException {
        Instant terminalOutputSince = null;
        var startedAt = Instant.now();
        var timeline = new ArrayList<AcpxExecutionEvent>();
        var eventReader = new AgentLifecycleEventReader(request.stdoutPath(), request.stderrPath());
        var health = new AgentExecutionHealth(new AcpxFailureSignalCatalog(), recoverableSignalGrace, agentProgressTimeout);
        while (true) {
            if (process.waitFor(STDERR_SIGNAL_POLL_SECONDS, TimeUnit.SECONDS)) {
                recordEvent(timeline, request, AcpxExecutionEvent.now(
                        AgentExecutionTimelineEventKind.PROCESS_WAIT_COMPLETED,
                        "adapter",
                        eventReader.nextSequence(),
                        startedAt,
                        Map.of("exitCode", process.exitValue(), "waitMode", "process_exit")
                ));
                return new ProcessWaitResult(process.exitValue(), List.copyOf(timeline));
            }
            var elapsed = Duration.between(startedAt, Instant.now());
            for (var event : eventReader.readNewEvents()) {
                health.observe(event, elapsed);
            }
            if (terminalContract.isComplete(readFile(request.stdoutPath()))) {
                if (terminalOutputSince == null) {
                    terminalOutputSince = Instant.now();
                    recordEvent(timeline, request, AcpxExecutionEvent.now(
                            AgentExecutionTimelineEventKind.TERMINAL_CONTROL_DETECTED,
                            "stdout",
                            eventReader.nextSequence(),
                            startedAt,
                            Map.of("graceMs", Duration.ofSeconds(TERMINAL_OUTPUT_GRACE_SECONDS).toMillis())
                    ));
                    continue;
                }
                if (Duration.between(terminalOutputSince, Instant.now())
                        .compareTo(Duration.ofSeconds(TERMINAL_OUTPUT_GRACE_SECONDS)) >= 0) {
                    health.observe(AgentLifecycleEvent.terminalContractSeen(eventReader.nextSequence()), elapsed);
                    recordEvent(timeline, request, AcpxExecutionEvent.now(
                            AgentExecutionTimelineEventKind.TERMINAL_CONTROL_GRACE_ELAPSED,
                            "adapter",
                            eventReader.nextSequence(),
                            startedAt,
                            Map.of(
                                    "exitCode", 0,
                                    "waitMode", "terminal_control_grace",
                                    "action", "terminate_process_tree",
                                    "graceMs", Duration.ofSeconds(TERMINAL_OUTPUT_GRACE_SECONDS).toMillis()
                            )
                    ));
                    terminateProcessTree(process.toHandle());
                    return new ProcessWaitResult(0, List.copyOf(timeline));
                }
            } else {
                terminalOutputSince = null;
            }
            var assessment = health.assess(elapsed);
            if (assessment.status() == AgentExecutionHealth.Status.RECOVERABLE_FAILURE) {
                appendAdapterDiagnostic(request.stderrPath(), assessment.failure(), startedAt, assessment.failure().interruptRunningProcess());
                recordEvent(timeline, request, AcpxExecutionEvent.now(
                        AgentExecutionTimelineEventKind.RECOVERABLE_SIGNAL_DETECTED,
                        "adapter",
                        eventReader.nextSequence(),
                        startedAt,
                        Map.of(
                                "exitCode", RECOVERABLE_SIGNAL_EXIT_CODE,
                                "waitMode", "recoverable_signal",
                                "action", "terminate_process_tree",
                                "failureReason", assessment.failure().reason()
                        )
                ));
                terminateProcessTree(process.toHandle());
                return new ProcessWaitResult(RECOVERABLE_SIGNAL_EXIT_CODE, List.copyOf(timeline));
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
            return new AcpxRunResult(RECOVERABLE_SIGNAL_EXIT_CODE, startedAt, finishedAt, command);
        }
    }

    private record ProcessWaitResult(int exitCode, List<AcpxExecutionEvent> executionTimeline) {
    }
}
