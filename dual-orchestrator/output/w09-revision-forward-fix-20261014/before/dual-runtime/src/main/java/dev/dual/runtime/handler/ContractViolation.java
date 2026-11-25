package dev.dual.runtime.handler;

import java.util.Map;

/** Typed semantic failure after a strict agent result has been decoded. */
record ContractViolation(String errorCode, String message, Map<String, Object> details) {
    ContractViolation {
        details = details == null ? Map.of() : Map.copyOf(details);
    }

    static ContractViolation of(String errorCode, String message, Map<String, Object> details) {
        return new ContractViolation(errorCode, message, details);
    }
}
