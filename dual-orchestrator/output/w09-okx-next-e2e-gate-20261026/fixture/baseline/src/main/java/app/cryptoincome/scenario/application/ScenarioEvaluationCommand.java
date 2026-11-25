package app.cryptoincome.scenario.application;

import app.cryptoincome.scenario.domain.FrozenScenarioEvaluation;
import app.cryptoincome.scenario.domain.ProductEvidenceInput;
import app.cryptoincome.scenario.domain.ScenarioProductAssumptionCatalog;

import java.util.List;

public record ScenarioEvaluationCommand(
        FrozenScenarioEvaluation evaluation,
        ScenarioProductAssumptionCatalog productAssumptions,
        List<ProductEvidenceInput> productEvidence
) {
    public ScenarioEvaluationCommand {
        if (evaluation == null || productAssumptions == null || productEvidence == null) {
            throw new IllegalArgumentException("情景评估命令字段不能为空");
        }
        productEvidence = List.copyOf(productEvidence);
        if (productEvidence.stream().map(ProductEvidenceInput::productCode).distinct().count()
                != productEvidence.size()) {
            throw new IllegalArgumentException("同一产品只能提交一份证据");
        }
    }
}
