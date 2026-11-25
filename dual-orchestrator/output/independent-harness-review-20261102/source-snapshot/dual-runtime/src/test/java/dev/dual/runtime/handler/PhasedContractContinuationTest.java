package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.dual.core.contract.TaskContract;
import dev.dual.core.workflow.*;
import dev.dual.runtime.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static dev.dual.runtime.handler.VerificationReadinessTest.JSON;

/** Offline lifecycle test: fixture-approved GOAL, real handlers/ledger; deterministic provider outputs. */
class PhasedContractContinuationTest {
    @TempDir Path root;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void criterionDetailsReachReviewAndImplementationButMethodSuffixCannotAdvance(boolean appendToMethod) throws Exception {
        var f = new Flow(root);
        f.verificationDetails = List.of("AC-1: assert the exact result before display rounding",
                "AC-2: assert invalid input is rejected; inspect the real report");
        f.methodSuffix = appendToMethod ? "; AC-1: inspect the exact assertion" : "";
        f.untilPause();
        assertEquals("gate.plan", f.node(), "missing readiness must retain the legal recording path");
        f.recordReadiness();
        ReviewGateActions.resolve(f.base.ledger, f.base.paths, "revise", "Bind the registered method; keep AC details in validation_plan");
        f.untilPause();
        var reviewInput = f.inputs.get("plan.codex_plan_review").render(root);
        for (var detail : f.verificationDetails) assertTrue(reviewInput.contains(detail));
        assertFalse(f.calls.contains("implement.claude_implement"));
        if (appendToMethod) {
            assertEquals("gate.plan", f.node());
            assertEquals(VerificationReadiness.REQUIRED, f.planDecision().result().get("readiness_reason"));
            return;
        }
        assertEquals("gate.contract_review", f.node());
        var candidate = TaskContractProjectionVerifier.executionCandidate(f.base.ledger, root, f.base.ws,
                f.base.ledger.loadCurrent(f.base.paths).activeNodeInstanceIds().getFirst(), true);
        assertTrue(candidate.contract().verificationBindings().stream().allMatch(b -> b.method().equals("run project tests")));
        ReviewGateActions.resolve(f.base.ledger, f.base.paths, "approve", "Approve this exact EXECUTION scope");
        f.untilPause();
        assertEquals(WorkflowStatus.COMPLETED, f.base.ledger.loadCurrent(f.base.paths).status());
        var implementationInput = f.inputs.get("implement.claude_implement").render(root);
        for (var detail : f.verificationDetails) assertTrue(implementationInput.contains(detail));
        assertEquals(1, Collections.frequency(f.calls, "implement.claude_implement"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"revise", "approve"})
    void missingReadinessCanBeRecordedRevisedReviewedApprovedAndConsumed(String resolutionAction) throws Exception {
        var f = new Flow(root);
        f.untilPause();
        assertEquals("gate.plan", f.node(), f.current());
        assertEquals(List.of("design.claude_design", "design.codex_arch_review", "plan.claude_plan", "plan.codex_plan_review"), f.calls);
        assertEquals("READY_TO_IMPLEMENT", f.planDecision().result().get("decision"));
        assertEquals(VerificationReadiness.REQUIRED, f.planDecision().result().get("readiness_reason"));
        f.recordReadiness();
        assertEquals("gate.plan", f.node(), "recording is not approval or dispatch");
        ReviewGateActions.resolve(f.base.ledger, f.base.paths, resolutionAction, "Use the recorded test environment");
        f.untilPause();
        assertEquals("gate.contract_review", f.node(), f.current());
        assertEquals(2, Collections.frequency(f.calls, "plan.claude_plan"));
        assertEquals(2, Collections.frequency(f.calls, "plan.codex_plan_review"));
        assertEquals(1, Collections.frequency(f.calls, "design.codex_arch_review"));
        assertFalse(f.calls.contains("implement.claude_implement"));
        var candidate = TaskContractProjectionVerifier.executionCandidate(f.base.ledger, f.base.root, f.base.ws,
                f.base.ledger.loadCurrent(f.base.paths).activeNodeInstanceIds().getFirst(), true);
        assertEquals(TaskContract.Phase.EXECUTION, candidate.contract().phase());
        assertEquals(List.of(new TaskContract.ScopeEntry("product.txt", TaskContract.ScopeOperation.CREATE)), candidate.contract().scope().included());
        ReviewGateActions.resolve(f.base.ledger, f.base.paths, "approve", "Approve the exact implementation scope");
        f.untilPause();
        assertEquals(WorkflowStatus.COMPLETED, f.base.ledger.loadCurrent(f.base.paths).status(), f.current());
        assertEquals(1, Collections.frequency(f.calls, "implement.claude_implement"));
        var input = f.inputs.get("implement.claude_implement");
        assertNotNull(input.reviewedDesign()); assertNotNull(input.reviewedPlan());
        assertEquals(candidate.contractRef(), input.contract().contract().ref());
        assertTrue(f.inputs.get("plan.claude_plan").obligations().stream().anyMatch(o -> o.text().equals("Use the recorded test environment")));
    }

    @Test void dependencyDriftBeforeApprovalRejectsWithoutDecisionOrDispatch() throws Exception {
        var f = new Flow(root); f.untilPause(); f.recordReadiness();
        ReviewGateActions.resolve(f.base.ledger, f.base.paths, "revise", "Bind current readiness");
        f.untilPause(); assertEquals("gate.contract_review", f.node(), f.current());
        Files.writeString(root.resolve("test-tool.lock"), "changed environment");
        var events = Files.readAllBytes(f.base.paths.eventsJsonl());
        assertThrows(java.io.IOException.class, () -> ReviewGateActions.resolve(f.base.ledger, f.base.paths, "approve", "approve"));
        assertArrayEquals(events, Files.readAllBytes(f.base.paths.eventsJsonl()));
        assertEquals("gate.contract_review", f.node());
        assertFalse(f.calls.contains("implement.claude_implement"));
        assertNotNull(TaskContractProjectionVerifier.executionCandidate(f.base.ledger, f.base.root, f.base.ws,
                f.base.ledger.loadCurrent(f.base.paths).activeNodeInstanceIds().getFirst(), false), "expired candidate must still be inspectable");
    }

    @Test void driftAfterApprovalIsTypedPreparationFailureAndRestoredSameProofCanContinue() throws Exception {
        var f = new Flow(root); f.untilPause(); f.recordReadiness();
        ReviewGateActions.resolve(f.base.ledger, f.base.paths, "revise", "Bind current readiness");
        f.untilPause();
        ReviewGateActions.resolve(f.base.ledger, f.base.paths, "approve", "Approve implementation");
        var old = Files.readAllBytes(root.resolve("test-tool.lock"));
        Files.writeString(root.resolve("test-tool.lock"), "different tool");
        var id = f.base.ledger.loadCurrent(f.base.paths).activeNodeInstanceIds().getFirst();
        var context = new TaskHandler.TaskContext(f.base.root, f.base.ws,
                f.base.ledger.loadDefinition(f.base.paths).node("implement.claude_implement"),
                f.base.ledger.loadCoreNode(f.base.paths, id), f.base.ledger);
        var failure = assertThrows(AgentRunGovernanceContext.QualificationException.class,
                () -> AgentRunGovernanceContext.ensureQualified(context, "must-not-dispatch"));
        assertEquals(VerificationReadiness.REQUIRED, failure.reason());
        assertFalse(f.calls.contains("implement.claude_implement"));
        assertFalse(Files.exists(f.base.paths.workstreamDir().resolve("context/agent-governance-" + id + "-must-not-dispatch.md")));
        Files.write(root.resolve("test-tool.lock"), old);
        f.untilPause();
        assertEquals(WorkflowStatus.COMPLETED, f.base.ledger.loadCurrent(f.base.paths).status());
        assertEquals(1, Collections.frequency(f.calls, "implement.claude_implement"));
    }

    @Test void weakeningApprovedExitRequirementReturnsToAuthorReviewAndNewExecutionApproval() throws Exception {
        var f = new Flow(root); f.actualExitRequired = true;
        f.untilPause(); f.recordReadiness();
        ReviewGateActions.resolve(f.base.ledger, f.base.paths, "revise", "Bind prerequisites");
        f.untilPause();
        var originalCandidate = TaskContractProjectionVerifier.executionCandidate(f.base.ledger, root, f.base.ws,
                f.base.ledger.loadCurrent(f.base.paths).activeNodeInstanceIds().getFirst(), true);
        assertTrue(originalCandidate.contract().verificationBindings().stream().allMatch(b -> Boolean.TRUE.equals(b.actualExitRequired())));
        ReviewGateActions.resolve(f.base.ledger, f.base.paths, "approve", "Approve real exit evidence requirement");
        var original = Files.readAllBytes(root.resolve("test-tool.lock"));
        Files.writeString(root.resolve("test-tool.lock"), "temporary environment drift"); f.untilPause();
        var failedId = f.base.ledger.loadCurrent(f.base.paths).activeNodeInstanceIds().getFirst();
        assertEquals(WorkflowStatus.WAITING_FOR_RECOVERY, f.base.ledger.loadCurrent(f.base.paths).status());
        Files.write(root.resolve("test-tool.lock"), original);
        var t = f.base.request("renewed-exit-plan", VerificationReadiness.Action.RECORD, f.readiness);
        f.readiness = VerificationReadiness.record(f.base.ledger, root, f.base.ws, new VerificationReadiness.Request(
                t.schema(), t.requestId(), failedId, t.goal(), t.epoch(), t.criterionIds(), t.method(), t.executor(), t.evidenceKind(),
                t.environment(), t.statement(), t.detailOwner(), t.lifetime(), t.expiresAt(), t.action(), t.supersedes()));
        f.actualExitRequired = false;
        ReviewGateActions.revise(f.base.ledger, f.base.paths, failedId, "Propose native execution status instead of a real exit requirement");
        f.untilPause();
        assertEquals("gate.contract_review", f.node());
        assertEquals(3, Collections.frequency(f.calls, "plan.claude_plan"));
        assertEquals(3, Collections.frequency(f.calls, "plan.codex_plan_review"));
        assertFalse(f.calls.contains("implement.claude_implement"));
        var replacement = TaskContractProjectionVerifier.executionCandidate(f.base.ledger, root, f.base.ws,
                f.base.ledger.loadCurrent(f.base.paths).activeNodeInstanceIds().getFirst(), true);
        assertNotEquals(originalCandidate.rawJson(), replacement.rawJson());
        assertTrue(replacement.contract().verificationBindings().stream().noneMatch(b -> Boolean.TRUE.equals(b.actualExitRequired())));
        ReviewGateActions.resolve(f.base.ledger, f.base.paths, "approve", "Approve this explicitly changed evidence requirement");
        f.untilPause();
        assertEquals(WorkflowStatus.COMPLETED, f.base.ledger.loadCurrent(f.base.paths).status());
        assertEquals(1, Collections.frequency(f.calls, "implement.claude_implement"));
        assertEquals(replacement.contractRef(), f.inputs.get("implement.claude_implement").contract().contract().ref());
    }

    @Test void changedPersistedReviewFactsCannotBecomeQualifiedByNumericNormalization() throws Exception {
        var f = new Flow(root); f.untilPause(); f.recordReadiness();
        ReviewGateActions.resolve(f.base.ledger, f.base.paths, "revise", "Bind current readiness");
        f.untilPause();
        var gate = f.base.ledger.loadNode(f.base.paths, f.base.ledger.loadCurrent(f.base.paths).activeNodeInstanceIds().getFirst());
        var compile = f.base.ledger.loadNode(f.base.paths, String.valueOf(gate.inputTransition().get("source_node_instance_id")));
        var decisionId = String.valueOf(compile.inputTransition().get("source_node_instance_id"));
        var path = f.base.paths.nodeJson(decisionId);
        var bytes = Files.readAllBytes(path);
        var original = JSON.readTree(bytes);
        for (var value : List.of(JSON.getNodeFactory().numberNode(2), JSON.getNodeFactory().textNode("1"))) {
            var changed = (ObjectNode) original.deepCopy();
            ((ObjectNode) changed.at("/result/reviewed_candidate/contract")).set("epoch", value);
            JSON.writeValue(path.toFile(), changed);
            assertThrows(java.io.IOException.class, () -> ReviewGateActions.resolve(f.base.ledger, f.base.paths, "approve", "approve"));
            assertFalse(f.calls.contains("implement.claude_implement"));
        }
        Files.write(path, bytes);
        ReviewGateActions.resolve(f.base.ledger, f.base.paths, "approve", "Approve exact unchanged candidate");
        f.untilPause();
        assertEquals(WorkflowStatus.COMPLETED, f.base.ledger.loadCurrent(f.base.paths).status());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void failedPreparationReturnsToAuthorAndReviewThenKeepsOrRenewsApproval(boolean changedEnvironment) throws Exception {
        var f = new Flow(root); f.untilPause(); f.recordReadiness();
        ReviewGateActions.resolve(f.base.ledger, f.base.paths, "revise", "Bind prerequisites");
        f.untilPause(); ReviewGateActions.resolve(f.base.ledger, f.base.paths, "approve", "Approve exact execution");
        var original = Files.readAllBytes(root.resolve("test-tool.lock"));
        Files.writeString(root.resolve("test-tool.lock"), "environment drift");
        f.untilPause();
        assertEquals(WorkflowStatus.WAITING_FOR_RECOVERY, f.base.ledger.loadCurrent(f.base.paths).status());
        assertEquals(0, Collections.frequency(f.calls, "implement.claude_implement"));
        var failedId = f.base.ledger.loadCurrent(f.base.paths).activeNodeInstanceIds().getFirst();
        var failure = f.base.ledger.loadNode(f.base.paths, failedId);
        assertEquals(NodeStatus.FAILED, failure.status());
        if (!changedEnvironment) Files.write(root.resolve("test-tool.lock"), original);
        var template = f.base.request("renewed", VerificationReadiness.Action.RECORD, f.readiness);
        var req = new VerificationReadiness.Request(template.schema(), template.requestId(), failedId, template.goal(), template.epoch(),
                template.criterionIds(), template.method(), template.executor(), template.evidenceKind(), template.environment(),
                template.statement(), template.detailOwner(), template.lifetime(), template.expiresAt(), template.action(), template.supersedes());
        f.readiness = VerificationReadiness.record(f.base.ledger, root, f.base.ws, req);
        ReviewGateActions.revise(f.base.ledger, f.base.paths, failedId, "Use renewed verified prerequisites");
        assertEquals(NodeStatus.FAILED, f.base.ledger.loadNode(f.base.paths, failedId).status(), "repair never rewrites a failure into approval");
        assertThrows(java.io.IOException.class, () -> ReviewGateActions.revise(f.base.ledger, f.base.paths, failedId, "stale request"));
        f.untilPause();
        assertEquals(3, Collections.frequency(f.calls, "plan.claude_plan"), f.current());
        assertEquals(3, Collections.frequency(f.calls, "plan.codex_plan_review"), f.current());
        assertTrue(f.inputs.get("plan.claude_plan").obligations().stream().anyMatch(o -> o.text().equals("Use renewed verified prerequisites")));
        if (changedEnvironment) {
            assertEquals("gate.contract_review", f.node());
            assertEquals(0, Collections.frequency(f.calls, "implement.claude_implement"));
            ReviewGateActions.resolve(f.base.ledger, f.base.paths, "approve", "Approve changed environment");
            f.untilPause();
        }
        assertEquals(WorkflowStatus.COMPLETED, f.base.ledger.loadCurrent(f.base.paths).status(), f.current());
        assertEquals(1, Collections.frequency(f.calls, "implement.claude_implement"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void unknownExecutionRequiresExactStopAndRiskDecisionBeforeRetryOrAbort(boolean abort) throws Exception {
        var f = new Flow(root); f.untilPause(); f.recordReadiness();
        ReviewGateActions.resolve(f.base.ledger, f.base.paths, "revise", "Bind prerequisites");
        f.untilPause(); ReviewGateActions.resolve(f.base.ledger, f.base.paths, "approve", "Approve exact execution");
        f.crashAfterIntent = true;
        assertThrows(java.io.IOException.class, f::untilPause);
        f.untilPause();
        assertEquals(WorkflowStatus.WAITING_FOR_RECOVERY, f.base.ledger.loadCurrent(f.base.paths).status());
        var failed = f.base.ledger.loadNode(f.base.paths, f.base.ledger.loadCurrent(f.base.paths).activeNodeInstanceIds().getFirst());
        assertEquals("UNKNOWN", failed.result().get("external_effect"));
        assertThrows(java.io.IOException.class, () -> f.base.ledger.retryActiveNode(f.base.paths, "ordinary note"));
        assertThrows(java.io.IOException.class, () -> f.base.ledger.abortRecovery(f.base.paths, "ordinary note"));
        var template = (ObjectNode) JSON.valueToTree(ExecutionRecoveryAdjudication.describe(f.base.ledger, f.base.paths).get("decision_template"));
        template.put("request_id", "explicit-decision"); template.put("operator_note", "Stop observed; preserve uncertainty");
        template.put("disposition", abort ? "ABORT" : "AUTHORIZE_NEW_ATTEMPT");
        template.put("accept_duplicate_effect_risk", !abort);
        assertThrows(java.io.IOException.class, () -> ExecutionRecoveryAdjudication.apply(f.base.ledger, f.base.paths,
                JSON.treeToValue(template, ExecutionRecoveryAdjudication.Request.class)));
        var commandId = java.util.UUID.randomUUID().toString();
        var details = new LinkedHashMap<String, Object>(Map.of("command_id", commandId, "cleanup", true,
                "purpose", "session_cancel", "session", "fixture-session", "invocation", JSON.readTree(f.lastPrepared.path().toFile()).path("binding").path("native_invocation"), "command", List.of("fixture", "fixture", "cancel", "-s", "fixture-session")));
        AgentRunCommitProtocol.commandEvent(f.lastContext, f.lastPrepared, "command_intent", details);
        details.put("observation", Map.of("success", true, "timed_out", false, "exit_code", 0));
        AgentRunCommitProtocol.commandEvent(f.lastContext, f.lastPrepared, "command_result", details);
        var receipt = f.lastPrepared.path().getParent().resolve(f.lastPrepared.execution().nodeExecutionId() + "-command-" + commandId + "-result.json");
        template.set("evidence_refs", JSON.valueToTree(List.of(new TaskContract.Reference(f.base.root.relativize(receipt).toString(), TaskContractArtifactWriter.sha256(receipt)))));
        var wrong = template.deepCopy(); wrong.put("task", "foreign-task");
        assertThrows(java.io.IOException.class, () -> ExecutionRecoveryAdjudication.apply(f.base.ledger, f.base.paths,
                JSON.treeToValue(wrong, ExecutionRecoveryAdjudication.Request.class)));
        if (!abort) {
            var noRisk = template.deepCopy(); noRisk.put("accept_duplicate_effect_risk", false);
            assertThrows(java.io.IOException.class, () -> ExecutionRecoveryAdjudication.apply(f.base.ledger, f.base.paths,
                    JSON.treeToValue(noRisk, ExecutionRecoveryAdjudication.Request.class)));
        }
        var originalReceipt = Files.readAllBytes(receipt);
        var changedReceipt = (ObjectNode) JSON.readTree(originalReceipt);
        ((ObjectNode) changedReceipt.path("details")).set("command", JSON.valueToTree(List.of("fixture", "different-session", "cancel")));
        JSON.writeValue(receipt.toFile(), changedReceipt);
        var wrongReceipt = template.deepCopy();
        wrongReceipt.set("evidence_refs", JSON.valueToTree(List.of(new TaskContract.Reference(f.base.root.relativize(receipt).toString(), TaskContractArtifactWriter.sha256(receipt)))));
        var eventsBefore = Files.readAllBytes(f.base.paths.eventsJsonl());
        assertThrows(java.io.IOException.class, () -> ExecutionRecoveryAdjudication.apply(f.base.ledger, f.base.paths,
                JSON.treeToValue(wrongReceipt, ExecutionRecoveryAdjudication.Request.class)));
        assertArrayEquals(eventsBefore, Files.readAllBytes(f.base.paths.eventsJsonl()));
        Files.write(receipt, originalReceipt);
        var request = JSON.treeToValue(template, ExecutionRecoveryAdjudication.Request.class);
        ExecutionRecoveryAdjudication.apply(f.base.ledger, f.base.paths, request);
        var sequence = f.base.ledger.loadCurrent(f.base.paths).commitSequence();
        ExecutionRecoveryAdjudication.apply(f.base.ledger, f.base.paths, request);
        assertEquals(sequence, f.base.ledger.loadCurrent(f.base.paths).commitSequence());
        var old = new dev.dual.ledger.file.AtomicJsonWriter().readTree(f.base.paths.nodeExecutionJson(request.execution()));
        assertEquals("UNKNOWN", old.path("result").path("external_effect").asText());
        if (abort) assertEquals(WorkflowStatus.FAILED, f.base.ledger.loadCurrent(f.base.paths).status());
        else { f.untilPause(); assertEquals(WorkflowStatus.COMPLETED, f.base.ledger.loadCurrent(f.base.paths).status(), f.current()); }
    }

    @Test
    void coldJvmCompletesFixedPublicationWithoutProviderOrNewBytes() throws Exception {
        var f = new Flow(root); f.failPublication = true;
        assertThrows(java.io.IOException.class, f::untilPause);
        var execution = f.base.ledger.latestNodeExecution(f.base.paths, f.base.ledger.loadNode(f.base.paths,
                f.base.ledger.loadCurrent(f.base.paths).activeNodeInstanceIds().getFirst())).orElseThrow();
        assertEquals(AgentRunCommitProtocol.Observation.OUTCOME_RECORDED, AgentRunCommitProtocol.observe(f.base.paths, execution));
        var plan = JSON.readTree(f.lastPrepared.path().getParent().resolve("publication-plan.json").toFile());
        Files.delete(f.failedSidecar); // remove only this test's injected directory obstruction
        var javaCommand = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        var log = f.base.root.resolve("cold-recovery.log");
        var process = new ProcessBuilder(javaCommand, "-cp", System.getProperty("java.class.path"),
                ColdPublicationRecovery.class.getName(), f.base.root.toString(), f.base.ws).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        assertTrue(process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS), "cold recovery exceeded deadline");
        assertEquals(0, process.exitValue(), Files.readString(log));
        for (var it = plan.path("publication").path("bytes").fields(); it.hasNext();) {
            var entry = it.next();
            assertArrayEquals(Base64.getDecoder().decode(entry.getValue().asText()), Files.readAllBytes(f.base.root.resolve(entry.getKey())));
        }
        assertEquals(1, f.calls.size());
        assertEquals(NodeStatus.COMPLETED, f.base.ledger.loadNode(f.base.paths, execution.nodeInstanceId()).status());
    }

    public static final class ColdPublicationRecovery {
        public static void main(String[] args) throws Exception {
            var root = Path.of(args[0]);
            var ledger = new dev.dual.ledger.file.WorkflowFileLedger(new dev.dual.core.flow.BuiltInFlowOrchestrationKernel(), Map.of("test_binary", "readiness-v2"));
            var paths = new dev.dual.ledger.file.WorkstreamPaths(root, new dev.dual.core.id.DualId(args[1]));
            var execution = dev.dual.runtime.RecordedExecutionRecovery.active(ledger, paths).orElseThrow();
            dev.dual.runtime.RecordedExecutionRecovery.recover(ledger, paths, execution);
            // Same result may be reread after L03; it cannot create another event or call a provider.
            long sequence = ledger.loadCurrent(paths).commitSequence();
            dev.dual.runtime.RecordedExecutionRecovery.recover(ledger, paths, execution);
            if (sequence != ledger.loadCurrent(paths).commitSequence()) throw new AssertionError("duplicate completion advanced ledger");
        }
    }

    static final class Flow {
        boolean actualExitRequired;
        String methodSuffix = "";
        List<String> verificationDetails = List.of();
        final VerificationReadinessTest.Fixture base;
        final WorkflowRuntime runtime;
        final List<String> calls = new ArrayList<>();
        final Map<String, AuthoredExecutionContext.Snapshot> inputs = new HashMap<>();
        TaskContract.Reference readiness;
        int run;
        boolean crashAfterIntent;
        boolean failPublication;
        Path failedSidecar;
        TaskHandler.TaskContext lastContext;
        AgentRunCommitProtocol.Prepared lastPrepared;
        java.util.function.Function<TaskHandler.TaskContext, NodeResult> testQualification;
        Flow(Path root) throws Exception {
            var nodes = List.of(agent("design.claude_design", "design_result"), context("ctx.design", "design.codex_arch_review"),
                    agent("design.codex_arch_review", "agent_output_contract"), decision("design.decision"),
                    agent("plan.claude_plan", "plan_result"), context("ctx.plan", "plan.codex_plan_review"),
                    agent("plan.codex_plan_review", "agent_output_contract"), decision("plan.decision"),
                    new NodeDefinition("contract.execution_compile", NodeKind.TASK, "contract.compile",
                            Map.of("contract_phase", "EXECUTION", "required_subject", "reviewed_design_and_plan")),
                    new NodeDefinition("gate.contract_review", NodeKind.GATE, "human.review", Map.of("gate_purpose", "contract", "required_contract_phase", "GOAL", "allowed_actions", List.of("approve", "clarify", "reject"), "max_revisions", 3)),
                    agent("implement.claude_implement", "agent_output_contract"));
            var edges = List.of(edge("design.claude_design", "completed", "ctx.design"), edge("ctx.design", "completed", "design.codex_arch_review"),
                    edge("design.codex_arch_review", "completed", "design.decision"), edge("design.decision", "READY_FOR_ARCH_REVIEW", "plan.claude_plan"),
                    edge("plan.claude_plan", "completed", "ctx.plan"), edge("ctx.plan", "completed", "plan.codex_plan_review"),
                    edge("plan.codex_plan_review", "completed", "plan.decision"), edge("plan.decision", "READY_TO_IMPLEMENT", "contract.execution_compile"),
                    edge("plan.decision", VerificationReadiness.REQUIRED, "gate.plan"), edge("gate.plan", "revise", "plan.claude_plan"),
                    new EdgeDefinition("gate.plan", "approved", "plan.claude_plan", null, TransitionMetadata.loop("Human resolution requires a fresh author and independent review")),
                    edge("contract.execution_compile", "completed", "gate.contract_review"), edge("contract.execution_compile", "authorization_unchanged", "implement.claude_implement"), edge("gate.contract_review", "approved", "implement.claude_implement"));
            base = new VerificationReadinessTest.Fixture(root, nodes, edges, "design.claude_design");
            var provider = new TaskHandler() {
                public String type() { return "acpx.agent_run"; }
                public NodeResult execute(TaskContext context) throws Exception { return output(context); }
            };
            runtime = new WorkflowRuntime(base.ledger, new HandlerRegistry(List.of(provider, new ContextBuildPackageHandler(), new ContractCompileHandler()),
                    List.of(new ReviewDecisionHandler()), List.of(new HumanReviewGateHandler())));
        }
        void recordReadiness() throws Exception {
            var template = base.request("ready", VerificationReadiness.Action.RECORD, null);
            var request = new VerificationReadiness.Request(template.schema(), template.requestId(),
                    base.ledger.loadCurrent(base.paths).activeNodeInstanceIds().getFirst(), template.goal(), template.epoch(),
                    template.criterionIds(), template.method(), template.executor(), template.evidenceKind(), template.environment(),
                    template.statement(), template.detailOwner(), template.lifetime(), template.expiresAt(), template.action(), template.supersedes());
            readiness = VerificationReadiness.record(base.ledger, base.root, base.ws, request);
        }
        NodeResult output(TaskHandler.TaskContext context) throws Exception {
            var runId = "flow-" + (++run);
            if (testQualification != null && context.definition().id().equals("implement.claude_implement"))
                return testQualification.apply(context);
            var qualification = TaskContractProjectionVerifier.resolve(base.ledger, base.root, base.ws,
                    TaskContractProjectionVerifier.requiredPhase(context.definition()));
            if (qualification.failure() != null) return AgentRunGovernanceContext.qualificationFailure(context,
                    qualification.failure(), "AgentRunGovernanceContext.ensureQualified").result();
            var snapshot = AuthoredExecutionContext.prepare(context, runId).orElseThrow();
            inputs.put(context.definition().id(), snapshot);
            var dir = base.paths.runsDir().resolve(runId); Files.createDirectories(dir.resolve("prompts")); Files.createDirectories(dir.resolve("outputs"));
            var prompt = dir.resolve("prompts/prompt.md"); var manifest = dir.resolve("prompts/prompt.md.manifest.json");
            var body = snapshot.render(base.root); Files.writeString(prompt, body);
            JSON.writeValue(manifest.toFile(), Map.of("authoredContext", snapshot, "workstreamId", base.ws, "runId", runId,
                    "nodeId", context.definition().id(), "promptPath", prompt.toString(), "sections", List.of(Map.of(
                            "name", AuthoredExecutionContext.SECTION_NAME, "sourceRef", context.instance().nodeInstanceId(), "required", true,
                            "sha256", TaskContractArtifactWriter.sha256(body.getBytes(java.nio.charset.StandardCharsets.UTF_8))))));
            JSON.writeValue(dir.resolve("run.json").toFile(), Map.of("schemaVersion", 3, "runId", runId, "workstreamId", base.ws,
                    "nodeId", context.definition().id(), "agent", "fixture", "session", "fixture-session", "status", "prepared", "promptManifestPath", manifest.toString()));
            var prepared = AgentRunCommitProtocol.prepare(context, dir, List.of(prompt, manifest),
                    Map.of("contract_ref", snapshot.contract().contract().ref(), "contract_sha256", snapshot.contract().contract().sha256(), "run", JSON.readTree(dir.resolve("run.json").toFile()),
                            "native_invocation", Map.of("schema", "dual.acpx.invocation.v1", "executable", "fixture", "agent", "fixture", "cwd", base.root.toString(), "session", "fixture-session", "launch_environment", Map.of()),
                            "allowed_request_options", List.of(List.of("--deny-all"))));
            AgentRunCommitProtocol.qualifyDispatch(context, prepared);
            AgentRunCommitProtocol.beginExternalCall(context, prepared);
            lastContext = context; lastPrepared = prepared;
            if (crashAfterIntent && context.definition().id().equals("implement.claude_implement")) {
                crashAfterIntent = false;
                throw new java.io.IOException("simulated transport lost after intent");
            }
            AuthoredExecutionContext.verifyManifest(base.root, manifest, snapshot);
            calls.add(context.definition().id());
            var decision = switch (context.definition().id()) {
                case "plan.claude_plan" -> "READY_FOR_PLAN_REVIEW";
                case "plan.codex_plan_review" -> "READY_TO_IMPLEMENT";
                case "implement.claude_implement" -> "IMPLEMENTED";
                default -> "READY_FOR_ARCH_REVIEW";
            };
            var rendered = AgentResultPromptContract.render(base.ws, runId, context.definition().id(), decision, Set.of(decision));
            var envelope = (ObjectNode) JSON.readTree(rendered.substring(rendered.indexOf('{')));
            var result = (ObjectNode) envelope.get("result");
            if (result.has("must_fix")) result.putArray("must_fix");
            if (result.has("revision_responses")) {
                var responses = result.putArray("revision_responses");
                snapshot.obligations().forEach(o -> responses.addObject().put("obligation_id", o.id()).put("disposition", "ADDRESSED").put("evidence", "Bound current readiness"));
            }
            if (result.has("design")) ((ObjectNode) result.at("/design/planned_files/0")).put("path", "product.txt").put("operation", "CREATE");
            if (result.has("plan")) {
                var plan = (ObjectNode) result.get("plan");
                plan.putArray("edit_files").add("product.txt");
                plan.set("files", JSON.valueToTree(List.of(new TaskContract.ScopeEntry("product.txt", TaskContract.ScopeOperation.CREATE))));
                var bindings = readiness == null ? List.of("AC-1", "AC-2").stream().map(id -> new TaskContract.VerificationBinding(
                        id, "run project tests", TaskContract.Executor.AGENT, List.of(), List.of(),
                        TaskContract.EvidenceKind.PROGRAM_TOOL_EXECUTION_AND_ASSERTIONS, List.of(), "verification-owner", false, null)).toList() : base.bindings(readiness);
                bindings = bindings.stream().map(b -> new TaskContract.VerificationBinding(b.criterionId(), b.method() + methodSuffix, b.executor(),
                        b.environmentRefs(), b.authorityRefs(), b.evidenceKind(), b.feasibilityRefs(), b.detailOwner(),
                        actualExitRequired, actualExitRequired ? 0 : null)).toList();
                plan.set("verification_bindings", JSON.valueToTree(bindings)); plan.putArray("authority_refs");
                if (!verificationDetails.isEmpty()) plan.set("validation_plan", JSON.valueToTree(verificationDetails));
            }
            var output = dir.resolve("outputs/result.json"); JSON.writeValue(output.toFile(), envelope);
            if (failPublication) {
                failPublication = false;
                failedSidecar = AgentOutputContractSidecar.pathFor(output);
                Files.createDirectory(failedSidecar);
            }
            var metadata = (ObjectNode) JSON.readTree(dir.resolve("run.json").toFile());
            metadata.put("status", "process_exited");
            metadata.set("processResult", JSON.valueToTree(Map.of("exitCode", 0, "usability", "EXITED_ZERO", "durationMs", 1)));
            metadata.putArray("adapterSignals");
            JSON.writeValue(dir.resolve("run.json").toFile(), metadata);
            return NodeResult.completed("completed", List.of(base.root.relativize(output).toString()),
                    Map.of("run_id", runId, "exit_code", 0, "run_metadata", base.root.relativize(dir.resolve("run.json")).toString()));
        }
        void untilPause() throws Exception {
            for (int i = 0; i < 40; i++) if (runtime.resume(new WorkflowCommand(base.root, base.ws)).status() != WorkflowStatus.RUNNING) return;
            fail("workflow did not pause");
        }
        String node() throws Exception { var ids = base.ledger.loadCurrent(base.paths).activeNodeInstanceIds(); return ids.isEmpty() ? "" : base.ledger.loadNode(base.paths, ids.getFirst()).nodeDefinitionId(); }
        String current() throws Exception { var ids = base.ledger.loadCurrent(base.paths).activeNodeInstanceIds(); return ids.isEmpty() ? calls.toString() : base.ledger.loadNode(base.paths, ids.getFirst()).toString(); }
        dev.dual.ledger.file.NodeInstanceDocument planDecision() throws Exception {
            var gate = base.ledger.loadNode(base.paths, base.ledger.loadCurrent(base.paths).activeNodeInstanceIds().getFirst());
            return base.ledger.loadNode(base.paths, String.valueOf(gate.inputTransition().get("source_node_instance_id")));
        }
        static NodeDefinition agent(String id, String artifact) { return new NodeDefinition(id, NodeKind.TASK, "acpx.agent_run", Map.of("expected_artifact", artifact, "required_contract_phase", id.startsWith("implement") ? "EXECUTION" : "GOAL")); }
        static NodeDefinition context(String id, String target) { return new NodeDefinition(id, NodeKind.TASK, "context.build_package", Map.of("target_node", target, "required_contract_phase", "GOAL", "profile", target.startsWith("design") ? "architecture_review" : "plan_review")); }
        static NodeDefinition decision(String id) { return new NodeDefinition(id, NodeKind.DECISION, "review.decision", Map.of()); }
        static EdgeDefinition edge(String from, String on, String to) { return new EdgeDefinition(from, on, to); }
    }
}
