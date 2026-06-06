package dev.dual.runtime.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.dual.core.contract.TaskContract;
import dev.dual.core.workflow.NodeResult;
import dev.dual.core.workflow.NodeStatus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class ContextBuildPackageHandler implements TaskHandler {
    private static final int MAX_SNIPPET_CHARS = 4_000;
    private static final int MAX_RECENT_AGENT_OUTPUTS = 8;
    private static final TokenEstimator TOKEN_ESTIMATOR = TokenEstimator.defaultEstimator();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final List<String> FULL_SECTIONS = List.of(
            "requirement",
            "input_artifacts",
            "iteration_history",
            "human_feedback",
            "recent_agent_outputs",
            "git_evidence",
            "ledger_pointers",
            "task_contract"
    );

    @Override
    public String type() {
        return "context.build_package";
    }

    @Override
    public NodeResult execute(TaskContext context) throws Exception {
        var workstreamDir = context.workspaceRoot()
                .resolve(".agent")
                .resolve("workstreams")
                .resolve(context.workstreamId());
        var contextDir = workstreamDir.resolve("context");
        Files.createDirectories(contextDir);

        var packagePath = contextDir.resolve(context.instance().nodeInstanceId() + ".md");
        var profileName = profile(context);
        var hasSectionOverride = context.definition().config().get("include_sections") instanceof List<?>;

        if (ContextProfiles.hasProfile(profileName) && !hasSectionOverride) {
            return executeGoverned(context, workstreamDir, packagePath, profileName);
        }

        var markdown = renderPackage(context, workstreamDir);
        Files.writeString(packagePath, markdown, StandardCharsets.UTF_8);

        return buildLegacyResult(context, packagePath);
    }

    private static NodeResult buildLegacyResult(TaskContext context, Path packagePath) {
        return buildLegacyResult(context, packagePath, sections(context));
    }

    private static NodeResult buildLegacyResult(TaskContext context, Path packagePath, List<String> includedSections) {
        var packageRef = toWorkspaceRef(context.workspaceRoot(), packagePath);
        var result = new LinkedHashMap<String, Object>();
        result.put("context_package", packageRef);
        result.put("target_node", configText(context, "target_node", ""));
        result.put("profile", profile(context));
        result.put("sections", includedSections);
        result.put("input_ref_count", context.instance().inputRefs().size());
        result.put("generated_at", Instant.now().toString());
        return NodeResult.completed("completed", List.of(packageRef), result);
    }

    private static NodeResult executeGoverned(TaskContext context, Path workstreamDir, Path packagePath, String profileName) throws IOException {
        var budget = ContextProfiles.budgetFor(profileName);
        var handoff = loadHandoff(workstreamDir);

        // Phase 2: Resolve section availability
        var resolved = new ArrayList<ResolvedSection>();
        for (var req : budget.sections()) {
            var available = checkAvailability(req, context, workstreamDir);
            if (req.missingPolicy() == MissingPolicy.FAIL && !available) {
                return new NodeResult("failed", NodeStatus.FAILED, List.of(), Map.of("error", "required section '" + req.section() + "' data not found"), null);
            }
            resolved.add(new ResolvedSection(req, available));
        }

        // Phase 3: Plan budgets — priority order, softBudget allocation
        var sorted = new ArrayList<>(resolved);
        sorted.sort(java.util.Comparator.comparingInt((ResolvedSection rs) -> rs.req.priority().ordinal()));
        int allocated = 0;
        for (var rs : sorted) {
            if (!rs.available) continue;
            if (allocated + rs.req.softBudget() > budget.totalBudget()) {
                rs.degraded = true;
                if (rs.req.overflowPolicy() == OverflowPolicy.DROP_OPTIONAL) {
                    rs.dropped = true;
                } else if (rs.req.overflowPolicy() == OverflowPolicy.FAIL) {
                    return new NodeResult("failed", NodeStatus.FAILED, List.of(), Map.of("error", "budget exceeded at section '" + rs.req.section() + "'"), null);
                }
            } else {
                allocated += rs.req.softBudget();
            }
        }

        // Phase 4: Render sections with degradation
        var builder = new StringBuilder();
        appendGovernedHeader(builder, context, budget, sorted);

        for (var rs : sorted) {
            if (rs.dropped || !rs.available) {
                if (rs.req.missingPolicy() == MissingPolicy.PLACEHOLDER) {
                    builder.append("## ").append(sectionTitle(rs.req.section())).append("\n\n");
                    builder.append("(not available)\n\n");
                }
                continue;
            }
            var before = builder.length();
            appendGovernedSection(builder, rs, context, workstreamDir, handoff, budget);
            rs.renderedChars = builder.length() - before;
        }

        // Phase 5: Validate total budget (token-based)
        if (tokenCount(builder.toString()) > budget.totalBudget()) {
            for (int i = sorted.size() - 1; i >= 0; i--) {
                if (tokenCount(builder.toString()) <= budget.totalBudget()) break;
                var rs = sorted.get(i);
                if (!rs.available || rs.dropped) continue;
                if (rs.req.overflowPolicy() == OverflowPolicy.FAIL) continue;
                rs.dropped = true;
            }
            builder.setLength(0);
            appendGovernedHeader(builder, context, budget, sorted);
            for (var rs : sorted) {
                if (rs.dropped || !rs.available) {
                    if (rs.req.missingPolicy() == MissingPolicy.PLACEHOLDER) {
                        builder.append("## ").append(sectionTitle(rs.req.section())).append("\n\n");
                        builder.append("(not available)\n\n");
                    }
                    continue;
                }
                appendGovernedSection(builder, rs, context, workstreamDir, handoff, budget);
            }
            if (tokenCount(builder.toString()) > budget.totalBudget()) {
                return new NodeResult("failed", NodeStatus.FAILED, List.of(),
                        Map.of("error", "budget exceeded: " + tokenCount(builder.toString()) + " > " + budget.totalBudget() + " tokens"), null);
            }
        }

        // Rebuild header with actual sizes
        builder.replace(0, builder.indexOf("\n## Context Governance"), rebuildGovernedHeader(context, budget, sorted));

        Files.writeString(packagePath, builder.toString(), StandardCharsets.UTF_8);
        var declaredSections = budget.sections().stream()
                .map(SectionRequirement::section)
                .toList();
        return buildLegacyResult(context, packagePath, declaredSections);
    }

    private static boolean checkAvailability(SectionRequirement req, TaskContext context, Path workstreamDir) {
        return switch (req.section()) {
            case "requirement" -> Files.exists(workstreamDir.resolve("requirement.md"));
            case "input_artifacts" -> !context.instance().inputRefs().isEmpty();
            case "iteration_history" -> Files.exists(workstreamDir.resolve("iterations"));
            case "human_feedback" -> Files.exists(workstreamDir.resolve("nodes"));
            case "recent_agent_outputs" -> Files.exists(workstreamDir.resolve("runs"));
            case "git_evidence" -> Files.exists(workstreamDir.resolve("artifacts/git-evidence"));
            case "ledger_pointers" -> Files.exists(workstreamDir.resolve("events.jsonl"));
            case "task_contract" -> Files.exists(workstreamDir.resolve("artifacts/task-contract.json"));
            default -> false;
        };
    }

    private static void appendGovernedHeader(StringBuilder builder, TaskContext context,
                                              ContextBudget budget, List<ResolvedSection> sections) {
        builder.append("# Context Package\n\n");
        builder.append("- Workstream: ").append(context.workstreamId()).append("\n");
        builder.append("- Builder Node: ").append(context.definition().id()).append("\n");
        builder.append("- Target Node: ").append(configText(context, "target_node", "(not specified)")).append("\n");
        builder.append("- Generated At: ").append(Instant.now()).append("\n");
        builder.append("- Profile: ").append(profile(context)).append("\n");
        builder.append("- Total: -- / ").append(formatTokens(budget.totalBudget())).append(" tokens budget\n\n");

        builder.append("## Context Governance\n\n");
        builder.append("| Section | Level | Degraded | Original | Current |\n");
        builder.append("|---|---|---|---|---|\n");
        for (var rs : sections) {
            var level = rs.degraded ? rs.req.degradeTo().strategy().name().toLowerCase() : "full";
            var degraded = rs.degraded ? "yes" : "no";
            if (!rs.available) {
                if (rs.req.missingPolicy() == MissingPolicy.PLACEHOLDER) degraded = "-";
                else degraded = "skip";
            }
            var original = rs.available ? formatTokens(rs.renderedChars) : "-";
            var current = rs.available ? formatTokens(rs.renderedChars) : "-";
            builder.append("| ").append(rs.req.section()).append(" | ").append(level)
                   .append(" | ").append(degraded).append(" | ").append(original)
                   .append(" | ").append(current).append(" |\n");
        }
        builder.append("\nDegraded sections have full data at their original paths listed below.\n\n");
    }

    private static String rebuildGovernedHeader(TaskContext context, ContextBudget budget, List<ResolvedSection> sections) {
        var sb = new StringBuilder();
        int total = 0;
        for (var rs : sections) total += rs.renderedChars;

        sb.append("# Context Package\n\n");
        sb.append("- Workstream: ").append(context.workstreamId()).append("\n");
        sb.append("- Builder Node: ").append(context.definition().id()).append("\n");
        sb.append("- Target Node: ").append(configText(context, "target_node", "(not specified)")).append("\n");
        sb.append("- Generated At: ").append(Instant.now()).append("\n");
        sb.append("- Profile: ").append(profile(context)).append("\n");
        sb.append("- Total: ").append(formatTokens(total)).append(" / ").append(formatTokens(budget.totalBudget())).append(" tokens\n\n");

        sb.append("## Context Governance\n\n");
        sb.append("| Section | Level | Degraded | Original | Current |\n");
        sb.append("|---|---|---|---|---|\n");
        for (var rs : sections) {
            var level = rs.degraded ? rs.req.degradeTo().strategy().name().toLowerCase() : "full";
            var degraded = rs.degraded ? "yes" : "no";
            if (!rs.available) {
                degraded = rs.req.missingPolicy() == MissingPolicy.PLACEHOLDER ? "-" : "skip";
            }
            sb.append("| ").append(rs.req.section()).append(" | ").append(level)
              .append(" | ").append(degraded).append(" | ").append(formatTokens(rs.originalChars))
              .append(" | ").append(formatTokens(rs.renderedChars)).append(" |\n");
        }
        sb.append("\nDegraded sections have full data at their original paths listed below.\n\n");
        return sb.toString();
    }

    private static void appendGovernedSection(StringBuilder builder, ResolvedSection rs,
                                               TaskContext context, Path workstreamDir,
                                               StageHandoffDocument handoff,
                                               ContextBudget budget) throws IOException {
        builder.append("## ").append(sectionTitle(rs.req.section())).append("\n\n");
        var strategy = rs.degraded ? rs.req.degradeTo().strategy() : Degradation.FULL;
        var maxItems = rs.degraded ? rs.req.degradeTo().maxItems() : 0;

        switch (rs.req.section()) {
            case "requirement" -> {
                var content = readOrDefault(workstreamDir.resolve("requirement.md"), "(requirement.md not found)");
                // ADAPTIVE: compress if content exceeds softBudget tokens, regardless of degraded flag
                if (rs.req.degradeTo().strategy() == Degradation.ADAPTIVE
                        && tokenCount(content) > rs.req.softBudget()) {
                    builder.append(snippetToTokens(content, rs.req.softBudget(), workstreamDir.resolve("requirement.md")));
                } else {
                    builder.append(content);
                }
                builder.append("\n\n");
            }
            case "input_artifacts" -> {
                if (strategy == Degradation.SUMMARY) {
                    appendInputArtifactsSummary(builder, context);
                } else {
                    appendInputArtifacts(builder, context, maxSnippetChars(context, budget));
                }
            }
            case "iteration_history" -> {
                if (strategy == Degradation.DECISION_ONLY) {
                    appendIterationHistoryDecisions(builder, workstreamDir, maxItems > 0 ? maxItems : 5);
                } else {
                    appendIterationHistory(builder, workstreamDir);
                }
                appendCompletedWork(builder, handoff);
                appendImportantDecisions(builder, handoff);
            }
            case "human_feedback" -> {
                appendHumanFeedbackLimited(builder, workstreamDir, maxItems > 0 ? maxItems : 3);
                appendOpenRisks(builder, handoff);
                appendRecommendedNextStage(builder, handoff);
            }
            case "recent_agent_outputs" -> {
                appendRecentAgentOutputsLimited(builder, workstreamDir, maxItems > 0 ? maxItems : 2);
            }
            case "git_evidence" -> {
                if (strategy == Degradation.DIFF_STAT) {
                    appendGitEvidenceDiffStat(builder, workstreamDir, handoff);
                } else {
                    appendGitEvidence(builder, workstreamDir, maxSnippetChars(context, budget));
                }
            }
            case "ledger_pointers" -> {
                builder.append("- Events: ").append(toDisplayRef(workstreamDir.resolve("events.jsonl"))).append("\n");
                builder.append("- Workflow current: ").append(toDisplayRef(workstreamDir.resolve("workflow/current.json"))).append("\n");
                builder.append("- Node state directory: ").append(toDisplayRef(workstreamDir.resolve("nodes"))).append("\n");
                builder.append("- Agent runs directory: ").append(toDisplayRef(workstreamDir.resolve("runs"))).append("\n\n");
            }
            case "task_contract" -> {
                var contractPath = workstreamDir.resolve("artifacts/task-contract.json");
                if (Files.exists(contractPath)) {
                    appendTaskContract(builder, contractPath);
                } else {
                    builder.append("(task-contract.json not found)\n\n");
                }
            }
            default -> builder.append("(unknown section)\n\n");
        }
    }

    private static void appendTaskContract(StringBuilder builder, Path contractPath) throws IOException {
        var contract = OBJECT_MAPPER.readValue(contractPath.toFile(), TaskContract.class);
        builder.append("**Objective:** ").append(contract.objective()).append("\n\n");
        builder.append("**Status:** ").append(contract.status()).append("\n\n");
        if (contract.scope() != null) {
            builder.append("**Scope Included:**\n");
            for (var item : contract.scope().included()) {
                builder.append("- ").append(item).append("\n");
            }
            builder.append("\n**Scope Excluded:**\n");
            for (var item : contract.scope().excluded()) {
                builder.append("- ").append(item).append("\n");
            }
            builder.append("\n");
        }
        appendListSection(builder, "Acceptance Criteria", contract.acceptanceCriteria());
        appendListSection(builder, "Non-Goals", contract.nonGoals());
        appendListSection(builder, "Constraints", contract.constraints());
        appendListSection(builder, "Risk Areas", contract.riskAreas());
    }

    private static void appendListSection(StringBuilder builder, String title, List<String> items) {
        if (items.isEmpty()) {
            return;
        }
        builder.append("**").append(title).append(":**\n");
        for (var item : items) {
            builder.append("- ").append(item).append("\n");
        }
        builder.append("\n");
    }

    private static void appendInputArtifactsSummary(StringBuilder builder, TaskContext context) throws IOException {
        if (context.instance().inputRefs().isEmpty()) {
            builder.append("(no direct input refs)\n\n");
            return;
        }
        for (String inputRef : context.instance().inputRefs()) {
            var path = context.workspaceRoot().resolve(inputRef).normalize();
            builder.append("- ").append(inputRef);
            if (!Files.exists(path) || Files.isDirectory(path)) {
                builder.append(" (unavailable)\n");
                continue;
            }
            var content = Files.readString(path, StandardCharsets.UTF_8);
            var firstLine = content.lines().findFirst().map(s -> s.length() > 80 ? s.substring(0, 80) + "..." : s).orElse("(empty)");
            builder.append(" — ").append(firstLine).append("\n");
        }
        builder.append("\n");
    }

    private static void appendIterationHistoryDecisions(StringBuilder builder, Path workstreamDir, int maxItems) throws IOException {
        var iterationsDir = workstreamDir.resolve("iterations");
        if (!Files.exists(iterationsDir)) {
            builder.append("(no iteration history)\n\n");
            return;
        }
        try (Stream<Path> paths = Files.list(iterationsDir)) {
            var iterations = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .map(ContextBuildPackageHandler::readJsonUnchecked)
                    .filter(iteration -> !text(iteration.get("iterationId")).isBlank())
                    .sorted(Comparator.comparingInt(ContextBuildPackageHandler::iterationNumber).reversed())
                    .limit(maxItems)
                    .toList();
            if (iterations.isEmpty()) {
                builder.append("(no iteration history)\n\n");
                return;
            }
            for (var iteration : iterations) {
                builder.append("- ").append(text(iteration.get("iterationId")));
                builder.append(": trigger=").append(text(iteration.get("triggerAction")));
                builder.append(", target=").append(text(iteration.get("targetNodeDefinitionId")));
                builder.append("\n");
            }
            builder.append("\n");
        }
    }

    private static void appendHumanFeedbackLimited(StringBuilder builder, Path workstreamDir, int maxItems) throws IOException {
        var nodesDir = workstreamDir.resolve("nodes");
        if (!Files.exists(nodesDir)) {
            builder.append("(no human feedback)\n\n");
            return;
        }
        try (Stream<Path> paths = Files.list(nodesDir)) {
            var feedbackNodes = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .map(ContextBuildPackageHandler::readNodeUnchecked)
                    .filter(ContextBuildPackageHandler::hasHumanFeedback)
                    .sorted(Comparator.comparing(ContextBuildPackageHandler::finishedAtText).reversed())
                    .limit(maxItems)
                    .toList();
            if (feedbackNodes.isEmpty()) {
                builder.append("(no human feedback)\n\n");
                return;
            }
            for (Map<String, Object> node : feedbackNodes) {
                var result = resultMap(node);
                builder.append("### ").append(text(node.get("nodeDefinitionId"))).append("\n\n");
                builder.append("- Action: ").append(text(result.get("human_action"))).append("\n");
                builder.append("- Finished At: ").append(text(node.get("finishedAt"))).append("\n\n");
                builder.append("```text\n");
                builder.append(text(result.get("note")).stripTrailing());
                builder.append("\n```\n\n");
            }
        }
    }

    private static void appendRecentAgentOutputsLimited(StringBuilder builder, Path workstreamDir, int maxItems) throws IOException {
        var runsDir = workstreamDir.resolve("runs");
        if (!Files.exists(runsDir)) {
            builder.append("(no agent runs)\n\n");
            return;
        }
        try (Stream<Path> paths = Files.walk(runsDir)) {
            var outputs = paths
                    .filter(Files::isRegularFile)
                    .filter(ContextBuildPackageHandler::isAgentOutput)
                    .filter(path -> path.getFileName().toString().endsWith(".md"))
                    .sorted(Comparator.comparing(ContextBuildPackageHandler::lastModified).reversed())
                    .limit(maxItems)
                    .toList();
            if (outputs.isEmpty()) {
                builder.append("(no agent outputs)\n\n");
                return;
            }
            for (Path output : outputs) {
                builder.append("### ").append(toDisplayRef(output)).append("\n\n");
                appendAgentContractSummary(builder, output);
                builder.append("```text\n");
                builder.append(snippet(output, MAX_SNIPPET_CHARS));
                builder.append("\n```\n\n");
            }
        }
    }

    private static void appendGitEvidenceDiffStat(StringBuilder builder, Path workstreamDir,
                                                   StageHandoffDocument handoff) throws IOException {
        var evidenceDir = workstreamDir.resolve("artifacts/git-evidence");
        var changedFilesPath = evidenceDir.resolve("changed-files.txt");

        builder.append("### Changed Files\n\n");
        if (Files.exists(changedFilesPath)) {
            builder.append("```text\n");
            builder.append(snippet(changedFilesPath, 2_000));
            builder.append("\n```\n\n");
        } else if (handoff.hasChangedFiles()) {
            for (var file : handoff.changedFiles()) {
                builder.append("- ").append(file).append("\n");
            }
            builder.append("\n");
        } else if (!Files.exists(evidenceDir)) {
            builder.append("(git evidence not generated yet)\n\n");
            return;
        }

        var diffStat = evidenceDir.resolve("diff.stat");
        if (Files.exists(diffStat)) {
            builder.append("### Diff Stat\n\n");
            builder.append("```text\n");
            builder.append(snippet(diffStat, 1_000));
            builder.append("\n```\n\n");
        }
    }

    private static void appendCompletedWork(StringBuilder builder, StageHandoffDocument handoff) {
        if (handoff.hasCompletedWork()) {
            builder.append("### Completed Work\n\n");
            for (var item : handoff.completedWork()) {
                builder.append("- ").append(item).append("\n");
            }
            builder.append("\n");
        }
    }

    private static void appendImportantDecisions(StringBuilder builder, StageHandoffDocument handoff) {
        if (handoff.hasImportantDecisions()) {
            builder.append("### Important Decisions\n\n");
            for (var item : handoff.importantDecisions()) {
                builder.append("- ").append(item).append("\n");
            }
            builder.append("\n");
        }
    }

    private static void appendOpenRisks(StringBuilder builder, StageHandoffDocument handoff) {
        if (handoff.hasOpenRisks()) {
            builder.append("### Open Risks\n\n");
            for (var item : handoff.openRisks()) {
                builder.append("- ").append(item).append("\n");
            }
            builder.append("\n");
        }
    }

    private static void appendRecommendedNextStage(StringBuilder builder, StageHandoffDocument handoff) {
        if (handoff.hasRecommendedNextStage()) {
            builder.append("### Recommended Next Stage\n\n");
            builder.append(handoff.recommendedNextStage()).append("\n\n");
        }
    }

    private static String sectionTitle(String section) {
        return switch (section) {
            case "requirement" -> "User Requirement";
            case "input_artifacts" -> "Input Artifacts";
            case "iteration_history" -> "Iteration History";
            case "human_feedback" -> "Human Feedback";
            case "recent_agent_outputs" -> "Recent Agent Outputs";
            case "git_evidence" -> "Git Evidence";
            case "ledger_pointers" -> "Ledger Pointers";
            case "task_contract" -> "TaskContract";
            default -> section;
        };
    }

    private static String formatChars(int chars) {
        if (chars < 1_000) return chars + "";
        return String.format("%.1fk", chars / 1_000.0);
    }

    private static int tokenCount(String text) {
        return TOKEN_ESTIMATOR.estimate(text);
    }

    private static String formatTokens(int count) {
        if (count <= 0) return "0";
        if (count >= 1_000) return String.format("%.1fk", count / 1_000.0);
        return String.valueOf(count);
    }

    private static final class ResolvedSection {
        final SectionRequirement req;
        final boolean available;
        boolean degraded;
        boolean dropped;
        int originalChars;
        int renderedChars;

        ResolvedSection(SectionRequirement req, boolean available) {
            this.req = req;
            this.available = available;
        }
    }

    private static String renderPackage(TaskContext context, Path workstreamDir) throws IOException {
        var targetNode = configText(context, "target_node", "(not specified)");
        var purpose = configText(context, "purpose", "Prepare focused context for the next agent step.");
        var profile = profile(context);
        var sections = sections(context);
        var maxSnippetChars = maxSnippetChars(context);
        var maxRecentAgentOutputs = maxRecentAgentOutputs(context);
        var requirement = readOrDefault(workstreamDir.resolve("requirement.md"), "(requirement.md not found)");
        var builder = new StringBuilder();
        builder.append("# Context Package\n\n");
        builder.append("- Workstream: ").append(context.workstreamId()).append("\n");
        builder.append("- Builder Node: ").append(context.definition().id()).append("\n");
        builder.append("- Target Node: ").append(targetNode).append("\n");
        builder.append("- Generated At: ").append(Instant.now()).append("\n");
        builder.append("- Purpose: ").append(purpose).append("\n\n");

        builder.append("## Context Contract\n\n");
        builder.append("- Profile: ").append(profile).append("\n");
        builder.append("- Included Sections: ").append(String.join(", ", sections)).append("\n");
        builder.append("- Max Snippet Chars: ").append(maxSnippetChars).append("\n");
        builder.append("- Max Recent Agent Outputs: ").append(maxRecentAgentOutputs).append("\n\n");

        if (sections.contains("requirement")) {
            builder.append("## User Requirement\n\n");
            builder.append(requirement).append("\n\n");
        }

        if (sections.contains("input_artifacts")) {
            builder.append("## Input Artifacts\n\n");
            appendInputArtifacts(builder, context, maxSnippetChars);
        }

        if (sections.contains("iteration_history")) {
            builder.append("## Iteration History\n\n");
            appendIterationHistory(builder, workstreamDir);
        }

        if (sections.contains("human_feedback")) {
            builder.append("## Human Feedback\n\n");
            appendHumanFeedback(builder, workstreamDir);
        }

        if (sections.contains("recent_agent_outputs")) {
            builder.append("## Recent Agent Outputs\n\n");
            appendRecentAgentOutputs(builder, workstreamDir, maxSnippetChars, maxRecentAgentOutputs);
        }

        if (sections.contains("git_evidence")) {
            builder.append("## Git Evidence\n\n");
            appendGitEvidence(builder, workstreamDir, maxSnippetChars);
        }

        if (sections.contains("ledger_pointers")) {
            builder.append("## Ledger Pointers\n\n");
            builder.append("- Events: ").append(toDisplayRef(workstreamDir.resolve("events.jsonl"))).append("\n");
            builder.append("- Workflow current: ").append(toDisplayRef(workstreamDir.resolve("workflow/current.json"))).append("\n");
            builder.append("- Node state directory: ").append(toDisplayRef(workstreamDir.resolve("nodes"))).append("\n");
            builder.append("- Agent runs directory: ").append(toDisplayRef(workstreamDir.resolve("runs"))).append("\n");
        }
        return builder.toString();
    }

    private static void appendInputArtifacts(StringBuilder builder, TaskContext context, int maxSnippetChars) throws IOException {
        if (context.instance().inputRefs().isEmpty()) {
            builder.append("(no direct input refs)\n\n");
            return;
        }
        for (String inputRef : context.instance().inputRefs()) {
            var path = context.workspaceRoot().resolve(inputRef).normalize();
            builder.append("### ").append(inputRef).append("\n\n");
            if (!Files.exists(path) || Files.isDirectory(path)) {
                builder.append("(artifact unavailable or not a file)\n\n");
                continue;
            }
            builder.append("```text\n");
            builder.append(snippet(path, maxSnippetChars));
            builder.append("\n```\n\n");
        }
    }

    private static void appendGitEvidence(StringBuilder builder, Path workstreamDir, int maxSnippetChars) throws IOException {
        var evidenceDir = workstreamDir.resolve("artifacts/git-evidence");
        if (!Files.exists(evidenceDir)) {
            builder.append("(git evidence not generated yet)\n\n");
            return;
        }
        for (String fileName : List.of("git-evidence.json", "git-status-short.txt", "diff.stat", "changed-files.txt")) {
            var path = evidenceDir.resolve(fileName);
            builder.append("- ").append(fileName).append(": ").append(toDisplayRef(path)).append("\n");
        }
        var diffPatch = evidenceDir.resolve("diff.patch");
        if (Files.exists(diffPatch)) {
            builder.append("- diff.patch: ").append(toDisplayRef(diffPatch)).append("\n");
        }
        builder.append("\n");
        var evidenceJson = evidenceDir.resolve("git-evidence.json");
        if (Files.exists(evidenceJson)) {
            builder.append("```json\n");
            builder.append(snippet(evidenceJson, maxSnippetChars));
            builder.append("\n```\n\n");
        }
    }

    private static void appendHumanFeedback(StringBuilder builder, Path workstreamDir) throws IOException {
        var nodesDir = workstreamDir.resolve("nodes");
        if (!Files.exists(nodesDir)) {
            builder.append("(no workflow node state yet)\n\n");
            return;
        }
        try (Stream<Path> paths = Files.list(nodesDir)) {
            var feedbackNodes = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .map(ContextBuildPackageHandler::readNodeUnchecked)
                    .filter(ContextBuildPackageHandler::hasHumanFeedback)
                    .sorted(Comparator.comparing(ContextBuildPackageHandler::finishedAtText).reversed())
                    .limit(5)
                    .toList();
            if (feedbackNodes.isEmpty()) {
                builder.append("(no human gate feedback yet)\n\n");
                return;
            }
            for (Map<String, Object> node : feedbackNodes) {
                var result = resultMap(node);
                builder.append("### ").append(text(node.get("nodeDefinitionId"))).append("\n\n");
                builder.append("- Action: ").append(text(result.get("human_action"))).append("\n");
                builder.append("- Finished At: ").append(text(node.get("finishedAt"))).append("\n\n");
                builder.append("```text\n");
                builder.append(text(result.get("note")).stripTrailing());
                builder.append("\n```\n\n");
            }
        }
    }

    private static void appendIterationHistory(StringBuilder builder, Path workstreamDir) throws IOException {
        var iterationsDir = workstreamDir.resolve("iterations");
        if (!Files.exists(iterationsDir)) {
            builder.append("(no workflow iteration history yet)\n\n");
            return;
        }
        try (Stream<Path> paths = Files.list(iterationsDir)) {
            var iterations = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .map(ContextBuildPackageHandler::readJsonUnchecked)
                    .filter(iteration -> !text(iteration.get("iterationId")).isBlank())
                    .sorted(Comparator.comparingInt(ContextBuildPackageHandler::iterationNumber).reversed())
                    .limit(5)
                    .toList();
            if (iterations.isEmpty()) {
                builder.append("(no workflow iteration history yet)\n\n");
                return;
            }
            for (Map<String, Object> iteration : iterations) {
                builder.append("### ").append(text(iteration.get("iterationId"))).append("\n\n");
                builder.append("- Parent: ").append(blankAsNone(text(iteration.get("parentIterationId")))).append("\n");
                builder.append("- Trigger: ").append(text(iteration.get("triggerAction"))).append("\n");
                builder.append("- Target Node: ").append(text(iteration.get("targetNodeDefinitionId"))).append("\n");
                builder.append("- Created At: ").append(text(iteration.get("createdAt"))).append("\n");
                var reason = text(iteration.get("reason"));
                if (!reason.isBlank()) {
                    builder.append("\n```text\n");
                    builder.append(reason.stripTrailing());
                    builder.append("\n```\n");
                }
                builder.append("\n");
            }
        }
    }

    private static Map<String, Object> readNodeUnchecked(Path path) {
        return readJsonUnchecked(path);
    }

    private static Map<String, Object> readJsonUnchecked(Path path) {
        try {
            return OBJECT_MAPPER.readValue(path.toFile(), MAP_TYPE);
        } catch (IOException e) {
            return Map.of();
        }
    }

    private static boolean hasHumanFeedback(Map<String, Object> node) {
        var result = resultMap(node);
        return result.containsKey("human_action") && !text(result.get("note")).isBlank();
    }

    private static String finishedAtText(Map<String, Object> node) {
        return text(node.get("finishedAt"));
    }

    private static Map<String, Object> resultMap(Map<String, Object> node) {
        var result = node.get("result");
        if (result instanceof Map<?, ?> map) {
            var copy = new LinkedHashMap<String, Object>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() instanceof String key) {
                    copy.put(key, entry.getValue());
                }
            }
            return copy;
        }
        return Map.of();
    }

    private static void appendRecentAgentOutputs(StringBuilder builder, Path workstreamDir, int maxSnippetChars, int maxRecentAgentOutputs) throws IOException {
        var runsDir = workstreamDir.resolve("runs");
        if (!Files.exists(runsDir)) {
            builder.append("(no agent runs yet)\n\n");
            return;
        }
        try (Stream<Path> paths = Files.walk(runsDir)) {
            var outputs = paths
                    .filter(Files::isRegularFile)
                    .filter(ContextBuildPackageHandler::isAgentOutput)
                    .filter(path -> path.getFileName().toString().endsWith(".md"))
                    .sorted(Comparator.comparing(ContextBuildPackageHandler::lastModified).reversed())
                    .limit(maxRecentAgentOutputs)
                    .toList();
            if (outputs.isEmpty()) {
                builder.append("(no markdown agent outputs yet)\n\n");
                return;
            }
            for (Path output : outputs) {
                builder.append("### ").append(toDisplayRef(output)).append("\n\n");
                appendAgentContractSummary(builder, output);
                builder.append("```text\n");
                builder.append(snippet(output, maxSnippetChars));
                builder.append("\n```\n\n");
            }
        }
    }

    private static void appendAgentContractSummary(StringBuilder builder, Path output) {
        var sidecar = AgentOutputContractSidecar.pathFor(output);
        if (!Files.isRegularFile(sidecar)) {
            return;
        }
        var contract = AgentOutputContractDocument.read(sidecar, OBJECT_MAPPER);
        if (contract.isEmpty()) {
            return;
        }
        var nodeId = contract.get().nodeId();
        var decision = contract.get().decision();
        var summary = contract.get().summary();
        if (nodeId.isBlank() && decision.isBlank() && summary.isBlank()) {
            return;
        }
        if (!nodeId.isBlank()) {
            builder.append("- Contract: ").append(nodeId).append("\n");
        }
        if (!decision.isBlank()) {
            builder.append("- Decision: ").append(decision).append("\n");
        }
        if (!summary.isBlank()) {
            builder.append("- Summary: ").append(summary).append("\n");
        }
        appendListSummary(builder, "Must Fix", contract.get().mustFix());
        appendListSummary(builder, "Open Risks", contract.get().openRisks());
        appendListSummary(builder, "Changed Files", contract.get().changedFiles());
        var verification = contract.get().verificationSummary();
        if (!verification.isBlank()) {
            builder.append("- Verification: ").append(verification.replace("\n", " / ")).append("\n");
        }
        builder.append("- Contract Sidecar: ").append(toDisplayRef(sidecar)).append("\n\n");
    }

    private static void appendListSummary(StringBuilder builder, String label, Object value) {
        if (!(value instanceof List<?> items) || items.isEmpty()) {
            return;
        }
        var texts = items.stream()
                .map(ContextBuildPackageHandler::text)
                .filter(item -> !item.isBlank())
                .toList();
        if (!texts.isEmpty()) {
            builder.append("- ").append(label).append(": ").append(String.join("; ", texts)).append("\n");
        }
    }

    private static boolean isAgentOutput(Path path) {
        var parent = path.getParent();
        return parent != null && "outputs".equals(parent.getFileName().toString());
    }

    private static Instant lastModified(Path path) {
        try {
            return Files.readAttributes(path, BasicFileAttributes.class).lastModifiedTime().toInstant();
        } catch (IOException ignored) {
            return Instant.EPOCH;
        }
    }

    private static String snippet(Path path, int maxSnippetChars) throws IOException {
        var content = Files.readString(path, StandardCharsets.UTF_8);
        if (content.length() <= maxSnippetChars) {
            return content.stripTrailing();
        }
        return content.substring(0, maxSnippetChars).stripTrailing()
                + "\n\n[truncated; read full artifact at " + toDisplayRef(path) + "]";
    }

    private static String snippetToTokens(String content, int maxTokens, Path path) {
        var tokens = tokenCount(content);
        if (tokens <= maxTokens) return content.stripTrailing();
        // Estimate char position from token budget (4 chars/token ratio)
        var estimatedChars = Math.min(content.length(), maxTokens * 4);
        var truncated = content.substring(0, estimatedChars).stripTrailing();
        return truncated + "\n\n[adaptively compressed to ~" + maxTokens + " tokens; full data at " + toDisplayRef(path) + "]";
    }

    private static String readOrDefault(Path path, String fallback) throws IOException {
        if (!Files.exists(path)) {
            return fallback;
        }
        return Files.readString(path, StandardCharsets.UTF_8).stripTrailing();
    }

    private static String configText(TaskContext context, String key, String fallback) {
        var value = context.definition().config().get(key);
        if (value instanceof String text && !text.isBlank()) {
            return text;
        }
        return fallback;
    }

    private static String profile(TaskContext context) {
        return configText(context, "profile", "full");
    }

    private static List<String> sections(TaskContext context) {
        var override = context.definition().config().get("include_sections");
        if (override instanceof List<?> values && !values.isEmpty()) {
            return values.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .filter(section -> FULL_SECTIONS.contains(section))
                    .distinct()
                    .toList();
        }
        return switch (profile(context)) {
            case "product_review" -> List.of(
                    "requirement",
                    "input_artifacts",
                    "recent_agent_outputs",
                    "ledger_pointers"
            );
            case "architecture_review" -> List.of(
                    "requirement",
                    "input_artifacts",
                    "human_feedback",
                    "recent_agent_outputs",
                    "ledger_pointers"
            );
            case "plan_review" -> List.of(
                    "requirement",
                    "input_artifacts",
                    "iteration_history",
                    "human_feedback",
                    "recent_agent_outputs",
                    "ledger_pointers"
            );
            case "implementation" -> List.of(
                    "requirement",
                    "input_artifacts",
                    "iteration_history",
                    "human_feedback",
                    "recent_agent_outputs",
                    "ledger_pointers"
            );
            case "acceptance" -> List.of(
                    "requirement",
                    "input_artifacts",
                    "iteration_history",
                    "human_feedback",
                    "recent_agent_outputs",
                    "git_evidence",
                    "task_contract",
                    "ledger_pointers"
            );
            default -> FULL_SECTIONS;
        };
    }

    private static int maxSnippetChars(TaskContext context) {
        return positiveIntConfig(context, "max_snippet_chars", MAX_SNIPPET_CHARS, Map.of());
    }

    private static int maxSnippetChars(TaskContext context, ContextBudget budget) {
        return positiveIntConfig(context, "max_snippet_chars", MAX_SNIPPET_CHARS, budget.configOverrides());
    }

    private static int maxRecentAgentOutputs(TaskContext context) {
        return positiveIntConfig(context, "max_recent_agent_outputs", MAX_RECENT_AGENT_OUTPUTS, Map.of());
    }

    private static int maxRecentAgentOutputs(TaskContext context, ContextBudget budget) {
        return positiveIntConfig(context, "max_recent_agent_outputs", MAX_RECENT_AGENT_OUTPUTS, budget.configOverrides());
    }

    private static int positiveIntConfig(TaskContext context, String key, int fallback, Map<String, Object> overrides) {
        var override = overrides.get(key);
        if (override instanceof Number number && number.intValue() > 0) {
            return number.intValue();
        }
        if (override instanceof String text && text.matches("\\d+")) {
            return Integer.parseInt(text);
        }
        var value = context.definition().config().get(key);
        if (value instanceof Number number && number.intValue() > 0) {
            return number.intValue();
        }
        if (value instanceof String text && text.matches("\\d+")) {
            return Integer.parseInt(text);
        }
        return fallback;
    }

    private static String toWorkspaceRef(Path workspaceRoot, Path path) {
        return workspaceRoot.toAbsolutePath().normalize().relativize(path.toAbsolutePath().normalize()).toString();
    }

    private static String toDisplayRef(Path path) {
        return path.toAbsolutePath().normalize().toString();
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static int iterationNumber(Map<String, Object> iteration) {
        var number = iteration.get("number");
        if (number instanceof Number numeric) {
            return numeric.intValue();
        }
        var iterationId = text(iteration.get("iterationId"));
        if (iterationId.matches("iter-\\d{3}")) {
            return Integer.parseInt(iterationId.substring("iter-".length()));
        }
        return 0;
    }

    private static String blankAsNone(String value) {
        return value == null || value.isBlank() ? "(none)" : value;
    }

    private static StageHandoffDocument loadHandoff(Path workstreamDir) {
        var path = workstreamDir.resolve("artifacts/next-stage/handoff.json");
        if (!Files.exists(path)) return emptyHandoff();
        return StageHandoffDocument.read(path, OBJECT_MAPPER).orElseGet(ContextBuildPackageHandler::emptyHandoff);
    }

    private static StageHandoffDocument emptyHandoff() {
        return new StageHandoffDocument(1, "", "", "", "", List.of(), List.of(), List.of(), List.of(), "", "", "", "");
    }
}
