package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

import java.util.List;
import java.util.Set;

/** Prompt projection of the authoritative {@link AgentResultEnvelope} schema. */
public final class AgentResultPromptContract {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    private AgentResultPromptContract() {
    }

    public static String render(
            String workstreamId,
            String runId,
            String nodeId,
            String decisionExample,
            Set<String> allowedDecisions
    ) {
        var envelope = new AgentResultEnvelope(
                1,
                workstreamId,
                runId,
                nodeId,
                exampleResult(nodeId, decisionExample)
        );
        try {
            return """
                    Return exactly one JSON object as the final assistant message.
                    Do not use Markdown fences, XML tags, headings, commentary, or text before/after the JSON.
                    Preserve the exact key names and JSON primitive types shown below. Replace example values with factual values.
                    Allowed `result.decision` values: %s

                    %s
                    """.formatted(
                            allowedDecisions.stream().sorted().toList(),
                            OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(envelope)
                    ).stripTrailing();
        } catch (Exception failure) {
            throw new IllegalStateException("failed to render AgentResultEnvelope prompt contract", failure);
        }
    }

    private static AgentResultEnvelope.AgentResult exampleResult(String nodeId, String decision) {
        return switch (nodeId) {
            case "intake.claude_brainstorm" -> new AgentResultEnvelope.TaskContractProposalResult(
                    "task_contract_proposal",
                    decision,
                    "one short sentence",
                    false,
                    "one clear objective",
                    new AgentResultEnvelope.ProposalScope(
                            List.of("repo/relative/path.ext"),
                            List.of("explicit non-goal")
                    ),
                    List.of("observable acceptance criterion"),
                    List.of("required verification"),
                    List.of("technical or product constraint"),
                    List.of("explicit non-goal"),
                    List.of("risk for review"),
                    List.of("repo/relative/evidence.ext"),
                    List.of()
            );
            case "plan.claude_plan" -> new AgentResultEnvelope.PlanResult(
                    "plan_result",
                    decision,
                    "one short sentence",
                    "READY",
                    false,
                    List.of("read approved design and review context"),
                    List.of("plan is scoped to the approved contract"),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    "Planning only; implementation and verification happen later.",
                    emptyFailure(),
                    new AgentResultEnvelope.Plan(
                            List.of("ordered implementation step"),
                            List.of("path/to/file.ext"),
                            List.of(),
                            List.of("path/to/file.ext: intended change"),
                            List.of("verification command or check"),
                            "rollback approach",
                            List.of("None identified."),
                            List.of("review item")
                    )
            );
            case "implement.claude_self_test_report" -> new AgentResultEnvelope.SelfTestResult(
                    "self_test_report",
                    decision,
                    "one short sentence",
                    "READY",
                    List.of("command or inspection performed"),
                    List.of(),
                    List.of("specific result evidence"),
                    List.of(),
                    List.of(),
                    "short verification adequacy summary"
            );
            case "acceptance.codex_verifier_review" -> new AgentResultEnvelope.AcceptanceReviewResult(
                    "acceptance_review",
                    decision,
                    "one short sentence",
                    "ACCEPTED",
                    false,
                    List.of("review performed"),
                    List.of("specific acceptance evidence"),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    "short verification adequacy summary",
                    emptyFailure()
            );
            case "stage.claude_handoff" -> new AgentResultEnvelope.StageHandoffResult(
                    "stage_handoff",
                    decision,
                    "one short sentence",
                    "READY",
                    List.of("completed work"),
                    List.of(),
                    List.of(),
                    "short verification state",
                    List.of(),
                    "next stage",
                    "next prompt"
            );
            default -> new AgentResultEnvelope.AgentOutputResult(
                    "agent_output_contract",
                    decision,
                    "one short sentence",
                    "READY",
                    false,
                    List.of("check or review performed"),
                    List.of("specific evidence"),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    "short verification summary",
                    emptyFailure(),
                    List.of(),
                    null
            );
        };
    }

    private static AgentResultEnvelope.Failure emptyFailure() {
        return new AgentResultEnvelope.Failure("", List.of(), List.of(), List.of(), List.of(), "");
    }
}
