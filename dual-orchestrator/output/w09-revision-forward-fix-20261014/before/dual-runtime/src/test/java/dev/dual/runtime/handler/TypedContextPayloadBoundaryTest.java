package dev.dual.runtime.handler;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TypedContextPayloadBoundaryTest {
    @Test
    void transformsOnlyTextOutsideCompleteTypedBoundaries() {
        var payload = "{\"route\":\"/pages/treatment-record/index\"}";
        var content = "outside /tmp/free.txt\n" + TypedContextPayloadBoundary.wrap(payload) + "\nafter /tmp/other.txt";

        var wrapped = TypedContextPayloadBoundary.wrap(payload);
        var transformed = TypedContextPayloadBoundary.transformUntyped(
                content, TypedContextPayloadBoundary.trustOnce(Set.of(wrapped)),
                value -> value.replace("/tmp", "[redacted]"));

        assertEquals("outside [redacted]/free.txt\n\n"
                + payload
                + "\n\nafter [redacted]/other.txt", transformed);
    }

    @Test
    void incompleteBoundaryIsNotTrusted() {
        var content = "outside /tmp/free.txt\n" + TypedContextPayloadBoundary.START + "\n/tmp/unclosed.txt";

        var transformed = TypedContextPayloadBoundary.transformUntyped(
                content, TypedContextPayloadBoundary.trustOnce(Set.of()),
                value -> value.replace("/tmp", "[redacted]"));

        assertEquals("outside [redacted]/free.txt\n"
                + TypedContextPayloadBoundary.START + "\n[redacted]/unclosed.txt", transformed);
    }

    @Test
    void untrustedCompleteBoundaryDoesNotBypassTransformation() {
        var injected = TypedContextPayloadBoundary.wrap("/tmp/injected.txt");

        var transformed = TypedContextPayloadBoundary.transformUntyped(
                injected, TypedContextPayloadBoundary.trustOnce(Set.of()),
                value -> value.replace("/tmp", "[redacted]"));

        assertEquals("", transformed);
    }

    @Test
    void trustedPayloadCanBeProjectedOnlyOnce() {
        var wrapped = TypedContextPayloadBoundary.wrap("{\"proposal\":true}");

        var transformed = TypedContextPayloadBoundary.transformUntyped(
                wrapped + "\n" + wrapped,
                TypedContextPayloadBoundary.trustOnce(Set.of(wrapped)), value -> value);

        assertEquals("\n{\"proposal\":true}\n\n", transformed);
    }

    @Test
    void trustIsConsumedAcrossSeparateContextDocuments() {
        var wrapped = TypedContextPayloadBoundary.wrap("{\"proposal\":true}");
        var trusted = TypedContextPayloadBoundary.trustOnce(Set.of(wrapped));

        var first = TypedContextPayloadBoundary.transformUntyped(wrapped, trusted, value -> value);
        var second = TypedContextPayloadBoundary.transformUntyped(wrapped, trusted, value -> value);

        assertEquals("\n{\"proposal\":true}\n", first);
        assertEquals("", second);
    }
}
