package app.cryptoincome.grid.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record SpotCandle(
        String exchange,
        String instrumentId,
        CandleBar bar,
        Instant openTime,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        BigDecimal baseVolume,
        BigDecimal quoteVolume,
        boolean confirmed,
        String sourceEndpoint,
        String sourcePayload,
        String payloadHash,
        Instant observedAt
) {
    public SpotCandle {
        requireText(exchange, "exchange");
        requireText(instrumentId, "instrumentId");
        Objects.requireNonNull(bar, "bar");
        Objects.requireNonNull(openTime, "openTime");
        if (openTime.toEpochMilli() % bar.duration().toMillis() != 0) {
            throw new IllegalArgumentException("openTime 未按 " + bar.providerValue() + " 对齐");
        }
        requirePositive(open, "open");
        requirePositive(high, "high");
        requirePositive(low, "low");
        requirePositive(close, "close");
        if (high.compareTo(open) < 0 || high.compareTo(close) < 0 || high.compareTo(low) < 0) {
            throw new IllegalArgumentException("high 小于 OHLC 中的其他价格");
        }
        if (low.compareTo(open) > 0 || low.compareTo(close) > 0) {
            throw new IllegalArgumentException("low 大于开盘价或收盘价");
        }
        requireNonNegative(baseVolume, "baseVolume");
        requireNonNegative(quoteVolume, "quoteVolume");
        requireText(sourceEndpoint, "sourceEndpoint");
        requireText(sourcePayload, "sourcePayload");
        if (payloadHash == null || !payloadHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("payloadHash 必须是 64 位小写十六进制 SHA-256");
        }
        Objects.requireNonNull(observedAt, "observedAt");
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " 不能为空");
    }

    private static void requirePositive(BigDecimal value, String name) {
        if (value == null || value.signum() <= 0) throw new IllegalArgumentException(name + " 必须大于 0");
    }

    private static void requireNonNegative(BigDecimal value, String name) {
        if (value == null || value.signum() < 0) throw new IllegalArgumentException(name + " 不能为负数");
    }
}
