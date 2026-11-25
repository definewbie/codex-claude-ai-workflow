package dev.dual.runtime.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Renders a compact boundary between harness facts, human decisions, agent
 * proposals, and pending evidence gaps.
 */
final class ContextStateEvidenceBoundaryRenderer {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private ContextStateEvidenceBoundaryRenderer() {
    }

    static void append(StringBuilder builder, TaskHandler.TaskContext context, Path workstreamDir, ObjectMapper objectMapper) throws IOException {
        append(builder, context, workstreamDir, objectMapper, Optional.empty());
    }

    static void append(
            StringBuilder builder,
            TaskHandler.TaskContext context,
            Path workstreamDir,
            ObjectMapper objectMapper,
            Optional<TaskContractProjectionVerifier.QualifiedTaskContract> qualifiedContract
    ) throws IOException {
        builder.append("## State & Evidence Boundary\n\n");
        builder.append("This package separates deterministic harness facts from agent proposals.\n\n");

        appendTrustedFacts(builder, workstreamDir, objectMapper, qualifiedContract);
        var routedEvidence = AgentEvidenceIndex.fromRefs(
                context.ledger(), context.workspaceRoot(), context.workstreamId(), context.instance().inputRefs(), objectMapper);
        appendCurrentRepairObjective(builder, workstreamDir, objectMapper, routedEvidence);
        appendHumanDecisions(builder, qualifiedContract);
        appendAgentProposedStates(builder, routedEvidence);
        appendPendingGaps(builder, workstreamDir, objectMapper);
    }

    private static void appendTrustedFacts(
            StringBuilder builder,
            Path workstreamDir,
            ObjectMapper objectMapper,
            Optional<TaskContractProjectionVerifier.QualifiedTaskContract> qualifiedContract
    ) throws IOException {
        builder.append("### Trusted Harness Facts\n\n");
        if (qualifiedContract.isPresent()) {
            var snapshot = qualifiedContract.orElseThrow();
            var contract = snapshot.contract();
            builder.append("- TaskContract: ").append(contract.status()).append(" - ")
                    .append(contract.objective()).append(" (").append(snapshot.contractRef()).append(")\n");
        } else {
            builder.append("- TaskContract qualification: not available\n");
        }

        var gitEvidencePath = workstreamDir.resolve("artifacts/git-evidence/git-evidence.json");
        if (Files.exists(gitEvidencePath)) {
            var git = readJsonUnchecked(gitEvidencePath, objectMapper);
            builder.append("- Git Evidence: available=").append(text(git.get("available")));
            var changedFileCount = text(git.get("changedFileCount"));
            if (!changedFileCount.isBlank()) {
                builder.append(", changedFileCount=").append(changedFileCount);
            }
            builder.append(" (").append(toDisplayRef(gitEvidencePath)).append(")\n");
        } else {
            builder.append("- Git Evidence: not generated yet\n");
        }
    }

    private static void appendCurrentRepairObjective(
            StringBuilder builder,
            Path workstreamDir,
            ObjectMapper objectMapper,
            AgentEvidenceIndex routedEvidence
    ) throws IOException {
        var latestRejected = routedEvidence.recent(20)
                .stream()
                .filter(ContextStateEvidenceBoundaryRenderer::isRejectedAcceptance)
                .findFirst();
        if (latestRejected.isEmpty()) {
            return;
        }

        var entry = latestRejected.get();
        var contract = entry.document();
        var classification = readJsonUnchecked(workstreamDir.resolve("artifacts/failure-classification.json"), objectMapper);

        builder.append("\n### Current Repair Objective\n\n");
        var category = firstNonBlank(
                text(classification.get("category")),
                contract.failure().categoryHint()
        );
        if (!category.isBlank()) {
            builder.append("- Category: ").append(category).append("\n");
        }
        builder.append("- Source: ").append(toDisplayRef(entry.path())).append("\n");
        if (!contract.summary().isBlank()) {
            builder.append("- Summary: ").append(contract.summary()).append("\n");
        }
        appendList(builder, "Must Fix", contract.mustFix(), 8);
        appendList(builder, "Contract Gaps", contract.failure().contractGaps(), 8);
        if (!contract.failure().reason().isBlank()) {
            builder.append("- Failure Reason: ").append(contract.failure().reason()).append("\n");
        }
        var suggestedAction = text(classification.get("suggested_action"));
        if (!suggestedAction.isBlank()) {
            builder.append("- Suggested Action: ").append(suggestedAction).append("\n");
        }
        builder.append("\n");
    }

    private static boolean isRejectedAcceptance(AgentEvidenceIndex.Entry entry) {
        var document = entry.document();
        return "acceptance.codex_verifier_review".equals(document.nodeId())
                && ("REJECT".equals(document.decision()) || "REJECTED".equals(document.canonicalStageOutcome()));
    }

    private static void appendHumanDecisions(
            StringBuilder builder,
            Optional<TaskContractProjectionVerifier.QualifiedTaskContract> qualifiedContract
    ) {
        builder.append("\n### Human Decisions\n\n");
        if (qualifiedContract.isPresent()) {
            var snapshot = qualifiedContract.orElseThrow();
            var resolution = snapshot.resolutionDocument();
            builder.append("- Requirement Resolution: ").append(text(resolution.get("decision")))
                    .append(" by ").append(text(resolution.get("approved_by")))
                    .append(" (").append(snapshot.resolutionRef()).append(")\n");
            for (var item : resolvedQuestionTexts(resolution.get("resolved_questions")).stream().limit(3).toList()) {
                builder.append("  - ").append(item).append("\n");
            }
        } else {
            builder.append("- Requirement Resolution: not available\n");
        }
    }

    private static void appendAgentProposedStates(StringBuilder builder, AgentEvidenceIndex routedEvidence) {
        builder.append("\n### Agent Proposed States\n\n");
        var entries = routedEvidence.recent(5);
        if (entries.isEmpty()) {
            builder.append("- No routed validated agent sidecars yet\n");
            return;
        }
        for (var entry : entries) {
            var contract = entry.document();
            builder.append("- ").append(blankAsNone(contract.nodeId()))
                    .append(": decision=").append(blankAsNone(contract.decision()));
            if (!contract.canonicalStageOutcome().isBlank()) {
                builder.append(", stageOutcome=").append(contract.canonicalStageOutcome());
            }
            var evidenceState = entry.evidenceState();
            if (!evidenceState.isEmpty()) {
                builder.append(", evidenceState=").append(evidenceState);
            }
            builder.append(" (").append(toDisplayRef(entry.path())).append(")\n");
        }
    }

    private static void appendPendingGaps(StringBuilder builder, Path workstreamDir, ObjectMapper objectMapper) throws IOException {
        builder.append("\n### Pending Contract/Evidence Gaps\n\n");
        var failures = recentContractFailures(workstreamDir, 3);
        if (failures.isEmpty()) {
            builder.append("- No contract failure diagnostics recorded\n\n");
            return;
        }
        for (var failurePath : failures) {
            var failure = readJsonUnchecked(failurePath, objectMapper);
            builder.append("- ").append(blankAsNone(text(failure.get("node_id"))))
                    .append(": ").append(blankAsNone(text(failure.get("category"))))
                    .append(" / ").append(blankAsNone(text(failure.get("reason"))))
                    .append(" (").append(toDisplayRef(failurePath)).append(")\n");
            var hint = text(failure.get("recovery_hint"));
            if (!hint.isBlank()) {
                builder.append("  - Recovery: ").append(hint).append("\n");
            }
        }
        builder.append("\n");
    }

    private static List<Path> recentContractFailures(Path workstreamDir, int maxItems) throws IOException {
        var failureDir = workstreamDir.resolve("artifacts/contract-failures");
        if (!Files.exists(failureDir)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.list(failureDir)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(ContextStateEvidenceBoundaryRenderer::lastModified).reversed())
                    .limit(maxItems)
                    .toList();
        }
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

    private static Map<String, Object> readJsonUnchecked(Path path, ObjectMapper objectMapper) {
        try {
            return objectMapper.readValue(path.toFile(), MAP_TYPE);
        } catch (IOException e) {
            return Map.of();
        }
    }

    private static Instant lastModified(Path path) {
        try {
            return Files.readAttributes(path, BasicFileAttributes.class).lastModifiedTime().toInstant();
        } catch (IOException ignored) {
            return Instant.EPOCH;
        }
    }

    private static String blankAsNone(String value) {
        return value == null || value.isBlank() ? "(none)" : value;
    }

    private static String toDisplayRef(Path path) {
        return path.toAbsolutePath().normalize().toString();
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static void appendList(StringBuilder builder, String label, List<String> values, int maxItems) {
        if (values.isEmpty()) {
            return;
        }
        builder.append("- ").append(label).append(":\n");
        for (var value : values.stream().limit(maxItems).toList()) {
            builder.append("  - ").append(value).append("\n");
        }
    }

    private static String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? (second == null ? "" : second) : first;
    }
}
