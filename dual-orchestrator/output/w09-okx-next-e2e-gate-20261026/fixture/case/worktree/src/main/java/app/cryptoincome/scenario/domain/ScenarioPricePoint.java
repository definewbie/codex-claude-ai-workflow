package app.cryptoincome.scenario.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record ScenarioPricePoint(Instant at, BigDecimal btcUsdtPrice) {
    public ScenarioPricePoint {
        if (at == null || btcUsdtPrice == null || btcUsdtPrice.signum() <= 0) {
            throw new IllegalArgumentException("情景价格点的时间不能为空且价格必须大于0");
        }
    }
}
