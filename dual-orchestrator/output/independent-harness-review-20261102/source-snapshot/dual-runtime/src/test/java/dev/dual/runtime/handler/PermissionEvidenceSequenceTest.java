package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dual.core.execution.*;
import dev.dual.core.execution.AgentExecutionEvidence.*;
import dev.dual.core.workflow.*;
import dev.dual.runtime.WorkflowCommand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real offline workflow/approval/program file observations; ACP messages here are synthetic protocol fixtures. */
class PermissionEvidenceSequenceTest {
    @TempDir Path root;
    static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    static final String SELF = "implement.claude_self_test_report";
    static final String IMPLEMENT = "implement.claude_implement";

    @Test void independentRequiredEvidenceAllowsDeniedNonessentialReadAndFixedCompletionReload() throws Exception {
        for (var variant : List.of("contract", "small", "full", "terminal-delivery", "worker")) {
        var f = fixture(variant.equals("worker") ? "contract" : variant, null);
        if (variant.equals("worker")) f.enableWorker();
        f.permissionSignals = (context, tools) -> context.definition().id().equals(SELF)
                ? List.of(denial(tools, "optional-read", "README.md")) : List.of();
        f.until(SELF);
        var id = f.ledger.loadCurrent(f.paths).activeNodeInstanceIds().getFirst();
        f.step();
        var completed = f.ledger.loadNode(f.paths, id);
        assertEquals(NodeStatus.COMPLETED, completed.status());
        var execution = f.ledger.latestNodeExecution(f.paths, completed).orElseThrow();
        var fixed = AgentRunCommitProtocol.readCompletion(f.paths, execution);
        var assessments = JSON.valueToTree(fixed.result().get(PermissionEvidence.FACT));
        assertEquals("PROVEN_NONESSENTIAL", assessments.get(0).path("disposition").asText());
        assertEquals("SUCCESS_WITH_WARNINGS", JSON.valueToTree(fixed.result().get("agent_execution_outcome")).path("status").asText());
        assertNotEquals(SELF, f.currentNode());
        if (variant.equals("contract")) {
            var log = f.root.resolve("cold-permission.log");
            var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                    "-cp", System.getProperty("java.class.path"), ColdPermissionReader.class.getName(), f.root.toString(), f.ws, id)
                    .redirectErrorStream(true).redirectOutput(log.toFile()).start();
            assertTrue(process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(0, process.exitValue(), Files.readString(log));
        }

        // A newly constructed reader consumes the fixed bytes, without rerunning the fixture provider.
        var reader = dev.dual.runtime.RuntimeTestLedger.ledger();
        assertEquals(fixed, AgentRunCommitProtocol.readCompletion(f.paths,
                reader.latestNodeExecution(f.paths, reader.loadNode(f.paths, id)).orElseThrow()));
        }
    }

    @Test void requiredToolCannotBeDowngradedEvenWithCompleteArtifact() throws Exception {
        var f = fixture("contract", null);
        f.permissionSignals = (context, tools) -> context.definition().id().equals(SELF)
                ? List.of(denial(tools, "read-product", "tool.py")) : List.of();
        assertBlocked(f, SELF, "REQUIRED");
    }

    @Test void absoluteNativeFilePathsPreserveLegalReadsAndRejectOutsideOrControlTargets() throws Exception {
        for (var mode : List.of("inline", "worker")) {
            var f = fixture("contract", null);
            if (mode.equals("worker")) f.enableWorker();
            f.permissionSignals = (context, tools) -> context.definition().id().equals(SELF)
                    ? List.of(denial(tools, "optional-read", JSON.valueToTree(Map.of("file_path",
                            f.root.resolve("README.md").toAbsolutePath().toString())))) : List.of();
            f.until(SELF); var id = f.ledger.loadCurrent(f.paths).activeNodeInstanceIds().getFirst(); f.step();
            var node = f.ledger.loadNode(f.paths, id);
            assertEquals(NodeStatus.COMPLETED, node.status());
            assertEquals("SUCCESS_WITH_WARNINGS", JSON.valueToTree(node.result().get("agent_execution_outcome")).path("status").asText());
        }
        var outside = root.resolve("outside-readable.md"); Files.writeString(outside, "readable outside this workstream workspace");
        for (var targetKind : List.of("outside", "control")) {
            var f = fixture("contract", null);
            var target = targetKind.equals("outside") ? outside
                    : f.root.resolve(".agent/workstreams/" + f.ws + "/current.json");
            f.permissionSignals = (context, tools) -> context.definition().id().equals(SELF)
                    ? List.of(denial(tools, "optional-read", JSON.valueToTree(Map.of("file_path", target.toAbsolutePath().toString()))))
                    : List.of();
            assertBlocked(f, SELF, "UNKNOWN");
        }
    }

    @Test void workerAlsoRefusesRequiredDeniedTool() throws Exception {
        var f = fixture("contract", null); f.enableWorker();
        f.permissionSignals = (context, tools) -> context.definition().id().equals(SELF)
                ? List.of(denial(tools, "read-product", "tool.py")) : List.of();
        assertBlocked(f, SELF, "REQUIRED");
    }

    @Test void crossSessionMissingRawAssociationAndProtectedTargetsRemainBlocked() throws Exception {
        for (var variant : List.of("contract", "small", "full", "terminal-delivery")) {
            var f = fixture(variant, null);
            f.permissionSignals = (context, tools) -> {
                if (!context.definition().id().equals(SELF)) return List.of();
                var good = denial(tools, "optional-read", "README.md");
                return List.of(new AdapterSignal(good.type(), good.message(), good.observedSequence(), good.source(),
                        "foreign-session", false, good.operationRef()));
            };
            assertBlocked(f, SELF, "UNKNOWN");
        }
        var f = fixture("contract", null);
        f.permissionSignals = (context, tools) -> context.definition().id().equals(SELF)
                ? List.of(denial(tools, "control-read", ".agent/workstreams/x/current.json")) : List.of();
        assertBlocked(f, SELF, "UNKNOWN");
    }

    @Test void explicitNonessentialDeclarationRequiresExactExecutionApprovalAndRequestParameters() throws Exception {
        var f = fixture("contract", IMPLEMENT);
        f.until("gate.contract_review");
        var candidate = TaskContractProjectionVerifier.executionCandidate(f.ledger, f.root, f.ws,
                f.ledger.loadCurrent(f.paths).activeNodeInstanceIds().getFirst(), true);
        // The declaration is visible in the candidate's canonical JSON and human-readable material.
        assertEquals(1, candidate.contract().nonessentialOperationDeclarations().size());
        f.step(); // Open the real human packet; no approval yet.
        var gate = f.ledger.loadNode(f.paths, f.ledger.loadCurrent(f.paths).activeNodeInstanceIds().getFirst());
        var packetRef = ".agent/workstreams/" + f.ws + "/reviews/" + gate.nodeInstanceId() + ".json";
        var packet = JSON.readTree(f.root.resolve(packetRef).toFile());
        assertTrue(packet.path("taskContract").path("nonessentialOperationDeclarations").toString().contains("optional-read"));
        assertFalse(f.calls.contains(IMPLEMENT));
        f.approve();
        f.permissionSignals = (context, tools) -> context.definition().id().equals(IMPLEMENT)
                ? List.of(denial(tools, "optional-read", "README.md")) : List.of();
        f.until(IMPLEMENT); var id = f.ledger.loadCurrent(f.paths).activeNodeInstanceIds().getFirst(); f.step();
        var node = f.ledger.loadNode(f.paths, id);
        assertEquals(NodeStatus.COMPLETED, node.status());
        assertEquals("optional-read", JSON.valueToTree(node.result().get(PermissionEvidence.FACT)).get(0).path("declarationId").asText());
        f.until("acceptance.generate_final_report");
        var reportId = f.ledger.loadCurrent(f.paths).activeNodeInstanceIds().getFirst(); f.step();
        var report = f.ledger.loadNode(f.paths, reportId);
        assertTrue(report.outputRefs().stream().filter(r -> r.endsWith(".md")).anyMatch(r -> {
            try { return Files.readString(f.root.resolve(r)).contains("optional-read"); }
            catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
        }));
        var wrong = fixture("contract", IMPLEMENT);
        wrong.permissionSignals = (context, tools) -> context.definition().id().equals(IMPLEMENT)
                ? List.of(denial(tools, "optional-read", "tool.py")) : List.of();
        assertBlocked(wrong, IMPLEMENT, "UNKNOWN");
    }

    @Test void firstVersionPartialDeclarationCannotWaiveOtherApprovedObligations() throws Exception {
        for (var mode : List.of("inline", "worker")) {
            var f = fixture("contract", IMPLEMENT, List.of("AC-1"));
            if (mode.equals("worker")) f.enableWorker();
            f.until("gate.contract_review");
            var candidate = TaskContractProjectionVerifier.executionCandidate(f.ledger, f.root, f.ws,
                    f.ledger.loadCurrent(f.paths).activeNodeInstanceIds().getFirst(), true);
            assertEquals(Set.of("AC-1", "AC-2"), new HashSet<>(candidate.contract().verificationBindings()
                    .stream().map(dev.dual.core.contract.TaskContract.VerificationBinding::criterionId).toList()));
            assertEquals(List.of("AC-1"), candidate.contract().nonessentialOperationDeclarations().getFirst().criterionIds());
            f.step();
            assertFalse(f.calls.contains(IMPLEMENT));
            f.approve();
            f.permissionSignals = (context, tools) -> context.definition().id().equals(IMPLEMENT)
                    ? List.of(denial(tools, "optional-read", "README.md")) : List.of();
            assertBlocked(f, IMPLEMENT, "UNKNOWN");
        }
    }

    public static class ColdPermissionReader {
        public static void main(String[] args) throws Exception {
            var ledger = dev.dual.runtime.RuntimeTestLedger.ledger();
            var paths = new dev.dual.ledger.file.WorkstreamPaths(Path.of(args[0]), new dev.dual.core.id.DualId(args[1]));
            var execution = ledger.latestNodeExecution(paths, ledger.loadNode(paths, args[2])).orElseThrow();
            var fixed = AgentRunCommitProtocol.readCompletion(paths, execution);
            if (!"PROVEN_NONESSENTIAL".equals(JSON.valueToTree(fixed.result().get(PermissionEvidence.FACT)).get(0).path("disposition").asText())
                    || !"SUCCESS_WITH_WARNINGS".equals(JSON.valueToTree(fixed.result().get("agent_execution_outcome")).path("status").asText()))
                throw new IllegalStateException("permission evidence did not survive cold read");
        }
    }

    private AcceptanceRepairSequenceTest.Fixture fixture(String variant, String declaredNode) throws Exception {
        return fixture(variant, declaredNode, List.of("AC-1", "AC-2"));
    }

    private AcceptanceRepairSequenceTest.Fixture fixture(String variant, String declaredNode, List<String> declaredCriteria) throws Exception {
        return new AcceptanceRepairSequenceTest.Fixture(root.resolve(UUID.randomUUID().toString()),
                "VERIFICATION_INSUFFICIENT", "tool.py", "ws-permission", variant, dev.dual.runtime.RuntimeTestLedger.ledger(), definition -> {
            if (declaredNode == null) return definition;
            var nodes = definition.nodes().stream().map(node -> {
                if (!declaredNode.equals(node.id())) return node;
                var config = new LinkedHashMap<String,Object>(node.config());
                config.put("nonessential_operation_declarations", List.of(Map.of("id", "optional-read", "operation_kind", "read",
                        "operation_parameters_sha256", AdapterSignal.parametersHash(JSON.valueToTree(Map.of("path", "README.md"))),
                        "criterion_ids", declaredCriteria, "rationale", "Optional background read, no declared obligation depends on it",
                        "rule_identity", "permission_nonessential.v1")));
                return new NodeDefinition(node.id(), node.kind(), node.handlerType(), config);
            }).toList();
            return new WorkflowDefinition(definition.workflowId(), definition.version(), definition.entryNodeId(), nodes, definition.edges());
        });
    }

    private void assertBlocked(AcceptanceRepairSequenceTest.Fixture f, String target, String disposition) throws Exception {
        f.until(target); var id = f.ledger.loadCurrent(f.paths).activeNodeInstanceIds().getFirst();
        f.runtime.resume(new WorkflowCommand(f.root, f.ws));
        if (f.worker != null) f.worker.runOne(new WorkflowCommand(f.root, f.ws));
        var node = f.ledger.loadNode(f.paths, id);
        assertEquals(NodeStatus.FAILED, node.status());
        assertEquals("permission_blocked", JSON.valueToTree(node.result().get("agent_execution_outcome")).path("primaryReason").asText(), node.toString());
        assertEquals(disposition, JSON.valueToTree(node.result().get(PermissionEvidence.FACT)).get(0).path("disposition").asText());
        assertEquals(WorkflowStatus.WAITING_FOR_RECOVERY, f.ledger.loadCurrent(f.paths).status());
        assertTrue(node.outputRefs().stream().noneMatch(ref -> ref.endsWith(".contract.json")));
    }

    private static AdapterSignal denial(List<ToolObservation> tools, String call, String path) {
        return denial(tools, call, JSON.valueToTree(Map.of("path", path)));
    }

    private static AdapterSignal denial(List<ToolObservation> tools, String call, com.fasterxml.jackson.databind.JsonNode input) {
        if (tools.stream().noneMatch(t -> call.equals(t.toolCallId())))
            tools.add(new ToolObservation("fixture-session", call, "Read", "read", input, null, "pending", null, null, "", false, List.of(), "fixture-prompt"));
        var request = JSON.valueToTree(Map.of("id", 7, "method", "session/request_permission", "params", Map.of("sessionId", "fixture-session",
                "toolCall", Map.of("toolCallId", call, "kind", "read", "rawInput", input),
                "options", List.of(Map.of("optionId", "opaque-option", "kind", "reject_once"))))).toString();
        var response = JSON.valueToTree(Map.of("id", 7, "result", Map.of("outcome", Map.of("outcome", "selected", "optionId", "opaque-option")))).toString();
        var op = new AdapterSignal.OperationRef("fixture-session", "fixture-prompt", "7", call, 2, 3, "read",
                AdapterSignal.parametersHash(input), request, AdapterSignal.digest(request), response, AdapterSignal.digest(response));
        return new AdapterSignal(AdapterSignalType.PERMISSION_BLOCKED, "read denied", 3, "acpx_stream", "fixture-session", false, op);
    }
}
