package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.dual.core.contract.TaskContract;
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
import java.util.Optional;
import java.util.Set;

public final class HumanReviewGateHandler implements GateHandler {
    private static final int MAX_INPUT_SNIPPET_CHARS = 4000;
    private static final Set<String> POSITIVE_DECISIONS = Set.of(
            "ACCEPT", "ACCEPT_WITH_NOTES", "PASS",
            "READY_FOR_PRODUCT_REVIEW", "READY_FOR_ARCH_REVIEW",
            "READY_FOR_PLAN_REVIEW", "READY_TO_IMPLEMENT",
            "IMPLEMENTED", "SELF_TEST_COMPLETE", "HANDOFF_READY"
    );
    private static final Set<String> NEGATIVE_DECISIONS = Set.of(
            "REJECT", "NEEDS_REVISION", "BLOCKED",
            "SELF_TEST_INCOMPLETE"
    );
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
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
        packet.put("contractResolution", contractResolutionSummary(context));
        packet.put("latestAgentDecision", latestAgentDecision(context).toReviewPacketMap());
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
        appendContractResolution(builder, context);
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
        appendField(builder, "Lifecycle Status", decision.lifecycleStatus());
        appendField(builder, "Latest Decision", decision.decision());
        appendField(builder, "Latest Summary", decision.summary());
        appendField(builder, "Blocking", Boolean.toString(decision.blocking()));
        appendList(builder, "Must Fix", decision.mustFix());
        appendList(builder, "Missing Evidence", decision.missingEvidence());
        appendList(builder, "Open Risks", decision.openRisks());
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

    private void appendContractResolution(StringBuilder builder, GateContext context) {
        var resolution = contractResolutionSummary(context);
        if (resolution.isEmpty()) {
            return;
        }
        builder.append("## Contract Resolution\n\n");
        appendField(builder, "Decision", text(resolution.get("decision")));
        appendField(builder, "Approved By", text(resolution.get("approvedBy")));
        appendField(builder, "Gate Ref", text(resolution.get("gateRef")));
        appendList(builder, "Resolved Questions", resolution.get("resolvedQuestions"));
        appendList(builder, "Assumptions", resolution.get("assumptions"));
        appendList(builder, "Accepted Risks", resolution.get("acceptedRisks"));
        builder.append("\n");
    }

    private void appendLatestAgentDecision(StringBuilder builder, GateContext context) {
        var decision = latestAgentDecision(context);
        if (decision.isEmpty()) {
            return;
        }
        builder.append("## Latest Agent Decision\n\n");
        appendField(builder, "Node", decision.nodeId());
        appendField(builder, "Lifecycle Status", decision.lifecycleStatus());
        appendField(builder, "Decision", decision.decision());
        appendField(builder, "Summary", decision.summary());
        appendField(builder, "Blocking", Boolean.toString(decision.blocking()));
        appendList(builder, "Must Fix", decision.mustFix());
        appendList(builder, "Missing Evidence", decision.missingEvidence());
        appendList(builder, "Open Risks", decision.openRisks());
        appendList(builder, "Changed Files", decision.changedFiles());
        appendField(builder, "Verification", decision.verificationSummary());
        appendField(builder, "Evidence State", decision.evidenceState().toMap().toString());
        appendField(builder, "Contract Sidecar", decision.sidecarPath());
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
        var contract = readTaskContract(path);
        if (contract.isEmpty()) {
            return Map.of();
        }
        var summary = new LinkedHashMap<String, Object>();
        var document = contract.get();
        summary.put("objective", document.objective());
        summary.put("status", text(document.status()));
        summary.put("includedScope", document.scope() == null ? List.of() : document.scope().included());
        summary.put("excludedScope", document.scope() == null ? List.of() : document.scope().excluded());
        summary.put("acceptanceCriteria", document.acceptanceCriteria());
        summary.put("testRequirements", document.testRequirements());
        summary.put("constraints", document.constraints());
        summary.put("path", displayRef(context.workspaceRoot(), path));
        return summary;
    }

    private Optional<TaskContract> readTaskContract(Path path) {
        if (!Files.isRegularFile(path)) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(path.toFile(), TaskContract.class));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> contractResolutionSummary(GateContext context) {
        var path = workstreamDir(context).resolve("artifacts/contract-resolution.json");
        if (!Files.isRegularFile(path)) {
            return Map.of();
        }
        try {
            var tree = objectMapper.readValue(path.toFile(), Map.class);
            var summary = new LinkedHashMap<String, Object>();
            summary.put("decision", text(tree.get("decision")));
            summary.put("approvedBy", text(tree.get("approved_by")));
            summary.put("gateRef", text(tree.get("gate_ref")));
            summary.put("resolvedQuestions", resolvedQuestionTexts(tree.get("resolved_questions")));
            summary.put("assumptions", stringList(tree.get("assumptions")));
            summary.put("acceptedRisks", stringList(tree.get("accepted_risks")));
            summary.put("path", displayRef(context.workspaceRoot(), path));
            return summary;
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private List<String> resolvedQuestionTexts(Object value) {
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

    private AgentDecisionSummary latestAgentDecision(GateContext context) {
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

    private AgentDecisionSummary latestRunAgentDecision(GateContext context) {
        var runsDir = workstreamDir(context).resolve("runs");
        if (!Files.isDirectory(runsDir)) {
            return AgentDecisionSummary.empty();
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
            return AgentDecisionSummary.empty();
        }
        return AgentDecisionSummary.empty();
    }

    private AgentDecisionSummary agentDecisionFromSidecar(GateContext context, Path sidecar) {
        if (sidecar == null || !Files.isRegularFile(sidecar)) {
            return AgentDecisionSummary.empty();
        }
        var document = AgentOutputContractDocument.read(sidecar, objectMapper);
        if (document.isEmpty() || !document.get().hasDecision()) {
            return AgentDecisionSummary.empty();
        }
        return AgentDecisionSummary.from(document.get(), displayRef(context.workspaceRoot(), sidecar));
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
        var decisionText = decision.decision();
        var blocking = decision.blocking();
        var mustFix = decision.mustFix();
        var missingEvidence = decision.missingEvidence();
        var openRisks = decision.openRisks();
        var gateId = context.instance().nodeDefinitionId();

        var guidance = new LinkedHashMap<String, Object>();
        if (allowedActions.contains("complete") && gateId.contains("next_stage")) {
            guidance.put("recommendedAction", "complete");
            guidance.put("reason", "This is a next-stage gate. Complete the current workstream when the handoff and next-stage prompt are acceptable.");
        } else if (blocking && allowedActions.contains("revise")) {
            guidance.put("recommendedAction", "revise");
            guidance.put("reason", "The latest structured review marks the issue as blocking.");
        } else if (!mustFix.isEmpty() && allowedActions.contains("revise")) {
            guidance.put("recommendedAction", "revise");
            guidance.put("reason", "The latest agent decision still contains Must Fix items.");
        } else if (gateId.contains("acceptance") && !missingEvidence.isEmpty() && allowedActions.contains("revise")) {
            guidance.put("recommendedAction", "revise");
            guidance.put("reason", "The latest acceptance review reports missing evidence.");
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
        guidance.put("suggestedChecks", suggestedChecks(gateId, mustFix, missingEvidence, openRisks));
        return guidance;
    }

    private List<String> suggestedChecks(
            String gateId,
            List<String> mustFix,
            List<String> missingEvidence,
            List<String> openRisks
    ) {
        var checks = new ArrayList<String>();
        checks.add("Confirm the TaskContract objective, scope, non-goals, and acceptance criteria still match the task.");
        if (!mustFix.isEmpty()) {
            checks.add("Do not approve until every Must Fix item is addressed or explicitly accepted by the human reviewer.");
        }
        if (!missingEvidence.isEmpty()) {
            checks.add("Check whether missing evidence is acceptable for this gate or requires another agent pass.");
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

    private boolean isPositiveDecision(String decision) {
        return POSITIVE_DECISIONS.contains(decision);
    }

    private boolean isNegativeDecision(String decision) {
        return NEGATIVE_DECISIONS.contains(decision);
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
