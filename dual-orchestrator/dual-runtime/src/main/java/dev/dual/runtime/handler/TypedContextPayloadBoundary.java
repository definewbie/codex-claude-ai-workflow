package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * Marks verified typed payloads inside a context package so later free-text
 * projections can preserve their serialized bytes.
 */
public final class TypedContextPayloadBoundary {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
    static final String START = "<!-- dual:typed-payload:start -->";
    static final String END = "<!-- dual:typed-payload:end -->";

    private TypedContextPayloadBoundary() {
    }

    public static String wrap(String payload) {
        return START + "\n" + Objects.requireNonNull(payload, "payload") + "\n" + END;
    }

    public static String wrapJson(Object payload) {
        try {
            return wrap("```json\n"
                    + OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(payload)
                    + "\n```");
        } catch (IOException failure) {
            throw new IllegalArgumentException("typed context payload cannot be serialized", failure);
        }
    }

    public static TrustedPayloads trustOnce(Set<String> trustedWrappedPayloads) {
        return new TrustedPayloads(trustedWrappedPayloads);
    }

    public static String transformUntyped(
            String content,
            TrustedPayloads trustedPayloads,
            UnaryOperator<String> transform
    ) {
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(trustedPayloads, "trustedPayloads");
        Objects.requireNonNull(transform, "transform");
        var result = new StringBuilder(content.length());
        int cursor = 0;
        while (cursor < content.length()) {
            int start = content.indexOf(START, cursor);
            if (start < 0) {
                result.append(transform.apply(content.substring(cursor)));
                break;
            }
            int end = content.indexOf(END, start + START.length());
            if (end < 0) {
                // An incomplete boundary is not trusted; sanitize it as free text.
                result.append(transform.apply(content.substring(cursor)));
                break;
            }
            result.append(transform.apply(content.substring(cursor, start)));
            int boundaryEnd = end + END.length();
            var candidate = content.substring(start, boundaryEnd);
            if (trustedPayloads.consume(candidate)) {
                result.append(content, start + START.length(), end);
            }
            cursor = boundaryEnd;
        }
        return result.toString();
    }

    public static final class TrustedPayloads {
        private final Set<String> remaining;

        private TrustedPayloads(Set<String> trustedWrappedPayloads) {
            this.remaining = new HashSet<>(Objects.requireNonNull(
                    trustedWrappedPayloads, "trustedWrappedPayloads"));
        }

        private boolean consume(String candidate) {
            return remaining.remove(candidate);
        }
    }
}
