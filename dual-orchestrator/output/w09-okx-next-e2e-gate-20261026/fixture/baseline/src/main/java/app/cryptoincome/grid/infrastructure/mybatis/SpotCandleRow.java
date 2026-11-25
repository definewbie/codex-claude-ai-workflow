package app.cryptoincome.grid.infrastructure.mybatis;

import java.math.BigDecimal;
import java.time.Instant;

public class SpotCandleRow {
    private String exchange;
    private String instrumentId;
    private String bar;
    private Instant openTime;
    private BigDecimal openPrice;
    private BigDecimal highPrice;
    private BigDecimal lowPrice;
    private BigDecimal closePrice;
    private BigDecimal baseVolume;
    private BigDecimal quoteVolume;
    private String sourceEndpoint;
    private String sourcePayload;
    private String payloadHash;
    private Instant firstObservedAt;

    public String getExchange() { return exchange; }
    public void setExchange(String exchange) { this.exchange = exchange; }
    public String getInstrumentId() { return instrumentId; }
    public void setInstrumentId(String instrumentId) { this.instrumentId = instrumentId; }
    public String getBar() { return bar; }
    public void setBar(String bar) { this.bar = bar; }
    public Instant getOpenTime() { return openTime; }
    public void setOpenTime(Instant openTime) { this.openTime = openTime; }
    public BigDecimal getOpenPrice() { return openPrice; }
    public void setOpenPrice(BigDecimal openPrice) { this.openPrice = openPrice; }
    public BigDecimal getHighPrice() { return highPrice; }
    public void setHighPrice(BigDecimal highPrice) { this.highPrice = highPrice; }
    public BigDecimal getLowPrice() { return lowPrice; }
    public void setLowPrice(BigDecimal lowPrice) { this.lowPrice = lowPrice; }
    public BigDecimal getClosePrice() { return closePrice; }
    public void setClosePrice(BigDecimal closePrice) { this.closePrice = closePrice; }
    public BigDecimal getBaseVolume() { return baseVolume; }
    public void setBaseVolume(BigDecimal baseVolume) { this.baseVolume = baseVolume; }
    public BigDecimal getQuoteVolume() { return quoteVolume; }
    public void setQuoteVolume(BigDecimal quoteVolume) { this.quoteVolume = quoteVolume; }
    public String getSourceEndpoint() { return sourceEndpoint; }
    public void setSourceEndpoint(String sourceEndpoint) { this.sourceEndpoint = sourceEndpoint; }
    public String getSourcePayload() { return sourcePayload; }
    public void setSourcePayload(String sourcePayload) { this.sourcePayload = sourcePayload; }
    public String getPayloadHash() { return payloadHash; }
    public void setPayloadHash(String payloadHash) { this.payloadHash = payloadHash; }
    public Instant getFirstObservedAt() { return firstObservedAt; }
    public void setFirstObservedAt(Instant firstObservedAt) { this.firstObservedAt = firstObservedAt; }
}
