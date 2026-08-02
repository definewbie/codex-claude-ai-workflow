package dev.dual.adapter.acpx;

import dev.dual.core.execution.AdapterSignal;

import java.util.List;

record AcpxFailureSignal(
        String id,
        AcpxFailureReason reason,
        int priority,
        String message,
        boolean recoverable,
        boolean interruptRunningProcess,
        List<Matcher> matchers
) {
    boolean matches(AcpxFailureEvidence evidence) {
        var text = evidence.combinedTextLowercase();
        return matchers.stream().anyMatch(matcher -> matcher.matches(text));
    }

    AdapterSignal toAdapterSignal(long sequence, String source) {
        return new AdapterSignal(reason.adapterSignalType(), message, sequence, source, "", false);
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
