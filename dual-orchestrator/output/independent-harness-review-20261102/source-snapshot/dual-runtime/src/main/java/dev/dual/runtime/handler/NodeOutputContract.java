package dev.dual.runtime.handler;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public record NodeOutputContract(
        String nodeId,
        Set<String> requiredKeys,
        Set<String> allowedDecisions,
        Set<String> optionalKeys,
        Set<String> outputArtifactTypes,
        Set<String> supportedArtifactFields
) {
    public NodeOutputContract(
            String nodeId,
            Set<String> requiredKeys,
            Set<String> allowedDecisions,
            Set<String> optionalKeys,
            Set<String> outputArtifactTypes
    ) {
        this(nodeId, requiredKeys, allowedDecisions, optionalKeys, outputArtifactTypes, Set.of());
    }

    public NodeOutputContract(
            String nodeId,
            Set<String> requiredKeys,
            Set<String> allowedDecisions,
            Set<String> optionalKeys
    ) {
        this(nodeId, requiredKeys, allowedDecisions, optionalKeys, Set.of(), Set.of());
    }

    public NodeOutputContract {
        requiredKeys = Set.copyOf(requiredKeys);
        allowedDecisions = Set.copyOf(allowedDecisions);
        optionalKeys = optionalKeys == null ? Set.of() : Set.copyOf(optionalKeys);
        outputArtifactTypes = outputArtifactTypes == null ? Set.of() : Set.copyOf(outputArtifactTypes);
        supportedArtifactFields = supportedArtifactFields == null ? Set.of() : Set.copyOf(supportedArtifactFields);
    }

    public Set<String> allKnownKeys() {
        var keys = new LinkedHashSet<String>();
        keys.addAll(requiredKeys);
        keys.addAll(optionalKeys);
        return Collections.unmodifiableSet(keys);
    }

    public boolean emitsArtifact(String artifactType) {
        return outputArtifactTypes.contains(artifactType);
    }

    public boolean supportsArtifactField(String fieldName) {
        return supportedArtifactFields.contains(fieldName);
    }
}
