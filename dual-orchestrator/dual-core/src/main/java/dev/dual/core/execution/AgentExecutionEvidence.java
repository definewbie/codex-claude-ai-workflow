package dev.dual.core.execution;

import java.util.List;

/**
 * Typed facts collected from the adapter boundary and artifact validators.
 */
public record AgentExecutionEvidence(
        ProcessResult processResult,
        AgentResultContractResult agentResultContract,
        List<ArtifactContractResult> artifactContracts,
        List<AdapterSignal> adapterSignals,
        List<PermissionAssessment> permissionAssessments
) {
    public enum PermissionDisposition { REQUIRED, PROVEN_NONESSENTIAL, UNKNOWN }
    public record PermissionAssessment(AdapterSignal signal, dev.dual.core.contract.TaskContract.Reference contract,
                                       List<String> criterionIds, List<dev.dual.core.contract.TaskContract.Reference> evidenceRefs,
                                       String ruleIdentity, PermissionDisposition disposition, String declarationId,
                                       String runId, String executionId) {
        public PermissionAssessment {
            criterionIds = criterionIds == null ? List.of() : List.copyOf(criterionIds);
            evidenceRefs = evidenceRefs == null ? List.of() : List.copyOf(evidenceRefs);
        }
        public boolean proves(AdapterSignal denied) {
            return denied.equals(signal) && denied.operationRef() != null && denied.operationRef().complete()
                    && denied.sessionId().equals(denied.operationRef().sessionId())
                    && denied.observedSequence() == denied.operationRef().decisionSequence()
                    && disposition == PermissionDisposition.PROVEN_NONESSENTIAL && contract != null
                    && "permission_nonessential.v1".equals(ruleIdentity)
                    && !criterionIds.isEmpty() && criterionIds.stream().allMatch(id -> id != null && !id.isBlank())
                    && new java.util.HashSet<>(criterionIds).size() == criterionIds.size()
                    && !evidenceRefs.isEmpty() && runId != null && !runId.isBlank()
                    && executionId != null && !executionId.isBlank();
        }
    }
    public AgentExecutionEvidence(ProcessResult process, AgentResultContractResult result,
                                  List<ArtifactContractResult> artifacts, List<AdapterSignal> signals) {
        this(process, result, artifacts, signals, List.of());
    }
    /** One adapter-observed call. Reported status/exit are not a measured process exit. */
    public record ToolObservation(String sessionId, String toolCallId, String toolName, String kind,
                                  com.fasterxml.jackson.databind.JsonNode input,
                                  com.fasterxml.jackson.databind.JsonNode output,
                                  String nativeStatus, Integer reportedExitCode, Integer actualExitCode,
                                  String actualExitSource, boolean invalid,
                                  List<com.fasterxml.jackson.databind.JsonNode> rawUpdates, String promptId) {
        public ToolObservation(String sessionId, String toolCallId, String toolName, String kind,
                               com.fasterxml.jackson.databind.JsonNode input, com.fasterxml.jackson.databind.JsonNode output,
                               String nativeStatus, Integer reportedExitCode, Integer actualExitCode, String actualExitSource,
                               boolean invalid, List<com.fasterxml.jackson.databind.JsonNode> rawUpdates) {
            this(sessionId, toolCallId, toolName, kind, input, output, nativeStatus, reportedExitCode, actualExitCode,
                    actualExitSource, invalid, rawUpdates, "");
        }
        public ToolObservation {
            promptId = promptId == null ? "" : promptId;
            input = input == null ? null : input.deepCopy();
            output = output == null ? null : output.deepCopy();
            rawUpdates = rawUpdates == null ? List.of() : rawUpdates.stream()
                    .<com.fasterxml.jackson.databind.JsonNode>map(node -> node.deepCopy()).toList();
        }
        public boolean hasExecutionResult() {
            return !invalid && sessionId != null && !sessionId.isBlank() && toolCallId != null && !toolCallId.isBlank()
                    && input != null && !input.isNull() && output != null && !output.isNull()
                    && ("completed".equals(nativeStatus) || "failed".equals(nativeStatus));
        }
    }
    public AgentExecutionEvidence {
        if (processResult == null) {
            throw new IllegalArgumentException("processResult is required");
        }
        if (agentResultContract == null) {
            throw new IllegalArgumentException("agentResultContract is required");
        }
        artifactContracts = artifactContracts == null ? List.of() : List.copyOf(artifactContracts);
        adapterSignals = adapterSignals == null ? List.of() : List.copyOf(adapterSignals);
        permissionAssessments = permissionAssessments == null ? List.of() : List.copyOf(permissionAssessments);
    }
}
