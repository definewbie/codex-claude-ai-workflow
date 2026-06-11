package dev.dual.ledger.file;

final class FailureReasonSemantics {
    private FailureReasonSemantics() {
    }

    static boolean isArtifactContractFailure(String failureReason) {
        if (failureReason == null || failureReason.isBlank()) {
            return false;
        }
        return failureReason.contains("DUAL_CONTROL")
                || failureReason.contains("CONTRACT")
                || "AGENT_OUTPUT_CONTRACT_VIOLATION".equals(failureReason);
    }
}
