package dev.dual.server;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dual.core.id.DualId;
import dev.dual.core.workflow.NodeKind;
import dev.dual.ledger.file.AgentTaskDocument;
import dev.dual.ledger.file.FileLedgerStore;
import dev.dual.ledger.file.IterationSemantics;
import dev.dual.ledger.file.RecoveryAssessment;
import dev.dual.ledger.file.WorkflowFileLedger;
import dev.dual.ledger.file.WorkflowIterationDocument;
import dev.dual.ledger.file.WorkstreamAttentionEvaluator;
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
        this.workflowLedger = ServerLedgerFactory.create();
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
            var agentTasks = agentTaskSummaries(paths, 20);
            var activeNodes = current.activeNodeInstanceIds().stream()
                    .map(nodeId -> activeNode(paths, nodeId, agentTasks))
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
            response.put("agentTasks", agentTasks);
        }
        response.put("artifactContracts", artifactContracts(root, paths));

        var deliveryJson = paths.workstreamDir().resolve("delivery").resolve("delivery.json");
        if (Files.exists(deliveryJson)) {
            response.put("delivery", objectMapper.readValue(deliveryJson.toFile(), MAP_TYPE));
        }
        if (Files.exists(paths.deliveryMergePlanJson())) {
            response.put("deliveryMerge", objectMapper.readValue(paths.deliveryMergePlanJson().toFile(), MAP_TYPE));
        }
        return response;
    }

    public Map<String, Object> list(Path workspaceRoot) throws IOException {
        var root = workspaceRoot.toAbsolutePath().normalize();
        var items = new ArrayList<Map<String, Object>>();
        collectWorkstreams(root, root, items);
        for (var workspace : siblingAgentRunWorkspaces(root)) {
            collectWorkstreams(root, workspace, items);
        }
        items.sort(Comparator
                .comparing((Map<String, Object> item) -> text(item.get("updatedAt"))).reversed()
                .thenComparing(item -> text(item.get("workstreamId"))));
        var response = new LinkedHashMap<String, Object>();
        response.put("workdir", root.toString());
        response.put("workstreams", items);
        return response;
    }

    private void collectWorkstreams(Path requestedRoot, Path effectiveRoot, List<Map<String, Object>> items) throws IOException {
        var workstreamsDir = effectiveRoot.resolve(".agent").resolve("workstreams");
        if (!Files.exists(workstreamsDir)) {
            return;
        }
        try (var stream = Files.list(workstreamsDir)) {
            for (var dir : stream.filter(Files::isDirectory).sorted(Comparator.comparing(Path::toString)).toList()) {
                var workstreamJson = dir.resolve("workstream.json");
                if (Files.exists(workstreamJson)) {
                    items.add(workstreamSummary(requestedRoot, effectiveRoot, dir, workstreamJson));
                }
            }
        }
    }

    private List<Path> siblingAgentRunWorkspaces(Path root) throws IOException {
        var parent = root.getParent();
        var name = root.getFileName() == null ? "" : root.getFileName().toString();
        if (parent == null || name.isBlank()) {
            return List.of();
        }
        var agentRunsDir = parent.resolve(name + "-agent-runs");
        if (!Files.isDirectory(agentRunsDir)) {
            return List.of();
        }
        try (var stream = Files.list(agentRunsDir)) {
            return stream
                    .filter(Files::isDirectory)
                    .filter(path -> Files.isDirectory(path.resolve(".agent").resolve("workstreams")))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        }
    }

    public Map<String, Object> timeline(Path workspaceRoot, String workstreamId) throws IOException {
        var root = workspaceRoot.toAbsolutePath().normalize();
        var paths = new WorkstreamPaths(root, new DualId(workstreamId));
        if (!Files.exists(paths.timelineJson())) {
            throw new IOException("timeline projection not found: " + paths.timelineJson());
        }
        return normalizedTimeline(paths);
    }

    /**
     * Builds the operator overview API projection.
     *
     * The service keeps projection concerns here: enum/domain values are exposed
     * as stable API strings, while lifecycle meaning comes from ledger read
     * models such as WorkstreamAttention. It should not invent workflow state.
     */
    public Map<String, Object> overview(Path workspaceRoot, String workstreamId) throws IOException {
        var root = workspaceRoot.toAbsolutePath().normalize();
        var paths = new WorkstreamPaths(root, new DualId(workstreamId));
        if (!Files.exists(paths.timelineJson())) {
            throw new IOException("timeline projection not found: " + paths.timelineJson());
        }
        var timeline = normalizedTimeline(paths);
        var metrics = readJson(paths.workstreamMetricsJson());
        var summary = readJson(paths.workstreamDir().resolve("reports").resolve("real-case-summary.json"));
        var nodes = mapList(timeline.get("nodes"));
        var current = Files.exists(paths.workflowCurrentJson()) ? workflowLedger.loadCurrent(paths) : null;
        var activeNodeIds = current == null ? textList(timeline.get("activeNodeInstanceIds")) : current.activeNodeInstanceIds();
        var openGateIds = current == null ? textList(timeline.get("openGateIds")) : current.openGateIds();
        var status = current == null ? text(timeline.get("workflowStatus")) : current.status().name();
        var workflowUpdatedAt = current == null ? text(timeline.get("updatedAt")) : current.updatedAt().toString();
        var workflowId = current == null ? text(timeline.get("workflowId")) : current.workflowId();
        var definitionVersion = current == null ? text(timeline.get("definitionVersion")) : current.definitionVersion();
        var repairActions = repairActions(paths, summary);
        var finalAcceptance = finalAcceptance(paths, summary);
        var repairLoopCount = Math.max(number(summary.get("repair_loop_count")), repairActions.size());
        var verificationConfidence = text(summary.get("verification_confidence"), confidence(finalAcceptance));
        var artifacts = artifacts(root, paths);
        var completionAudit = completionAudit(paths, artifacts);
        var deliveryMerge = readJson(paths.deliveryMergePlanJson());
        var goalSatisfaction = readJson(paths.goalSatisfactionJson());
        var currentNode = currentNode(nodes, activeNodeIds);
        var latestAgentRuns = latestAgentRuns(root, paths, 6);
        var latestAgentTasks = agentTaskSummaries(paths, 12);
        var recoveryAssessment = recoveryAssessment(root, paths, workstreamId);
        var workstreamAttention = WorkstreamAttentionEvaluator.evaluate(paths);
        var attentionItems = new ArrayList<Map<String, Object>>();
        attentionItems.add(workstreamAttention.toMap());
        attentionItems.addAll(attention(root, workstreamId, status, finalAcceptance, verificationConfidence, repairLoopCount,
                artifacts, completionAudit));

        var response = new LinkedHashMap<String, Object>();
        response.put("workstreamId", workstreamId);
        response.put("workspace", root.toString());
        response.put("ledger", root.relativize(paths.workstreamDir()).toString());
        response.put("workflow", Map.of(
                "workflowId", workflowId,
                "definitionVersion", definitionVersion,
                "status", status,
                "currentNode", currentNode,
                "activeNodeInstanceIds", activeNodeIds,
                "openGateIds", openGateIds,
                "updatedAt", workflowUpdatedAt
        ));
        response.put("acceptance", Map.of(
                "finalDecision", finalAcceptance,
                "verificationConfidence", verificationConfidence,
                "repairLoopCount", repairLoopCount,
                "repairActions", repairActions
        ));
        response.put("goalSatisfaction", goalSatisfaction);
        response.put("whyStopped", workstreamAttention.detail());
        response.put("nextAction", nextAction(workstreamAttention));
        response.put("attention", attentionItems);
        response.put("metrics", metrics);
        response.put("recentTimeline", recent(nodes, 8).stream().map(this::timelineItem).toList());
        response.put("latestAgentTasks", latestAgentTasks);
        response.put("latestAgentRuns", latestAgentRuns);
        response.put("artifacts", artifacts);
        response.put("completionAudit", completionAudit);
        response.put("deliveryMerge", deliveryMerge);
        response.put("recoveryAssessment", recoveryAssessment);
        return response;
    }

    private Map<String, Object> normalizedTimeline(WorkstreamPaths paths) throws IOException {
        var timeline = new LinkedHashMap<>(objectMapper.readValue(paths.timelineJson().toFile(), MAP_TYPE));
        timeline.put("nodes", sortedTimelineNodes(mapList(timeline.get("nodes"))));
        return timeline;
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
        return reviewPacket(root, paths, workstreamId, node.nodeInstanceId(), node.nodeDefinitionId());
    }

    public Map<String, Object> review(Path workspaceRoot, String workstreamId, String nodeInstanceId) throws IOException {
        var root = workspaceRoot.toAbsolutePath().normalize();
        var paths = new WorkstreamPaths(root, new DualId(workstreamId));
        var node = workflowLedger.loadNode(paths, nodeInstanceId);
        if (node.kind() != NodeKind.GATE) {
            throw new IOException("node is not a human gate: " + node.nodeDefinitionId());
        }
        return reviewPacket(root, paths, workstreamId, node.nodeInstanceId(), node.nodeDefinitionId());
    }

    private Map<String, Object> reviewPacket(
            Path root,
            WorkstreamPaths paths,
            String workstreamId,
            String nodeInstanceId,
            String nodeDefinitionId
    ) throws IOException {
        var packetRef = ".agent/workstreams/" + workstreamId + "/reviews/" + nodeInstanceId + ".md";
        var packetJsonRef = ".agent/workstreams/" + workstreamId + "/reviews/" + nodeInstanceId + ".json";
        var packetPath = root.resolve(packetRef).normalize();
        var packetJsonPath = root.resolve(packetJsonRef).normalize();
        if (!packetPath.startsWith(root) || !Files.exists(packetPath)) {
            throw new IOException("review packet not found: " + packetRef);
        }

        var response = new LinkedHashMap<String, Object>();
        response.put("workstreamId", workstreamId);
        response.put("gateNodeInstanceId", nodeInstanceId);
        response.put("gateNodeDefinitionId", nodeDefinitionId);
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
        response.put("assessment", recoveryAssessment(root, paths, workstreamId));
        return response;
    }

    public Map<String, Object> recoveryAssessment(Path workspaceRoot, String workstreamId) {
        var root = workspaceRoot.toAbsolutePath().normalize();
        var paths = new WorkstreamPaths(root, new DualId(workstreamId));
        return recoveryAssessment(root, paths, workstreamId);
    }

    private Map<String, Object> workstreamSummary(Path requestedRoot, Path effectiveRoot, Path dir, Path workstreamJson) throws IOException {
        var workstream = objectMapper.readValue(workstreamJson.toFile(), MAP_TYPE);
        var item = new LinkedHashMap<String, Object>();
        item.put("workstreamId", text(workstream.get("workstreamId")));
        item.put("title", text(workstream.get("title")));
        item.put("status", text(workstream.get("status")));
        item.put("updatedAt", text(workstream.get("updatedAt")));
        item.put("effectiveWorkdir", effectiveRoot.toString());
        item.put("ledger", safeRelative(requestedRoot, dir));

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

    private Map<String, Object> activeNode(WorkstreamPaths paths, String nodeId, List<Map<String, Object>> agentTasks) {
        try {
            var node = workflowLedger.loadNode(paths, nodeId);
            var item = new LinkedHashMap<String, Object>();
            item.put("nodeInstanceId", node.nodeInstanceId());
            item.put("nodeDefinitionId", node.nodeDefinitionId());
            item.put("kind", node.kind().name());
            item.put("handlerType", node.handlerType());
            item.put("status", node.status().name());
            item.put("iterationId", node.iterationId());
            item.put("outputRefs", node.outputRefs() == null ? List.of() : node.outputRefs());
            item.put("latestExecutionId", node.latestExecutionId());
            latestTaskForNode(agentTasks, node.nodeInstanceId()).ifPresent(task -> item.put("latestAgentTask", task));
            return item;
        } catch (IOException e) {
            throw new WorkstreamQueryException(e);
        }
    }

    private List<Map<String, Object>> agentTaskSummaries(WorkstreamPaths paths, int limit) {
        try {
            return workflowLedger.readAgentTasks(paths).stream()
                    .sorted(Comparator.comparing(AgentTaskDocument::createdAt).reversed())
                    .limit(limit)
                    .map(this::agentTaskSummary)
                    .toList();
        } catch (IOException ignored) {
            return List.of();
        }
    }

    private Map<String, Object> agentTaskSummary(AgentTaskDocument task) {
        var item = new LinkedHashMap<String, Object>();
        item.put("agentTaskId", task.agentTaskId());
        item.put("workflowExecutionId", task.workflowExecutionId());
        item.put("nodeExecutionId", task.nodeExecutionId());
        item.put("nodeInstanceId", task.nodeInstanceId());
        item.put("nodeDefinitionId", task.nodeDefinitionId());
        item.put("kind", task.nodeKind().name());
        item.put("handlerType", task.handlerType());
        item.put("status", task.status().name());
        item.put("agent", task.agent());
        item.put("session", task.session());
        item.put("promptTemplate", task.promptTemplate());
        item.put("contextPackageRef", task.contextPackageRef());
        item.put("expectedContract", task.expectedContract());
        item.put("createdAt", task.createdAt());
        item.put("claimedAt", task.claimedAt());
        item.put("completedAt", task.completedAt());
        item.put("errorMessage", task.errorMessage());
        return item;
    }

    private java.util.Optional<Map<String, Object>> latestTaskForNode(List<Map<String, Object>> agentTasks, String nodeInstanceId) {
        return agentTasks.stream()
                .filter(task -> nodeInstanceId.equals(text(task.get("nodeInstanceId"))))
                .findFirst();
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
            if (IterationSemantics.isRepairLoopTrigger(action)) {
                actions.add(Map.of("action", action));
            }
        }
        if (actions.isEmpty() && number(summary.get("repair_loop_count")) > 0) {
            actions.add(Map.of("action", "see-summary"));
        }
        return actions;
    }

    private Map<String, Object> recoveryAssessment(Path root, WorkstreamPaths paths, String workstreamId) {
        try {
            var current = workflowLedger.loadCurrent(paths);
            if (current.activeNodeInstanceIds().isEmpty()) {
                return Map.of();
            }
            var node = workflowLedger.loadNode(paths, current.activeNodeInstanceIds().get(0));
            var assessment = new LinkedHashMap<>(RecoveryAssessment.from(node, root, workstreamId).toMap());
            assessment.put("mistakenIterationCloseable", mistakenIterationCloseable(paths, node.iterationId()));
            return assessment;
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private boolean mistakenIterationCloseable(WorkstreamPaths paths, String iterationId) {
        try {
            if (iterationId == null || iterationId.isBlank()) {
                return false;
            }
            var iteration = objectMapper.readValue(paths.iterationJson(iterationId).toFile(), WorkflowIterationDocument.class);
            if (!"approved".equals(iteration.triggerAction()) || iteration.triggerNodeInstanceId().isBlank()) {
                return false;
            }
            var trigger = workflowLedger.loadNode(paths, iteration.triggerNodeInstanceId());
            return "gate.human_next_stage_approval".equals(trigger.nodeDefinitionId());
        } catch (Exception ignored) {
            return false;
        }
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
        var node = currentNodeView(nodes, activeNodeIds);
        if (!node.isEmpty()) {
            return text(node.get("nodeDefinitionId"), text(node.get("nodeInstanceId")));
        }
        if (activeNodeIds.isEmpty()) {
            return "";
        }
        return activeNodeIds.get(0);
    }

    private Map<String, Object> currentNodeView(List<Map<String, Object>> nodes, List<String> activeNodeIds) {
        if (activeNodeIds.isEmpty()) {
            return Map.of();
        }
        var activeNodeId = activeNodeIds.get(0);
        for (var node : nodes) {
            if (activeNodeId.equals(text(node.get("nodeInstanceId")))) {
                return node;
            }
        }
        return Map.of();
    }

    private String nextAction(dev.dual.ledger.file.WorkstreamAttention attention) {
        if (attention.command().isBlank()) {
            return attention.nextAction();
        }
        return attention.nextAction() + " Command: " + attention.command();
    }

    private List<Map<String, Object>> attention(Path root, String workstreamId, String status, String finalAcceptance,
                                                String verificationConfidence, int repairLoopCount,
                                                Map<String, Object> artifacts,
                                                Map<String, Object> completionAudit) {
        var items = new ArrayList<Map<String, Object>>();
        var base = "--workdir \"" + root + "\" " + workstreamId;
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
            var gaps = textList(completionAudit.get("gaps"));
            if (!gaps.isEmpty()) {
                items.add(attentionItem(
                        "warning",
                        "Completion artifacts need review",
                        String.join("; ", gaps),
                        "dualctl summary " + base
                ));
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
            case "contextPackageMetadata" -> "context package metadata";
            default -> key;
        };
    }

    private Map<String, Object> timelineItem(Map<String, Object> node) {
        var item = new LinkedHashMap<String, Object>();
        item.put("nodeDefinitionId", text(node.get("nodeDefinitionId")));
        item.put("displayName", text(node.get("displayName"), text(node.get("nodeDefinitionId"))));
        item.put("status", text(node.get("status")));
        item.put("kind", text(node.get("kind")));
        item.put("iterationId", text(node.get("iterationId")));
        item.put("durationMs", text(node.get("durationMs")));
        item.put("latestExecutionId", text(node.get("latestExecutionId")));
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
        item.put("usage", map(source.get("usage")));
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
        latestContextPackageMetadata(paths).ifPresent(path ->
                artifact(artifacts, root, "contextPackageMetadata", path));
        return artifacts;
    }

    private java.util.Optional<Path> latestContextPackageMetadata(WorkstreamPaths paths) {
        var contextDir = paths.workstreamDir().resolve("context");
        if (!Files.isDirectory(contextDir)) {
            return java.util.Optional.empty();
        }
        try (var stream = Files.list(contextDir)) {
            return stream
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .max(Comparator.comparingLong(this::lastModifiedMillis));
        } catch (IOException ignored) {
            return java.util.Optional.empty();
        }
    }

    private long lastModifiedMillis(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException ignored) {
            return 0L;
        }
    }

    private Map<String, Object> completionAudit(WorkstreamPaths paths, Map<String, Object> artifacts) {
        if (Files.isRegularFile(paths.completionAuditJson())) {
            return readJson(paths.completionAuditJson());
        }
        var gaps = new ArrayList<String>();
        auditFinalReport(paths, artifacts, gaps);
        auditHandoff(paths, artifacts, gaps);
        auditNextStagePrompt(paths, artifacts, gaps);
        return Map.of(
                "status", gaps.isEmpty() ? "ready" : "needs_review",
                "gaps", List.copyOf(gaps)
        );
    }

    private void auditFinalReport(WorkstreamPaths paths, Map<String, Object> artifacts, List<String> gaps) {
        if (!artifactAvailable(artifacts, "finalReport")) {
            gaps.add("final report is missing");
            return;
        }
        var report = readText(paths.workstreamDir().resolve("reports").resolve("latest-final-report.md"));
        for (var heading : List.of("## User Request", "## Verification Evidence", "## Acceptance Summary", "## Git Evidence")) {
            if (!report.contains(heading)) {
                gaps.add("final report missing " + heading);
            }
        }
    }

    private void auditHandoff(WorkstreamPaths paths, Map<String, Object> artifacts, List<String> gaps) {
        if (!artifactAvailable(artifacts, "handoffJson")) {
            gaps.add("handoff.json is missing");
            return;
        }
        var handoff = readJson(paths.workstreamDir().resolve("artifacts").resolve("next-stage").resolve("handoff.json"));
        if (text(handoff.get("workstreamId")).isBlank()) {
            gaps.add("handoff.json missing workstreamId");
        }
        if (textList(handoff.get("completedWork")).isEmpty() && textList(handoff.get("changedFiles")).isEmpty()) {
            gaps.add("handoff.json has no completedWork or changedFiles");
        }
        if (text(handoff.get("recommendedNextStage")).isBlank()
                && text(handoff.get("suggestedNextPrompt")).isBlank()) {
            gaps.add("handoff.json has no recommendedNextStage or suggestedNextPrompt");
        }
    }

    private void auditNextStagePrompt(WorkstreamPaths paths, Map<String, Object> artifacts, List<String> gaps) {
        if (!artifactAvailable(artifacts, "nextStagePrompt")) {
            gaps.add("next-stage prompt is missing");
            return;
        }
        var prompt = readText(paths.workstreamDir().resolve("artifacts").resolve("next-stage").resolve("next-stage-prompt.md"));
        if (!prompt.contains("## Prompt")) {
            gaps.add("next-stage prompt missing ## Prompt section");
        }
        if (!prompt.contains("## Handoff Context")) {
            gaps.add("next-stage prompt missing ## Handoff Context section");
        }
    }

    private static boolean artifactAvailable(Map<String, Object> artifacts, String key) {
        return Boolean.TRUE.equals(mapValue(artifacts.get(key)).get("available"));
    }

    private static String readText(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            return "";
        }
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

    private static List<Map<String, Object>> sortedTimelineNodes(List<Map<String, Object>> nodes) {
        return nodes.stream()
                .sorted(WorkstreamQueryService::compareTimelineNodes)
                .toList();
    }

    private static int compareTimelineNodes(Map<String, Object> left, Map<String, Object> right) {
        var byTime = timelineInstant(left).compareTo(timelineInstant(right));
        if (byTime != 0) {
            return byTime;
        }
        return text(left.get("nodeInstanceId")).compareTo(text(right.get("nodeInstanceId")));
    }

    private static Instant timelineInstant(Map<String, Object> node) {
        var startedAt = parseInstant(text(node.get("startedAt")));
        if (startedAt != null) {
            return startedAt;
        }
        var finishedAt = parseInstant(text(node.get("finishedAt")));
        if (finishedAt != null) {
            return finishedAt;
        }
        return Instant.MAX;
    }

    private static Instant parseInstant(String value) {
        if (value.isBlank() || "null".equals(value)) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static List<String> textList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private static Map<String, Object> map(Object value) {
        if (value instanceof Map<?, ?> source) {
            var result = new LinkedHashMap<String, Object>();
            for (var entry : source.entrySet()) {
                if (entry.getKey() instanceof String key) {
                    result.put(key, entry.getValue());
                }
            }
            return result;
        }
        return Map.of();
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

    private static String safeRelative(Path root, Path path) {
        var normalizedRoot = root.toAbsolutePath().normalize();
        var normalizedPath = path.toAbsolutePath().normalize();
        if (normalizedPath.startsWith(normalizedRoot)) {
            return normalizedRoot.relativize(normalizedPath).toString();
        }
        return normalizedPath.toString();
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
