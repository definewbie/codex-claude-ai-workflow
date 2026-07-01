package dev.dual.runtime.governance;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Typed exploration plan for one agent run.
 *
 * The plan is a bounded execution guardrail. Design, plan, intake, and review
 * runs are harness-enforced because broad discovery in those stages is a mainline
 * latency and token-governance boundary, not just observability.
 */
public final class ExplorationPlan {
    private final Map<String, Object> document;

    private ExplorationPlan(Map<String, Object> document) {
        this.document = Map.copyOf(document);
    }

    public static ExplorationPlan fromScope(
            String workstreamId,
            String stageId,
            String nodeDefinitionId,
            String nodeInstanceId,
            String iterationId,
            List<String> readScope,
            List<String> writeScope,
            AgentCapabilityCatalog capabilityCatalog
    ) {
        var stage = text(stageId);
        var intent = intent(stage);
        var maxDirectReads = maxDirectReads(stage);
        var preferredKinds = preferredKinds(stage, capabilityCatalog);

        var document = new LinkedHashMap<String, Object>();
        document.put("schemaVersion", 1);
        document.put("artifactType", "agent_exploration_plan");
        document.put("workstreamId", text(workstreamId));
        document.put("stageId", stage);
        document.put("nodeDefinitionId", text(nodeDefinitionId));
        document.put("targetNodeDefinitionId", text(nodeDefinitionId));
        document.put("nodeInstanceId", text(nodeInstanceId));
        document.put("iterationId", text(iterationId));
        document.put("explorationIntent", intent);
        document.put("preferredCapabilityKinds", preferredKinds);
        document.put("allowedDirectReadFiles", normalizedList(readScope));
        document.put("expectedEditFiles", normalizedList(writeScope));
        document.put("maxDirectReadFiles", maxDirectReads);
        document.put("forbiddenExplorationIntents", forbiddenIntents(stage));
        document.put("failureMode", failureMode(stage));
        document.put("generatedAt", Instant.now().toString());
        return new ExplorationPlan(document);
    }

    public Map<String, Object> toDocument() {
        return new LinkedHashMap<>(document);
    }

    private static List<String> preferredKinds(String stage, AgentCapabilityCatalog capabilityCatalog) {
        var available = capabilityCatalog == null ? List.<String>of() : capabilityCatalog.capabilityKinds();
        var preferred = new ArrayList<String>();
        if (List.of("intake", "design", "plan", "acceptance").contains(stage)) {
            addIfAvailable(preferred, available, "symbol_index");
        }
        if (List.of("design", "plan", "acceptance").contains(stage)) {
            addIfAvailable(preferred, available, "token_filtered_shell");
        }
        addIfAvailable(preferred, available, "direct_file_read");
        return List.copyOf(preferred);
    }

    private static void addIfAvailable(List<String> target, List<String> available, String kind) {
        if (available.contains(kind) && !target.contains(kind)) {
            target.add(kind);
        }
    }

    private static String intent(String stage) {
        return switch (stage) {
            case "intake" -> "contract_discovery";
            case "design" -> "architecture_discovery";
            case "plan" -> "change_planning";
            case "implement", "self_test" -> "edit_execution";
            case "acceptance" -> "incremental_review";
            default -> "bounded_context_use";
        };
    }

    private static int maxDirectReads(String stage) {
        return switch (stage) {
            case "intake" -> 0;
            case "acceptance" -> 4;
            case "design", "plan" -> 6;
            default -> 12;
        };
    }

    private static String failureMode(String stage) {
        return switch (stage) {
            case "intake", "design", "plan", "acceptance" -> "block";
            default -> "warn";
        };
    }

    private static List<String> forbiddenIntents(String stage) {
        if ("intake".equals(stage)) {
            return List.of("implementation_design", "full_repo_audit", "test_strategy_design");
        }
        if ("acceptance".equals(stage)) {
            return List.of("full_repo_audit");
        }
        return List.of();
    }

    private static List<String> normalizedList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        var result = new LinkedHashSet<String>();
        for (var value : values) {
            var normalized = normalize(value);
            if (!normalized.isBlank()) {
                result.add(normalized);
            }
        }
        return List.copyOf(result);
    }

    private static String normalize(String value) {
        var text = text(value);
        while (text.startsWith("./")) {
            text = text.substring(2);
        }
        return text;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
