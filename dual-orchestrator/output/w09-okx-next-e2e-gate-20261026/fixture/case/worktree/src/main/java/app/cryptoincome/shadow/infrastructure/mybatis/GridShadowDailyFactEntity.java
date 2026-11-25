package app.cryptoincome.shadow.infrastructure.mybatis;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.Instant;

@TableName("grid_shadow_daily_fact")
public class GridShadowDailyFactEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private Long shadowStudyId;
    private String variantRole;
    private Instant valuationAt;
    private Integer candleCount;
    private BigDecimal netIncome;
    private BigDecimal simpleEarnIncome;
    private BigDecimal buyHoldIncome;
    private BigDecimal maximumDrawdownRate;
    private BigDecimal pathDivergenceRate;
    private BigDecimal endingBtcValueRatio;
    private Integer completedCycles;
    private BigDecimal totalFees;
    private String exitReason;

    public Long getId() { return id; } public void setId(Long v) { id = v; }
    public Long getShadowStudyId() { return shadowStudyId; } public void setShadowStudyId(Long v) { shadowStudyId = v; }
    public String getVariantRole() { return variantRole; } public void setVariantRole(String v) { variantRole = v; }
    public Instant getValuationAt() { return valuationAt; } public void setValuationAt(Instant v) { valuationAt = v; }
    public Integer getCandleCount() { return candleCount; } public void setCandleCount(Integer v) { candleCount = v; }
    public BigDecimal getNetIncome() { return netIncome; } public void setNetIncome(BigDecimal v) { netIncome = v; }
    public BigDecimal getSimpleEarnIncome() { return simpleEarnIncome; } public void setSimpleEarnIncome(BigDecimal v) { simpleEarnIncome = v; }
    public BigDecimal getBuyHoldIncome() { return buyHoldIncome; } public void setBuyHoldIncome(BigDecimal v) { buyHoldIncome = v; }
    public BigDecimal getMaximumDrawdownRate() { return maximumDrawdownRate; } public void setMaximumDrawdownRate(BigDecimal v) { maximumDrawdownRate = v; }
    public BigDecimal getPathDivergenceRate() { return pathDivergenceRate; } public void setPathDivergenceRate(BigDecimal v) { pathDivergenceRate = v; }
    public BigDecimal getEndingBtcValueRatio() { return endingBtcValueRatio; } public void setEndingBtcValueRatio(BigDecimal v) { endingBtcValueRatio = v; }
    public Integer getCompletedCycles() { return completedCycles; } public void setCompletedCycles(Integer v) { completedCycles = v; }
    public BigDecimal getTotalFees() { return totalFees; } public void setTotalFees(BigDecimal v) { totalFees = v; }
    public String getExitReason() { return exitReason; } public void setExitReason(String v) { exitReason = v; }
}
