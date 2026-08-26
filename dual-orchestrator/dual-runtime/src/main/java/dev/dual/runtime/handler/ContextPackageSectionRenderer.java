package dev.dual.runtime.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.dual.core.contract.TaskContract;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

final class ContextPackageSectionRenderer {
    private static final int DEFAULT_SNIPPET_CHARS = 4_000;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private ContextPackageSectionRenderer() {
    }

    static void appendTaskContract(StringBuilder builder, Path contractPath) throws IOException {
        var contract = OBJECT_MAPPER.readValue(contractPath.toFile(), TaskContract.class);
        builder.append("**Objective:** ").append(contract.objective()).append("\n\n");
        builder.append("**Status:** ").append(contract.status()).append("\n\n");
        if (contract.scope() != null) {
            builder.append("**Scope Included:**\n");
            for (var item : contract.scope().included()) {
                builder.append("- ").append(item.operation()).append(": ").append(item.path()).append("\n");
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
        appendListSection(builder, "Evidence References", contract.evidenceRefs());
    }

    static void appendQualifiedTaskContract(
            StringBuilder builder,
            TaskContractProjectionVerifier.QualifiedTaskContract qualifiedContract
    ) {
        builder.append(qualifiedContract.wrappedPayload()).append("\n\n");
    }

    static void appendContractResolution(StringBuilder builder, Path resolutionPath) throws IOException {
        if (!Files.exists(resolutionPath)) {
            return;
        }
        var json = OBJECT_MAPPER.readValue(resolutionPath.toFile(), MAP_TYPE);
        builder.append("**Contract Resolution:**\n");
        builder.append("- Decision: ").append(text(json.get("decision"))).append("\n");
        builder.append("- Approved By: ").append(text(json.get("approved_by"))).append("\n");
        builder.append("- Gate Ref: ").append(text(json.get("gate_ref"))).append("\n");
        appendListSection(builder, "Resolution Assumptions", listOfText(json.get("assumptions")));
        appendListSection(builder, "Accepted Risks", listOfText(json.get("accepted_risks")));
        appendListSection(builder, "Resolved Questions", resolvedQuestionTexts(json.get("resolved_questions")));
    }

    static void appendInputArtifactsSummary(StringBuilder builder, TaskHandler.TaskContext context) throws IOException {
        if (context.instance().inputRefs().isEmpty()) {
            builder.append("(no direct input refs)\n\n");
            return;
        }
        var evidence = AgentEvidenceIndex.fromRefs(
                context.workspaceRoot(), context.workstreamId(), context.instance().inputRefs(), OBJECT_MAPPER);
        var renderedSidecars = new HashSet<Path>();
        for (String inputRef : context.instance().inputRefs()) {
            var path = context.workspaceRoot().resolve(inputRef).normalize();
            if (evidence.isValidatedPlanProjectionRef(context.workspaceRoot(), inputRef)) {
                continue;
            }
            var entry = evidence.entryForRef(context.workspaceRoot(), inputRef);
            if (entry.isPresent()) {
                if (!renderedSidecars.add(entry.get().path())) {
                    continue;
                }
                var contract = entry.get().document();
                builder.append("- ").append(inputRef).append(" — ");
                if (!contract.decision().isBlank()) {
                    builder.append(contract.decision());
                    if (!contract.summary().isBlank()) {
                        builder.append(": ");
                    }
                }
                builder.append(contract.summary().isBlank()
                        ? "(validated agent result)"
                        : contract.summary().replace("\n", " / "));
                builder.append("\n");
                appendTypedPlanArtifact(builder, contract.planArtifact());
                continue;
            }
            builder.append("- ").append(inputRef);
            if (!Files.exists(path) || Files.isDirectory(path)) {
                builder.append(" (unavailable)\n");
                continue;
            }
            if (isTranscriptArtifact(path)) {
                builder.append(" (execution transcript; omitted from context)\n");
                continue;
            }
            if (isExecutionDiagnostic(path)) {
                builder.append(" (execution diagnostic; omitted from context)\n");
                continue;
            }
            if (isAgentResultCandidate(path)) {
                builder.append(" (unvalidated agent result omitted from context)\n");
                continue;
            }
            builder.append(" (routed artifact has no typed context projection; content omitted)\n");
        }
        builder.append("\n");
    }

    static void appendIterationHistoryDecisions(StringBuilder builder, Path workstreamDir, int maxItems) throws IOException {
        var iterationsDir = workstreamDir.resolve("iterations");
        if (!Files.exists(iterationsDir)) {
            builder.append("(no iteration history)\n\n");
            return;
        }
        try (Stream<Path> paths = Files.list(iterationsDir)) {
            var iterations = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .map(ContextPackageSectionRenderer::readJsonUnchecked)
                    .filter(iteration -> !text(iteration.get("iterationId")).isBlank())
                    .sorted(Comparator.comparingInt(ContextPackageSectionRenderer::iterationNumber).reversed())
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
                var reason = text(iteration.get("reason"));
                if (!reason.isBlank()) {
                    builder.append(", reason=").append(reason.replace("\n", " "));
                }
                builder.append("\n");
            }
            builder.append("\n");
        }
    }

    static void appendHumanFeedbackLimited(StringBuilder builder, Path workstreamDir, int maxItems) throws IOException {
        var nodesDir = workstreamDir.resolve("nodes");
        if (!Files.exists(nodesDir)) {
            builder.append("(no human feedback)\n\n");
            return;
        }
        try (Stream<Path> paths = Files.list(nodesDir)) {
            var feedbackNodes = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .map(ContextPackageSectionRenderer::readNodeUnchecked)
                    .filter(ContextPackageSectionRenderer::hasHumanFeedback)
                    .sorted(Comparator.comparing(ContextPackageSectionRenderer::finishedAtText).reversed())
                    .limit(maxItems)
                    .toList();
            if (feedbackNodes.isEmpty()) {
                builder.append("(no human feedback)\n\n");
                return;
            }
            for (Map<String, Object> node : feedbackNodes) {
                appendHumanFeedbackNode(builder, node);
            }
        }
    }

    static void appendRecentAgentOutputsLimited(
            StringBuilder builder,
            TaskHandler.TaskContext context,
            int maxItems
    ) {
        appendRoutedAgentOutputs(builder, context, maxItems);
    }

    static void appendGitEvidenceDiffStat(StringBuilder builder, Path workstreamDir,
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

    static void appendCompletedWork(StringBuilder builder, StageHandoffDocument handoff) {
        if (handoff.hasCompletedWork()) {
            builder.append("### Completed Work\n\n");
            for (var item : handoff.completedWork()) {
                builder.append("- ").append(item).append("\n");
            }
            builder.append("\n");
        }
    }

    static void appendImportantDecisions(StringBuilder builder, StageHandoffDocument handoff) {
        if (handoff.hasImportantDecisions()) {
            builder.append("### Important Decisions\n\n");
            for (var item : handoff.importantDecisions()) {
                builder.append("- ").append(item).append("\n");
            }
            builder.append("\n");
        }
    }

    static void appendOpenRisks(StringBuilder builder, StageHandoffDocument handoff) {
        if (handoff.hasOpenRisks()) {
            builder.append("### Open Risks\n\n");
            for (var item : handoff.openRisks()) {
                builder.append("- ").append(item).append("\n");
            }
            builder.append("\n");
        }
    }

    static void appendRecommendedNextStage(StringBuilder builder, StageHandoffDocument handoff) {
        if (handoff.hasRecommendedNextStage()) {
            builder.append("### Recommended Next Stage\n\n");
            builder.append(handoff.recommendedNextStage()).append("\n\n");
        }
    }

    static void appendWorkingMemory(StringBuilder builder, Path workstreamDir, Path workspaceRoot) {
        WorkstreamMemoryProjection.appendAgentHandoff(builder, workstreamDir, workspaceRoot);
    }

    static void appendReviewEvidenceBrief(StringBuilder builder, Path workstreamDir, Path workspaceRoot) {
        WorkstreamMemoryProjection.appendReviewEvidenceBrief(builder, workstreamDir, workspaceRoot);
    }

    static void appendRequiredReviewSubject(
            StringBuilder builder,
            TaskContractProposalReference.ResolvedProposal subject
    ) throws IOException {
        builder.append("## Required Review Subject\n\n");
        builder.append("- Proposal ID: ").append(subject.reference().proposalId()).append("\n");
        builder.append("- Proposal Reference: ").append(subject.referenceRef()).append("\n");
        builder.append("- Proposal SHA-256: ").append(subject.reference().sha256()).append("\n\n");
        builder.append(TypedContextPayloadBoundary.wrapJson(subject.proposal())).append("\n\n");
    }

    static void appendRequiredArchitectureReviewSubject(
            StringBuilder builder,
            AgentEvidenceIndex.Entry subject
    ) {
        builder.append("## Required Architecture Review Subject\n\n");
        appendAgentContractFields(builder, subject.document(), subject.path(), true);
    }

    static void appendInputArtifacts(StringBuilder builder, TaskHandler.TaskContext context, int maxSnippetChars) throws IOException {
        if (context.instance().inputRefs().isEmpty()) {
            builder.append("(no direct input refs)\n\n");
            return;
        }
        var evidence = AgentEvidenceIndex.fromRefs(
                context.workspaceRoot(), context.workstreamId(), context.instance().inputRefs(), OBJECT_MAPPER);
        var renderedSidecars = new HashSet<Path>();
        for (String inputRef : context.instance().inputRefs()) {
            var path = context.workspaceRoot().resolve(inputRef).normalize();
            if (evidence.isValidatedPlanProjectionRef(context.workspaceRoot(), inputRef)) {
                continue;
            }
            var entry = evidence.entryForRef(context.workspaceRoot(), inputRef);
            if (entry.isPresent()) {
                if (!renderedSidecars.add(entry.get().path())) {
                    continue;
                }
                builder.append("### ").append(inputRef).append("\n\n");
                appendAgentContractFields(builder, entry.get().document(), entry.get().path());
                continue;
            }
            builder.append("### ").append(inputRef).append("\n\n");
            if (!Files.exists(path) || Files.isDirectory(path)) {
                builder.append("(artifact unavailable or not a file)\n\n");
                continue;
            }
            if (isTranscriptArtifact(path)) {
                builder.append("(execution transcript omitted from context; inspect raw file at ")
                        .append(toDisplayRef(path))
                        .append(")\n\n");
                continue;
            }
            if (isExecutionDiagnostic(path)) {
                builder.append("(execution diagnostic omitted from context)\n\n");
                continue;
            }
            if (isAgentResultCandidate(path)) {
                builder.append("(unvalidated agent result omitted from context)\n\n");
                continue;
            }
            builder.append("(routed artifact has no typed context projection; content omitted)\n\n");
        }
    }

    static void appendGitEvidence(StringBuilder builder, Path workstreamDir, int maxSnippetChars) throws IOException {
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

    static void appendHumanFeedback(StringBuilder builder, Path workstreamDir) throws IOException {
        var nodesDir = workstreamDir.resolve("nodes");
        if (!Files.exists(nodesDir)) {
            builder.append("(no workflow node state yet)\n\n");
            return;
        }
        try (Stream<Path> paths = Files.list(nodesDir)) {
            var feedbackNodes = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .map(ContextPackageSectionRenderer::readNodeUnchecked)
                    .filter(ContextPackageSectionRenderer::hasHumanFeedback)
                    .sorted(Comparator.comparing(ContextPackageSectionRenderer::finishedAtText).reversed())
                    .limit(5)
                    .toList();
            if (feedbackNodes.isEmpty()) {
                builder.append("(no human gate feedback yet)\n\n");
                return;
            }
            for (Map<String, Object> node : feedbackNodes) {
                appendHumanFeedbackNode(builder, node);
            }
        }
    }

    static void appendIterationHistory(StringBuilder builder, Path workstreamDir) throws IOException {
        var iterationsDir = workstreamDir.resolve("iterations");
        if (!Files.exists(iterationsDir)) {
            builder.append("(no workflow iteration history yet)\n\n");
            return;
        }
        try (Stream<Path> paths = Files.list(iterationsDir)) {
            var iterations = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .map(ContextPackageSectionRenderer::readJsonUnchecked)
                    .filter(iteration -> !text(iteration.get("iterationId")).isBlank())
                    .sorted(Comparator.comparingInt(ContextPackageSectionRenderer::iterationNumber).reversed())
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

    static void appendRecentAgentOutputs(
            StringBuilder builder,
            TaskHandler.TaskContext context,
            int maxRecentAgentOutputs
    ) {
        appendRoutedAgentOutputs(builder, context, maxRecentAgentOutputs);
    }

    private static void appendHumanFeedbackNode(StringBuilder builder, Map<String, Object> node) {
        var result = resultMap(node);
        builder.append("### ").append(text(node.get("nodeDefinitionId"))).append("\n\n");
        builder.append("- Action: ").append(text(result.get("human_action"))).append("\n");
        builder.append("- Finished At: ").append(text(node.get("finishedAt"))).append("\n\n");
        builder.append("```text\n");
        builder.append(text(result.get("note")).stripTrailing());
        builder.append("\n```\n\n");
    }

    private static void appendRoutedAgentOutputs(
            StringBuilder builder,
            TaskHandler.TaskContext context,
            int maxItems
    ) {
        var entries = AgentEvidenceIndex.fromRefs(
                context.workspaceRoot(), context.workstreamId(), context.instance().inputRefs(), OBJECT_MAPPER)
                .recent(maxItems);
        if (entries.isEmpty()) {
            builder.append("(no routed validated agent outputs)\n\n");
            return;
        }
        for (var entry : entries) {
            appendAgentContractSummary(builder, entry);
        }
    }

    private static void appendAgentContractSummary(StringBuilder builder, AgentEvidenceIndex.Entry entry) {
        var contract = entry.document();
        builder.append("### ").append(toDisplayRef(entry.path())).append("\n\n");
        appendAgentContractFields(builder, contract, entry.path());
    }

    private static void appendAgentContractFields(
            StringBuilder builder,
            AgentOutputContractDocument contract,
            Path sidecar
    ) {
        appendAgentContractFields(builder, contract, sidecar, false);
    }

    private static void appendAgentContractFields(
            StringBuilder builder,
            AgentOutputContractDocument contract,
            Path sidecar,
            boolean includeArchitectureEvidence
    ) {
        var nodeId = contract.nodeId();
        var decision = contract.decision();
        var summary = contract.summary();
        if (nodeId.isBlank() && decision.isBlank() && summary.isBlank()) {
            builder.append("(validated agent result has no summary fields)\n\n");
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
        if (includeArchitectureEvidence) {
            appendListSection(builder, "Checks", contract.checks());
            appendListSection(builder, "Evidence", contract.evidence());
        }
        appendListSummary(builder, "Must Fix", contract.mustFix());
        appendReviewObligations(builder, contract.contractObligations());
        appendListSummary(builder, "Open Risks", contract.openRisks());
        appendListSummary(builder, "Changed Files", contract.changedFiles());
        var verification = contract.verificationSummary();
        if (!verification.isBlank()) {
            builder.append("- Verification: ").append(verification.replace("\n", " / ")).append("\n");
        }
        builder.append("- Contract Sidecar: ").append(toDisplayRef(sidecar)).append("\n");
        builder.append("- Raw Output: ").append(contract.sourceOutputRef()).append("\n\n");
        appendTypedPlanArtifact(builder, contract.planArtifact());
    }

    private static void appendReviewObligations(
            StringBuilder builder,
            List<AgentResultEnvelope.ReviewObligation> obligations
    ) {
        if (obligations == null || obligations.isEmpty()) {
            return;
        }
        builder.append("- Contract Obligations:\n");
        for (var obligation : obligations) {
            builder.append("  - [").append(obligation.id()).append("] ")
                    .append(obligation.text()).append("\n");
        }
    }

    private static void appendTypedPlanArtifact(StringBuilder builder, PlanArtifactDocument plan) {
        if (plan == null) {
            return;
        }
        builder.append("**Typed Plan Evidence:**\n\n");
        appendListSection(builder, "Implementation Steps", plan.implementationSteps());
        appendListSection(builder, "Edit Files", plan.editFiles());
        appendListSection(builder, "Reference Files", plan.referenceFiles());
        appendListSection(builder, "File-Level Changes", plan.fileLevelChanges());
        appendListSection(builder, "Validation Plan", plan.validationPlan());
        if (!plan.rollbackNotes().isBlank()) {
            builder.append("**Rollback Notes:** ").append(plan.rollbackNotes()).append("\n\n");
        }
        appendListSection(builder, "Known Risks", plan.knownRisks());
        appendListSection(builder, "Human Review Checklist", plan.humanReviewChecklist());
    }

    private static void appendListSummary(StringBuilder builder, String label, Object value) {
        if (!(value instanceof List<?> items) || items.isEmpty()) {
            return;
        }
        var texts = items.stream()
                .map(ContextPackageSectionRenderer::text)
                .filter(item -> !item.isBlank())
                .toList();
        if (!texts.isEmpty()) {
            builder.append("- ").append(label).append(": ").append(String.join("; ", texts)).append("\n");
        }
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

    private static List<String> resolvedQuestionTexts(Object value) {
        if (!(value instanceof List<?> items)) {
            return List.of();
        }
        var result = new ArrayList<String>();
        for (var item : items) {
            if (item instanceof Map<?, ?> map) {
                var question = text(map.get("question"));
                var resolution = text(map.get("resolution"));
                if (!question.isBlank()) {
                    result.add(resolution.isBlank() ? question : question + " -> " + resolution);
                }
            }
        }
        return List.copyOf(result);
    }

    private static List<String> listOfText(Object value) {
        if (!(value instanceof List<?> items)) {
            return List.of();
        }
        return items.stream()
                .map(ContextPackageSectionRenderer::text)
                .filter(item -> !item.isBlank())
                .toList();
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

    private static boolean isAgentResultCandidate(Path path) {
        var parent = path.getParent();
        return parent != null
                && "outputs".equals(parent.getFileName().toString())
                && path.getFileName().toString().endsWith(".json");
    }

    private static boolean isExecutionDiagnostic(Path path) {
        var fileName = path.getFileName().toString();
        return fileName.endsWith(".stderr.log")
                || fileName.endsWith(".stdout.log")
                || fileName.endsWith(".transcript.log")
                || fileName.endsWith(".meta.json");
    }

    private static boolean isTranscriptArtifact(Path path) {
        return path.getFileName().toString().endsWith(".transcript.log");
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

    private static String snippetWithoutToolLog(Path path, int maxSnippetChars) throws IOException {
        var content = Files.readString(path, StandardCharsets.UTF_8);
        var sanitized = content.lines()
                .filter(line -> !line.startsWith("[tool] "))
                .filter(line -> !line.startsWith("[client] "))
                .filter(line -> !line.startsWith("  input: "))
                .filter(line -> !line.startsWith("  output:"))
                .filter(line -> !line.startsWith("  kind: "))
                .limit(80)
                .collect(java.util.stream.Collectors.joining("\n"));
        if (sanitized.isBlank()) {
            sanitized = "(agent output contains only tool/client frames; read raw output at " + toDisplayRef(path) + ")";
        }
        if (sanitized.length() <= maxSnippetChars) {
            return sanitized.stripTrailing();
        }
        return sanitized.substring(0, maxSnippetChars).stripTrailing()
                + "\n\n[truncated sanitized fallback; read raw output at " + toDisplayRef(path) + "]";
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
}
