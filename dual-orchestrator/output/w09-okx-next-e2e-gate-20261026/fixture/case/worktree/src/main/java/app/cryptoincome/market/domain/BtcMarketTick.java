package app.cryptoincome.market.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record BtcMarketTick(
        String exchange,
        String instrumentId,
        BigDecimal lastPrice,
        BigDecimal bidPrice,
        BigDecimal askPrice,
        Instant providerTimestamp,
        Instant observedAt,
        String sourceEndpoint,
        String sourcePayload,
        String payloadHash
) {
    public BtcMarketTick {
        requireText(exchange, "exchange");
        requireText(instrumentId, "instrumentId");
        requirePositive(lastPrice, "lastPrice");
        requireOptionalPositive(bidPrice, "bidPrice");
        requireOptionalPositive(askPrice, "askPrice");
        if (bidPrice != null && askPrice != null && bidPrice.compareTo(askPrice) > 0) {
            throw new IllegalArgumentException("买一价不能高于卖一价");
        }
        if (providerTimestamp == null || observedAt == null) {
            throw new IllegalArgumentException("行情时间不能为空");
        }
        requireText(sourceEndpoint, "sourceEndpoint");
        requireText(sourcePayload, "sourcePayload");
        if (payloadHash == null || !payloadHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("payloadHash 必须是 64 位小写十六进制 SHA-256");
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " 不能为空");
    }

    private static void requirePositive(BigDecimal value, String name) {
        if (value == null || value.signum() <= 0) throw new IllegalArgumentException(name + " 必须大于 0");
    }

    private static void requireOptionalPositive(BigDecimal value, String name) {
        if (value != null && value.signum() <= 0) throw new IllegalArgumentException(name + " 必须大于 0");
    }
}
