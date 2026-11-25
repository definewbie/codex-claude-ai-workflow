package app.cryptoincome.scenario;

import app.cryptoincome.scenario.application.ProductQualificationPolicy;
import app.cryptoincome.scenario.domain.*;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ProductQualificationPolicyTest {
    private final ProductQualificationPolicy policy = new ProductQualificationPolicy();
    private final Instant asOf = Instant.parse("2026-08-21T00:00:00Z");
    private final ProductQualificationPolicy.QualificationRule rule =
            new ProductQualificationPolicy.QualificationRule(Duration.ofDays(1), Set.of("APR", "CAPACITY"));

    @Test
    void backendRuleDerivesEligibilityInsteadOfAcceptingItFromApi() {
        var eligible = policy.qualify(input(asOf.minusSeconds(60), Set.of("APR", "CAPACITY"), Set.of()),
                asOf, rule);
        var stale = policy.qualify(input(asOf.minus(Duration.ofDays(2)), Set.of("APR", "CAPACITY"), Set.of()),
                asOf, rule);
        var unknown = policy.qualify(input(asOf.minusSeconds(60), Set.of("APR", "CAPACITY"), Set.of("EXIT_COST")),
                asOf, rule);

        assertThat(eligible.status()).isEqualTo(ScenarioEvidenceStatus.ELIGIBLE);
        assertThat(stale.status()).isEqualTo(ScenarioEvidenceStatus.RESEARCH_ONLY);
        assertThat(stale.reasonCodes()).containsExactly("STALE_EVIDENCE");
        assertThat(unknown.status()).isEqualTo(ScenarioEvidenceStatus.INELIGIBLE);
        assertThat(unknown.reasonCodes()).contains("UNKNOWN_REQUIRED_EVIDENCE");
    }

    private static ProductEvidenceInput input(Instant observedAt, Set<String> known, Set<String> unknown) {
        return new ProductEvidenceInput(ScenarioProductCode.FIXED_EARN_90D, observedAt,
                "OKX确认页", known, unknown);
    }
}
