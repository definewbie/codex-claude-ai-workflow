package app.cryptoincome.assessment.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record SmartArbitrageCosts(
        Instant asOf,
        String source,
        Optional<OpeningCost> openingCost,
        Optional<BigDecimal> exitCost,
        Optional<BigDecimal> slippageCost,
        Optional<BigDecimal> rebalanceCost,
        Optional<BigDecimal> serviceCost
) {
    public SmartArbitrageCosts {
        Objects.requireNonNull(asOf, "asOf 不能为空");
        if (source == null || source.isBlank()) throw new IllegalArgumentException("source 不能为空");
        openingCost = Objects.requireNonNull(openingCost, "openingCost 不能为空");
        exitCost = requireOptionalCost(exitCost, "退出成本");
        slippageCost = requireOptionalCost(slippageCost, "滑点成本");
        rebalanceCost = requireOptionalCost(rebalanceCost, "再平衡成本");
        serviceCost = requireOptionalCost(serviceCost, "服务/融资成本");
    }

    public BigDecimal knownTotal() {
        BigDecimal total = openingCost.map(OpeningCost::amount).orElse(BigDecimal.ZERO);
        return total.add(exitCost.orElse(BigDecimal.ZERO))
                .add(slippageCost.orElse(BigDecimal.ZERO))
                .add(rebalanceCost.orElse(BigDecimal.ZERO))
                .add(serviceCost.orElse(BigDecimal.ZERO));
    }

    public boolean complete() {
        return openingCost.isPresent() && exitCost.isPresent() && slippageCost.isPresent()
                && rebalanceCost.isPresent() && serviceCost.isPresent();
    }

    public List<String> unknowns() {
        List<String> result = new ArrayList<>();
        if (openingCost.isEmpty()) result.add("开仓成本");
        if (exitCost.isEmpty()) result.add("退出成本");
        if (slippageCost.isEmpty()) result.add("滑点成本");
        if (rebalanceCost.isEmpty()) result.add("再平衡成本");
        if (serviceCost.isEmpty()) result.add("服务/融资成本");
        return List.copyOf(result);
    }

    private static Optional<BigDecimal> requireOptionalCost(Optional<BigDecimal> value, String name) {
        Objects.requireNonNull(value, name + "不能为空");
        value.ifPresent(cost -> {
            if (cost.signum() < 0) throw new IllegalArgumentException(name + "不能为负数");
        });
        return value;
    }
}
