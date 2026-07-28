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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class HumanReviewGateHandler implements GateHandler {
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
        packet.put("inputArtifacts", inputArtifactSummaries(context));
        packet.put("reviewPacketPath", packetRef);
        packet.put("taskContract", taskContractSummary(context));
        packet.put("contractResolution", contractResolutionSummary(context));
        packet.put("latestAgentDecision", latestAgentDecision(context).toReviewPacketMap());
        packet.put("reviewSubject", latestReviewSubject(context));
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
        appendReviewSubject(builder, context);
        appendGitEvidence(builder, context);
        appendDelivery(builder, context);

        appendInputArtifacts(builder, context);
        return builder.toString();
    }

    private void appendInputArtifacts(StringBuilder builder, GateContext context) {
        builder.append("## Input Artifacts\n\n");
        var artifacts = inputArtifactSummaries(context);
        if (artifacts.isEmpty()) {
            builder.append("No input artifacts were attached to this gate.\n");
            return;
        }
        builder.append("Raw agent logs are not embedded here by default. Use the paths below when deeper debugging is needed.\n\n");
        for (var artifact : artifacts) {
            builder.append("### ").append(text(artifact.get("ref"))).append("\n\n");
            appendField(builder, "Status", text(artifact.get("status")));
            appendField(builder, "Kind", text(artifact.get("kind")));
            appendField(builder, "Sidecar", text(artifact.get("sidecar")));
            appendField(builder, "Decision", text(artifact.get("decision")));
            appendField(builder, "Summary", text(artifact.get("summary")));
            appendList(builder, "Must Fix", artifact.get("mustFix"));
            appendList(builder, "Open Risks", artifact.get("openRisks"));
            appendField(builder, "Raw Artifact", text(artifact.get("ref")));
            builder.append("\n");
        }
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
        appendField(builder, "Stage Outcome", decision.stageOutcome());
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
        appendField(builder, "Stage Outcome", decision.stageOutcome());
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
        summary.put("includedScope", document.scope() == null
                ? List.of()
                : document.scope().included().stream()
                        .map(entry -> Map.of(
                                "path", entry.path(),
                                "operation", entry.operation().name()))
                        .toList());
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
        var fromRefs = AgentEvidenceIndex.latestFromRefs(
                        context.workspaceRoot(), context.workstreamId(), context.instance().inputRefs(), objectMapper)
                .map(entry -> agentDecisionFromSidecar(context, entry));
        if (fromRefs.isPresent() && !fromRefs.get().isEmpty()) {
            return fromRefs.get();
        }
        return AgentDecisionSummary.empty();
    }

    private Map<String, Object> latestReviewSubject(GateContext context) {
        return AgentEvidenceIndex.latestFromRefs(
                        context.workspaceRoot(), context.workstreamId(), context.instance().inputRefs(), objectMapper)
                .map(entry -> entry.document().reviewSubject())
                .filter(java.util.Objects::nonNull)
                .map(TaskContractProposalReference::toMap)
                .orElse(Map.of());
    }

    private void appendReviewSubject(StringBuilder builder, GateContext context) {
        var subject = latestReviewSubject(context);
        if (subject.isEmpty()) {
            return;
        }
        builder.append("## Review Subject\n\n");
        appendField(builder, "Proposal ID", text(subject.get("proposal_id")));
        appendField(builder, "Artifact Ref", text(subject.get("artifact_ref")));
        appendField(builder, "SHA-256", text(subject.get("sha256")));
        builder.append("\n");
    }

    private AgentDecisionSummary agentDecisionFromSidecar(GateContext context, AgentEvidenceIndex.Entry entry) {
        if (entry == null || !entry.document().hasDecision()) {
            return AgentDecisionSummary.empty();
        }
        return AgentDecisionSummary.from(entry.document(), displayRef(context.workspaceRoot(), entry.path()));
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

    private List<Map<String, Object>> inputArtifactSummaries(GateContext context) {
        var result = new ArrayList<Map<String, Object>>();
        for (var inputRef : context.instance().inputRefs()) {
            result.add(inputArtifactSummary(context, inputRef));
        }
        return List.copyOf(result);
    }

    private Map<String, Object> inputArtifactSummary(GateContext context, String inputRef) {
        var summary = new LinkedHashMap<String, Object>();
        summary.put("ref", inputRef);
        var path = resolveInsideWorkspace(context.workspaceRoot(), inputRef);
        if (path == null) {
            summary.put("status", "outside_workspace");
            summary.put("kind", "unavailable");
            return summary;
        }
        if (!Files.isRegularFile(path)) {
            summary.put("status", "not_found");
            summary.put("kind", "unavailable");
            return summary;
        }
        summary.put("status", "available");
        summary.put("kind", artifactKind(path));

        var sidecar = sidecarPath(path);
        if (Files.isRegularFile(sidecar)) {
            summary.put("sidecar", displayRef(context.workspaceRoot(), sidecar));
            appendSidecarSummary(context, summary, inputRef);
        }
        return summary;
    }

    private String artifactKind(Path path) {
        var name = path.getFileName().toString();
        if (name.endsWith(".contract.json")) {
            return "contract_sidecar";
        }
        if (name.endsWith(".stderr.log")) {
            return "stderr_log";
        }
        if (name.endsWith(".md")) {
            return "markdown_output";
        }
        if (name.endsWith(".json")) {
            return "json";
        }
        return "file";
    }

    private Path sidecarPath(Path artifactPath) {
        var name = artifactPath.getFileName().toString();
        if (name.endsWith(".contract.json")) {
            return artifactPath;
        }
        if (name.endsWith(".md")) {
            return artifactPath.resolveSibling(name.substring(0, name.length() - ".md".length()) + ".contract.json");
        }
        return AgentOutputContractSidecar.pathFor(artifactPath);
    }

    private void appendSidecarSummary(
            GateContext context,
            Map<String, Object> summary,
            String inputRef
    ) {
        var document = AgentEvidenceIndex.validatedDocument(
                        context.workspaceRoot(), context.workstreamId(), inputRef, objectMapper)
                .orElseThrow(() -> new IllegalStateException(
                        "routed human review sidecar is unavailable: " + inputRef));
        summary.put("decision", document.decision());
        summary.put("summary", document.summary());
        summary.put("mustFix", document.mustFix());
        summary.put("openRisks", document.openRisks());
    }

    private Map<String, Object> operatorGuidance(GateContext context, List<String> allowedActions) {
        var decision = latestAgentDecision(context);
        var decisionText = decision.decision();
        var blocking = decision.blocking();
        var mustFix = decision.mustFix();
        var missingEvidence = decision.missingEvidence();
        var openRisks = decision.openRisks();
        var gatePurpose = gatePurpose(context);
        var terminalGate = terminalGate(context);

        var guidance = new LinkedHashMap<String, Object>();
        if (allowedActions.contains("complete") && terminalGate) {
            guidance.put("recommendedAction", "complete");
            guidance.put("reason", "This is a next-stage gate. Complete the current workstream when the handoff and next-stage prompt are acceptable.");
        } else if (blocking && allowedActions.contains("revise")) {
            guidance.put("recommendedAction", "revise");
            guidance.put("reason", "The latest structured review marks the issue as blocking.");
        } else if (!mustFix.isEmpty() && allowedActions.contains("revise")) {
            guidance.put("recommendedAction", "revise");
            guidance.put("reason", "The latest agent decision still contains Must Fix items.");
        } else if ("acceptance".equals(gatePurpose) && !missingEvidence.isEmpty() && allowedActions.contains("revise")) {
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
        guidance.put("suggestedChecks", suggestedChecks(gatePurpose, mustFix, missingEvidence, openRisks));
        return guidance;
    }

    private List<String> suggestedChecks(
            String gatePurpose,
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
        if ("acceptance".equals(gatePurpose)) {
            checks.add("Compare Codex acceptance, developer self-test evidence, Git evidence, and changed files before approval.");
        } else if ("design".equals(gatePurpose) || "plan".equals(gatePurpose)) {
            checks.add("Check whether the design or plan stays inside scope and avoids unnecessary implementation detail.");
        } else if ("next_stage".equals(gatePurpose)) {
            checks.add("Check the handoff and next-stage prompt before completing the current workstream.");
        }
        return List.copyOf(checks);
    }

    private static String gatePurpose(GateContext context) {
        return HumanGateSemantics.purpose(context.definition().config(), context.instance().nodeDefinitionId());
    }

    private static boolean terminalGate(GateContext context) {
        return HumanGateSemantics.terminal(context.definition().config(), context.instance().nodeDefinitionId());
    }

    private boolean isPositiveDecision(String decision) {
        return switch (StageOutcome.fromDecision(decision)) {
            case READY, ACCEPTED, ACCEPTED_WITH_NOTES -> true;
            default -> false;
        };
    }

    private boolean isNegativeDecision(String decision) {
        return switch (StageOutcome.fromDecision(decision)) {
            case NEEDS_REVISION, BLOCKED, REJECTED, INSUFFICIENT_EVIDENCE -> true;
            default -> false;
        };
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
