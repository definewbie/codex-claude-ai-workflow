package dev.dual.runtime.handler;

import dev.dual.core.workflow.NodeKind;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

public final class HandlerRegistry {
    private final Map<String, TaskHandler> taskHandlers;
    private final Map<String, DecisionHandler> decisionHandlers;
    private final Map<String, GateHandler> gateHandlers;

    public HandlerRegistry(
            List<TaskHandler> taskHandlers,
            List<DecisionHandler> decisionHandlers,
            List<GateHandler> gateHandlers
    ) {
        this.taskHandlers = index(taskHandlers == null ? List.of() : taskHandlers, TaskHandler::type);
        this.decisionHandlers = index(decisionHandlers == null ? List.of() : decisionHandlers, DecisionHandler::type);
        this.gateHandlers = index(gateHandlers == null ? List.of() : gateHandlers, GateHandler::type);
    }

    public Object resolve(NodeKind kind, String type) {
        if (kind == null) {
            throw new IllegalArgumentException("kind is required");
        }
        return switch (kind) {
            case TASK -> task(type);
            case DECISION -> decision(type);
            case GATE -> gate(type);
        };
    }

    public TaskHandler task(String type) {
        var handler = taskHandlers.get(requiredType(type));
        if (handler == null) {
            throw new IllegalArgumentException("missing handler: " + type);
        }
        return handler;
    }

    public DecisionHandler decision(String type) {
        var handler = decisionHandlers.get(requiredType(type));
        if (handler == null) {
            throw new IllegalArgumentException("missing handler: " + type);
        }
        return handler;
    }

    public GateHandler gate(String type) {
        var handler = gateHandlers.get(requiredType(type));
        if (handler == null) {
            throw new IllegalArgumentException("missing handler: " + type);
        }
        return handler;
    }

    private static String requiredType(String type) {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("handler type is required");
        }
        return type;
    }

    private static <T> Map<String, T> index(List<T> handlers, Function<T, String> typeExtractor) {
        var indexed = new LinkedHashMap<String, T>();
        for (T handler : handlers) {
            if (handler == null) {
                throw new IllegalArgumentException("handler is required");
            }
            var type = requiredType(typeExtractor.apply(handler));
            if (indexed.containsKey(type)) {
                throw new IllegalArgumentException("duplicate handler: " + type);
            }
            indexed.put(type, handler);
        }
        return Map.copyOf(indexed);
    }
}
