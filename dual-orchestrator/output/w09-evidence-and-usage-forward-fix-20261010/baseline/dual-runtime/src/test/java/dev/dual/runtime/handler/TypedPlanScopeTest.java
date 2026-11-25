package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.dual.core.workflow.WorkflowStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class TypedPlanScopeTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    @TempDir Path root;

    @Test
    void invalidPlansCannotPublishOrReachAnyPlanSuccessorInEitherBusiness() throws Exception {
        int sample = 0;
        for (var business : List.of("converter/render.py", "site/home.html")) {
            for (var topology : AuthoredStageSequenceTest.PlanTopology.values()) {
                for (var edits : List.of(List.of("outside/file.txt"), List.of("../escape.txt"),
                        List.of(business, business), List.<String>of())) {
                    var fixture = new AuthoredStageSequenceTest.Fixture(root.resolve("case-" + sample++), business, topology);
                    fixture.before("plan.claude_plan");
                    fixture.outputMutation = result -> {
                        if (result.has("plan")) {
                            var files = ((ObjectNode) result.get("plan")).putArray("edit_files");
                            edits.forEach(files::add);
                        }
                    };
                    fixture.advanceToGate();
                    assertEquals(WorkflowStatus.WAITING_FOR_RECOVERY, fixture.ledger.loadCurrent(fixture.paths).status());
                    assertEquals("plan.claude_plan", fixture.calls.getLast());
                    assertFalse(fixture.calls.contains("plan.codex_plan_review"));
                    assertFalse(fixture.calls.contains("implement.claude_implement"));
                    var snapshot = fixture.inputs.get("plan.claude_plan");
                    var outputs = fixture.runDir(snapshot.runId()).resolve("outputs");
                    assertTrue(Files.isRegularFile(outputs.resolve("result.json")), "raw candidate remains evidence only");
                    assertFalse(Files.exists(outputs.resolve("result.contract.json")));
                    assertFalse(Files.exists(outputs.resolve("result.plan.json")));
                    assertFalse(Files.exists(fixture.paths.workstreamDir().resolve("artifacts/plan/plan_artifact.json")));
                    try (var nodes = Files.list(fixture.paths.nodesDir())) {
                        assertFalse(nodes.anyMatch(path -> path.getFileName().toString().contains("human_plan_approval")),
                                "an invalid plan must not create an approval gate");
                    }
                }
            }
        }
    }

    @Test
    void scopeShapeIsNotRepairableAndPromptDoesNotDelegateEnforcementToTheModel() throws Exception {
        for (var edits : List.of(List.of("../escape"), List.of("src/a", "src/a"), List.of("./src/a"),
                List.of("src//a"), List.of("src/"), List.of("/absolute"), List.of(".agent/file"),
                List.of(".git/config"), List.of("src\\a"), List.of(" src/a"), List.of("src/a "),
                List.of("C:/src/a"))) {
            var prompt = AgentResultPromptContract.render("ws", "run", "plan.claude_plan",
                    "READY_FOR_PLAN_REVIEW", Set.of("READY_FOR_PLAN_REVIEW"));
            assertTrue(prompt.contains("edit_files must contain unique canonical proposed paths respecting the approved GOAL exclusions"));
            var candidate = (ObjectNode) JSON.readTree(prompt.substring(prompt.indexOf('{')));
            var files = ((ObjectNode) candidate.at("/result/plan")).putArray("edit_files");
            edits.forEach(files::add);
            var parsed = new AgentResultEnvelopeParser().parse(candidate.toString());
            assertTrue(parsed.valid());
            var result = new AgentResultEnvelopeValidator().validate(parsed.envelope(), "ws", "run", ContractRegistry.forNode("plan.claude_plan"));
            assertFalse(result.valid());
            assertEquals("NOT_REPAIRABLE", result.correctionDisposition().name());
        }
    }

    @Test
    void approvedPlanKeepsOccurrenceProjectionWithoutPublishingCanonicalPlan() throws Exception {
        for (var business : List.of("converter/render.py", "site/home.html", "renamed/render file.py", "renamed/home page.html")) {
            var fixture = new AuthoredStageSequenceTest.Fixture(root.resolve("case-" + Math.abs(business.hashCode())), business);
            fixture.before("plan.claude_plan");
            fixture.advanceToGate();
            assertEquals("gate.human_plan_approval", fixture.currentNode());
            var output = fixture.runDir(fixture.inputs.get("plan.claude_plan").runId()).resolve("outputs");
            assertTrue(Files.isRegularFile(output.resolve("result.contract.json")));
            assertTrue(Files.isRegularFile(output.resolve("result.plan.json")));
            assertFalse(Files.exists(fixture.paths.workstreamDir().resolve("artifacts/plan/plan_artifact.json")));
            assertFalse(Files.exists(fixture.root.resolve(business)), "planned CREATE need not already exist");
            fixture.approve();
            assertEquals(WorkflowStatus.COMPLETED, fixture.ledger.loadCurrent(fixture.paths).status());
            assertEquals(fixture.inputs.get("implement.claude_implement").reviewedPlan(),
                    fixture.inputs.get("implement.claude_self_test_report").reviewedPlan());
        }
    }

    @Test
    void everyPlanTopologySurvivesColdReadAndFeedsTheSameApprovedIdentityToImplementationAndSelfTest() throws Exception {
        int sample = 0;
        for (var business : List.of("converter/render.py", "site/home.html")) {
            for (var topology : AuthoredStageSequenceTest.PlanTopology.values()) {
                var f = new AuthoredStageSequenceTest.Fixture(root.resolve("case-" + sample++), business, topology);
                var context = f.before("implement.claude_implement");
                var expected = AuthoredExecutionContext.prepare(context, "cold-plan").orElseThrow();
                var output = f.root.resolve("cold-plan.json");
                var error = f.root.resolve("cold-plan.err");
                var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin/java").toString(),
                        "-cp", System.getProperty("java.class.path"), AuthoredBoundarySequenceTest.ColdRead.class.getName(),
                        f.root.toString(), f.ws, context.instance().nodeInstanceId(), "cold-plan")
                        .redirectOutput(output.toFile()).redirectError(error.toFile()).start();
                try {
                    assertTrue(process.waitFor(20, TimeUnit.SECONDS));
                    assertEquals(0, process.exitValue(), Files.readString(error));
                    assertEquals(expected, JSON.readValue(output.toFile(), AuthoredExecutionContext.Snapshot.class));
                } finally { if (process.isAlive()) process.destroyForcibly(); }
                f.advanceToGate();
                assertEquals(WorkflowStatus.COMPLETED, f.ledger.loadCurrent(f.paths).status());
                assertEquals(expected.reviewedPlan(), f.inputs.get("implement.claude_implement").reviewedPlan());
                assertEquals(expected.reviewedPlan(), f.inputs.get("implement.claude_self_test_report").reviewedPlan());
            }
        }
    }

    @Test
    void publicationRequiresCurrentQualificationButDoesNotRequireEveryContractPathOrReferenceInEditScope() throws Exception {
        int sample = 0;
        for (var business : List.of("converter/render.py", "site/home.html")) {
            for (var damage : List.of("none", "missing-contract", "missing-gate", "tampered-contract")) {
                var f = new AuthoredStageSequenceTest.Fixture(root.resolve("case-" + sample++), business);
                var context = dev.dual.runtime.RuntimeTestLedger.claim(f.before("plan.claude_plan"));
                var qualification = TaskContractProjectionVerifier.resolve(f.ledger, f.root, f.ws,
                        dev.dual.core.contract.TaskContract.Phase.GOAL).qualified().orElseThrow();
                var contractPath = f.root.resolve(qualification.contractRef());
                f.outputMutation = result -> ((ObjectNode) result.get("plan")).putArray("reference_files").add("unscoped/reference.md");
                var result = f.executeProvider(context);
                switch (damage) {
                    case "missing-contract" -> Files.delete(contractPath);
                    case "missing-gate" -> Files.delete(f.root.resolve(qualification.approvalGateRef()));
                    case "tampered-contract" -> Files.writeString(contractPath, Files.readString(contractPath) + " ");
                    default -> { }
                }
                var validated = new ArtifactContractValidator(dev.dual.runtime.RuntimeTestLedger.ledger()).validate(f.root, f.ws, context.instance().nodeInstanceId(),
                        context.definition(), result);
                if (damage.equals("none")) {
                    assertEquals(dev.dual.core.workflow.NodeStatus.COMPLETED, validated.status(), validated.toString());
                } else {
                    assertEquals(dev.dual.core.workflow.NodeStatus.FAILED, validated.status());
                    assertTrue(validated.result().toString().contains("PLAN_CONTRACT_NOT_QUALIFIED"), validated.toString());
                    assertFalse(Files.exists(AgentOutputContractSidecar.pathFor(f.root.resolve(result.outputRefs().getFirst()))));
                }
            }
        }
    }

    @Test
    void planWriteFailuresRemainIoFailuresAndColdReadersCannotUsePartialPublication() throws Exception {
        int sample = 0;
        for (var business : List.of("converter/render.py", "site/home.html")) {
            for (var blockedFile : List.of("result.contract.json", "result.plan.json")) {
                var f = new AuthoredStageSequenceTest.Fixture(root.resolve("case-" + sample++), business);
                var context = dev.dual.runtime.RuntimeTestLedger.claim(f.before("plan.claude_plan"));
                var result = f.executeProvider(context);
                var output = f.root.resolve(result.outputRefs().getFirst());
                Files.createDirectory(output.getParent().resolve(blockedFile));
                assertThrows(java.io.IOException.class, () -> new ArtifactContractValidator(dev.dual.runtime.RuntimeTestLedger.ledger()).validate(
                        f.root, f.ws, context.instance().nodeInstanceId(), context.definition(), result));
                assertEquals(blockedFile.equals("result.plan.json"),
                        Files.isRegularFile(AgentOutputContractSidecar.pathFor(output)));
                var stdout = f.root.resolve("cold-publication.txt");
                var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin/java").toString(),
                        "-cp", System.getProperty("java.class.path"), ColdPublicationRead.class.getName(),
                        f.root.toString(), f.ws, result.outputRefs().getFirst())
                        .redirectOutput(stdout.toFile()).redirectError(f.root.resolve("cold-publication.err").toFile()).start();
                try {
                    assertTrue(process.waitFor(20, TimeUnit.SECONDS));
                    assertEquals(0, process.exitValue(), Files.readString(f.root.resolve("cold-publication.err")));
                    assertEquals("REJECTED", Files.readString(stdout).trim());
                } finally { if (process.isAlive()) process.destroyForcibly(); }
                assertFalse(f.calls.contains("implement.claude_implement"));
                assertFalse(Files.exists(f.paths.workstreamDir().resolve("artifacts/plan/plan_artifact.json")));
            }
        }
    }

    public static class ColdPublicationRead {
        public static void main(String[] args) {
            try {
                if (AgentEvidenceIndex.validatedDocument(dev.dual.runtime.RuntimeTestLedger.ledger(), Path.of(args[0]), args[1], args[2], JSON).isPresent())
                    throw new AssertionError("partial publication became usable evidence");
            } catch (IllegalStateException expected) {
                // A physically present sidecar with no completed producer is also a rejection.
            }
            System.out.println("REJECTED");
        }
    }

    @Test
    void approvedPlanDoesNotExcuseActualGitScopeViolation() throws Exception {
        for (var business : List.of("converter/render.py", "site/home.html")) {
            var f = new AuthoredStageSequenceTest.Fixture(root.resolve("case-" + Math.abs(business.hashCode())), business);
            git(f.root, "init"); git(f.root, "config", "user.email", "dual@example.test");
            git(f.root, "config", "user.name", "Dual Test");
            Files.writeString(f.root.resolve(".gitignore"), ".agent/\n");
            git(f.root, "add", ".gitignore", "test-tool.lock"); git(f.root, "commit", "-m", "fixture baseline");
            var base = git(f.root, "rev-parse", "HEAD").trim();
            var workspace = f.paths.workstreamDir().resolve("workspaces/workspace-readiness.json");
            var workspaceDoc = (ObjectNode) JSON.readTree(workspace.toFile());
            workspaceDoc.put("baseCommit", base); JSON.writeValue(workspace.toFile(), workspaceDoc);
            f.before("implement.claude_implement");
            f.advanceToGate();
            assertEquals(WorkflowStatus.COMPLETED, f.ledger.loadCurrent(f.paths).status());
            Files.createDirectories(f.root.resolve(business).getParent());
            Files.writeString(f.root.resolve(business), "authorized output\n");
            var definition = new dev.dual.core.workflow.NodeDefinition("implement.artifact_validation",
                    dev.dual.core.workflow.NodeKind.TASK, "artifact.validate", Map.of("required_contract_phase", "EXECUTION"));
            var instance = new dev.dual.core.workflow.NodeInstance("node-git-scope", definition.id(), definition.kind(), definition.handlerType(),
                    dev.dual.core.workflow.NodeStatus.READY, "implement", "iter-001", List.of(), List.of(), Map.of(), null, null, null);
            var context = new TaskHandler.TaskContext(f.root, f.ws, definition, instance, dev.dual.runtime.RuntimeTestLedger.ledger());
            var handler = new ArtifactValidateHandler();
            var good = handler.execute(context);
            assertEquals("PASS", good.result().get("change_scope_status"), good.toString());
            Files.writeString(f.root.resolve("unauthorized.txt"), "must be rejected\n");
            var bad = handler.execute(context);
            assertEquals("FAIL", bad.result().get("change_scope_status"), bad.toString());
            assertNotEquals(dev.dual.core.workflow.NodeStatus.COMPLETED, bad.status());
        }
    }

    private static String git(Path root, String... args) throws Exception {
        var command = new java.util.ArrayList<>(List.of("git")); command.addAll(List.of(args));
        var process = new ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true).start();
        var output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), output);
        return output;
    }
}
