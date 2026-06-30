package dev.dual.runtime.handler;

import dev.dual.core.execution.ArtifactContractResult;
import dev.dual.core.execution.TerminalControlResult;
import dev.dual.core.workflow.NodeResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class AgentExecutionResultFacts {
    static final String TERMINAL_CONTROL_RESULT = "agent_execution_terminal_control_result";
    static final String ARTIFACT_CONTRACT_RESULTS = "agent_execution_artifact_contract_results";
    static final String REREAD_REASONS = "reread_reasons";

    private AgentExecutionResultFacts() {
    }

    static NodeResult attach(
            NodeResult result,
            TerminalControlResult terminalControlResult,
            List<ArtifactContractResult> artifactContractResults,
            Object rereadReasons
    ) {
        var facts = new LinkedHashMap<String, Object>();
        facts.putAll(result.result());
        facts.put(TERMINAL_CONTROL_RESULT, terminalControlToMap(terminalControlResult));
        facts.put(ARTIFACT_CONTRACT_RESULTS, artifactContractResults.stream()
                .map(AgentExecutionResultFacts::artifactContractToMap)
                .toList());
        if (rereadReasons != null) {
            facts.put(REREAD_REASONS, rereadReasons);
        }
        return new NodeResult(result.outcome(), result.status(), result.outputRefs(), facts, result.errorMessage());
    }

    static TerminalControlResult terminalControlResult(NodeResult result) {
        var value = result.result().get(TERMINAL_CONTROL_RESULT);
        if (value instanceof Map<?, ?> map) {
            return new TerminalControlResult(
                    booleanValue(map.get("present")),
                    booleanValue(map.get("valid")),
                    text(map.get("decision")),
                    text(map.get("summary")),
                    text(map.get("parseError"))
            );
        }
        return TerminalControlResult.missing();
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
        return result.result().containsKey(TERMINAL_CONTROL_RESULT)
                || result.result().containsKey(ARTIFACT_CONTRACT_RESULTS)
                || (result.result().containsKey("run_metadata")
                && result.result().containsKey("process_usability"));
    }

    static Map<String, Object> terminalControlToMap(TerminalControlResult terminal) {
        var map = new LinkedHashMap<String, Object>();
        map.put("present", terminal.present());
        map.put("valid", terminal.valid());
        map.put("decision", terminal.decision());
        map.put("summary", terminal.summary());
        map.put("parseError", terminal.parseError());
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
