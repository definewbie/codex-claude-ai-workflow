package app.cryptoincome.market.domain;

import java.util.Optional;

public interface BtcMarketTickStore {
    boolean append(BtcMarketTick tick);
    Optional<BtcMarketTick> latest(String exchange, String instrumentId);
}
