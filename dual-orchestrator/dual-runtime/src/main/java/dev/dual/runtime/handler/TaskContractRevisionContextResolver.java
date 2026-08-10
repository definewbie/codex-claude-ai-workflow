package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.dual.core.workflow.NodeStatus;
import dev.dual.ledger.file.NodeInstanceDocument;
import dev.dual.ledger.file.WorkflowIterationDocument;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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
                    root, workstreamId, workstreamDir, trigger.inputRefs(), iteration.parentIterationId());
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
            String expectedIterationId
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
                    || !"NEEDS_REVISION".equals(document.get().decision())
                    || document.get().reviewSubject() == null
                    || document.get().contractObligations().isEmpty()
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
