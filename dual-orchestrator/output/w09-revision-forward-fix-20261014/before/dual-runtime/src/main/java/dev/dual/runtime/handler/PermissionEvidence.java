package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dual.core.contract.TaskContract;
import dev.dual.core.execution.AdapterSignal;
import dev.dual.core.execution.AdapterSignalType;
import dev.dual.core.execution.AgentExecutionEvidence.*;
import dev.dual.core.workflow.NodeResult;
import dev.dual.ledger.file.WorkflowFileLedger;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Program-owned association of a denied request with approved obligations; never judges a shell command. */
final class PermissionEvidence {
    static final String FACT = "permission_assessments";
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private static final String RULE = "permission_nonessential.v1";
    private PermissionEvidence() {}

    static NodeResult assess(WorkflowFileLedger ledger, Path root, String ws, String node, String definition,
                             NodeResult result, AgentResultEnvelope.AgentResult artifact) {
        var signals = AgentRunMetadataFinalizer.resultAdapterSignals(result).stream()
                .filter(s -> s.type() == AdapterSignalType.PERMISSION_BLOCKED && !s.superseded()).toList();
        // Even an empty program assessment replaces any externally supplied candidate fact.
        var assessments = new ArrayList<PermissionAssessment>();
        for (var signal : signals) assessments.add(assessOne(ledger, root, ws, node, definition, result, artifact, signal));
        var facts = new LinkedHashMap<String,Object>(result.result()); facts.put(FACT, List.copyOf(assessments));
        return new NodeResult(result.outcome(), result.status(), result.outputRefs(), facts, result.errorMessage());
    }

    private static PermissionAssessment assessOne(WorkflowFileLedger ledger, Path root, String ws, String node,
                                                  String definition, NodeResult result,
                                                  AgentResultEnvelope.AgentResult artifact, AdapterSignal signal) {
        TaskContract.Reference contractRef = null;
        var criteria = new ArrayList<String>();
        var refs = new LinkedHashSet<TaskContract.Reference>();
        String run = String.valueOf(result.result().getOrDefault("run_id", "")), execution = "", declaration = "";
        var disposition = PermissionDisposition.UNKNOWN;
        try {
            var qualified = TaskContractProjectionVerifier.resolve(ledger, root, ws, TaskContract.Phase.EXECUTION)
                    .qualified().orElseThrow(() -> new IOException("no approved EXECUTION"));
            contractRef = new TaskContract.Reference(qualified.contractRef(), qualified.sha256());
            criteria.addAll(qualified.contract().verificationBindings().stream().map(TaskContract.VerificationBinding::criterionId).toList());
            var current = VerificationEvidence.load(ledger, root, ws, node, result, "CURRENT");
            execution = current.execution(); run = current.run();
            if (current.permissionSignals() == null || Collections.frequency(current.permissionSignals(), signal) != 1
                    || !current.baseline().contract().equals(contractRef) || !validOperation(root, current, signal))
                throw new IOException("denied operation is not uniquely bound to this execution");
            refs.add(JSON.convertValue(result.result().get(VerificationEvidence.FACT), TaskContract.Reference.class));
            var op = signal.operationRef();
            var declared = qualified.contract().nonessentialOperationDeclarations().stream()
                    .filter(d -> definition.equals(d.nodeDefinitionId()) && op.kind().equals(d.operationKind())
                            && op.parametersSha256().equals(d.operationParametersSha256())
                            && new HashSet<>(criteria).equals(new HashSet<>(d.criterionIds()))).toList();
            var checks = VerificationEvidence.checks(artifact);
            if (checks != null && checks.stream().anyMatch(c -> c != null && "CURRENT".equals(c.executionRef())
                    && c.toolCallIds() != null && c.toolCallIds().contains(op.toolCallId()))) {
                disposition = PermissionDisposition.REQUIRED;
                throw new IOException("denied tool is explicitly selected by a criterion");
            }
            boolean verified = "SELF_TEST_COMPLETE".equals(artifact.decision()) || "ACCEPT".equals(artifact.decision())
                    || "ACCEPT_WITH_NOTES".equals(artifact.decision());
            if (verified && checks != null && checks.size() == criteria.size()) {
                // verify() has already checked the approved requirements and immutable source identities.
                // A denied call selected by any criterion cannot be declared nonessential afterwards.
                for (var check : checks) {
                    var binding = qualified.contract().verificationBindings().stream()
                            .filter(b -> b.criterionId().equals(check.criterionId())).findFirst().orElseThrow();
                    if (binding.evidenceKind() == TaskContract.EvidenceKind.OPERATOR_DECLARATION_WITH_MATERIAL) {
                        refs.addAll(check.materialRefs()); continue;
                    }
                    var observed = VerificationEvidence.load(ledger, root, ws, node, result, check.executionRef());
                    var selected = check.toolCallIds().isEmpty() ? observed.tools().stream().filter(ToolObservation::hasExecutionResult)
                            .map(ToolObservation::toolCallId).toList() : check.toolCallIds();
                    for (var tool : observed.tools().stream().filter(t -> selected.contains(t.toolCallId())).toList()) {
                        if (op.sessionId().equals(tool.sessionId()) && op.promptId().equals(tool.promptId()) && op.toolCallId().equals(tool.toolCallId())) {
                            disposition = PermissionDisposition.REQUIRED;
                            throw new IOException("denied tool is an evidence producer for an approved criterion");
                        }
                        if (!"completed".equals(tool.nativeStatus())) throw new IOException("required tool did not complete");
                    }
                    refs.add("CURRENT".equals(check.executionRef())
                            ? JSON.convertValue(result.result().get(VerificationEvidence.FACT), TaskContract.Reference.class)
                            : new TaskContract.Reference(check.executionRef(), TaskContractArtifactWriter.sha256(root.resolve(check.executionRef()))));
                }
                disposition = PermissionDisposition.PROVEN_NONESSENTIAL;
            } else if (declared.size() == 1) {
                var d = declared.getFirst();
                // The contract verifier rechecks the exact frozen node inputs, not today's mutable config.
                VerificationEvidence.read(root, d.nodeContractRef());
                declaration = d.id(); refs.add(d.nodeContractRef()); disposition = PermissionDisposition.PROVEN_NONESSENTIAL;
            }
        } catch (IOException | IllegalArgumentException invalid) {
            // Missing or ambiguous association remains the original permission block, never a JSON failure.
        }
        return new PermissionAssessment(signal, contractRef, criteria, List.copyOf(refs), RULE, disposition, declaration, run, execution);
    }

    private static boolean validOperation(Path root, VerificationEvidence.Observation observed, AdapterSignal signal) throws IOException {
        var op = signal.operationRef();
        if (op == null || !op.complete() || !signal.sessionId().equals(op.sessionId())
                || signal.observedSequence() != op.decisionSequence() || !List.of("read", "search").contains(op.kind())) return false;
        var request = JSON.readTree(op.requestJson()); var response = JSON.readTree(op.decisionJson());
        var params = request.path("params"); var tool = params.path("toolCall");
        if (!"session/request_permission".equals(request.path("method").asText())
                || !op.requestId().equals(request.path("id").asText()) || !op.requestId().equals(response.path("id").asText())
                || !op.sessionId().equals(params.path("sessionId").asText())
                || !op.toolCallId().equals(tool.path("toolCallId").asText()) || !op.kind().equals(tool.path("kind").asText())
                || !op.parametersSha256().equals(AdapterSignal.parametersHash(tool.get("rawInput")))) return false;
        var outcome = response.path("result").path("outcome");
        var option = outcome.path("optionId").asText();
        if (!"selected".equals(outcome.path("outcome").asText())) return false;
        var matches = new ArrayList<com.fasterxml.jackson.databind.JsonNode>();
        for (var entry : params.path("options")) if (option.equals(entry.path("optionId").asText())) matches.add(entry);
        if (matches.size() != 1 || !List.of("reject_once", "reject_always").contains(matches.getFirst().path("kind").asText())) return false;
        var calls = observed.tools().stream().filter(t -> op.sessionId().equals(t.sessionId()) && op.toolCallId().equals(t.toolCallId())).toList();
        if (calls.size() != 1 || !op.promptId().equals(calls.getFirst().promptId()) || calls.getFirst().invalid() || !op.kind().equals(calls.getFirst().kind())
                || !op.parametersSha256().equals(AdapterSignal.parametersHash(calls.getFirst().input()))) return false;
        // Only structured, canonical product-file reads are in this bounded rule. No command classification.
        var raw = tool.path("rawInput");
        var target = raw.hasNonNull("path") ? raw.path("path").asText() : raw.path("file_path").asText();
        var realRoot = root.toRealPath();
        var supplied = Path.of(target);
        var relative = target;
        if (supplied.isAbsolute()) {
            if (!supplied.normalize().equals(supplied)) return false;
            var workspace = root.toAbsolutePath().normalize();
            var base = supplied.startsWith(workspace) ? workspace : realRoot;
            if (!supplied.startsWith(base)) return false;
            relative = base.relativize(supplied).toString();
        }
        if (!dev.dual.core.contract.TaskContractValidator.isCanonicalPath(relative)) return false;
        var path = realRoot.resolve(relative);
        return Files.isRegularFile(path) && path.toRealPath().equals(path);
    }
}
