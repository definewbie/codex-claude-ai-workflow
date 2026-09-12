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
