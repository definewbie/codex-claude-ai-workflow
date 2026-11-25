package app.cryptoincome.scenario.domain;

import java.util.List;

public record ScenarioDecisionReadModel(
        FrozenScenarioEvaluation evaluation,
        PortfolioOptionSet optionSet,
        List<QualifiedProductEvidence> qualifiedProducts,
        List<QualifiedProductEvidence> excludedProducts,
        List<String> limitations
) {
    public ScenarioDecisionReadModel {
        if (evaluation == null || optionSet == null || qualifiedProducts == null
                || excludedProducts == null || limitations == null) {
            throw new IllegalArgumentException("情景决策读模型字段不能为空");
        }
        qualifiedProducts = List.copyOf(qualifiedProducts);
        excludedProducts = List.copyOf(excludedProducts);
        limitations = List.copyOf(limitations);
        long evidenceProducts = java.util.stream.Stream.concat(qualifiedProducts.stream(), excludedProducts.stream())
                .map(QualifiedProductEvidence::productCode).distinct().count();
        if (evidenceProducts != qualifiedProducts.size() + excludedProducts.size()
                || qualifiedProducts.stream().anyMatch(value -> value.status() != ScenarioEvidenceStatus.ELIGIBLE)
                || excludedProducts.stream().anyMatch(value -> value.status() == ScenarioEvidenceStatus.ELIGIBLE)) {
            throw new IllegalArgumentException("产品资格投影重复或状态分组错误");
        }
        java.util.Set<String> expectedPaths = evaluation.scenarios().stream()
                .flatMap(scenario -> scenario.paths().stream())
                .map(path -> path.scenarioCode() + "/" + path.pathCode()).collect(java.util.stream.Collectors.toSet());
        if (optionSet.options().stream().anyMatch(option -> !option.pathOutcomes().stream()
                .map(path -> path.scenarioCode() + "/" + path.pathCode())
                .collect(java.util.stream.Collectors.toSet()).equals(expectedPaths))) {
            throw new IllegalArgumentException("方案路径投影必须完整覆盖冻结情景");
        }
    }
}
