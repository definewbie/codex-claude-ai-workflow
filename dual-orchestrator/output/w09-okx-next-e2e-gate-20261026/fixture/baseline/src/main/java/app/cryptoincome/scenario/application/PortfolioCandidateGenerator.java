package app.cryptoincome.scenario.application;

import app.cryptoincome.scenario.domain.PortfolioAllocation;
import app.cryptoincome.scenario.domain.PortfolioConstraintSnapshot;
import app.cryptoincome.scenario.domain.ScenarioProductCode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class PortfolioCandidateGenerator {
    public List<List<PortfolioAllocation>> generate(BigDecimal totalCapitalUsdt,
                                                     Set<ScenarioProductCode> eligibleProducts,
                                                     PortfolioConstraintSnapshot constraints,
                                                     Map<ScenarioProductCode, BigDecimal> amountCaps) {
        if (totalCapitalUsdt == null || totalCapitalUsdt.signum() <= 0 || eligibleProducts == null
                || eligibleProducts.isEmpty() || constraints == null || amountCaps == null) {
            throw new IllegalArgumentException("组合枚举输入不完整");
        }
        List<ScenarioProductCode> products = eligibleProducts.stream()
                .sorted(Comparator.comparingInt(Enum::ordinal)).toList();
        int totalUnits = BigDecimal.ONE.divide(constraints.allocationStep()).intValueExact();
        EnumMap<ScenarioProductCode, Integer> maximumUnits = new EnumMap<>(ScenarioProductCode.class);
        for (ScenarioProductCode product : products) {
            BigDecimal ratioCap = constraints.maximumProductRatios().getOrDefault(product, BigDecimal.ONE);
            BigDecimal amountCapRatio = amountCaps.containsKey(product)
                    ? amountCaps.get(product).divide(totalCapitalUsdt, 20, RoundingMode.DOWN)
                    : BigDecimal.ONE;
            BigDecimal effective = ratioCap.min(amountCapRatio).min(BigDecimal.ONE);
            maximumUnits.put(product, effective.divide(constraints.allocationStep(), 0, RoundingMode.FLOOR).intValueExact());
        }
        int minimumLiquidUnits = constraints.minimumLiquidUsdtRatio()
                .divide(constraints.allocationStep(), 0, RoundingMode.CEILING).intValueExact();
        List<List<PortfolioAllocation>> result = new ArrayList<>();
        enumerate(products, maximumUnits, 0, totalUnits, new int[products.size()], minimumLiquidUnits,
                totalCapitalUsdt, constraints.allocationStep(), result);
        return List.copyOf(result);
    }

    private static void enumerate(List<ScenarioProductCode> products,
                                  Map<ScenarioProductCode, Integer> maximumUnits,
                                  int index, int remainingUnits, int[] units,
                                  int minimumLiquidUnits, BigDecimal totalCapital,
                                  BigDecimal step, List<List<PortfolioAllocation>> output) {
        if (index == products.size()) {
            if (remainingUnits != 0) return;
            int flexibleIndex = products.indexOf(ScenarioProductCode.FLEXIBLE_EARN);
            int liquidUnits = flexibleIndex < 0 ? 0 : units[flexibleIndex];
            if (liquidUnits < minimumLiquidUnits) return;
            ArrayList<PortfolioAllocation> allocations = new ArrayList<>();
            for (int i = 0; i < products.size(); i++) {
                if (units[i] == 0) continue;
                BigDecimal ratio = step.multiply(new BigDecimal(units[i]));
                allocations.add(new PortfolioAllocation(products.get(i), ratio, totalCapital.multiply(ratio)));
            }
            output.add(List.copyOf(allocations));
            return;
        }
        ScenarioProductCode product = products.get(index);
        int maximum = Math.min(remainingUnits, maximumUnits.getOrDefault(product, 0));
        for (int value = 0; value <= maximum; value++) {
            units[index] = value;
            enumerate(products, maximumUnits, index + 1, remainingUnits - value, units,
                    minimumLiquidUnits, totalCapital, step, output);
        }
        units[index] = 0;
    }
}
