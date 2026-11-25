package dev.dual.runtime.handler;

import java.util.List;
import java.util.Map;

record ContextBudget(
        int totalBudget,
        List<SectionRequirement> sections,
        Map<String, Object> configOverrides
) {
    ContextBudget(int totalBudget, List<SectionRequirement> sections) {
        this(totalBudget, sections, Map.of());
    }
}
