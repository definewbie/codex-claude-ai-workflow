package app.cryptoincome.funding.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record FundingRateObservation(
        String exchange,
        DerivativeInstrumentType instrumentType,
        String instrumentId,
        FundingFormulaType formulaType,
        BigDecimal predictedRate,
        BigDecimal realizedRate,
        Instant fundingTime,
        FundingSettlementMethod settlementMethod,
        String sourceEndpoint,
        String sourcePayload,
        String payloadHash,
        Instant observedAt
) {
    public FundingRateObservation {
        exchange = requireText(exchange, "exchange");
        instrumentType = Objects.requireNonNull(instrumentType, "instrumentType 不能为空");
        instrumentId = requireText(instrumentId, "instrumentId");
        formulaType = Objects.requireNonNull(formulaType, "formulaType 不能为空");
        predictedRate = Objects.requireNonNull(predictedRate, "predictedRate 不能为空");
        realizedRate = Objects.requireNonNull(realizedRate, "realizedRate 不能为空");
        fundingTime = Objects.requireNonNull(fundingTime, "fundingTime 不能为空");
        settlementMethod = Objects.requireNonNull(settlementMethod, "settlementMethod 不能为空");
        sourceEndpoint = requireText(sourceEndpoint, "sourceEndpoint");
        sourcePayload = requireText(sourcePayload, "sourcePayload");
        payloadHash = requireText(payloadHash, "payloadHash");
        observedAt = Objects.requireNonNull(observedAt, "observedAt 不能为空");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
        return value;
    }
}
