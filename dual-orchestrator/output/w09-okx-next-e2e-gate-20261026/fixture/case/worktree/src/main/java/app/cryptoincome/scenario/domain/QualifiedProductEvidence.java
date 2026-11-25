package app.cryptoincome.scenario.domain;

import java.time.Instant;
import java.util.List;

public record QualifiedProductEvidence(
        ScenarioProductCode productCode,
        ScenarioEvidenceStatus status,
        Instant observedAt,
        String sourceReference,
        List<String> reasonCodes
) {
    public QualifiedProductEvidence {
        reasonCodes = List.copyOf(reasonCodes);
    }
}
