package app.cryptoincome.market.domain;

public interface BtcMarketTickSource {
    BtcMarketTick fetch(String instrumentId);
}
