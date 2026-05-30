package dev.dual.runtime.handler;

record SectionRequirement(
        String section,
        MissingPolicy missingPolicy,
        Priority priority,
        DegradationPolicy degradeTo,
        OverflowPolicy overflowPolicy,
        int softBudget
) {
}
