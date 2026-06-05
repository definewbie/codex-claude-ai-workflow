package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.dual.core.workflow.NodeResult;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class HumanReviewGateHandler implements GateHandler {
    private static final int MAX_INPUT_SNIPPET_CHARS = 4000;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    @Override
    public String type() {
        return "human.review";
    }

    @Override
    public NodeResult openOrInspect(GateContext context) throws Exception {
        var allowedActions = allowedActions(context);
        var packetRef = reviewPacketRef(context, ".md");
        var packetJsonRef = reviewPacketRef(context, ".json");
        writeReviewPacket(context, allowedActions, packetRef, packetJsonRef);

        return NodeResult.waiting("open", Map.of(
                "allowed_actions", allowedActions,
                "review_packet", packetRef,
                "review_packet_json", packetJsonRef
        ));
    }

    @SuppressWarnings("unchecked")
    private List<String> allowedActions(GateContext context) {
        var configured = context.definition().config().getOrDefault("allowed_actions", List.of("approve", "revise"));
        if (configured instanceof List<?> values) {
            return values.stream().map(String::valueOf).toList();
        }
        return List.of("approve", "revise");
    }

    private String reviewPacketRef(GateContext context, String extension) {
        return ".agent/workstreams/" + context.workstreamId()
                + "/reviews/" + context.instance().nodeInstanceId() + extension;
    }

    private void writeReviewPacket(
            GateContext context,
            List<String> allowedActions,
            String packetRef,
            String packetJsonRef
    ) throws Exception {
        var packetPath = context.workspaceRoot().resolve(packetRef);
        var packetJsonPath = context.workspaceRoot().resolve(packetJsonRef);
        Files.createDirectories(packetPath.getParent());

        var packet = new LinkedHashMap<String, Object>();
        packet.put("schemaVersion", 1);
        packet.put("workstreamId", context.workstreamId());
        packet.put("gateNodeInstanceId", context.instance().nodeInstanceId());
        packet.put("gateNodeDefinitionId", context.instance().nodeDefinitionId());
        packet.put("stageId", context.instance().stageId());
        packet.put("iterationId", context.instance().iterationId());
        packet.put("allowedActions", allowedActions);
        packet.put("inputRefs", context.instance().inputRefs());
        packet.put("reviewPacketPath", packetRef);
        packet.put("taskContract", taskContractSummary(context));
        packet.put("latestAgentDecision", latestAgentDecision(context));
        packet.put("gitEvidence", gitEvidenceSummary(context));
        packet.put("delivery", deliverySummary(context));
        packet.put("operatorGuidance", operatorGuidance(context, allowedActions));
        packet.put("createdAt", Instant.now().toString());

        objectMapper.writeValue(packetJsonPath.toFile(), packet);
        Files.writeString(packetPath, renderMarkdown(context, allowedActions), StandardCharsets.UTF_8);
    }

    private String renderMarkdown(GateContext context, List<String> allowedActions) throws Exception {
        var builder = new StringBuilder();
        builder.append("# Human Review Packet\n\n");
        builder.append("- Workstream: ").append(context.workstreamId()).append("\n");
        builder.append("- Gate: ").append(context.instance().nodeDefinitionId()).append("\n");
        builder.append("- Gate Instance: ").append(context.instance().nodeInstanceId()).append("\n");
        builder.append("- Stage: ").append(context.instance().stageId()).append("\n");
        builder.append("- Iteration: ").append(context.instance().iterationId()).append("\n");
        builder.append("- Allowed Actions: ").append(String.join(", ", allowedActions)).append("\n\n");

        appendOperatorGuidance(builder, context, allowedActions);

        builder.append("## Review Focus\n\n");
        builder.append("- Read the referenced artifacts before approving.\n");
        builder.append("- Use `approve` only when the current gate is good enough to continue.\n");
        builder.append("- Use `revise` when the agent should address concrete feedback first.\n\n");

        appendReviewSummary(builder, context);
        appendTaskContract(builder, context);
        appendLatestAgentDecision(builder, context);
        appendGitEvidence(builder, context);
        appendDelivery(builder, context);

        builder.append("## Input Artifacts\n\n");
        if (context.instance().inputRefs().isEmpty()) {
            builder.append("No input artifacts were attached to this gate.\n");
        } else {
            for (var inputRef : context.instance().inputRefs()) {
                builder.append("### ").append(inputRef).append("\n\n");
                builder.append("```markdown\n");
                builder.append(readSnippet(context.workspaceRoot(), inputRef));
                builder.append("\n```\n\n");
            }
        }
        return builder.toString();
    }

    private void appendOperatorGuidance(StringBuilder builder, GateContext context, List<String> allowedActions) {
        var guidance = operatorGuidance(context, allowedActions);
        if (guidance.isEmpty()) {
            return;
        }
        builder.append("## Operator Guidance\n\n");
        appendField(builder, "Recommended Action", text(guidance.get("recommendedAction")));
        appendField(builder, "Reason", text(guidance.get("reason")));
        appendList(builder, "Suggested Checks", guidance.get("suggestedChecks"));
        builder.append("\n");
    }

    private void appendReviewSummary(StringBuilder builder, GateContext context) {
        builder.append("## Review Summary\n\n");
        var contract = taskContractSummary(context);
        var decision = latestAgentDecision(context);
        appendField(builder, "Objective", text(contract.get("objective")));
        appendField(builder, "Latest Decision", text(decision.get("decision")));
        appendField(builder, "Latest Summary", text(decision.get("summary")));
        appendList(builder, "Must Fix", decision.get("mustFix"));
        appendList(builder, "Open Risks", decision.get("openRisks"));
        builder.append("\n");
    }

    private void appendTaskContract(StringBuilder builder, GateContext context) {
        var contract = taskContractSummary(context);
        if (contract.isEmpty()) {
            return;
        }
        builder.append("## Task Contract\n\n");
        appendField(builder, "Objective", text(contract.get("objective")));
        appendField(builder, "Status", text(contract.get("status")));
        appendList(builder, "Included Scope", contract.get("includedScope"));
        appendList(builder, "Excluded Scope", contract.get("excludedScope"));
        appendList(builder, "Acceptance Criteria", contract.get("acceptanceCriteria"));
        appendList(builder, "Test Requirements", contract.get("testRequirements"));
        appendList(builder, "Constraints", contract.get("constraints"));
        builder.append("\n");
    }

    private void appendLatestAgentDecision(StringBuilder builder, GateContext context) {
        var decision = latestAgentDecision(context);
        if (decision.isEmpty()) {
            return;
        }
        builder.append("## Latest Agent Decision\n\n");
        appendField(builder, "Node", text(decision.get("nodeId")));
        appendField(builder, "Decision", text(decision.get("decision")));
        appendField(builder, "Summary", text(decision.get("summary")));
        appendList(builder, "Must Fix", decision.get("mustFix"));
        appendList(builder, "Open Risks", decision.get("openRisks"));
        appendList(builder, "Changed Files", decision.get("changedFiles"));
        appendField(builder, "Verification", text(decision.get("verificationSummary")));
        appendField(builder, "Contract Sidecar", text(decision.get("sidecarPath")));
        builder.append("\n");
    }

    private void appendGitEvidence(StringBuilder builder, GateContext context) {
        var evidence = gitEvidenceSummary(context);
        if (evidence.isEmpty()) {
            return;
        }
        builder.append("## Git Evidence\n\n");
        appendList(builder, "Changed Files", evidence.get("changedFiles"));
        appendPreformatted(builder, "Diff Stat", text(evidence.get("diffStat")));
        appendPreformatted(builder, "Status", text(evidence.get("statusShort")));
        builder.append("\n");
    }

    private void appendDelivery(StringBuilder builder, GateContext context) {
        var delivery = deliverySummary(context);
        if (delivery.isEmpty()) {
            return;
        }
        builder.append("## Delivery Artifacts\n\n");
        appendField(builder, "PR Body", text(delivery.get("prBodyPath")));
        appendField(builder, "Commit Message", text(delivery.get("commitMessagePath")));
        builder.append("\n");
    }

    private Map<String, Object> taskContractSummary(GateContext context) {
        var path = workstreamDir(context).resolve("artifacts/task-contract.json");
        var json = readJson(path);
        if (json.isEmpty()) {
            return Map.of();
        }
        var summary = new LinkedHashMap<String, Object>();
        summary.put("objective", text(json.get("objective")));
        summary.put("status", text(json.get("status")));
        var scope = objectMap(json.get("scope"));
        summary.put("includedScope", stringList(scope.get("included")));
        summary.put("excludedScope", stringList(scope.get("excluded")));
        summary.put("acceptanceCriteria", stringList(json.get("acceptance_criteria")));
        summary.put("testRequirements", stringList(json.get("test_requirements")));
        summary.put("constraints", stringList(json.get("constraints")));
        summary.put("path", displayRef(context.workspaceRoot(), path));
        return summary;
    }

    private Map<String, Object> latestAgentDecision(GateContext context) {
        for (int i = context.instance().inputRefs().size() - 1; i >= 0; i--) {
            var ref = context.instance().inputRefs().get(i);
            var sidecar = resolveInsideWorkspace(context.workspaceRoot(), ref.replaceFirst("\\.md$", ".contract.json"));
            var summary = agentDecisionFromSidecar(context, sidecar);
            if (!summary.isEmpty()) {
                return summary;
            }
        }
        return latestRunAgentDecision(context);
    }

    private Map<String, Object> latestRunAgentDecision(GateContext context) {
        var runsDir = workstreamDir(context).resolve("runs");
        if (!Files.isDirectory(runsDir)) {
            return Map.of();
        }
        try (var paths = Files.walk(runsDir)) {
            var sidecars = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".contract.json"))
                    .sorted(Comparator.comparing(path -> path.toAbsolutePath().normalize().toString()))
                    .toList();
            for (int i = sidecars.size() - 1; i >= 0; i--) {
                var summary = agentDecisionFromSidecar(context, sidecars.get(i));
                if (!summary.isEmpty()) {
                    return summary;
                }
            }
        } catch (Exception ignored) {
            return Map.of();
        }
        return Map.of();
    }

    private Map<String, Object> agentDecisionFromSidecar(GateContext context, Path sidecar) {
        if (sidecar == null || !Files.isRegularFile(sidecar)) {
            return Map.of();
        }
        var json = readJson(sidecar);
        if (json.isEmpty() || text(json.get("decision")).isBlank()) {
            return Map.of();
        }
        var summary = new LinkedHashMap<String, Object>();
        summary.put("nodeId", text(json.get("node_id")));
        summary.put("decision", text(json.get("decision")));
        summary.put("summary", text(json.get("summary")));
        summary.put("mustFix", meaningfulList(json.get("must_fix")));
        summary.put("openRisks", meaningfulList(json.get("open_risks")));
        summary.put("changedFiles", stringList(json.get("changed_files")));
        summary.put("verificationSummary", text(json.get("verification_summary")));
        summary.put("sidecarPath", displayRef(context.workspaceRoot(), sidecar));
        return summary;
    }

    private Map<String, Object> gitEvidenceSummary(GateContext context) {
        var evidenceDir = workstreamDir(context).resolve("artifacts/git-evidence");
        var changedFiles = readLines(evidenceDir.resolve("changed-files.txt"));
        var diffStat = readText(evidenceDir.resolve("diff.stat"));
        var status = readText(evidenceDir.resolve("git-status-short.txt"));
        if (changedFiles.isEmpty() && diffStat.isBlank() && status.isBlank()) {
            return Map.of();
        }
        var summary = new LinkedHashMap<String, Object>();
        summary.put("changedFiles", changedFiles);
        summary.put("diffStat", diffStat);
        summary.put("statusShort", status);
        return summary;
    }

    private Map<String, Object> deliverySummary(GateContext context) {
        var deliveryDir = workstreamDir(context).resolve("delivery");
        var summary = new LinkedHashMap<String, Object>();
        var prBody = deliveryDir.resolve("pr-body.md");
        var commitMessage = deliveryDir.resolve("commit-message.txt");
        if (Files.isRegularFile(prBody)) {
            summary.put("prBodyPath", displayRef(context.workspaceRoot(), prBody));
        }
        if (Files.isRegularFile(commitMessage)) {
            summary.put("commitMessagePath", displayRef(context.workspaceRoot(), commitMessage));
        }
        return summary;
    }

    private Map<String, Object> operatorGuidance(GateContext context, List<String> allowedActions) {
        var decision = latestAgentDecision(context);
        var decisionText = text(decision.get("decision"));
        var mustFix = stringList(decision.get("mustFix"));
        var openRisks = stringList(decision.get("openRisks"));
        var gateId = context.instance().nodeDefinitionId();

        var guidance = new LinkedHashMap<String, Object>();
        if (allowedActions.contains("complete") && gateId.contains("next_stage")) {
            guidance.put("recommendedAction", "complete");
            guidance.put("reason", "This is a next-stage gate. Complete the current workstream when the handoff and next-stage prompt are acceptable.");
        } else if (!mustFix.isEmpty() && allowedActions.contains("revise")) {
            guidance.put("recommendedAction", "revise");
            guidance.put("reason", "The latest agent decision still contains Must Fix items.");
        } else if (isNegativeDecision(decisionText) && allowedActions.contains("revise")) {
            guidance.put("recommendedAction", "revise");
            guidance.put("reason", "The latest agent decision indicates the work is not ready to continue.");
        } else if (isPositiveDecision(decisionText) && allowedActions.contains("approve")) {
            guidance.put("recommendedAction", "approve");
            guidance.put("reason", "The latest agent decision is positive. Approve only after checking the packet evidence.");
        } else {
            guidance.put("recommendedAction", allowedActions.isEmpty() ? "inspect" : allowedActions.get(0));
            guidance.put("reason", "No decisive recommendation is available. Read the packet and choose an allowed action.");
        }
        guidance.put("suggestedChecks", suggestedChecks(gateId, mustFix, openRisks));
        return guidance;
    }

    private List<String> suggestedChecks(String gateId, List<String> mustFix, List<String> openRisks) {
        var checks = new ArrayList<String>();
        checks.add("Confirm the TaskContract objective, scope, non-goals, and acceptance criteria still match the task.");
        if (!mustFix.isEmpty()) {
            checks.add("Do not approve until every Must Fix item is addressed or explicitly accepted by the human reviewer.");
        }
        if (!openRisks.isEmpty()) {
            checks.add("Review open risks and decide whether they are acceptable for this stage.");
        }
        if (gateId.contains("acceptance")) {
            checks.add("Compare Codex acceptance, developer self-test evidence, Git evidence, and changed files before approval.");
        } else if (gateId.contains("design") || gateId.contains("plan")) {
            checks.add("Check whether the design or plan stays inside scope and avoids unnecessary implementation detail.");
        } else if (gateId.contains("next_stage")) {
            checks.add("Check the handoff and next-stage prompt before completing the current workstream.");
        }
        return List.copyOf(checks);
    }

    private List<String> meaningfulList(Object value) {
        return stringList(value).stream()
                .filter(item -> {
                    var normalized = item.trim().toLowerCase();
                    return !normalized.isBlank()
                            && !normalized.equals("none")
                            && !normalized.equals("none.")
                            && !normalized.equals("none blocking")
                            && !normalized.equals("none blocking.")
                            && !normalized.equals("no blocking issues")
                            && !normalized.equals("no blocking issues.")
                            && !normalized.equals("n/a")
                            && !normalized.equals("na")
                            && !normalized.equals("无")
                            && !normalized.equals("无。");
                })
                .toList();
    }

    private boolean isPositiveDecision(String decision) {
        return decision.contains("ACCEPT")
                || decision.contains("PASS")
                || decision.contains("READY")
                || decision.contains("APPROVE");
    }

    private boolean isNegativeDecision(String decision) {
        return decision.contains("REJECT")
                || decision.contains("NEEDS_REVISION")
                || decision.contains("BLOCKED")
                || decision.contains("FAIL");
    }

    private String readSnippet(Path workspaceRoot, String ref) throws Exception {
        var path = resolveInsideWorkspace(workspaceRoot, ref);
        if (path == null) {
            return "(artifact outside workspace)";
        }
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            return "(artifact not found)";
        }
        var content = Files.readString(path, StandardCharsets.UTF_8);
        if (content.length() <= MAX_INPUT_SNIPPET_CHARS) {
            return content;
        }
        return content.substring(0, MAX_INPUT_SNIPPET_CHARS)
                + "\n\n... truncated for review packet ...";
    }

    private Path workstreamDir(GateContext context) {
        return context.workspaceRoot()
                .resolve(".agent/workstreams")
                .resolve(context.workstreamId())
                .normalize();
    }

    private Path resolveInsideWorkspace(Path workspaceRoot, String ref) {
        var root = workspaceRoot.toAbsolutePath().normalize();
        var path = root.resolve(ref).normalize();
        if (!path.startsWith(root)) {
            return null;
        }
        return path;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readJson(Path path) {
        try {
            if (!Files.isRegularFile(path)) {
                return Map.of();
            }
            return objectMapper.readValue(path.toFile(), Map.class);
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private Map<String, Object> objectMap(Object value) {
        if (!(value instanceof Map<?, ?> source)) {
            return Map.of();
        }
        var copy = new LinkedHashMap<String, Object>();
        for (var entry : source.entrySet()) {
            if (entry.getKey() instanceof String key) {
                copy.put(key, entry.getValue());
            }
        }
        return copy;
    }

    private List<String> readLines(Path path) {
        var text = readText(path);
        if (text.isBlank()) {
            return List.of();
        }
        return text.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .toList();
    }

    private String readText(Path path) {
        try {
            if (!Files.isRegularFile(path)) {
                return "";
            }
            return Files.readString(path, StandardCharsets.UTF_8).trim();
        } catch (Exception ignored) {
            return "";
        }
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> items)) {
            return List.of();
        }
        var result = new ArrayList<String>();
        for (var item : items) {
            var text = text(item);
            if (!text.isBlank()) {
                result.add(text);
            }
        }
        return List.copyOf(result);
    }

    private void appendField(StringBuilder builder, String label, String value) {
        if (!value.isBlank()) {
            builder.append("- ").append(label).append(": ").append(value.replace("\n", " / ")).append("\n");
        }
    }

    private void appendList(StringBuilder builder, String label, Object value) {
        var items = stringList(value);
        if (items.isEmpty()) {
            return;
        }
        builder.append("- ").append(label).append(":\n");
        for (var item : items) {
            builder.append("  - ").append(item.replace("\n", " / ")).append("\n");
        }
    }

    private void appendPreformatted(StringBuilder builder, String label, String value) {
        if (value.isBlank()) {
            return;
        }
        builder.append("### ").append(label).append("\n\n");
        builder.append("```text\n").append(value).append("\n```\n\n");
    }

    private String displayRef(Path workspaceRoot, Path path) {
        var root = workspaceRoot.toAbsolutePath().normalize();
        var normalized = path.toAbsolutePath().normalize();
        if (normalized.startsWith(root)) {
            return root.relativize(normalized).toString();
        }
        return normalized.toString();
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
