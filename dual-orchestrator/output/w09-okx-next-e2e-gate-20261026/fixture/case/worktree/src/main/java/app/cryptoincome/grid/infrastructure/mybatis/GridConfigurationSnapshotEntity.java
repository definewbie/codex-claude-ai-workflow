package app.cryptoincome.grid.infrastructure.mybatis;

import java.math.BigDecimal;
import java.time.Instant;

public class GridConfigurationSnapshotEntity {
    private Long id;
    private String exchange;
    private String instrumentId;
    private String sourceType;
    private String recommendationHorizonLabel;
    private BigDecimal lowerPrice;
    private BigDecimal upperPrice;
    private Integer gridCount;
    private String gridType;
    private BigDecimal displayedAnnualizedRate;
    private BigDecimal displayedMinimumPerGridRate;
    private BigDecimal displayedMaximumPerGridRate;
    private Boolean profitReinvestmentEnabled;
    private Boolean autoEarnEnabled;
    private Boolean trailingUpEnabled;
    private Boolean trailingDownEnabled;
    private BigDecimal takeProfitPrice;
    private BigDecimal stopLossPrice;
    private String sourceReference;
    private Instant observedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getExchange() { return exchange; }
    public void setExchange(String exchange) { this.exchange = exchange; }
    public String getInstrumentId() { return instrumentId; }
    public void setInstrumentId(String instrumentId) { this.instrumentId = instrumentId; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getRecommendationHorizonLabel() { return recommendationHorizonLabel; }
    public void setRecommendationHorizonLabel(String value) { this.recommendationHorizonLabel = value; }
    public BigDecimal getLowerPrice() { return lowerPrice; }
    public void setLowerPrice(BigDecimal lowerPrice) { this.lowerPrice = lowerPrice; }
    public BigDecimal getUpperPrice() { return upperPrice; }
    public void setUpperPrice(BigDecimal upperPrice) { this.upperPrice = upperPrice; }
    public Integer getGridCount() { return gridCount; }
    public void setGridCount(Integer gridCount) { this.gridCount = gridCount; }
    public String getGridType() { return gridType; }
    public void setGridType(String gridType) { this.gridType = gridType; }
    public BigDecimal getDisplayedAnnualizedRate() { return displayedAnnualizedRate; }
    public void setDisplayedAnnualizedRate(BigDecimal value) { this.displayedAnnualizedRate = value; }
    public BigDecimal getDisplayedMinimumPerGridRate() { return displayedMinimumPerGridRate; }
    public void setDisplayedMinimumPerGridRate(BigDecimal value) { this.displayedMinimumPerGridRate = value; }
    public BigDecimal getDisplayedMaximumPerGridRate() { return displayedMaximumPerGridRate; }
    public void setDisplayedMaximumPerGridRate(BigDecimal value) { this.displayedMaximumPerGridRate = value; }
    public Boolean getProfitReinvestmentEnabled() { return profitReinvestmentEnabled; }
    public void setProfitReinvestmentEnabled(Boolean value) { this.profitReinvestmentEnabled = value; }
    public Boolean getAutoEarnEnabled() { return autoEarnEnabled; }
    public void setAutoEarnEnabled(Boolean value) { this.autoEarnEnabled = value; }
    public Boolean getTrailingUpEnabled() { return trailingUpEnabled; }
    public void setTrailingUpEnabled(Boolean value) { this.trailingUpEnabled = value; }
    public Boolean getTrailingDownEnabled() { return trailingDownEnabled; }
    public void setTrailingDownEnabled(Boolean value) { this.trailingDownEnabled = value; }
    public BigDecimal getTakeProfitPrice() { return takeProfitPrice; }
    public void setTakeProfitPrice(BigDecimal value) { this.takeProfitPrice = value; }
    public BigDecimal getStopLossPrice() { return stopLossPrice; }
    public void setStopLossPrice(BigDecimal value) { this.stopLossPrice = value; }
    public String getSourceReference() { return sourceReference; }
    public void setSourceReference(String value) { this.sourceReference = value; }
    public Instant getObservedAt() { return observedAt; }
    public void setObservedAt(Instant observedAt) { this.observedAt = observedAt; }
}
