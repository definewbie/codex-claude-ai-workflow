package dev.dual.adapter.acpx;

import java.util.List;

record AcpxFailureSignal(
        String id,
        String reason,
        String message,
        boolean recoverable,
        List<Matcher> matchers
) {
    boolean matches(AcpxFailureEvidence evidence) {
        var text = evidence.combinedTextLowercase();
        return matchers.stream().anyMatch(matcher -> matcher.matches(text));
    }

    AcpxFailureClassifier.AcpxFailure toFailure() {
        return new AcpxFailureClassifier.AcpxFailure(reason, message, recoverable);
    }

    static Matcher contains(String text) {
        return new Matcher(text == null ? "" : text.toLowerCase());
    }

    record Matcher(String needle) {
        boolean matches(String text) {
            return !needle.isBlank() && text.contains(needle);
        }
    }
}
