package dev.dual.runtime.handler;

import dev.dual.core.contract.ContractStatus;
import dev.dual.core.contract.TaskContract;
import dev.dual.core.contract.TaskContractValidator;
import dev.dual.core.workflow.NodeResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class ContractCompileHandler implements TaskHandler {

    private static final Pattern FILE_REF_PATTERN = Pattern.compile("`([^`]+)`");
    private static final Pattern CONSTRAINT_PATTERN = Pattern.compile(
            "(?i)(under|no more than|max|chinese|中文)"
    );

    @Override
    public String type() {
        return "contract.compile";
    }

    @Override
    public NodeResult execute(TaskContext context) throws IOException {
        var workstreamDir = context.workspaceRoot()
                .resolve(".agent")
                .resolve("workstreams")
                .resolve(context.workstreamId());

        var requirementPath = workstreamDir.resolve("requirement.md");
        if (!Files.exists(requirementPath)) {
            return new NodeResult(
                    "failed", dev.dual.core.workflow.NodeStatus.FAILED,
                    List.of(),
                    Map.of("error", "requirement.md not found in workstream directory"),
                    "requirement.md not found: " + requirementPath
            );
        }

        var requirementText = Files.readString(requirementPath, StandardCharsets.UTF_8);
        var contract = compile(requirementText, context.workstreamId());

        // Validate and potentially patch
        var errors = TaskContractValidator.validate(contract);
        if (!errors.isEmpty()) {
            // If AC is empty and READY_FOR_REVIEW, add default AC
            if (contract.status() == ContractStatus.READY_FOR_REVIEW
                    && contract.acceptanceCriteria().isEmpty()) {
                var patchedAc = new ArrayList<>(contract.acceptanceCriteria());
                patchedAc.add("Implementation achieves: " + contract.objective());
                patchedAc.add("No files changed outside scope");
                patchedAc.add("Tests pass");
                contract = new TaskContract(
                        contract.id(), contract.version(), contract.createdAt(),
                        contract.objective(), contract.scope(), patchedAc,
                        contract.testRequirements(), contract.constraints(),
                        contract.nonGoals(), contract.riskAreas(),
                        contract.ambiguityNotes(), contract.humanQuestions(),
                        contract.status(), contract.compilationPass(), contract.source()
                );
                errors = TaskContractValidator.validate(contract);
            }

            // If still failing, set to NEEDS_CLARIFICATION
            if (!errors.isEmpty()) {
                var ambiguityNotes = new ArrayList<>(contract.ambiguityNotes());
                ambiguityNotes.addAll(errors);
                var humanQuestions = new ArrayList<>(contract.humanQuestions());
                humanQuestions.add("Contract compilation produced validation errors — please clarify the requirement");
                contract = new TaskContract(
                        contract.id(), contract.version(), contract.createdAt(),
                        contract.objective(), contract.scope(), contract.acceptanceCriteria(),
                        contract.testRequirements(), contract.constraints(),
                        contract.nonGoals(), contract.riskAreas(),
                        ambiguityNotes, humanQuestions,
                        ContractStatus.NEEDS_CLARIFICATION, contract.compilationPass(), contract.source()
                );
            }
        }

        // Write artifacts
        var artifactsDir = workstreamDir.resolve("artifacts");
        TaskContractArtifactWriter.writeJson(contract, artifactsDir);
        TaskContractArtifactWriter.writeMarkdown(contract, artifactsDir);

        var now = Instant.now();
        var result = new LinkedHashMap<String, Object>();
        result.put("contract_id", contract.id());
        result.put("contract_status", contract.status().name());
        result.put("contract_version", contract.version());
        result.put("compilation_pass", contract.compilationPass());
        result.put("artifacts", List.of(
                ".agent/workstreams/" + context.workstreamId() + "/artifacts/task-contract.json",
                ".agent/workstreams/" + context.workstreamId() + "/artifacts/task-contract.md"
        ));
        result.put("generated_at", now.toString());

        return NodeResult.completed("completed", List.of(
                ".agent/workstreams/" + context.workstreamId() + "/artifacts/task-contract.json",
                ".agent/workstreams/" + context.workstreamId() + "/artifacts/task-contract.md"
        ), result);
    }

    TaskContract compile(String requirementText, String workstreamId) {
        var objective = extractObjective(requirementText);
        var included = extractIncludedScope(requirementText);
        var constraints = extractConstraints(requirementText);
        var acceptanceCriteria = List.of(
                "Implementation achieves: " + objective,
                "No files changed outside scope",
                "Tests pass"
        );

        return new TaskContract(
                "tc-" + workstreamId + "-v1",
                1,
                Instant.now(),
                objective,
                new TaskContract.Scope(included, List.of()),
                acceptanceCriteria,
                List.of(),
                constraints,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                ContractStatus.READY_FOR_REVIEW,
                1,
                new TaskContract.Source(
                        ".agent/workstreams/" + workstreamId + "/requirement.md",
                        "contract.compile"
                )
        );
    }

    private String extractObjective(String text) {
        var lines = text.lines().iterator();
        while (lines.hasNext()) {
            var line = lines.next().trim();
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            return line;
        }
        return "Undefined objective";
    }

    private List<String> extractIncludedScope(String text) {
        var matcher = FILE_REF_PATTERN.matcher(text);
        var included = new java.util.LinkedHashSet<String>();
        while (matcher.find()) {
            included.add(matcher.group(1));
        }
        return List.copyOf(included);
    }

    private List<String> extractConstraints(String text) {
        return text.lines()
                .filter(line -> CONSTRAINT_PATTERN.matcher(line).find())
                .map(String::trim)
                .toList();
    }
}
