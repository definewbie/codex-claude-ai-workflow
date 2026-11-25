package app.cryptoincome.scenario.application;

import app.cryptoincome.scenario.domain.*;
import org.springframework.stereotype.Component;

@Component
public class ScenarioProductCalculatorCatalog {
    private final SimpleEarnScenarioCalculator earn;
    private final SmartArbitrageScenarioCalculator smart;
    private final FixedGridScenarioCalculator grid;
    private final BtcSpotScenarioCalculator spot;

    public ScenarioProductCalculatorCatalog(SimpleEarnScenarioCalculator earn,
                                            SmartArbitrageScenarioCalculator smart,
                                            FixedGridScenarioCalculator grid,
                                            BtcSpotScenarioCalculator spot) {
        this.earn = earn;
        this.smart = smart;
        this.grid = grid;
        this.spot = spot;
    }

    public ProductScenarioOutcome calculate(ScenarioProductCode code, ScenarioCalculationContext context,
                                            ScenarioProductAssumptionCatalog assumptions, MarketRegime regime) {
        return switch (code) {
            case FIXED_EARN_90D -> earn.calculate(context, assumptions.fixedEarn().orElseThrow());
            case FLEXIBLE_EARN -> earn.calculate(context, assumptions.flexibleEarn().orElseThrow());
            case SMART_ARBITRAGE -> smart.calculate(context,
                    assumptions.smartArbitrage().orElseThrow()
                            .forAllocation(regime, context.allocatedCapitalUsdt()));
            case SPOT_GRID -> grid.calculate(context,
                    assumptions.spotGrid().orElseThrow().forAllocation(context.allocatedCapitalUsdt()));
            case BTC_SPOT -> spot.calculate(context, assumptions.btcSpot().orElseThrow());
        };
    }
}
