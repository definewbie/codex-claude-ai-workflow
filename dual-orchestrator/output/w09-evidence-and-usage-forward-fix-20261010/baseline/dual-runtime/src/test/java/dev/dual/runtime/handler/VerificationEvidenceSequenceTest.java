package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dual.core.workflow.WorkflowStatus;
import dev.dual.runtime.WorkflowCommand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Offline program observations through the real lifecycle; not native/provider capability proof. */
class VerificationEvidenceSequenceTest {
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    @TempDir Path root;

    @Test void legitimateNoExitEvidenceCanBeConsumedAgainButSourceOrBytesDriftCannot() throws Exception {
        var f = new AcceptanceRepairSequenceTest.Fixture(root, "IMPLEMENTATION_BUG", "product.txt");
        f.until("implement.claude_self_test_report");
        var node = f.ledger.loadCurrent(f.paths).activeNodeInstanceIds().getFirst();
        f.step();
        var completed = f.ledger.loadNode(f.paths, node);
        var sidecar = completed.outputRefs().stream().filter(r -> r.endsWith(".contract.json")).findFirst().orElseThrow();
        var document = AgentOutputContractDocument.read(root.resolve(sidecar), JSON).orElseThrow();
        VerificationEvidence.verifyPublished(f.ledger, root, f.ws, document);
        // A fresh reader may reuse the exact fixed result; no exit requirement was approved.
        VerificationEvidence.verifyPublished(dev.dual.runtime.RuntimeTestLedger.ledger(), root, f.ws, document);
        var fixedExecution = f.ledger.latestNodeExecution(f.paths, completed).orElseThrow();
        var publication = AgentRunCommitProtocol.recordedPublication(f.root, f.paths, fixedExecution);
        var fixedResult = AgentRunCommitProtocol.readCompletion(f.paths, fixedExecution);
        assertNotNull(publication);
        publication.publish(f.ledger, f.root, fixedResult);
        var observation = completed.outputRefs().stream().filter(r -> r.endsWith("verification-observations.json")).findFirst().orElseThrow();
        assertTrue(JSON.readTree(root.resolve(observation).toFile()).path("tools").get(0).path("actualExitCode").isNull());
        Files.writeString(root.resolve("product.txt"), "late child write\n");
        assertThrows(java.io.IOException.class, () -> VerificationEvidence.verifyPublished(f.ledger, root, f.ws, document));
        assertThrows(java.io.IOException.class, () -> publication.publish(f.ledger, f.root, fixedResult));
        Files.writeString(root.resolve("product.txt"), "output\n");
        VerificationEvidence.verifyPublished(f.ledger, root, f.ws, document);
        publication.publish(f.ledger, f.root, fixedResult);
        Files.writeString(root.resolve(observation), "{}\n");
        assertThrows(java.io.IOException.class, () -> VerificationEvidence.verifyPublished(f.ledger, root, f.ws, document));
    }

    @Test void driftBeforeContextBeforePreparationAndBeforeDispatchNeverCallsTheReviewer() throws Exception {
        for (var point : List.of("context", "prepare", "dispatch")) {
            var f = new AcceptanceRepairSequenceTest.Fixture(root.resolve(point), "IMPLEMENTATION_BUG", "product.txt");
            f.until(point.equals("context") ? "context.acceptance_codex_verifier_review" : "acceptance.codex_verifier_review");
            if (point.equals("dispatch")) f.beforeDispatch = context -> {
                if (context.definition().id().equals("acceptance.codex_verifier_review")) {
                    try { Files.writeString(f.root.resolve("product.txt"), "late write"); }
                    catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
                }
            };
            else Files.writeString(f.root.resolve("product.txt"), "late write");
            if (point.equals("context")) {
                f.runtime.resume(new WorkflowCommand(f.root, f.ws));
                assertEquals(WorkflowStatus.WAITING_FOR_RECOVERY, f.ledger.loadCurrent(f.paths).status(), point);
            } else {
                // This offline provider deliberately exposes the qualification exception. The
                // acpx handler maps the same typed failure to its normal preparation failure.
                var failure = assertThrows(AgentRunGovernanceContext.QualificationException.class,
                        () -> f.runtime.resume(new WorkflowCommand(f.root, f.ws)), point);
                assertTrue(failure.getMessage().contains("source changed"), failure.getMessage());
            }
            assertFalse(f.calls.contains("acceptance.codex_verifier_review"), point);
        }
    }

    @Test void approvedActualExitCannotBeSatisfiedByACompletedNativeStatus() throws Exception {
        var f = new AcceptanceRepairSequenceTest.Fixture(root, "IMPLEMENTATION_BUG", "product.txt");
        f.outputMutation = result -> {
            if (result.has("plan")) for (var binding : result.path("plan").path("verification_bindings"))
                ((com.fasterxml.jackson.databind.node.ObjectNode) binding).put("actual_exit_required", true).put("expected_exit_code", 0);
        };
        f.until("implement.claude_self_test_report");
        var node = f.ledger.loadCurrent(f.paths).activeNodeInstanceIds().getFirst();
        f.runtime.resume(new WorkflowCommand(f.root, f.ws));
        var failed = f.ledger.loadNode(f.paths, node);
        assertEquals(WorkflowStatus.WAITING_FOR_RECOVERY, f.ledger.loadCurrent(f.paths).status());
        assertTrue(JSON.valueToTree(failed).toString().contains("required actual process exit is unavailable"));
        assertTrue(failed.outputRefs().stream().noneMatch(r -> r.endsWith(".contract.json")));
    }

    @Test void inventedToolIdentityCannotPublishSuccessfulSelfTest() throws Exception {
        var f = new AcceptanceRepairSequenceTest.Fixture(root, "IMPLEMENTATION_BUG", "product.txt");
        f.outputMutation = result -> {
            if (result.has("verification_checks")) for (var check : result.path("verification_checks"))
                ((com.fasterxml.jackson.databind.node.ObjectNode) check).putArray("tool_call_ids").add("invented-call");
        };
        f.until("implement.claude_self_test_report");
        var node = f.ledger.loadCurrent(f.paths).activeNodeInstanceIds().getFirst();
        f.runtime.resume(new WorkflowCommand(f.root, f.ws));
        assertEquals(WorkflowStatus.WAITING_FOR_RECOVERY, f.ledger.loadCurrent(f.paths).status());
        assertTrue(JSON.valueToTree(f.ledger.loadNode(f.paths, node)).toString().contains("invented-call"));
    }
    @Test void measuredExitMustMatchTheSpecificApprovedRequirement() throws Exception {
        var binding = new dev.dual.core.contract.TaskContract.VerificationBinding("AC-1", "process exits 7",
                dev.dual.core.contract.TaskContract.Executor.AGENT, List.of(), List.of(),
                dev.dual.core.contract.TaskContract.EvidenceKind.PROGRAM_TOOL_EXECUTION_AND_ASSERTIONS, List.of(), "test", true, 7);
        var process = new ProcessBuilder("/bin/sh", "-c", "printf verified; exit 7").start();
        var output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        var exit = process.waitFor();
        var observed = new dev.dual.core.execution.AgentExecutionEvidence.ToolObservation("offline-session", "local-process", "fixture.exec", "execute",
                JSON.valueToTree(Map.of("command", "printf verified; exit 7")), JSON.valueToTree(Map.of("stdout", output)),
                "completed", 0, exit, "java.process.waitFor", false, List.of());
        VerificationEvidence.verifyExit(binding, observed);
        var reportRead = new dev.dual.core.execution.AgentExecutionEvidence.ToolObservation("offline-session", "read-report", "Read", "read",
                JSON.valueToTree(Map.of("path", "report.txt")), JSON.valueToTree(Map.of("content", output)),
                "completed", null, null, "", false, List.of());
        VerificationEvidence.verifySelectedExits(binding, List.of(observed, reportRead));
        assertThrows(java.io.IOException.class, () -> VerificationEvidence.verifySelectedExits(binding, List.of(reportRead)));
        var unmeasured = new dev.dual.core.execution.AgentExecutionEvidence.ToolObservation("offline-session", "another-command", "Bash", "execute",
                observed.input(), observed.output(), "completed", 0, null, "", false, List.of());
        assertThrows(java.io.IOException.class, () -> VerificationEvidence.verifySelectedExits(binding, List.of(observed, unmeasured)));
        var zeroRequired = new dev.dual.core.contract.TaskContract.VerificationBinding("AC-1", "process exits 0",
                binding.executor(), List.of(), List.of(), binding.evidenceKind(), List.of(), "test", true, 0);
        assertThrows(java.io.IOException.class, () -> VerificationEvidence.verifyExit(zeroRequired, observed));
    }

}
