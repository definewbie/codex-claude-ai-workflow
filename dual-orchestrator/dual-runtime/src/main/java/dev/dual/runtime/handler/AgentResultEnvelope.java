package dev.dual.runtime.handler;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import dev.dual.core.contract.TaskContract;
import java.util.List;

/** Strict, lineage-bound workflow result emitted as the final ACP assistant message. */
public record AgentResultEnvelope(
        int schemaVersion,
        String workstreamId,
        String runId,
        String nodeId,
        AgentResult result
) {
    @JsonTypeInfo(
            use = JsonTypeInfo.Id.NAME,
            include = JsonTypeInfo.As.EXISTING_PROPERTY,
            property = "artifact_type",
            visible = true
    )
    @JsonSubTypes({
            @JsonSubTypes.Type(value = TaskContractProposalResult.class, name = "task_contract_proposal"),
            @JsonSubTypes.Type(value = TaskContractRevisionPatchResult.class, name = "task_contract_revision_patch"),
            @JsonSubTypes.Type(value = AgentOutputResult.class, name = "agent_output_contract"),
            @JsonSubTypes.Type(value = DesignResult.class, name = "design_result"),
            @JsonSubTypes.Type(value = PlanResult.class, name = "plan_result"),
            @JsonSubTypes.Type(value = SelfTestResult.class, name = "self_test_report"),
            @JsonSubTypes.Type(value = AcceptanceReviewResult.class, name = "acceptance_review"),
            @JsonSubTypes.Type(value = StageHandoffResult.class, name = "stage_handoff")
    })
    public sealed interface AgentResult permits
            TaskContractProposalResult,
            TaskContractRevisionPatchResult,
            AgentOutputResult,
            DesignResult,
            PlanResult,
            SelfTestResult,
            AcceptanceReviewResult,
            StageHandoffResult {
        String artifactType();

        String decision();

        String summary();
    }

    public record DesignResult(
            String artifactType,
            String decision,
            String summary,
            String lifecycleStatus,
            List<String> checks,
            List<String> evidence,
            List<String> missingEvidence,
            List<String> mustFix,
            List<String> openRisks,
            String verificationSummary,
            Design design,
            List<AuthoredRevisionResponse> revisionResponses
    ) implements AgentResult {
    }

    public record Design(List<PlannedFile> plannedFiles) {
    }

    public record PlannedFile(String path, TaskContract.ScopeOperation operation, String responsibility) {
    }

    public record AuthoredRevisionResponse(String obligationId, AuthoredRevisionDisposition disposition, String evidence) {
    }

    public enum AuthoredRevisionDisposition {
        ADDRESSED, NOT_ADDRESSED
    }

    public record TaskContractProposalResult(
            String artifactType,
            String decision,
            String summary,
            boolean needsHumanInput,
            String objective,
            ProposalScope scope,
            List<ContractClause> acceptanceCriteria,
            List<ContractClause> testRequirements,
            List<ContractClause> constraints,
            List<ContractClause> nonGoals,
            List<ContractClause> riskAreas,
            List<String> evidenceRefs,
            List<String> openQuestions,
            List<RevisionResponse> revisionResponses
    ) implements AgentResult {
    }

    public record ProposalScope(List<ProposalScopePath> included, List<String> excluded) {
    }

    public record ProposalScopePath(String path) {
    }

    public record ContractClause(String id, String text) {
    }

    public record ReviewObligation(String id, String text) {
    }

    public record RevisionResponse(String reviewObligationId, List<String> resolvedByClauseIds) {
    }

    public record TaskContractRevisionPatchResult(
            String artifactType,
            String decision,
            String summary,
            String baseProposalId,
            String baseProposalSha256,
            List<ObligationPatch> obligationPatches
    ) implements AgentResult {
    }

    public record ObligationPatch(
            String reviewObligationId,
            List<String> resolvedByClauseIds,
            List<ClauseChange> clauseChanges,
            List<ProposalScopePath> scopeAdditions,
            List<String> evidenceRefAdditions
    ) {
    }

    public record ClauseChange(
            ClauseChangeOperation operation,
            ClauseSection section,
            String id,
            String text
    ) {
    }

    public enum ClauseChangeOperation {
        ADD,
        REPLACE
    }

    public enum ClauseSection {
        ACCEPTANCE_CRITERIA,
        TEST_REQUIREMENTS,
        CONSTRAINTS,
        NON_GOALS,
        RISK_AREAS
    }

    public record AgentOutputResult(
            String artifactType,
            String decision,
            String summary,
            String lifecycleStatus,
            boolean blocking,
            List<String> checks,
            List<String> evidence,
            List<String> missingEvidence,
            List<String> mustFix,
            List<ReviewObligation> contractObligations,
            List<String> acceptanceNotes,
            List<String> openRisks,
            List<String> changedFiles,
            String verificationSummary,
            Failure failure,
            List<RereadReason> rereadReasons
    ) implements AgentResult {
        public AgentOutputResult(
                String artifactType,
                String decision,
                String summary,
                String lifecycleStatus,
                boolean blocking,
                List<String> checks,
                List<String> evidence,
                List<String> missingEvidence,
                List<String> mustFix,
                List<String> acceptanceNotes,
                List<String> openRisks,
                List<String> changedFiles,
                String verificationSummary,
                Failure failure,
                List<RereadReason> rereadReasons
        ) {
            this(artifactType, decision, summary, lifecycleStatus, blocking,
                    checks, evidence, missingEvidence, mustFix, List.of(), acceptanceNotes,
                    openRisks, changedFiles, verificationSummary, failure, rereadReasons);
        }
    }

    public record PlanResult(
            String artifactType,
            String decision,
            String summary,
            String lifecycleStatus,
            boolean blocking,
            List<String> checks,
            List<String> evidence,
            List<String> missingEvidence,
            List<String> mustFix,
            List<String> acceptanceNotes,
            List<String> openRisks,
            List<String> changedFiles,
            String verificationSummary,
            Failure failure,
            Plan plan,
            List<AuthoredRevisionResponse> revisionResponses
    ) implements AgentResult {
    }

    public record Plan(
            List<String> implementationSteps,
            List<String> editFiles,
            List<String> referenceFiles,
            List<String> fileLevelChanges,
            List<String> validationPlan,
            String rollbackNotes,
            List<String> knownRisks,
            List<String> humanReviewChecklist
    ) {
    }

    public record SelfTestResult(
            String artifactType,
            String decision,
            String summary,
            String lifecycleStatus,
            List<String> checksRun,
            List<String> checksSkipped,
            List<String> evidence,
            List<String> missingEvidence,
            List<String> acceptanceNotes,
            String verificationSummary
    ) implements AgentResult {
    }

    public record AcceptanceReviewResult(
            String artifactType,
            String decision,
            String summary,
            String lifecycleStatus,
            boolean blocking,
            List<String> checks,
            List<String> evidence,
            List<String> missingEvidence,
            List<String> mustFix,
            List<String> acceptanceNotes,
            List<String> openRisks,
            List<String> changedFiles,
            String verificationSummary,
            Failure failure
    ) implements AgentResult {
    }

    public record StageHandoffResult(
            String artifactType,
            String decision,
            String summary,
            String lifecycleStatus,
            List<String> completedWork,
            List<String> importantDecisions,
            List<String> changedFiles,
            String verificationState,
            List<String> openRisks,
            String recommendedNextStage,
            String suggestedNextPrompt
    ) implements AgentResult {
    }

    public record Failure(
            String categoryHint,
            VerificationRemediation verificationRemediation,
            List<String> contractGaps,
            List<String> outOfScopeFiles,
            List<String> missingTests,
            List<String> contextGaps,
            String reason
    ) {
    }

    public enum VerificationRemediation {
        NONE,
        EVIDENCE_COLLECTION,
        TEST_CODE_CHANGE
    }

    public record RereadReason(String path, String reason, String stage, String evidence) {
    }

}
