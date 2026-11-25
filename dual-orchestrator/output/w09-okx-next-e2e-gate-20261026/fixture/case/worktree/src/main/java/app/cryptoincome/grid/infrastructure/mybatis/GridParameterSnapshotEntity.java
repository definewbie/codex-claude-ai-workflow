package app.cryptoincome.grid.infrastructure.mybatis;

import java.math.BigDecimal;
import java.time.Instant;

public class GridParameterSnapshotEntity {
    private Long id;
    private String exchange;
    private String instrumentId;
    private String duration;
    private String gridType;
    private BigDecimal lowerPrice;
    private BigDecimal upperPrice;
    private Integer gridCount;
    private BigDecimal annualizedRate;
    private BigDecimal minimumPerGridProfitRate;
    private BigDecimal maximumPerGridProfitRate;
    private BigDecimal minimumInvestment;
    private String investmentCurrency;
    private String sourceEndpoint;
    private String sourcePayload;
    private String payloadHash;
    private Instant observedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getExchange() { return exchange; }
    public void setExchange(String exchange) { this.exchange = exchange; }
    public String getInstrumentId() { return instrumentId; }
    public void setInstrumentId(String instrumentId) { this.instrumentId = instrumentId; }
    public String getDuration() { return duration; }
    public void setDuration(String duration) { this.duration = duration; }
    public String getGridType() { return gridType; }
    public void setGridType(String gridType) { this.gridType = gridType; }
    public BigDecimal getLowerPrice() { return lowerPrice; }
    public void setLowerPrice(BigDecimal lowerPrice) { this.lowerPrice = lowerPrice; }
    public BigDecimal getUpperPrice() { return upperPrice; }
    public void setUpperPrice(BigDecimal upperPrice) { this.upperPrice = upperPrice; }
    public Integer getGridCount() { return gridCount; }
    public void setGridCount(Integer gridCount) { this.gridCount = gridCount; }
    public BigDecimal getAnnualizedRate() { return annualizedRate; }
    public void setAnnualizedRate(BigDecimal annualizedRate) { this.annualizedRate = annualizedRate; }
    public BigDecimal getMinimumPerGridProfitRate() { return minimumPerGridProfitRate; }
    public void setMinimumPerGridProfitRate(BigDecimal value) { this.minimumPerGridProfitRate = value; }
    public BigDecimal getMaximumPerGridProfitRate() { return maximumPerGridProfitRate; }
    public void setMaximumPerGridProfitRate(BigDecimal value) { this.maximumPerGridProfitRate = value; }
    public BigDecimal getMinimumInvestment() { return minimumInvestment; }
    public void setMinimumInvestment(BigDecimal minimumInvestment) { this.minimumInvestment = minimumInvestment; }
    public String getInvestmentCurrency() { return investmentCurrency; }
    public void setInvestmentCurrency(String investmentCurrency) { this.investmentCurrency = investmentCurrency; }
    public String getSourceEndpoint() { return sourceEndpoint; }
    public void setSourceEndpoint(String sourceEndpoint) { this.sourceEndpoint = sourceEndpoint; }
    public String getSourcePayload() { return sourcePayload; }
    public void setSourcePayload(String sourcePayload) { this.sourcePayload = sourcePayload; }
    public String getPayloadHash() { return payloadHash; }
    public void setPayloadHash(String payloadHash) { this.payloadHash = payloadHash; }
    public Instant getObservedAt() { return observedAt; }
    public void setObservedAt(Instant observedAt) { this.observedAt = observedAt; }
}
