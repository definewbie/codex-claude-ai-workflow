package dev.dual.runtime.handler;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public record NodeOutputContract(
        String nodeId,
        Set<String> requiredKeys,
        Set<String> allowedDecisions,
        Set<String> optionalKeys
) {
    public NodeOutputContract {
        requiredKeys = Set.copyOf(requiredKeys);
        allowedDecisions = Set.copyOf(allowedDecisions);
        optionalKeys = optionalKeys == null ? Set.of() : Set.copyOf(optionalKeys);
    }

    public void validate(DualControlBlock block) {
        var missing = new LinkedHashSet<String>();
        for (var key : requiredKeys) {
            if (!block.values().containsKey(key) || block.values().get(key).isBlank()) {
                missing.add(key);
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(
                    "node " + nodeId + ": dual-control missing required keys: " + missing
                            + ". Required: " + requiredKeys);
        }

        var decision = block.values().get("decision");
        if (decision != null && !allowedDecisions.contains(decision.trim())) {
            throw new IllegalArgumentException(
                    "node " + nodeId + ": decision '" + decision.trim() + "' not allowed."
                            + " Allowed: " + allowedDecisions);
        }
    }

    public Set<String> allKnownKeys() {
        var keys = new LinkedHashSet<String>();
        keys.addAll(requiredKeys);
        keys.addAll(optionalKeys);
        return Collections.unmodifiableSet(keys);
    }
}
