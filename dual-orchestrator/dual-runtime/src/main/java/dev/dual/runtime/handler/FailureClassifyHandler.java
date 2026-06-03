package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.dual.core.arbitration.FailureClassification;
import dev.dual.core.arbitration.FailureCategory;
import dev.dual.core.contract.TaskContract;
import dev.dual.core.workflow.NodeResult;

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
 * Reads TaskContract artifact and review metadata from instance result map,
 * runs FailureArbiter, writes failure-classification.json artifact.
 * Produces compound outcome strings: REJECT_{CATEGORY} or NOTED_{CATEGORY}.
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
                .resolve(".agent")
                .resolve("workstreams")
                .resolve(context.workstreamId());

        // Read TaskContract artifact
        var contractPath = workstreamDir.resolve("artifacts").resolve("task-contract.json");
        TaskContract taskContract = null;
        if (Files.exists(contractPath)) {
            taskContract = objectMapper.readValue(contractPath.toFile(), TaskContract.class);
        }

        // Extract review decision and metadata from instance result map or upstream
        // acceptance output sidecar. The classify node result is often empty
        // because routing comes from the previous decision node.
        var instanceResult = context.instance().result();
        var reviewMetadata = readReviewMetadata(context);
        String reviewDecision = extractString(instanceResult, "decision",
                extractString(reviewMetadata, "decision", "REJECT"));
        String reviewSummary = extractString(instanceResult, "summary",
                extractString(reviewMetadata, "summary", ""));

        @SuppressWarnings("unchecked")
        List<String> contractGaps = (List<String>) instanceResult.getOrDefault("contract_gaps", List.of());
        @SuppressWarnings("unchecked")
        List<String> outOfScopeFiles = (List<String>) instanceResult.getOrDefault("out_of_scope_files", List.of());
        @SuppressWarnings("unchecked")
        List<String> missingTests = (List<String>) instanceResult.getOrDefault("missing_tests", List.of());

        // Read git evidence for changed file count
        int changedFileCount = readChangedFileCount(workstreamDir);

        // Classify
        var classification = arbiter.classify(
                contractGaps, outOfScopeFiles, missingTests,
                reviewSummary, taskContract, changedFileCount, reviewDecision
        );

        // Write artifact
        var artifactsDir = workstreamDir.resolve("artifacts");
        Files.createDirectories(artifactsDir);
        var classificationPath = artifactsDir.resolve("failure-classification.json");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(classificationPath.toFile(), classification);

        // Build compound outcome
        var prefix = "REJECT".equals(reviewDecision) ? "REJECT_" : "NOTED_";
        var outcome = prefix + classification.category().name();

        var classificationRef = ".agent/workstreams/" + context.workstreamId()
                + "/artifacts/failure-classification.json";
        var outputRefs = new ArrayList<String>();
        outputRefs.add(classificationRef);
        var result = new LinkedHashMap<String, Object>();
        result.put("category", classification.category().name());
        result.put("reasoning", classification.reasoning());
        result.put("suggested_action", classification.suggestedAction());
        result.put("matched_rule", classification.matchedRule());
        result.put("review_decision", reviewDecision);
        result.put("classification_ref", classificationRef);

        var candidateRef = writeCalibrationCandidateIfNeeded(
                context, workstreamDir, classification, classificationRef);
        if (candidateRef != null) {
            outputRefs.add(candidateRef);
            result.put("calibration_candidate_ref", candidateRef);
        }

        if ("REJECT".equals(reviewDecision)) {
            var summaryRef = writeCurrentFailureSummary(
                    context, workstreamDir, classification, classificationRef, candidateRef);
            outputRefs.add(summaryRef);
            result.put("failure_summary_ref", summaryRef);
        }

        return NodeResult.completed(outcome, outputRefs, result);
    }

    private Map<String, Object> readReviewMetadata(TaskContext context) throws IOException {
        for (var inputRef : context.instance().inputRefs()) {
            if (!inputRef.endsWith(".contract.json")) {
                continue;
            }
            var path = context.workspaceRoot().resolve(inputRef).normalize();
            if (!Files.isRegularFile(path)) {
                continue;
            }
            var json = objectMapper.readTree(path.toFile());
            var metadata = new LinkedHashMap<String, Object>();
            if (json.hasNonNull("decision")) {
                metadata.put("decision", json.get("decision").asText());
            }
            if (json.hasNonNull("summary")) {
                metadata.put("summary", json.get("summary").asText());
            }
            return metadata;
        }
        return Map.of();
    }

    private String extractString(Map<String, Object> map, String key, String defaultValue) {
        var value = map.get(key);
        return value == null ? defaultValue : String.valueOf(value);
    }

    private int readChangedFileCount(Path workstreamDir) throws IOException {
        // Try git-evidence/changed-files.txt first (from TestLedgerFixture pattern)
        var changedFilesPath = workstreamDir.resolve("artifacts")
                .resolve("git-evidence").resolve("changed-files.txt");
        if (Files.exists(changedFilesPath)) {
            return (int) Files.readString(changedFilesPath, StandardCharsets.UTF_8)
                    .lines()
                    .filter(line -> !line.isBlank())
                    .count();
        }

        // Fallback: git-evidence.txt (simple format)
        var gitEvidencePath = workstreamDir.resolve("git-evidence.txt");
        if (Files.exists(gitEvidencePath)) {
            var content = Files.readString(gitEvidencePath, StandardCharsets.UTF_8);
            return (int) content.lines()
                    .map(String::trim)
                    .filter(line -> !line.isBlank())
                    .filter(line -> !line.isEmpty() && Character.isLetter(line.charAt(0)))
                    .count();
        }

        return -1;
    }

    private String writeCalibrationCandidateIfNeeded(
            TaskContext context,
            Path workstreamDir,
            FailureClassification classification,
            String classificationRef
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
            String candidateRef
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
