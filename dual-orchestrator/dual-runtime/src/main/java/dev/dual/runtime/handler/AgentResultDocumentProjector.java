package dev.dual.runtime.handler;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Projects one validated agent result into the existing durable read models. */
final class AgentResultDocumentProjector {
    Map<String, Object> outputContract(
            AgentResultEnvelope envelope,
            NodeOutputContract contract,
            String handlerType,
            String nodeInstanceId,
            String sourceOutputRef
    ) {
        var result = envelope.result();
        var document = new LinkedHashMap<String, Object>();
        document.put("artifact_type", "agent_output_contract");
        document.put("schema_version", 1);
        document.put("workstream_id", envelope.workstreamId());
        document.put("run_id", envelope.runId());
        document.put("node_id", envelope.nodeId());
        document.put("node_instance_id", nodeInstanceId == null ? "" : nodeInstanceId);
        document.put("handler_type", handlerType);
        document.put("decision", result.decision());
        document.put("summary", result.summary());
        document.put("required_keys", List.copyOf(contract.requiredKeys()));
        document.put("allowed_decisions", List.copyOf(contract.allowedDecisions()));
        document.put("source_output_ref", sourceOutputRef);
        document.put("source_artifact_type", result.artifactType());
        document.put("generated_at", Instant.now().toString());
        document.put("control", Map.of("decision", result.decision(), "summary", result.summary()));
        document.put("control_source", Map.of("kind", "strict_agent_result_envelope"));

        switch (result) {
            case AgentResultEnvelope.TaskContractProposalResult proposal -> {
                evidenceDefaults(document);
                document.put("task_contract_proposal", taskContractProposalMap(proposal));
            }
            case AgentResultEnvelope.AgentOutputResult output -> {
                evidenceFields(document, output.lifecycleStatus(), output.blocking(), output.checks(), output.evidence(),
                        output.missingEvidence(), output.mustFix(), output.acceptanceNotes(), output.openRisks(),
                        output.changedFiles(), output.verificationSummary(), output.failure());
                document.put("contract_obligations", safeList(output.contractObligations()).stream()
                        .map(obligation -> Map.of("id", obligation.id(), "text", obligation.text()))
                        .toList());
                document.put("reread_reasons", output.rereadReasons().stream().map(this::rereadReason).toList());
            }
            case AgentResultEnvelope.PlanResult plan -> {
                evidenceFields(
                        document, plan.lifecycleStatus(), plan.blocking(), plan.checks(), plan.evidence(),
                        plan.missingEvidence(), plan.mustFix(), plan.acceptanceNotes(), plan.openRisks(),
                        plan.changedFiles(), plan.verificationSummary(), plan.failure());
                document.put("plan_artifact", planArtifact(plan));
            }
            case AgentResultEnvelope.SelfTestResult selfTest -> {
                document.put("lifecycle_status", selfTest.lifecycleStatus());
                document.put("blocking", false);
                document.put("checks_run", selfTest.checksRun());
                document.put("checks_skipped", selfTest.checksSkipped());
                document.put("evidence", selfTest.evidence());
                document.put("missing_evidence", selfTest.missingEvidence());
                document.put("acceptance_notes", selfTest.acceptanceNotes());
                document.put("verification_summary", selfTest.verificationSummary());
                evidenceDefaultsIfAbsent(document);
            }
            case AgentResultEnvelope.AcceptanceReviewResult review -> evidenceFields(
                    document, review.lifecycleStatus(), review.blocking(), review.checks(), review.evidence(),
                    review.missingEvidence(), review.mustFix(), review.acceptanceNotes(), review.openRisks(),
                    review.changedFiles(), review.verificationSummary(), review.failure());
            case AgentResultEnvelope.StageHandoffResult handoff -> {
                document.put("lifecycle_status", handoff.lifecycleStatus());
                document.put("blocking", false);
                document.put("completed_work", handoff.completedWork());
                document.put("important_decisions", handoff.importantDecisions());
                document.put("changed_files", handoff.changedFiles());
                document.put("verification_summary", handoff.verificationState());
                document.put("open_risks", handoff.openRisks());
                document.put("recommended_next_stage", handoff.recommendedNextStage());
                document.put("suggested_next_prompt", handoff.suggestedNextPrompt());
                evidenceDefaultsIfAbsent(document);
            }
        }
        return document;
    }

    TaskContractProposal taskContractProposal(AgentResultEnvelope.TaskContractProposalResult result) {
        return new TaskContractProposal(
                result.artifactType(),
                TaskContractProposal.SCHEMA_VERSION,
                result.objective(),
                new TaskContractProposal.Scope(result.scope().included(), result.scope().excluded()),
                proposalClauses(result.acceptanceCriteria()),
                proposalClauses(result.testRequirements()),
                proposalClauses(result.constraints()),
                proposalClauses(result.nonGoals()),
                proposalClauses(result.riskAreas()),
                result.evidenceRefs(),
                result.openQuestions(),
                revisionResponses(result.revisionResponses())
        );
    }

    private Map<String, Object> taskContractProposalMap(
            AgentResultEnvelope.TaskContractProposalResult result
    ) {
        var proposal = new LinkedHashMap<String, Object>();
        proposal.put("artifact_type", result.artifactType());
        proposal.put("schema_version", TaskContractProposal.SCHEMA_VERSION);
        proposal.put("objective", result.objective());
        proposal.put("scope", Map.of(
                "included", result.scope().included().stream()
                        .map(entry -> Map.of(
                                "path", entry.path(),
                                "operation", entry.operation().name()))
                        .toList(),
                "excluded", result.scope().excluded()));
        proposal.put("acceptance_criteria", clauseMaps(result.acceptanceCriteria()));
        proposal.put("test_requirements", clauseMaps(result.testRequirements()));
        proposal.put("constraints", clauseMaps(result.constraints()));
        proposal.put("non_goals", clauseMaps(result.nonGoals()));
        proposal.put("risk_areas", clauseMaps(result.riskAreas()));
        proposal.put("evidence_refs", result.evidenceRefs());
        proposal.put("open_questions", result.openQuestions());
        proposal.put("revision_responses", result.revisionResponses().stream()
                .map(response -> Map.of(
                        "review_obligation_id", response.reviewObligationId(),
                        "resolved_by_clause_ids", response.resolvedByClauseIds()))
                .toList());
        return proposal;
    }

    private static List<TaskContractProposal.ContractClause> proposalClauses(
            List<AgentResultEnvelope.ContractClause> clauses
    ) {
        return safeList(clauses).stream()
                .map(clause -> new TaskContractProposal.ContractClause(clause.id(), clause.text()))
                .toList();
    }

    private static List<TaskContractProposal.RevisionResponse> revisionResponses(
            List<AgentResultEnvelope.RevisionResponse> responses
    ) {
        return safeList(responses).stream()
                .map(response -> new TaskContractProposal.RevisionResponse(
                        response.reviewObligationId(), response.resolvedByClauseIds()))
                .toList();
    }

    private static List<Map<String, String>> clauseMaps(List<AgentResultEnvelope.ContractClause> clauses) {
        return safeList(clauses).stream()
                .map(clause -> Map.of("id", clause.id(), "text", clause.text()))
                .toList();
    }

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    Map<String, Object> planArtifact(AgentResultEnvelope.PlanResult result) {
        var plan = result.plan();
        var document = new LinkedHashMap<String, Object>();
        document.put("artifact_type", "plan_artifact");
        document.put("schema_version", 1);
        document.put("implementation_steps", plan.implementationSteps());
        document.put("edit_files", plan.editFiles());
        document.put("reference_files", plan.referenceFiles());
        document.put("file_level_changes", plan.fileLevelChanges());
        document.put("validation_plan", plan.validationPlan());
        document.put("rollback_notes", plan.rollbackNotes());
        document.put("known_risks", plan.knownRisks());
        document.put("human_review_checklist", plan.humanReviewChecklist());
        return document;
    }

    private void evidenceFields(
            Map<String, Object> document,
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
            AgentResultEnvelope.Failure failure
    ) {
        document.put("lifecycle_status", lifecycleStatus);
        document.put("blocking", blocking);
        document.put("checks", checks);
        document.put("evidence", evidence);
        document.put("missing_evidence", missingEvidence);
        document.put("must_fix", mustFix);
        document.put("acceptance_notes", acceptanceNotes);
        document.put("open_risks", openRisks);
        document.put("changed_files", changedFiles);
        document.put("verification_summary", verificationSummary);
        document.put("failure", failure(failure));
        document.put("checks_run", List.of());
        document.put("checks_skipped", List.of());
    }

    private static void evidenceDefaults(Map<String, Object> document) {
        document.put("blocking", false);
        document.put("checks_run", List.of("task_contract_proposal mechanical validation"));
        document.put("checks_skipped", List.of());
        document.put("evidence", List.of("canonical task_contract_proposal result"));
        document.put("missing_evidence", List.of());
        document.put("must_fix", List.of());
        document.put("acceptance_notes", List.of());
        document.put("open_risks", List.of());
        document.put("changed_files", List.of());
        document.put("verification_summary", "task contract proposal validated");
        document.put("failure", Map.of());
    }

    private static void evidenceDefaultsIfAbsent(Map<String, Object> document) {
        document.putIfAbsent("checks", List.of());
        document.putIfAbsent("checks_run", List.of());
        document.putIfAbsent("checks_skipped", List.of());
        document.putIfAbsent("evidence", List.of());
        document.putIfAbsent("missing_evidence", List.of());
        document.putIfAbsent("must_fix", List.of());
        document.putIfAbsent("acceptance_notes", List.of());
        document.putIfAbsent("open_risks", List.of());
        document.putIfAbsent("changed_files", List.of());
        document.putIfAbsent("failure", Map.of());
    }

    private Map<String, Object> failure(AgentResultEnvelope.Failure value) {
        if (value == null) {
            return Map.of();
        }
        var failure = new LinkedHashMap<String, Object>();
        failure.put("category_hint", value.categoryHint());
        failure.put("contract_gaps", value.contractGaps());
        failure.put("out_of_scope_files", value.outOfScopeFiles());
        failure.put("missing_tests", value.missingTests());
        failure.put("context_gaps", value.contextGaps());
        failure.put("reason", value.reason());
        return failure;
    }

    private Map<String, Object> rereadReason(AgentResultEnvelope.RereadReason value) {
        var reason = new LinkedHashMap<String, Object>();
        reason.put("path", value.path());
        reason.put("reason", value.reason());
        reason.put("stage", value.stage());
        reason.put("evidence", value.evidence());
        return reason;
    }

}
