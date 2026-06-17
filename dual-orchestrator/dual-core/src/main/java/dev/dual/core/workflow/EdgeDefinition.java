package dev.dual.core.workflow;

public record EdgeDefinition(String from, String on, String to, EdgeCondition condition, TransitionMetadata metadata) {
    public EdgeDefinition(String from, String on, String to) {
        this(from, on, to, EdgeCondition.unconditional(), TransitionMetadata.normal());
    }

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
        if (condition == null) {
            condition = EdgeCondition.unconditional();
        }
        if (metadata == null) {
            metadata = TransitionMetadata.normal();
        }
    }

    public EdgeDefinition(String from, String on, String to, EdgeCondition condition) {
        this(from, on, to, condition, TransitionMetadata.normal());
    }

}
