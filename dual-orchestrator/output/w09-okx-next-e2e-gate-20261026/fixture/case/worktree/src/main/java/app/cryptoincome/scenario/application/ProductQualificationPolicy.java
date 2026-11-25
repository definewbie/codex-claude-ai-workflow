package app.cryptoincome.scenario.application;

import app.cryptoincome.scenario.domain.*;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Set;

import org.springframework.stereotype.Component;

/** Backend-owned evidence rule; API callers cannot submit an eligibility status. */
@Component
public class ProductQualificationPolicy {
    public QualifiedProductEvidence qualify(ProductEvidenceInput input, Instant asOf, QualificationRule rule) {
        if (input == null || asOf == null || rule == null) {
            throw new IllegalArgumentException("产品资格校验输入不能为空");
        }
        if (input.observedAt().isAfter(asOf)) {
            throw new IllegalArgumentException("产品证据时间不能晚于评估时点");
        }
        ArrayList<String> reasons = new ArrayList<>();
        ScenarioEvidenceStatus status = ScenarioEvidenceStatus.ELIGIBLE;
        if (!input.unknownFields().isEmpty()) {
            status = ScenarioEvidenceStatus.INELIGIBLE;
            reasons.add("UNKNOWN_REQUIRED_EVIDENCE");
        }
        if (!input.knownFields().containsAll(rule.requiredKnownFields())) {
            status = ScenarioEvidenceStatus.INELIGIBLE;
            reasons.add("MISSING_REQUIRED_EVIDENCE");
        }
        if (Duration.between(input.observedAt(), asOf).compareTo(rule.maximumAge()) > 0
                && status != ScenarioEvidenceStatus.INELIGIBLE) {
            status = ScenarioEvidenceStatus.RESEARCH_ONLY;
            reasons.add("STALE_EVIDENCE");
        }
        return new QualifiedProductEvidence(input.productCode(), status, input.observedAt(),
                input.sourceReference(), reasons);
    }

    public record QualificationRule(Duration maximumAge, Set<String> requiredKnownFields) {
        public QualificationRule {
            if (maximumAge == null || maximumAge.isZero() || maximumAge.isNegative()
                    || requiredKnownFields == null) {
                throw new IllegalArgumentException("产品资格规则不合法");
            }
            requiredKnownFields = Set.copyOf(requiredKnownFields);
        }
    }
}
