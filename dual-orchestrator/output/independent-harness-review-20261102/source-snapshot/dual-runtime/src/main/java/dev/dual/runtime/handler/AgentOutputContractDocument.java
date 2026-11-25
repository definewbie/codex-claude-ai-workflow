package dev.dual.runtime.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dual.core.contract.TaskContract;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Typed reader for agent output sidecars.
 *
 * Map-based JSON handling is kept at this boundary. Runtime routing,
 * classification, and human guidance should consume this record instead of
 * repeatedly reading stringly-typed fields from raw maps.
 */
public record AgentOutputContractDocument(
        String workstreamId,
        String runId,
        String nodeId,
        String nodeInstanceId,
        String stageOutcome,
        String lifecycleStatus,
        String decision,
        String summary,
        String sourceArtifactType,
        String sourceOutputRef,
        boolean blocking,
        List<String> checks,
        List<String> evidence,
        List<String> mustFix,
        List<AgentResultEnvelope.ReviewObligation> contractObligations,
        List<String> missingEvidence,
        List<String> acceptanceNotes,
        List<String> openRisks,
        List<String> changedFiles,
        String verificationSummary,
        List<String> completedWork,
        List<String> importantDecisions,
        String recommendedNextStage,
        String suggestedNextPrompt,
        Map<String, String> control,
        FailureFields failure,
        List<RereadReason> rereadReasons,
        TaskContractProposalReference reviewSubject,
        TaskContractProposal taskContractProposal,
        RepositoryFactBinding.Reference repositoryFactBinding,
        ProposalDerivation proposalDerivation,
        PlanArtifactDocument planArtifact,
        AgentResultEnvelope.Design design,
        List<AgentResultEnvelope.AuthoredRevisionResponse> authoredRevisionResponses,
        AuthoredExecutionContext.Reference authoredContext,
        List<AgentResultEnvelope.VerificationCheck> verificationChecks
) {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    static Optional<AgentOutputContractDocument> read(Path path, ObjectMapper objectMapper) {
        try {
            return Optional.of(from(objectMapper.readValue(path.toFile(), MAP_TYPE)));
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    public static AgentOutputContractDocument from(Map<String, Object> document) {
        return new AgentOutputContractDocument(
                text(document.get("workstream_id")),
                text(document.get("run_id")),
                text(document.get("node_id")),
                text(document.get("node_instance_id")),
                firstNonBlank(document.get("stage_outcome"), document.get("lifecycle_status")),
                text(document.get("lifecycle_status")),
                text(document.get("decision")),
                text(document.get("summary")),
                text(document.get("source_artifact_type")),
                text(document.get("source_output_ref")),
                booleanValue(document.get("blocking")),
                firstNonEmptyList(document.get("checks"), document.get("checks_run")),
                stringList(document.get("evidence")),
                stringList(document.get("must_fix")),
                reviewObligationList(document.get("contract_obligations")),
                stringList(document.get("missing_evidence")),
                stringList(document.get("acceptance_notes")),
                stringList(document.get("open_risks")),
                stringList(document.get("changed_files")),
                text(document.get("verification_summary")),
                stringList(document.get("completed_work")),
                stringList(document.get("important_decisions")),
                text(document.get("recommended_next_stage")),
                text(document.get("suggested_next_prompt")),
                stringMap(document.get("control")),
                FailureFields.from(objectMap(document.get("failure"))),
                rereadReasonList(document.get("reread_reasons")),
                TaskContractProposalReference.fromMap(objectMap(document.get("review_subject"))).orElse(null),
                taskContractProposal(objectMap(document.get("task_contract_proposal"))),
                RepositoryFactBinding.Reference.fromMap(objectMap(document.get("repository_fact_binding"))),
                ProposalDerivation.from(objectMap(document.get("proposal_derivation"))),
                planArtifact(objectMap(document.get("plan_artifact"))),
                design(objectMap(document.get("design"))),
                authoredResponses(document.get("revision_responses")),
                authoredReference(document.get("authored_context")),
                verificationChecks(document.get("verification_checks"))
        );
    }

    public record ProposalDerivation(
            String kind,
            String baseProposalId,
            String baseProposalSha256,
            TaskContractProposalReference baseProposalReference,
            // Historical field name: authored revision raw output, including goal_reproposal.
            String patchSourceOutputRef
    ) {
        static ProposalDerivation from(Map<String, Object> document) {
            if (document.isEmpty()) {
                return null;
            }
            return new ProposalDerivation(
                    text(document.get("kind")),
                    text(document.get("base_proposal_id")),
                    text(document.get("base_proposal_sha256")),
                    TaskContractProposalReference.fromMap(
                            objectMap(document.get("base_proposal_reference"))).orElse(null),
                    text(document.get("patch_source_output_ref")));
        }

        boolean validFor(TaskContractProposalReference reference, String sourceOutputRef) {
            return ("revision_patch_merge".equals(kind) || "goal_reproposal".equals(kind))
                    && reference != null
                    && reference.proposalId().equals(baseProposalId)
                    && reference.sha256().equals(baseProposalSha256)
                    && reference.equals(baseProposalReference)
                    && sourceOutputRef != null
                    && sourceOutputRef.equals(patchSourceOutputRef);
        }

        Map<String, Object> toMap() {
            var map = new LinkedHashMap<String, Object>();
            map.put("kind", kind);
            map.put("base_proposal_id", baseProposalId);
            map.put("base_proposal_sha256", baseProposalSha256);
            map.put("base_proposal_reference", baseProposalReference.toMap());
            map.put("patch_source_output_ref", patchSourceOutputRef);
            return Map.copyOf(map);
        }
    }

    public boolean hasValidatedLineage(String expectedWorkstreamId) {
        var normalizedSource = sourceOutputRef.replace('\\', '/');
        return expectedWorkstreamId != null
                && !expectedWorkstreamId.isBlank()
                && expectedWorkstreamId.equals(workstreamId)
                && !runId.isBlank()
                && !nodeId.isBlank()
                && !nodeInstanceId.isBlank()
                && normalizedSource.contains("/runs/" + runId + "/");
    }

    public boolean hasValidatedLineage(
            String expectedWorkstreamId,
            Path workspaceRoot,
            Path sidecarPath
    ) {
        if (!hasValidatedLineage(expectedWorkstreamId)
                || workspaceRoot == null
                || sidecarPath == null) {
            return false;
        }
        var root = workspaceRoot.toAbsolutePath().normalize();
        var source = root.resolve(sourceOutputRef).normalize();
        if (!source.startsWith(root)) {
            return false;
        }
        return AgentOutputContractSidecar.pathFor(source)
                .equals(sidecarPath.toAbsolutePath().normalize());
    }

    public boolean hasDecision() {
        return !decision.isBlank();
    }

    public String canonicalStageOutcome() {
        return stageOutcome.isBlank() ? lifecycleStatus : stageOutcome;
    }

    public boolean hasRequiredTypedPayload() {
        if (("design_result".equals(sourceArtifactType) || "plan_result".equals(sourceArtifactType))
                && !AuthoredRevisionValidator.shapeErrors(authoredRevisionResponses).isEmpty()) return false;
        if ("design_result".equals(sourceArtifactType)) {
            return DesignScopeValidator.shapeErrors(design).isEmpty();
        }
        if (!"plan_result".equals(sourceArtifactType)) {
            return true;
        }
        return planArtifact != null
                && "plan_artifact".equals(planArtifact.artifactType())
                && planArtifact.schemaVersion() == 2
                && planArtifact.missingRequiredField().isEmpty();
    }

    private static PlanArtifactDocument planArtifact(Map<String, Object> document) {
        return document.isEmpty() ? null : PlanArtifactDocument.from(document);
    }

    private static AuthoredExecutionContext.Reference authoredReference(Object value) {
        if (value == null) return null;
        return AuthoredExecutionContext.JSON.convertValue(value, AuthoredExecutionContext.Reference.class);
    }

    private static List<AgentResultEnvelope.VerificationCheck> verificationChecks(Object value) {
        if (value == null) return List.of();
        return AuthoredExecutionContext.JSON.convertValue(value, new TypeReference<List<AgentResultEnvelope.VerificationCheck>>() {});
    }

    private static List<AgentResultEnvelope.AuthoredRevisionResponse> authoredResponses(Object value) {
        if (!(value instanceof List<?> values)) return null;
        var result = new ArrayList<AgentResultEnvelope.AuthoredRevisionResponse>();
        for (var entry : values) {
            var map = objectMap(entry);
            result.add(new AgentResultEnvelope.AuthoredRevisionResponse(text(map.get("obligation_id")),
                    AgentResultEnvelope.AuthoredRevisionDisposition.valueOf(text(map.get("disposition"))), text(map.get("evidence"))));
        }
        return List.copyOf(result);
    }

    private static AgentResultEnvelope.Design design(Map<String, Object> document) {
        if (!(document.get("planned_files") instanceof List<?> files)) {
            return null;
        }
        var planned = new ArrayList<AgentResultEnvelope.PlannedFile>();
        for (var value : files) {
            var file = objectMap(value);
            TaskContract.ScopeOperation operation = null;
            try {
                operation = TaskContract.ScopeOperation.valueOf(text(file.get("operation")));
            } catch (IllegalArgumentException ignored) {
                // Invalid operations remain invalid, never inferred from the path.
            }
            planned.add(new AgentResultEnvelope.PlannedFile(
                    file.get("path") instanceof String path ? path : null,
                    operation, file.get("responsibility") instanceof String responsibility ? responsibility : null));
        }
        return new AgentResultEnvelope.Design(List.copyOf(planned));
    }

    private static TaskContractProposal taskContractProposal(Map<String, Object> document) {
        if (document.isEmpty()) {
            return null;
        }
        var scope = objectMap(document.get("scope"));
        return new TaskContractProposal(
                text(document.get("artifact_type")),
                integerValue(document.get("schema_version")),
                text(document.get("objective")),
                new TaskContractProposal.Scope(
                        scopeEntries(scope.get("included")),
                        stringList(scope.get("excluded"))),
                contractClauseList(document.get("acceptance_criteria")),
                contractClauseList(document.get("test_requirements")),
                contractClauseList(document.get("constraints")),
                contractClauseList(document.get("non_goals")),
                contractClauseList(document.get("risk_areas")),
                stringList(document.get("evidence_refs")),
                stringList(document.get("open_questions")),
                revisionResponseList(document.get("revision_responses")));
    }

    private static List<TaskContract.ScopeEntry> scopeEntries(Object value) {
        if (!(value instanceof List<?> items)) {
            return List.of();
        }
        var entries = new ArrayList<TaskContract.ScopeEntry>();
        for (var item : items) {
            var entry = objectMap(item);
            var path = text(entry.get("path"));
            TaskContract.ScopeOperation operation = null;
            try {
                operation = TaskContract.ScopeOperation.valueOf(text(entry.get("operation")));
            } catch (IllegalArgumentException ignored) {
                // The proposal validator reports the missing/invalid operation.
            }
            entries.add(new TaskContract.ScopeEntry(path, operation));
        }
        return List.copyOf(entries);
    }

    boolean isStageHandoffArtifact() {
        return "stage_handoff".equals(sourceArtifactType);
    }

    record FailureFields(
            String categoryHint,
            AgentResultEnvelope.VerificationRemediation verificationRemediation,
            List<String> contractGaps,
            List<String> outOfScopeFiles,
            List<String> missingTests,
            List<String> contextGaps,
            String reason
    ) {
        static FailureFields from(Map<String, Object> document) {
            return new FailureFields(
                    document.get("category_hint") instanceof String category ? category : "",
                    verificationRemediation(document.get("verification_remediation")),
                    stringList(document.get("contract_gaps")),
                    stringList(document.get("out_of_scope_files")),
                    stringList(document.get("missing_tests")),
                    stringList(document.get("context_gaps")),
                    text(document.get("reason"))
            );
        }

        static FailureFields empty() {
            return new FailureFields("", AgentResultEnvelope.VerificationRemediation.NONE,
                    List.of(), List.of(), List.of(), List.of(), "");
        }

        Map<String, Object> toMap() {
            var document = new LinkedHashMap<String, Object>();
            document.put("category_hint", categoryHint);
            document.put("verification_remediation", verificationRemediation == null
                    ? "" : verificationRemediation.name());
            document.put("contract_gaps", contractGaps);
            document.put("out_of_scope_files", outOfScopeFiles);
            document.put("missing_tests", missingTests);
            document.put("context_gaps", contextGaps);
            document.put("reason", reason);
            return document;
        }

        boolean isEmpty() {
            return categoryHint.isBlank()
                    && (verificationRemediation == null
                    || verificationRemediation == AgentResultEnvelope.VerificationRemediation.NONE)
                    && contractGaps.isEmpty()
                    && outOfScopeFiles.isEmpty()
                    && missingTests.isEmpty()
                    && contextGaps.isEmpty()
                    && reason.isBlank();
        }

        private static AgentResultEnvelope.VerificationRemediation verificationRemediation(Object value) {
            var text = value == null ? "" : String.valueOf(value);
            if (text.isEmpty()) {
                return null;
            }
            try {
                return AgentResultEnvelope.VerificationRemediation.valueOf(text);
            } catch (IllegalArgumentException invalid) {
                throw new IllegalArgumentException("Unknown verification_remediation: " + text, invalid);
            }
        }
    }

    /**
     * One declared reason an agent re-read a file already covered by the read-reuse
     * contract. Optional structured field; presence is validated against the
     * allowed-reason catalog (see {@code ReadReuseContractReasons}).
     */
    public record RereadReason(String path, String reason, String stage, String evidence) {
        static RereadReason from(Map<String, Object> document) {
            return new RereadReason(
                    text(document.get("path")),
                    text(document.get("reason")),
                    text(document.get("stage")),
                    text(document.get("evidence")));
        }

        Map<String, Object> toMap() {
            var map = new LinkedHashMap<String, Object>();
            map.put("path", path);
            map.put("reason", reason);
            map.put("stage", stage);
            map.put("evidence", evidence);
            return map;
        }
    }

    static List<RereadReason> rereadReasonList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        var out = new ArrayList<RereadReason>();
        for (var item : list) {
            if (item instanceof Map<?, ?> map) {
                var copy = new LinkedHashMap<String, Object>();
                for (var e : map.entrySet()) {
                    if (e.getKey() instanceof String key) {
                        copy.put(key, e.getValue());
                    }
                }
                out.add(RereadReason.from(copy));
            }
        }
        return List.copyOf(out);
    }

    private static List<TaskContractProposal.ContractClause> contractClauseList(Object value) {
        if (!(value instanceof List<?> items)) {
            return List.of();
        }
        var clauses = new ArrayList<TaskContractProposal.ContractClause>();
        for (var item : items) {
            var document = objectMap(item);
            if (!document.isEmpty()) {
                clauses.add(new TaskContractProposal.ContractClause(
                        text(document.get("id")), text(document.get("text"))));
            }
        }
        return List.copyOf(clauses);
    }

    private static List<TaskContractProposal.RevisionResponse> revisionResponseList(Object value) {
        if (!(value instanceof List<?> items)) {
            return List.of();
        }
        var responses = new ArrayList<TaskContractProposal.RevisionResponse>();
        for (var item : items) {
            var document = objectMap(item);
            if (!document.isEmpty()) {
                responses.add(new TaskContractProposal.RevisionResponse(
                        text(document.get("review_obligation_id")),
                        stringList(document.get("resolved_by_clause_ids"))));
            }
        }
        return List.copyOf(responses);
    }

    private static List<AgentResultEnvelope.ReviewObligation> reviewObligationList(Object value) {
        if (!(value instanceof List<?> items)) {
            return List.of();
        }
        var obligations = new ArrayList<AgentResultEnvelope.ReviewObligation>();
        for (var item : items) {
            var document = objectMap(item);
            if (!document.isEmpty()) {
                obligations.add(new AgentResultEnvelope.ReviewObligation(
                        text(document.get("id")), text(document.get("text"))));
            }
        }
        return List.copyOf(obligations);
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> items)) {
            return List.of();
        }
        var result = new ArrayList<String>();
        for (var item : items) {
            var text = text(item);
            if (!text.isBlank()) {
                result.add(text);
            }
        }
        return List.copyOf(result);
    }

    private static List<String> firstNonEmptyList(Object first, Object second) {
        var firstList = stringList(first);
        return firstList.isEmpty() ? stringList(second) : firstList;
    }

    private static Map<String, String> stringMap(Object value) {
        if (!(value instanceof Map<?, ?> source)) {
            return Map.of();
        }
        var copy = new LinkedHashMap<String, String>();
        for (var entry : source.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                copy.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
            }
        }
        return copy;
    }

    private static Map<String, Object> objectMap(Object value) {
        if (!(value instanceof Map<?, ?> source)) {
            return Map.of();
        }
        var copy = new LinkedHashMap<String, Object>();
        for (var entry : source.entrySet()) {
            if (entry.getKey() instanceof String key) {
                copy.put(key, entry.getValue());
            }
        }
        return copy;
    }

    private static boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(text(value));
    }

    private static int integerValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(text(value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String firstNonBlank(Object first, Object second) {
        var firstText = text(first);
        return firstText.isBlank() ? text(second) : firstText;
    }
}
