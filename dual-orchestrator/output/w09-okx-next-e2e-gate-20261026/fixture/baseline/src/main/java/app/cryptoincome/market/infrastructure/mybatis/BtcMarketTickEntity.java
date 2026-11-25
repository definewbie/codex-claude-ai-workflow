package app.cryptoincome.market.infrastructure.mybatis;

import java.math.BigDecimal;
import java.time.Instant;

public class BtcMarketTickEntity {
    private Long id;
    private String exchange;
    private String instrumentId;
    private BigDecimal lastPrice;
    private BigDecimal bidPrice;
    private BigDecimal askPrice;
    private Instant providerTs;
    private Instant observedAt;
    private String sourceEndpoint;
    private String sourcePayload;
    private String payloadHash;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getExchange() { return exchange; }
    public void setExchange(String exchange) { this.exchange = exchange; }
    public String getInstrumentId() { return instrumentId; }
    public void setInstrumentId(String instrumentId) { this.instrumentId = instrumentId; }
    public BigDecimal getLastPrice() { return lastPrice; }
    public void setLastPrice(BigDecimal lastPrice) { this.lastPrice = lastPrice; }
    public BigDecimal getBidPrice() { return bidPrice; }
    public void setBidPrice(BigDecimal bidPrice) { this.bidPrice = bidPrice; }
    public BigDecimal getAskPrice() { return askPrice; }
    public void setAskPrice(BigDecimal askPrice) { this.askPrice = askPrice; }
    public Instant getProviderTs() { return providerTs; }
    public void setProviderTs(Instant providerTs) { this.providerTs = providerTs; }
    public Instant getObservedAt() { return observedAt; }
    public void setObservedAt(Instant observedAt) { this.observedAt = observedAt; }
    public String getSourceEndpoint() { return sourceEndpoint; }
    public void setSourceEndpoint(String sourceEndpoint) { this.sourceEndpoint = sourceEndpoint; }
    public String getSourcePayload() { return sourcePayload; }
    public void setSourcePayload(String sourcePayload) { this.sourcePayload = sourcePayload; }
    public String getPayloadHash() { return payloadHash; }
    public void setPayloadHash(String payloadHash) { this.payloadHash = payloadHash; }
}
