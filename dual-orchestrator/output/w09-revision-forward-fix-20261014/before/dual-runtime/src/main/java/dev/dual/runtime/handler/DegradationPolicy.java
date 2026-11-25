package dev.dual.runtime.handler;

record DegradationPolicy(
        Degradation strategy,
        int maxItems
) {
}
