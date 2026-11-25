package dev.dual.adapter.acpx;

import dev.dual.core.workflow.WorkflowStatus;
import dev.dual.runtime.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Same real pipeline and declared variants; only the external provider is replaced. */
class WorkflowFirstLifecycleSequenceTest {
    @TempDir Path root;

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void providerFailureCannotPublishEvenWhenItReturnsAValidCandidate(boolean queued) throws Exception {
        var f = new HumanRequirementContinuationSequenceTest.Fixture(root, queued);
        f.transport.providerDenied = true;
        f.untilGate();
        assertEquals(WorkflowStatus.WAITING_FOR_RECOVERY, f.ledger.loadCurrent(f.paths).status());
        assertEquals(1, f.transport.calls.size());
        var outcome = (Map<?, ?>) f.active().result().get("agent_execution_outcome");
        assertEquals("rate_limited", outcome.get("primaryReason"));
        assertTrue(f.active().outputRefs().stream().noneMatch(ref -> ref.endsWith(".contract.json")),
                "failed provider execution must not publish a business sidecar");
        try (var files = java.nio.file.Files.walk(f.paths.workstreamDir().resolve("runs"))) {
            assertTrue(files.noneMatch(path -> path.toString().endsWith(".contract.json")));
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void incompleteProcessCannotBeReclassifiedAsMissingArtifactAfterPublicationIsBlocked(boolean queued) throws Exception {
        var f = new HumanRequirementContinuationSequenceTest.Fixture(root, queued);
        f.transport.process = dev.dual.core.execution.ProcessResult.nonZeroIncomplete(5);
        f.untilGate();
        assertEquals(WorkflowStatus.WAITING_FOR_RECOVERY, f.ledger.loadCurrent(f.paths).status());
        assertEquals(List.of("intake.claude_brainstorm"), f.transport.calls);
        var node = f.active();
        var outcome = (Map<?, ?>) node.result().get("agent_execution_outcome");
        assertEquals("process_output_incomplete", outcome.get("primaryReason"));
        var run = HumanRequirementContinuationSequenceTest.JSON.readTree(
                root.resolve(node.result().get("run_metadata").toString()).toFile());
        assertEquals("process_output_incomplete", run.at("/executionOutcome/primaryReason").asText());
        assertTrue(run.at("/artifactContractResults/0/valid").asBoolean(),
                "a valid candidate does not become invalid because execution prevents publication");
        var recovery = dev.dual.ledger.file.RecoveryAssessment.from(node, root, f.ws);
        assertEquals("agent_output_incomplete", recovery.classification());
        assertEquals("RETRY_AFTER_EXTERNAL_FIX", recovery.retryReadiness());
        assertFalse(recovery.retrySafe());
        assertTrue(java.nio.file.Files.readString(f.paths.currentFailureMarkdown())
                .contains("- Reason: process_output_incomplete\n"));
        try (var files = java.nio.file.Files.walk(f.paths.workstreamDir().resolve("runs"))) {
            assertTrue(files.noneMatch(path -> path.toString().endsWith(".contract.json")));
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void interfaceFailureKeepsRunNodeRecoveryAndSummaryConsistentAcrossColdRead(boolean queued) throws Exception {
        var f = new HumanRequirementContinuationSequenceTest.Fixture(root, queued);
        f.transport.interfaceIncompatible = true;
        f.untilGate();
        assertEquals(WorkflowStatus.WAITING_FOR_RECOVERY, f.ledger.loadCurrent(f.paths).status());
        var node = f.active();
        assertEquals("adapter_incompatible", ((Map<?, ?>) node.result().get("agent_execution_outcome")).get("primaryReason"));
        var run = HumanRequirementContinuationSequenceTest.JSON.readTree(root.resolve(node.result().get("run_metadata").toString()).toFile());
        assertEquals("adapter_incompatible", run.at("/executionOutcome/primaryReason").asText());
        var summary = java.nio.file.Files.readString(f.paths.currentFailureMarkdown());
        assertTrue(summary.contains("- Reason: adapter_incompatible\n"), summary);
        assertFalse(summary.contains("- Reason: AGENT_OUTPUT_CONTRACT_VIOLATION\n"));
        assertTrue(summary.contains("## Secondary Contract Diagnostic"), summary);
        assertFalse(summary.contains("## Contract Failure Diagnostic"));
        assertEquals("agent_interface_incompatible", dev.dual.ledger.file.RecoveryAssessment.from(node, root, f.ws).classification());
        assertTrue(node.outputRefs().stream().noneMatch(ref -> ref.endsWith(".contract.json")));
        var child = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin/java").toString(), "-cp",
                System.getProperty("java.class.path"), FailureColdReader.class.getName(), root.toString(), f.ws).start();
        assertTrue(child.waitFor(30, java.util.concurrent.TimeUnit.SECONDS));
        assertEquals(0, child.exitValue(), new String(child.getErrorStream().readAllBytes()));
        assertEquals("adapter_incompatible/agent_interface_incompatible/true",
                new String(child.getInputStream().readAllBytes()));
    }

    public static class FailureColdReader {
        public static void main(String[] args) throws Exception {
            var root = Path.of(args[0]);
            var paths = new dev.dual.ledger.file.WorkstreamPaths(root, new dev.dual.core.id.DualId(args[1]));
            var ledger = AcpxTestLedger.ledger();
            var current = ledger.loadCurrent(paths);
            var node = ledger.loadNode(paths, current.activeNodeInstanceIds().getFirst());
            var reason = ((Map<?, ?>) node.result().get("agent_execution_outcome")).get("primaryReason");
            var recovery = dev.dual.ledger.file.RecoveryAssessment.from(node, root, args[1]);
            System.out.print(reason + "/" + recovery.classification() + "/" +
                    java.nio.file.Files.readString(paths.currentFailureMarkdown()).contains("- Reason: " + reason + "\n"));
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void trustworthyCompleteProcessWarningPublishesAndReachesIndependentReview(boolean queued) throws Exception {
        var f = new HumanRequirementContinuationSequenceTest.Fixture(root, queued);
        f.transport.process = dev.dual.core.execution.ProcessResult.nonZeroComplete(5);
        f.untilGate();
        assertEquals(WorkflowStatus.WAITING_FOR_HUMAN, f.ledger.loadCurrent(f.paths).status());
        assertEquals("gate.human_requirement_review", f.active().nodeDefinitionId());
        assertEquals(List.of("intake.claude_brainstorm", "intake.codex_product_review"), f.transport.calls);
        try (var files = java.nio.file.Files.walk(f.paths.workstreamDir().resolve("runs"))) {
            var runs = files.filter(path -> path.getFileName().toString().equals("run.json")).toList();
            assertEquals(2, runs.size());
            for (var path : runs) {
                var run = HumanRequirementContinuationSequenceTest.JSON.readTree(path.toFile());
                assertEquals("SUCCESS_WITH_WARNINGS", run.at("/executionOutcome/status").asText());
                long decisions = java.util.stream.StreamSupport.stream(run.path("executionTimeline").spliterator(), false)
                        .filter(event -> "contract_validated".equals(event.path("kind").asText())).count();
                assertEquals(1, decisions, "publication must not trigger a second execution decision");
                assertTrue(run.at("/artifactContractResults/0/valid").asBoolean());
            }
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void preparationStorageFailureStopsBeforeProviderAndRetainsTheException(boolean queued) throws Exception {
        var f = new HumanRequirementContinuationSequenceTest.Fixture(root, queued);
        f.manifestWriter = new PromptManifestWriter() {
            @Override public void write(Path path, String ws, String run, String node, String agent, String template,
                    Path prompt, List<AgentPromptRenderer.PromptSection> sections,
                    dev.dual.runtime.handler.AuthoredExecutionContext.Snapshot snapshot) throws java.io.IOException {
                throw new java.io.IOException("simulated preparation storage failure");
            }
        };
        f.untilGate();
        assertEquals(WorkflowStatus.WAITING_FOR_RECOVERY, f.ledger.loadCurrent(f.paths).status());
        assertTrue(f.transport.calls.isEmpty());
        assertTrue(f.active().outputRefs().isEmpty());
        assertEquals("INPUT_PREPARATION_FAILED", f.active().result().get("reason"));
        assertTrue(f.active().result().get("failure_code").toString().contains("Exception"));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void publishedCandidateWaitsForLocalCompletionBeforeIndependentReview(boolean queued) throws Exception {
        var f = new HumanRequirementContinuationSequenceTest.Fixture(root, queued);
        f.ledger = dev.dual.ledger.file.InterruptedTestLedger.onTarget(target -> {
            if (f.transport.completedCandidateNode == null
                    || !target.getFileName().toString().equals(f.transport.completedCandidateNode + ".json")) return false;
            try (var files = java.nio.file.Files.walk(f.paths.workstreamDir().resolve("runs"))) {
                return files.anyMatch(path -> path.toString().endsWith(".contract.json"));
            } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
        }, Map.of("test_binary", "acpx-tests-v3"));
        assertThrows(Exception.class, f::untilGate);
        assertEquals(List.of("intake.claude_brainstorm"), f.transport.calls);
        var cold = AcpxTestLedger.ledger();
        assertThrows(dev.dual.ledger.file.LedgerSession.PendingCommitException.class, () -> cold.loadCurrent(f.paths));
        try (var write = cold.writeSession(f.paths, "finish fixed completion", "recover")) { write.commit(); }
        f.ledger = cold;
        f.untilGate();
        assertEquals("gate.human_requirement_review", f.active().nodeDefinitionId());
        assertEquals(List.of("intake.claude_brainstorm", "intake.codex_product_review"), f.transport.calls,
                "finish-forward must not repeat the author and must permit the independent reviewer");
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void removedApprovedContractStopsTheNextAuthorBeforeProvider(boolean queued) throws Exception {
        var f = new HumanRequirementContinuationSequenceTest.Fixture(root, queued);
        f.untilGate();
        dev.dual.runtime.handler.ReviewGateActions.resolve(f.ledger, f.paths, "approve", "Approve reviewed requirements");
        f.untilGate();
        assertEquals("gate.contract_review", f.active().nodeDefinitionId());
        dev.dual.runtime.handler.ReviewGateActions.resolve(f.ledger, f.paths, "approve", "Approve this compiled contract");
        var contract = f.root.resolve(f.active().inputRefs().stream().filter(r -> r.endsWith("/task-contract.json")).findFirst().orElseThrow());
        var approvedBytes = java.nio.file.Files.readAllBytes(contract);
        java.nio.file.Files.delete(contract);
        int before = f.transport.calls.size();
        f.untilGate();
        assertEquals(WorkflowStatus.WAITING_FOR_RECOVERY, f.ledger.loadCurrent(f.paths).status());
        assertEquals(before, f.transport.calls.size());
        assertTrue(f.active().outputRefs().isEmpty());
        assertEquals("ARTIFACT_MISSING", f.active().result().get("reason"));
        assertFalse(f.active().result().toString().contains("AGENT_RESULT_MISSING"));
        var recovery = dev.dual.ledger.file.RecoveryAssessment.from(f.active(), f.root, f.ws);
        assertEquals("qualification_input_drift", recovery.classification(), recovery.toString());
        assertEquals("PAIRED", ((Map<?, ?>)f.active().result().get("continuation_failure")).get("status"));
        assertFalse(f.active().result().containsKey("input_preparation_failure"), "a local context task is not a native run");
        assertFalse(recovery.retrySafe());
        var failedId = f.active().nodeInstanceId();
        java.nio.file.Files.write(contract, approvedBytes);
        f.ledger.retryActiveNode(f.paths, "The exact previously approved bytes have been restored");
        f.untilGate();
        assertEquals("gate.human_acceptance", f.active().nodeDefinitionId());
        assertEquals(dev.dual.core.workflow.NodeStatus.COMPLETED, f.ledger.loadNode(f.paths, failedId).status());
        assertTrue(f.transport.calls.subList(before, f.transport.calls.size()).containsAll(List.of("implement.claude_implement", "acceptance.codex_verifier_review")), "exact restoration permits implementation and independent verification");
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void unavailablePrerequisitesRequireHumanDispositionThenEvidenceReverification(boolean queued) throws Exception {
        for (var variant : List.of("small", "terminal-delivery", "contract", "full")) {
            for (var category : List.of("TOOL_LIMITATION", "CONTEXT_INSUFFICIENT")) {
                for (var action : List.of("collect_evidence", "revise")) {
                    var f = new HumanRequirementContinuationSequenceTest.Fixture(root.resolve(variant).resolve(category).resolve(action), queued, variant);
                    f.transport.acceptanceFailure = category;
                    for (var gate : List.of("gate.human_requirement_review", "gate.contract_review")) {
                        f.untilGate();
                        assertEquals(gate, f.active().nodeDefinitionId());
                        f.ledger.resolveOpenGate(f.paths, f.definition, "approve", "Approve the reviewed subject");
                    }
                    f.untilGate();
                    assertEquals("gate.human_implementation_repair", f.active().nodeDefinitionId(), category);
                    assertEquals(1, Collections.frequency(f.transport.calls, "implement.claude_implement"));
                    var source = java.nio.file.Files.readString(f.root.resolve("README.md"));
                    dev.dual.runtime.handler.EvidenceCollection.resolveGate(f.ledger, f.paths, f.definition,
                            action, action.equals("revise") ? "Repair the approved documentation to address the prerequisite"
                                    : "Prerequisite is now available; verify against the unchanged product");
                    f.untilGate();
                    assertEquals("gate.human_acceptance", f.active().nodeDefinitionId());
                    assertEquals(action.equals("revise") ? 2 : 1,
                            Collections.frequency(f.transport.calls, "implement.claude_implement"));
                    assertEquals(2, Collections.frequency(f.transport.calls, "implement.claude_self_test_report"));
                    assertEquals(2, Collections.frequency(f.transport.calls, "acceptance.codex_verifier_review"));
                    assertEquals(source, java.nio.file.Files.readString(f.root.resolve("README.md")));
                }
            }
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void acceptedProductTraversesEveryGateAndTerminalPreventsNewExecution(boolean queued) throws Exception {
        for (var variant : List.of("small", "terminal-delivery", "contract", "full")) {
            var f = new HumanRequirementContinuationSequenceTest.Fixture(root.resolve(variant), queued, variant);
            var target = root.resolve(variant + "-target");
            f.git("worktree", "add", "--detach", target.toString(), "HEAD");
            // Initialize the repository's actual target before any author/reviewer/gate runs.
            var domain = (com.fasterxml.jackson.databind.node.ObjectNode) HumanRequirementContinuationSequenceTest.JSON.readTree(f.paths.workstreamJson().toFile());
            var repo = (com.fasterxml.jackson.databind.node.ObjectNode) domain.get("repository");
            repo.put("originalWorkdir", target.toString()); repo.put("gitRoot", target.toString());
            HumanRequirementContinuationSequenceTest.JSON.writeValue(f.paths.workstreamJson().toFile(), domain);
            var delivery = new dev.dual.runtime.delivery.DeliveryApplicationService(f.ledger);
            var gates = new ArrayList<String>();
            for (int i = 0; i < 12; i++) {
                f.untilGate();
                var current = f.ledger.loadCurrent(f.paths);
                if (current.status() == WorkflowStatus.COMPLETED) break;
                assertEquals(WorkflowStatus.WAITING_FOR_HUMAN, current.status(), f.active().toString());
                gates.add(f.active().nodeDefinitionId());
                if (f.active().nodeDefinitionId().equals("gate.human_acceptance")) {
                    assertEquals("WORKFLOW_NOT_DELIVERABLE", delivery.apply(f.root, f.ws, target).reasonCode());
                    assertEquals("Generic utility\n", java.nio.file.Files.readString(target.resolve("README.md")));
                }
                var decidedId = f.active().nodeInstanceId();
                var action = f.active().nodeDefinitionId().equals("gate.human_next_stage_approval") ? "complete" : "approve";
                dev.dual.runtime.handler.ReviewGateActions.resolve(f.ledger, f.paths, action, "Accept the exact reviewed evidence");
                if (gates.getLast().equals("gate.contract_review"))
                    assertEquals("COVERED", ((Map<?, ?>) f.ledger.loadNode(f.paths, decidedId).result().get("decision_checks")).get("familyCoverage"));
            }
            assertEquals(WorkflowStatus.COMPLETED, f.ledger.loadCurrent(f.paths).status());
            var expectedGates = List.of("gate.human_requirement_review", "gate.contract_review", "gate.human_acceptance");
            assertEquals(expectedGates, gates);
            assertTrue(f.transport.calls.contains("plan.codex_plan_review"), "each current variant requires independent plan review");
            assertTrue(f.transport.calls.containsAll(List.of("implement.claude_implement", "implement.claude_self_test_report",
                    "acceptance.codex_verifier_review")));
            int calls = f.transport.calls.size();
            var before = f.snapshot();
            f.runtime().resume(new WorkflowCommand(f.root, f.ws));
            if (queued) new AgentWorker(f.ledger, f.handlers()).runOne(new WorkflowCommand(f.root, f.ws));
            assertThrows(Exception.class, () -> f.ledger.retryActiveNode(f.paths, "cannot reopen terminal"));
            assertThrows(Exception.class, () -> f.ledger.resolveOpenGate(f.paths, f.definition, "approve", "cannot reapprove"));
            assertEquals(calls, f.transport.calls.size());
            assertEquals(before, f.snapshot(), "terminal operations cannot create execution or rewrite evidence");
            var applied = delivery.apply(f.root, f.ws, target);
            assertEquals("APPLIED", applied.status(), applied.toString());
            assertFalse(applied.canFinalize(), "staging is not a commit");
            assertTrue(applied.currentActions().contains("COMMIT_EXTERNALLY"));
            assertEquals(applied.operationId(), delivery.apply(f.root, f.ws, target).operationId());
            f.git("-C", target.toString(), "-c", "user.name=Fixture", "-c", "user.email=fixture@example.test", "commit", "-m", "approved delivery");
            var sha = f.git("-C", target.toString(), "rev-parse", "HEAD").trim();
            assertTrue(delivery.view(f.root, f.ws, target).canFinalize());
            assertEquals("COMMITTED", delivery.finalizeCommit(f.root, f.ws, sha).status());
            assertEquals("COMMITTED", delivery.finalizeCommit(f.root, f.ws, sha).status());
            assertEquals(calls, f.transport.calls.size(), "delivery/replay never invokes providers");

        }
    }
}
