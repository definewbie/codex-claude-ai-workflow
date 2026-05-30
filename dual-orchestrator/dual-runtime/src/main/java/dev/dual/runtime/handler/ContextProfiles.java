package dev.dual.runtime.handler;

import java.util.List;
import java.util.Map;

/**
 * Budget values are in tokens (estimated at ~4 chars/token).
 * softBudget and totalBudget represent token allocations.
 */
final class ContextProfiles {
    private ContextProfiles() {}

    private static final Map<String, ContextBudget> PROFILES = Map.of(
            "product_review", productReview(),
            "architecture_review", architectureReview(),
            "plan_review", planReview(),
            "implementation", implementation(),
            "acceptance", acceptance(),
            "full", full()
    );

    static ContextBudget budgetFor(String profile) {
        return PROFILES.getOrDefault(profile, full());
    }

    static boolean hasProfile(String profile) {
        return PROFILES.containsKey(profile);
    }

    static ContextBudget implementation() {
        return new ContextBudget(6_000, List.of(
                new SectionRequirement("requirement",
                        MissingPolicy.FAIL, Priority.CRITICAL,
                        new DegradationPolicy(Degradation.ADAPTIVE, 0),
                        OverflowPolicy.FAIL, 2_500),
                new SectionRequirement("input_artifacts",
                        MissingPolicy.PLACEHOLDER, Priority.HIGH,
                        new DegradationPolicy(Degradation.SUMMARY, 0),
                        OverflowPolicy.DEGRADE, 1_500),
                new SectionRequirement("iteration_history",
                        MissingPolicy.SKIP, Priority.MEDIUM,
                        new DegradationPolicy(Degradation.DECISION_ONLY, 5),
                        OverflowPolicy.DEGRADE, 750),
                new SectionRequirement("human_feedback",
                        MissingPolicy.SKIP, Priority.HIGH,
                        new DegradationPolicy(Degradation.LATEST, 3),
                        OverflowPolicy.DEGRADE, 1_000),
                new SectionRequirement("recent_agent_outputs",
                        MissingPolicy.SKIP, Priority.LOW,
                        new DegradationPolicy(Degradation.LATEST, 2),
                        OverflowPolicy.DEGRADE, 1_000),
                new SectionRequirement("git_evidence",
                        MissingPolicy.SKIP, Priority.MEDIUM,
                        new DegradationPolicy(Degradation.DIFF_STAT, 0),
                        OverflowPolicy.DEGRADE, 750),
                new SectionRequirement("ledger_pointers",
                        MissingPolicy.SKIP, Priority.LOW,
                        new DegradationPolicy(Degradation.POINTER_ONLY, 0),
                        OverflowPolicy.DROP_OPTIONAL, 125)
        ));
    }

    static ContextBudget productReview() {
        return new ContextBudget(4_000, List.of(
                new SectionRequirement("requirement",
                        MissingPolicy.FAIL, Priority.CRITICAL,
                        new DegradationPolicy(Degradation.ADAPTIVE, 0),
                        OverflowPolicy.FAIL, 2_000),
                new SectionRequirement("input_artifacts",
                        MissingPolicy.PLACEHOLDER, Priority.HIGH,
                        new DegradationPolicy(Degradation.SUMMARY, 0),
                        OverflowPolicy.DEGRADE, 1_000),
                new SectionRequirement("recent_agent_outputs",
                        MissingPolicy.SKIP, Priority.HIGH,
                        new DegradationPolicy(Degradation.LATEST, 3),
                        OverflowPolicy.DEGRADE, 750),
                new SectionRequirement("ledger_pointers",
                        MissingPolicy.SKIP, Priority.LOW,
                        new DegradationPolicy(Degradation.POINTER_ONLY, 0),
                        OverflowPolicy.DROP_OPTIONAL, 125)
        ), Map.of("max_snippet_chars", 2_000, "max_recent_agent_outputs", 3));
    }

    static ContextBudget architectureReview() {
        return new ContextBudget(5_000, List.of(
                new SectionRequirement("requirement",
                        MissingPolicy.FAIL, Priority.CRITICAL,
                        new DegradationPolicy(Degradation.ADAPTIVE, 0),
                        OverflowPolicy.FAIL, 2_000),
                new SectionRequirement("input_artifacts",
                        MissingPolicy.PLACEHOLDER, Priority.HIGH,
                        new DegradationPolicy(Degradation.SUMMARY, 0),
                        OverflowPolicy.DEGRADE, 1_000),
                new SectionRequirement("human_feedback",
                        MissingPolicy.SKIP, Priority.HIGH,
                        new DegradationPolicy(Degradation.LATEST, 5),
                        OverflowPolicy.DEGRADE, 750),
                new SectionRequirement("recent_agent_outputs",
                        MissingPolicy.SKIP, Priority.MEDIUM,
                        new DegradationPolicy(Degradation.LATEST, 3),
                        OverflowPolicy.DEGRADE, 750),
                new SectionRequirement("ledger_pointers",
                        MissingPolicy.SKIP, Priority.LOW,
                        new DegradationPolicy(Degradation.POINTER_ONLY, 0),
                        OverflowPolicy.DROP_OPTIONAL, 125)
        ), Map.of("max_snippet_chars", 3_000, "max_recent_agent_outputs", 5));
    }

    static ContextBudget planReview() {
        return new ContextBudget(5_500, List.of(
                new SectionRequirement("requirement",
                        MissingPolicy.FAIL, Priority.CRITICAL,
                        new DegradationPolicy(Degradation.ADAPTIVE, 0),
                        OverflowPolicy.FAIL, 2_000),
                new SectionRequirement("input_artifacts",
                        MissingPolicy.PLACEHOLDER, Priority.HIGH,
                        new DegradationPolicy(Degradation.SUMMARY, 0),
                        OverflowPolicy.DEGRADE, 1_000),
                new SectionRequirement("iteration_history",
                        MissingPolicy.SKIP, Priority.MEDIUM,
                        new DegradationPolicy(Degradation.DECISION_ONLY, 5),
                        OverflowPolicy.DEGRADE, 500),
                new SectionRequirement("human_feedback",
                        MissingPolicy.SKIP, Priority.HIGH,
                        new DegradationPolicy(Degradation.LATEST, 5),
                        OverflowPolicy.DEGRADE, 750),
                new SectionRequirement("recent_agent_outputs",
                        MissingPolicy.SKIP, Priority.MEDIUM,
                        new DegradationPolicy(Degradation.LATEST, 3),
                        OverflowPolicy.DEGRADE, 750),
                new SectionRequirement("ledger_pointers",
                        MissingPolicy.SKIP, Priority.LOW,
                        new DegradationPolicy(Degradation.POINTER_ONLY, 0),
                        OverflowPolicy.DROP_OPTIONAL, 125)
        ), Map.of("max_snippet_chars", 3_000));
    }

    static ContextBudget acceptance() {
        return new ContextBudget(6_500, List.of(
                new SectionRequirement("requirement",
                        MissingPolicy.FAIL, Priority.CRITICAL,
                        new DegradationPolicy(Degradation.ADAPTIVE, 0),
                        OverflowPolicy.FAIL, 2_000),
                new SectionRequirement("input_artifacts",
                        MissingPolicy.PLACEHOLDER, Priority.HIGH,
                        new DegradationPolicy(Degradation.FULL, 0),
                        OverflowPolicy.DEGRADE, 1_500),
                new SectionRequirement("iteration_history",
                        MissingPolicy.SKIP, Priority.MEDIUM,
                        new DegradationPolicy(Degradation.DECISION_ONLY, 5),
                        OverflowPolicy.DEGRADE, 500),
                new SectionRequirement("human_feedback",
                        MissingPolicy.SKIP, Priority.HIGH,
                        new DegradationPolicy(Degradation.LATEST, 5),
                        OverflowPolicy.DEGRADE, 750),
                new SectionRequirement("recent_agent_outputs",
                        MissingPolicy.SKIP, Priority.MEDIUM,
                        new DegradationPolicy(Degradation.LATEST, 3),
                        OverflowPolicy.DEGRADE, 1_000),
                new SectionRequirement("git_evidence",
                        MissingPolicy.SKIP, Priority.HIGH,
                        new DegradationPolicy(Degradation.DIFF_STAT, 0),
                        OverflowPolicy.DEGRADE, 500),
                new SectionRequirement("ledger_pointers",
                        MissingPolicy.SKIP, Priority.LOW,
                        new DegradationPolicy(Degradation.POINTER_ONLY, 0),
                        OverflowPolicy.DROP_OPTIONAL, 125),
                new SectionRequirement("task_contract",
                        MissingPolicy.FAIL, Priority.CRITICAL,
                        new DegradationPolicy(Degradation.FULL, 0),
                        OverflowPolicy.FAIL, 2_000)
        ));
    }

    static ContextBudget full() {
        return new ContextBudget(7_500, List.of(
                new SectionRequirement("requirement",
                        MissingPolicy.PLACEHOLDER, Priority.CRITICAL,
                        new DegradationPolicy(Degradation.ADAPTIVE, 0),
                        OverflowPolicy.FAIL, 2_500),
                new SectionRequirement("input_artifacts",
                        MissingPolicy.PLACEHOLDER, Priority.HIGH,
                        new DegradationPolicy(Degradation.FULL, 0),
                        OverflowPolicy.DEGRADE, 1_500),
                new SectionRequirement("iteration_history",
                        MissingPolicy.SKIP, Priority.MEDIUM,
                        new DegradationPolicy(Degradation.FULL, 0),
                        OverflowPolicy.DEGRADE, 1_000),
                new SectionRequirement("human_feedback",
                        MissingPolicy.SKIP, Priority.HIGH,
                        new DegradationPolicy(Degradation.LATEST, 5),
                        OverflowPolicy.DEGRADE, 1_000),
                new SectionRequirement("recent_agent_outputs",
                        MissingPolicy.SKIP, Priority.MEDIUM,
                        new DegradationPolicy(Degradation.LATEST, 5),
                        OverflowPolicy.DEGRADE, 1_250),
                new SectionRequirement("git_evidence",
                        MissingPolicy.SKIP, Priority.MEDIUM,
                        new DegradationPolicy(Degradation.FULL, 0),
                        OverflowPolicy.DEGRADE, 1_000),
                new SectionRequirement("ledger_pointers",
                        MissingPolicy.SKIP, Priority.LOW,
                        new DegradationPolicy(Degradation.POINTER_ONLY, 0),
                        OverflowPolicy.DROP_OPTIONAL, 125)
        ));
    }
}
