package dev.dual.runtime.handler;

import java.util.Map;

public final class HumanGateSemantics {
    private HumanGateSemantics() {
    }

    public static String purposeForDefinitionId(String nodeDefinitionId) {
        return switch (nodeDefinitionId) {
            case "gate.human_requirement_review" -> "requirement";
            case "gate.contract_review" -> "contract";
            case "gate.human_design_approval" -> "design";
            case "gate.human_plan_approval" -> "plan";
            case "gate.human_acceptance" -> "acceptance";
            case "gate.human_next_stage_approval" -> "next_stage";
            default -> "general";
        };
    }

    public static boolean terminalGate(String nodeDefinitionId) {
        return "gate.human_next_stage_approval".equals(nodeDefinitionId);
    }

    public static String purpose(Map<String, Object> config, String nodeDefinitionId) {
        var configured = config.get("gate_purpose");
        if (configured instanceof String text && !text.isBlank()) {
            return text;
        }
        return purposeForDefinitionId(nodeDefinitionId);
    }

    public static boolean terminal(Map<String, Object> config, String nodeDefinitionId) {
        var configured = config.get("terminal_gate");
        if (configured instanceof Boolean value) {
            return value;
        }
        return terminalGate(nodeDefinitionId);
    }
}
