package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import dev.dual.core.contract.TaskContract;

import java.util.ArrayList;
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
                    Every JSON object is closed. Do not add keys that are not declared for that object.
                    Allowed `result.decision` values: %s
                    %s
                    %s
                    %s

                    %s
                    """.formatted(
                            allowedDecisions.stream().sorted().toList(),
                            rereadReasonInstruction(nodeId),
                            verificationRemediationInstruction(nodeId),
                            contractRevisionInstruction(nodeId),
                            OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(envelope)
                    ).stripTrailing();
        } catch (Exception failure) {
            throw new IllegalStateException("failed to render AgentResultEnvelope prompt contract", failure);
        }
    }

    private static String contractRevisionInstruction(String nodeId) {
        if ("intake.claude_brainstorm".equals(nodeId)) {
            return "Each task-contract clause must contain stable string fields `id` and `text`. "
                    + "On an initial intake, `result.revision_responses` must be `[]`. "
                    + "On a revision, copy every routed Contract Obligation ID exactly once into "
                    + "`review_obligation_id` and list one or more clause IDs in `resolved_by_clause_ids`. "
                    + "Preserve every existing clause not named by the current `revision_responses` by copying "
                    + "its `id` and `text` character-for-character from the verified previous proposal, including "
                    + "whitespace, punctuation, capitalization, and Markdown backticks. Formatting normalization "
                    + "is a modification and is prohibited unless that clause ID is declared in "
                    + "`resolved_by_clause_ids`. "
                    + "List every added or modified clause ID in at least one `resolved_by_clause_ids` entry, "
                    + "and do not delete existing clause IDs. "
                    + closedItemFields(
                    "result.revision_responses",
                    new AgentResultEnvelope.RevisionResponse("CO-1", List.of("AC-1")));
        }
        if ("intake.codex_product_review".equals(nodeId)) {
            return "For NEEDS_REVISION with non-empty `result.failure.contract_gaps`, "
                    + "`result.contract_obligations` must contain one object per contract obligation "
                    + "with stable string fields `id` and `text`. Otherwise it must be `[]`. "
                    + closedItemFields(
                    "result.contract_obligations",
                    new AgentResultEnvelope.ReviewObligation("CO-1", "binding contract obligation"));
        }
        if (ContractRegistry.forNode(nodeId).emitsArtifact("agent_output_contract")) {
            return "For this node, `result.contract_obligations` must remain exactly `[]`; "
                    + "contract obligations are produced only by intake contract review.";
        }
        return "";
    }

    private static String rereadReasonInstruction(String nodeId) {
        var contract = ContractRegistry.forNode(nodeId);
        if (!contract.emitsArtifact("agent_output_contract")) {
            return "";
        }
        if (!ContractRegistry.supportsRereadReasonField(nodeId)) {
            return "For this node, `result.reread_reasons` must remain exactly `[]`.";
        }
        return "Each `result.reread_reasons` entry must use string fields; `reason` must be one of "
                + ReadReuseContractReasons.ALLOWED_REPEATED_READ_REASONS + ". "
                + closedItemFields(
                "result.reread_reasons",
                new AgentResultEnvelope.RereadReason(
                        "repo/file.ext",
                        ReadReuseContractReasons.ALLOWED_REPEATED_READ_REASONS.stream().sorted().findFirst().orElseThrow(),
                        "design",
                        "specific evidence"));
    }

    private static String closedItemFields(String fieldPath, Object sample) {
        var tree = OBJECT_MAPPER.valueToTree(sample);
        if (!tree.isObject()) {
            throw new IllegalArgumentException("closed item sample must serialize as an object: " + fieldPath);
        }
        var fields = new ArrayList<String>();
        tree.fieldNames().forEachRemaining(fields::add);
        return "Each `" + fieldPath + "` item is a closed object with exactly these fields: "
                + fields + ". No other keys are permitted.";
    }

    private static String verificationRemediationInstruction(String nodeId) {
        if (!"acceptance.codex_verifier_review".equals(nodeId)) {
            return "";
        }
        return "For a REJECT with non-empty `result.failure.missing_tests`, set "
                + "`result.failure.verification_remediation` to `EVIDENCE_COLLECTION` only when "
                + "existing test/verification evidence must be collected, or `TEST_CODE_CHANGE` when "
                + "a test source change is required. Do not infer this from prose in downstream nodes.";
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
                            List.of(
                                    new TaskContract.ScopeEntry(
                                            "repo/relative/existing.ext",
                                            TaskContract.ScopeOperation.MODIFY),
                                    new TaskContract.ScopeEntry(
                                            "repo/relative/new.ext",
                                            TaskContract.ScopeOperation.CREATE)
                            ),
                            List.of("explicit non-goal")
                    ),
                    List.of(
                            new AgentResultEnvelope.ContractClause("AC-1", "observable acceptance criterion"),
                            new AgentResultEnvelope.ContractClause("AC-2", "second observable criterion")),
                    List.of(new AgentResultEnvelope.ContractClause("TR-1", "required verification")),
                    List.of(new AgentResultEnvelope.ContractClause("C-1", "technical or product constraint")),
                    List.of(new AgentResultEnvelope.ContractClause("NG-1", "explicit non-goal")),
                    List.of(new AgentResultEnvelope.ContractClause("R-1", "risk for review")),
                    List.of("repo/relative/evidence.ext"),
                    List.of(),
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
                    List.of()
            );
        };
    }

    private static AgentResultEnvelope.Failure emptyFailure() {
        return new AgentResultEnvelope.Failure("", AgentResultEnvelope.VerificationRemediation.NONE,
                List.of(), List.of(), List.of(), List.of(), "");
    }
}
