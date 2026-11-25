package app.cryptoincome.scenario.domain;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

public record ScenarioProductAssumptionCatalog(
        Optional<EarnScenarioAssumption> fixedEarn,
        Optional<EarnScenarioAssumption> flexibleEarn,
        Optional<SmartArbitrageScenarioProfile> smartArbitrage,
        Optional<GridScenarioProfile> spotGrid,
        Optional<BtcSpotScenarioAssumption> btcSpot
) {
    public ScenarioProductAssumptionCatalog {
        fixedEarn = fixedEarn == null ? Optional.empty() : fixedEarn;
        flexibleEarn = flexibleEarn == null ? Optional.empty() : flexibleEarn;
        smartArbitrage = smartArbitrage == null ? Optional.empty() : smartArbitrage;
        spotGrid = spotGrid == null ? Optional.empty() : spotGrid;
        btcSpot = btcSpot == null ? Optional.empty() : btcSpot;
        fixedEarn.ifPresent(value -> require(value.productCode(), ScenarioProductCode.FIXED_EARN_90D));
        flexibleEarn.ifPresent(value -> require(value.productCode(), ScenarioProductCode.FLEXIBLE_EARN));
        if (fixedEarn.isEmpty() && flexibleEarn.isEmpty() && smartArbitrage.isEmpty()
                && spotGrid.isEmpty() && btcSpot.isEmpty()) {
            throw new IllegalArgumentException("至少需要一个产品假设");
        }
    }

    public Set<ScenarioProductCode> availableProducts() {
        EnumSet<ScenarioProductCode> result = EnumSet.noneOf(ScenarioProductCode.class);
        fixedEarn.ifPresent(ignored -> result.add(ScenarioProductCode.FIXED_EARN_90D));
        flexibleEarn.ifPresent(ignored -> result.add(ScenarioProductCode.FLEXIBLE_EARN));
        smartArbitrage.ifPresent(ignored -> result.add(ScenarioProductCode.SMART_ARBITRAGE));
        spotGrid.ifPresent(ignored -> result.add(ScenarioProductCode.SPOT_GRID));
        btcSpot.ifPresent(ignored -> result.add(ScenarioProductCode.BTC_SPOT));
        return Set.copyOf(result);
    }

    private static void require(ScenarioProductCode actual, ScenarioProductCode expected) {
        if (actual != expected) throw new IllegalArgumentException("简单赚币产品假设放置错误");
    }
}
