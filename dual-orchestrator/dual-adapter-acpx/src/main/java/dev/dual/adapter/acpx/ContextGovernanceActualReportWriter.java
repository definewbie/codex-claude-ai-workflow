package dev.dual.adapter.acpx;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.dual.runtime.handler.RepositoryFactBinding;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.HexFormat;
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
            AcpxToolUsage toolUsage,
            RepositoryFactBinding.Verified repositoryFactBinding
    ) throws IOException {
        return write(workspaceRoot, workstreamId, nodeDefinitionId, runId, null, transcriptPath, toolUsage,
                repositoryFactBinding);
    }

    Optional<Path> write(
            Path workspaceRoot,
            String workstreamId,
            String nodeDefinitionId,
            String runId,
            Path outputPath,
            Path transcriptPath,
            AcpxToolUsage toolUsage,
            RepositoryFactBinding.Verified repositoryFactBinding
    ) throws IOException {
        if (workspaceRoot == null || workstreamId == null || workstreamId.isBlank() || toolUsage == null) {
            return Optional.empty();
        }
        var workstreamDir = workspaceRoot.resolve(WORKSTREAM_DIR).resolve(workstreamId);
        if (!Files.isDirectory(workstreamDir)) {
            return Optional.empty();
        }
        var memoryDir = workstreamDir.resolve(MEMORY_DIR);
        if (repositoryFactBinding == null) {
            throw new IOException("repository fact binding is required for an actual governance report");
        }
        var bindingVerification = RepositoryFactBinding.verify(
                workspaceRoot,
                workstreamId,
                repositoryFactBinding.ref(),
                repositoryFactBinding.sha256(),
                repositoryFactBinding.document().nodeInstanceId(),
                runId,
                OBJECT_MAPPER);
        if (!bindingVerification.valid()) {
            throw new IOException("repository fact binding is invalid: " + bindingVerification.errors());
        }
        var binding = bindingVerification.verified();
        var plan = boundDocument(workspaceRoot, binding, "fileRelevancePlan");
        var policy = boundDocument(workspaceRoot, binding, "stageScopePolicy");
        var capabilityCatalog = boundDocument(workspaceRoot, binding, "agentCapabilityCatalog");
        var repoMap = boundDocument(workspaceRoot, binding, "repoMap");
        var plannedRead = textList(plan.map(doc -> doc.get("readCandidates")).orElse(null));
        var plannedWrite = firstNonEmpty(textList(plan.map(doc -> doc.get("writeCandidates")).orElse(null)),
                textList(policy.map(doc -> doc.get("writeScope")).orElse(null)));
        var actualRead = filterRepoFiles(toolUsage.readFiles());
        var actualDirectRead = filterRepoFiles(toolUsage.directReadFiles());
        var actualReadKinds = readKinds(repoMap.orElse(null), actualRead);
        var actualDirectReadKinds = readKinds(repoMap.orElse(null), actualDirectRead);
        var actualWrite = filterRepoFiles(toolUsage.writeFiles());
        var actualCapabilityUsages = ToolUsageCapabilityClassifier.classifyUsages(toolUsage, map(capabilityCatalog.orElse(null)));
        var actualCapabilities = actualCapabilityUsages.stream()
                .map(item -> textValue(item.get("kind")))
                .filter(kind -> !kind.isBlank())
                .distinct()
                .toList();
        var evidenceFiles = evidenceFiles(workstreamDir);
        var repeatedReadFiles = repeatedReads(workstreamDir, runId, actualRead, evidenceFiles);
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
        report.put("repositoryFactBinding", binding.ref());
        report.put("repositoryFactBindingSha256", binding.sha256());
        report.put("inventoryHead", binding.document().inventoryHead());
        report.put("inventorySha256", binding.document().inventorySha256());
        report.put("inventoryPathCount", binding.document().inventoryPathCount());
        report.put("reportPhase", "actual");
        report.put("planningAvailable", plan.isPresent() || policy.isPresent());
        report.put("recommendedReadFiles", plannedRead);
        report.put("expectedWriteFiles", plannedWrite);
        report.put("actualReadFiles", actualRead);
        report.put("actualDirectReadFiles", actualDirectRead);
        report.put("actualReadEvidence", readEvidence(workspaceRoot, actualRead));
        report.put("actualReadFileKinds", actualReadKinds);
        report.put("actualDirectReadFileKinds", actualDirectReadKinds);
        report.put("actualCapabilityUsages", actualCapabilityUsages);
        report.put("actualWriteFiles", actualWrite);
        report.put("repeatedReadFiles", repeatedReadFiles);
        report.put("toolUsage", toolUsage);
        report.put("transcriptPath", relativeOrNull(workspaceRoot, transcriptPath));
        report.put("telemetryPending", false);
        report.put("generatedAt", Instant.now().toString());

        var outputDir = workstreamDir.resolve(GOVERNANCE_DIR);
        Files.createDirectories(outputDir);
        var fileName = safeFileName(nodeDefinitionId) + "-actual-" + safeFileName(runId) + ".json";
        var reportPath = outputDir.resolve(fileName);
        OBJECT_MAPPER.writeValue(reportPath.toFile(), report);
        writeMemoryMetrics(memoryDir, workstreamId, nodeDefinitionId, runId, plannedRead, actualRead, repeatedReadFiles);
        return Optional.of(reportPath);
    }

    private static void writeMemoryMetrics(
            Path memoryDir,
            String workstreamId,
            String nodeDefinitionId,
            String runId,
            List<String> plannedRead,
            List<String> actualRead,
            List<Map<String, Object>> repeatedReadFiles
    ) throws IOException {
        Files.createDirectories(memoryDir);
        var uniqueRead = new LinkedHashSet<>(actualRead);
        var planned = new LinkedHashSet<>(plannedRead);
        var repeatedReadCount = repeatedReadFiles.size();
        var allowedRepeatedReadCount = repeatedReadFiles.stream()
                .filter(item -> Boolean.TRUE.equals(item.get("evidenceKnown")))
                .count();
        var workingSetHitCount = actualRead.stream().filter(planned::contains).count();

        var document = new LinkedHashMap<String, Object>();
        document.put("schemaVersion", 1);
        document.put("artifactType", "memory_metrics");
        document.put("workstreamId", workstreamId);
        document.put("nodeDefinitionId", nodeDefinitionId);
        document.put("runId", runId);
        document.put("readCount", actualRead.size());
        document.put("uniqueReadCount", uniqueRead.size());
        document.put("repeatedReadCount", repeatedReadCount);
        document.put("allowedRepeatedReadCount", allowedRepeatedReadCount);
        document.put("unexplainedRepeatedReadCount", Math.max(0, repeatedReadCount - (int) allowedRepeatedReadCount));
        document.put("workingSetHitCount", workingSetHitCount);
        document.put("workingSetHitRate", actualRead.isEmpty() ? 0.0 : (double) workingSetHitCount / actualRead.size());
        document.put("generatedAt", Instant.now().toString());
        OBJECT_MAPPER.writeValue(memoryDir.resolve("memory-metrics.json").toFile(), document);
    }

    private static Optional<JsonNode> boundDocument(
            Path workspaceRoot,
            RepositoryFactBinding.Verified binding,
            String artifactName
    ) throws IOException {
        var artifact = binding.document().artifacts().get(artifactName);
        if (artifact == null) {
            return Optional.empty();
        }
        var path = workspaceRoot.toAbsolutePath().normalize().resolve(artifact.ref()).normalize();
        return Optional.of(OBJECT_MAPPER.readTree(path.toFile()));
    }

    private static Map<String, String> readKinds(JsonNode repoMap, List<String> actualReadFiles) {
        if (repoMap == null || actualReadFiles == null || actualReadFiles.isEmpty()) {
            return Map.of();
        }
        var byPath = new LinkedHashMap<String, String>();
        var files = repoMap.get("files");
        if (files != null && files.isArray()) {
            for (var item : files) {
                var path = normalize(text(item.get("path")));
                var kind = text(item.get("kind"));
                if (!path.isBlank() && !kind.isBlank()) {
                    byPath.put(path, kind);
                }
            }
        }
        var result = new LinkedHashMap<String, String>();
        for (var path : actualReadFiles) {
            var normalized = normalize(path);
            var kind = byPath.get(normalized);
            if (kind != null && !kind.isBlank()) {
                result.put(normalized, kind);
            }
        }
        return Map.copyOf(result);
    }

    private static List<Map<String, Object>> readEvidence(Path workspaceRoot, List<String> actualReadFiles)
            throws IOException {
        var root = workspaceRoot.toAbsolutePath().normalize();
        var evidence = new ArrayList<Map<String, Object>>();
        for (var ref : actualReadFiles) {
            var path = root.resolve(ref).normalize();
            if (!path.startsWith(root) || !Files.isRegularFile(path)) {
                continue;
            }
            evidence.add(Map.of(
                    "path", normalize(ref),
                    "sha256", sha256(path),
                    "sizeBytes", Files.size(path)
            ));
        }
        return List.copyOf(evidence);
    }

    private static String sha256(Path path) throws IOException {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            digest.update(Files.readAllBytes(path));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IOException("SHA-256 is unavailable", impossible);
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

    private static String textValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(JsonNode node) {
        if (node == null || node.isNull()) {
            return Map.of();
        }
        return OBJECT_MAPPER.convertValue(node, Map.class);
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
