package app.cryptoincome.run.domain;

public final class IllegalRunTransitionException extends IllegalStateException {

    public IllegalRunTransitionException(long runId, String targetStatus) {
        super("analysis_run %d 无法转换为 %s".formatted(runId, targetStatus));
    }
}
