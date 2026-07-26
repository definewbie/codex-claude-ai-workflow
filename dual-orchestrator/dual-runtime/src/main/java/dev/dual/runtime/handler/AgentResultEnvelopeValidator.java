package dev.dual.runtime.handler;

import java.util.ArrayList;
import java.util.List;

/** Node and execution lineage validation after strict JSON deserialization. */
public final class AgentResultEnvelopeValidator {
    public ValidationResult validate(
            AgentResultEnvelope envelope,
            String expectedWorkstreamId,
            String expectedRunId,
            NodeOutputContract contract
    ) {
        var errors = new ArrayList<String>();
        if (envelope == null) {
            errors.add("envelope is required");
            return ValidationResult.failed(errors);
        }
        if (envelope.schemaVersion() != 1) {
            errors.add("schema_version must be 1");
        }
        if (envelope.workstreamId() == null || envelope.workstreamId().isBlank()) {
            errors.add("workstream_id is required");
        } else if (expectedWorkstreamId == null || expectedWorkstreamId.isBlank()
                || !expectedWorkstreamId.equals(envelope.workstreamId())) {
            errors.add("workstream_id does not match current workstream");
        }
        if (envelope.runId() == null || envelope.runId().isBlank()) {
            errors.add("run_id is required");
        } else if (expectedRunId == null || expectedRunId.isBlank()
                || !expectedRunId.equals(envelope.runId())) {
            errors.add("run_id does not match current execution");
        }
        if (envelope.nodeId() == null || envelope.nodeId().isBlank()) {
            errors.add("node_id is required");
        } else if (!contract.nodeId().equals(envelope.nodeId())) {
            errors.add("node_id does not match current node");
        }
        if (envelope.result() == null) {
            errors.add("result is required");
        } else {
            if (envelope.result().artifactType() == null || envelope.result().artifactType().isBlank()) {
                errors.add("result.artifact_type is required");
            } else if (!contract.emitsArtifact(envelope.result().artifactType())) {
                errors.add("result.artifact_type is not allowed for node " + contract.nodeId());
            }
            if (envelope.result().decision() == null || envelope.result().decision().isBlank()) {
                errors.add("result.decision is required");
            } else if (!contract.allowedDecisions().contains(envelope.result().decision())) {
                errors.add("result.decision is not allowed for node " + contract.nodeId());
            }
            if (envelope.result().summary() == null || envelope.result().summary().isBlank()) {
                errors.add("result.summary is required");
            }
            validatePayload(envelope.result(), errors);
        }
        return errors.isEmpty() ? ValidationResult.pass() : ValidationResult.failed(errors);
    }

    private static void validatePayload(AgentResultEnvelope.AgentResult result, List<String> errors) {
        switch (result) {
            case AgentResultEnvelope.TaskContractProposalResult proposal -> {
                requiredText(proposal.objective(), "result.objective", errors);
                if (proposal.scope() == null) {
                    errors.add("result.scope is required");
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
            }
            case AgentResultEnvelope.AgentOutputResult output -> {
                validateEvidencePayload(
                        output.lifecycleStatus(), output.checks(), output.evidence(), output.missingEvidence(),
                        output.mustFix(), output.acceptanceNotes(), output.openRisks(), output.changedFiles(),
                        output.verificationSummary(), output.failure(), errors);
                requiredList(output.rereadReasons(), "result.reread_reasons", false, errors);
                validateRereadReasons(output.rereadReasons(), errors);
            }
            case AgentResultEnvelope.PlanResult plan -> {
                validateEvidencePayload(
                        plan.lifecycleStatus(), plan.checks(), plan.evidence(), plan.missingEvidence(),
                        plan.mustFix(), plan.acceptanceNotes(), plan.openRisks(), plan.changedFiles(),
                        plan.verificationSummary(), plan.failure(), errors);
                if (plan.plan() == null) {
                    errors.add("result.plan is required");
                } else {
                    requiredList(plan.plan().implementationSteps(), "result.plan.implementation_steps", true, errors);
                    requiredList(plan.plan().editFiles(), "result.plan.edit_files", true, errors);
                    requiredList(plan.plan().referenceFiles(), "result.plan.reference_files", false, errors);
                    requiredList(plan.plan().fileLevelChanges(), "result.plan.file_level_changes", true, errors);
                    requiredList(plan.plan().validationPlan(), "result.plan.validation_plan", true, errors);
                    requiredText(plan.plan().rollbackNotes(), "result.plan.rollback_notes", errors);
                    requiredList(plan.plan().knownRisks(), "result.plan.known_risks", true, errors);
                    requiredList(plan.plan().humanReviewChecklist(), "result.plan.human_review_checklist", true, errors);
                }
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
            case AgentResultEnvelope.AcceptanceReviewResult review -> validateEvidencePayload(
                    review.lifecycleStatus(), review.checks(), review.evidence(), review.missingEvidence(),
                    review.mustFix(), review.acceptanceNotes(), review.openRisks(), review.changedFiles(),
                    review.verificationSummary(), review.failure(), errors);
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

    private static void validateScopeEntries(
            List<dev.dual.core.contract.TaskContract.ScopeEntry> entries,
            List<String> errors
    ) {
        if (entries == null) {
            return;
        }
        for (var entry : entries) {
            if (entry == null) {
                errors.add("result.scope.included contains null entry");
                continue;
            }
            requiredText(entry.path(), "result.scope.included.path", errors);
            if (entry.operation() == null) {
                errors.add("result.scope.included.operation is required");
            }
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
            List<String> errors
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
            errors.add("result.failure is required");
            return;
        }
        requiredList(failure.contractGaps(), "result.failure.contract_gaps", false, errors);
        requiredList(failure.outOfScopeFiles(), "result.failure.out_of_scope_files", false, errors);
        requiredList(failure.missingTests(), "result.failure.missing_tests", false, errors);
        requiredList(failure.contextGaps(), "result.failure.context_gaps", false, errors);
    }

    private static void validateRereadReasons(
            List<AgentResultEnvelope.RereadReason> reasons,
            List<String> errors
    ) {
        if (reasons == null) {
            return;
        }
        for (int index = 0; index < reasons.size(); index++) {
            var reason = reasons.get(index);
            if (reason == null) {
                errors.add("result.reread_reasons[" + index + "] is required");
                continue;
            }
            requiredText(reason.path(), "result.reread_reasons[" + index + "].path", errors);
            if (!ReadReuseContractReasons.isAllowed(reason.reason())) {
                errors.add("result.reread_reasons[" + index + "].reason is not allowed");
            }
        }
    }

    private static void requiredText(String value, String field, List<String> errors) {
        if (value == null || value.isBlank()) {
            errors.add(field + " is required");
        }
    }

    private static void requiredList(List<?> value, String field, boolean nonEmpty, List<String> errors) {
        if (value == null) {
            errors.add(field + " is required");
        } else if (nonEmpty && value.isEmpty()) {
            errors.add(field + " must not be empty");
        }
    }

    public record ValidationResult(boolean valid, List<String> errors) {
        static ValidationResult pass() {
            return new ValidationResult(true, List.of());
        }

        static ValidationResult failed(List<String> errors) {
            return new ValidationResult(false, List.copyOf(errors));
        }
    }
}
