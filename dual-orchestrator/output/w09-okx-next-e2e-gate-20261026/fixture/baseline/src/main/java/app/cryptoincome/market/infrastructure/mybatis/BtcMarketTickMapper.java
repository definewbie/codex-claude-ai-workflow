package app.cryptoincome.market.infrastructure.mybatis;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.Instant;

public interface BtcMarketTickMapper {
    @Insert("""
            INSERT INTO btc_market_tick_fact (
                exchange, instrument_id, last_price, bid_price, ask_price,
                provider_ts, observed_at, source_endpoint, source_payload, payload_hash
            ) VALUES (
                #{exchange}, #{instrumentId}, #{lastPrice}, #{bidPrice}, #{askPrice},
                #{providerTs}, #{observedAt}, #{sourceEndpoint}, CAST(#{sourcePayload} AS JSON), #{payloadHash}
            )
            ON DUPLICATE KEY UPDATE id = id
            """)
    int insert(
            @Param("exchange") String exchange,
            @Param("instrumentId") String instrumentId,
            @Param("lastPrice") BigDecimal lastPrice,
            @Param("bidPrice") BigDecimal bidPrice,
            @Param("askPrice") BigDecimal askPrice,
            @Param("providerTs") Instant providerTs,
            @Param("observedAt") Instant observedAt,
            @Param("sourceEndpoint") String sourceEndpoint,
            @Param("sourcePayload") String sourcePayload,
            @Param("payloadHash") String payloadHash);

    @Select("""
            SELECT id, exchange, instrument_id, last_price, bid_price, ask_price,
                   provider_ts, observed_at, source_endpoint, source_payload, payload_hash
            FROM btc_market_tick_fact
            WHERE exchange = #{exchange} AND instrument_id = #{instrumentId}
            ORDER BY provider_ts DESC, observed_at DESC, id DESC
            LIMIT 1
            """)
    BtcMarketTickEntity latest(@Param("exchange") String exchange, @Param("instrumentId") String instrumentId);
}
