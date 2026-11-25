package app.cryptoincome.shadow.infrastructure.mybatis;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.Instant;

@TableName("grid_shadow_study")
public class GridShadowStudyEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private String state;
    private Long configurationSnapshotId;
    private Instant startAt;
    private Integer targetDays;
    private BigDecimal capital;
    private BigDecimal simpleEarnApr;
    private BigDecimal makerFeeRate;
    private BigDecimal takerFeeRate;
    private BigDecimal exitSlippageBps;
    private BigDecimal maximumDrawdownRate;
    private BigDecimal maximumPathDivergenceRate;
    private BigDecimal maximumEndingBtcValueRatio;
    private BigDecimal candidateLowerPrice;
    private BigDecimal candidateUpperPrice;
    private Integer candidateGridCount;
    private BigDecimal candidateTakeProfitPrice;
    private BigDecimal candidateStopLossPrice;
    private BigDecimal baselineLowerPrice;
    private BigDecimal baselineUpperPrice;
    private Integer baselineGridCount;
    private BigDecimal entryReferencePrice;
    private Instant evidenceAsOf;
    private String evidenceReference;
    private Instant createdAt;
    private Instant stoppedAt;

    public Long getId() { return id; } public void setId(Long v) { id = v; }
    public String getState() { return state; } public void setState(String v) { state = v; }
    public Long getConfigurationSnapshotId() { return configurationSnapshotId; } public void setConfigurationSnapshotId(Long v) { configurationSnapshotId = v; }
    public Instant getStartAt() { return startAt; } public void setStartAt(Instant v) { startAt = v; }
    public Integer getTargetDays() { return targetDays; } public void setTargetDays(Integer v) { targetDays = v; }
    public BigDecimal getCapital() { return capital; } public void setCapital(BigDecimal v) { capital = v; }
    public BigDecimal getSimpleEarnApr() { return simpleEarnApr; } public void setSimpleEarnApr(BigDecimal v) { simpleEarnApr = v; }
    public BigDecimal getMakerFeeRate() { return makerFeeRate; } public void setMakerFeeRate(BigDecimal v) { makerFeeRate = v; }
    public BigDecimal getTakerFeeRate() { return takerFeeRate; } public void setTakerFeeRate(BigDecimal v) { takerFeeRate = v; }
    public BigDecimal getExitSlippageBps() { return exitSlippageBps; } public void setExitSlippageBps(BigDecimal v) { exitSlippageBps = v; }
    public BigDecimal getMaximumDrawdownRate() { return maximumDrawdownRate; } public void setMaximumDrawdownRate(BigDecimal v) { maximumDrawdownRate = v; }
    public BigDecimal getMaximumPathDivergenceRate() { return maximumPathDivergenceRate; } public void setMaximumPathDivergenceRate(BigDecimal v) { maximumPathDivergenceRate = v; }
    public BigDecimal getMaximumEndingBtcValueRatio() { return maximumEndingBtcValueRatio; } public void setMaximumEndingBtcValueRatio(BigDecimal v) { maximumEndingBtcValueRatio = v; }
    public BigDecimal getCandidateLowerPrice() { return candidateLowerPrice; } public void setCandidateLowerPrice(BigDecimal v) { candidateLowerPrice = v; }
    public BigDecimal getCandidateUpperPrice() { return candidateUpperPrice; } public void setCandidateUpperPrice(BigDecimal v) { candidateUpperPrice = v; }
    public Integer getCandidateGridCount() { return candidateGridCount; } public void setCandidateGridCount(Integer v) { candidateGridCount = v; }
    public BigDecimal getCandidateTakeProfitPrice() { return candidateTakeProfitPrice; } public void setCandidateTakeProfitPrice(BigDecimal v) { candidateTakeProfitPrice = v; }
    public BigDecimal getCandidateStopLossPrice() { return candidateStopLossPrice; } public void setCandidateStopLossPrice(BigDecimal v) { candidateStopLossPrice = v; }
    public BigDecimal getBaselineLowerPrice() { return baselineLowerPrice; } public void setBaselineLowerPrice(BigDecimal v) { baselineLowerPrice = v; }
    public BigDecimal getBaselineUpperPrice() { return baselineUpperPrice; } public void setBaselineUpperPrice(BigDecimal v) { baselineUpperPrice = v; }
    public Integer getBaselineGridCount() { return baselineGridCount; } public void setBaselineGridCount(Integer v) { baselineGridCount = v; }
    public BigDecimal getEntryReferencePrice() { return entryReferencePrice; } public void setEntryReferencePrice(BigDecimal v) { entryReferencePrice = v; }
    public Instant getEvidenceAsOf() { return evidenceAsOf; } public void setEvidenceAsOf(Instant v) { evidenceAsOf = v; }
    public String getEvidenceReference() { return evidenceReference; } public void setEvidenceReference(String v) { evidenceReference = v; }
    public Instant getCreatedAt() { return createdAt; } public void setCreatedAt(Instant v) { createdAt = v; }
    public Instant getStoppedAt() { return stoppedAt; } public void setStoppedAt(Instant v) { stoppedAt = v; }
}
