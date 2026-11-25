package dev.dual.runtime.handler;

import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeResult;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HandlerRegistryTest {
    @Test
    void resolvesHandlersByKindAndType() {
        var registry = new HandlerRegistry(List.of(new FakeTaskHandler()), List.of(), List.of());

        assertEquals("fake.task", registry.task("fake.task").type());
        assertEquals("fake.task", ((TaskHandler) registry.resolve(NodeKind.TASK, "fake.task")).type());
    }

    @Test
    void failsFastForMissingHandler() {
        var registry = new HandlerRegistry(List.of(), List.of(), List.of());

        var error = assertThrows(IllegalArgumentException.class, () -> registry.resolve(NodeKind.TASK, "missing"));
        assertEquals("missing handler: missing", error.getMessage());
    }

    @Test
    void rejectsDuplicateHandlerTypes() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new HandlerRegistry(List.of(new FakeTaskHandler(), new FakeTaskHandler()), List.of(), List.of())
        );
    }

    private static final class FakeTaskHandler implements TaskHandler {
        @Override
        public String type() {
            return "fake.task";
        }

        @Override
        public NodeResult execute(TaskContext context) {
            return NodeResult.completed("completed", List.of(), Map.of());
        }
    }
}
