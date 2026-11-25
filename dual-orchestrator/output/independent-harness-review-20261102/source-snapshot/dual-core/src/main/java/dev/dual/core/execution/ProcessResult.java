package dev.dual.core.execution;

/**
 * Raw process-level facts for one agent execution.
 *
 * <p>The process result does not decide workflow state. It only records what
 * happened at the adapter boundary so {@link AgentExecutionOutcomeResolver}
 * can combine it with the agent result and artifact contract results.</p>
 */
public record ProcessResult(
        String command,
        Long pid,
        Integer exitCode,
        ProcessUsability usability,
        long durationMs,
        String stdoutPath,
        String stderrPath,
        boolean interrupted,
        boolean timedOut
) {
    public ProcessResult {
        if (usability == null) {
            throw new IllegalArgumentException("usability is required");
        }
        if (durationMs < 0) {
            throw new IllegalArgumentException("durationMs must be non-negative");
        }
        command = blankToEmpty(command);
        stdoutPath = blankToEmpty(stdoutPath);
        stderrPath = blankToEmpty(stderrPath);
    }

    public static ProcessResult exitedZero() {
        return new ProcessResult("", null, 0, ProcessUsability.EXITED_ZERO, 0, "", "", false, false);
    }

    public static ProcessResult nonZeroComplete(int exitCode) {
        return new ProcessResult(
                "", null, exitCode, ProcessUsability.EXITED_NON_ZERO_WITH_COMPLETE_OUTPUT, 0, "", "", false, false
        );
    }

    public static ProcessResult nonZeroIncomplete(int exitCode) {
        return new ProcessResult(
                "", null, exitCode, ProcessUsability.EXITED_NON_ZERO_WITH_INCOMPLETE_OUTPUT, 0, "", "", false, false
        );
    }

    public static ProcessResult timedOutProcess() {
        return new ProcessResult("", null, null, ProcessUsability.TIMED_OUT, 0, "", "", false, true);
    }

    public static ProcessResult interruptedProcess() {
        return new ProcessResult("", null, null, ProcessUsability.INTERRUPTED, 0, "", "", true, false);
    }

    public static ProcessResult killedAfterRecoverableSignal() {
        return new ProcessResult(
                "", null, null, ProcessUsability.KILLED_AFTER_RECOVERABLE_SIGNAL, 0, "", "", false, false
        );
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value;
    }
}
