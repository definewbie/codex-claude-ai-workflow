package app.cryptoincome.scenario.domain;

import java.time.Instant;
import java.util.Set;

public record ProductEvidenceInput(
        ScenarioProductCode productCode,
        Instant observedAt,
        String sourceReference,
        Set<String> knownFields,
        Set<String> unknownFields
) {
    public ProductEvidenceInput {
        if (productCode == null || observedAt == null || sourceReference == null || sourceReference.isBlank()
                || knownFields == null || unknownFields == null) {
            throw new IllegalArgumentException("产品证据输入不能为空");
        }
        knownFields = Set.copyOf(knownFields);
        unknownFields = Set.copyOf(unknownFields);
    }
}
