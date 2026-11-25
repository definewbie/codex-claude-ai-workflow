package app.cryptoincome.funding.infrastructure.mybatis;

import java.time.Instant;

public class FundingSyncRequestEntity {
    private Long id;
    private String requestKey;
    private String instrumentId;
    private Integer pageLimit;
    private String state;
    private Long currentRunId;
    private Long successfulRunId;
    private Integer nextAttempt;
    private Instant createdAt;
    private Instant updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRequestKey() { return requestKey; }
    public void setRequestKey(String requestKey) { this.requestKey = requestKey; }
    public String getInstrumentId() { return instrumentId; }
    public void setInstrumentId(String instrumentId) { this.instrumentId = instrumentId; }
    public Integer getPageLimit() { return pageLimit; }
    public void setPageLimit(Integer pageLimit) { this.pageLimit = pageLimit; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public Long getCurrentRunId() { return currentRunId; }
    public void setCurrentRunId(Long currentRunId) { this.currentRunId = currentRunId; }
    public Long getSuccessfulRunId() { return successfulRunId; }
    public void setSuccessfulRunId(Long successfulRunId) { this.successfulRunId = successfulRunId; }
    public Integer getNextAttempt() { return nextAttempt; }
    public void setNextAttempt(Integer nextAttempt) { this.nextAttempt = nextAttempt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
