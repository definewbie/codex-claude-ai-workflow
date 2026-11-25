package dev.dual.adapter.acpx;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.dual.core.execution.ProcessResult;
import dev.dual.core.execution.ProcessUsability;
import dev.dual.core.id.DualId;
import dev.dual.core.workflow.*;
import dev.dual.flow.liteflow.FlowKernelProvider;
import dev.dual.ledger.file.*;
import dev.dual.runtime.*;
import dev.dual.runtime.handler.*;
import dev.dual.runtime.lifecycle.WorkflowDefinitionLoader;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.*;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** User-flow expectations: a positive recommendation never removes the human's right to revise. */
class HumanRequirementContinuationSequenceTest {
    static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule());
    @TempDir Path root;

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void humanRevisionReachesSameAuthorReReviewAndBothApprovals(boolean queued) throws Exception {
        for (var recommendation : List.of("READY_FOR_PRODUCT_REVIEW", "BLOCKED")) {
            var f = new Fixture(root.resolve(recommendation), queued);
            f.transport.recommendation = recommendation;
            f.untilGate();
            assertEquals("gate.human_requirement_review", f.active().nodeDefinitionId(), f.active().toString());
            var parent = new TaskContractRevisionContextResolver(AcpxTestLedger.ledger());
            var firstReview = f.active().inputRefs();
            var note = "Keep the existing scope and make the acceptance wording precise";
            f.ledger.resolveOpenGate(f.paths, f.definition, "revise", note);
            assertThrows(Exception.class, () -> f.ledger.resolveOpenGate(f.paths, f.definition, "revise", note));
            f.transport.recommendation = "READY_FOR_PRODUCT_REVIEW";
            f.untilGate();
            assertEquals("gate.human_requirement_review", f.active().nodeDefinitionId());
            assertNotEquals(firstReview, f.active().inputRefs());
            assertEquals(List.of("intake.claude_brainstorm", "intake.codex_product_review",
                    "intake.claude_brainstorm", "intake.codex_product_review"), f.transport.calls);
            assertEquals(List.of(note), f.transport.obligations);
            assertTrue(f.transport.prompts.get(2).contains(note));
            var resolved = parent.resolveForIteration(f.root, f.ws, f.active().iterationId());
            assertTrue(resolved.valid(), resolved.errors().toString());
            f.ledger.resolveOpenGate(f.paths, f.definition, "approve", "Accept revised requirement");
            f.untilGate();
            assertEquals("gate.contract_review", f.active().nodeDefinitionId());
            assertThrows(AgentRunGovernanceContext.QualificationException.class, () -> f.qualifiedInput(),
                    "compiled proposal must not bypass the second approval");
            f.ledger.resolveOpenGate(f.paths, f.definition, "approve", "Approve compiled scope and criteria");
            assertTrue(f.qualifiedInput().trustedTaskContractPayload().isPresent());
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void missingRevisionAndClarificationNotesAreRejectedBeforeAnyWrite(boolean queued) throws Exception {
        var f = new Fixture(root, queued);
        f.untilGate();
        for (var action : List.of("revise", "clarify")) {
            if (action.equals("clarify")) {
                f.ledger.resolveOpenGate(f.paths, f.definition, "approve", "Accept");
                f.untilGate();
            }
            var before = f.snapshot();
            assertThrows(Exception.class, () -> f.ledger.resolveOpenGate(f.paths, f.definition, action, " "));
            assertEquals(before, f.snapshot(), "predictably invalid human action must make no durable write");
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void automaticRevisionAndContractClarificationReenterBothApprovalGates(boolean queued) throws Exception {
        var f = new Fixture(root, queued);
        f.transport.recommendation = "NEEDS_REVISION";
        f.untilGate();
        assertEquals("gate.human_requirement_review", f.active().nodeDefinitionId(), f.active().toString());
        assertEquals(List.of("Clarify acceptance wording"), f.transport.obligations);
        f.ledger.resolveOpenGate(f.paths, f.definition, "approve", "Accept revised requirement");
        f.untilGate();
        var oldGate = f.active().nodeInstanceId();
        f.ledger.resolveOpenGate(f.paths, f.definition, "clarify", "Make the criterion explicit");
        f.untilGate();
        assertEquals("gate.human_requirement_review", f.active().nodeDefinitionId(), f.active().toString());
        assertEquals(List.of("Clarify acceptance wording", "Make the criterion explicit"), f.transport.obligations);
        f.ledger.resolveOpenGate(f.paths, f.definition, "approve", "Accept clarification");
        f.untilGate();
        assertEquals("gate.contract_review", f.active().nodeDefinitionId());
        assertNotEquals(oldGate, f.active().nodeInstanceId());
        assertThrows(AgentRunGovernanceContext.QualificationException.class, () -> f.qualifiedInput());
        f.ledger.resolveOpenGate(f.paths, f.definition, "approve", "Approve clarified contract");
        assertTrue(f.qualifiedInput().trustedTaskContractPayload().isPresent());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void invalidRevisionInputRetainsItsCauseThroughColdLedgerAndRecovery(boolean queued) throws Exception {
        var f = new Fixture(root, queued);
        f.untilGate();
        f.ledger.resolveOpenGate(f.paths, f.definition, "revise", "Clarify wording");
        var iteration = f.active().iterationId();
        Files.delete(f.paths.workstreamDir().resolve("iterations/" + iteration + ".json"));
        f.untilGate();
        var cold = new WorkflowFileLedger(FlowKernelProvider.create("liteflow"), Map.of("test_binary", "acpx-tests-v3"));
        var current = cold.loadCurrent(f.paths);
        var failed = cold.loadNode(f.paths, current.activeNodeInstanceIds().getFirst());
        assertEquals(WorkflowStatus.WAITING_FOR_RECOVERY, current.status());
        assertEquals(NodeStatus.FAILED, failed.status());
        assertEquals("TASK_CONTRACT_REVISION_CONTEXT_INVALID", failed.result().get("reason"));
        assertEquals(2, f.transport.calls.size(), "input rejection must never dispatch another provider");
        assertTrue(failed.outputRefs().isEmpty());
        assertNull(failed.runRef());
        assertFalse(failed.result().containsKey("parse_error"));
        var recovery = RecoveryAssessment.from(failed, f.root, f.ws);
        assertEquals("input_preparation_failure", recovery.classification());
        assertFalse(recovery.retrySafe());
        assertTrue(recovery.reason().contains("TASK_CONTRACT_REVISION_CONTEXT_INVALID"));
        assertFalse(recovery.reason().contains("JSON"));
        f.runtime().resume(new WorkflowCommand(f.root, f.ws));
        assertEquals(2, f.transport.calls.size());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void persistedHumanDecisionCannotBeForgedOrDetachedFromItsSubject(boolean queued) throws Exception {
        for (var corrupt : List.of("missing_gate", "action", "subject", "note", "iteration", "proposal_sha")) {
            var f = new Fixture(root.resolve(corrupt), queued);
            f.untilGate();
            var gateNode = f.active();
            var gate = f.ledger.loadOpenGateForNode(f.paths, gateNode.nodeInstanceId());
            f.ledger.resolveOpenGate(f.paths, f.definition, "revise", "Clarify wording");
            var path = f.paths.gateJson(gate.gateId());
            var json = (ObjectNode) JSON.readTree(path.toFile());
            switch (corrupt) {
                case "missing_gate" -> Files.delete(path);
                case "action" -> { ((ObjectNode) json.get("result")).put("human_action", "approved"); JSON.writeValue(path.toFile(), json); }
                case "subject" -> { ((ObjectNode) json.get("result")).putArray("subject_input_refs"); JSON.writeValue(path.toFile(), json); }
                case "note" -> { ((ObjectNode) json.get("result")).put("note", "different instructions"); JSON.writeValue(path.toFile(), json); }
                case "iteration" -> {
                    var nodePath = f.paths.nodeJson(gateNode.nodeInstanceId());
                    var node = (ObjectNode) JSON.readTree(nodePath.toFile()); node.put("iterationId", "iter-other"); JSON.writeValue(nodePath.toFile(), node);
                }
                case "proposal_sha" -> {
                    var reviewRef = gateNode.inputRefs().stream().filter(ref -> ref.endsWith(".contract.json")).findFirst().orElseThrow();
                    var review = JSON.readTree(f.root.resolve(reviewRef).toFile());
                    Files.writeString(f.root.resolve(review.at("/review_subject/artifact_ref").asText()), "{}");
                }
            }
            f.untilGate();
            assertEquals(2, f.transport.calls.size(), corrupt + " must be rejected before provider");
            assertEquals(WorkflowStatus.WAITING_FOR_RECOVERY, f.ledger.loadCurrent(f.paths).status(), corrupt);
            assertEquals("TASK_CONTRACT_REVISION_CONTEXT_INVALID", f.active().result().get("reason"), corrupt);
            assertTrue(f.active().outputRefs().isEmpty());
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void interruptedHumanDecisionFinishesOnceBeforeLegalContinuation(boolean queued) throws Exception {
        for (var point : List.of("before_manifest", "after_manifest", "after_replace", "before_append", "before_pending_delete")) {
            var f = new Fixture(root.resolve(point), queued);
            f.untilGate();
            var current = f.ledger.loadCurrent(f.paths);
            var broken = dev.dual.ledger.file.InterruptedTestLedger.at(point, Map.of("test_binary", "acpx-tests-v3"));
            assertThrows(java.io.IOException.class, () -> broken.resolveOpenGate(f.paths, f.definition, "revise", "Clarify wording"));
            assertEquals(2, f.transport.calls.size(), "local completion must not invoke either client");
            var cold = AcpxTestLedger.ledger();
            if (point.equals("before_manifest")) {
                assertEquals(current, cold.loadCurrent(f.paths));
                cold.resolveOpenGate(f.paths, f.definition, "revise", "Clarify wording");
            } else {
                assertThrows(LedgerSession.PendingCommitException.class, () -> cold.loadCurrent(f.paths));
                try (var write = cold.writeSession(f.paths, "finish saved human decision", "recover")) { write.commit(); }
            }
            assertThrows(Exception.class, () -> cold.resolveOpenGate(f.paths, f.definition, "revise", "Duplicate decision"));
            f.untilGate();
            assertEquals("gate.human_requirement_review", f.active().nodeDefinitionId());
            assertEquals(4, f.transport.calls.size(), "one revised author and one independent review, no repeated decision");
            assertEquals(List.of("Clarify wording"), f.transport.obligations);
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void nativeOnceChoiceContinuesThroughIndependentAcceptanceOrStopsWithoutPublication(boolean queued) throws Exception {
        for (var choice : List.of("allow", "deny")) {
            var f = new Fixture(root.resolve(choice), queued, "contract", true);
            f.transport.permissionChoice = choice;
            // Reach execution through the real requirement, plan/readiness and scope approval consumers.
            for (int i = 0; i < 8 && !f.transport.calls.contains("implement.claude_implement"); i++) {
                f.untilGate();
                if (f.transport.calls.contains("implement.claude_implement")) break;
                assertEquals(WorkflowStatus.WAITING_FOR_HUMAN, f.ledger.loadCurrent(f.paths).status());
                f.ledger.resolveOpenGate(f.paths, f.definition, "approve", "Approve the current reviewed candidate");
            }
            assertEquals(1, f.transport.permissionCommits);
            if (choice.equals("allow")) {
                assertEquals("gate.human_acceptance", f.active().nodeDefinitionId(), f.active().toString());
                assertTrue(f.transport.calls.indexOf("implement.claude_self_test_report") > f.transport.calls.indexOf("implement.claude_implement"));
                assertTrue(f.transport.calls.indexOf("acceptance.codex_verifier_review") > f.transport.calls.indexOf("implement.claude_self_test_report"));
                assertEquals(WorkflowStatus.WAITING_FOR_HUMAN, f.ledger.loadCurrent(f.paths).status(), "native allow cannot supply final delivery approval");
                assertEquals("Documented generic utility behavior\n", Files.readString(f.root.resolve("README.md")));
            } else {
                assertEquals(WorkflowStatus.WAITING_FOR_RECOVERY, f.ledger.loadCurrent(f.paths).status());
                assertTrue(f.active().outputRefs().stream().noneMatch(ref -> ref.endsWith(".contract.json")),
                        "diagnostic references may remain, but denial cannot publish a business sidecar: " + f.active());
                assertFalse(f.transport.calls.contains("acceptance.codex_verifier_review"));
                assertEquals("Generic utility\n", Files.readString(f.root.resolve("README.md")));
                assertFalse(f.active().result().toString().contains("MODEL_DID_NOT_RETURN_JSON"));
            }
        }
    }

    static final class Fixture {
        final Path root;
        final boolean queued;
        final String ws = "ws-human-continuation";
        final WorkstreamPaths paths;
        final WorkflowDefinition definition;
        WorkflowFileLedger ledger = new WorkflowFileLedger(FlowKernelProvider.create("liteflow"), Map.of("test_binary", "acpx-tests-v3"));
        final Transport transport;
        PromptManifestWriter manifestWriter = new PromptManifestWriter();
        dev.dual.core.contract.TaskContract.Reference readiness;
        List<dev.dual.core.contract.TaskContract.VerificationBinding> bindings = List.of();
        Fixture(Path root, boolean queued) throws Exception {
            this(root, queued, "contract");
        }
        Fixture(Path root, boolean queued, String variant) throws Exception { this(root, queued, variant, false); }
        Fixture(Path root, boolean queued, String variant, boolean permissionFixture) throws Exception {
            this.root = root.toAbsolutePath(); this.queued = queued;
            Files.createDirectories(this.root);
            git("init", "-q");
            Files.writeString(this.root.resolve("README.md"), "Generic utility\n");
            Files.writeString(this.root.resolve(".gitignore"), ".agent/\n");
            git("add", "."); git("-c", "user.name=Fixture", "-c", "user.email=fixture@example.invalid", "commit", "-qm", "baseline");
            paths = new WorkstreamPaths(this.root, new DualId(ws));
            definition = variant.equals("small") ? new WorkflowDefinitionLoader().load(Path.of(
                    "../dual-runtime/src/test/resources/dev/dual/runtime/lifecycle/agent-lifecycle.small-plan-human-review-experiment.json"))
                    : new WorkflowDefinitionLoader().loadOrDefault(null, variant);
            AcpxTestLedger.initialize(ledger, paths, ws, definition);
            Files.writeString(paths.workstreamDir().resolve("requirement.md"), "Clarify the generic utility documentation.");
            // Keep the complete L00 identity; only correct the fixture repository base facts.
            var stream = (ObjectNode) JSON.readTree(paths.workstreamDir().resolve("workstream.json").toFile());
            var workspaceId = stream.path("primaryWorkspaceId").asText();
            var workspacePath = paths.workstreamDir().resolve("workspaces/" + workspaceId + ".json");
            var workspace = (ObjectNode) JSON.readTree(workspacePath.toFile());
            workspace.put("baseCommit", git("rev-parse", "HEAD").trim());
            JSON.writeValue(workspacePath.toFile(), workspace);
            transport = new Transport(this, permissionFixture);
        }
        HandlerRegistry handlers() {
            return new HandlerRegistry(List.of(new AcpxAgentTaskHandler(transport, false, new AcpxUsageReader(), manifestWriter), new ContextBuildPackageHandler(),
                    new ContractCompileHandler(), new ArtifactValidateHandler(), new FailureClassifyHandler(),
                    new ReportGenerateFinalHandler(), new DeliveryPrepareHandler(), new PromptGenerateNextStageHandler()),
                    List.of(new ReviewDecisionHandler()), List.of(new HumanReviewGateHandler()));
        }
        WorkflowRuntime runtime() { return new WorkflowRuntime(ledger, handlers(), queued ? AgentTaskDispatchMode.WORKER : AgentTaskDispatchMode.INLINE); }
        NodeInstanceDocument active() throws Exception { return ledger.loadNode(paths, ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst()); }
        AgentRunGovernanceContext.PreparedContext qualifiedInput() throws Exception {
            return AgentRunGovernanceContext.ensureQualified(new TaskHandler.TaskContext(root, ws,
                    new NodeDefinition("fixture.execution_qualification", NodeKind.TASK, "acpx.agent_run", Map.of("required_contract_phase", "EXECUTION")), ledger.loadCoreNode(paths, active().nodeInstanceId()), AcpxTestLedger.ledger()), "check-next-input");
        }
        void untilGate() throws Exception {
            for (int i = 0; i < 40; i++) {
                if (ledger.loadCurrent(paths).status().isTerminal()) return;
                if (ledger.loadCurrent(paths).status() != WorkflowStatus.RUNNING) {
                    if (active().nodeDefinitionId().equals("gate.human_plan_approval")
                            && VerificationReadiness.REQUIRED.equals(ledger.loadOpenGateForNode(paths, active().nodeInstanceId()).result().get("source_outcome"))) {
                        recordFixtureReadiness();
                        ledger.resolveOpenGate(paths, definition, "revise", "Bind the operator-provided verification conditions in the plan");
                    } else return;
                }
                var command = new WorkflowCommand(root, ws);
                runtime().resume(command);
                if (queued && ledger.loadCurrent(paths).status() == WorkflowStatus.RUNNING) new AgentWorker(ledger, handlers()).runOne(command);
            }
            fail("workflow did not pause");
        }
        dev.dual.ledger.file.NodeInstanceDocument goalCompile() throws Exception {
            try (var files = Files.list(paths.nodesDir())) {
                return files.filter(p -> p.toString().endsWith(".json")).map(p -> {
                    try { return ledger.loadNode(paths, p.getFileName().toString().replace(".json", "")); }
                    catch (Exception failure) { throw new IllegalStateException(failure); }
                }).filter(n -> "GOAL".equals(n.result().get("contract_phase")))
                        .max(java.util.Comparator.comparingLong(n -> ((Number) n.result().get("contract_epoch")).longValue())).orElseThrow();
            }
        }
        void recordFixtureReadiness() throws Exception {
            // The simulated operator uses the product readiness entry only after reaching its waiting gate.
            var compile = goalCompile();
            var ref = compile.outputRefs().stream().filter(r -> r.endsWith("/task-contract.json")).findFirst().orElseThrow();
            var goal = JSON.readValue(root.resolve(ref).toFile(), dev.dual.core.contract.TaskContract.class);
            var tool = root.resolve(".git/verification-tool.lock"); Files.writeString(tool, "offline test runner");
            var hash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(tool)));
            readiness = VerificationReadiness.record(ledger, root, ws, new VerificationReadiness.Request(1,
                    "fixture-readiness-" + goal.epoch(), active().nodeInstanceId(),
                    new dev.dual.core.contract.TaskContract.Reference(ref, compile.result().get("task_contract_sha256").toString()), goal.epoch(),
                    goal.acceptanceCriteria().stream().map(dev.dual.core.contract.TaskContract.Clause::id).toList(), "Run offline verification",
                    dev.dual.core.contract.TaskContract.Executor.AGENT, dev.dual.core.contract.TaskContract.EvidenceKind.PROGRAM_TOOL_EXECUTION_AND_ASSERTIONS,
                    new VerificationReadiness.Environment(root.toRealPath().toString(), "offline-test-runner", "fixture-operator",
                            List.of(new VerificationReadiness.Dependency(tool.toRealPath().toString(), hash, false))),
                    "Fixture verification tools are prepared", "fixture-operator", VerificationReadiness.Lifetime.AUTHORIZATION_EPOCH, null,
                    VerificationReadiness.Action.RECORD, null));
            bindings = goal.acceptanceCriteria().stream().map(c -> new dev.dual.core.contract.TaskContract.VerificationBinding(c.id(), "Run offline verification",
                    dev.dual.core.contract.TaskContract.Executor.AGENT, List.of(readiness), List.of(),
                    dev.dual.core.contract.TaskContract.EvidenceKind.PROGRAM_TOOL_EXECUTION_AND_ASSERTIONS, List.of(readiness), "fixture-operator", false, null)).toList();
        }

        Map<String, String> snapshot() throws Exception {
            var snapshot = new TreeMap<String, String>();
            try (var files = Files.walk(paths.workstreamDir())) {
                for (var file : files.filter(Files::isRegularFile).toList()) snapshot.put(file.toString(), java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file))));
            }
            return snapshot;
        }
        String git(String... args) throws Exception {
            var cmd = new ArrayList<>(List.of("git", "-C", root.toString())); cmd.addAll(List.of(args));
            var process = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            var output = new String(process.getInputStream().readAllBytes());
            assertEquals(0, process.waitFor(), output);
            return output;
        }
    }

    static final class Transport extends AcpxAgentTransport {
        final Fixture f;
        final List<String> calls = new ArrayList<>(), prompts = new ArrayList<>(), obligations = new ArrayList<>();
        String recommendation = "READY_FOR_PRODUCT_REVIEW";
        String acceptanceFailure = "";
        boolean providerDenied;
        boolean interfaceIncompatible;
        ProcessResult process = ProcessResult.exitedZero();
        String completedCandidateNode;
        String permissionChoice;
        int permissionCommits;
        Transport(Fixture f, boolean finitePermission) {
            super("offline-fixture", java.time.Duration.ZERO, java.time.Duration.ofSeconds(5), java.time.Duration.ofSeconds(2),
                    finitePermission ? java.time.Duration.ofSeconds(30) : java.time.Duration.ZERO);
            this.f = f;
        }
        @Override public AcpxRunResult runClaudeDesign(AcpxRunRequest request) throws java.io.IOException { return run(request); }
        @Override public AcpxRunResult runCodexDesignReview(AcpxRunRequest request) throws java.io.IOException { return run(request); }
        @Override AcpxRunResult runAgentResultCorrection(String agent, AcpxRunRequest request) { throw new AssertionError("fixture must supply valid output first time"); }
        // A synthetic native request plus real R/L/handler consumers; IPC behavior has separate local-peer tests.
        boolean nativeChoice(AcpxRunRequest request) throws Exception {
            var raw = JSON.writeValueAsString(Map.of("sessionId", "offline-native", "toolCall", Map.of("toolCallId", "edit-readme", "kind", "edit", "title", "approved README edit"),
                    "options", List.of(Map.of("optionId", "specific-allow", "kind", "allow_once", "name", "Once"), Map.of("optionId", "specific-deny", "kind", "reject_once", "name", "Deny"))));
            var key = UUID.randomUUID().toString();
            var envelope = Map.<String,Object>of("schema", "dual.native.permission.request.v1", "requestKey", key, "sessionId", "offline-native", "promptId", "offline-prompt", "rpcId", "offline-rpc",
                    "ownerGeneration", 1, "rawJson", raw, "rawSha256", HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8))), "deadlineMs", System.currentTimeMillis()+20000);
            var sink = request.executionEventSink();
            sink.permissionRequest(envelope);
            var execution = f.ledger.latestNodeExecution(f.paths, f.active()).orElseThrow();
            var saved = NativePermissionRequests.decide(f.ledger, f.paths, execution.nodeExecutionId(), key,
                    permissionChoice.equals("allow") ? "specific-allow" : "specific-deny", "offline user choice");
            assertEquals(false, saved.get("send_authority"));
            assertFalse(f.ledger.latestNodeExecution(f.paths, f.active()).orElseThrow().result().containsKey("permission_reply"));
            var reply = sink.permissionReply(key).orElseThrow(); permissionCommits++;
            assertEquals(key, reply.get("requestKey"));
            assertTrue(sink.permissionReply(key).isEmpty(), "the same callback cannot obtain a second send qualification");
            sink.permissionObservation(key, "send-intent", Map.of("attempts", 1));
            sink.permissionObservation(key, "send-result", Map.of("delivery", "ACKNOWLEDGED", "consumed", true));
            return permissionChoice.equals("allow");
        }
        AcpxRunResult run(AcpxRunRequest request) throws java.io.IOException {
            try {
                var node = f.active();
                var runId = request.promptPath().getParent().getParent().getFileName().toString();
                var id = node.nodeDefinitionId();
                calls.add(id); prompts.add(Files.readString(request.promptPath()));
                if (interfaceIncompatible) {
                    // Synthetic adapter failure; no model output, used to exercise the real durable consumers.
                    Files.writeString(request.stdoutPath(), ""); Files.writeString(request.stderrPath(), "");
                    var now = Instant.now();
                    return new AcpxRunResult(now, now, List.of("offline-fixture"), List.of(), null, null,
                            ProcessResult.nonZeroIncomplete(1), List.of(new dev.dual.core.execution.AdapterSignal(
                            dev.dual.core.execution.AdapterSignalType.ADAPTER_INCOMPATIBLE, "fixture incompatible protocol", 1)));
                }
                if (id.equals("implement.claude_implement") && permissionChoice != null && !nativeChoice(request)) {
                    Files.writeString(request.stdoutPath(), "offline exact native denial"); Files.writeString(request.stderrPath(), "");
                    var now = Instant.now();
                    return new AcpxRunResult(now, now, List.of("offline-fixture"), List.of(), null, null,
                            ProcessResult.nonZeroIncomplete(5), List.of(new dev.dual.core.execution.AdapterSignal(
                            dev.dual.core.execution.AdapterSignalType.PERMISSION_BLOCKED, "exact offered reject_once selected", 1)));
                }
                var emission = AgentResultPromptContract.ProposalEmissionContext.initial();
                TaskContractRevisionContextResolver.Context revision = null;
                if (id.equals("intake.claude_brainstorm")) {
                    var resolved = new TaskContractRevisionContextResolver(AcpxTestLedger.ledger()).resolveForNode(f.root, f.ws, node.nodeInstanceId());
                    assertTrue(resolved.valid(), resolved.errors().toString());
                    revision = resolved.context();
                    if (revision.revision()) emission = AgentResultPromptContract.proposalEmissionContext(revision);
                }
                var decision = switch (id) {
                    case "intake.claude_brainstorm" -> "READY_FOR_PRODUCT_REVIEW";
                    case "intake.codex_product_review" -> recommendation;
                    case "plan.claude_plan" -> "READY_FOR_PLAN_REVIEW";
                    case "plan.codex_plan_review" -> "READY_TO_IMPLEMENT";
                    case "implement.claude_implement" -> "IMPLEMENTED";
                    case "implement.claude_self_test_report" -> "SELF_TEST_COMPLETE";
                    case "acceptance.codex_verifier_review" -> acceptanceFailure.isEmpty() ? "ACCEPT" : "REJECT";
                    case "stage.claude_handoff" -> "HANDOFF_READY";
                    default -> "READY_FOR_ARCH_REVIEW";
                };
                var example = AgentResultPromptContract.render(f.ws, runId, id, decision, Set.of(decision), emission);
                var envelope = (ObjectNode) JSON.readTree(example.substring(example.lastIndexOf("\n{") + 1));
                var result = (ObjectNode) envelope.get("result");
                if (result.has("must_fix")) result.putArray("must_fix");
                if (result.has("contract_obligations")) result.putArray("contract_obligations");
                if (id.equals("acceptance.codex_verifier_review") && decision.equals("REJECT")) {
                    result.put("blocking", true);
                    result.putArray("missing_evidence").add("Operator must supply the unavailable verification record");
                    var failure = (ObjectNode) result.get("failure");
                    failure.put("category_hint", acceptanceFailure);
                    failure.putArray("contract_gaps"); failure.putArray("out_of_scope_files");
                    failure.putArray("missing_tests"); failure.putArray("context_gaps");
                    failure.put("verification_remediation", "NONE");
                    failure.put("reason", "Required verification service is unavailable; no product changes requested");
                    if (acceptanceFailure.equals("CONTEXT_INSUFFICIENT"))
                        failure.putArray("context_gaps").add("Operator verification record is unavailable");
                    acceptanceFailure = "";
                }
                if (decision.equals("BLOCKED")) {
                    result.put("blocking", true);
                    result.putArray("must_fix").add("Clarify the acceptance wording");
                }
                if (decision.equals("NEEDS_REVISION")) {
                    result.put("blocking", true);
                    ((ObjectNode) result.get("failure")).putArray("contract_gaps").add("Clarify acceptance wording");
                    result.putArray("contract_obligations").addObject().put("id", "CO-1").put("text", "Clarify acceptance wording");
                    recommendation = "READY_FOR_PRODUCT_REVIEW";
                }
                if ((result.has("design") || result.has("plan")) && result.has("revision_responses")) {
                    var manifest = JSON.readTree(PromptManifestWriter.manifestPathFor(request.promptPath()).toFile());
                    var snapshot = JSON.treeToValue(manifest.get("authoredContext"), AuthoredExecutionContext.Snapshot.class);
                    var responses = result.putArray("revision_responses");
                    snapshot.obligations().forEach(o -> responses.addObject().put("obligation_id", o.id())
                            .put("disposition", "ADDRESSED").put("evidence", "Plan includes the registered verification conditions"));
                }
                if (result.has("design")) ((ObjectNode) result.at("/design/planned_files/0"))
                        .put("path", "README.md").put("operation", "MODIFY");
                if (result.has("plan")) {
                    var plan = (ObjectNode) result.get("plan");
                    plan.putArray("edit_files").add("README.md");
                    plan.putArray("files").addObject().put("path", "README.md").put("operation", "MODIFY");
                    if (f.bindings.isEmpty()) {
                        var goalRef = f.goalCompile().outputRefs().stream().filter(r -> r.endsWith("/task-contract.json")).findFirst().orElseThrow();
                        var goal = JSON.readValue(f.root.resolve(goalRef).toFile(), dev.dual.core.contract.TaskContract.class);
                        f.bindings = goal.acceptanceCriteria().stream().map(c -> new dev.dual.core.contract.TaskContract.VerificationBinding(c.id(), "Run offline verification",
                                dev.dual.core.contract.TaskContract.Executor.AGENT, List.of(), List.of(),
                                dev.dual.core.contract.TaskContract.EvidenceKind.PROGRAM_TOOL_EXECUTION_AND_ASSERTIONS, List.of(), "fixture-operator", false, null)).toList();
                    }
                    plan.set("verification_bindings", JSON.valueToTree(f.bindings)); plan.putArray("authority_refs");
                }
                if (id.equals("implement.claude_implement")) Files.writeString(f.root.resolve("README.md"), "Documented generic utility behavior\n");
                if (revision != null) {
                    if (!revision.revision()) {
                        ((ObjectNode) result.get("scope")).putArray("included").addObject().put("path", "README.md");
                        result.putArray("evidence_refs").add("README.md");
                    } else {
                        var patches = result.putArray("obligation_patches");
                        for (var obligation : revision.obligations()) {
                            obligations.add(obligation.text());
                            var patch = patches.addObject().put("review_obligation_id", obligation.id());
                            patch.putArray("resolved_by_clause_ids").add("AC-1");
                            patch.putArray("clause_changes").addObject().put("operation", "REPLACE")
                                    .put("section", "ACCEPTANCE_CRITERIA").put("id", "AC-1")
                                    .put("text", "The documented utility behavior is observable");
                            patch.putArray("scope_additions"); patch.putArray("evidence_ref_additions");
                        }
                    }
                }
                var observations = new ArrayList<dev.dual.core.execution.AgentExecutionEvidence.ToolObservation>();
                if (result.has("verification_checks")) {
                    var content = Files.readString(f.root.resolve("README.md"));
                    observations.add(new dev.dual.core.execution.AgentExecutionEvidence.ToolObservation("fixture-session", "read-readme", "fixture.read", "read",
                            JSON.valueToTree(Map.of("path", "README.md")), JSON.valueToTree(Map.of("content", content)),
                            "completed", null, null, "", false, List.of()));
                    result.set("verification_checks", JSON.valueToTree(f.bindings.stream().map(binding ->
                            new AgentResultEnvelope.VerificationCheck(binding.criterionId(), "Inspected fixture README", "CURRENT", List.of("read-readme"), List.of())).toList()));
                }
                Files.writeString(request.stdoutPath(), "fixture transcript"); Files.writeString(request.stderrPath(), "");
                completedCandidateNode = node.nodeInstanceId();
                var now = Instant.now();
                return new AcpxRunResult(now, now.plusMillis(1), List.of("fixture"), List.of(), null,
                        new AcpxPromptCompletion("fixture", "end_turn", envelope.toString()),
                        process,
                        providerDenied ? List.of(new dev.dual.core.execution.AdapterSignal(
                                dev.dual.core.execution.AdapterSignalType.RATE_LIMITED, "quota exhausted", 1)) : List.of(), observations);
            } catch (java.io.IOException e) { throw e; }
            catch (Exception e) { throw new java.io.IOException(e); }
        }
    }
}
