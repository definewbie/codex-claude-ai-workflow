package app.cryptoincome.funding.infrastructure.mybatis;

import java.time.Instant;

public class StaleExecutionRow {
    private String requestKey;
    private Long requestId;
    private Long runId;
    private String instrumentId;
    private Integer pageLimit;
    private Instant startedAt;

    public String getRequestKey() { return requestKey; }
    public void setRequestKey(String requestKey) { this.requestKey = requestKey; }
    public Long getRequestId() { return requestId; }
    public void setRequestId(Long requestId) { this.requestId = requestId; }
    public Long getRunId() { return runId; }
    public void setRunId(Long runId) { this.runId = runId; }
    public String getInstrumentId() { return instrumentId; }
    public void setInstrumentId(String instrumentId) { this.instrumentId = instrumentId; }
    public Integer getPageLimit() { return pageLimit; }
    public void setPageLimit(Integer pageLimit) { this.pageLimit = pageLimit; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
}
