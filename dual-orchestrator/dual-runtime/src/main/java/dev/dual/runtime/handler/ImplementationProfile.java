package dev.dual.runtime.handler;

import java.util.List;

final class ImplementationProfile {
    private ImplementationProfile() {}

    static final int DEFAULT_TOTAL_BUDGET = 24_000;

    static ContextBudget budget() {
        return new ContextBudget(DEFAULT_TOTAL_BUDGET, List.of(
                new SectionRequirement("requirement",
                        MissingPolicy.FAIL, Priority.CRITICAL,
                        new DegradationPolicy(Degradation.FULL, 0),
                        OverflowPolicy.FAIL, 10_000),
                new SectionRequirement("input_artifacts",
                        MissingPolicy.PLACEHOLDER, Priority.HIGH,
                        new DegradationPolicy(Degradation.SUMMARY, 0),
                        OverflowPolicy.DEGRADE, 6_000),
                new SectionRequirement("iteration_history",
                        MissingPolicy.SKIP, Priority.MEDIUM,
                        new DegradationPolicy(Degradation.DECISION_ONLY, 5),
                        OverflowPolicy.DEGRADE, 3_000),
                new SectionRequirement("human_feedback",
                        MissingPolicy.SKIP, Priority.HIGH,
                        new DegradationPolicy(Degradation.LATEST, 3),
                        OverflowPolicy.DEGRADE, 4_000),
                new SectionRequirement("recent_agent_outputs",
                        MissingPolicy.SKIP, Priority.LOW,
                        new DegradationPolicy(Degradation.LATEST, 2),
                        OverflowPolicy.DEGRADE, 4_000),
                new SectionRequirement("git_evidence",
                        MissingPolicy.SKIP, Priority.MEDIUM,
                        new DegradationPolicy(Degradation.DIFF_STAT, 0),
                        OverflowPolicy.DEGRADE, 3_000),
                new SectionRequirement("ledger_pointers",
                        MissingPolicy.SKIP, Priority.LOW,
                        new DegradationPolicy(Degradation.POINTER_ONLY, 0),
                        OverflowPolicy.DROP_OPTIONAL, 500)
        ));
    }
}
