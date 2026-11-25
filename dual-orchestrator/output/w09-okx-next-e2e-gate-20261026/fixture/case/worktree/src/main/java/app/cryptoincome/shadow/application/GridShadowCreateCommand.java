package app.cryptoincome.shadow.application;

import app.cryptoincome.grid.domain.GridScenarioParameters;

import java.math.BigDecimal;
import java.time.Instant;

public record GridShadowCreateCommand(
        long configurationSnapshotId, Instant startAt, int targetDays, BigDecimal capital,
        BigDecimal simpleEarnApr, BigDecimal makerFeeRate, BigDecimal takerFeeRate,
        BigDecimal exitSlippageBps, BigDecimal maximumDrawdownRate,
        BigDecimal maximumPathDivergenceRate, BigDecimal maximumEndingBtcValueRatio,
        GridScenarioParameters candidate, Instant evidenceAsOf, String evidenceReference) {}
