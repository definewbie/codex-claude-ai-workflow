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
                            verificationRemediationInstruction(nodeId, exampleResult) + acceptanceClassificationInstruction(exampleResult),
                            evidenceReferenceInstruction(nodeId) + contractRevisionInstruction(nodeId, proposalContext) + authoredRevisionInstruction(exampleResult)
                                    + selfTestInstruction(exampleResult) + verificationInstruction(exampleResult),
                            OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(envelope)
                    ).stripTrailing();
        } catch (Exception failure) {
            throw new IllegalStateException("failed to render AgentResultEnvelope prompt contract", failure);
        }
    }

    private static String evidenceReferenceInstruction(String nodeId) {
        if (!nodeId.startsWith("intake.")) return "";
        return "evidence_refs are canonical product-tree file paths, not program artifact references. "
                + "An empty tracked-path inventory permits evidence_refs=[]; do not create files to populate it. "
                + "For a non-empty inventory include at least one existing tracked product file supported by its repo-map fact. "
                + "Never copy .agent/ or .git/ references into evidence_refs or evidence_ref_additions. "
                + "Program repo-map/binding/context materials are carried by RepositoryFactBinding; reading them does not make them product evidence. ";
    }

    private static String selfTestInstruction(AgentResultEnvelope.AgentResult result) {
        if (!(result instanceof AgentResultEnvelope.SelfTestResult)) return "";
        return "\n`result.must_fix` is required. List concrete product/test code defects there, not missing "
                + "permissions, tools, or external verification records. Use missing_evidence for missing proof. "
                + "Both lists may be non-empty when code defects and missing proof coexist. "
                + "SELF_TEST_COMPLETE requires both lists empty; otherwise use SELF_TEST_INCOMPLETE or BLOCKED. "
                + "Do not invent code changes to resolve an environment or evidence-only blocker.";
    }

    private static String verificationInstruction(AgentResultEnvelope.AgentResult result) {
        if (result instanceof AgentResultEnvelope.PlanResult)
            return "\n" + VerificationReadiness.PLAN_BINDING_INSTRUCTION
                    + "Each verification_binding must explicitly set actual_exit_required (boolean) and expected_exit_code (integer or null). "
                    + "Derive these from the approved acceptance requirements before execution; never weaken a required exit check. "
                    + "expected_exit_code requires actual_exit_required=true. Operator declarations cannot supply measured process exits.";
        if (!(result instanceof AgentResultEnvelope.SelfTestResult) && !(result instanceof AgentResultEnvelope.AcceptanceReviewResult)) return "";
        return "\nSuccessful self-test/acceptance must have one verification_checks entry for each approved criterion. "
                + "Each entry contains exactly criterion_id, assertion, execution_ref, tool_call_ids, material_refs. "
                + "assertion describes what the actual result demonstrates. execution_ref is CURRENT for this execution's program observations, "
                + "or an exact prior verification-observations.json reference supplied in context; operator evidence uses the bound submission manifest. "
                + "tool_call_ids may select known call IDs; [] selects the complete observed result set, not zero required evidence. "
                + "material_refs contains actual material references with ref and sha256 fields when used, or []. Never invent IDs, paths, hashes, outputs or exits. "
                + "Native completion is not measured exit 0. Missing required evidence must remain incomplete/rejected; verification_checks may be [] for an incomplete result. "
                + "A declaration or a tool status alone does not establish the criterion; inspect its actual input/output and any required report.";
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
                    ? "\nedit_files must contain unique canonical proposed paths respecting the approved GOAL exclusions. Exact file operations require later EXECUTION approval. "
                        + CANONICAL_SCOPE_PATH_REQUIREMENT + "A subset is allowed; reference_files do not authorize edits. "
                    : "")
                + closedItemFields("result.revision_responses", new AgentResultEnvelope.AuthoredRevisionResponse(
                        "sha256:" + "0".repeat(64) + "#human-note:0",
                        AgentResultEnvelope.AuthoredRevisionDisposition.NOT_ADDRESSED, "Explain what remains unresolved"));
    }

    private static String contractRevisionInstruction(String nodeId, ProposalEmissionContext context) {
        if ("intake.claude_brainstorm".equals(nodeId)) {
            if (context.revision()) {
                return (context.goalReproposalAllowed()
                        ? "Use task_contract_revision_patch for ordinary changes. Before first GOAL approval only, an actual objective or scope.excluded change may use task_contract_proposal with base_proposal_id. Preserve scope.included/evidence_refs (retain or ADD), open_questions, all clause IDs/sections (ADD/REPLACE only); revision_responses must cover each obligation using actual relevant clause IDs. Full proposal without a goal/exclusion change is rejected. Full result shape: " + json(exampleResult(nodeId,"READY_FOR_PRODUCT_REVIEW",ProposalEmissionContext.initial())) + " Replace this illustrative content with the full revised baseline, set base_proposal_id to the exact current ID and supply all revision_responses. "
                        : "Return only `task_contract_revision_patch`; do not return or copy a full proposal. ")
                        + "The patch is bound to base proposal id `" + context.baseProposalId()
                        + "`. Full content identity is fixed by Dual; never emit base_proposal_sha256. "
                        + "Cover each authoritative routed obligation exactly once. Preserve its id and implement its text: "
                        + json(context.obligations())
                        + (context.acceptanceEvidence().isEmpty() ? "" :
                            ". Original verified rework evidence (retain non-contract defects as evidence; "
                            + "do not reinterpret them as contract obligations or approval): "
                            + context.acceptanceEvidence())
                        + ". For task_contract_revision_patch only: clause ADD/REPLACE, exact scope ADD, and evidence-ref ADD are supported. "
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
                                List.of(), List.of()))
                        + (context.goalReproposalAllowed()
                            ? closedItemFields("result.revision_responses",
                                new AgentResultEnvelope.RevisionResponse("CO-1", List.of("AC-1")))
                            : "");
            }
            return "Intake `result.scope.included` contains candidate paths, not write authorization. "
                    + "GOAL approval fixes objective, acceptance and exclusions; exact implementation scope is approved in EXECUTION after design and plan review. "
                    + "Only approved EXECUTION `scope.included` is the exhaustive authorization set for permitted repository changes; "
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

    private static String acceptanceClassificationInstruction(AgentResultEnvelope.AgentResult result) {
        if (!(result instanceof AgentResultEnvelope.AcceptanceReviewResult)) return "";
        return "\nFor REJECT, failure.category_hint is required and must be exactly one of "
                + AgentContractSemantics.acceptanceCategories()
                + ". No aliases or case normalization. For accepted decisions leave category_hint empty. "
                + "Choose the repair owner: IMPLEMENTATION_BUG for product, documentation or test changes within the "
                + "existing contract, including mixed repair and missing evidence; VERIFICATION_INSUFFICIENT only for "
                + "collecting existing evidence. Contract categories require contract_gaps; all other categories forbid "
                + "contract_gaps (existing-contract violations belong in must_fix). IMPLEMENTATION_BUG requires must_fix, "
                + "out_of_scope_files or TEST_CODE_CHANGE. VERIFICATION_INSUFFICIENT requires missing_tests or missing_evidence "
                + "and forbids context_gaps, out_of_scope_files and TEST_CODE_CHANGE. CONTEXT_INSUFFICIENT requires context_gaps; "
                + "TOOL_LIMITATION requires failure.reason; both forbid out_of_scope_files and TEST_CODE_CHANGE. REVIEW_NOISE "
                + "requires failure.reason, empty failure lists, empty must_fix/missing_evidence and NONE remediation. "
                + "Contract categories may retain mixed issues. EVIDENCE_COLLECTION never overrides the category. "
                + "TEST_CODE_CHANGE permits only implementation or contract categories.";
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
                            "proposed responsibility respecting the approved objective and exclusions"))), List.of());
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
                            List.of("review item"),
                            List.of(new dev.dual.core.contract.TaskContract.ScopeEntry("path/to/file.ext",
                                    dev.dual.core.contract.TaskContract.ScopeOperation.MODIFY)),
                            List.of(new dev.dual.core.contract.TaskContract.VerificationBinding(
                                    "approved-criterion-id", "observable assertion and execution method",
                                    dev.dual.core.contract.TaskContract.Executor.AGENT,
                                    List.of(new dev.dual.core.contract.TaskContract.Reference("replace/with/actual-environment-ref", "0".repeat(64))),
                                    List.of(), dev.dual.core.contract.TaskContract.EvidenceKind.PROGRAM_TOOL_EXECUTION_AND_ASSERTIONS,
                                    List.of(new dev.dual.core.contract.TaskContract.Reference("replace/with/actual-feasibility-ref", "0".repeat(64))),
                                    "implement.claude_self_test_report", false, null)), List.of()
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
                    List.of(),
                    "short verification adequacy summary"
            , List.of());
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
            , List.of());
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
                List.copyOf(context.obligations()),
                context.lateRoot() == null ? "" : json(context.lateRoot())
                        + (context.scopeRevision() ? "\nSCOPE REVIEW ONLY: add only a subset of these bound CREATE candidates: "
                            + json(context.scopeReviewCandidates())
                            + ". Do not change clauses, evidence refs, objective, exclusions or any existing scope entry. "
                            + "These files already exist but remain CREATE relative to the original Git base. "
                            + "Respond to obligations using existing clause ids; keep clause_changes and evidence_ref_additions empty."
                            : ""));
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
            List<AgentResultEnvelope.ReviewObligation> obligations,
            String acceptanceEvidence,
            boolean goalReproposalAllowed
    ) {
        public ProposalEmissionContext {
            obligations = obligations == null ? List.of() : List.copyOf(obligations);
            acceptanceEvidence = acceptanceEvidence == null ? "" : acceptanceEvidence;
        }

        public ProposalEmissionContext(EmissionMode mode, String baseProposalId, String baseProposalSha256,
                                       List<AgentResultEnvelope.ReviewObligation> obligations) {
            this(mode, baseProposalId, baseProposalSha256, obligations, "", false);
        }

        public ProposalEmissionContext(EmissionMode mode,String id,String sha,List<AgentResultEnvelope.ReviewObligation> obligations,String evidence) {
            this(mode,id,sha,obligations,evidence,false);
        }
        public ProposalEmissionContext withGoalReproposal(boolean allowed) {
            return new ProposalEmissionContext(mode,baseProposalId,baseProposalSha256,obligations,acceptanceEvidence,allowed);
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
