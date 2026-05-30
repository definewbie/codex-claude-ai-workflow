package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.dual.core.arbitration.FailureClassification;
import dev.dual.core.contract.TaskContract;
import dev.dual.core.workflow.NodeResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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

        // Extract review decision and metadata from instance result map
        var instanceResult = context.instance().result();
        String reviewDecision = extractString(instanceResult, "decision", "REJECT");
        String reviewSummary = extractString(instanceResult, "summary", "");

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
        var result = new LinkedHashMap<String, Object>();
        result.put("category", classification.category().name());
        result.put("reasoning", classification.reasoning());
        result.put("suggested_action", classification.suggestedAction());
        result.put("matched_rule", classification.matchedRule());
        result.put("review_decision", reviewDecision);
        result.put("classification_ref", classificationRef);

        return NodeResult.completed(outcome, List.of(classificationRef), result);
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
}
