package app.cryptoincome.scenario.application;

import app.cryptoincome.scenario.domain.ProductScenarioOutcome;
import app.cryptoincome.scenario.domain.ScenarioCalculationContext;
import app.cryptoincome.scenario.domain.ScenarioProductCode;

public interface ProductScenarioCalculator<A> {
    boolean supports(ScenarioProductCode productCode);
    ProductScenarioOutcome calculate(ScenarioCalculationContext context, A assumption);
}
