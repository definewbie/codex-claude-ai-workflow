package dev.dual.adapter.acpx;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Writes execution-derived context governance evidence.
 *
 * The planned report is created before an agent run. This writer creates the
 * matching "actual" report after an acpx execution, using run telemetry as the
 * source of truth and keeping workflow routing untouched.
 */
final class ContextGovernanceActualReportWriter {
    private static final String WORKSTREAM_DIR = ".agent/workstreams";
    private static final String MEMORY_DIR = "memory";
    private static final String GOVERNANCE_DIR = "artifacts/context-governance";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(SerializationFeature.INDENT_OUTPUT);

    Optional<Path> write(
            Path workspaceRoot,
            String workstreamId,
            String nodeDefinitionId,
            String runId,
            Path transcriptPath,
            AcpxToolUsage toolUsage
    ) throws IOException {
        if (workspaceRoot == null || workstreamId == null || workstreamId.isBlank() || toolUsage == null) {
            return Optional.empty();
        }
        var workstreamDir = workspaceRoot.resolve(WORKSTREAM_DIR).resolve(workstreamId);
        if (!Files.isDirectory(workstreamDir)) {
            return Optional.empty();
        }
        var memoryDir = workstreamDir.resolve(MEMORY_DIR);
        var plan = latestDocument(memoryDir, "file-relevance-plan-", nodeDefinitionId);
        var policy = latestDocument(memoryDir, "stage-scope-policy-", nodeDefinitionId);
        var plannedRead = firstNonEmpty(textList(plan.map(doc -> doc.get("readCandidates")).orElse(null)),
                textList(policy.map(doc -> doc.get("readScope")).orElse(null)));
        var plannedWrite = firstNonEmpty(textList(plan.map(doc -> doc.get("writeCandidates")).orElse(null)),
                textList(policy.map(doc -> doc.get("writeScope")).orElse(null)));
        var actualRead = filterRepoFiles(toolUsage.readFiles());
        var actualWrite = filterRepoFiles(toolUsage.writeFiles());
        var evidenceFiles = evidenceFiles(workstreamDir);
        var report = new LinkedHashMap<String, Object>();
        report.put("schemaVersion", 1);
        report.put("artifactType", "context_governance_report");
        report.put("workstreamId", workstreamId);
        report.put("nodeDefinitionId", nodeDefinitionId);
        report.put("policyNodeDefinitionId", text(policy.map(doc -> doc.get("nodeDefinitionId")).orElse(null)));
        report.put("targetNodeDefinitionId", firstNonBlank(
                text(policy.map(doc -> doc.get("targetNodeDefinitionId")).orElse(null)),
                text(plan.map(doc -> doc.get("targetNodeDefinitionId")).orElse(null)),
                nodeDefinitionId
        ));
        report.put("nodeInstanceId", text(policy.map(doc -> doc.get("nodeInstanceId")).orElse(null)));
        report.put("iterationId", text(policy.map(doc -> doc.get("iterationId")).orElse(null)));
        report.put("stageId", text(policy.map(doc -> doc.get("stageId")).orElse(null)));
        report.put("runId", runId);
        report.put("reportPhase", "actual");
        report.put("planningAvailable", plan.isPresent() || policy.isPresent());
        report.put("plannedReadFiles", plannedRead);
        report.put("plannedWriteFiles", plannedWrite);
        report.put("actualReadFiles", actualRead);
        report.put("actualWriteFiles", actualWrite);
        report.put("outsideScopeReads", outsideScope(actualRead, plannedRead));
        report.put("outsideScopeWrites", outsideScope(actualWrite, plannedWrite));
        report.put("repeatedReadFiles", repeatedReads(workstreamDir, runId, actualRead, evidenceFiles));
        report.put("toolUsage", toolUsage);
        report.put("transcriptPath", relativeOrNull(workspaceRoot, transcriptPath));
        report.put("telemetryPending", false);
        report.put("generatedAt", Instant.now().toString());

        var outputDir = workstreamDir.resolve(GOVERNANCE_DIR);
        Files.createDirectories(outputDir);
        var fileName = safeFileName(nodeDefinitionId) + "-actual-" + safeFileName(runId) + ".json";
        var outputPath = outputDir.resolve(fileName);
        OBJECT_MAPPER.writeValue(outputPath.toFile(), report);
        return Optional.of(outputPath);
    }

    private static Optional<JsonNode> latestDocument(Path dir, String prefix, String nodeDefinitionId) throws IOException {
        if (!Files.isDirectory(dir)) {
            return Optional.empty();
        }
        var candidates = new ArrayList<Path>();
        try (var files = Files.list(dir)) {
            files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith(prefix))
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .forEach(candidates::add);
        }
        candidates.sort(Comparator.comparing(ContextGovernanceActualReportWriter::lastModified).reversed());
        for (var path : candidates) {
            var json = OBJECT_MAPPER.readTree(path.toFile());
            if (matchesNodeDefinition(json, nodeDefinitionId)) {
                return Optional.of(json);
            }
        }
        return Optional.empty();
    }

    private static boolean matchesNodeDefinition(JsonNode json, String nodeDefinitionId) {
        if (nodeDefinitionId == null || nodeDefinitionId.isBlank()) {
            return false;
        }
        var targetNodeDefinitionId = text(json.get("targetNodeDefinitionId"));
        if (!targetNodeDefinitionId.isBlank()) {
            return nodeDefinitionId.equals(targetNodeDefinitionId);
        }
        return nodeDefinitionId.equals(text(json.get("nodeDefinitionId")));
    }

    private static long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }

    private static List<String> textList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        var values = new LinkedHashSet<String>();
        for (var item : node) {
            var text = text(item);
            if (!text.isBlank()) {
                values.add(text);
            }
        }
        return List.copyOf(values);
    }

    private static List<String> firstNonEmpty(List<String> first, List<String> second) {
        return first.isEmpty() ? second : first;
    }

    private static String firstNonBlank(String first, String second, String fallback) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return fallback == null ? "" : fallback;
    }

    private static List<String> filterRepoFiles(List<String> files) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }
        var values = new LinkedHashSet<String>();
        for (var file : files) {
            var normalized = normalize(file);
            if (isRepoEvidenceFile(normalized)) {
                values.add(normalized);
            }
        }
        return List.copyOf(values);
    }

    private static List<String> outsideScope(List<String> actual, List<String> planned) {
        if (actual.isEmpty()) {
            return List.of();
        }
        var values = new ArrayList<String>();
        for (var path : actual) {
            if (!allowed(path, planned)) {
                values.add(path);
            }
        }
        return List.copyOf(values);
    }

    private List<Map<String, Object>> repeatedReads(
            Path workstreamDir,
            String currentRunId,
            List<String> actualRead,
            List<String> evidenceFiles
    ) throws IOException {
        if (actualRead.isEmpty()) {
            return List.of();
        }
        var previousCounts = previousReadCounts(workstreamDir, currentRunId);
        var evidence = new LinkedHashSet<>(evidenceFiles);
        var repeated = new ArrayList<Map<String, Object>>();
        for (var path : actualRead) {
            var count = previousCounts.getOrDefault(path, 0);
            if (count <= 0) {
                continue;
            }
            var item = new LinkedHashMap<String, Object>();
            item.put("path", path);
            item.put("previousCount", count);
            item.put("evidenceKnown", evidence.contains(path));
            repeated.add(item);
        }
        return List.copyOf(repeated);
    }

    private Map<String, Integer> previousReadCounts(Path workstreamDir, String currentRunId) throws IOException {
        var runsDir = workstreamDir.resolve("runs");
        if (!Files.isDirectory(runsDir)) {
            return Map.of();
        }
        var counts = new LinkedHashMap<String, Integer>();
        try (var stream = Files.list(runsDir)) {
            for (var runDir : stream.filter(Files::isDirectory).sorted(Comparator.comparing(path -> path.getFileName().toString())).toList()) {
                if (runDir.getFileName().toString().equals(currentRunId)) {
                    continue;
                }
                var runJson = runDir.resolve("run.json");
                if (!Files.isRegularFile(runJson)) {
                    continue;
                }
                var run = OBJECT_MAPPER.readTree(runJson.toFile());
                for (var file : textList(run.path("toolUsage").path("readFiles"))) {
                    var normalized = normalize(file);
                    if (isRepoEvidenceFile(normalized)) {
                        counts.merge(normalized, 1, Integer::sum);
                    }
                }
            }
        }
        return counts;
    }

    private static List<String> evidenceFiles(Path workstreamDir) throws IOException {
        var manifest = workstreamDir.resolve("memory").resolve("evidence-manifest.json");
        if (!Files.isRegularFile(manifest)) {
            return List.of();
        }
        var files = new LinkedHashSet<String>();
        var document = OBJECT_MAPPER.readTree(manifest.toFile());
        for (var item : document.path("items")) {
            for (var file : textList(item.path("files"))) {
                var normalized = normalize(file);
                if (isRepoEvidenceFile(normalized)) {
                    files.add(normalized);
                }
            }
        }
        return List.copyOf(files);
    }

    private static boolean isRepoEvidenceFile(String normalized) {
        return !normalized.isBlank()
                && !normalized.startsWith(".agent/")
                && !normalized.startsWith(".git/")
                && !normalized.startsWith("/Users/")
                && !normalized.startsWith("/private/");
    }

    private static boolean allowed(String path, List<String> planned) {
        if (planned == null || planned.isEmpty()) {
            return false;
        }
        for (var allowed : planned) {
            var normalized = normalize(allowed);
            if (path.equals(normalized) || path.startsWith(normalized + "/")) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String value) {
        var text = value == null ? "" : value.trim();
        while (text.startsWith("./")) {
            text = text.substring(2);
        }
        return text;
    }

    private static String text(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        return node.asText("");
    }

    private static String relativeOrNull(Path workspaceRoot, Path path) {
        if (workspaceRoot == null || path == null) {
            return null;
        }
        try {
            return workspaceRoot.toAbsolutePath().normalize().relativize(path.toAbsolutePath().normalize()).toString();
        } catch (RuntimeException ignored) {
            return path.toString();
        }
    }

    private static String safeFileName(String value) {
        var text = value == null || value.isBlank() ? "unknown" : value;
        return text.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
