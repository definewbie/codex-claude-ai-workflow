package dev.dual.runtime.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.dual.core.contract.TaskContract;
import dev.dual.core.contract.TaskContractValidator;
import dev.dual.core.workflow.NodeStatus;
import dev.dual.core.workflow.NodeResult;
import dev.dual.ledger.file.GateDocument;
import dev.dual.ledger.file.NodeInstanceDocument;
import dev.dual.ledger.file.WorkflowFileLedger;
import dev.dual.ledger.file.LedgerSession;
import dev.dual.ledger.file.WorkstreamPaths;
import dev.dual.core.id.DualId;
import dev.dual.ledger.file.ContractQualificationChecks;
import dev.dual.ledger.file.ContractQualificationChecks.*;
import dev.dual.runtime.handler.ContractQualificationTrace.Rule;
import static dev.dual.ledger.file.ContractQualificationChecks.values;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/** Resolves the last human-approved contract.compile occurrence into one immutable snapshot. */
final class TaskContractProjectionVerifier {
    static final String FAILURE_OUTCOME = "TASK_CONTRACT_PROJECTION_QUALIFICATION_FAILED";
    private static final Pattern WORKFLOW_EVENT_ID = Pattern.compile("evt-(\\d{6,})");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private TaskContractProjectionVerifier() {
    }

    /** Local tasks preserve their original cause; this is the same diagnostic, not a native execution record. */
    static NodeResult failureResult(TaskHandler.TaskContext context, Failure failure, String errorMessage) {
        var facts = new java.util.LinkedHashMap<String, Object>();
        facts.put("failure_code", FAILURE_OUTCOME);
        facts.put("reason", failure.reason());
        facts.put("error", failure.message());
        facts.put("continuation_failure", ContractQualificationTrace.continuation(context, failure.reason(),
                failure.check(), context.definition().handlerType()).toMap());
        return new NodeResult(FAILURE_OUTCOME, NodeStatus.FAILED, List.of(), facts, errorMessage);
    }

    static boolean requiresContract(dev.dual.core.workflow.NodeDefinition definition) {
        if (definition.config().containsKey("requires_approved_task_contract"))
            throw new IllegalArgumentException("legacy contract qualification flag is not supported: " + definition.id());
        if (!definition.config().containsKey("required_contract_phase")) return false;
        requiredPhase(definition);
        return true;
    }

    static TaskContract.Phase requiredPhase(dev.dual.core.workflow.NodeDefinition definition) {
        var value = definition.config().get("required_contract_phase");
        if (!(value instanceof String phase)) throw new IllegalArgumentException("required_contract_phase is missing: " + definition.id());
        try { return TaskContract.Phase.valueOf(phase); }
        catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("invalid contract phase: " + definition.id()); }
    }

    static Resolution resolve(WorkflowFileLedger ledger, Path workspaceRoot, String workstreamId) {
        return resolve(ledger, workspaceRoot, workstreamId, TaskContract.Phase.EXECUTION);
    }

    static Resolution resolve(WorkflowFileLedger ledger, Path workspaceRoot, String workstreamId, TaskContract.Phase phase) {
        var paths = new WorkstreamPaths(workspaceRoot, new DualId(workstreamId));
        var trace = new ContractQualificationTrace(ledger, workspaceRoot);
        try {
            return ledger.inspect(paths, (snapshot, files) -> phase == TaskContract.Phase.GOAL
                    ? resolveGoal(snapshot, files, trace) : resolveExecution(snapshot, files, true, trace));
        } catch (IOException | IllegalArgumentException failure) {
            return Resolution.failed(failure instanceof IOException io ? trace.failure(Rule.SNAPSHOT_AVAILABLE, "CONTRACT_SNAPSHOT_UNAVAILABLE", io) : trace.failure(Rule.SNAPSHOT_AVAILABLE, "CONTRACT_SNAPSHOT_UNAVAILABLE", failure.getMessage()));
        }
    }

    private static Resolution resolveGoal(WorkflowFileLedger ledger, LedgerSession files, ContractQualificationTrace trace) throws IOException {
        var paths = files.paths();
        var root = paths.workspaceRoot();
        var ws = paths.workstreamId().value();
        var nodes = readNodes(files, paths.nodesDir(), trace);
        if (nodes.failure() != null) return Resolution.failed(nodes.failure());
        var candidates = nodes.nodes().stream().filter(n -> "contract.compile".equals(n.handlerType())
                && n.status() == NodeStatus.COMPLETED && "GOAL".equals(text(n.result().get("contract_phase"))))
                .sorted(java.util.Comparator.comparingLong((NodeInstanceDocument n) -> longValue(n.result().get("contract_epoch"))).reversed())
                .toList();
        if (candidates.isEmpty()) return Resolution.absent();
        var compile = candidates.getFirst();
        long epoch = longValue(compile.result().get("contract_epoch"));
        if (trace.rejects(Rule.GOAL_EPOCH, (epoch < 1 || candidates.stream().filter(n -> longValue(n.result().get("contract_epoch")) == epoch).count() != 1), values("epoch", epoch, "completedGoalCompilers", candidates)))
            return Resolution.failed(trace.failure(Rule.GOAL_EPOCH, "CONTRACT_EPOCH_INVALID", "GOAL authorization epoch is missing or duplicated"));
        var prefix = ".agent/workstreams/" + ws + "/artifacts/contracts/" + compile.nodeInstanceId() + "/";
        var contractRef = prefix + "task-contract.json";
        var resolutionRef = prefix + "contract-resolution.json";
        if (trace.rejects(Rule.GOAL_OUTPUTS, (!compile.outputRefs().containsAll(List.of(contractRef, resolutionRef))), values("compiler", compile, "contractRef", contractRef, "resolutionRef", resolutionRef), paths.nodeJson(compile.nodeInstanceId())))
            return Resolution.failed(trace.failure(Rule.GOAL_OUTPUTS, "OUTPUT_REF_MISMATCH", "GOAL compile does not publish its occurrence artifacts"));
        var bytes = trace.readBytes(files, root.resolve(contractRef));
        var resolutionBytes = trace.readBytes(files, root.resolve(resolutionRef));
        var contract = TaskContractArtifactWriter.mapper().readValue(bytes, TaskContract.class);
        if (trace.rejects(Rule.GOAL_IDENTITY, (contract.phase() != TaskContract.Phase.GOAL || contract.epoch() != epoch
                || !TaskContractValidator.validate(contract).isEmpty()
                || !contract.id().equals(text(compile.result().get("contract_id")))
                || !contract.status().name().equals(text(compile.result().get("contract_status")))
                || contract.version() != intValue(compile.result().get("contract_version"))
                || !TaskContractArtifactWriter.sha256(bytes).equals(text(compile.result().get("task_contract_sha256")))
                || !TaskContractArtifactWriter.sha256(resolutionBytes).equals(text(compile.result().get("contract_resolution_sha256")))), values("compiler", compile, "contract", contract, "contractSha", TaskContractArtifactWriter.sha256(bytes), "resolutionSha", TaskContractArtifactWriter.sha256(resolutionBytes)), root.resolve(contractRef), root.resolve(resolutionRef), paths.nodeJson(compile.nodeInstanceId())))
            return Resolution.failed(trace.failure(Rule.GOAL_IDENTITY, "GOAL_CONTRACT_INVALID", "GOAL identity, shape or immutable bytes do not match its compiler"));
        var approval = contract.source().goalApproval();
        var approvalPath = root.resolve(approval.ref());
        if (trace.rejects(Rule.GOAL_APPROVAL_BYTES, (!approvalPath.normalize().getParent().equals(paths.gatesDir().toAbsolutePath().normalize())
                || !approval.sha256().equals(TaskContractArtifactWriter.sha256(trace.readBytes(files, approvalPath)))), values("approval", approval, "approvalPath", approvalPath.normalize().toString(), "expectedParent", paths.gatesDir().toAbsolutePath().normalize().toString()), approvalPath))
            return Resolution.failed(trace.failure(Rule.GOAL_APPROVAL_BYTES, "GOAL_APPROVAL_INVALID", "GOAL approval reference is not an unchanged workstream gate"));
        var gate = trace.read(files, approvalPath, GateDocument.class);
        var gateNode = node(nodes.nodes(), gate.nodeInstanceId());
        if (trace.rejects(Rule.GOAL_APPROVAL_ROUTE, (gateNode == null || gateNode.status() != NodeStatus.COMPLETED
                || !"gate.human_requirement_review".equals(gateNode.nodeDefinitionId())
                || !"resolved".equals(gate.status())
                || !"approved".equals(text(gate.result().get("human_action")))
                || !"approved".equals(text(gateNode.result().get("human_action")))
                || !text(gate.result().get("note")).equals(text(gateNode.result().get("note")))
                || !textList(gate.result().get("subject_input_refs")).equals(gateNode.inputRefs())
                || !compile.inputRefs().equals(gateNode.outputRefs())
                || !gateNode.nodeInstanceId().equals(text(compile.inputTransition().get("source_node_instance_id")))
                || !TaskContractArtifactWriter.sha256(trace.readBytes(files, paths.nodeJson(gateNode.nodeInstanceId())))
                        .equals(text(compile.inputTransition().get("source_node_sha256")))), values("gate", gate, "gateNode", gateNode, "compiler", compile), approvalPath, paths.nodeJson(gate.nodeInstanceId()), paths.nodeJson(compile.nodeInstanceId())))
            return Resolution.failed(trace.failure(Rule.GOAL_APPROVAL_ROUTE, "GOAL_APPROVAL_INVALID", "GOAL compiler is not routed from the matching completed human decision"));
        var review = AgentEvidenceIndex.latestDecisionFromRefs(ledger, root, ws, gateNode.inputRefs(), OBJECT_MAPPER);
        if (trace.rejects(Rule.GOAL_REVIEW_SUBJECT, (review.entry() == null || !"intake.codex_product_review".equals(review.entry().document().nodeId())
                || review.entry().document().reviewSubject() == null), values("review", review.entry() == null ? null : review.entry().document())))
            return Resolution.failed(trace.failure(Rule.GOAL_REVIEW_SUBJECT, "GOAL_SUBJECT_INVALID", "approved review subject is not verifiable"));
        var subject = review.entry().document().reviewSubject();
        if (trace.rejects(Rule.GOAL_PROPOSAL_BYTES, (!contract.source().proposal().ref().equals(subject.artifactRef())
                || !contract.source().proposal().sha256().equals(subject.sha256())
                || !subject.sha256().equals(TaskContractArtifactWriter.sha256(trace.readBytes(files, root.resolve(subject.artifactRef()))))), values("declaredProposal", contract.source().proposal(), "reviewSubject", subject), root.resolve(subject.artifactRef())))
            return Resolution.failed(trace.failure(Rule.GOAL_PROPOSAL_BYTES, "GOAL_SUBJECT_INVALID", "GOAL does not bind the approved proposal bytes"));
        String approvalEvent = null;
        long last = -1;
        for (var line : files.readLines(paths.eventsJsonl())) {
            var event = OBJECT_MAPPER.readValue(line, MAP_TYPE);
            var id = text(event.get("event_id"));
            var matcher = WORKFLOW_EVENT_ID.matcher(id);
            if (!matcher.matches()) {
                if (trace.rejects(Rule.GOAL_EVENT_ID, ("node_routed".equals(text(event.get("event_type")))), values("event", event))) throw trace.rejection(Rule.GOAL_EVENT_ID, "invalid route event identity");
                continue;
            }
            long sequence = Long.parseLong(matcher.group(1));
            if (trace.eventCheck(Rule.GOAL_EVENT_ORDER, (sequence <= last), values("sequence", sequence, "previous", last), trace.route(paths.eventsJsonl(), line, id, sequence))) throw trace.rejection(Rule.GOAL_EVENT_ORDER, "unordered workflow events");
            last = sequence;
            var payload = map(event.get("payload"));
            if (!"node_routed".equals(text(event.get("event_type")))
                    || !compile.nodeInstanceId().equals(text(payload.get("node_instance_id")))) continue;
            if (trace.eventCheck(Rule.GOAL_EVENT_BINDING, (approvalEvent != null || !gateNode.nodeInstanceId().equals(text(payload.get("source_node_instance_id")))
                    || !gateNode.nodeDefinitionId().equals(text(payload.get("from")))
                    || !compile.nodeDefinitionId().equals(text(payload.get("to")))
                    || !"approved".equals(text(payload.get("outcome")))), values("previousApprovalEvent", approvalEvent, "event", event, "compiler", compile.nodeInstanceId(), "gateNode", gateNode.nodeInstanceId()), trace.route(paths.eventsJsonl(), line, id, sequence)))
                throw trace.rejection(Rule.GOAL_EVENT_BINDING, "invalid GOAL approval routing occurrence");
            approvalEvent = id;
        }
        if (trace.rejects(Rule.GOAL_EVENT_PRESENT, (approvalEvent == null), values("approvalEvent", approvalEvent, "compiler", compile.nodeInstanceId(), "gateNode", gateNode.nodeInstanceId()))) return Resolution.failed(trace.failure(Rule.GOAL_EVENT_PRESENT, "GOAL_APPROVAL_INVALID", "GOAL approval route is missing"));
        return Resolution.qualified(new QualifiedTaskContract(new String(bytes, StandardCharsets.UTF_8), contract,
                OBJECT_MAPPER.readValue(bytes, MAP_TYPE), TaskContractArtifactWriter.sha256(bytes), contractRef,
                new String(resolutionBytes, StandardCharsets.UTF_8), OBJECT_MAPPER.readValue(resolutionBytes, MAP_TYPE),
                TaskContractArtifactWriter.sha256(resolutionBytes), resolutionRef, compile.nodeInstanceId(),
                ".agent/workstreams/" + ws + "/nodes/" + compile.nodeInstanceId() + ".json", approval.ref(), approvalEvent));
    }

    /** Inspection of an approval boundary only; never usable as dispatch qualification. */
    static Resolution approvedExecutionBoundary(WorkflowFileLedger ledger, WorkstreamPaths paths) throws IOException {
        return ledger.inspect(paths, (scoped, files) -> resolveExecution(scoped, files, false, new ContractQualificationTrace(scoped, paths.workspaceRoot())));
    }

    private static Resolution resolveExecution(WorkflowFileLedger ledger, LedgerSession files, boolean qualifyReadiness, ContractQualificationTrace trace) throws IOException {
        var workspaceRoot = files.paths().workspaceRoot();
        var workstreamId = files.paths().workstreamId().value();
        var workstreamDir = files.paths().workstreamDir();
        var nodes = readNodes(files, workstreamDir.resolve("nodes"), trace);
        if (nodes.failure() != null) {
            return Resolution.failed(nodes.failure());
        }
        var occurrenceRead = lastContractGateOccurrence(files, workstreamDir.resolve("events.jsonl"), trace);
        if (occurrenceRead.failure() != null) {
            return Resolution.failed(occurrenceRead.failure());
        }
        if (occurrenceRead.occurrence() == null) return Resolution.absent();
        var occurrence = occurrenceRead.occurrence();
        var gateNode = node(nodes.nodes(), occurrence.nodeInstanceId());
        if (trace.rejects(Rule.EXECUTION_GATE_PRESENT, (gateNode == null || !"gate.contract_review".equals(gateNode.nodeDefinitionId())), values("gateNode", gateNode, "origin", occurrence), workstreamDir.resolve("nodes/" + occurrence.nodeInstanceId() + ".json"))) {
            return Resolution.failed(trace.failure(Rule.EXECUTION_GATE_PRESENT, "GATE_NODE_MISSING", "contract approval gate node is missing"));
        }
        if (trace.rejects(Rule.EXECUTION_GATE_COMPLETED, (gateNode.status() != NodeStatus.COMPLETED), values("gateNode", gateNode), workstreamDir.resolve("nodes/" + gateNode.nodeInstanceId() + ".json"))) {
            return Resolution.failed(trace.failure(Rule.EXECUTION_GATE_COMPLETED, "GATE_NOT_COMPLETED", "latest contract approval gate is not completed"));
        }
        var gateRead = readGate(files, workstreamDir.resolve("gates").resolve(occurrence.gateId() + ".json"), trace);
        if (gateRead.failure() != null) {
            return Resolution.failed(gateRead.failure());
        }
        var gate = gateRead.gate();
        if (trace.rejects(Rule.EXECUTION_GATE_LINEAGE, (!gate.nodeInstanceId().equals(gateNode.nodeInstanceId()) || !"resolved".equals(gate.status())), values("gateNode", gateNode.nodeInstanceId(), "gate", gate), workstreamDir.resolve("gates/" + occurrence.gateId() + ".json"))) {
            return Resolution.failed(trace.failure(Rule.EXECUTION_GATE_LINEAGE, "GATE_LINEAGE_INVALID", "latest contract gate lineage or status is invalid"));
        }
        var gateAction = text(gate.result().get("human_action"));
        var nodeAction = text(gateNode.result().get("human_action"));
        if (trace.rejects(Rule.EXECUTION_APPROVED, (!"approved".equals(gateAction) || !gateAction.equals(nodeAction)), values("gateAction", gateAction, "nodeAction", nodeAction))) {
            return Resolution.failed(trace.failure(Rule.EXECUTION_APPROVED,
                    "CONTRACT_NOT_APPROVED", "latest contract gate action is not approved: " + gateAction));
        }
        if (trace.rejects(Rule.EXECUTION_NOTE, (!text(gate.result().get("note")).equals(text(gateNode.result().get("note")))), values("gateNote", gate.result().get("note"), "nodeNote", gateNode.result().get("note")))) {
            return Resolution.failed(trace.failure(Rule.EXECUTION_NOTE, "GATE_RESULT_MISMATCH", "contract gate note does not match node result"));
        }
        var gateRefs = textList(gate.result().get("subject_input_refs"));
        if (trace.rejects(Rule.EXECUTION_SUBJECT, (!gateRefs.equals(gateNode.inputRefs())), values("gateRefs", gateRefs, "nodeInputRefs", gateNode.inputRefs()))) {
            return Resolution.failed(trace.failure(Rule.EXECUTION_SUBJECT, "GATE_SUBJECT_MISMATCH", "contract gate subject refs do not match node input refs"));
        }
        var sourceId = text(gate.result().get("source_node_instance_id"));
        if (trace.rejects(Rule.EXECUTION_SOURCE, (sourceId.isBlank()
                || !sourceId.equals(text(gateNode.result().get("source_node_instance_id")))
                || !sourceId.equals(occurrence.sourceNodeInstanceId())), values("source", sourceId, "nodeSource", gateNode.result().get("source_node_instance_id"), "routeSource", occurrence.sourceNodeInstanceId()))) {
            return Resolution.failed(trace.failure(Rule.EXECUTION_SOURCE, "GATE_SOURCE_MISMATCH", "contract gate source occurrence identity is missing or inconsistent"));
        }
        var compileNode = node(nodes.nodes(), sourceId);
        if (trace.rejects(Rule.EXECUTION_COMPILER, (compileNode == null
                || !"contract.compile".equals(compileNode.handlerType())
                || !"EXECUTION".equals(text(compileNode.result().get("contract_phase")))
                || compileNode.status() != NodeStatus.COMPLETED), values("compiler", compileNode))) {
            return Resolution.failed(trace.failure(Rule.EXECUTION_COMPILER, "COMPILE_BINDING_MISSING", "approved source is not a completed contract.compile node"));
        }
        if (trace.rejects(Rule.EXECUTION_EVENT_SOURCE, (!compileNode.nodeDefinitionId().equals(occurrence.fromNodeDefinitionId())), values("compilerDefinition", compileNode.nodeDefinitionId(), "routeFrom", occurrence.fromNodeDefinitionId()))) {
            return Resolution.failed(trace.failure(Rule.EXECUTION_EVENT_SOURCE, "EVENT_SOURCE_MISMATCH", "routing event source does not match approved compile node"));
        }
        var newerCompile = newerCompletedCompile(nodes.nodes(), compileNode);
        if (trace.rejects(Rule.EXECUTION_NEWER_COMPILER, (newerCompile != null), values("approvedCompiler", compileNode, "newerCompiler", newerCompile))) {
            return Resolution.failed(trace.failure(Rule.EXECUTION_NEWER_COMPILER,
                    "UNAPPROVED_RECOMPILE", "completed contract.compile is newer than the approved source: "
                            + newerCompile.nodeInstanceId()));
        }
        var artifactsDir = workstreamDir.resolve("artifacts/contracts").resolve(compileNode.nodeInstanceId());
        var contractPath = artifactsDir.resolve("task-contract.json");
        var resolutionPath = artifactsDir.resolve("contract-resolution.json");
        var availabilityFailure = availableArtifacts(files, trace, contractPath, resolutionPath);
        if (availabilityFailure != null) return Resolution.failed(availabilityFailure);
        var contractRef = ".agent/workstreams/" + workstreamId + "/artifacts/contracts/" + compileNode.nodeInstanceId() + "/task-contract.json";
        var resolutionRef = ".agent/workstreams/" + workstreamId + "/artifacts/contracts/" + compileNode.nodeInstanceId() + "/contract-resolution.json";
        if (trace.rejects(Rule.EXECUTION_OUTPUTS, (!compileNode.outputRefs().contains(contractRef)
                || !compileNode.outputRefs().contains(resolutionRef)
                || !gateRefs.contains(contractRef)
                || !gateRefs.contains(resolutionRef)), values("compilerRefs", compileNode.outputRefs(), "gateRefs", gateRefs, "contractRef", contractRef, "resolutionRef", resolutionRef))) {
            return Resolution.failed(trace.failure(Rule.EXECUTION_OUTPUTS, "OUTPUT_REF_MISMATCH", "approved compile/gate refs do not bind both contract artifacts"));
        }

        final byte[] contractBytes;
        final byte[] resolutionBytes;
        final TaskContract contract;
        final Map<String, Object> document;
        final Map<String, Object> resolutionDocument;
        try {
            contractBytes = trace.readBytes(files, contractPath);
            resolutionBytes = trace.readBytes(files, resolutionPath);
            contract = TaskContractArtifactWriter.mapper().readValue(contractBytes, TaskContract.class);
            document = OBJECT_MAPPER.readValue(contractBytes, MAP_TYPE);
            resolutionDocument = OBJECT_MAPPER.readValue(resolutionBytes, MAP_TYPE);
        } catch (Exception failure) {
            return Resolution.failed(trace.failure(Rule.EXECUTION_PARSE, "CONTRACT_INVALID", "approved contract artifacts cannot be parsed"));
        }
        var identityFailure = validateArtifactIdentity(trace, compileNode, contract, contractBytes, resolutionBytes, contractRef, resolutionRef);
        if (identityFailure != null) return Resolution.failed(identityFailure);
        var sha256 = TaskContractArtifactWriter.sha256(contractBytes);
        var resolutionSha256 = TaskContractArtifactWriter.sha256(resolutionBytes);
        var inputFailure = validateExecutionInputs(ledger, files, compileNode, contract, false, trace);
        if (inputFailure == null && qualifyReadiness) {
            try {
                var bindings = currentVerificationBindings(ledger, files, contract, contractRef, sha256, trace);
                validateReadiness(ledger, workspaceRoot, workstreamId,
                        resolveGoal(ledger, files, trace).qualified().orElseThrow(), bindings, trace);
            } catch (IOException invalid) { inputFailure = trace.failure(Rule.CANDIDATE_READINESS, VerificationReadiness.REQUIRED, invalid); }
        }
        if (inputFailure != null) return Resolution.failed(inputFailure);
        var nodeRef = ".agent/workstreams/" + workstreamId + "/nodes/"
                + compileNode.nodeInstanceId() + ".json";
        var gateRef = ".agent/workstreams/" + workstreamId + "/gates/" + occurrence.gateId() + ".json";
        return Resolution.qualified(new QualifiedTaskContract(
                new String(contractBytes, StandardCharsets.UTF_8), contract, Map.copyOf(document), sha256,
                contractRef, new String(resolutionBytes, StandardCharsets.UTF_8), Map.copyOf(resolutionDocument),
                resolutionSha256, resolutionRef, compileNode.nodeInstanceId(), nodeRef, gateRef, occurrence.eventId()));
    }

    private static Failure availableArtifacts(LedgerSession files, ContractQualificationTrace trace, Path contractPath, Path resolutionPath) throws IOException {
        var contractPresent = trace.exists(files, contractPath);
        // Preserve the original short circuit; an unobserved second file is not a missing file claim.
        var resolutionPresent = contractPresent && trace.exists(files, resolutionPath);
        if (trace.rejects(Rule.EXECUTION_ARTIFACTS, !contractPresent || !resolutionPresent,
                values("contractPath", contractPath.toString(), "resolutionPath", resolutionPath.toString(),
                        "contractPresent", contractPresent, "resolutionObserved", contractPresent, "resolutionPresent", resolutionPresent)))
            return trace.failure(Rule.EXECUTION_ARTIFACTS, "ARTIFACT_MISSING", "approved contract or requirement resolution artifact is missing");
        return null;
    }

    private static Failure validateArtifactIdentity(ContractQualificationTrace trace, NodeInstanceDocument compileNode,
            TaskContract contract, byte[] contractBytes, byte[] resolutionBytes, String contractRef, String resolutionRef) {
        var errors = TaskContractValidator.validate(contract);
        if (trace.rejects(Rule.EXECUTION_SHAPE, (contract.phase() != TaskContract.Phase.EXECUTION || !errors.isEmpty()), values("contract", contract, "validationErrors", errors), trace.root().resolve(contractRef))) {
            return (trace.failure(Rule.EXECUTION_SHAPE,
                    "CONTRACT_INVALID", "task-contract.json is invalid: " + String.join("; ", errors)));
        }

        var sha256 = TaskContractArtifactWriter.sha256(contractBytes);
        var resolutionSha256 = TaskContractArtifactWriter.sha256(resolutionBytes);
        var expectedSha = text(compileNode.result().get("task_contract_sha256"));
        var expectedResolutionSha = text(compileNode.result().get("contract_resolution_sha256"));
        if (trace.rejects(Rule.EXECUTION_HASH_BINDING, (expectedSha.isBlank() || expectedResolutionSha.isBlank()), values("contractExpectedSha", expectedSha, "resolutionExpectedSha", expectedResolutionSha), trace.root().resolve(contractRef), trace.root().resolve(resolutionRef))) {
            return (trace.failure(Rule.EXECUTION_HASH_BINDING,
                    "COMPILE_BINDING_MISSING", "approved contract.compile has incomplete artifact SHA binding"));
        }
        if (trace.rejects(Rule.EXECUTION_HASH, (!sha256.equals(expectedSha) || !resolutionSha256.equals(expectedResolutionSha)), values("contractSha", sha256, "resolutionSha", resolutionSha256, "expectedContractSha", expectedSha, "expectedResolutionSha", expectedResolutionSha), trace.root().resolve(contractRef), trace.root().resolve(resolutionRef))) {
            return (trace.failure(Rule.EXECUTION_HASH, "HASH_MISMATCH", "approved contract artifact SHA-256 does not match compile result"));
        }
        if (trace.rejects(Rule.EXECUTION_IDENTITY, (!contract.id().equals(text(compileNode.result().get("contract_id")))
                || !contract.status().name().equals(text(compileNode.result().get("contract_status")))
                || contract.version() != intValue(compileNode.result().get("contract_version"))
                || contract.epoch() != longValue(compileNode.result().get("contract_epoch"))), values("contractId", contract.id(), "contractStatus", contract.status().name(), "contractVersion", contract.version(), "contractEpoch", contract.epoch(), "compilerId", compileNode.result().get("contract_id"), "compilerStatus", compileNode.result().get("contract_status"), "compilerVersion", compileNode.result().get("contract_version"), "compilerEpoch", compileNode.result().get("contract_epoch")), trace.root().resolve(contractRef))) {
            return (trace.failure(Rule.EXECUTION_IDENTITY,
                    "IDENTITY_MISMATCH", "task-contract.json identity does not match compile result"));
        }
        return null;
    }

    private static List<TaskContract.VerificationBinding> currentVerificationBindings(WorkflowFileLedger ledger,
            LedgerSession files, TaskContract approved, String contractRef, String sha, ContractQualificationTrace trace) throws IOException {
        var paths = files.paths();
        var current = ledger.loadCurrent(paths);
        if (current.activeNodeInstanceIds().size() != 1) return approved.verificationBindings();
        var cursor = ledger.loadNode(paths, current.activeNodeInstanceIds().getFirst());
        var visited = new java.util.HashSet<String>();
        while (visited.add(cursor.nodeInstanceId())) {
            if (Boolean.TRUE.equals(cursor.result().get("authorization_reused"))) {
                if (trace.rejects(Rule.TECHNICAL_APPROVAL, (cursor.status() != NodeStatus.COMPLETED || !"contract.compile".equals(cursor.handlerType())
                        || !contractRef.equals(cursor.result().get("approved_contract_ref"))
                        || !sha.equals(cursor.result().get("approved_contract_sha256"))), values("cursor", cursor, "approvedContractRef", contractRef, "approvedContractSha", sha)))
                    throw trace.rejection(Rule.TECHNICAL_APPROVAL, "invalid technical continuation approval binding");
                var lineage = new AuthoredStageLineageResolver(ledger, paths.workspaceRoot(), paths.workstreamId().value(), Long.MAX_VALUE);
                var plan = lineage.reviewed(AuthoredStageLineageResolver.Stage.PLAN, cursor);
                var design = lineage.reviewed(AuthoredStageLineageResolver.Stage.DESIGN, cursor);
                var expectedReviews = List.of(new TaskContract.Reference(design.decision().ref(), design.decision().sha256()),
                        new TaskContract.Reference(plan.decision().ref(), plan.decision().sha256()));
                if (trace.rejects(Rule.TECHNICAL_REVIEW, (!OBJECT_MAPPER.valueToTree(expectedReviews).equals(OBJECT_MAPPER.valueToTree(cursor.result().get("reviewed_candidates")))), values("expectedReviews", expectedReviews, "actualReviews", cursor.result().get("reviewed_candidates"))))
                    throw trace.rejection(Rule.TECHNICAL_REVIEW, "technical continuation lost its independent reviews");
                var document = AgentOutputContractDocument.read(AuthoredExecutionContext.verifiedPath(paths.workspaceRoot(), plan.candidate().sidecar()), OBJECT_MAPPER).orElseThrow();
                if (trace.rejects(Rule.TECHNICAL_BOUNDARY, (!sameExecutionBoundary(ledger, paths, approved, document.planArtifact())), values("approved", approved, "plan", document.planArtifact())))
                    throw trace.rejection(Rule.TECHNICAL_BOUNDARY, "technical revision changed the execution authorization boundary");
                return document.planArtifact().verificationBindings();
            }
            var source = text(cursor.inputTransition().get("source_node_instance_id"));
            if (source.isBlank()) return approved.verificationBindings();
            var path = paths.nodeJson(source);
            if (trace.rejects(Rule.TECHNICAL_SOURCE, (!TaskContractArtifactWriter.sha256(trace.readBytes(files, path)).equals(text(cursor.inputTransition().get("source_node_sha256")))), values("routeSource", source, "sourceExpectedSha", cursor.inputTransition().get("source_node_sha256")), path))
                throw trace.rejection(Rule.TECHNICAL_SOURCE, "technical continuation route source changed");
            cursor = ledger.loadNode(paths, source);
        }
        throw trace.rejection(Rule.TECHNICAL_CYCLE, "technical continuation lineage cycle");
    }

    static boolean sameExecutionBoundary(WorkflowFileLedger ledger, WorkstreamPaths paths, TaskContract approved,
                                         PlanArtifactDocument plan) throws IOException {
        return approved.nonessentialOperationDeclarations().equals(ContractCompileHandler.nonessentialDeclarations(ledger, paths))
                && plan != null && new java.util.HashSet<>(approved.scope().included()).equals(new java.util.HashSet<>(plan.files()))
                && approved.authorityRefs().equals(plan.authorityRefs())
                && VerificationReadiness.sameVerificationBoundary(ledger, paths, approved.verificationBindings(), plan.verificationBindings());
    }

    private static Failure validateExecutionInputs(WorkflowFileLedger ledger, LedgerSession files,
                                                   NodeInstanceDocument compileNode, TaskContract contract,
                                                   boolean readiness, ContractQualificationTrace trace) throws IOException {
        var workspaceRoot = files.paths().workspaceRoot();
        var workstreamId = files.paths().workstreamId().value();
        var goalResolution = resolveGoal(ledger, files, trace);
        if (trace.rejects(Rule.GOAL_REQUIRED, (goalResolution.qualified().isEmpty()), values("goalPresent", goalResolution.qualified().isPresent(), "goalFailure", goalResolution.failure() == null ? null : goalResolution.failure().reason()))) return trace.failure(Rule.GOAL_REQUIRED, "GOAL_CONTRACT_NOT_QUALIFIED", "current GOAL is required");
        var goal = goalResolution.qualified().orElseThrow();
        if (trace.rejects(Rule.EXECUTION_GOAL, (!new TaskContract.Reference(goal.contractRef(), goal.sha256()).equals(contract.goalContract())
                || contract.epoch() != goal.contract().epoch()
                || !contract.objective().equals(goal.contract().objective())
                || !contract.acceptanceCriteria().equals(goal.contract().acceptanceCriteria())
                || !contract.testRequirements().equals(goal.contract().testRequirements())
                || !contract.constraints().equals(goal.contract().constraints())
                || !contract.nonGoals().equals(goal.contract().nonGoals())
                || !contract.scope().excluded().equals(goal.contract().scope().excluded())
                || !contract.source().equals(goal.contract().source())), values("execution", contract, "goal", goal.contract(), "goalRef", goal.contractRef(), "goalSha", goal.sha256()), workspaceRoot.resolve(goal.contractRef())))
            return trace.failure(Rule.EXECUTION_GOAL, "EXECUTION_GOAL_MISMATCH", "execution contract no longer binds the approved goal");
        trace.readBytes(files, files.paths().workflowDefinitionJson());
        var actualDeclarations = ContractCompileHandler.nonessentialDeclarations(ledger, files.paths());
        if (trace.rejects(Rule.EXECUTION_DECLARATIONS, (!contract.nonessentialOperationDeclarations().equals(actualDeclarations)), values("declared", contract.nonessentialOperationDeclarations(), "frozen", actualDeclarations), files.paths().workflowDefinitionJson()))
            return trace.failure(Rule.EXECUTION_DECLARATIONS, "EXECUTION_DECLARATION_MISMATCH", "nonessential declarations must bind the frozen node contracts");
        var lineage = new AuthoredStageLineageResolver(ledger, workspaceRoot, workstreamId, Long.MAX_VALUE);
        var reviewed = new ArrayList<TaskContract.Reference>();
        for (var stage : AuthoredStageLineageResolver.Stage.values()) {
            var value = lineage.reviewed(stage, compileNode);
            reviewed.add(new TaskContract.Reference(value.decision().ref(), value.decision().sha256()));
        }
        if (trace.rejects(Rule.EXECUTION_REVIEWS, (!reviewed.equals(contract.reviewedCandidates())), values("declaredReviews", contract.reviewedCandidates(), "routedReviews", reviewed)))
            return trace.failure(Rule.EXECUTION_REVIEWS, "EXECUTION_REVIEW_MISMATCH", "execution contract must consume its routed independent reviews");
        if (readiness) {
            try { validateReadiness(ledger, workspaceRoot, workstreamId, goal, contract.verificationBindings(), trace); }
            catch (IOException failure) { return trace.failure(Rule.CANDIDATE_READINESS, VerificationReadiness.REQUIRED, failure.getMessage()); }
        }
        return null;
    }

    private static void validateReadiness(WorkflowFileLedger ledger, Path root, String ws,
            QualifiedTaskContract goal, List<TaskContract.VerificationBinding> bindings, ContractQualificationTrace trace) throws IOException {
        var observed = new java.util.LinkedHashMap<String, Object>();
        try {
            VerificationReadiness.validateBindings(ledger, root, ws, goal, bindings, observed);
            trace.checkReadiness(ContractQualificationChecks.values("readiness", observed), false);
        } catch (IOException failure) {
            trace.checkReadiness(ContractQualificationChecks.values("readiness", observed), true);
            throw trace.rejection(Rule.CANDIDATE_READINESS, failure.getMessage());
        }
    }

    /** The current gate's immutable subject. This read never confers execution approval. */
    static CandidateProjection executionCandidate(WorkflowFileLedger ledger, Path root, String ws, String gateNodeId,
                                                   boolean requireReady) throws IOException {
        var paths = new WorkstreamPaths(root, new DualId(ws));
        var trace = new ContractQualificationTrace(ledger, root);
        return ledger.inspect(paths, (snapshot, files) -> {
            var current = snapshot.loadCurrent(paths);
            if (trace.rejects(Rule.CANDIDATE_CURRENT, (!current.activeNodeInstanceIds().equals(List.of(gateNodeId))
                    || current.status() != dev.dual.core.workflow.WorkflowStatus.WAITING_FOR_HUMAN), values("active", current.activeNodeInstanceIds(), "status", current.status(), "expectedGate", gateNodeId)))
                throw trace.rejection(Rule.CANDIDATE_CURRENT, "contract candidate requires its current waiting gate");
            var gateNode = snapshot.loadNode(paths, gateNodeId);
            var gate = snapshot.loadOpenGateForNode(paths, gateNodeId);
            var sourceId = text(gateNode.result().get("source_node_instance_id"));
            var origin = lastContractGateOccurrence(files, paths.eventsJsonl(), trace);
            if (trace.rejects(Rule.CANDIDATE_SUBJECT, (!"gate.contract_review".equals(gateNode.nodeDefinitionId()) || gate == null || origin.failure() != null
                    || origin.occurrence() == null || !gateNodeId.equals(origin.occurrence().nodeInstanceId())
                    || !gate.gateId().equals(origin.occurrence().gateId())
                    || !sourceId.equals(origin.occurrence().sourceNodeInstanceId())
                    || !sourceId.equals(text(gate.result().get("source_node_instance_id")))
                    || !gateNode.inputRefs().equals(textList(gate.result().get("subject_input_refs")))), values("gateNode", gateNode, "gate", gate, "origin", origin.occurrence(), "originFailure", origin.failure() == null ? null : origin.failure().reason())))
                throw trace.rejection(Rule.CANDIDATE_SUBJECT, "contract gate subject or route is inconsistent");
            var compile = snapshot.loadNode(paths, sourceId);
            if (trace.rejects(Rule.CANDIDATE_COMPILER, (compile.status() != NodeStatus.COMPLETED || !"contract.compile".equals(compile.handlerType())
                    || !"EXECUTION".equals(compile.result().get("contract_phase"))
                    || !compile.nodeDefinitionId().equals(origin.occurrence().fromNodeDefinitionId())
                    || !sourceId.equals(text(gateNode.inputTransition().get("source_node_instance_id")))
                    || !TaskContractArtifactWriter.sha256(trace.readBytes(files, paths.nodeJson(sourceId)))
                        .equals(text(gateNode.inputTransition().get("source_node_sha256")))), values("compiler", compile, "gateSource", sourceId, "gateInputTransition", gateNode.inputTransition(), "routeFrom", origin.occurrence().fromNodeDefinitionId())))
                throw trace.rejection(Rule.CANDIDATE_COMPILER, "contract gate does not bind its completed execution compiler");
            var prefix = ".agent/workstreams/" + ws + "/artifacts/contracts/" + sourceId + "/";
            var contractRef = prefix + "task-contract.json";
            var resolutionRef = prefix + "contract-resolution.json";
            if (trace.rejects(Rule.CANDIDATE_OUTPUTS, (!compile.outputRefs().containsAll(List.of(contractRef, resolutionRef))
                    || !gateNode.inputRefs().containsAll(List.of(contractRef, resolutionRef))), values("compilerRefs", compile.outputRefs(), "gateRefs", gateNode.inputRefs(), "contractRef", contractRef, "resolutionRef", resolutionRef)))
                throw trace.rejection(Rule.CANDIDATE_OUTPUTS, "contract candidate references are not published by its compiler");
            var availabilityFailure = availableArtifacts(files, trace, root.resolve(contractRef), root.resolve(resolutionRef));
            if (availabilityFailure != null) throw trace.rejection(availabilityFailure);
            var bytes = trace.readBytes(files, root.resolve(contractRef));
            var resolution = trace.readBytes(files, root.resolve(resolutionRef));
            var contract = TaskContractArtifactWriter.mapper().readValue(bytes, TaskContract.class);
            if (trace.rejects(Rule.CANDIDATE_IDENTITY, (contract.phase() != TaskContract.Phase.EXECUTION || !TaskContractValidator.validate(contract).isEmpty()
                    || !contract.id().equals(compile.result().get("contract_id"))
                    || contract.epoch() != longValue(compile.result().get("contract_epoch"))
                    || contract.version() != intValue(compile.result().get("contract_version"))
                    || !contract.status().name().equals(compile.result().get("contract_status"))
                    || !TaskContractArtifactWriter.sha256(bytes).equals(compile.result().get("task_contract_sha256"))
                    || !TaskContractArtifactWriter.sha256(resolution).equals(compile.result().get("contract_resolution_sha256"))), values("contract", contract, "compiler", compile, "contractSha", TaskContractArtifactWriter.sha256(bytes), "resolutionSha", TaskContractArtifactWriter.sha256(resolution))))
                throw trace.rejection(Rule.CANDIDATE_IDENTITY, "contract candidate bytes or identity changed");
            var identityFailure = validateArtifactIdentity(trace, compile, contract, bytes, resolution, contractRef, resolutionRef);
            if (identityFailure != null) throw trace.rejection(identityFailure);
            var failure = validateExecutionInputs(snapshot, files, compile, contract, requireReady, trace);
            if (failure != null) throw trace.rejection(failure);
            trace.deferred(Rule.EXECUTION_GATE_PRESENT, "post-approval/target condition must be checked by its actual consumer");
            trace.deferred(Rule.EXECUTION_GATE_COMPLETED, "post-approval/target condition must be checked by its actual consumer");
            trace.deferred(Rule.EXECUTION_GATE_LINEAGE, "post-approval/target condition must be checked by its actual consumer");
            trace.deferred(Rule.EXECUTION_APPROVED, "post-approval/target condition must be checked by its actual consumer");
            trace.deferred(Rule.EXECUTION_NOTE, "post-approval/target condition must be checked by its actual consumer");
            trace.deferred(Rule.EXECUTION_SUBJECT, "post-approval/target condition must be checked by its actual consumer");
            trace.deferred(Rule.EXECUTION_SOURCE, "post-approval/target condition must be checked by its actual consumer");
            trace.deferred(Rule.EXECUTION_COMPILER, "post-approval/target condition must be checked by its actual consumer");
            trace.deferred(Rule.EXECUTION_EVENT_SOURCE, "post-approval/target condition must be checked by its actual consumer");
            trace.deferred(Rule.EXECUTION_NEWER_COMPILER, "post-approval/target condition must be checked by its actual consumer");
            trace.deferred(Rule.CURRENT_READINESS, "post-approval/target condition must be checked by its actual consumer");
            trace.deferred(Rule.TECHNICAL_APPROVAL, "post-approval/target condition must be checked by its actual consumer");
            trace.deferred(Rule.TECHNICAL_REVIEW, "post-approval/target condition must be checked by its actual consumer");
            trace.deferred(Rule.TECHNICAL_BOUNDARY, "post-approval/target condition must be checked by its actual consumer");
            trace.deferred(Rule.TECHNICAL_SOURCE, "post-approval/target condition must be checked by its actual consumer");
            trace.deferred(Rule.TECHNICAL_CYCLE, "post-approval/target condition must be checked by its actual consumer");
            trace.deferred(Rule.ACCEPTANCE_CONTEXT, "post-approval/target condition must be checked by its actual consumer");
            trace.deferred(Rule.ACCEPTANCE_CONTRACT, "post-approval/target condition must be checked by its actual consumer");
            trace.deferred(Rule.ACCEPTANCE_CONTEXT_READ, "post-approval/target condition must be checked by its actual consumer");
            trace.deferred(Rule.ACCEPTANCE_CONTEXT_UNIQUE, "post-approval/target condition must be checked by its actual consumer");
            trace.deferred(Rule.ACCEPTANCE_CONTEXT_BINDING, "post-approval/target condition must be checked by its actual consumer");
            trace.deferred(Rule.ACCEPTANCE_HASH, "post-approval/target condition must be checked by its actual consumer");
            trace.deferred(Rule.ACCEPTANCE_LINEAGE, "post-approval/target condition must be checked by its actual consumer");
            return new CandidateProjection(contract, OBJECT_MAPPER.readValue(bytes, MAP_TYPE), new String(bytes, StandardCharsets.UTF_8),
                    OBJECT_MAPPER.readValue(resolution, MAP_TYPE), new String(resolution, StandardCharsets.UTF_8), contractRef, resolutionRef,
                    trace.decision(gateNodeId, sourceId, gateNode.inputRefs()));
        });
    }



    static Optional<String> trustedWrappedPayload(
            WorkflowFileLedger ledger,
            Path workspaceRoot,
            String workstreamId,
            String nodeId,
            List<String> inputRefs
    ) {
        var trace = new ContractQualificationTrace(ledger, workspaceRoot);
        var binding = AcceptanceContextBinding.fromRefs(workspaceRoot, workstreamId, nodeId, inputRefs, OBJECT_MAPPER, trace);
        if (binding.isEmpty()) {
            return Optional.empty();
        }
        var resolution = resolve(ledger, workspaceRoot, workstreamId);
        if (resolution.failure() != null || resolution.qualified().isEmpty()) {
            throw new IllegalArgumentException(resolution.failure() == null
                    ? "qualified task contract is required"
                    : resolution.failure().reason() + ": " + resolution.failure().message());
        }
        var qualified = resolution.qualified().orElseThrow();
        var mismatch = binding.get().validate(qualified, trace);
        if (mismatch.isPresent()) {
            throw new IllegalArgumentException(mismatch.get().reason() + ": " + mismatch.get().message());
        }
        return Optional.of(qualified.wrappedPayload());
    }

    static Resolution resolveRoutedAcceptance(
            WorkflowFileLedger ledger,
            Path workspaceRoot,
            String workstreamId,
            String nodeId,
            List<String> inputRefs
    ) {
        var trace = new ContractQualificationTrace(ledger, workspaceRoot);
        final Optional<AcceptanceContextBinding> binding;
        try {
            binding = AcceptanceContextBinding.fromRefs(
                    workspaceRoot, workstreamId, nodeId, inputRefs, OBJECT_MAPPER, trace);
        } catch (IllegalArgumentException failure) {
            return Resolution.failed(trace.failure(Rule.ACCEPTANCE_CONTEXT, "CONTEXT_BINDING_INVALID", failure));
        }
        if (binding.isEmpty()) {
            return Resolution.absent();
        }
        var resolution = resolve(ledger, workspaceRoot, workstreamId);
        if (trace.rejects(Rule.ACCEPTANCE_CONTRACT, (resolution.failure() != null || resolution.qualified().isEmpty()), values("contractPresent", resolution.qualified().isPresent(), "failure", resolution.failure() == null ? null : resolution.failure().reason()))) {
            return resolution.failure() == null
                    ? Resolution.failed(trace.failure(Rule.ACCEPTANCE_CONTRACT, "COMPILE_BINDING_MISSING", "qualified task contract is required"))
                    : resolution;
        }
        var mismatch = binding.get().validate(resolution.qualified().orElseThrow(), trace);
        return mismatch.map(Resolution::failed).orElse(resolution);
    }

    private static OccurrenceRead lastContractGateOccurrence(LedgerSession files, Path eventsPath, ContractQualificationTrace trace) throws IOException {
        if (!trace.exists(files, eventsPath)) {
            return new OccurrenceRead(null, null);
        }
        ContractGateOccurrence last = null;
        long previous = -1;
        try {
            for (var line : files.readLines(eventsPath)) {
                if (line.isBlank()) {
                    continue;
                }
                final Map<String, Object> event;
                try {
                    event = OBJECT_MAPPER.readValue(line, MAP_TYPE);
                } catch (Exception failure) {
                    return new OccurrenceRead(null, trace.failure(Rule.EVENT_PARSE,
                            "EVENT_HISTORY_INVALID", "workflow event history contains invalid JSON"));
                }
                var eventId = text(event.get("event_id"));
                var matcher = WORKFLOW_EVENT_ID.matcher(eventId);
                if (!matcher.matches()) {
                    if (trace.rejects(Rule.EVENT_ID, ("node_routed".equals(text(event.get("event_type")))), values("event", event))) {
                        return new OccurrenceRead(null, trace.failure(Rule.EVENT_ID,
                                "EVENT_HISTORY_INVALID", "routing event does not have a workflow event id"));
                    }
                    continue;
                }
                var sequence = Long.parseLong(matcher.group(1));
                if (trace.eventCheck(Rule.EVENT_ORDER, (sequence <= previous), values("sequence", sequence, "previous", previous), trace.route(eventsPath, line, eventId, sequence))) {
                    return new OccurrenceRead(null, trace.failure(Rule.EVENT_ORDER,
                            "EVENT_HISTORY_INVALID", "workflow event history is not strictly ordered"));
                }
                previous = sequence;
                if (!"node_routed".equals(text(event.get("event_type")))) {
                    continue;
                }
                var payload = map(event.get("payload"));
                if (!"gate.contract_review".equals(text(payload.get("to")))) {
                    continue;
                }
                var gateId = text(payload.get("gate_id"));
                var nodeInstanceId = text(payload.get("node_instance_id"));
                var sourceNodeInstanceId = text(payload.get("source_node_instance_id"));
                var from = text(payload.get("from"));
                if (trace.eventCheck(Rule.EVENT_SOURCE, (gateId.isBlank() || nodeInstanceId.isBlank()
                        || sourceNodeInstanceId.isBlank() || from.isBlank()), values("gateId", gateId, "nodeInstanceId", nodeInstanceId, "sourceNodeInstanceId", sourceNodeInstanceId, "from", from), trace.route(eventsPath, line, eventId, sequence))) {
                    return new OccurrenceRead(null, trace.failure(Rule.EVENT_SOURCE,
                            "GATE_SOURCE_MISSING", "contract gate routing event has incomplete source lineage"));
                }
                last = new ContractGateOccurrence(eventId, gateId, nodeInstanceId, sourceNodeInstanceId, from);
            }
            return new OccurrenceRead(last, null);
        } catch (IOException failure) {
            return new OccurrenceRead(null, trace.failure(Rule.EVENT_READ,
                    "EVENT_HISTORY_UNREADABLE", "cannot read workflow event history"));
        }
    }

    private static GateRead readGate(LedgerSession files, Path path, ContractQualificationTrace trace) throws IOException {
        if (trace.rejects(Rule.GATE_DOCUMENT_PRESENT, (!trace.exists(files, path)), values("path", path.toString()), path)) {
            return new GateRead(null, trace.failure(Rule.GATE_DOCUMENT_PRESENT, "GATE_DOCUMENT_MISSING", "contract approval gate document is missing"));
        }
        try {
            return new GateRead(trace.read(files, path, GateDocument.class), null);
        } catch (Exception failure) {
            return new GateRead(null, trace.failure(Rule.GATE_DOCUMENT_PARSE, "GATE_DOCUMENT_INVALID", "contract approval gate document cannot be parsed"));
        }
    }

    private static NodeInstanceDocument node(List<NodeInstanceDocument> nodes, String nodeInstanceId) {
        var matches = nodes.stream().filter(node -> node.nodeInstanceId().equals(nodeInstanceId)).toList();
        return matches.size() == 1 ? matches.getFirst() : null;
    }

    private static NodeInstanceDocument newerCompletedCompile(
            List<NodeInstanceDocument> nodes,
            NodeInstanceDocument approved
    ) {
        if (approved.finishedAt() == null) {
            return approved;
        }
        return nodes.stream()
                .filter(node -> !node.nodeInstanceId().equals(approved.nodeInstanceId()))
                .filter(node -> "contract.compile".equals(node.handlerType()))
                .filter(node -> "EXECUTION".equals(text(node.result().get("contract_phase"))))
                .filter(node -> node.status() == NodeStatus.COMPLETED)
                .filter(node -> node.finishedAt() == null || !node.finishedAt().isBefore(approved.finishedAt()))
                .findFirst()
                .orElse(null);
    }

    private static Nodes readNodes(LedgerSession files, Path nodesDir, ContractQualificationTrace trace) throws IOException {
        if (!trace.exists(files, nodesDir)) {
            return new Nodes(List.of(), null);
        }
        var nodes = new ArrayList<NodeInstanceDocument>();
        try (var paths = files.list(nodesDir).stream()) {
            for (var path : paths.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted().toList()) {
                try {
                    nodes.add(trace.read(files, path, NodeInstanceDocument.class));
                } catch (Exception failure) {
                    return new Nodes(List.of(), trace.failure(Rule.NODE_DOCUMENT_READ,
                            "NODE_LEDGER_UNREADABLE", "cannot read node ledger document " + path.getFileName()));
                }
            }
            return new Nodes(List.copyOf(nodes), null);
        } catch (IOException failure) {
            return new Nodes(List.of(), trace.failure(Rule.NODE_DIRECTORY_READ, "NODE_LEDGER_UNREADABLE", "cannot list node ledger"));
        }
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> raw ? (Map<String, Object>) raw : Map.of();
    }

    private static List<String> textList(Object value) {
        return value instanceof List<?> list ? list.stream().map(String::valueOf).toList() : List.of();
    }

    private static long longValue(Object value) { return value instanceof Number n ? n.longValue() : -1; }

    private static int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(text(value));
        } catch (NumberFormatException ignored) {
            return Integer.MIN_VALUE;
        }
    }

    record QualifiedTaskContract(
            String rawJson,
            TaskContract contract,
            Map<String, Object> document,
            String sha256,
            String contractRef,
            String rawResolutionJson,
            Map<String, Object> resolutionDocument,
            String resolutionSha256,
            String resolutionRef,
            String compileNodeInstanceId,
            String compileNodeRef,
            String approvalGateRef,
            String approvalEventId
    ) {
        String wrappedPayload() {
            return TypedContextPayloadBoundary.wrap("```json\n" + rawJson + "\n```");
        }
    }

    record Failure(String reason, String message, Check check) {
        /** Sibling consumers outside this family explicitly have no observed rule check. */
        static Failure of(String reason, String message) { return new Failure(reason, message, null); }
    }

    record CandidateProjection(TaskContract contract, Map<String, Object> document, String rawJson,
                               Map<String, Object> resolutionDocument, String rawResolutionJson,
                               String contractRef, String resolutionRef, Decision decisionChecks) {}





    record Resolution(Optional<QualifiedTaskContract> qualified, Failure failure) {
        static Resolution absent() {
            return new Resolution(Optional.empty(), null);
        }

        static Resolution qualified(QualifiedTaskContract contract) {
            return new Resolution(Optional.of(contract), null);
        }

        static Resolution failed(Failure failure) {
            return new Resolution(Optional.empty(), failure);
        }
    }

    private record Nodes(List<NodeInstanceDocument> nodes, Failure failure) {
    }

    private record ContractGateOccurrence(
            String eventId,
            String gateId,
            String nodeInstanceId,
            String sourceNodeInstanceId,
            String fromNodeDefinitionId
    ) {
    }

    private record OccurrenceRead(ContractGateOccurrence occurrence, Failure failure) {
    }

    private record GateRead(GateDocument gate, Failure failure) {
    }

    private record AcceptanceContextBinding(String sha256, String compileNodeRef) {
        static Optional<AcceptanceContextBinding> fromRefs(
                Path workspaceRoot,
                String workstreamId,
                String nodeId,
                List<String> inputRefs,
                ObjectMapper objectMapper, ContractQualificationTrace trace
        ) {
            var root = workspaceRoot.toAbsolutePath().normalize();
            var contextDir = root.resolve(".agent/workstreams").resolve(workstreamId).resolve("context").normalize();
            var matches = new ArrayList<Map<String, Object>>();
            for (var ref : inputRefs == null ? List.<String>of() : inputRefs) {
                var path = root.resolve(ref).normalize();
                if (!path.startsWith(contextDir)
                        || !contextDir.equals(path.getParent())
                        || !path.getFileName().toString().endsWith(".json")
                        || !Files.isRegularFile(path)
                        || Files.isSymbolicLink(path)) {
                    continue;
                }
                try {
                    var document = objectMapper.readValue(path.toFile(), MAP_TYPE);
                    if ("acceptance".equals(text(document.get("profile")))
                            && nodeId.equals(text(document.get("targetNode")))) {
                        matches.add(document);
                    }
                } catch (IOException failure) {
                    throw trace.contextRejection(Rule.ACCEPTANCE_CONTEXT_READ, "cannot read routed acceptance context metadata", failure);
                }
            }
            if (matches.isEmpty()) {
                return Optional.empty();
            }
            if (trace.rejects(Rule.ACCEPTANCE_CONTEXT_UNIQUE, (matches.size() != 1), values("contexts", matches))) {
                throw trace.contextRejection(Rule.ACCEPTANCE_CONTEXT_UNIQUE, "multiple routed acceptance context metadata documents", null);
            }
            var document = matches.getFirst();
            var sha256 = text(document.get("taskContractSha256"));
            var compileNodeRef = text(document.get("taskContractCompileNodeRef"));
            if (trace.rejects(Rule.ACCEPTANCE_CONTEXT_BINDING, (sha256.isBlank() || compileNodeRef.isBlank()), values("sha", sha256, "compilerRef", compileNodeRef))) {
                throw trace.contextRejection(Rule.ACCEPTANCE_CONTEXT_BINDING, "routed acceptance context metadata has no task contract binding", null);
            }
            return Optional.of(new AcceptanceContextBinding(sha256, compileNodeRef));
        }

        Optional<Failure> validate(QualifiedTaskContract qualified, ContractQualificationTrace trace) {
            if (trace.rejects(Rule.ACCEPTANCE_HASH, (!sha256.equals(qualified.sha256())), values("routedSha", sha256, "qualifiedSha", qualified.sha256()))) {
                return Optional.of(trace.failure(Rule.ACCEPTANCE_HASH,
                        "CONTEXT_BINDING_HASH_MISMATCH", "routed context SHA does not match current task contract"));
            }
            if (trace.rejects(Rule.ACCEPTANCE_LINEAGE, (!compileNodeRef.equals(qualified.compileNodeRef())), values("routedCompilerRef", compileNodeRef, "qualifiedCompilerRef", qualified.compileNodeRef()))) {
                return Optional.of(trace.failure(Rule.ACCEPTANCE_LINEAGE,
                        "CONTEXT_BINDING_LINEAGE_MISMATCH", "routed context compile ref does not match current binding"));
            }
            return Optional.empty();
        }
    }
}
