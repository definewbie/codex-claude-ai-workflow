package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** Node and execution lineage validation after strict JSON deserialization. */
public final class AgentResultEnvelopeValidator {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    public ValidationResult validate(
            AgentResultEnvelope envelope,
            String expectedWorkstreamId,
            String expectedRunId,
            NodeOutputContract contract
    ) {
        var errors = new ValidationErrors();
        if (envelope == null) {
            errors.reject("envelope is required");
            return errors.result();
        }
        if (envelope.schemaVersion() == 0) {
            errors.repairable("schema_version", CorrectionValue.SCHEMA_VERSION, "schema_version must be 1");
        } else if (envelope.schemaVersion() != 1) {
            errors.reject("schema_version must be 1");
        }
        if (envelope.workstreamId() == null || envelope.workstreamId().isBlank()) {
            errors.repairable("workstream_id", CorrectionValue.WORKSTREAM_ID, "workstream_id is required");
        } else if (expectedWorkstreamId == null || expectedWorkstreamId.isBlank()
                || !expectedWorkstreamId.equals(envelope.workstreamId())) {
            errors.reject("workstream_id does not match current workstream");
        }
        if (envelope.runId() == null || envelope.runId().isBlank()) {
            errors.repairable("run_id", CorrectionValue.RUN_ID, "run_id is required");
        } else if (expectedRunId == null || expectedRunId.isBlank()
                || !expectedRunId.equals(envelope.runId())) {
            errors.reject("run_id does not match current execution");
        }
        if (envelope.nodeId() == null || envelope.nodeId().isBlank()) {
            errors.repairable("node_id", CorrectionValue.NODE_ID, "node_id is required");
        } else if (!contract.nodeId().equals(envelope.nodeId())) {
            errors.reject("node_id does not match current node");
        }
        if (envelope.result() == null) {
            errors.reject("result is required");
        } else {
            if (envelope.result().artifactType() == null || envelope.result().artifactType().isBlank()) {
                errors.reject("result.artifact_type is required");
            } else if (!contract.emitsArtifact(envelope.result().artifactType())) {
                errors.reject("result.artifact_type is not allowed for node " + contract.nodeId());
            }
            if (envelope.result().decision() == null || envelope.result().decision().isBlank()) {
                errors.reject("result.decision is required");
            } else if (!contract.allowedDecisions().contains(envelope.result().decision())) {
                errors.reject("result.decision is not allowed for node " + contract.nodeId());
            }
            if (envelope.result().summary() == null || envelope.result().summary().isBlank()) {
                errors.reject("result.summary is required");
            }
            validatePayload(envelope.result(), contract.nodeId(), errors);
        }
        return errors.result();
    }

    public ValidationResult validateCorrection(
            AgentResultEnvelope original,
            AgentResultEnvelope corrected,
            String expectedWorkstreamId,
            String expectedRunId,
            NodeOutputContract contract,
            ValidationResult originalValidation
    ) {
        var correctedValidation = validate(corrected, expectedWorkstreamId, expectedRunId, contract);
        if (!correctedValidation.valid()) {
            return correctedValidation;
        }
        if (original == null || originalValidation == null || !originalValidation.schemaCorrectionAllowed()) {
            return ValidationResult.rejected("schema correction has no repairable original candidate");
        }
        var expected = OBJECT_MAPPER.valueToTree(original);
        if (!(expected instanceof ObjectNode expectedObject)) {
            return ValidationResult.rejected("schema correction baseline is not an object");
        }
        for (var target : originalValidation.correctionTargets()) {
            if (!applyExpectedValue(
                    expectedObject,
                    target,
                    expectedWorkstreamId,
                    expectedRunId,
                    contract.nodeId()
            )) {
                return ValidationResult.rejected("schema correction target is invalid: " + target.path());
            }
        }
        if (!expectedObject.equals(OBJECT_MAPPER.valueToTree(corrected))) {
            return ValidationResult.rejected("schema correction changed fields outside the repairable schema targets");
        }
        return correctedValidation;
    }

    private static void validatePayload(
            AgentResultEnvelope.AgentResult result,
            String nodeId,
            ValidationErrors errors
    ) {
        switch (result) {
            case AgentResultEnvelope.TaskContractProposalResult proposal -> {
                requiredText(proposal.objective(), "result.objective", errors);
                if (proposal.scope() == null) {
                    errors.reject("result.scope is required");
                } else {
                    requiredList(proposal.scope().included(), "result.scope.included", true, errors);
                    requiredList(proposal.scope().excluded(), "result.scope.excluded", false, errors);
                    validateScopeEntries(proposal.scope().included(), errors);
                }
                requiredList(proposal.acceptanceCriteria(), "result.acceptance_criteria", true, errors);
                requiredList(proposal.testRequirements(), "result.test_requirements", true, errors);
                requiredList(proposal.constraints(), "result.constraints", false, errors);
                requiredList(proposal.nonGoals(), "result.non_goals", false, errors);
                requiredList(proposal.riskAreas(), "result.risk_areas", false, errors);
                requiredList(proposal.evidenceRefs(), "result.evidence_refs", false, errors);
                requiredList(proposal.openQuestions(), "result.open_questions", false, errors);
                requiredList(proposal.revisionResponses(), "result.revision_responses", false, errors);
                validateContractClauses(proposal, errors);
                validateRevisionResponses(proposal.revisionResponses(), errors);
            }
            case AgentResultEnvelope.TaskContractRevisionPatchResult patch -> validateRevisionPatch(patch, errors);
            case AgentResultEnvelope.AgentOutputResult output -> {
                validateEvidencePayload(
                        output.lifecycleStatus(), output.checks(), output.evidence(), output.missingEvidence(),
                        output.mustFix(), output.acceptanceNotes(), output.openRisks(), output.changedFiles(),
                        output.verificationSummary(), output.failure(), errors);
                requiredList(output.rereadReasons(), "result.reread_reasons", false, errors);
                validateRereadReasons(output.rereadReasons(), errors);
                validateReviewObligations(nodeId, output, errors);
                validateNoVerificationRemediation(output.failure(), errors);
            }
            case AgentResultEnvelope.DesignResult design -> {
                AuthoredRevisionValidator.shapeErrors(design.revisionResponses()).forEach(errors::reject);
                requiredText(design.lifecycleStatus(), "result.lifecycle_status", errors);
                requiredList(design.checks(), "result.checks", false, errors);
                requiredList(design.evidence(), "result.evidence", false, errors);
                requiredList(design.missingEvidence(), "result.missing_evidence", false, errors);
                requiredList(design.mustFix(), "result.must_fix", false, errors);
                requiredList(design.openRisks(), "result.open_risks", false, errors);
                requiredText(design.verificationSummary(), "result.verification_summary", errors);
                for (var error : DesignScopeValidator.shapeErrors(design.design())) {
                    errors.reject(error);
                }
            }
            case AgentResultEnvelope.PlanResult plan -> {
                AuthoredRevisionValidator.shapeErrors(plan.revisionResponses()).forEach(errors::reject);
                validateEvidencePayload(
                        plan.lifecycleStatus(), plan.checks(), plan.evidence(), plan.missingEvidence(),
                        plan.mustFix(), plan.acceptanceNotes(), plan.openRisks(), plan.changedFiles(),
                        plan.verificationSummary(), plan.failure(), errors);
                if (plan.plan() == null) {
                    errors.reject("result.plan is required");
                } else {
                    requiredList(plan.plan().implementationSteps(), "result.plan.implementation_steps", true, errors);
                    requiredList(plan.plan().editFiles(), "result.plan.edit_files", true, errors);
                    PlanScopeValidator.shapeErrors(plan.plan()).forEach(errors::reject);
                    requiredList(plan.plan().referenceFiles(), "result.plan.reference_files", false, errors);
                    requiredList(plan.plan().fileLevelChanges(), "result.plan.file_level_changes", true, errors);
                    requiredList(plan.plan().validationPlan(), "result.plan.validation_plan", true, errors);
                    requiredText(plan.plan().rollbackNotes(), "result.plan.rollback_notes", errors);
                    requiredList(plan.plan().knownRisks(), "result.plan.known_risks", true, errors);
                    requiredList(plan.plan().humanReviewChecklist(), "result.plan.human_review_checklist", true, errors);
                }
                validateNoVerificationRemediation(plan.failure(), errors);
            }
            case AgentResultEnvelope.SelfTestResult selfTest -> {
                requiredText(selfTest.lifecycleStatus(), "result.lifecycle_status", errors);
                requiredList(selfTest.checksRun(), "result.checks_run", false, errors);
                requiredList(selfTest.checksSkipped(), "result.checks_skipped", false, errors);
                requiredList(selfTest.evidence(), "result.evidence", false, errors);
                requiredList(selfTest.missingEvidence(), "result.missing_evidence", false, errors);
                requiredList(selfTest.acceptanceNotes(), "result.acceptance_notes", false, errors);
                requiredText(selfTest.verificationSummary(), "result.verification_summary", errors);
            }
            case AgentResultEnvelope.AcceptanceReviewResult review -> {
                validateEvidencePayload(
                        review.lifecycleStatus(), review.checks(), review.evidence(), review.missingEvidence(),
                        review.mustFix(), review.acceptanceNotes(), review.openRisks(), review.changedFiles(),
                        review.verificationSummary(), review.failure(), errors);
                validateVerificationRemediation(review, errors);
            }
            case AgentResultEnvelope.StageHandoffResult handoff -> {
                requiredText(handoff.lifecycleStatus(), "result.lifecycle_status", errors);
                requiredList(handoff.completedWork(), "result.completed_work", true, errors);
                requiredList(handoff.importantDecisions(), "result.important_decisions", false, errors);
                requiredList(handoff.changedFiles(), "result.changed_files", false, errors);
                requiredText(handoff.verificationState(), "result.verification_state", errors);
                requiredList(handoff.openRisks(), "result.open_risks", false, errors);
                requiredText(handoff.recommendedNextStage(), "result.recommended_next_stage", errors);
                requiredText(handoff.suggestedNextPrompt(), "result.suggested_next_prompt", errors);
            }
        }
    }

    private static void validateRevisionPatch(
            AgentResultEnvelope.TaskContractRevisionPatchResult patch,
            ValidationErrors errors
    ) {
        requiredText(patch.baseProposalId(), "result.base_proposal_id", errors);
        requiredText(patch.baseProposalSha256(), "result.base_proposal_sha256", errors);
        if (patch.baseProposalSha256() != null
                && !patch.baseProposalSha256().matches("[0-9a-f]{64}")) {
            errors.reject("result.base_proposal_sha256 must be 64 lowercase hexadecimal characters");
        }
        requiredList(patch.obligationPatches(), "result.obligation_patches", true, errors);
        if (patch.obligationPatches() == null) {
            return;
        }
        var obligationIds = new HashSet<String>();
        for (int index = 0; index < patch.obligationPatches().size(); index++) {
            var value = patch.obligationPatches().get(index);
            var item = "result.obligation_patches[" + index + "]";
            if (value == null) {
                errors.reject(item + " is required");
                continue;
            }
            requiredText(value.reviewObligationId(), item + ".review_obligation_id", errors);
            requiredList(value.resolvedByClauseIds(), item + ".resolved_by_clause_ids", true, errors);
            requiredList(value.clauseChanges(), item + ".clause_changes", false, errors);
            requiredList(value.scopeAdditions(), item + ".scope_additions", false, errors);
            requiredList(value.evidenceRefAdditions(), item + ".evidence_ref_additions", false, errors);
            if (value.reviewObligationId() != null && !value.reviewObligationId().isBlank()
                    && !obligationIds.add(value.reviewObligationId().trim())) {
                errors.reject("result.obligation_patches contains duplicate obligation id: "
                        + value.reviewObligationId().trim());
            }
            if (value.clauseChanges() != null) {
                for (int changeIndex = 0; changeIndex < value.clauseChanges().size(); changeIndex++) {
                    var change = value.clauseChanges().get(changeIndex);
                    var changeItem = item + ".clause_changes[" + changeIndex + "]";
                    if (change == null) {
                        errors.reject(changeItem + " is required");
                        continue;
                    }
                    if (change.operation() == null) {
                        errors.reject(changeItem + ".operation is required");
                    }
                    if (change.section() == null) {
                        errors.reject(changeItem + ".section is required");
                    }
                    requiredText(change.id(), changeItem + ".id", errors);
                    requiredText(change.text(), changeItem + ".text", errors);
                }
            }
            validateScopeEntries(value.scopeAdditions(), errors);
        }
        TaskContractRevisionPatchTargetValidator.validate(patch).forEach(errors::reject);
    }

    private static void validateContractClauses(
            AgentResultEnvelope.TaskContractProposalResult proposal,
            ValidationErrors errors
    ) {
        var ids = new HashSet<String>();
        validateContractClauses(proposal.acceptanceCriteria(), "result.acceptance_criteria", ids, errors);
        validateContractClauses(proposal.testRequirements(), "result.test_requirements", ids, errors);
        validateContractClauses(proposal.constraints(), "result.constraints", ids, errors);
        validateContractClauses(proposal.nonGoals(), "result.non_goals", ids, errors);
        validateContractClauses(proposal.riskAreas(), "result.risk_areas", ids, errors);
    }

    private static void validateContractClauses(
            List<AgentResultEnvelope.ContractClause> clauses,
            String field,
            HashSet<String> ids,
            ValidationErrors errors
    ) {
        if (clauses == null) {
            return;
        }
        for (int index = 0; index < clauses.size(); index++) {
            var clause = clauses.get(index);
            var item = field + "[" + index + "]";
            if (clause == null) {
                errors.reject(item + " is required");
                continue;
            }
            requiredText(clause.id(), item + ".id", errors);
            requiredText(clause.text(), item + ".text", errors);
            if (clause.id() != null && !clause.id().isBlank() && !ids.add(clause.id().trim())) {
                errors.reject("result contract clause id is duplicated: " + clause.id().trim());
            }
        }
    }

    private static void validateRevisionResponses(
            List<AgentResultEnvelope.RevisionResponse> responses,
            ValidationErrors errors
    ) {
        if (responses == null) {
            return;
        }
        var obligationIds = new HashSet<String>();
        for (int index = 0; index < responses.size(); index++) {
            var response = responses.get(index);
            var item = "result.revision_responses[" + index + "]";
            if (response == null) {
                errors.reject(item + " is required");
                continue;
            }
            requiredText(response.reviewObligationId(), item + ".review_obligation_id", errors);
            requiredList(response.resolvedByClauseIds(), item + ".resolved_by_clause_ids", true, errors);
            if (response.reviewObligationId() != null
                    && !response.reviewObligationId().isBlank()
                    && !obligationIds.add(response.reviewObligationId().trim())) {
                errors.reject("result.revision_responses contains duplicate obligation id: "
                        + response.reviewObligationId().trim());
            }
        }
    }

    private static void validateReviewObligations(
            String nodeId,
            AgentResultEnvelope.AgentOutputResult output,
            ValidationErrors errors
    ) {
        var obligations = output.contractObligations();
        var contractRevision = "intake.codex_product_review".equals(nodeId)
                && "NEEDS_REVISION".equals(output.decision())
                && output.failure() != null
                && output.failure().contractGaps() != null
                && !output.failure().contractGaps().isEmpty();
        if (contractRevision) {
            requiredList(obligations, "result.contract_obligations", true, errors);
        } else if (obligations != null && !obligations.isEmpty()) {
            errors.reject("result.contract_obligations is only allowed for intake contract revisions");
        }
        if (obligations == null) {
            return;
        }
        var ids = new HashSet<String>();
        for (int index = 0; index < obligations.size(); index++) {
            var obligation = obligations.get(index);
            var item = "result.contract_obligations[" + index + "]";
            if (obligation == null) {
                errors.reject(item + " is required");
                continue;
            }
            requiredText(obligation.id(), item + ".id", errors);
            requiredText(obligation.text(), item + ".text", errors);
            if (obligation.id() != null && !obligation.id().isBlank()
                    && !ids.add(obligation.id().trim())) {
                errors.reject("result.contract_obligations contains duplicate id: "
                        + obligation.id().trim());
            }
        }
    }

    private static void validateScopeEntries(
            List<AgentResultEnvelope.ProposalScopePath> entries,
            ValidationErrors errors
    ) {
        if (entries == null) {
            return;
        }
        for (var entry : entries) {
            if (entry == null) {
                errors.reject("result.scope.included contains null entry");
                continue;
            }
            requiredText(entry.path(), "result.scope.included.path", errors);
        }
    }

    private static void validateEvidencePayload(
            String lifecycleStatus,
            List<String> checks,
            List<String> evidence,
            List<String> missingEvidence,
            List<String> mustFix,
            List<String> acceptanceNotes,
            List<String> openRisks,
            List<String> changedFiles,
            String verificationSummary,
            AgentResultEnvelope.Failure failure,
            ValidationErrors errors
    ) {
        requiredText(lifecycleStatus, "result.lifecycle_status", errors);
        requiredList(checks, "result.checks", false, errors);
        requiredList(evidence, "result.evidence", false, errors);
        requiredList(missingEvidence, "result.missing_evidence", false, errors);
        requiredList(mustFix, "result.must_fix", false, errors);
        requiredList(acceptanceNotes, "result.acceptance_notes", false, errors);
        requiredList(openRisks, "result.open_risks", false, errors);
        requiredList(changedFiles, "result.changed_files", false, errors);
        requiredText(verificationSummary, "result.verification_summary", errors);
        if (failure == null) {
            errors.reject("result.failure is required");
            return;
        }
        requiredList(failure.contractGaps(), "result.failure.contract_gaps", false, errors);
        requiredList(failure.outOfScopeFiles(), "result.failure.out_of_scope_files", false, errors);
        requiredList(failure.missingTests(), "result.failure.missing_tests", false, errors);
        requiredList(failure.contextGaps(), "result.failure.context_gaps", false, errors);
    }

    private static void validateVerificationRemediation(
            AgentResultEnvelope.AcceptanceReviewResult review,
            ValidationErrors errors
    ) {
        if (review.failure() == null) {
            return;
        }
        var remediation = review.failure().verificationRemediation();
        var rejectWithMissingTests = "REJECT".equals(review.decision())
                && review.failure().missingTests() != null
                && !review.failure().missingTests().isEmpty();
        if (rejectWithMissingTests
                && (remediation == null || remediation == AgentResultEnvelope.VerificationRemediation.NONE)) {
            errors.reject("result.failure.verification_remediation is required when REJECT lists missing_tests");
        }
        if (!rejectWithMissingTests && remediation != null
                && remediation != AgentResultEnvelope.VerificationRemediation.NONE) {
            errors.reject("result.failure.verification_remediation is only allowed when REJECT lists missing_tests");
        }
    }

    private static void validateNoVerificationRemediation(
            AgentResultEnvelope.Failure failure,
            ValidationErrors errors
    ) {
        if (failure != null
                && failure.verificationRemediation() != null
                && failure.verificationRemediation() != AgentResultEnvelope.VerificationRemediation.NONE) {
            errors.reject("result.failure.verification_remediation must be NONE outside acceptance review");
        }
    }

    private static void validateRereadReasons(
            List<AgentResultEnvelope.RereadReason> reasons,
            ValidationErrors errors
    ) {
        if (reasons == null) {
            return;
        }
        for (int index = 0; index < reasons.size(); index++) {
            var reason = reasons.get(index);
            if (reason == null) {
                errors.reject("result.reread_reasons[" + index + "] is required");
                continue;
            }
            requiredText(reason.path(), "result.reread_reasons[" + index + "].path", errors);
            if (!ReadReuseContractReasons.isAllowed(reason.reason())) {
                errors.reject("result.reread_reasons[" + index + "].reason is not allowed");
            }
        }
    }

    private static void requiredText(String value, String field, ValidationErrors errors) {
        if (value == null || value.isBlank()) {
            errors.reject(field + " is required");
        }
    }

    private static void requiredList(List<?> value, String field, boolean nonEmpty, ValidationErrors errors) {
        if (value == null) {
            if (nonEmpty) {
                errors.reject(field + " is required");
            } else {
                errors.repairable(field, CorrectionValue.EMPTY_LIST, field + " is required");
            }
        } else if (nonEmpty && value.isEmpty()) {
            errors.reject(field + " must not be empty");
        }
    }

    private static boolean applyExpectedValue(
            ObjectNode root,
            CorrectionTarget target,
            String expectedWorkstreamId,
            String expectedRunId,
            String expectedNodeId
    ) {
        var value = switch (target.value()) {
            case SCHEMA_VERSION -> IntNode.valueOf(1);
            case WORKSTREAM_ID -> TextNode.valueOf(expectedWorkstreamId);
            case RUN_ID -> TextNode.valueOf(expectedRunId);
            case NODE_ID -> TextNode.valueOf(expectedNodeId);
            case EMPTY_LIST -> new ArrayNode(OBJECT_MAPPER.getNodeFactory());
        };
        var segments = target.path().split("\\.");
        ObjectNode parent = root;
        for (int index = 0; index < segments.length - 1; index++) {
            var child = parent.get(segments[index]);
            if (!(child instanceof ObjectNode childObject)) {
                return false;
            }
            parent = childObject;
        }
        parent.set(segments[segments.length - 1], value);
        return true;
    }

    public enum CorrectionDisposition {
        NONE,
        SCHEMA_REPAIRABLE,
        NOT_REPAIRABLE
    }

    public enum CorrectionValue {
        SCHEMA_VERSION,
        WORKSTREAM_ID,
        RUN_ID,
        NODE_ID,
        EMPTY_LIST
    }

    public record CorrectionTarget(String path, CorrectionValue value) {
    }

    public record ValidationResult(
            boolean valid,
            List<String> errors,
            CorrectionDisposition correctionDisposition,
            List<CorrectionTarget> correctionTargets
    ) {
        static ValidationResult pass() {
            return new ValidationResult(true, List.of(), CorrectionDisposition.NONE, List.of());
        }

        static ValidationResult rejected(String error) {
            return new ValidationResult(
                    false,
                    List.of(error),
                    CorrectionDisposition.NOT_REPAIRABLE,
                    List.of()
            );
        }

        public boolean schemaCorrectionAllowed() {
            return correctionDisposition == CorrectionDisposition.SCHEMA_REPAIRABLE;
        }
    }

    private static final class ValidationErrors {
        private final List<String> messages = new ArrayList<>();
        private final List<CorrectionTarget> correctionTargets = new ArrayList<>();
        private boolean rejected;

        void repairable(String field, CorrectionValue value, String message) {
            messages.add(message);
            correctionTargets.add(new CorrectionTarget(field, value));
        }

        void reject(String message) {
            messages.add(message);
            rejected = true;
        }

        ValidationResult result() {
            if (messages.isEmpty()) {
                return ValidationResult.pass();
            }
            var disposition = !correctionTargets.isEmpty() && !rejected
                    ? CorrectionDisposition.SCHEMA_REPAIRABLE
                    : CorrectionDisposition.NOT_REPAIRABLE;
            return new ValidationResult(
                    false,
                    List.copyOf(messages),
                    disposition,
                    List.copyOf(correctionTargets)
            );
        }
    }
}
