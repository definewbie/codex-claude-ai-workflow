package app.cryptoincome.funding.infrastructure.mybatis;

import java.time.Instant;

public class FundingBackfillRequestEntity {
    private Long id;
    private String requestKey;
    private String instrumentId;
    private Instant windowStart;
    private Instant windowEnd;
    private Integer pageLimit;
    private String state;
    private String coverageState;
    private Instant cursorTs;
    private Integer pagesFetched;
    private Instant earliestFactTime;
    private Instant latestFactTime;
    private Integer nextAttempt;
    private Long currentRunId;
    private Instant createdAt;
    private Instant updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRequestKey() { return requestKey; }
    public void setRequestKey(String requestKey) { this.requestKey = requestKey; }
    public String getInstrumentId() { return instrumentId; }
    public void setInstrumentId(String instrumentId) { this.instrumentId = instrumentId; }
    public Instant getWindowStart() { return windowStart; }
    public void setWindowStart(Instant windowStart) { this.windowStart = windowStart; }
    public Instant getWindowEnd() { return windowEnd; }
    public void setWindowEnd(Instant windowEnd) { this.windowEnd = windowEnd; }
    public Integer getPageLimit() { return pageLimit; }
    public void setPageLimit(Integer pageLimit) { this.pageLimit = pageLimit; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getCoverageState() { return coverageState; }
    public void setCoverageState(String coverageState) { this.coverageState = coverageState; }
    public Instant getCursorTs() { return cursorTs; }
    public void setCursorTs(Instant cursorTs) { this.cursorTs = cursorTs; }
    public Integer getPagesFetched() { return pagesFetched; }
    public void setPagesFetched(Integer pagesFetched) { this.pagesFetched = pagesFetched; }
    public Instant getEarliestFactTime() { return earliestFactTime; }
    public void setEarliestFactTime(Instant earliestFactTime) { this.earliestFactTime = earliestFactTime; }
    public Instant getLatestFactTime() { return latestFactTime; }
    public void setLatestFactTime(Instant latestFactTime) { this.latestFactTime = latestFactTime; }
    public Integer getNextAttempt() { return nextAttempt; }
    public void setNextAttempt(Integer nextAttempt) { this.nextAttempt = nextAttempt; }
    public Long getCurrentRunId() { return currentRunId; }
    public void setCurrentRunId(Long currentRunId) { this.currentRunId = currentRunId; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
