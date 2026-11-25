package app.cryptoincome.funding.domain;

public class FundingRateSyncInProgressException extends RuntimeException {
    private final long runId;

    public FundingRateSyncInProgressException(long runId) {
        super("相同逻辑请求正在执行，runId=" + runId);
        this.runId = runId;
    }

    public long runId() {
        return runId;
    }
}
