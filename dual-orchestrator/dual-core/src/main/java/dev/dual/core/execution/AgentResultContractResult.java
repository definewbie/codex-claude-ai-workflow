package dev.dual.core.execution;

/** Validation result for the strict final {@code AgentResultEnvelope}. */
public record AgentResultContractResult(
        boolean present,
        boolean valid,
        String decision,
        String summary,
        String validationError
) {
    public AgentResultContractResult {
        decision = decision == null ? "" : decision;
        summary = summary == null ? "" : summary;
        validationError = validationError == null ? "" : validationError;
        if (valid && !present) {
            throw new IllegalArgumentException("valid agent result requires present=true");
        }
    }

    public static AgentResultContractResult valid(String decision) {
        return new AgentResultContractResult(true, true, decision, "", "");
    }

    public static AgentResultContractResult missing() {
        return new AgentResultContractResult(false, false, "", "", "agent result is missing");
    }

    public static AgentResultContractResult invalid(String validationError) {
        return new AgentResultContractResult(true, false, "", "", validationError);
    }
}
