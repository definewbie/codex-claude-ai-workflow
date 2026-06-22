package dev.dual.core.execution;

import java.util.List;

/**
 * Typed facts collected from the adapter boundary and artifact validators.
 */
public record AgentExecutionEvidence(
        ProcessResult processResult,
        TerminalControlResult terminalControl,
        List<ArtifactContractResult> artifactContracts,
        List<AdapterSignal> adapterSignals
) {
    public AgentExecutionEvidence {
        if (processResult == null) {
            throw new IllegalArgumentException("processResult is required");
        }
        if (terminalControl == null) {
            throw new IllegalArgumentException("terminalControl is required");
        }
        artifactContracts = artifactContracts == null ? List.of() : List.copyOf(artifactContracts);
        adapterSignals = adapterSignals == null ? List.of() : List.copyOf(adapterSignals);
    }
}
