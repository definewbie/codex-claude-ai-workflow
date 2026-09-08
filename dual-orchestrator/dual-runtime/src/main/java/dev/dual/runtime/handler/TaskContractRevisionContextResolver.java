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
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Collections;
import dev.dual.ledger.file.WorkflowDefinitionDocument;

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
            var resolved = resolveForIteration(workspaceRoot, workstreamId, node.iterationId());
            if (resolved.valid() && resolved.context().lateRoot() != null)
                new LateReader(workspaceRoot, workstreamId).verifyAuthor(node, resolved.context().iteration());
            return resolved;
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
            var ancestry = lateAncestry(root, workstreamId, iteration);
            if (!ancestry.isEmpty()) {
                return new LateReader(root, workstreamId).resolve(ancestry);
            }
            if ("clarify".equals(iteration.triggerAction())) {
                return resolveContractClarification(
                        root, workstreamId, workstreamDir, iteration);
            }
            if ("revise".equals(iteration.triggerAction())) {
                return resolveHumanRequirementRevision(
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

    private Result resolveHumanRequirementRevision(
            Path root,
            String workstreamId,
            Path workstreamDir,
            WorkflowIterationDocument iteration
    ) throws IOException {
        if (iteration.triggerNodeInstanceId().isBlank() || iteration.parentIterationId().isBlank()) {
            return failure("human requirement revision trigger lineage is incomplete", iteration.iterationId());
        }
        var triggerPath = workstreamDir.resolve("nodes").resolve(iteration.triggerNodeInstanceId() + ".json");
        if (!Files.isRegularFile(triggerPath)) {
            return failure("human requirement revision gate node is missing", triggerPath.toString());
        }
        var trigger = OBJECT_MAPPER.readValue(triggerPath.toFile(), NodeInstanceDocument.class);
        var note = text(trigger.result().get("note"));
        if (!"gate.human_requirement_review".equals(trigger.nodeDefinitionId())
                || !iteration.parentIterationId().equals(trigger.iterationId())
                || trigger.status() != NodeStatus.COMPLETED
                || !"revise".equals(text(trigger.result().get("human_action")))
                || note.isBlank()) {
            return failure("revision trigger is not a completed human requirement revision gate",
                    iteration.triggerNodeInstanceId());
        }
        var baselines = verifiedBaselines(
                root, workstreamId, workstreamDir, trigger.inputRefs(), iteration.parentIterationId(),
                Set.of("BLOCKED"), false);
        if (baselines.size() != 1) {
            return failure("human requirement revision must reference exactly one verified blocked intake review",
                    "review_count=" + baselines.size());
        }
        var baseline = baselines.getFirst();
        return new Result(new Context(
                iteration,
                true,
                baseline.previousProposal(),
                baseline.previousReference(),
                List.of(new AgentResultEnvelope.ReviewObligation("HUMAN-REVISE-1", note))), List.of());
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

    public Result resolveForProposal(Path root, String workstreamId, TaskContractProposalReference candidate) {
        try {
            require(candidate != null && candidate.resolveArtifact(root, workstreamId, OBJECT_MAPPER).isPresent(),
                    "proposal reference invalid");
            var result = resolveForNode(root, workstreamId, candidate.producerNodeInstanceId());
            if (result.valid() && result.context().lateRoot() != null) {
                var reader = new LateReader(root, workstreamId);
                reader.proposal(candidate);
                reader.derivedFrom(candidate, result.context().previousReference());
            }
            return result;
        } catch (Exception failure) {
            return failure("proposal revision context could not be resolved", message(failure));
        }
    }

    public Result resolveForDisplayIteration(Path root, String workstreamId, String iterationId) {
        return resolveForIteration(root, workstreamId, iterationId);
    }

    public TaskContractProposalReference proposalForCompile(Path root, String workstreamId, String compileId)
            throws IOException {
        return new LateReader(root, workstreamId).compiledBaseline(compileId).previousReference();
    }

    public TaskContractProposalReference proposalForContractGate(Path root, String workstreamId, String gateNodeId)
            throws IOException {
        var reader = new LateReader(root, workstreamId);
        return reader.compiledBaseline(contractGateCompile(reader, gateNodeId).nodeInstanceId()).previousReference();
    }

    TaskContractProjectionVerifier.LegacyProjection contractGateCandidate(Path root, String workstreamId, String gateNodeId)
            throws IOException {
        var reader = new LateReader(root, workstreamId);
        var compile = contractGateCompile(reader, gateNodeId);
        reader.compiledBaseline(compile.nodeInstanceId());
        var projection = TaskContractProjectionVerifier.readLegacyProjection(root, workstreamId).projection()
                .orElseThrow(() -> new IOException("contract candidate artifacts are missing or invalid"));
        reader.path(projection.contractRef());
        reader.path(projection.resolutionRef());
        require(compile.outputRefs().contains(projection.contractRef())
                && compile.outputRefs().contains(projection.resolutionRef()), "contract candidate refs are not bound by compile");
        require(TaskContractArtifactWriter.sha256(projection.rawJson().getBytes(java.nio.charset.StandardCharsets.UTF_8))
                        .equals(text(compile.result().get("task_contract_sha256")))
                && TaskContractArtifactWriter.sha256(projection.rawResolutionJson().getBytes(java.nio.charset.StandardCharsets.UTF_8))
                        .equals(text(compile.result().get("contract_resolution_sha256"))),
                "contract candidate SHA does not match source compile");
        // Return the checked snapshot; presentation must not read the stable files a second time.
        return projection;
    }

    private static NodeInstanceDocument contractGateCompile(LateReader reader, String gateNodeId) throws IOException {
        var gateNode = reader.node(gateNodeId);
        var event = reader.origin(gateNodeId);
        var gate = OBJECT_MAPPER.readValue(reader.path(reader.relative(
                reader.dir.resolve("gates/" + event.gateId() + ".json"))).toFile(), GateDocument.class);
        require(gateNode.nodeDefinitionId().equals("gate.contract_review")
                && event.to().equals(gateNode.nodeDefinitionId())
                && gate.nodeInstanceId().equals(gateNodeId) && gate.gateId().equals(event.gateId())
                && gateNode.inputRefs().equals(stringList(gate.result().get("subject_input_refs"))),
                "current contract gate subject mismatch");
        var sourceId = text(gateNode.result().get("source_node_instance_id"));
        require(sourceId.equals(event.sourceId())
                && sourceId.equals(text(gate.result().get("source_node_instance_id")))
                && event.action().equals(text(gate.result().get("source_outcome"))),
                "current contract gate source mismatch");
        var compile = reader.node(sourceId);
        reader.completed(compile, "contract.compile");
        require(compile.iterationId().equals(gateNode.iterationId()), "current contract gate iteration mismatch");
        reader.route(compile, gateNode, event.action());
        return compile;
    }

    public Result resolveForCompile(Path root, String workstreamId, String compileId) {
        try {
            return resolveForProposal(root, workstreamId, proposalForCompile(root, workstreamId, compileId));
        } catch (Exception failure) {
            return failure("compile revision context could not be resolved", message(failure));
        }
    }

    static Map<String, Object> captureAcceptanceReworkBaseline(
            Path root, String workstreamId, TaskContractProjectionVerifier.QualifiedTaskContract qualified,
            AgentEvidenceIndex.Entry acceptance) throws IOException {
        var reader = new LateReader(root, workstreamId);
        var reviewRef = reader.relative(acceptance.path());
        var review = reader.acceptance(reviewRef, reader.sha(reviewRef));
        require(review.equals(acceptance.document()), "acceptance capture changed while reading");
        var compile = reader.node(qualified.compileNodeInstanceId());
        var baseline = reader.compiledBaseline(compile.nodeInstanceId());
        var approval = reader.gateByRef(qualified.approvalGateRef(), "approved");
        reader.approvedRoute(approval);
        require(approval.node().nodeDefinitionId().equals("gate.contract_review")
                && approval.source().nodeInstanceId().equals(compile.nodeInstanceId())
                && approval.event().id().equals(qualified.approvalEventId()), "qualified contract gate mismatch");
        var requirement = reader.requirementApproval(compile, baseline);
        require(text(qualified.resolutionDocument().get("gate_ref")).equals(requirement.ref()),
                "qualified resolution requirement gate mismatch");
        require(qualified.sha256().equals(text(compile.result().get("task_contract_sha256")))
                && qualified.resolutionSha256().equals(text(compile.result().get("contract_resolution_sha256"))),
                "qualified compile hashes mismatch");
        require(reader.sha(qualified.contractRef()).equals(qualified.sha256())
                && reader.sha(qualified.resolutionRef()).equals(qualified.resolutionSha256()),
                "qualified capture artifacts changed");
        var workspace = reader.workspace();
        var values = new LinkedHashMap<String, Object>();
        values.put("proposal_reference", baseline.previousReference().toMap());
        values.put("acceptance_ref", reviewRef);
        values.put("acceptance_sha256", reader.sha(reviewRef));
        var productReviewRef = reader.reviewRef(compile.inputRefs());
        values.put("proposal_review_ref", productReviewRef);
        values.put("proposal_review_sha256", reader.sha(productReviewRef));
        values.put("compile_node_instance_id", compile.nodeInstanceId());
        values.put("task_contract_sha256", qualified.sha256());
        values.put("contract_resolution_sha256", qualified.resolutionSha256());
        values.put("contract_gate_ref", approval.ref());
        values.put("contract_gate_sha256", reader.sha(approval.ref()));
        values.put("contract_gate_event_id", approval.event().id());
        values.put("requirement_gate_ref", requirement.ref());
        values.put("requirement_gate_sha256", reader.sha(requirement.ref()));
        values.put("workspace_id", workspace.get(0));
        values.put("base_commit", workspace.get(1));
        return Map.of("acceptance_rework_baseline", Map.copyOf(values));
    }

    private static boolean contractRework(String action) {
        return Set.of("REJECT_CONTRACT_GAP", "REJECT_CONTRACT_AMBIGUITY").contains(action);
    }

    // Only the nearest acceptance root belongs to this negotiation. Initial negotiation stays unchanged.
    private static List<WorkflowIterationDocument> lateAncestry(
            Path root, String ws, WorkflowIterationDocument current) throws IOException {
        var chain = new ArrayList<WorkflowIterationDocument>();
        var seen = new HashSet<String>();
        var dir = workstreamDir(root, ws);
        var item = current;
        while (true) {
            require(seen.add(item.iterationId()), "contract revision ancestry cycle");
            chain.add(item);
            if (contractRework(item.triggerAction())) {
                Collections.reverse(chain);
                return List.copyOf(chain);
            }
            if ("workflow_started".equals(item.triggerAction())) return List.of();
            require(!item.parentIterationId().isBlank(), "contract revision ancestry parent missing");
            var parentId = item.parentIterationId();
            var path = dir.resolve("iterations").resolve(parentId + ".json").normalize();
            require(path.getParent().equals(dir.resolve("iterations")) && Files.isRegularFile(path),
                    "contract revision ancestry parent missing");
            item = OBJECT_MAPPER.readValue(path.toFile(), WorkflowIterationDocument.class);
            require(parentId.equals(item.iterationId()), "contract revision ancestry identity mismatch");
        }
    }

    private static void require(boolean condition, String reason) throws IOException {
        if (!condition) throw new IOException(reason);
    }

    private static final class LateReader {
        private final Path root;
        private final String ws;
        private final Path dir;
        private final List<Event> events;
        private final WorkflowDefinitionDocument definition;

        LateReader(Path root, String ws) throws IOException {
            this.root = root.toAbsolutePath().normalize();
            this.ws = ws;
            this.dir = workstreamDir(this.root, ws);
            definition = OBJECT_MAPPER.readValue(path(relative(dir.resolve("workflow/definition.json"))).toFile(),
                    WorkflowDefinitionDocument.class);
            var items = new ArrayList<Event>();
            var ids = new HashSet<String>();
            for (var line : Files.readAllLines(path(relative(dir.resolve("events.jsonl"))))) {
                if (line.isBlank()) continue;
                var tree = OBJECT_MAPPER.readTree(line);
                var id = tree.path("event_id").asText();
                require(ids.add(id), "duplicate historical event identity");
                if (!tree.path("event_type").asText().equals("node_routed")) continue;
                require(id.matches("evt-[0-9]+"), "invalid routing event identity");
                var payload = tree.path("payload");
                items.add(new Event(id, Long.parseLong(id.substring(4)),
                        payload.path("from").asText(), payload.path("outcome").asText(),
                        payload.path("to").asText(), payload.path("node_instance_id").asText(),
                        payload.path("gate_id").asText(), payload.path("source_node_instance_id").asText()));
            }
            events = List.copyOf(items);
        }

        Path path(String ref) throws IOException {
            var value = root.resolve(ref).normalize();
            require(!ref.isBlank() && !Path.of(ref).isAbsolute() && relative(value).equals(ref)
                    && value.startsWith(dir) && Files.isRegularFile(value)
                    && value.toRealPath().equals(root.toRealPath().resolve(ref).normalize()),
                    "missing or noncanonical historical reference: " + ref);
            return value;
        }

        String relative(Path value) { return root.relativize(value.toAbsolutePath().normalize()).toString(); }
        String sha(String ref) throws IOException { return TaskContractArtifactWriter.sha256(path(ref)); }
        NodeInstanceDocument node(String id) throws IOException {
            require(id.matches("[A-Za-z0-9._-]+"), "invalid historical node identity");
            var node = OBJECT_MAPPER.readValue(path(relative(dir.resolve("nodes/" + id + ".json"))).toFile(),
                    NodeInstanceDocument.class);
            require(id.equals(node.nodeInstanceId()), "historical node identity mismatch");
            return node;
        }

        void completed(NodeInstanceDocument node, String role) throws IOException {
            require(node.status() == NodeStatus.COMPLETED && node.nodeDefinitionId().equals(role),
                    "historical node not completed with required role: " + role);
        }

        void edge(String from, String action, String to) throws IOException {
            require(definition.edges().stream().filter(e -> e.from().equals(from)
                    && e.on().equals(action) && e.to().equals(to)).count() == 1,
                    "historical transition is not uniquely declared");
        }

        Event origin(String id) throws IOException {
            var matches = events.stream().filter(e -> e.targetId().equals(id)).toList();
            require(matches.size() == 1, "historical routed occurrence missing or ambiguous: " + id);
            return matches.getFirst();
        }

        Event route(NodeInstanceDocument source, NodeInstanceDocument target, String action) throws IOException {
            var event = origin(target.nodeInstanceId());
            require(event.from().equals(source.nodeDefinitionId()) && event.to().equals(target.nodeDefinitionId())
                    && event.action().equals(action) && target.inputRefs().equals(source.outputRefs()),
                    "historical routed source/input mismatch");
            edge(event.from(), action, event.to());
            return event;
        }

        AgentOutputContractDocument document(String ref) throws IOException {
            var file = path(ref);
            var entries = AgentEvidenceIndex.fromRefs(root, ws, List.of(ref), OBJECT_MAPPER).recent(2);
            require(entries.size() == 1 && entries.getFirst().path().equals(file),
                    "historical sidecar publication lineage or semantics invalid");
            return entries.getFirst().document();
        }

        TaskContractProposal proposal(TaskContractProposalReference ref) throws IOException {
            require(ref != null && ref.resolveArtifact(root, ws, OBJECT_MAPPER).isPresent(),
                    "historical proposal reference invalid");
            var document = document(ref.artifactRef());
            require(document.taskContractProposal() != null, "historical proposal payload missing");
            return document.taskContractProposal();
        }

        AgentOutputContractDocument acceptance(String ref, String hash) throws IOException {
            require(sha(ref).equals(hash), "acceptance reference hash mismatch");
            var document = document(ref);
            require(document.nodeId().equals("acceptance.codex_verifier_review")
                    && document.sourceArtifactType().equals("acceptance_review")
                    && document.decision().equals("REJECT") && !document.failure().contractGaps().isEmpty(),
                    "late root requires a rejected acceptance with contract obligations");
            return document;
        }

        String reviewRef(List<String> refs) throws IOException {
            var matches = new ArrayList<String>();
            for (var ref : refs) {
                if (!ref.endsWith(".contract.json")) continue;
                var document = document(ref);
                if (document.nodeId().equals("intake.codex_product_review")) matches.add(ref);
            }
            require(matches.size() == 1, "exactly one historical proposal review required");
            return matches.getFirst();
        }

        Baseline reviewed(List<String> refs, String iteration, Set<String> decisions) throws IOException {
            var review = document(reviewRef(refs));
            require(review.sourceArtifactType().equals("agent_output_contract")
                    && decisions.contains(review.decision()) && review.reviewSubject() != null,
                    "historical proposal review subject/decision invalid");
            require(node(review.nodeInstanceId()).iterationId().equals(iteration)
                    && node(review.reviewSubject().producerNodeInstanceId()).iterationId().equals(iteration),
                    "historical review/proposal iteration mismatch");
            return new Baseline(review, review.reviewSubject(), proposal(review.reviewSubject()));
        }

        Gate gateByRef(String ref, String action) throws IOException {
            var document = OBJECT_MAPPER.readValue(path(ref).toFile(), GateDocument.class);
            var node = node(document.nodeInstanceId());
            var event = origin(node.nodeInstanceId());
            require(event.gateId().equals(document.gateId()) && event.to().equals(node.nodeDefinitionId())
                    && document.status().equals("resolved") && node.status() == NodeStatus.COMPLETED
                    && action.equals(text(document.result().get("human_action")))
                    && action.equals(text(node.result().get("human_action")))
                    && text(document.result().get("note")).equals(text(node.result().get("note")))
                    && node.inputRefs().equals(stringList(document.result().get("subject_input_refs"))),
                    "historical gate decision/subject mismatch");
            var sourceId = text(document.result().get("source_node_instance_id"));
            require(!sourceId.isBlank() && sourceId.equals(event.sourceId())
                    && sourceId.equals(text(node.result().get("source_node_instance_id"))),
                    "historical gate source identity mismatch");
            var source = node(sourceId);
            require(source.status() == NodeStatus.COMPLETED && source.iterationId().equals(node.iterationId())
                    && event.action().equals(text(document.result().get("source_outcome"))),
                    "historical gate source state mismatch");
            route(source, node, event.action());
            return new Gate(ref, node, document, source, event);
        }

        Gate gate(NodeInstanceDocument node, String action) throws IOException {
            var event = origin(node.nodeInstanceId());
            return gateByRef(relative(dir.resolve("gates/" + event.gateId() + ".json")), action);
        }

        Baseline requirementSubject(Gate gate, Set<String> decisions) throws IOException {
            completed(gate.node(), "gate.human_requirement_review");
            completed(gate.source(), "intake.decision");
            var baseline = reviewed(gate.node().inputRefs(), gate.node().iterationId(), decisions);
            require(gate.source().inputRefs().equals(gate.node().inputRefs())
                    && baseline.review().decision().equals(text(gate.source().result().get("decision")))
                    && baseline.review().decision().equals(gate.event().action()),
                    "requirement decision does not match reviewed subject");
            route(node(baseline.review().nodeInstanceId()), gate.source(), "completed");
            return baseline;
        }

        Gate requirementApproval(NodeInstanceDocument compile, Baseline baseline) throws IOException {
            var matches = new ArrayList<Gate>();
            try (var files = Files.list(dir.resolve("gates"))) {
                for (var file : files.filter(p -> p.getFileName().toString().endsWith(".json")).toList()) {
                    var doc = OBJECT_MAPPER.readValue(file.toFile(), GateDocument.class);
                    var candidate = node(doc.nodeInstanceId());
                    if (!candidate.nodeDefinitionId().equals("gate.human_requirement_review")
                            || !candidate.iterationId().equals(compile.iterationId())
                            || !candidate.outputRefs().equals(compile.inputRefs())
                            || !doc.status().equals("resolved")
                            || !text(doc.result().get("human_action")).equals("approved")) continue;
                    var gate = gateByRef(relative(file), "approved");
                    var subject = requirementSubject(gate, Set.of("READY_FOR_PRODUCT_REVIEW", "BLOCKED"));
                    require(subject.previousReference().equals(baseline.previousReference()),
                            "compile requirement subject mismatch");
                    if (subject.review().decision().equals("BLOCKED"))
                        require(!text(doc.result().get("note")).isBlank(), "blocked approval note missing");
                    route(candidate, compile, "approved");
                    matches.add(gate);
                }
            }
            require(matches.size() == 1, "compile requirement approval missing or ambiguous");
            return matches.getFirst();
        }

        Baseline compiledBaseline(String id) throws IOException {
            var compile = node(id);
            completed(compile, "contract.compile");
            var baseline = reviewed(compile.inputRefs(), compile.iterationId(),
                    Set.of("READY_FOR_PRODUCT_REVIEW", "BLOCKED"));
            requirementApproval(compile, baseline);
            return baseline;
        }

        void derivedFrom(TaskContractProposalReference candidate, TaskContractProposalReference parent) throws IOException {
            var doc = document(candidate.artifactRef());
            require(doc.proposalDerivation() != null && doc.sourceArtifactType().equals("task_contract_revision_patch")
                    && doc.proposalDerivation().baseProposalReference().equals(parent),
                    "late proposal does not derive from the preceding patch parent");
            var author = node(candidate.producerNodeInstanceId());
            var iteration = OBJECT_MAPPER.readValue(path(relative(dir.resolve("iterations/"
                    + author.iterationId() + ".json"))).toFile(), WorkflowIterationDocument.class);
            verifyAuthor(author, iteration);
        }

        void verifyAuthor(NodeInstanceDocument author, WorkflowIterationDocument iteration) throws IOException {
            require(author.iterationId().equals(iteration.iterationId()), "late author iteration mismatch");
            var trigger = trigger(iteration);
            var entry = node(iterationRoute(iteration, trigger).targetId());
            if (iteration.targetNodeDefinitionId().equals("intake.claude_brainstorm")) {
                require(entry.nodeInstanceId().equals(author.nodeInstanceId()), "late author is not the routed iteration entry");
            } else {
                completed(entry, "context.intake_claude_brainstorm");
                require(entry.iterationId().equals(author.iterationId()), "late intake context iteration mismatch");
                route(entry, author, "completed");
            }
        }

        Event approvedRoute(Gate gate) throws IOException {
            var matches = new ArrayList<Event>();
            for (var event : events) {
                if (event.sequence() <= gate.event().sequence() || !event.from().equals(gate.node().nodeDefinitionId())
                        || !event.action().equals("approved")) continue;
                var target = node(event.targetId());
                if (target.iterationId().equals(gate.node().iterationId())
                        && target.inputRefs().equals(gate.node().outputRefs()))
                    matches.add(route(gate.node(), target, "approved"));
            }
            require(matches.size() == 1, "contract approval successor missing or ambiguous");
            return matches.getFirst();
        }

        List<String> workspace() throws IOException {
            var workstream = OBJECT_MAPPER.readTree(path(relative(dir.resolve("workstream.json"))).toFile());
            var id = workstream.path("primaryWorkspaceId").asText();
            require(id.matches("[A-Za-z0-9._-]+"), "primary workspace missing");
            var workspace = OBJECT_MAPPER.readTree(path(relative(dir.resolve("workspaces/" + id + ".json"))).toFile());
            var workdir = workspace.path("workdir").asText();
            require(workdir.isBlank() || Path.of(workdir).toAbsolutePath().normalize().equals(root),
                    "original workspace directory changed");
            var base = workspace.path("baseCommit").asText();
            require(!base.isBlank(), "original workspace base missing");
            return List.of(id, base);
        }

        Result resolve(List<WorkflowIterationDocument> chain) throws IOException {
            var first = chain.getFirst();
            var classifier = trigger(first);
            completed(classifier, "acceptance.classify_failure");
            require(first.targetNodeDefinitionId().equals("context.intake_claude_brainstorm"),
                    "late root target must be intake context");
            var rootEvent = iterationRoute(first, classifier);
            require(classifier.result().get("acceptance_rework_baseline") instanceof Map<?, ?>,
                    "fixed acceptance rework baseline missing");
            var binding = OBJECT_MAPPER.convertValue(classifier.result().get("acceptance_rework_baseline"),
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            var ref = TaskContractProposalReference.fromMap(OBJECT_MAPPER.convertValue(binding.get("proposal_reference"),
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {})).orElseThrow(
                    () -> new IOException("fixed approved proposal reference missing"));
            var acceptanceRef = text(binding.get("acceptance_ref"));
            var acceptanceHash = text(binding.get("acceptance_sha256"));
            var acceptance = acceptance(acceptanceRef, acceptanceHash);
            require(classifier.inputRefs().contains(acceptanceRef) && classifier.outputRefs().contains(acceptanceRef)
                    && node(acceptance.nodeInstanceId()).iterationId().equals(classifier.iterationId())
                    && first.triggerAction().equals("REJECT_" + text(classifier.result().get("category")))
                    && text(classifier.result().get("category")).equals(acceptance.failure().categoryHint()),
                    "classifier acceptance identity/category mismatch");
            require(classifier.inputRefs().stream().filter(r -> r.endsWith(".contract.json")).count() == 1,
                    "classifier acceptance source is ambiguous");
            var compile = node(text(binding.get("compile_node_instance_id")));
            var base = compiledBaseline(compile.nodeInstanceId());
            require(base.previousReference().equals(ref), "frozen compile proposal mismatch");
            var productRef = reviewRef(compile.inputRefs());
            require(productRef.equals(text(binding.get("proposal_review_ref")))
                    && sha(productRef).equals(text(binding.get("proposal_review_sha256")))
                    && text(compile.result().get("task_contract_sha256")).equals(text(binding.get("task_contract_sha256")))
                    && text(compile.result().get("contract_resolution_sha256")).equals(text(binding.get("contract_resolution_sha256"))),
                    "frozen compile/review hash mismatch");
            var contractGateRef = text(binding.get("contract_gate_ref"));
            require(sha(contractGateRef).equals(text(binding.get("contract_gate_sha256"))), "frozen contract gate hash mismatch");
            var approval = gateByRef(contractGateRef, "approved");
            require(approvedRoute(approval).sequence() < rootEvent.sequence(), "root precedes its contract approval");
            require(approval.node().nodeDefinitionId().equals("gate.contract_review")
                    && approval.source().nodeInstanceId().equals(compile.nodeInstanceId())
                    && approval.event().id().equals(text(binding.get("contract_gate_event_id"))),
                    "frozen contract approval mismatch");
            var requirement = requirementApproval(compile, base);
            require(requirement.ref().equals(text(binding.get("requirement_gate_ref")))
                    && sha(requirement.ref()).equals(text(binding.get("requirement_gate_sha256"))),
                    "frozen requirement approval mismatch");
            var workspace = workspace();
            require(workspace.equals(List.of(text(binding.get("workspace_id")), text(binding.get("base_commit")))),
                    "original workspace identity/base changed");
            var lateRoot = new LateRoot(classifier.nodeInstanceId(), first.iterationId(), ref,
                    acceptanceRef, acceptanceHash, workspace.get(0), workspace.get(1), acceptance);
            var obligations = new LinkedHashMap<String, AgentResultEnvelope.ReviewObligation>();
            for (int i = 0; i < acceptance.failure().contractGaps().size(); i++)
                add(obligations, "sha256:" + acceptanceHash + "#contract-gap:" + i,
                        acceptance.failure().contractGaps().get(i));
            var direct = base;
            long through = rootEvent.sequence();
            for (int i = 1; i < chain.size(); i++) {
                var iteration = chain.get(i);
                require(iteration.parentIterationId().equals(chain.get(i - 1).iterationId()), "late parent chain mismatch");
                var trigger = trigger(iteration);
                var event = iterationRoute(iteration, trigger);
                require(event.sequence() > through, "late revision event order invalid");
                through = event.sequence();
                var previous = direct.previousReference();
                if (iteration.triggerAction().equals("NEEDS_REVISION")) {
                    completed(trigger, "intake.decision");
                    direct = reviewed(trigger.inputRefs(), trigger.iterationId(), Set.of("NEEDS_REVISION"));
                    require(text(trigger.result().get("decision")).equals("NEEDS_REVISION")
                            && !direct.review().contractObligations().isEmpty(), "late review obligations missing");
                    route(node(direct.review().nodeInstanceId()), trigger, "completed");
                } else if (iteration.triggerAction().equals("revise")) {
                    var gate = gate(trigger, "revise");
                    direct = requirementSubject(gate, Set.of("READY_FOR_PRODUCT_REVIEW", "BLOCKED"));
                } else if (iteration.triggerAction().equals("clarify")) {
                    var gate = gate(trigger, "clarify");
                    completed(trigger, "gate.contract_review");
                    direct = compiledBaseline(gate.source().nodeInstanceId());
                } else throw new IOException("unsupported late revision trigger");
                derivedFrom(direct.previousReference(), previous);
                addReview(obligations, direct.review(), reviewRef(
                        iteration.triggerAction().equals("clarify")
                                ? node(gate(trigger, "clarify").source().nodeInstanceId()).inputRefs() : trigger.inputRefs()));
                if (!iteration.triggerAction().equals("NEEDS_REVISION")) {
                    var note = text(trigger.result().get("note"));
                    require(!note.isBlank(), "late revision human note missing");
                    add(obligations, "gate:" + trigger.nodeInstanceId() + "#note", note);
                }
            }
            // Approval closes negotiation; a future rejection must capture a new root instead of inheriting it.
            for (var event : events) {
                if (event.sequence() <= approval.event().sequence() || event.sequence() > through
                        || !event.to().equals("gate.contract_review")) continue;
                var node = node(event.targetId());
                if (node.status() == NodeStatus.COMPLETED && text(node.result().get("human_action")).equals("approved"))
                    throw new IOException("late revision crosses a newer approved contract epoch");
            }
            return new Result(new Context(chain.getLast(), true, direct.previousProposal(), direct.previousReference(),
                    List.copyOf(obligations.values()), base.previousProposal(), lateRoot), List.of());
        }

        NodeInstanceDocument trigger(WorkflowIterationDocument iteration) throws IOException {
            var trigger = node(iteration.triggerNodeInstanceId());
            require(trigger.status() == NodeStatus.COMPLETED && trigger.iterationId().equals(iteration.parentIterationId()),
                    "late trigger parent/status mismatch");
            return trigger;
        }

        Event iterationRoute(WorkflowIterationDocument iteration, NodeInstanceDocument trigger) throws IOException {
            var matches = new ArrayList<Event>();
            for (var event : events) {
                if (!event.from().equals(trigger.nodeDefinitionId()) || !event.action().equals(iteration.triggerAction())
                        || !event.to().equals(iteration.targetNodeDefinitionId())) continue;
                var target = node(event.targetId());
                if (target.iterationId().equals(iteration.iterationId())) {
                    matches.add(route(trigger, target, iteration.triggerAction()));
                }
            }
            require(matches.size() == 1, "late iteration routing missing or ambiguous");
            return matches.getFirst();
        }

        void addReview(Map<String, AgentResultEnvelope.ReviewObligation> obligations,
                       AgentOutputContractDocument review, String ref) throws IOException {
            for (var obligation : review.contractObligations())
                add(obligations, "sha256:" + sha(ref) + "#review:" + obligation.id(), obligation.text());
        }

        void add(Map<String, AgentResultEnvelope.ReviewObligation> values, String id, String value) throws IOException {
            require(value != null && !value.isBlank(), "late obligation text missing");
            var obligation = new AgentResultEnvelope.ReviewObligation(id, value);
            var previous = values.putIfAbsent(id, obligation);
            require(previous == null || previous.equals(obligation), "late obligation source collision");
        }
    }

    private record Event(String id, long sequence, String from, String action, String to,
                         String targetId, String gateId, String sourceId) {}
    private record Gate(String ref, NodeInstanceDocument node, GateDocument document,
                        NodeInstanceDocument source, Event event) {}

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
            List<AgentResultEnvelope.ReviewObligation> obligations,
            TaskContractProposal approvedScopeBaseline,
            LateRoot lateRoot
    ) {
        public Context(WorkflowIterationDocument iteration, boolean revision,
                       TaskContractProposal previousProposal, TaskContractProposalReference previousReference,
                       List<AgentResultEnvelope.ReviewObligation> obligations) {
            this(iteration, revision, previousProposal, previousReference, obligations, null, null);
        }

        public String originalBaseCommit() { return lateRoot == null ? "" : lateRoot.baseCommit(); }
    }

    public record LateRoot(String classifierNodeInstanceId, String iterationId,
                           TaskContractProposalReference approvedProposalReference,
                           String acceptanceRef, String acceptanceSha256,
                           String workspaceId, String baseCommit,
                           AgentOutputContractDocument acceptanceEvidence) {}

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
