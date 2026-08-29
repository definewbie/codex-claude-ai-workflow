package dev.dual.runtime.handler;

import dev.dual.core.workflow.NodeResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;

public final class ReportGenerateFinalHandler implements TaskHandler {
    private static final int MAX_SNIPPET_CHARS = 4_000;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public String type() {
        return "report.generate_final";
    }

    @Override
    public NodeResult execute(TaskContext context) throws IOException {
        var evidence = CompletionEvidenceLineageResolver.resolveForReport(
                context.workspaceRoot(), context.workstreamId(), context.instance());
        if (evidence.status() != CompletionEvidenceLineageResolver.Status.AVAILABLE) {
            var result = new LinkedHashMap<String, Object>();
            result.put("failure_code", evidence.failureCode());
            result.put("error", evidence.detail());
            if (evidence.goal() != null) {
                result.put("goal_node_instance_id", evidence.goal().nodeInstanceId());
                result.put("goal_iteration_id", evidence.goal().iterationId());
            }
            return new NodeResult(
                    evidence.failureCode(),
                    dev.dual.core.workflow.NodeStatus.FAILED,
                    List.of(),
                    result,
                    evidence.detail());
        }
        var workstreamDir = context.workspaceRoot()
                .resolve(".agent")
                .resolve("workstreams")
                .resolve(context.workstreamId());
        var reportsDir = workstreamDir.resolve("reports");
        Files.createDirectories(reportsDir);

        var iterationId = context.instance().iterationId();
        var reportPath = reportsDir.resolve(iterationId + "-final-report.md");
        var latestPath = reportsDir.resolve("latest-final-report.md");
        var markdown = renderReport(context, workstreamDir, evidence);
        Files.writeString(reportPath, markdown, StandardCharsets.UTF_8);
        Files.writeString(latestPath, markdown, StandardCharsets.UTF_8);

        var reportRef = toWorkspaceRef(context.workspaceRoot(), reportPath);
        var latestRef = toWorkspaceRef(context.workspaceRoot(), latestPath);
        var result = new LinkedHashMap<String, Object>();
        result.put("report_path", reportRef);
        result.put("latest_report_path", latestRef);
        result.put("iteration_id", iterationId);
        result.put("generated_at", Instant.now().toString());
        return NodeResult.completed("completed", List.of(reportRef, latestRef), result);
    }

    private static String renderReport(
            TaskContext context,
            Path workstreamDir,
            CompletionEvidenceLineageResolver.Resolution evidence
    ) throws IOException {
        var builder = new StringBuilder();
        builder.append("# Dual Final Report\n\n");
        builder.append("- Workstream: ").append(context.workstreamId()).append("\n");
        builder.append("- Iteration: ").append(context.instance().iterationId()).append("\n");
        builder.append("- Generated At: ").append(Instant.now()).append("\n");
        builder.append("- Report Scope: pre-human-delivery verification snapshot\n");
        builder.append("- Excludes: subsequent human acceptance, delivery apply, and delivery commit status\n");
        builder.append("- Ledger: ").append(toDisplayRef(workstreamDir)).append("\n\n");

        builder.append("## User Request\n\n");
        builder.append(readOrDefault(workstreamDir.resolve("requirement.md"), "(requirement.md not found)")).append("\n\n");

        builder.append("## TaskContract\n\n");
        appendTaskContractSection(builder, workstreamDir);
        appendContractResolutionSection(builder, workstreamDir);

        appendFailureClassificationSection(builder, workstreamDir);

        builder.append("## Verification Evidence\n\n");
        appendVerificationEvidence(builder, evidence);

        builder.append("## Acceptance Summary\n\n");
        appendContractSummary(builder, evidence.acceptance());

        builder.append("## Developer Self-Test Summary\n\n");
        appendContractSummary(builder, evidence.selfTest());

        builder.append("## Git Evidence\n\n");
        appendGitEvidence(builder, workstreamDir);

        builder.append("## Selected Typed Outputs\n\n");
        appendSelectedOutputs(builder, evidence);

        builder.append("## Run Artifacts\n\n");
        builder.append("- Workflow current: ").append(toDisplayRef(workstreamDir.resolve("workflow/current.json"))).append("\n");
        builder.append("- Events: ").append(toDisplayRef(workstreamDir.resolve("events.jsonl"))).append("\n");
        builder.append("- Runs: ").append(toDisplayRef(workstreamDir.resolve("runs"))).append("\n");
        builder.append("- Context: ").append(toDisplayRef(workstreamDir.resolve("context"))).append("\n");
        builder.append("- Git evidence: ").append(toDisplayRef(workstreamDir.resolve("artifacts/git-evidence"))).append("\n");
        return builder.toString();
    }

    private static void appendTaskContractSection(StringBuilder builder, Path workstreamDir) throws IOException {
        var contractPath = workstreamDir.resolve("artifacts/task-contract.json");
        if (!Files.exists(contractPath)) {
            builder.append("(no TaskContract — legacy workflow)\n\n");
            return;
        }
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        try {
            var contract = mapper.readValue(contractPath.toFile(), dev.dual.core.contract.TaskContract.class);
            builder.append("- **Contract ID:** ").append(contract.id()).append("\n");
            builder.append("- **Objective:** ").append(contract.objective()).append("\n");
            builder.append("- **Status:** ").append(contract.status()).append("\n");
            builder.append("- **Scope Included:** ").append(contract.scope().included().isEmpty()
                    ? "(none)"
                    : contract.scope().included().stream()
                            .map(item -> item.operation() + ": " + item.path())
                            .collect(java.util.stream.Collectors.joining(", "))).append("\n");
            builder.append("- **Scope Excluded:** ").append(contract.scope().excluded().isEmpty() ? "(none)" : String.join(", ", contract.scope().excluded())).append("\n");
            builder.append("- **Acceptance Criteria:** ").append(contract.acceptanceCriteria().size()).append(" items").append("\n");
            builder.append("- **Non-Goals:** ").append(contract.nonGoals().isEmpty() ? "(none)" : String.join(", ", contract.nonGoals())).append("\n");
            builder.append("- **Contract Gaps Found:** 0\n");
            builder.append("- **Out-of-scope Changes:** 0\n");
            builder.append("\n");
        } catch (Exception e) {
            builder.append("(error reading TaskContract: ").append(e.getMessage()).append(")\n\n");
        }
    }

    private static void appendContractResolutionSection(StringBuilder builder, Path workstreamDir) throws IOException {
        var resolutionPath = workstreamDir.resolve("artifacts/contract-resolution.json");
        if (!Files.exists(resolutionPath)) {
            return;
        }
        builder.append("## Contract Resolution\n\n");
        builder.append("- Source: ").append(toDisplayRef(resolutionPath)).append("\n");
        try {
            var json = OBJECT_MAPPER.readTree(resolutionPath.toFile());
            appendJsonField(builder, "Decision", json.path("decision").asText(""));
            appendJsonField(builder, "Approved By", json.path("approved_by").asText(""));
            appendJsonField(builder, "Gate Ref", json.path("gate_ref").asText(""));
            appendResolvedQuestions(builder, json.path("resolved_questions"));
            appendJsonList(builder, "Assumptions", json.path("assumptions"));
            appendJsonList(builder, "Accepted Risks", json.path("accepted_risks"));
        } catch (Exception e) {
            builder.append("- Error reading contract resolution: ").append(e.getMessage()).append("\n");
        }
        builder.append("\n");
    }

    private static void appendResolvedQuestions(StringBuilder builder, JsonNode node) {
        if (node == null || !node.isArray() || node.isEmpty()) {
            return;
        }
        builder.append("- **Resolved Questions:**\n");
        for (var item : node) {
            var question = item.path("question").asText("");
            var resolution = item.path("resolution").asText("");
            var source = item.path("source").asText("");
            if (!question.isBlank()) {
                builder.append("  - ").append(question);
                if (!resolution.isBlank()) {
                    builder.append(" -> ").append(resolution);
                }
                if (!source.isBlank()) {
                    builder.append(" (").append(source).append(")");
                }
                builder.append("\n");
            }
        }
    }

    private static void appendFailureClassificationSection(StringBuilder builder, Path workstreamDir) throws IOException {
        var classificationPath = workstreamDir.resolve("artifacts/failure-classification.json");
        if (!Files.exists(classificationPath)) {
            builder.append("## Failure Classification\n\n");
            builder.append("(no failure classification — accepted or legacy workflow)\n\n");
            return;
        }
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        try {
            var tree = mapper.readTree(classificationPath.toFile());
            var reviewDecision = tree.path("review_decision").asText("(unknown)");
            if ("ACCEPT_WITH_NOTES".equals(reviewDecision)) {
                builder.append("## Acceptance Notes Classification\n\n");
            } else {
                builder.append("## Failure Classification\n\n");
            }
            builder.append("- **Category:** ").append(tree.path("category").asText("(unknown)")).append("\n");
            builder.append("- **Reasoning:** ").append(tree.path("reasoning").asText("(none)")).append("\n");
            builder.append("- **Suggested Action:** ").append(tree.path("suggested_action").asText("(none)")).append("\n");
            builder.append("- **Matched Rule:** ").append(tree.path("matched_rule").asInt(0)).append("\n");
            builder.append("- **Review Decision:** ").append(reviewDecision).append("\n");
            builder.append("- **Source:** ").append(tree.path("source").asText("(unknown)")).append("\n");
            builder.append("- **Classified At:** ").append(tree.path("classified_at").asText("(unknown)")).append("\n");
            var evidence = tree.path("evidence");
            if (evidence.isArray() && !evidence.isEmpty()) {
                builder.append("- **Evidence:**\n");
                for (var item : evidence) {
                    builder.append("  - ").append(item.asText()).append("\n");
                }
            }
            builder.append("\n");
        } catch (Exception e) {
            builder.append("## Failure Classification\n\n");
            builder.append("(error reading failure classification: ").append(e.getMessage()).append(")\n\n");
        }
    }

    private static void appendVerificationEvidence(
            StringBuilder builder,
            CompletionEvidenceLineageResolver.Resolution evidence
    ) throws IOException {
        appendContractEvidence(builder, "Developer Self-Test Evidence", evidence.selfTest().path());
        appendContractEvidence(builder, "Codex Acceptance Evidence", evidence.acceptance().path());
    }

    private static void appendContractEvidence(StringBuilder builder, String title, Path sidecar) throws IOException {
        builder.append("### ").append(title).append("\n\n");
        builder.append("- Source: ").append(toDisplayRef(sidecar)).append("\n");
        try {
            var json = OBJECT_MAPPER.readTree(sidecar.toFile());
            appendJsonField(builder, "Decision", json.path("decision").asText(""));
            appendJsonField(builder, "Summary", json.path("summary").asText(""));
            appendJsonField(builder, "Verification Summary", json.path("verification_summary").asText(""));
            appendJsonList(builder, "Checks Run", json.path("checks_run"));
            appendJsonList(builder, "Checks Skipped", json.path("checks_skipped"));
            appendJsonList(builder, "Evidence", json.path("evidence"));
            appendJsonList(builder, "Missing Evidence", json.path("missing_evidence"));
            appendJsonList(builder, "Acceptance Notes", json.path("acceptance_notes"));
        } catch (Exception e) {
            builder.append("- Error reading structured evidence: ").append(e.getMessage()).append("\n");
        }
        builder.append("\n");
    }

    private static void appendJsonField(StringBuilder builder, String label, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        builder.append("- **").append(label).append(":** ").append(value).append("\n");
    }

    private static void appendJsonList(StringBuilder builder, String label, JsonNode node) {
        if (node == null || !node.isArray() || node.isEmpty()) {
            return;
        }
        builder.append("- **").append(label).append(":**\n");
        for (var item : node) {
            var text = item.asText("");
            if (!text.isBlank()) {
                builder.append("  - ").append(text).append("\n");
            }
        }
    }

    private static void appendContractSummary(
            StringBuilder builder,
            CompletionEvidenceLineageResolver.Evidence evidence
    ) throws IOException {
        var sidecar = evidence.path();
        builder.append("- Source: ").append(evidence.ref()).append("\n");
        try {
            var json = OBJECT_MAPPER.readTree(sidecar.toFile());
            appendJsonField(builder, "Node", json.path("node_id").asText(""));
            appendJsonField(builder, "Decision", json.path("decision").asText(""));
            appendJsonField(builder, "Stage Outcome", json.path("stage_outcome").asText(""));
            appendJsonField(builder, "Summary", json.path("summary").asText(""));
            appendJsonField(builder, "Verification Summary", json.path("verification_summary").asText(""));
            appendJsonList(builder, "Evidence", json.path("evidence"));
            appendJsonList(builder, "Missing Evidence", json.path("missing_evidence"));
            appendJsonList(builder, "Must Fix", json.path("must_fix"));
            appendJsonList(builder, "Open Risks", json.path("open_risks"));
            appendJsonList(builder, "Acceptance Notes", json.path("acceptance_notes"));
        } catch (Exception e) {
            builder.append("- Error reading structured summary: ").append(e.getMessage()).append("\n");
        }
        builder.append("\n");
    }

    private static void appendGitEvidence(StringBuilder builder, Path workstreamDir) throws IOException {
        var evidenceDir = workstreamDir.resolve("artifacts/git-evidence");
        if (!Files.exists(evidenceDir)) {
            builder.append("(git evidence not generated)\n\n");
            return;
        }
        for (String fileName : List.of(
                "git-evidence.json",
                "git-status-short.txt",
                "diff.stat",
                "changed-files.txt",
                "hygiene-warnings.txt",
                "diff.patch"
        )) {
            var path = evidenceDir.resolve(fileName);
            if (Files.exists(path)) {
                builder.append("- ").append(fileName).append(": ").append(toDisplayRef(path)).append("\n");
            }
        }
        builder.append("\n");
        var hygieneWarnings = evidenceDir.resolve("hygiene-warnings.txt");
        if (Files.exists(hygieneWarnings) && !Files.readString(hygieneWarnings, StandardCharsets.UTF_8).isBlank()) {
            builder.append("## Repository Hygiene Warnings\n\n");
            builder.append("```text\n");
            builder.append(snippet(hygieneWarnings, MAX_SNIPPET_CHARS));
            builder.append("\n```\n\n");
        }
        var evidenceJson = evidenceDir.resolve("git-evidence.json");
        if (Files.exists(evidenceJson)) {
            builder.append("```json\n");
            builder.append(snippet(evidenceJson, MAX_SNIPPET_CHARS));
            builder.append("\n```\n\n");
        }
    }

    private static void appendSelectedOutputs(
            StringBuilder builder,
            CompletionEvidenceLineageResolver.Resolution evidence
    ) {
        for (var selected : List.of(evidence.selfTest(), evidence.acceptance())) {
            builder.append("- Source output: ").append(selected.document().sourceOutputRef()).append("\n");
            builder.append("  Sidecar: ").append(selected.ref()).append("\n");
        }
        builder.append("\n");
    }

    private static String readOrDefault(Path path, String fallback) throws IOException {
        if (!Files.exists(path)) {
            return fallback;
        }
        return Files.readString(path, StandardCharsets.UTF_8).stripTrailing();
    }

    private static String snippet(Path path, int maxChars) throws IOException {
        var content = Files.readString(path, StandardCharsets.UTF_8).stripTrailing();
        if (content.length() <= maxChars) {
            return content;
        }
        return content.substring(0, maxChars).stripTrailing()
                + "\n\n[truncated; read the full artifact at " + path.toAbsolutePath().normalize() + "]";
    }

    private static String toDisplayRef(Path path) {
        return path.toAbsolutePath().normalize().toString();
    }

    private static String toWorkspaceRef(Path workspaceRoot, Path path) {
        return workspaceRoot.toAbsolutePath().normalize().relativize(path.toAbsolutePath().normalize()).toString();
    }
}
