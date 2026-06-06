package dev.dual.server;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dual.core.id.DualId;
import dev.dual.core.workflow.NodeKind;
import dev.dual.ledger.file.FileLedgerStore;
import dev.dual.ledger.file.WorkflowFileLedger;
import dev.dual.ledger.file.WorkstreamPaths;
import org.springframework.stereotype.Service;

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

@Service
public class WorkstreamQueryService {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final FileLedgerStore legacyStore;
    private final WorkflowFileLedger workflowLedger;

    public WorkstreamQueryService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.legacyStore = new FileLedgerStore();
        this.workflowLedger = new WorkflowFileLedger();
    }

    public Map<String, Object> status(Path workspaceRoot, String workstreamId) throws IOException {
        var root = workspaceRoot.toAbsolutePath().normalize();
        var paths = new WorkstreamPaths(root, new DualId(workstreamId));
        var legacy = legacyStore.loadStatus(root, workstreamId);

        var response = new LinkedHashMap<String, Object>();
        response.put("workstreamId", legacy.workstream().workstreamId());
        response.put("ledger", root.relativize(paths.workstreamDir()).toString());
        response.put("workspace", Map.of(
                "effectiveWorkdir", legacy.workspace().effectiveWorkdir(),
                "branch", legacy.workspace().branch(),
                "baseBranch", legacy.workspace().baseBranch(),
                "baseCommit", legacy.workspace().baseCommit()
        ));

        if (Files.exists(paths.workflowCurrentJson())) {
            var current = workflowLedger.loadCurrent(paths);
            var activeNodes = current.activeNodeInstanceIds().stream()
                    .map(nodeId -> activeNode(paths, nodeId))
                    .toList();
            response.put("workflow", Map.of(
                    "workflowId", current.workflowId(),
                    "definitionVersion", current.definitionVersion(),
                    "status", current.status().name(),
                    "activeNodeInstanceIds", current.activeNodeInstanceIds(),
                    "openGateIds", current.openGateIds(),
                    "activeNodes", activeNodes,
                    "updatedAt", current.updatedAt()
            ));
        }
        response.put("artifactContracts", artifactContracts(root, paths));

        var deliveryJson = paths.workstreamDir().resolve("delivery").resolve("delivery.json");
        if (Files.exists(deliveryJson)) {
            response.put("delivery", objectMapper.readValue(deliveryJson.toFile(), MAP_TYPE));
        }
        return response;
    }

    public Map<String, Object> list(Path workspaceRoot) throws IOException {
        var root = workspaceRoot.toAbsolutePath().normalize();
        var workstreamsDir = root.resolve(".agent").resolve("workstreams");
        var items = new ArrayList<Map<String, Object>>();
        if (Files.exists(workstreamsDir)) {
            try (var stream = Files.list(workstreamsDir)) {
                for (var dir : stream.filter(Files::isDirectory).sorted(Comparator.comparing(Path::toString)).toList()) {
                    var workstreamJson = dir.resolve("workstream.json");
                    if (Files.exists(workstreamJson)) {
                        items.add(workstreamSummary(root, dir, workstreamJson));
                    }
                }
            }
        }
        var response = new LinkedHashMap<String, Object>();
        response.put("workdir", root.toString());
        response.put("workstreams", items);
        return response;
    }

    public Map<String, Object> timeline(Path workspaceRoot, String workstreamId) throws IOException {
        var root = workspaceRoot.toAbsolutePath().normalize();
        var paths = new WorkstreamPaths(root, new DualId(workstreamId));
        if (!Files.exists(paths.timelineJson())) {
            throw new IOException("timeline projection not found: " + paths.timelineJson());
        }
        return objectMapper.readValue(paths.timelineJson().toFile(), MAP_TYPE);
    }

    public Map<String, Object> overview(Path workspaceRoot, String workstreamId) throws IOException {
        var root = workspaceRoot.toAbsolutePath().normalize();
        var paths = new WorkstreamPaths(root, new DualId(workstreamId));
        if (!Files.exists(paths.timelineJson())) {
            throw new IOException("timeline projection not found: " + paths.timelineJson());
        }
        var timeline = objectMapper.readValue(paths.timelineJson().toFile(), MAP_TYPE);
        var metrics = readJson(paths.workstreamMetricsJson());
        var summary = readJson(paths.workstreamDir().resolve("reports").resolve("real-case-summary.json"));
        var nodes = mapList(timeline.get("nodes"));
        var activeNodeIds = textList(timeline.get("activeNodeInstanceIds"));
        var openGateIds = textList(timeline.get("openGateIds"));
        var status = text(timeline.get("workflowStatus"));
        var repairActions = repairActions(paths, summary);
        var finalAcceptance = finalAcceptance(paths, summary);
        var repairLoopCount = Math.max(number(summary.get("repair_loop_count")), repairActions.size());
        var verificationConfidence = text(summary.get("verification_confidence"), confidence(finalAcceptance));
        var artifacts = artifacts(root, paths);

        var response = new LinkedHashMap<String, Object>();
        response.put("workstreamId", workstreamId);
        response.put("workspace", root.toString());
        response.put("ledger", root.relativize(paths.workstreamDir()).toString());
        response.put("workflow", Map.of(
                "workflowId", text(timeline.get("workflowId")),
                "definitionVersion", text(timeline.get("definitionVersion")),
                "status", status,
                "currentNode", currentNode(nodes, activeNodeIds),
                "activeNodeInstanceIds", activeNodeIds,
                "openGateIds", openGateIds,
                "updatedAt", text(timeline.get("updatedAt"))
        ));
        response.put("acceptance", Map.of(
                "finalDecision", finalAcceptance,
                "verificationConfidence", verificationConfidence,
                "repairLoopCount", repairLoopCount,
                "repairActions", repairActions
        ));
        response.put("whyStopped", whyStopped(status, currentNode(nodes, activeNodeIds), openGateIds));
        response.put("nextAction", nextAction(root, workstreamId, status, openGateIds));
        response.put("attention", attention(root, workstreamId, status, finalAcceptance, verificationConfidence, repairLoopCount, artifacts));
        response.put("metrics", metrics);
        response.put("recentTimeline", recent(nodes, 8).stream().map(this::timelineItem).toList());
        response.put("latestAgentRuns", latestAgentRuns(root, paths, 6));
        response.put("artifacts", artifacts);
        return response;
    }

    public Map<String, Object> currentReview(Path workspaceRoot, String workstreamId) throws IOException {
        var root = workspaceRoot.toAbsolutePath().normalize();
        var paths = new WorkstreamPaths(root, new DualId(workstreamId));
        var current = workflowLedger.loadCurrent(paths);
        if (current.activeNodeInstanceIds().isEmpty()) {
            throw new IOException("no active node for workstream: " + workstreamId);
        }
        var node = workflowLedger.loadNode(paths, current.activeNodeInstanceIds().get(0));
        if (node.kind() != NodeKind.GATE) {
            throw new IOException("active node is not a human gate: " + node.nodeDefinitionId());
        }
        var packetRef = ".agent/workstreams/" + workstreamId + "/reviews/" + node.nodeInstanceId() + ".md";
        var packetJsonRef = ".agent/workstreams/" + workstreamId + "/reviews/" + node.nodeInstanceId() + ".json";
        var packetPath = root.resolve(packetRef).normalize();
        var packetJsonPath = root.resolve(packetJsonRef).normalize();
        if (!packetPath.startsWith(root) || !Files.exists(packetPath)) {
            throw new IOException("review packet not found: " + packetRef);
        }

        var response = new LinkedHashMap<String, Object>();
        response.put("workstreamId", workstreamId);
        response.put("gateNodeInstanceId", node.nodeInstanceId());
        response.put("gateNodeDefinitionId", node.nodeDefinitionId());
        response.put("packetPath", packetRef);
        response.put("packetMarkdown", Files.readString(packetPath, StandardCharsets.UTF_8));
        if (packetJsonPath.startsWith(root) && Files.exists(packetJsonPath)) {
            response.put("packet", objectMapper.readValue(packetJsonPath.toFile(), MAP_TYPE));
        }
        return response;
    }

    public Map<String, Object> currentRecovery(Path workspaceRoot, String workstreamId) throws IOException {
        var root = workspaceRoot.toAbsolutePath().normalize();
        var paths = new WorkstreamPaths(root, new DualId(workstreamId));
        var failurePath = paths.currentFailureMarkdown();
        var failureRef = root.relativize(failurePath.toAbsolutePath().normalize()).toString();

        var response = new LinkedHashMap<String, Object>();
        response.put("workstreamId", workstreamId);
        response.put("recoveryPath", failureRef);
        if (!Files.isRegularFile(failurePath)) {
            response.put("available", false);
            response.put("message", "No current failure summary found.");
            response.put("markdown", "");
            return response;
        }
        response.put("available", true);
        response.put("message", "Current failure summary is available.");
        response.put("markdown", Files.readString(failurePath, StandardCharsets.UTF_8));
        return response;
    }

    private Map<String, Object> workstreamSummary(Path root, Path dir, Path workstreamJson) throws IOException {
        var workstream = objectMapper.readValue(workstreamJson.toFile(), MAP_TYPE);
        var item = new LinkedHashMap<String, Object>();
        item.put("workstreamId", text(workstream.get("workstreamId")));
        item.put("title", text(workstream.get("title")));
        item.put("status", text(workstream.get("status")));
        item.put("updatedAt", text(workstream.get("updatedAt")));
        item.put("ledger", root.relativize(dir).toString());

        var currentJson = dir.resolve("workflow").resolve("current.json");
        if (Files.exists(currentJson)) {
            var current = objectMapper.readValue(currentJson.toFile(), MAP_TYPE);
            item.put("workflowId", text(current.get("workflowId")));
            item.put("workflowStatus", text(current.get("status")));
            item.put("activeNodeCount", sizeOf(current.get("activeNodeInstanceIds")));
            item.put("openGateCount", sizeOf(current.get("openGateIds")));
        }
        return item;
    }

    private Map<String, Object> activeNode(WorkstreamPaths paths, String nodeId) {
        try {
            var node = workflowLedger.loadNode(paths, nodeId);
            return Map.of(
                    "nodeInstanceId", node.nodeInstanceId(),
                    "nodeDefinitionId", node.nodeDefinitionId(),
                    "kind", node.kind().name(),
                    "handlerType", node.handlerType(),
                    "status", node.status().name(),
                    "iterationId", node.iterationId(),
                    "outputRefs", node.outputRefs() == null ? List.of() : node.outputRefs()
            );
        } catch (IOException e) {
            throw new WorkstreamQueryException(e);
        }
    }

    private List<Map<String, Object>> artifactContracts(Path root, WorkstreamPaths paths) throws IOException {
        if (!Files.exists(paths.runsDir())) {
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(paths.runsDir())) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".contract.json"))
                    .sorted(Comparator.comparing(WorkstreamQueryService::lastModified).reversed())
                    .limit(12)
                    .map(path -> artifactContract(root, path))
                    .toList();
        }
    }

    private Map<String, Object> artifactContract(Path root, Path path) {
        var source = readJson(path);
        var item = new LinkedHashMap<String, Object>();
        for (var key : List.of(
                "artifact_type",
                "node_id",
                "handler_type",
                "decision",
                "summary",
                "must_fix",
                "open_risks",
                "changed_files",
                "verification_summary",
                "source_output_ref",
                "generated_at"
        )) {
            item.put(key, source.getOrDefault(key, defaultValueFor(key)));
        }
        item.put("sidecar_ref", root.relativize(path.toAbsolutePath().normalize()).toString());
        return item;
    }

    private List<Map<String, Object>> repairActions(WorkstreamPaths paths, Map<String, Object> summary) throws IOException {
        var actions = new ArrayList<Map<String, Object>>();
        if (!Files.isRegularFile(paths.eventsJsonl())) {
            if (number(summary.get("repair_loop_count")) > 0) {
                actions.add(Map.of("action", "see-summary"));
            }
            return actions;
        }
        for (var line : Files.readAllLines(paths.eventsJsonl(), StandardCharsets.UTF_8)) {
            if (line.isBlank()) {
                continue;
            }
            var event = objectMapper.readValue(line, MAP_TYPE);
            if (!"iteration_started".equals(text(event.get("event_type")))) {
                continue;
            }
            var payload = mapValue(event.get("payload"));
            var action = text(payload.get("trigger_action"));
            if (action.startsWith("REJECT_")) {
                actions.add(Map.of("action", action));
            }
        }
        if (actions.isEmpty() && number(summary.get("repair_loop_count")) > 0) {
            actions.add(Map.of("action", "see-summary"));
        }
        return actions;
    }

    private String finalAcceptance(WorkstreamPaths paths, Map<String, Object> summary) throws IOException {
        var fromSummary = text(summary.get("final_acceptance_decision"));
        if (!fromSummary.isBlank()) {
            return fromSummary;
        }
        if (!Files.isDirectory(paths.runsDir())) {
            return "none";
        }
        var decisions = new ArrayList<String>();
        try (var stream = Files.walk(paths.runsDir(), 4)) {
            for (var path : stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals("acceptance-codex-review.contract.json"))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList()) {
                var decision = text(readJson(path).get("decision"));
                if (!decision.isBlank()) {
                    decisions.add(decision);
                }
            }
        }
        return decisions.isEmpty() ? "none" : decisions.get(decisions.size() - 1);
    }

    private String currentNode(List<Map<String, Object>> nodes, List<String> activeNodeIds) {
        if (activeNodeIds.isEmpty()) {
            return "";
        }
        var activeNodeId = activeNodeIds.get(0);
        for (var node : nodes) {
            if (activeNodeId.equals(text(node.get("nodeInstanceId")))) {
                return text(node.get("nodeDefinitionId"), activeNodeId);
            }
        }
        return activeNodeId;
    }

    private String whyStopped(String status, String currentNode, List<String> openGateIds) {
        if ("WAITING_FOR_HUMAN".equals(status)) {
            return "Waiting for human review" + (openGateIds.isEmpty() ? "." : ": " + String.join(", ", openGateIds));
        }
        if ("WAITING_FOR_RECOVERY".equals(status)) {
            return "Waiting for recovery. Inspect the failure summary before retrying.";
        }
        if ("FAILED".equals(status) || "BLOCKED".equals(status)) {
            return "Stopped at " + (currentNode.isBlank() ? "the current node" : currentNode) + ".";
        }
        if ("COMPLETED".equals(status)) {
            return "Workflow completed.";
        }
        if ("RUNNING".equals(status)) {
            return currentNode.isBlank() ? "Workflow is running." : "Running at " + currentNode + ".";
        }
        return status.isBlank() ? "Workflow state is not available." : "Workflow status is " + status + ".";
    }

    private String nextAction(Path root, String workstreamId, String status, List<String> openGateIds) {
        var base = "--workdir \"" + root + "\" " + workstreamId;
        if ("WAITING_FOR_HUMAN".equals(status)) {
            return "Read the review packet, then run: dualctl review show " + base;
        }
        if ("WAITING_FOR_RECOVERY".equals(status) || "FAILED".equals(status) || "BLOCKED".equals(status)) {
            return "Inspect recovery, then retry when ready: dualctl recovery show " + base;
        }
        if ("COMPLETED".equals(status)) {
            return "Review final artifacts: dualctl summary " + base;
        }
        if (!openGateIds.isEmpty()) {
            return "Review the open gate: dualctl review show " + base;
        }
        return "Continue workflow: dualctl run " + base;
    }

    private List<Map<String, Object>> attention(Path root, String workstreamId, String status, String finalAcceptance,
                                                String verificationConfidence, int repairLoopCount,
                                                Map<String, Object> artifacts) {
        var items = new ArrayList<Map<String, Object>>();
        var base = "--workdir \"" + root + "\" " + workstreamId;
        if ("WAITING_FOR_HUMAN".equals(status)) {
            items.add(attentionItem(
                    "info",
                    "Human gate is open",
                    "Read the current review packet before approving, revising, or completing the workstream.",
                    "dualctl review show " + base
            ));
        }
        if ("WAITING_FOR_RECOVERY".equals(status) || "FAILED".equals(status) || "BLOCKED".equals(status)) {
            items.add(attentionItem(
                    "critical",
                    "Workflow needs recovery",
                    "Inspect the current failure summary and only retry after the external cause or ledger issue is understood.",
                    "dualctl recovery show " + base
            ));
        }
        if ("ACCEPT_WITH_NOTES".equals(finalAcceptance)) {
            items.add(attentionItem(
                    "warning",
                    "Accepted with notes",
                    "The lifecycle completed, but Codex left notes. Review the final report before treating this as delivery-ready.",
                    "dualctl summary " + base
            ));
        } else if ("REJECT".equals(finalAcceptance)) {
            items.add(attentionItem(
                    "critical",
                    "Final acceptance rejected",
                    "Do not deliver until the rejection has been classified and repaired.",
                    "dualctl recovery show " + base
            ));
        }
        if (!"none".equals(finalAcceptance)
                && ("LOW".equals(verificationConfidence) || "UNKNOWN".equals(verificationConfidence))) {
            items.add(attentionItem(
                    "warning",
                    "Verification confidence is " + verificationConfidence,
                    "Evidence is weak or missing. Read the self-test report, Git evidence, and Codex acceptance output.",
                    "dualctl summary " + base
            ));
        }
        if (repairLoopCount > 0) {
            items.add(attentionItem(
                    "info",
                    "Repair loop occurred",
                    "This run required " + repairLoopCount + " repair loop(s). Check the repaired diff and final acceptance notes.",
                    "dualctl timeline " + base
            ));
        }
        if ("COMPLETED".equals(status)) {
            for (var entry : artifacts.entrySet()) {
                var artifact = mapValue(entry.getValue());
                if (!Boolean.TRUE.equals(artifact.get("available")) && requiredArtifact(entry.getKey())) {
                    items.add(attentionItem(
                            "warning",
                            "Missing " + humanArtifactName(entry.getKey()),
                            "A key lifecycle artifact is missing from the ledger.",
                            "dualctl summary " + base
                    ));
                }
            }
        }
        if (items.isEmpty() && "COMPLETED".equals(status) && "ACCEPT".equals(finalAcceptance)) {
            items.add(attentionItem(
                    "ok",
                    "No obvious follow-up",
                    "The workflow completed with ACCEPT and required artifacts are present.",
                    "dualctl summary " + base
            ));
        }
        return items;
    }

    private Map<String, Object> attentionItem(String severity, String title, String detail, String command) {
        var item = new LinkedHashMap<String, Object>();
        item.put("severity", severity);
        item.put("title", title);
        item.put("detail", detail);
        item.put("command", command);
        return item;
    }

    private boolean requiredArtifact(String key) {
        return switch (key) {
            case "finalReport", "workstreamSummary", "handoffJson", "nextStagePrompt" -> true;
            default -> false;
        };
    }

    private String humanArtifactName(String key) {
        return switch (key) {
            case "finalReport" -> "final report";
            case "workstreamSummary" -> "workstream summary";
            case "handoffJson" -> "handoff JSON";
            case "nextStagePrompt" -> "next-stage prompt";
            case "deliveryPrBody" -> "delivery PR body";
            default -> key;
        };
    }

    private Map<String, Object> timelineItem(Map<String, Object> node) {
        var item = new LinkedHashMap<String, Object>();
        item.put("nodeDefinitionId", text(node.get("nodeDefinitionId")));
        item.put("status", text(node.get("status")));
        item.put("kind", text(node.get("kind")));
        item.put("iterationId", text(node.get("iterationId")));
        item.put("durationMs", text(node.get("durationMs")));
        return item;
    }

    private List<Map<String, Object>> latestAgentRuns(Path root, WorkstreamPaths paths, int limit) throws IOException {
        if (!Files.isDirectory(paths.runsDir())) {
            return List.of();
        }
        try (var stream = Files.list(paths.runsDir())) {
            return stream
                    .filter(Files::isDirectory)
                    .sorted(Comparator.comparing(WorkstreamQueryService::lastModified).reversed())
                    .limit(limit)
                    .map(path -> runSummary(root, path))
                    .toList();
        }
    }

    private Map<String, Object> runSummary(Path root, Path runDir) {
        var runJson = runDir.resolve("run.json");
        var source = readJson(runJson);
        var item = new LinkedHashMap<String, Object>();
        item.put("runId", text(source.get("runId"), runDir.getFileName().toString()));
        item.put("agent", text(source.get("agent")));
        item.put("nodeId", text(source.get("nodeId")));
        item.put("status", text(source.get("status")));
        item.put("durationMs", text(source.get("durationMs")));
        item.put("ref", root.relativize(runDir.toAbsolutePath().normalize()).toString());
        return item;
    }

    private Map<String, Object> artifacts(Path root, WorkstreamPaths paths) {
        var artifacts = new LinkedHashMap<String, Object>();
        artifact(artifacts, root, "finalReport", paths.workstreamDir().resolve("reports").resolve("latest-final-report.md"));
        artifact(artifacts, root, "workstreamSummary", paths.workstreamDir().resolve("reports").resolve("workstream-summary.md"));
        artifact(artifacts, root, "handoffJson", paths.workstreamDir().resolve("artifacts").resolve("next-stage").resolve("handoff.json"));
        artifact(artifacts, root, "nextStagePrompt", paths.workstreamDir().resolve("artifacts").resolve("next-stage").resolve("next-stage-prompt.md"));
        artifact(artifacts, root, "deliveryPrBody", paths.workstreamDir().resolve("delivery").resolve("pr-body.md"));
        artifact(artifacts, root, "currentFailure", paths.currentFailureMarkdown());
        return artifacts;
    }

    private void artifact(Map<String, Object> artifacts, Path root, String key, Path path) {
        var normalized = path.toAbsolutePath().normalize();
        artifacts.put(key, Map.of(
                "available", Files.isRegularFile(path),
                "path", root.relativize(normalized).toString()
        ));
    }

    private Map<String, Object> readJson(Path path) {
        try {
            return objectMapper.readValue(path.toFile(), MAP_TYPE);
        } catch (IOException e) {
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapValue(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> mapList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream()
                    .filter(Map.class::isInstance)
                    .map(item -> (Map<String, Object>) item)
                    .toList();
        }
        return List.of();
    }

    private static List<Map<String, Object>> recent(List<Map<String, Object>> nodes, int limit) {
        return nodes.stream()
                .skip(Math.max(0, nodes.size() - limit))
                .toList();
    }

    private static List<String> textList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private static int number(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static String confidence(String finalDecision) {
        return switch (finalDecision) {
            case "ACCEPT" -> "HIGH";
            case "ACCEPT_WITH_NOTES" -> "MEDIUM";
            case "REJECT" -> "LOW";
            default -> "UNKNOWN";
        };
    }

    private static Object defaultValueFor(String key) {
        return switch (key) {
            case "must_fix", "open_risks", "changed_files" -> List.of();
            default -> "";
        };
    }

    private static Instant lastModified(Path path) {
        try {
            return Files.readAttributes(path, BasicFileAttributes.class).lastModifiedTime().toInstant();
        } catch (IOException ignored) {
            return Instant.EPOCH;
        }
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String text(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private static int sizeOf(Object value) {
        return value instanceof List<?> list ? list.size() : 0;
    }

    static final class WorkstreamQueryException extends RuntimeException {
        WorkstreamQueryException(IOException cause) {
            super(cause);
        }
    }
}
