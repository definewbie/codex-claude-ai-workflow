package dev.dual.runtime.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Rebuildable evidence index for one workstream.
 *
 * The manifest is not the evidence source of truth. It points to ledger and
 * artifact files so context packages can reuse known facts without expanding
 * raw transcripts or re-discovering the repository from scratch.
 */
final class EvidenceManifest {
    static final String MANIFEST_REF_NAME = "memory/evidence-manifest.json";

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final String workstreamId;
    private final String updatedAt;
    private final List<EvidenceItem> items;

    EvidenceManifest(String workstreamId, String updatedAt, List<EvidenceItem> items) {
        this.workstreamId = workstreamId;
        this.updatedAt = updatedAt;
        this.items = List.copyOf(items);
    }

    static EvidenceManifest empty(String workstreamId) {
        return new EvidenceManifest(workstreamId, Instant.now().toString(), List.of());
    }

    static EvidenceManifest rebuild(Path workspaceRoot, Path workstreamDir, String workstreamId,
                                    List<String> routedRefs, ObjectMapper objectMapper) throws IOException {
        return rebuild(workspaceRoot, workstreamDir, workstreamId, routedRefs, objectMapper, Optional.empty());
    }

    static EvidenceManifest rebuild(
            Path workspaceRoot,
            Path workstreamDir,
            String workstreamId,
            List<String> routedRefs,
            ObjectMapper objectMapper,
            Optional<TaskContractProjectionVerifier.QualifiedTaskContract> qualifiedContract
    ) throws IOException {
        var items = new ArrayList<EvidenceItem>();
        addFixedArtifacts(workspaceRoot, workstreamDir, objectMapper, items, qualifiedContract);
        addRoutedAgentContracts(workspaceRoot, workstreamDir, workstreamId, routedRefs, objectMapper, items);
        addHumanDecisions(workspaceRoot, workstreamDir, objectMapper, items);
        items.sort(Comparator
                .comparing(EvidenceItem::stageId)
                .thenComparing(EvidenceItem::iterationId)
                .thenComparing(EvidenceItem::type)
                .thenComparing(EvidenceItem::sourceRef));
        return new EvidenceManifest(workstreamId, Instant.now().toString(), items);
    }

    Path write(Path workstreamDir, ObjectMapper objectMapper) throws IOException {
        var memoryDir = workstreamDir.resolve("memory");
        Files.createDirectories(memoryDir);
        var path = memoryDir.resolve("evidence-manifest.json");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), toMap());
        return path;
    }

    List<EvidenceItem> items() {
        return items;
    }

    List<String> sourceRefs() {
        return items.stream()
                .map(EvidenceItem::sourceRef)
                .filter(ref -> !ref.isBlank())
                .distinct()
                .toList();
    }

    List<EvidenceItem> stageHandoffs() {
        return items.stream()
                .filter(item -> "stage_handoff".equals(item.type()))
                .toList();
    }

    List<EvidenceItem> reviewItems() {
        return items.stream()
                .filter(item -> List.of(
                        "task_contract",
                        "contract_resolution",
                        "agent_output_contract",
                        "self_test_report",
                        "acceptance_review",
                        "git_evidence",
                        "human_decision"
                ).contains(item.type()))
                .toList();
    }

    Map<String, Object> toMap() {
        var document = new LinkedHashMap<String, Object>();
        document.put("schemaVersion", 1);
        document.put("workstreamId", workstreamId);
        document.put("updatedAt", updatedAt);
        document.put("items", items.stream().map(EvidenceItem::toMap).toList());
        return document;
    }

    private static void addFixedArtifacts(
            Path workspaceRoot,
            Path workstreamDir,
            ObjectMapper objectMapper,
            List<EvidenceItem> items,
            Optional<TaskContractProjectionVerifier.QualifiedTaskContract> qualifiedContract
    ) throws IOException {
        var taskContractPath = workstreamDir.resolve("artifacts/task-contract.json");
        if (qualifiedContract.isPresent()) {
            items.add(itemFromJson(
                    workspaceRoot, workstreamDir, taskContractPath, "task_contract",
                    qualifiedContract.get().document()));
        } else {
            addJsonArtifact(workspaceRoot, workstreamDir, objectMapper, items, taskContractPath, "task_contract");
        }
        addJsonArtifact(workspaceRoot, workstreamDir, objectMapper, items,
                workstreamDir.resolve("artifacts/contract-resolution.json"), "contract_resolution");
        addJsonArtifact(workspaceRoot, workstreamDir, objectMapper, items,
                workstreamDir.resolve("artifacts/next-stage/handoff.json"), "stage_handoff");
        addJsonArtifact(workspaceRoot, workstreamDir, objectMapper, items,
                workstreamDir.resolve("artifacts/failure-classification.json"), "failure_classification");

        var gitEvidence = workstreamDir.resolve("artifacts/git-evidence/git-evidence.json");
        if (Files.isRegularFile(gitEvidence)) {
            addJsonArtifact(workspaceRoot, workstreamDir, objectMapper, items, gitEvidence, "git_evidence");
        }
    }

    private static void addRoutedAgentContracts(
            Path workspaceRoot,
            Path workstreamDir,
            String workstreamId,
            List<String> routedRefs,
            ObjectMapper objectMapper,
            List<EvidenceItem> items
    ) {
        for (var entry : AgentEvidenceIndex.fromRefs(
                workspaceRoot, workstreamId, routedRefs, objectMapper).recent(Integer.MAX_VALUE)) {
            var document = entry.document();
            var type = switch (document.sourceArtifactType()) {
                case "self_test_report" -> "self_test_report";
                case "acceptance_review" -> "acceptance_review";
                case "stage_handoff" -> "stage_handoff";
                default -> "agent_output_contract";
            };
            var sourceRef = workspaceRef(workspaceRoot, entry.path());
            var claims = new ArrayList<String>();
            claims.addAll(document.completedWork());
            claims.addAll(document.importantDecisions());
            if (!document.decision().isBlank()) {
                claims.add("Decision: " + document.decision());
            }
            var risks = new ArrayList<String>();
            risks.addAll(document.openRisks());
            risks.addAll(document.mustFix());
            risks.addAll(document.missingEvidence());
            items.add(new EvidenceItem(
                    stableId(type, sourceRef),
                    type,
                    inferStageFromNode(document.nodeId()),
                    "",
                    document.nodeInstanceId(),
                    "",
                    sourceRef,
                    document.summary().isBlank() ? type : document.summary(),
                    List.copyOf(claims),
                    document.changedFiles(),
                    List.copyOf(risks),
                    "available"
            ));
        }
    }

    private static void addHumanDecisions(Path workspaceRoot, Path workstreamDir, ObjectMapper objectMapper,
                                          List<EvidenceItem> items) throws IOException {
        var nodesDir = workstreamDir.resolve("nodes");
        if (!Files.exists(nodesDir)) {
            return;
        }
        try (Stream<Path> paths = Files.list(nodesDir)) {
            var nodes = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .toList();
            for (var node : nodes) {
                var json = readJson(objectMapper, node).orElse(Map.of());
                var result = mapValue(json.get("result"));
                if (!result.containsKey("human_action")) {
                    continue;
                }
                items.add(new EvidenceItem(
                        stableId("human_decision", workspaceRef(workspaceRoot, node)),
                        "human_decision",
                        stageId(json),
                        text(json.get("iterationId")),
                        text(json.get("nodeInstanceId")),
                        "",
                        workspaceRef(workspaceRoot, node),
                        "Human action: " + text(result.get("human_action")),
                        List.of(text(result.get("note"))).stream().filter(s -> !s.isBlank()).toList(),
                        List.of(),
                        List.of(),
                        "available"
                ));
            }
        }
    }

    private static void addJsonArtifact(Path workspaceRoot, Path workstreamDir, ObjectMapper objectMapper,
                                        List<EvidenceItem> items, Path path, String type) throws IOException {
        if (!Files.isRegularFile(path)) {
            return;
        }
        var json = readJson(objectMapper, path).orElse(Map.of());
        items.add(itemFromJson(workspaceRoot, workstreamDir, path, type, json));
    }

    private static EvidenceItem itemFromJson(Path workspaceRoot, Path workstreamDir, Path path,
                                             String type, Map<String, Object> json) {
        var sourceRef = workspaceRef(workspaceRoot, path);
        var claims = claims(json);
        var files = files(json);
        var risks = risks(json);
        return new EvidenceItem(
                stableId(type, sourceRef),
                type,
                firstNonBlank(stageId(json), inferStageFromPath(workstreamDir, path)),
                text(json.get("iteration_id")).isBlank() ? text(json.get("iterationId")) : text(json.get("iteration_id")),
                text(json.get("node_instance_id")).isBlank() ? text(json.get("nodeInstanceId")) : text(json.get("node_instance_id")),
                text(json.get("node_execution_id")).isBlank() ? text(json.get("nodeExecutionId")) : text(json.get("node_execution_id")),
                sourceRef,
                summary(json, type),
                claims,
                files,
                risks,
                "available"
        );
    }

    private static String summary(Map<String, Object> json, String type) {
        for (var key : List.of("summary", "objective", "reasoning", "verification_summary", "verificationSummary")) {
            var value = text(json.get(key));
            if (!value.isBlank()) {
                return value;
            }
        }
        var decision = firstNonBlank(text(json.get("decision")), text(json.get("status")), text(json.get("lifecycle_status")));
        if (!decision.isBlank()) {
            return type + ": " + decision;
        }
        return type;
    }

    private static List<String> claims(Map<String, Object> json) {
        var result = new ArrayList<String>();
        for (var key : List.of("completedWork", "completed_work", "importantDecisions", "important_decisions", "acceptance_criteria")) {
            result.addAll(listOfText(json.get(key)));
        }
        var decision = firstNonBlank(text(json.get("decision")), text(json.get("status")), text(json.get("lifecycle_status")));
        if (!decision.isBlank()) {
            result.add("Decision: " + decision);
        }
        return List.copyOf(result);
    }

    private static List<String> files(Map<String, Object> json) {
        var result = new ArrayList<String>();
        for (var key : List.of("changedFiles", "changed_files", "touched_files", "files")) {
            result.addAll(listOfText(json.get(key)));
        }
        return List.copyOf(result);
    }

    private static List<String> risks(Map<String, Object> json) {
        var result = new ArrayList<String>();
        for (var key : List.of("openRisks", "open_risks", "must_fix", "missing_evidence", "risk_areas")) {
            result.addAll(listOfText(json.get(key)));
        }
        var failure = mapValue(json.get("failure"));
        var category = firstNonBlank(text(failure.get("category_hint")), text(failure.get("category")));
        if (!category.isBlank()) {
            result.add("Failure category: " + category);
        }
        return List.copyOf(result);
    }

    private static Optional<Map<String, Object>> readJson(ObjectMapper objectMapper, Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(path.toFile(), MAP_TYPE));
        } catch (IOException | RuntimeException e) {
            var fallback = new LinkedHashMap<String, Object>();
            fallback.put("summary", "Invalid JSON artifact: " + path.getFileName());
            fallback.put("raw_size", Files.isRegularFile(path)
                    ? Files.readString(path, StandardCharsets.UTF_8).length()
                    : 0);
            return Optional.of(fallback);
        }
    }

    private static String stageId(Map<String, Object> json) {
        return firstNonBlank(
                text(json.get("stage_id")),
                text(json.get("stageId")),
                inferStageFromNode(text(json.get("node_id"))),
                inferStageFromNode(text(json.get("nodeDefinitionId")))
        );
    }

    private static String inferStageFromPath(Path workstreamDir, Path path) {
        var ref = workstreamDir.relativize(path).toString();
        if (ref.contains("git-evidence")) return "implementation";
        if (ref.contains("next-stage")) return "handoff";
        if (ref.contains("failure-classification")) return "acceptance";
        return "";
    }

    private static String inferStageFromNode(String nodeId) {
        if (nodeId == null || nodeId.isBlank()) {
            return "";
        }
        var dot = nodeId.indexOf('.');
        return dot > 0 ? nodeId.substring(0, dot) : "";
    }

    private static String stableId(String type, String sourceRef) {
        return type + ":" + Integer.toHexString(sourceRef.hashCode());
    }

    private static String workspaceRef(Path workspaceRoot, Path path) {
        return workspaceRoot.toAbsolutePath().normalize()
                .relativize(path.toAbsolutePath().normalize())
                .toString();
    }

    private static Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        var copy = new LinkedHashMap<String, Object>();
        for (var entry : map.entrySet()) {
            if (entry.getKey() instanceof String key) {
                copy.put(key, entry.getValue());
            }
        }
        return copy;
    }

    private static List<String> listOfText(Object value) {
        if (!(value instanceof List<?> items)) {
            return List.of();
        }
        return items.stream()
                .map(EvidenceManifest::text)
                .filter(item -> !item.isBlank())
                .toList();
    }

    private static String firstNonBlank(String... values) {
        for (var value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    record EvidenceItem(
            String id,
            String type,
            String stageId,
            String iterationId,
            String nodeInstanceId,
            String nodeExecutionId,
            String sourceRef,
            String summary,
            List<String> claims,
            List<String> files,
            List<String> riskTags,
            String status
    ) {
        Map<String, Object> toMap() {
            var map = new LinkedHashMap<String, Object>();
            map.put("id", id);
            map.put("type", type);
            map.put("stageId", stageId);
            map.put("iterationId", iterationId);
            map.put("nodeInstanceId", nodeInstanceId);
            map.put("nodeExecutionId", nodeExecutionId);
            map.put("sourceRef", sourceRef);
            map.put("summary", summary);
            map.put("claims", claims);
            map.put("files", files);
            map.put("riskTags", riskTags);
            map.put("status", status);
            return map;
        }
    }
}
