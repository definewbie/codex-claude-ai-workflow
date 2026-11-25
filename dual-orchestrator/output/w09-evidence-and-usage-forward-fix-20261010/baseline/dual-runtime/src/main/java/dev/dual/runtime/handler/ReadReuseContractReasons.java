package dev.dual.runtime.handler;

import java.util.List;

/**
 * Central catalog of reasons an agent may re-read a file already covered by the
 * read-reuse contract. Shared by {@code RepoScopeGovernance} (which writes the
 * contract into {@code read-reuse-contract.json}) and the artifact-contract
 * validator (which validates declared {@code reread_reasons}) so the allowed
 * set has exactly one source of truth.
 */
public final class ReadReuseContractReasons {
    public static final List<String> ALLOWED_REPEATED_READ_REASONS = List.of(
            "file changed after prior read",
            "exact current content is needed before editing",
            "previous evidence does not cover the selected stage");

    static boolean isAllowed(String reason) {
        return reason != null && ALLOWED_REPEATED_READ_REASONS.contains(reason);
    }

    private ReadReuseContractReasons() {
    }
}
