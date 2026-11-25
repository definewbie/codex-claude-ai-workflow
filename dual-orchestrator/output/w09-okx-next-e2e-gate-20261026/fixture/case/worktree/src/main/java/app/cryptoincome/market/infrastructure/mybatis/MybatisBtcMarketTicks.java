package app.cryptoincome.market.infrastructure.mybatis;

import app.cryptoincome.market.domain.BtcMarketTick;
import app.cryptoincome.market.domain.BtcMarketTickStore;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public class MybatisBtcMarketTicks implements BtcMarketTickStore {
    private final BtcMarketTickMapper mapper;

    public MybatisBtcMarketTicks(BtcMarketTickMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public boolean append(BtcMarketTick tick) {
        return mapper.insert(tick.exchange(), tick.instrumentId(), tick.lastPrice(), tick.bidPrice(), tick.askPrice(),
                tick.providerTimestamp(), tick.observedAt(), tick.sourceEndpoint(), tick.sourcePayload(),
                tick.payloadHash()) == 1;
    }

    @Override
    public Optional<BtcMarketTick> latest(String exchange, String instrumentId) {
        return Optional.ofNullable(mapper.latest(exchange, instrumentId)).map(this::toDomain);
    }

    private BtcMarketTick toDomain(BtcMarketTickEntity value) {
        return new BtcMarketTick(value.getExchange(), value.getInstrumentId(), value.getLastPrice(),
                value.getBidPrice(), value.getAskPrice(), value.getProviderTs(), value.getObservedAt(),
                value.getSourceEndpoint(), value.getSourcePayload(), value.getPayloadHash());
    }
}
