package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.dual.core.workflow.NodeStatus;
import dev.dual.ledger.file.GateDocument;
import dev.dual.ledger.file.NodeInstanceDocument;
import dev.dual.ledger.file.WorkflowIterationDocument;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Resolves the exact typed baseline and obligations for one intake revision. */
public final class TaskContractRevisionContextResolver {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    public Result resolveForNode(
            Path workspaceRoot,
            String workstreamId,
            String nodeInstanceId
    ) {
        var workstreamDir = workstreamDir(workspaceRoot, workstreamId);
        try {
            var nodePath = workstreamDir.resolve("nodes").resolve(nodeInstanceId + ".json");
            if (!Files.isRegularFile(nodePath)) {
                return failure("current proposal node is missing", nodePath.toString());
            }
            var node = OBJECT_MAPPER.readValue(nodePath.toFile(), NodeInstanceDocument.class);
            if (!nodeInstanceId.equals(node.nodeInstanceId())
                    || !"intake.claude_brainstorm".equals(node.nodeDefinitionId())) {
                return failure("current proposal node identity is inconsistent", nodeInstanceId);
            }
            return resolveForIteration(workspaceRoot, workstreamId, node.iterationId());
        } catch (Exception failure) {
            return failure("revision context could not be resolved", message(failure));
        }
    }

    public Result resolveForIteration(
            Path workspaceRoot,
            String workstreamId,
            String iterationId
    ) {
        var root = workspaceRoot.toAbsolutePath().normalize();
        var workstreamDir = workstreamDir(root, workstreamId);
        try {
            var iterationPath = workstreamDir.resolve("iterations").resolve(iterationId + ".json");
            if (!Files.isRegularFile(iterationPath)) {
                return failure("current workflow iteration is missing", iterationPath.toString());
            }
            var iteration = OBJECT_MAPPER.readValue(iterationPath.toFile(), WorkflowIterationDocument.class);
            if (!iterationId.equals(iteration.iterationId())) {
                return failure("current workflow iteration identity is inconsistent",
                        iterationId, iteration.iterationId());
            }
            if ("workflow_started".equals(iteration.triggerAction())) {
                return new Result(new Context(iteration, false, null, null, List.of()), List.of());
            }
            if ("clarify".equals(iteration.triggerAction())) {
                return resolveContractClarification(
                        root, workstreamId, workstreamDir, iteration);
            }
            if (!"NEEDS_REVISION".equals(iteration.triggerAction())) {
                return failure("contract revision reached an unsupported iteration trigger",
                        "trigger_action=" + iteration.triggerAction());
            }
            if (iteration.triggerNodeInstanceId().isBlank() || iteration.parentIterationId().isBlank()) {
                return failure("revision iteration trigger lineage is incomplete", iteration.iterationId());
            }
            var triggerPath = workstreamDir.resolve("nodes")
                    .resolve(iteration.triggerNodeInstanceId() + ".json");
            if (!Files.isRegularFile(triggerPath)) {
                return failure("revision trigger decision node is missing", triggerPath.toString());
            }
            var trigger = OBJECT_MAPPER.readValue(triggerPath.toFile(), NodeInstanceDocument.class);
            if (!"intake.decision".equals(trigger.nodeDefinitionId())
                    || !iteration.parentIterationId().equals(trigger.iterationId())
                    || trigger.status() != NodeStatus.COMPLETED
                    || !"NEEDS_REVISION".equals(text(trigger.result().get("decision")))) {
                return failure("revision trigger is not the completed intake decision",
                        iteration.triggerNodeInstanceId());
            }
            var baselines = verifiedBaselines(
                    root, workstreamId, workstreamDir, trigger.inputRefs(), iteration.parentIterationId(),
                    Set.of("NEEDS_REVISION"), true);
            if (baselines.size() != 1) {
                return failure("revision trigger must reference exactly one verified rejected intake review",
                        "review_count=" + baselines.size());
            }
            var baseline = baselines.get(0);
            return new Result(new Context(
                    iteration,
                    true,
                    baseline.previousProposal(),
                    baseline.previousReference(),
                    baseline.review().contractObligations()), List.of());
        } catch (Exception failure) {
            return failure("revision iteration lineage could not be resolved", message(failure));
        }
    }

    private Result resolveContractClarification(
            Path root,
            String workstreamId,
            Path workstreamDir,
            WorkflowIterationDocument iteration
    ) throws IOException {
        if (iteration.triggerNodeInstanceId().isBlank() || iteration.parentIterationId().isBlank()) {
            return failure("contract clarification iteration trigger lineage is incomplete", iteration.iterationId());
        }
        var triggerPath = workstreamDir.resolve("nodes").resolve(iteration.triggerNodeInstanceId() + ".json");
        if (!Files.isRegularFile(triggerPath)) {
            return failure("contract clarification gate node is missing", triggerPath.toString());
        }
        var trigger = OBJECT_MAPPER.readValue(triggerPath.toFile(), NodeInstanceDocument.class);
        var note = text(trigger.result().get("note"));
        if (!"gate.contract_review".equals(trigger.nodeDefinitionId())
                || !iteration.parentIterationId().equals(trigger.iterationId())
                || trigger.status() != NodeStatus.COMPLETED
                || !"clarify".equals(text(trigger.result().get("human_action")))
                || note.isBlank()) {
            return failure("revision trigger is not a completed contract clarification gate",
                    iteration.triggerNodeInstanceId());
        }
        var resolutionRefs = trigger.inputRefs().stream()
                .filter(ref -> ref != null && ref.endsWith("/artifacts/contract-resolution.json"))
                .toList();
        if (resolutionRefs.size() != 1) {
            return failure("contract clarification must reference exactly one contract resolution",
                    "resolution_count=" + resolutionRefs.size());
        }
        var resolutionPath = root.resolve(resolutionRefs.getFirst()).normalize();
        if (!resolutionPath.startsWith(root) || !Files.isRegularFile(resolutionPath)) {
            return failure("contract clarification resolution artifact is missing", resolutionRefs.getFirst());
        }
        var resolution = OBJECT_MAPPER.readValue(resolutionPath.toFile(), ContractResolutionDocument.class);
        if (!"APPROVE".equals(resolution.decision())
                || !"human_gate".equals(resolution.approvedBy())
                || resolution.gateRef() == null || resolution.gateRef().isBlank()) {
            return failure("contract clarification resolution is not bound to an approved requirement gate",
                    resolutionRefs.getFirst());
        }
        var requirementGatePath = root.resolve(resolution.gateRef()).normalize();
        if (!requirementGatePath.startsWith(root) || !Files.isRegularFile(requirementGatePath)) {
            return failure("approved requirement gate referenced by contract resolution is missing",
                    resolution.gateRef());
        }
        var requirementGate = OBJECT_MAPPER.readValue(requirementGatePath.toFile(), GateDocument.class);
        var gateNodePath = workstreamDir.resolve("nodes").resolve(requirementGate.nodeInstanceId() + ".json");
        if (!Files.isRegularFile(gateNodePath)) {
            return failure("approved requirement gate node is missing", requirementGate.nodeInstanceId());
        }
        var gateNode = OBJECT_MAPPER.readValue(gateNodePath.toFile(), NodeInstanceDocument.class);
        var gateAction = text(requirementGate.result().get("human_action"));
        var subjectRefs = stringList(requirementGate.result().get("subject_input_refs"));
        if (!"resolved".equals(requirementGate.status())
                || !"approved".equals(gateAction)
                || !"gate.human_requirement_review".equals(gateNode.nodeDefinitionId())
                || !iteration.parentIterationId().equals(gateNode.iterationId())) {
            return failure("contract resolution requirement gate lineage is invalid", resolution.gateRef());
        }
        var baselines = verifiedBaselines(
                root, workstreamId, workstreamDir, subjectRefs, iteration.parentIterationId(),
                Set.of("READY_FOR_PRODUCT_REVIEW", "BLOCKED"), false);
        if (baselines.size() != 1) {
            return failure("contract clarification must resolve exactly one verified approved proposal",
                    "review_count=" + baselines.size());
        }
        var baseline = baselines.getFirst();
        return new Result(new Context(
                iteration,
                true,
                baseline.previousProposal(),
                baseline.previousReference(),
                List.of(new AgentResultEnvelope.ReviewObligation("HUMAN-CLARIFY-1", note))), List.of());
    }

    static boolean nodeBelongsToIteration(
            Path workstreamDir,
            String nodeInstanceId,
            String expectedIterationId
    ) throws IOException {
        if (nodeInstanceId == null || nodeInstanceId.isBlank()) {
            return false;
        }
        var nodePath = workstreamDir.resolve("nodes").resolve(nodeInstanceId + ".json");
        if (!Files.isRegularFile(nodePath)) {
            return false;
        }
        var node = OBJECT_MAPPER.readValue(nodePath.toFile(), NodeInstanceDocument.class);
        return expectedIterationId.equals(node.iterationId());
    }

    private List<Baseline> verifiedBaselines(
            Path root,
            String workstreamId,
            Path workstreamDir,
            List<String> refs,
            String expectedIterationId,
            Set<String> allowedDecisions,
            boolean requireObligations
    ) throws IOException {
        var baselines = new ArrayList<Baseline>();
        for (var ref : refs) {
            if (ref == null || !ref.endsWith(".contract.json")) {
                continue;
            }
            var path = root.resolve(ref).normalize();
            if (!path.startsWith(root) || !Files.isRegularFile(path)) {
                continue;
            }
            var document = AgentOutputContractDocument.read(path, OBJECT_MAPPER);
            if (document.isEmpty()
                    || AgentOutputLineageVerifier.violation(root, workstreamId, path, document.get()).isPresent()
                    || !"intake.codex_product_review".equals(document.get().nodeId())
                    || !"agent_output_contract".equals(document.get().sourceArtifactType())
                    || !allowedDecisions.contains(document.get().decision())
                    || document.get().reviewSubject() == null
                    || (requireObligations && document.get().contractObligations().isEmpty())
                    || !nodeBelongsToIteration(
                            workstreamDir, document.get().nodeInstanceId(), expectedIterationId)
                    || !nodeBelongsToIteration(
                            workstreamDir,
                            document.get().reviewSubject().producerNodeInstanceId(),
                            expectedIterationId)) {
                continue;
            }
            var artifactPath = document.get().reviewSubject().resolveArtifact(root, workstreamId, OBJECT_MAPPER);
            if (artifactPath.isEmpty()) {
                continue;
            }
            var proposalDocument = AgentOutputContractDocument.read(artifactPath.get(), OBJECT_MAPPER);
            if (proposalDocument.isEmpty() || proposalDocument.get().taskContractProposal() == null) {
                continue;
            }
            baselines.add(new Baseline(
                    document.get(),
                    document.get().reviewSubject(),
                    proposalDocument.get().taskContractProposal()));
        }
        return List.copyOf(baselines);
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        return values.stream().map(String::valueOf).filter(item -> !item.isBlank()).toList();
    }

    private static Path workstreamDir(Path workspaceRoot, String workstreamId) {
        return workspaceRoot.toAbsolutePath().normalize()
                .resolve(".agent/workstreams")
                .resolve(workstreamId);
    }

    private static Result failure(String message, String... facts) {
        var errors = new ArrayList<String>();
        errors.add(message);
        errors.addAll(List.of(facts));
        return new Result(null, List.copyOf(errors));
    }

    private static String message(Exception failure) {
        return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    public record Context(
            WorkflowIterationDocument iteration,
            boolean revision,
            TaskContractProposal previousProposal,
            TaskContractProposalReference previousReference,
            List<AgentResultEnvelope.ReviewObligation> obligations
    ) {}

    public record Result(Context context, List<String> errors) {
        public boolean valid() {
            return context != null && errors.isEmpty();
        }
    }

    private record Baseline(
            AgentOutputContractDocument review,
            TaskContractProposalReference previousReference,
            TaskContractProposal previousProposal
    ) {}
}
