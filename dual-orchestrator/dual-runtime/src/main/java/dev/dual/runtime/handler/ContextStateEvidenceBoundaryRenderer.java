package dev.dual.runtime.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dual.core.contract.TaskContract;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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

    static void append(StringBuilder builder, Path workstreamDir, ObjectMapper objectMapper) throws IOException {
        builder.append("## State & Evidence Boundary\n\n");
        builder.append("This package separates deterministic harness facts from agent proposals.\n\n");

        appendTrustedFacts(builder, workstreamDir, objectMapper);
        appendHumanDecisions(builder, workstreamDir, objectMapper);
        appendAgentProposedStates(builder, workstreamDir, objectMapper);
        appendPendingGaps(builder, workstreamDir, objectMapper);
    }

    private static void appendTrustedFacts(StringBuilder builder, Path workstreamDir, ObjectMapper objectMapper) throws IOException {
        builder.append("### Trusted Harness Facts\n\n");
        var contractPath = workstreamDir.resolve("artifacts/task-contract.json");
        if (Files.exists(contractPath)) {
            var contract = objectMapper.readValue(contractPath.toFile(), TaskContract.class);
            builder.append("- TaskContract: ").append(contract.status()).append(" - ")
                    .append(contract.objective()).append(" (").append(toDisplayRef(contractPath)).append(")\n");
        } else {
            builder.append("- TaskContract: not generated yet\n");
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

    private static void appendHumanDecisions(StringBuilder builder, Path workstreamDir, ObjectMapper objectMapper) {
        builder.append("\n### Human Decisions\n\n");
        var resolutionPath = workstreamDir.resolve("artifacts/contract-resolution.json");
        if (Files.exists(resolutionPath)) {
            var resolution = readJsonUnchecked(resolutionPath, objectMapper);
            builder.append("- Contract Resolution: ").append(text(resolution.get("decision")))
                    .append(" by ").append(text(resolution.get("approved_by")))
                    .append(" (").append(toDisplayRef(resolutionPath)).append(")\n");
            for (var item : resolvedQuestionTexts(resolution.get("resolved_questions")).stream().limit(3).toList()) {
                builder.append("  - ").append(item).append("\n");
            }
        } else {
            builder.append("- Contract Resolution: not available\n");
        }
    }

    private static void appendAgentProposedStates(StringBuilder builder, Path workstreamDir, ObjectMapper objectMapper) throws IOException {
        builder.append("\n### Agent Proposed States\n\n");
        var sidecars = recentContractSidecars(workstreamDir, 5);
        if (sidecars.isEmpty()) {
            builder.append("- No validated agent sidecars yet\n");
            return;
        }
        for (var sidecarPath : sidecars) {
            var document = AgentOutputContractDocument.read(sidecarPath, objectMapper);
            if (document.isEmpty()) {
                continue;
            }
            var contract = document.get();
            builder.append("- ").append(blankAsNone(contract.nodeId()))
                    .append(": decision=").append(blankAsNone(contract.decision()));
            if (!contract.lifecycleStatus().isBlank()) {
                builder.append(", lifecycle=").append(contract.lifecycleStatus());
            }
            var evidenceState = readJsonUnchecked(sidecarPath, objectMapper).get("evidence_state");
            if (evidenceState != null) {
                builder.append(", evidenceState=").append(evidenceState);
            }
            builder.append(" (").append(toDisplayRef(sidecarPath)).append(")\n");
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

    private static List<Path> recentContractSidecars(Path workstreamDir, int maxItems) throws IOException {
        var runsDir = workstreamDir.resolve("runs");
        if (!Files.exists(runsDir)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(runsDir)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".contract.json"))
                    .sorted(Comparator.comparing(ContextStateEvidenceBoundaryRenderer::lastModified).reversed())
                    .limit(maxItems)
                    .toList();
        }
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
}
