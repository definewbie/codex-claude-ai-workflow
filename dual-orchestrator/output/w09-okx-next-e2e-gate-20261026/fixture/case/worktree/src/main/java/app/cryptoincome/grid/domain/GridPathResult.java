package app.cryptoincome.grid.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record GridPathResult(
        BigDecimal finalEquity,
        BigDecimal endingUsdtAfterExit,
        BigDecimal totalIncome,
        BigDecimal completedGridProfit,
        BigDecimal inventoryPnl,
        BigDecimal totalFees,
        BigDecimal maximumDrawdownRate,
        int completedCycles,
        BigDecimal endingBtcBeforeExit,
        BigDecimal endingBtcValueRatioBeforeExit,
        long outOfRangeMinutes,
        int tradeCount,
        GridExitReason exitReason,
        Instant exitTime,
        BigDecimal exitTriggerPrice
) {}
