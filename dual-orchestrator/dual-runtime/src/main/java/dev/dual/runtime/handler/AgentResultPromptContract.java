package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Prompt projection of the authoritative {@link AgentResultEnvelope} schema. */
public final class AgentResultPromptContract {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    private static final String CANONICAL_SCOPE_PATH_REQUIREMENT =
            "Each scope path must name one exact file, not a directory; it must be repository-relative, "
                    + "must not end with `/`, and must already be canonical (no `./`, `..`, repeated separators, "
                    + "backslashes, leading/trailing whitespace, `.git`, or `.agent` segments). ";

    private AgentResultPromptContract() {
    }

    public static String render(
            String workstreamId,
            String runId,
            String nodeId,
            String decisionExample,
            Set<String> allowedDecisions
    ) {
        return render(workstreamId, runId, nodeId, decisionExample, allowedDecisions,
                ProposalEmissionContext.initial());
    }

    public static String render(
            String workstreamId,
            String runId,
            String nodeId,
            String decisionExample,
            Set<String> allowedDecisions,
            ProposalEmissionContext proposalContext
    ) {
        var exampleResult = exampleResult(nodeId, decisionExample, proposalContext);
        var envelope = new AgentResultEnvelope(
                1,
                workstreamId,
                runId,
                nodeId,
                exampleResult
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

                    %s
                    """.formatted(
                            allowedDecisions.stream().sorted().toList(),
                            verificationRemediationAllowedValues(exampleResult),
                            rereadReasonInstruction(nodeId),
                            verificationRemediationInstruction(nodeId, exampleResult),
                            contractRevisionInstruction(nodeId, proposalContext) + authoredRevisionInstruction(exampleResult),
                            OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(envelope)
                    ).stripTrailing();
        } catch (Exception failure) {
            throw new IllegalStateException("failed to render AgentResultEnvelope prompt contract", failure);
        }
    }

    private static String authoredRevisionInstruction(AgentResultEnvelope.AgentResult result) {
        if (!(result instanceof AgentResultEnvelope.DesignResult) && !(result instanceof AgentResultEnvelope.PlanResult)) return "";
        return "\n`result.revision_responses` is required: return [] when there are no active authored obligations. "
                + "Otherwise cover every active obligation_id from Required Authored Context exactly once; "
                + "do not add unknown IDs or omit unresolved obligations. Each entry has exactly obligation_id, "
                + "disposition (ADDRESSED or NOT_ADDRESSED), and non-empty evidence. "
                + "Use NOT_ADDRESSED when unresolved; ADDRESSED is a claim for review, not an approval or closure. "
                + "Preserve all previously required behavior and the approved contract while making this revision."
                + (result instanceof AgentResultEnvelope.PlanResult
                    ? "\nedit_files must contain unique canonical paths within the approved TaskContract scope. "
                        + CANONICAL_SCOPE_PATH_REQUIREMENT + "A subset is allowed; reference_files do not authorize edits. "
                    : "")
                + closedItemFields("result.revision_responses", new AgentResultEnvelope.AuthoredRevisionResponse(
                        "sha256:" + "0".repeat(64) + "#human-note:0",
                        AgentResultEnvelope.AuthoredRevisionDisposition.NOT_ADDRESSED, "Explain what remains unresolved"));
    }

    private static String contractRevisionInstruction(String nodeId, ProposalEmissionContext context) {
        if ("intake.claude_brainstorm".equals(nodeId)) {
            if (context.revision()) {
                return "Return only `task_contract_revision_patch`; do not return or copy a full proposal. "
                        + "The patch is bound to base proposal id `" + context.baseProposalId()
                        + "` and sha256 `" + context.baseProposalSha256() + "`. "
                        + "Cover each authoritative routed obligation exactly once. Preserve its id and implement its text: "
                        + json(context.obligations())
                        + ". Only clause ADD/REPLACE, exact scope ADD, and evidence-ref ADD are supported. "
                        + "No DELETE, section move, scope removal, objective/open-question change, JSON Pointer, "
                        + "or arbitrary field patch is allowed. `scope_additions` uses exact repository-relative "
                        + "paths. " + CANONICAL_SCOPE_PATH_REQUIREMENT
                        + "Dual derives CREATE/MODIFY from the effective worktree. `section` is authoritative; clause IDs are opaque "
                        + "and their prefixes do not determine section. "
                        + "Across the entire `result.obligation_patches` array, each `clause_changes[].id`, "
                        + "`scope_additions[].path`, and `evidence_ref_additions[]` target must appear at most once. "
                        + "When one evidence ref supports multiple obligations, add it in one most relevant patch only; "
                        + "use each obligation's `resolved_by_clause_ids` to express coverage instead of adding the ref again. "
                        + closedItemFields("result.obligation_patches",
                        new AgentResultEnvelope.ObligationPatch(
                                "CO-1", List.of("AC-1"),
                                List.of(new AgentResultEnvelope.ClauseChange(
                                        AgentResultEnvelope.ClauseChangeOperation.REPLACE,
                                        AgentResultEnvelope.ClauseSection.ACCEPTANCE_CRITERIA,
                                        "AC-1", "replacement text")),
                                List.of(), List.of()));
            }
            return "`result.scope.included` is the exhaustive authorization set for permitted repository changes; "
                    + "each entry names one exact repository-relative path. " + CANONICAL_SCOPE_PATH_REQUIREMENT
                    + "Dual derives CREATE/MODIFY from the effective worktree. "
                    + "It does not require every declared path to be changed. Acceptance criteria, constraints, "
                    + "risks, and other prose cannot authorize an undeclared path. Never describe the typed scope "
                    + "as illustrative or non-exhaustive. When a revision obligation identifies a scope gap, "
                    + "update the typed path entries directly and map the obligation to one or more "
                    + "genuinely relevant clause IDs. "
                    + "Each task-contract clause must contain stable string fields `id` and `text`. "
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

    private static String json(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception failure) {
            throw new IllegalStateException("failed to render typed prompt value", failure);
        }
    }

    private static String verificationRemediationAllowedValues(AgentResultEnvelope.AgentResult result) {
        if (!hasVerificationRemediation(result)) {
            return "";
        }
        var allowed = result instanceof AgentResultEnvelope.AcceptanceReviewResult
                ? java.util.Arrays.stream(AgentResultEnvelope.VerificationRemediation.values())
                        .map(Enum::name)
                        .sorted()
                        .toList()
                : List.of(AgentResultEnvelope.VerificationRemediation.NONE.name());
        return "Allowed `result.failure.verification_remediation` values for this node: " + allowed;
    }

    private static String verificationRemediationInstruction(
            String nodeId,
            AgentResultEnvelope.AgentResult result
    ) {
        if (!hasVerificationRemediation(result)) {
            return "";
        }
        if (!"acceptance.codex_verifier_review".equals(nodeId)) {
            return "For this node, `result.failure.verification_remediation` must remain `NONE`.";
        }
        return "For a REJECT with non-empty `result.failure.missing_tests`, set "
                + "`result.failure.verification_remediation` to `EVIDENCE_COLLECTION` only when "
                + "existing test/verification evidence must be collected, or `TEST_CODE_CHANGE` when "
                + "a test source change is required. Do not infer this from prose in downstream nodes.";
    }

    private static boolean hasVerificationRemediation(AgentResultEnvelope.AgentResult result) {
        return result instanceof AgentResultEnvelope.AgentOutputResult
                || result instanceof AgentResultEnvelope.PlanResult
                || result instanceof AgentResultEnvelope.AcceptanceReviewResult;
    }

    private static AgentResultEnvelope.AgentResult exampleResult(
            String nodeId,
            String decision,
            ProposalEmissionContext proposalContext
    ) {
        return switch (nodeId) {
            case "intake.claude_brainstorm" -> proposalContext.revision()
                    ? new AgentResultEnvelope.TaskContractRevisionPatchResult(
                    "task_contract_revision_patch",
                    decision,
                    "one short sentence",
                    proposalContext.baseProposalId(),
                    proposalContext.baseProposalSha256(),
                    List.of(new AgentResultEnvelope.ObligationPatch(
                            proposalContext.obligations().isEmpty()
                                    ? "CO-1" : proposalContext.obligations().getFirst().id(),
                            List.of("AC-1"),
                            List.of(new AgentResultEnvelope.ClauseChange(
                                    AgentResultEnvelope.ClauseChangeOperation.REPLACE,
                                    AgentResultEnvelope.ClauseSection.ACCEPTANCE_CRITERIA,
                                    "AC-1",
                                    "replacement text")),
                            List.of(new AgentResultEnvelope.ProposalScopePath("repo/relative/new.ext")),
                            List.of("repo/relative/evidence.ext"))))
                    : new AgentResultEnvelope.TaskContractProposalResult(
                    "task_contract_proposal",
                    decision,
                    "one short sentence",
                    false,
                    "one clear objective",
                    new AgentResultEnvelope.ProposalScope(
                            List.of(
                                    new AgentResultEnvelope.ProposalScopePath("repo/relative/existing.ext"),
                                    new AgentResultEnvelope.ProposalScopePath("repo/relative/new.ext")
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
            case "design.claude_design" -> new AgentResultEnvelope.DesignResult(
                    "design_result", decision, "one short sentence", "READY",
                    List.of("read the approved contract"), List.of("design evidence"),
                    List.of(), List.of(), List.of(), "Design only; no implementation performed.",
                    new AgentResultEnvelope.Design(List.of(new AgentResultEnvelope.PlannedFile(
                            "repo/relative/new.ext", dev.dual.core.contract.TaskContract.ScopeOperation.CREATE,
                            "responsibility assigned within the approved scope"))), List.of());
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
                    ), List.of()
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

    public static ProposalEmissionContext proposalEmissionContext(
            TaskContractRevisionContextResolver.Context context
    ) {
        if (context == null || !context.revision()) {
            return ProposalEmissionContext.initial();
        }
        if (context.previousReference() == null) {
            throw new IllegalArgumentException("revision proposal reference is required");
        }
        return new ProposalEmissionContext(
                EmissionMode.REVISION_PATCH,
                context.previousReference().proposalId(),
                context.previousReference().sha256(),
                List.copyOf(context.obligations()));
    }

    public enum EmissionMode {
        INITIAL_FULL,
        REVISION_PATCH,
        NOT_APPLICABLE
    }

    public record ProposalEmissionContext(
            EmissionMode mode,
            String baseProposalId,
            String baseProposalSha256,
            List<AgentResultEnvelope.ReviewObligation> obligations
    ) {
        public ProposalEmissionContext {
            obligations = obligations == null ? List.of() : List.copyOf(obligations);
        }

        public static ProposalEmissionContext initial() {
            return new ProposalEmissionContext(EmissionMode.INITIAL_FULL, "", "", List.of());
        }

        public static ProposalEmissionContext notApplicable() {
            return new ProposalEmissionContext(EmissionMode.NOT_APPLICABLE, "", "", List.of());
        }

        boolean revision() {
            return mode == EmissionMode.REVISION_PATCH;
        }
    }

    private static AgentResultEnvelope.Failure emptyFailure() {
        return new AgentResultEnvelope.Failure("", AgentResultEnvelope.VerificationRemediation.NONE,
                List.of(), List.of(), List.of(), List.of(), "");
    }
}
