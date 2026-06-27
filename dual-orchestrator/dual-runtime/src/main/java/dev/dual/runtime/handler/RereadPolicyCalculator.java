package dev.dual.runtime.handler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Harness-computed re-read policy: decides whether re-reading a reusable file
 * is justified, from objective facts only — actual reads, the read-reuse
 * contract (reusable + invalidated), and the expected edit set.
 *
 * <p>It deliberately does NOT consult agent self-reported {@code reread_reasons}
 * as the sole justification; that field remains a supplementary explanation
 * only. This removes the prior failure mode where the harness delegated
 * execution-fact reconciliation to the LLM.</p>
 */
final class RereadPolicyCalculator {

    record Decision(String path, boolean allowed, String reason) {
    }

    record Result(List<Decision> decisions, List<String> denied) {
    }

    private RereadPolicyCalculator() {
    }

    static Result compute(Collection<String> actualReads,
                          Set<String> reusableReadFiles,
                          Set<String> invalidatedReadFiles,
                          Set<String> expectedEditFiles) {
        if (actualReads == null) {
            return new Result(List.of(), List.of());
        }
        var reusable = reusableReadFiles == null ? Set.<String>of() : reusableReadFiles;
        if (reusable.isEmpty()) {
            return new Result(List.of(), List.of());
        }
        var invalidated = invalidatedReadFiles == null ? Set.<String>of() : invalidatedReadFiles;
        var expectedEdits = expectedEditFiles == null ? Set.<String>of() : expectedEditFiles;

        var decisions = new ArrayList<Decision>();
        var denied = new ArrayList<String>();
        var ordered = new LinkedHashSet<String>();
        for (var read : actualReads) {
            var normalized = normalize(read);
            if (!normalized.isBlank()) {
                ordered.add(normalized);
            }
        }
        for (var path : ordered) {
            if (!reusable.contains(path)) {
                continue;
            }
            boolean allowed;
            String reason;
            if (expectedEdits.contains(path)) {
                allowed = true;
                reason = "exact current content is needed before editing";
            } else if (invalidated.contains(path)) {
                allowed = true;
                reason = "file changed after prior read";
            } else {
                allowed = false;
                reason = "reusable file re-read without an expected edit or invalidation";
                denied.add(path);
            }
            decisions.add(new Decision(path, allowed, reason));
        }
        return new Result(List.copyOf(decisions), List.copyOf(denied));
    }

    private static String normalize(String value) {
        var text = value == null ? "" : value.trim();
        while (text.startsWith("./")) {
            text = text.substring(2);
        }
        return text;
    }
}
