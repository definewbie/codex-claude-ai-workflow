package app.cryptoincome.grid.sync.infrastructure;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;

@TableName("candle_sync_execution")
public class CandleSyncExecutionEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private String state;
    private String instrumentId;
    private String bar;
    private Instant windowFrom;
    private Instant windowTo;
    private String coverage;
    private Integer pages;
    private Integer received;
    private Integer completedInWindow;
    private Integer newFacts;
    private Integer duplicateFacts;
    private Instant cursorTs;
    private String failureCategory;
    private String failureMessage;
    private Instant createdAt;
    private Instant startedAt;
    private Instant completedAt;
    private Instant updatedAt;

    public Long getId() { return id; } public void setId(Long value) { id = value; }
    public String getState() { return state; } public void setState(String value) { state = value; }
    public String getInstrumentId() { return instrumentId; } public void setInstrumentId(String value) { instrumentId = value; }
    public String getBar() { return bar; } public void setBar(String value) { bar = value; }
    public Instant getWindowFrom() { return windowFrom; } public void setWindowFrom(Instant value) { windowFrom = value; }
    public Instant getWindowTo() { return windowTo; } public void setWindowTo(Instant value) { windowTo = value; }
    public String getCoverage() { return coverage; } public void setCoverage(String value) { coverage = value; }
    public Integer getPages() { return pages; } public void setPages(Integer value) { pages = value; }
    public Integer getReceived() { return received; } public void setReceived(Integer value) { received = value; }
    public Integer getCompletedInWindow() { return completedInWindow; } public void setCompletedInWindow(Integer value) { completedInWindow = value; }
    public Integer getNewFacts() { return newFacts; } public void setNewFacts(Integer value) { newFacts = value; }
    public Integer getDuplicateFacts() { return duplicateFacts; } public void setDuplicateFacts(Integer value) { duplicateFacts = value; }
    public Instant getCursorTs() { return cursorTs; } public void setCursorTs(Instant value) { cursorTs = value; }
    public String getFailureCategory() { return failureCategory; } public void setFailureCategory(String value) { failureCategory = value; }
    public String getFailureMessage() { return failureMessage; } public void setFailureMessage(String value) { failureMessage = value; }
    public Instant getCreatedAt() { return createdAt; } public void setCreatedAt(Instant value) { createdAt = value; }
    public Instant getStartedAt() { return startedAt; } public void setStartedAt(Instant value) { startedAt = value; }
    public Instant getCompletedAt() { return completedAt; } public void setCompletedAt(Instant value) { completedAt = value; }
    public Instant getUpdatedAt() { return updatedAt; } public void setUpdatedAt(Instant value) { updatedAt = value; }
}
