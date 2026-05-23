package dev.dual.core.workflow;

public record EdgeDefinition(String from, String on, String to) {
    public EdgeDefinition {
        if (from == null || from.isBlank()) {
            throw new IllegalArgumentException("edge from is required");
        }
        if (on == null || on.isBlank()) {
            throw new IllegalArgumentException("edge on is required");
        }
        if (to == null || to.isBlank()) {
            throw new IllegalArgumentException("edge to is required");
        }
    }
}
