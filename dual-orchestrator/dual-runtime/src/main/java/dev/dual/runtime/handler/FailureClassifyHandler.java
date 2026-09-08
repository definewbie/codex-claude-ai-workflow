package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.dual.core.arbitration.FailureClassification;
import dev.dual.core.arbitration.FailureCategory;
import dev.dual.core.workflow.NodeResult;
import dev.dual.core.workflow.NodeStatus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * TaskHandler that classifies acceptance review failures using FailureArbiter.
 * Type: "acceptance.failure_classify"
 *
 * Reads TaskContract and ledger-routed acceptance evidence,
 * runs FailureArbiter, and writes failure-classification.json.
 * Produces compound outcome strings: REJECT_{CATEGORY}.
 */
public final class FailureClassifyHandler implements TaskHandler {

    private final FailureArbiter arbiter = new FailureArbiter();
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Override
    public String type() {
        return "acceptance.failure_classify";
    }

    @Override
    public NodeResult execute(TaskContext context) throws IOException {
        var workstreamDir = context.workspaceRoot()
                .resolve(".agent/workstreams").resolve(context.workstreamId());

        TaskContractProjectionVerifier.QualifiedTaskContract qualified = null;
        if (AgentRunGovernanceContext.requiresApprovedTaskContract(context)) {
            var resolution = TaskContractProjectionVerifier.resolve(context.workspaceRoot(), context.workstreamId());
            if (resolution.failure() != null || resolution.qualified().isEmpty()) {
                var failure = resolution.failure() == null
                        ? TaskContractProjectionVerifier.Failure.of(
                                "APPROVAL_BINDING_MISSING", "failure classification requires an approved task contract")
                        : resolution.failure();
                return qualificationFailure(failure);
            }
            qualified = resolution.qualified().orElseThrow();
        } else {
            var legacy = TaskContractProjectionVerifier.readLegacyProjection(
                    context.workspaceRoot(), context.workstreamId());
            if (legacy.failure() != null) {
                throw new IOException(legacy.failure().message());
            }
        }

        var evidenceRead = AgentEvidenceIndex.latestDecisionFromRefs(context.workspaceRoot(),
                context.workstreamId(), context.instance().inputRefs(), objectMapper);
        if (evidenceRead.entry() == null) {
            var missing = evidenceRead.missing();
            return failedEvidence(
                    missing ? "MISSING_CONTRACT_SIDECAR" : "INVALID_CONTRACT_SIDECAR",
                    missing
                            ? "No acceptance contract sidecar found in failure-classification input refs."
                            : evidenceRead.message(),
                    context,
                    evidenceRead
            );
        }

        var reviewMetadata = evidenceRead.entry().document();
        if (!"acceptance_review".equals(reviewMetadata.sourceArtifactType())) {
            return new NodeResult("ACCEPTANCE_CLASSIFICATION_INVALID", NodeStatus.FAILED, List.of(),
                    Map.of("reason", "ACCEPTANCE_CLASSIFICATION_INVALID"), "An acceptance_review sidecar is required");
        }
        var semanticViolation = AgentContractSemantics.validateOutputContract(reviewMetadata);
        if (semanticViolation.isPresent()) {
            var invalid = semanticViolation.get();
            return new NodeResult(invalid.errorCode(), NodeStatus.FAILED, List.of(),
                    Map.of("reason", invalid.errorCode(), "error", invalid.message()), invalid.message());
        }
        var reviewDecision = reviewMetadata.decision();
        if (!"REJECT".equals(reviewDecision)) {
            return failedDecision(reviewDecision, context, evidenceRead.entry().path());
        }
        var stageOutcome = reviewMetadata.canonicalStageOutcome().isBlank()
                ? StageOutcome.fromDecision(reviewDecision).name()
                : reviewMetadata.canonicalStageOutcome();
        var failure = reviewMetadata.failure();

        final FailureCategory category;
        try {
            category = FailureCategory.valueOf(failure.categoryHint());
        } catch (IllegalArgumentException invalid) {
            return new NodeResult("ACCEPTANCE_CLASSIFICATION_INVALID", NodeStatus.FAILED, List.of(),
                    Map.of("reason", "ACCEPTANCE_CLASSIFICATION_INVALID"), "Canonical acceptance category is required");
        }
        var supportingEvidence = new ArrayList<String>();
        supportingEvidence.addAll(failure.contractGaps());
        supportingEvidence.addAll(reviewMetadata.mustFix());
        supportingEvidence.addAll(failure.outOfScopeFiles());
        supportingEvidence.addAll(failure.missingTests());
        supportingEvidence.addAll(reviewMetadata.missingEvidence());
        supportingEvidence.addAll(failure.contextGaps());
        if (!failure.reason().isBlank()) supportingEvidence.add(failure.reason());
        var classification = arbiter.classify(category, supportingEvidence);
        Map<String, Object> baseline = Map.of();
        if (category == FailureCategory.CONTRACT_GAP || category == FailureCategory.CONTRACT_AMBIGUITY) {
            try {
                if (qualified == null) throw new IOException("contract repair requires a qualified contract");
                baseline = TaskContractRevisionContextResolver.captureAcceptanceReworkBaseline(
                        context.workspaceRoot(), context.workstreamId(), qualified, evidenceRead.entry());
            } catch (IOException invalid) {
                return new NodeResult("ACCEPTANCE_REWORK_BASELINE_INVALID", NodeStatus.FAILED, List.of(),
                        Map.of("reason", "ACCEPTANCE_REWORK_BASELINE_INVALID", "error", invalid.getMessage()),
                        invalid.getMessage());
            }
        }

        var artifactsDir = workstreamDir.resolve("artifacts");
        Files.createDirectories(artifactsDir);
        var classificationPath = artifactsDir.resolve("failure-classification.json");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(classificationPath.toFile(), classification);

        var outcome = "REJECT_" + classification.category().name();

        var classificationRef = ".agent/workstreams/" + context.workstreamId()
                + "/artifacts/failure-classification.json";
        var outputRefs = new ArrayList<String>();
        outputRefs.add(context.workspaceRoot().toAbsolutePath().normalize()
                .relativize(evidenceRead.entry().path()).toString());
        outputRefs.add(classificationRef);
        var result = new LinkedHashMap<String, Object>();
        result.putAll(baseline);
        result.put("category", classification.category().name());
        result.put("reasoning", classification.reasoning());
        result.put("suggested_action", classification.suggestedAction());
        result.put("matched_rule", classification.matchedRule());
        result.put("review_decision", reviewDecision);
        result.put("stage_outcome", stageOutcome);
        result.put("classification_ref", classificationRef);

        var candidateRef = writeCalibrationCandidateIfNeeded(
                context, workstreamDir, classification, classificationRef, stageOutcome);
        if (candidateRef != null) {
            outputRefs.add(candidateRef);
            result.put("calibration_candidate_ref", candidateRef);
        }

        var summaryRef = writeCurrentFailureSummary(
                context, workstreamDir, classification, classificationRef, candidateRef, stageOutcome);
        outputRefs.add(summaryRef);
        result.put("failure_summary_ref", summaryRef);

        return NodeResult.completed(outcome, outputRefs, result);
    }

    private NodeResult qualificationFailure(TaskContractProjectionVerifier.Failure failure) {
        return new NodeResult(
                TaskContractProjectionVerifier.FAILURE_OUTCOME,
                NodeStatus.FAILED,
                List.of(),
                Map.of(
                        "failure_code", TaskContractProjectionVerifier.FAILURE_OUTCOME,
                        "reason", failure.reason(),
                        "error", failure.message()),
                failure.message());
    }

    private NodeResult failedEvidence(String outcome, String message, TaskContext context,
                                      AgentEvidenceIndex.DecisionRead read) {
        var parseError = read.errorCode().isBlank()
                ? Map.of()
                : Map.of(
                    "code", read.errorCode(),
                    "message", read.message(),
                    "path", read.path()
                );
        return new NodeResult(outcome, NodeStatus.FAILED, List.of(), Map.of(
                "reason", outcome,
                "message", message,
                "input_refs", context.instance().inputRefs(),
                "parse_error", parseError
        ), message);
    }

    private NodeResult failedDecision(String decision, TaskContext context, Path sidecarPath) {
        var outcome = "ACCEPTANCE_CLASSIFICATION_NOT_REQUIRED";
        var message = "Failure classification requires a REJECT acceptance decision, but received: " + decision;
        return new NodeResult(outcome, NodeStatus.FAILED, List.of(), Map.of(
                "reason", outcome,
                "message", message,
                "review_decision", decision,
                "input_refs", context.instance().inputRefs(),
                "acceptance_sidecar", context.workspaceRoot().toAbsolutePath().normalize()
                        .relativize(sidecarPath).toString()
        ), message);
    }


    private String writeCalibrationCandidateIfNeeded(
            TaskContext context,
            Path workstreamDir,
            FailureClassification classification,
            String classificationRef,
            String stageOutcome
    ) throws IOException {
        var updateType = proposedUpdateType(classification.category());
        if (updateType == null) {
            return null;
        }

        var candidateId = context.instance().nodeInstanceId() + "-"
                + classification.category().name().toLowerCase().replace('_', '-');
        var candidatesDir = workstreamDir.resolve("calibration").resolve("candidates");
        Files.createDirectories(candidatesDir);

        var jsonRef = ".agent/workstreams/" + context.workstreamId()
                + "/calibration/candidates/" + candidateId + ".json";
        var mdRef = ".agent/workstreams/" + context.workstreamId()
                + "/calibration/candidates/" + candidateId + ".md";

        var candidate = new LinkedHashMap<String, Object>();
        candidate.put("schemaVersion", 1);
        candidate.put("candidateId", candidateId);
        candidate.put("sourceWorkstreamId", context.workstreamId());
        candidate.put("sourceNodeId", context.instance().nodeDefinitionId());
        candidate.put("failureCategory", classification.category().name());
        candidate.put("reviewDecision", classification.reviewDecision());
        candidate.put("stageOutcome", stageOutcome);
        candidate.put("matchedRule", classification.matchedRule());
        candidate.put("proposedUpdateType", updateType);
        candidate.put("reasoning", classification.reasoning());
        candidate.put("suggestedAction", classification.suggestedAction());
        candidate.put("evidenceRefs", List.of(classificationRef));
        candidate.put("status", "DRAFT");

        objectMapper.writerWithDefaultPrettyPrinter()
                .writeValue(candidatesDir.resolve(candidateId + ".json").toFile(), candidate);
        Files.writeString(candidatesDir.resolve(candidateId + ".md"),
                renderCalibrationCandidateMarkdown(candidate, mdRef), StandardCharsets.UTF_8);
        return jsonRef;
    }

    private String proposedUpdateType(FailureCategory category) {
        return switch (category) {
            case CONTRACT_GAP, CONTRACT_AMBIGUITY -> "CONTRACT_TEMPLATE";
            case CONTEXT_INSUFFICIENT -> "CONTEXT_RULE";
            case IMPLEMENTATION_BUG -> "REGRESSION_TEST";
            case VERIFICATION_INSUFFICIENT -> null;
            case TOOL_LIMITATION, REVIEW_NOISE -> null;
        };
    }

    private String renderCalibrationCandidateMarkdown(Map<String, Object> candidate, String mdRef) {
        return """
                # Calibration Candidate

                - Candidate: %s
                - Status: %s
                - Failure Category: %s
                - Proposed Update Type: %s
                - Review Decision: %s
                - Stage Outcome: %s
                - Source Node: %s

                ## Reasoning

                %s

                ## Suggested Action

                %s

                ## Artifact

                %s
                """.formatted(
                candidate.get("candidateId"),
                candidate.get("status"),
                candidate.get("failureCategory"),
                candidate.get("proposedUpdateType"),
                candidate.get("reviewDecision"),
                candidate.get("stageOutcome"),
                candidate.get("sourceNodeId"),
                candidate.get("reasoning"),
                candidate.get("suggestedAction"),
                mdRef
        );
    }

    private String writeCurrentFailureSummary(
            TaskContext context,
            Path workstreamDir,
            FailureClassification classification,
            String classificationRef,
            String candidateRef,
            String stageOutcome
    ) throws IOException {
        var recoveryDir = workstreamDir.resolve("recovery");
        Files.createDirectories(recoveryDir);
        var summaryRef = ".agent/workstreams/" + context.workstreamId()
                + "/recovery/current-failure.md";
        var candidateLine = candidateRef == null ? "- Calibration Candidate: N/A\n"
                : "- Calibration Candidate: " + candidateRef + "\n";
        var content = """
                # Current Failure

                - Node: %s
                - Decision: %s
                - Stage Outcome: %s
                - Category: %s
                - Matched Rule: %d
                - Classification: %s
                %s
                ## Reasoning

                %s

                ## Suggested Action

                %s
                """.formatted(
                context.instance().nodeDefinitionId(),
                classification.reviewDecision(),
                stageOutcome,
                classification.category().name(),
                classification.matchedRule(),
                classificationRef,
                candidateLine,
                classification.reasoning(),
                classification.suggestedAction()
        );
        Files.writeString(recoveryDir.resolve("current-failure.md"), content, StandardCharsets.UTF_8);
        return summaryRef;
    }
}
