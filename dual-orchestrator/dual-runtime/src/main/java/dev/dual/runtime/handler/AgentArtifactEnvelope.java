package dev.dual.runtime.handler;

public record AgentArtifactEnvelope(
        String type,
        int version,
        String json
) {}
