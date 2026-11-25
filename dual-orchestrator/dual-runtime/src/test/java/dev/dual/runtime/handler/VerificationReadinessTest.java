package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dual.core.contract.*;
import dev.dual.core.domain.*;
import dev.dual.core.flow.BuiltInFlowOrchestrationKernel;
import dev.dual.core.id.DualId;
import dev.dual.core.workflow.*;
import dev.dual.ledger.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Operator readiness sequences against a real committed GOAL and waiting subject. No provider. */
class VerificationReadinessTest {
    @TempDir Path temporary;
    static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    @Test void readinessContextSeparatesFixedMethodFromDetailsWithoutInventingReadiness() throws Exception {
        var f = new Fixture(temporary);
        var empty = VerificationReadiness.context(f.ledger, f.root, f.ws, f.goal());
        assertTrue(empty.contains("copy its `method` exactly as the decoded JSON string"));
        assertTrue(empty.contains("in `plan.validation_plan`, labeled with approved criterion IDs"));
        assertTrue(empty.contains("report the missing readiness without inventing references"));
        var request = f.request("literal", VerificationReadiness.Action.RECORD, null);
        var ref = VerificationReadiness.record(f.ledger, f.root, f.ws, request);
        var events = Files.readAllBytes(f.paths.eventsJsonl());
        var bytes = Files.readAllBytes(f.root.resolve(ref.ref()));
        var context = VerificationReadiness.context(f.ledger, f.root, f.ws, f.goal());
        assertTrue(context.contains(ref.sha256()));
        assertTrue(context.contains(request.method()));
        assertArrayEquals(events, Files.readAllBytes(f.paths.eventsJsonl()));
        assertArrayEquals(bytes, Files.readAllBytes(f.root.resolve(ref.ref())));
        f.validate(ref);
    }

    @Test void usefulSuffixAndWhitespaceStillCannotChangeTheRegisteredMethod() throws Exception {
        var f = new Fixture(temporary);
        var ref = VerificationReadiness.record(f.ledger, f.root, f.ws, f.request("literal", VerificationReadiness.Action.RECORD, null));
        var bindings = f.bindings(ref);
        var original = bindings.getFirst();
        var goal = f.goal();
        for (var suffix : List.of("; AC-1: inspect the exact assertion", " ", "\n")) {
            var changed = new TaskContract.VerificationBinding(original.criterionId(), original.method() + suffix,
                    original.executor(), original.environmentRefs(), original.authorityRefs(), original.evidenceKind(),
                    original.feasibilityRefs(), original.detailOwner(), original.actualExitRequired(), original.expectedExitCode());
            try (var read = f.ledger.readSession(f.paths)) {
                assertThrows(IOException.class, () -> VerificationReadiness.validateBindings(read.ledger(), f.root, f.ws,
                        goal, List.of(changed, bindings.getLast())));
            }
        }
        f.validate(ref);
    }

    @Test void registerThenReuseDoesNotApproveAndDuplicateResponseIsStable() throws Exception {
        var f = new Fixture(temporary);
        var before = f.ledger.loadCurrent(f.paths);
        var request = f.request("r1", VerificationReadiness.Action.RECORD, null);
        var ref = VerificationReadiness.record(f.ledger, f.root, f.ws, request);
        var events = Files.readAllBytes(f.paths.eventsJsonl());
        assertEquals(ref, VerificationReadiness.record(f.ledger, f.root, f.ws, request));
        assertArrayEquals(events, Files.readAllBytes(f.paths.eventsJsonl()));
        var after = f.ledger.loadCurrent(f.paths);
        assertEquals(before.status(), after.status());
        assertEquals(before.activeNodeInstanceIds(), after.activeNodeInstanceIds());
        assertEquals(before.openGateIds(), after.openGateIds());
        assertEquals(before.lastEventId(), after.lastEventId());
        assertEquals(before.commitSequence() + 1, after.commitSequence());
        assertEquals("open", f.ledger.loadOpenGateForNode(f.paths, request.subject()).status());
        f.validate(ref);
        Files.writeString(f.root.resolve("unrelated-source.java"), "normal implementation changed unrelated source");
        f.validate(ref); // Source changes are not blanket environment invalidation.
        assertTrue(VerificationReadiness.context(f.ledger, f.root, f.ws, f.goal()).contains(ref.sha256()));
        assertThrows(IOException.class, () -> VerificationReadiness.record(f.ledger, f.root, f.ws,
                f.request("r2", VerificationReadiness.Action.RECORD, null)));
    }

    @Test void changedDependencyRejectsOldProofButDoesNotPreventWithdrawal() throws Exception {
        var f = new Fixture(temporary);
        var ref = VerificationReadiness.record(f.ledger, f.root, f.ws, f.request("original", VerificationReadiness.Action.RECORD, null));
        Files.writeString(f.root.resolve("test-tool.lock"), "different tool environment");
        assertThrows(IOException.class, () -> f.validate(ref));
        var withdrawal = f.request("withdraw", VerificationReadiness.Action.WITHDRAW, ref);
        withdrawal = new VerificationReadiness.Request(withdrawal.schema(), withdrawal.requestId(), withdrawal.subject(),
                withdrawal.goal(), withdrawal.epoch(), withdrawal.criterionIds(), withdrawal.method(), withdrawal.executor(),
                withdrawal.evidenceKind(), withdrawal.environment(), withdrawal.statement(), withdrawal.detailOwner(),
                VerificationReadiness.Lifetime.EXPIRES_AT, Instant.parse("2020-01-01T00:00:00Z"), withdrawal.action(), ref);
        VerificationReadiness.record(f.ledger, f.root, f.ws, withdrawal);
        assertThrows(IOException.class, () -> f.validate(ref));
        assertTrue(Files.exists(f.root.resolve(ref.ref())), "historical declaration is retained");
        var newRef = VerificationReadiness.record(f.ledger, f.root, f.ws, f.request("restored", VerificationReadiness.Action.RECORD, null));
        f.validate(newRef);
        assertThrows(IOException.class, () -> f.validate(ref), "new proof must not revive the old reference");
    }

    @Test void replacementIsExplicitAndCannotBorrowAnotherGoalOrSubject() throws Exception {
        var f = new Fixture(temporary);
        var first = VerificationReadiness.record(f.ledger, f.root, f.ws, f.request("original", VerificationReadiness.Action.RECORD, null));
        var replacement = VerificationReadiness.record(f.ledger, f.root, f.ws, f.request("replacement", VerificationReadiness.Action.RECORD, first));
        f.validate(replacement);
        assertThrows(IOException.class, () -> f.validate(first));
        var valid = f.request("bad", VerificationReadiness.Action.RECORD, replacement);
        var wrongGoal = new VerificationReadiness.Request(valid.schema(), valid.requestId(), valid.subject(),
                new TaskContract.Reference(valid.goal().ref(), "a".repeat(64)), valid.epoch(), valid.criterionIds(), valid.method(),
                valid.executor(), valid.evidenceKind(), valid.environment(), valid.statement(), valid.detailOwner(), valid.lifetime(), null,
                valid.action(), valid.supersedes());
        var events = Files.readAllBytes(f.paths.eventsJsonl());
        assertThrows(IOException.class, () -> VerificationReadiness.record(f.ledger, f.root, f.ws, wrongGoal));
        var wrongSubject = new VerificationReadiness.Request(valid.schema(), valid.requestId(), "old-occurrence", valid.goal(),
                valid.epoch(), valid.criterionIds(), valid.method(), valid.executor(), valid.evidenceKind(), valid.environment(),
                valid.statement(), valid.detailOwner(), valid.lifetime(), null, valid.action(), valid.supersedes());
        assertThrows(IOException.class, () -> VerificationReadiness.record(f.ledger, f.root, f.ws, wrongSubject));
        assertArrayEquals(events, Files.readAllBytes(f.paths.eventsJsonl()));
    }

    @Test void unregisteredBytesMissingCriteriaAndChangedMethodDoNotQualify() throws Exception {
        var f = new Fixture(temporary);
        var ref = VerificationReadiness.record(f.ledger, f.root, f.ws, f.request("registered", VerificationReadiness.Action.RECORD, null));
        var copied = f.root.resolve(ref.ref()).resolveSibling("copy.json");
        var orphan = (com.fasterxml.jackson.databind.node.ObjectNode) JSON.readTree(f.root.resolve(ref.ref()).toFile());
        orphan.set("request", JSON.valueToTree(f.request("copy", VerificationReadiness.Action.RECORD, ref)));
        JSON.writeValue(copied.toFile(), orphan);
        assertThrows(IOException.class, () -> f.validate(new TaskContract.Reference(f.root.relativize(copied).toString(), TaskContractArtifactWriter.sha256(copied))));
        assertThrows(IOException.class, () -> VerificationReadiness.record(f.ledger, f.root, f.ws,
                f.request("copy", VerificationReadiness.Action.RECORD, ref)), "an orphan artifact cannot be returned as a committed duplicate");
        var goal = f.goal();
        var bindings = f.bindings(ref);
        try (var read = f.ledger.readSession(f.paths)) {
            assertThrows(IOException.class, () -> VerificationReadiness.validateBindings(read.ledger(), f.root, f.ws, goal, bindings.subList(0, 1)));
            var original = bindings.getFirst();
            var changed = new TaskContract.VerificationBinding(original.criterionId(), "different verification method", original.executor(),
                    original.environmentRefs(), original.authorityRefs(), original.evidenceKind(), original.feasibilityRefs(), original.detailOwner(), original.actualExitRequired(), original.expectedExitCode());
            assertThrows(IOException.class, () -> VerificationReadiness.validateBindings(read.ledger(), f.root, f.ws, goal, List.of(changed, bindings.getLast())));
        }
        var content = JSON.readTree(f.root.resolve(ref.ref()).toFile());
        ((com.fasterxml.jackson.databind.node.ObjectNode) content).put("source", "PROGRAM_OBSERVED");
        JSON.writeValue(f.root.resolve(ref.ref()).toFile(), content);
        assertThrows(IOException.class, () -> f.validate(ref));
    }

    @Test void uploadedPayloadCannotDeclareItsOwnProgramProducer() throws Exception {
        var f = new Fixture(temporary);
        var tree = (com.fasterxml.jackson.databind.node.ObjectNode) JSON.valueToTree(f.request("upload", VerificationReadiness.Action.RECORD, null));
        tree.put("source", "PROGRAM_OBSERVED");
        var payload = f.root.resolve("declaration.json"); JSON.writeValue(payload.toFile(), tree);
        assertThrows(IOException.class, () -> VerificationReadiness.readRequest(payload));
    }

    @Test void expiredOrChangedDuplicateRequestsDoNotReplaceAnExistingDeclaration() throws Exception {
        var f = new Fixture(temporary);
        var request = f.request("stable", VerificationReadiness.Action.RECORD, null);
        var ref = VerificationReadiness.record(f.ledger, f.root, f.ws, request);
        var expired = new VerificationReadiness.Request(request.schema(), "expired", request.subject(), request.goal(), request.epoch(),
                request.criterionIds(), request.method(), request.executor(), request.evidenceKind(), request.environment(),
                request.statement(), request.detailOwner(), VerificationReadiness.Lifetime.EXPIRES_AT,
                Instant.parse("2020-01-01T00:00:00Z"), request.action(), ref);
        var changed = new VerificationReadiness.Request(request.schema(), request.requestId(), request.subject(), request.goal(), request.epoch(),
                request.criterionIds(), "different method", request.executor(), request.evidenceKind(), request.environment(),
                request.statement(), request.detailOwner(), request.lifetime(), null, request.action(), null);
        var events = Files.readAllBytes(f.paths.eventsJsonl());
        assertThrows(IOException.class, () -> VerificationReadiness.record(f.ledger, f.root, f.ws, expired));
        assertThrows(IOException.class, () -> VerificationReadiness.record(f.ledger, f.root, f.ws, changed));
        assertArrayEquals(events, Files.readAllBytes(f.paths.eventsJsonl()));
        f.validate(ref);
    }

    static final class Fixture {
        final Path root;
        final String ws;
        final WorkflowFileLedger ledger;
        final WorkstreamPaths paths;
        final String subject;
        Fixture(Path directory) throws Exception { this(directory, List.of(), List.of(), "gate.plan"); }
        Fixture(Path directory, List<NodeDefinition> tailNodes, List<EdgeDefinition> tailEdges, String afterGoal) throws Exception {
            this(directory, tailNodes, tailEdges, afterGoal, "ws-readiness",
                    new WorkflowFileLedger(new BuiltInFlowOrchestrationKernel(), Map.of("test_binary", "readiness-v2")));
        }
        Fixture(Path directory, List<NodeDefinition> tailNodes, List<EdgeDefinition> tailEdges, String afterGoal,
                String workstream, WorkflowFileLedger suppliedLedger) throws Exception {
            this(directory, tailNodes, tailEdges, afterGoal, workstream, suppliedLedger, null);
        }
        Fixture(Path directory, List<NodeDefinition> tailNodes, List<EdgeDefinition> tailEdges, String afterGoal,
                String workstream, WorkflowFileLedger suppliedLedger, TaskContract goalTemplate) throws Exception {
            this.ws = workstream; this.ledger = suppliedLedger;
            Files.createDirectories(directory);
            root = directory.toRealPath(); paths = new WorkstreamPaths(root, new DualId(ws));
            var nodes = new ArrayList<>(List.of(
                    new NodeDefinition("intake.codex_product_review", NodeKind.TASK, "acpx.agent_run", Map.of()),
                    new NodeDefinition("gate.human_requirement_review", NodeKind.GATE, "human.review", Map.of()),
                    new NodeDefinition("contract.compile", NodeKind.TASK, "contract.compile", Map.of("contract_phase", "GOAL")),
                    new NodeDefinition("gate.plan", NodeKind.GATE, "human.review", Map.of("gate_purpose", "plan", "required_contract_phase", "GOAL", "allowed_actions", List.of("approve", "revise"), "max_revisions", 3))));
            nodes.addAll(tailNodes);
            var edges = new ArrayList<>(List.of(
                    new EdgeDefinition("intake.codex_product_review", "READY", "gate.human_requirement_review"),
                    new EdgeDefinition("gate.human_requirement_review", "approved", "contract.compile"),
                    new EdgeDefinition("contract.compile", "completed", afterGoal)));
            edges.addAll(tailEdges);
            var definition = new WorkflowDefinition("readiness", 1, "intake.codex_product_review", nodes, edges);
            var workspace = new DualId("workspace-readiness");
            ledger.initialize(paths, Workstream.created(paths.workstreamId(), "Verify output", new Workstream.RepositoryRef(root, root), workspace, Instant.now()),
                    Workspace.primaryGitWorktree(workspace, paths.workstreamId(), root, root, "test", "main", "test-base"),
                    Files.exists(paths.requirementMarkdown()) ? Files.readString(paths.requirementMarkdown()) : "Verify output",
                    WorkstreamSettingsDocument.of("auto"), "wf-readiness", definition);
            var producer = ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst();
            var dir = paths.runsDir().resolve("review/outputs"); Files.createDirectories(dir);
            var proposal = paths.workstreamDir().resolve("artifacts/proposal.json"); Files.writeString(proposal, "{\"fixture\":\"approved goal proposal\"}");
            var proposalRef = new TaskContract.Reference(root.relativize(proposal).toString(), TaskContractArtifactWriter.sha256(proposal));
            var sidecar = dir.resolve("review.contract.json");
            var outputRef = root.relativize(dir.resolve("review.json")).toString();
            JSON.writeValue(sidecar.toFile(), Map.of("workstream_id", ws, "run_id", "review", "node_id", "intake.codex_product_review",
                    "node_instance_id", producer, "decision", "READY_FOR_PRODUCT_REVIEW", "summary", "Ready for user decision",
                    "source_artifact_type", "agent_output_contract", "source_output_ref", outputRef,
                    "review_subject", new TaskContractProposalReference(1, "proposal", ws, "intake.claude_brainstorm", "proposal-node", proposalRef.ref(), proposalRef.sha256())));
            AgentOutputLineageFixture.publish(root, ws, sidecar, "iter-001");
            var produced = ledger.loadNode(paths, producer);
            ledger.completeAndRoute(paths, definition, producer, NodeStatus.COMPLETED, "READY", produced.outputRefs(), produced.result());
            var gateNode = ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst();
            var gate = ledger.loadOpenGateForNode(paths, gateNode);
            ledger.resolveOpenGate(paths, definition, "approved", "Accept objective, criteria and exclusions");
            var approval = paths.gateJson(gate.gateId());
            var compile = ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst();
            var contract = new TaskContract(2, TaskContract.Phase.GOAL, 1, "goal", 1, Instant.now(), "Verify output",
                    new TaskContract.Scope(List.of(), List.of("secrets.txt")),
                    List.of(new TaskContract.Clause("AC-1", "Output matches input"), new TaskContract.Clause("AC-2", "Invalid inputs are rejected")),
                    List.of(new TaskContract.Clause("T-1", "Run automated tests")), List.of("Preserve exclusions"), List.of(), List.of(), List.of(),
                    List.of(), List.of(), ContractStatus.READY_FOR_REVIEW, 1,
                    new TaskContract.Source(root.relativize(paths.requirementMarkdown()).toString(), "test compiler fixture", proposalRef,
                            new TaskContract.Reference(root.relativize(approval).toString(), TaskContractArtifactWriter.sha256(approval))),
                    null, List.of(), List.of(), List.of());
            if (goalTemplate != null) contract = new TaskContract(2, TaskContract.Phase.GOAL, 1, "goal", 1, contract.createdAt(),
                    goalTemplate.objective(), new TaskContract.Scope(List.of(), goalTemplate.scope().excluded()),
                    goalTemplate.acceptanceCriteria(), goalTemplate.testRequirements(), goalTemplate.constraints(), goalTemplate.nonGoals(),
                    goalTemplate.riskAreas(), goalTemplate.evidenceRefs(), goalTemplate.ambiguityNotes(), goalTemplate.humanQuestions(),
                    ContractStatus.READY_FOR_REVIEW, 1, contract.source(), null, List.of(), List.of(), List.of());
            assertEquals(List.of(), TaskContractValidator.validate(contract));
            var prefix = ".agent/workstreams/" + ws + "/artifacts/contracts/" + compile + "/";
            var bytes = TaskContractArtifactWriter.mapper().writeValueAsBytes(contract);
            var resolution = "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            try (var write = ledger.writeSession(paths, "publish GOAL compiler fixture", "goal")) {
                write.files().writeBytes(root.resolve(prefix + "task-contract.json"), bytes);
                write.files().writeBytes(root.resolve(prefix + "contract-resolution.json"), resolution);
                write.ledger().completeAndRoute(paths, definition, compile, NodeStatus.COMPLETED, "completed",
                        List.of(prefix + "task-contract.json", prefix + "contract-resolution.json"), Map.of(
                                "contract_phase", "GOAL", "contract_epoch", 1, "contract_id", "goal", "contract_version", 1,
                                "contract_status", "READY_FOR_REVIEW", "task_contract_sha256", TaskContractArtifactWriter.sha256(bytes),
                                "contract_resolution_sha256", TaskContractArtifactWriter.sha256(resolution)));
                write.commit();
            }
            subject = ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst();
            assertNotNull(goal());
            Files.writeString(root.resolve("test-tool.lock"), "known test tool");
        }
        TaskContractProjectionVerifier.QualifiedTaskContract goal() {
            var resolution = TaskContractProjectionVerifier.resolve(ledger, root, ws, TaskContract.Phase.GOAL);
            assertNull(resolution.failure(), String.valueOf(resolution.failure()));
            return resolution.qualified().orElseThrow();
        }
        VerificationReadiness.Request request(String id, VerificationReadiness.Action action, TaskContract.Reference supersedes) throws Exception {
            var goal = goal();
            var dependency = root.resolve("test-tool.lock");
            return new VerificationReadiness.Request(1, id, subject, new TaskContract.Reference(goal.contractRef(), goal.sha256()), 1,
                    goal.contract().acceptanceCriteria().stream().map(TaskContract.Clause::id).toList(), "run project tests", TaskContract.Executor.AGENT,
                    TaskContract.EvidenceKind.PROGRAM_TOOL_EXECUTION_AND_ASSERTIONS,
                    new VerificationReadiness.Environment(root.toString(), "local-test-environment", "operator",
                            List.of(new VerificationReadiness.Dependency(dependency.toString(), TaskContractArtifactWriter.sha256(dependency), false))),
                    "The test dependencies are prepared", "verification-owner", VerificationReadiness.Lifetime.AUTHORIZATION_EPOCH,
                    null, action, supersedes);
        }
        List<TaskContract.VerificationBinding> bindings(TaskContract.Reference ref) {
            return goal().contract().acceptanceCriteria().stream().map(TaskContract.Clause::id).map(id -> new TaskContract.VerificationBinding(id, "run project tests", TaskContract.Executor.AGENT,
                    List.of(ref), List.of(), TaskContract.EvidenceKind.PROGRAM_TOOL_EXECUTION_AND_ASSERTIONS, List.of(ref), "verification-owner", false, null)).toList();
        }
        void validate(TaskContract.Reference ref) throws Exception {
            var goal = goal();
            var bindings = bindings(ref);
            try (var read = ledger.readSession(paths)) { VerificationReadiness.validateBindings(read.ledger(), root, ws, goal, bindings); }
        }
    }
}
