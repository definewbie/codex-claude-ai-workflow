package dev.dual.runtime.handler;

import java.util.List;

record ContextBudget(
        int totalBudget,
        List<SectionRequirement> sections
) {
}
