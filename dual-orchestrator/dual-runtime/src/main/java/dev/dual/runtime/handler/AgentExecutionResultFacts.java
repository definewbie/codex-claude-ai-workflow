package dev.dual.runtime.handler;

import dev.dual.core.execution.ArtifactContractResult;
import dev.dual.core.execution.AgentResultContractResult;
import dev.dual.core.workflow.NodeResult;
import dev.dual.core.workflow.NodeStatus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AgentExecutionResultFacts {
    static final String AGENT_RESULT_CONTRACT = "agent_execution_result_contract";
    static final String ARTIFACT_CONTRACT_RESULTS = "agent_execution_artifact_contract_results";
    static final String REREAD_REASONS = "reread_reasons";

    private AgentExecutionResultFacts() {
    }

    /** Closed, machine-owned input rejection. No provider result or arbitrary metadata can enter here. */
    public static NodeResult inputPreparationFailed(String reason, String code, List<String> details) {
        return inputPreparationFailed(reason, code, details, null);
    }

    public static NodeResult inputPreparationFailed(String reason, String code, List<String> details,
            dev.dual.ledger.file.ContractQualificationChecks.ContinuationFailure diagnostic) {
        if (reason == null || reason.isBlank() || code == null || code.isBlank()
                || details == null || details.isEmpty()) {
            throw new IllegalArgumentException("input rejection requires reason, code and details");
        }
        var errors = List.copyOf(details);
        var facts = new LinkedHashMap<String, Object>(Map.of(
                "reason", reason, "failure_code", code, "errors", errors,
                "input_preparation_failure", Map.of("reason", reason, "code", code, "details", errors,
                        "providerInvoked", false, "retryPolicy", "MANUAL_ONLY")));
        if (diagnostic != null) {
            if (!TaskContractProjectionVerifier.FAILURE_OUTCOME.equals(reason) || !code.equals(diagnostic.failureCode()))
                throw new IllegalArgumentException("diagnostic cannot replace the original preparation cause");
            facts.put("continuation_failure", diagnostic.toMap());
        }
        return new NodeResult(reason, NodeStatus.FAILED, List.of(), facts, String.join("; ", errors));
    }

    /** Reject contradictory facts instead of letting a phase marker become a publication bypass. */
    static boolean inputPreparationFailed(NodeResult result) {
        if (!result.result().containsKey("input_preparation_failure")) return false;
        if (!(result.result().get("input_preparation_failure") instanceof Map<?, ?> failure)
                || !(failure.get("reason") instanceof String reason)
                || !(failure.get("code") instanceof String code)
                || !(failure.get("details") instanceof List<?> details)
                || details.stream().anyMatch(item -> !(item instanceof String))) {
            throw new IllegalArgumentException("invalid input preparation failure fact");
        }
        var diagnostic = result.result().containsKey("continuation_failure")
                ? dev.dual.ledger.file.ContractQualificationChecks.continuation(result.result().get("continuation_failure")) : null;
        var expected = inputPreparationFailed(reason, code, details.stream().map(String.class::cast).toList(), diagnostic);
        var base = new LinkedHashMap<String, Object>(result.result());
        // Only the existing R03 consumer may validate these content bindings; they confer no provider eligibility.
        if (base.containsKey("completion_ref") || base.containsKey("completion_sha256")) {
            if (!(base.remove("completion_ref") instanceof String ref) || ref.isBlank()
                    || !(base.remove("completion_sha256") instanceof String sha) || !sha.matches("[a-f0-9]{64}"))
                throw new IllegalArgumentException("invalid preparation completion binding");
        }
        if (!dev.dual.ledger.file.ContractQualificationChecks.identity(expected).equals(
                dev.dual.ledger.file.ContractQualificationChecks.identity(new NodeResult(result.outcome(), result.status(), result.outputRefs(), base, result.errorMessage())))) {
            throw new IllegalArgumentException("input preparation failure contradicts node result or execution evidence");
        }
        return true;
    }

    /** Carry an acquisition rejection to publication using the existing terminal contract fact. */
    public static NodeResult rejectAgentResult(NodeResult result, String message) {
        return attach(new NodeResult("AGENT_OUTPUT_CONTRACT_VIOLATION", NodeStatus.FAILED,
                        result.outputRefs(), result.result(), message),
                AgentResultContractResult.invalid(message), List.of(), null);
    }

    static NodeResult attach(
            NodeResult result,
            AgentResultContractResult agentResultContract,
            List<ArtifactContractResult> artifactContractResults,
            Object rereadReasons
    ) {
        var facts = new LinkedHashMap<String, Object>();
        facts.putAll(result.result());
        facts.put(AGENT_RESULT_CONTRACT, agentResultContractToMap(agentResultContract));
        facts.put(ARTIFACT_CONTRACT_RESULTS, artifactContractResults.stream()
                .map(AgentExecutionResultFacts::artifactContractToMap)
                .toList());
        if (rereadReasons != null) {
            facts.put(REREAD_REASONS, rereadReasons);
        }
        return new NodeResult(result.outcome(), result.status(), result.outputRefs(), facts, result.errorMessage());
    }

    static AgentResultContractResult agentResultContract(NodeResult result) {
        var value = result.result().get(AGENT_RESULT_CONTRACT);
        if (value instanceof Map<?, ?> map) {
            return new AgentResultContractResult(
                    booleanValue(map.get("present")),
                    booleanValue(map.get("valid")),
                    text(map.get("decision")),
                    text(map.get("summary")),
                    text(map.get("validationError"))
            );
        }
        return AgentResultContractResult.missing();
    }

    static List<ArtifactContractResult> artifactContractResults(NodeResult result) {
        var value = result.result().get(ARTIFACT_CONTRACT_RESULTS);
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        var results = new ArrayList<ArtifactContractResult>();
        for (var item : list) {
            if (item instanceof Map<?, ?> map) {
                results.add(new ArtifactContractResult(
                        text(map.get("contractType")),
                        booleanValue(map.get("required")),
                        booleanValue(map.get("present")),
                        booleanValue(map.get("valid")),
                        text(map.get("path")),
                        text(map.get("errorCode")),
                        text(map.get("errorMessage"))
                ));
            }
        }
        return List.copyOf(results);
    }

    static Object rereadReasons(NodeResult result) {
        return result.result().get(REREAD_REASONS);
    }

    static boolean present(NodeResult result) {
        return result.result().containsKey(AGENT_RESULT_CONTRACT)
                || result.result().containsKey(ARTIFACT_CONTRACT_RESULTS)
                || (result.result().containsKey("run_metadata")
                && result.result().containsKey("process_usability"));
    }

    static Map<String, Object> agentResultContractToMap(AgentResultContractResult result) {
        var map = new LinkedHashMap<String, Object>();
        map.put("present", result.present());
        map.put("valid", result.valid());
        map.put("decision", result.decision());
        map.put("summary", result.summary());
        map.put("validationError", result.validationError());
        return map;
    }

    static Map<String, Object> artifactContractToMap(ArtifactContractResult artifact) {
        var map = new LinkedHashMap<String, Object>();
        map.put("contractType", artifact.contractType());
        map.put("required", artifact.required());
        map.put("present", artifact.present());
        map.put("valid", artifact.valid());
        map.put("path", artifact.path());
        map.put("errorCode", artifact.errorCode());
        map.put("errorMessage", artifact.errorMessage());
        return map;
    }

    private static boolean booleanValue(Object value) {
        return value instanceof Boolean bool && bool;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
