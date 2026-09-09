package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.dual.core.id.DualId;
import dev.dual.core.workflow.*;
import dev.dual.ledger.file.WorkflowFileLedger;
import dev.dual.ledger.file.WorkstreamPaths;
import dev.dual.runtime.WorkflowCommand;
import dev.dual.runtime.WorkflowRuntime;
import dev.dual.runtime.lifecycle.WorkflowDefinitionLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Real publication, routing, approvals, compilation and authored consumption; only agent output is substituted. */
class AcceptanceRepairSequenceTest {
    private static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule());
    @TempDir Path root;

    @Test
    void scopeRequestPreservesOldAuthorityUntilBothApprovalsThenPassesGitAgain() throws Exception {
        var f = new Fixture(root, "SCOPE_ADDITION", "tool.py");
        f.until("gate.human_implementation_repair");
        var old = TaskContractProjectionVerifier.resolve(root, f.ws).qualified().orElseThrow();
        var gate = f.ledger.loadNode(f.paths, f.ledger.loadCurrent(f.paths).activeNodeInstanceIds().getFirst());
        assertTrue(Files.exists(root.resolve("helper.py")));
        assertFalse(old.contract().scope().included().stream().anyMatch(e -> e.path().equals("helper.py")));
        f.ledger.resolveOpenGate(f.paths, f.ledger.loadDefinition(f.paths), "request_scope_revision",
                "Review retaining helper.py for the existing behavior; do not change other requirements");
        f.until("gate.human_requirement_review");
        assertEquals(old.sha256(), TaskContractProjectionVerifier.resolve(root, f.ws).qualified().orElseThrow().sha256());
        assertEquals(1, Collections.frequency(f.calls, "implement.claude_implement"));
        f.approve(); f.until("gate.contract_review");
        assertTrue(TaskContractProjectionVerifier.resolve(root, f.ws).qualified().isEmpty());
        f.approve();
        var fresh = TaskContractProjectionVerifier.resolve(root, f.ws).qualified().orElseThrow();
        assertTrue(fresh.contract().scope().included().contains(new dev.dual.core.contract.TaskContract.ScopeEntry(
                "helper.py", dev.dual.core.contract.TaskContract.ScopeOperation.CREATE)));
        assertEquals(old.contract().acceptanceCriteria(), fresh.contract().acceptanceCriteria());
        f.until("gate.human_acceptance");
        assertEquals(2, Collections.frequency(f.calls, "implement.claude_implement"));
        assertEquals(2, Collections.frequency(f.calls, "design.claude_design"));
        var gateReloaded = f.ledger.loadNode(f.paths, gate.nodeInstanceId());
        assertEquals("request_scope_revision", gateReloaded.result().get("human_action"));
        var acceptanceGate = f.ledger.loadNode(f.paths, f.ledger.loadCurrent(f.paths).activeNodeInstanceIds().getFirst());
        assertTrue(acceptanceGate.inputRefs().stream().anyMatch(ref -> ref.contains("/delivery/candidates/")));
    }

    @Test
    void humanCanStillRequireRepairBackToTheOriginalScope() throws Exception {
        var f = new Fixture(root, "SCOPE_ADDITION", "tool.py");
        f.until("gate.human_implementation_repair");
        var original = TaskContractProjectionVerifier.resolve(root, f.ws).qualified().orElseThrow();
        f.removeScopeAddition = true;
        f.ledger.resolveOpenGate(f.paths, f.ledger.loadDefinition(f.paths), "revise", "Remove the unapproved helper");
        f.until("gate.human_acceptance");
        assertFalse(Files.exists(root.resolve("helper.py")));
        assertEquals(original.sha256(), TaskContractProjectionVerifier.resolve(root, f.ws).qualified().orElseThrow().sha256());
        assertEquals(1, Collections.frequency(f.calls, "intake.claude_brainstorm"));
        assertEquals(2, Collections.frequency(f.calls, "implement.claude_implement"));
    }

    @Test
    void scopeRequestRejectsTamperedEvidenceBeforeAnyLedgerWrite() throws Exception {
        var f = new Fixture(root, "SCOPE_ADDITION", "tool.py");
        f.until("gate.human_implementation_repair");
        var gate = f.ledger.loadNode(f.paths, f.ledger.loadCurrent(f.paths).activeNodeInstanceIds().getFirst());
        var ref = gate.inputRefs().stream().filter(r -> r.contains("/occurrences/")
                && r.contains("change-scope-evaluation-")).findFirst().orElseThrow();
        Files.writeString(root.resolve(ref), "{}\n");
        var before = snapshot(f.paths.workstreamDir());
        assertThrows(java.io.IOException.class, () -> f.ledger.resolveOpenGate(f.paths, f.ledger.loadDefinition(f.paths),
                "request_scope_revision", "Review the candidate"));
        assertEquals(before, snapshot(f.paths.workstreamDir()));
        assertEquals(WorkflowStatus.WAITING_FOR_HUMAN, new WorkflowFileLedger().loadCurrent(f.paths).status());
    }

    @Test
    void scopePatchCannotSmuggleAThirdPathOrChangeApprovedClauses() throws Exception {
        for (var mutation : List.of("path", "clause")) {
            var f = new Fixture(root.resolve(mutation), "SCOPE_ADDITION", "tool.py");
            f.until("gate.human_implementation_repair");
            var before = TaskContractProjectionVerifier.resolve(f.root, f.ws).qualified().orElseThrow();
            f.ledger.resolveOpenGate(f.paths, f.ledger.loadDefinition(f.paths), "request_scope_revision", "Review the helper");
            f.until("intake.claude_brainstorm");
            if (mutation.equals("path")) f.scopeRequestedPath = "third.py";
            else f.replaceAcceptanceClause = true;
            f.runtime.resume(new WorkflowCommand(f.root, f.ws));
            assertEquals(WorkflowStatus.WAITING_FOR_RECOVERY, f.ledger.loadCurrent(f.paths).status());
            assertEquals(before.sha256(), TaskContractProjectionVerifier.resolve(f.root, f.ws).qualified().orElseThrow().sha256());
            assertEquals(1, Collections.frequency(f.calls, "implement.claude_implement"));
        }
    }

    private static Map<String, String> snapshot(Path dir) throws Exception {
        var snapshot = new TreeMap<String, String>();
        try (var paths = Files.walk(dir)) {
            for (var file : paths.filter(Files::isRegularFile).toList())
                snapshot.put(dir.relativize(file).toString(), Base64.getEncoder().encodeToString(Files.readAllBytes(file)));
        }
        return snapshot;
    }

    @Test
    void contractReworkMustProduceTwoNewApprovalsBeforeFreshAuthoredExecution() throws Exception {
        var f = new Fixture(root, "CONTRACT_GAP", "site/home.html");
        f.until("acceptance.classify_failure");
        var old = TaskContractProjectionVerifier.resolve(root, f.ws).qualified().orElseThrow();
        var oldImplement = f.implementContexts.getFirst();
        f.step();
        assertEquals("context.intake_claude_brainstorm", f.currentNode());
        f.until("gate.human_requirement_review");
        assertEquals(old.compileNodeInstanceId(), TaskContractProjectionVerifier.resolve(root, f.ws)
                .qualified().orElseThrow().compileNodeInstanceId());
        assertEquals(1, Collections.frequency(f.calls, "implement.claude_implement"));
        f.approve();
        f.until("gate.contract_review");
        assertTrue(TaskContractProjectionVerifier.resolve(root, f.ws).qualified().isEmpty(),
                "requirement approval and compile are not contract approval");
        assertEquals(1, Collections.frequency(f.calls, "design.claude_design"));
        f.approve();
        var fresh = TaskContractProjectionVerifier.resolve(root, f.ws).qualified().orElseThrow();
        assertNotEquals(old.compileNodeInstanceId(), fresh.compileNodeInstanceId());
        assertThrows(java.io.IOException.class, () -> AuthoredExecutionContext.prepare(oldImplement, "old-retry"));
        f.until("acceptance.generate_final_report");
        assertEquals(2, Collections.frequency(f.calls, "intake.claude_brainstorm"));
        assertEquals(2, Collections.frequency(f.calls, "design.claude_design"));
        assertEquals(2, Collections.frequency(f.calls, "plan.claude_plan"));
        assertEquals(2, Collections.frequency(f.calls, "implement.claude_implement"));
        assertEquals(2, f.acceptanceCount);
        assertTrue(Files.exists(root.resolve("site/home.html")));
        assertTrue(f.patchObligations.stream().anyMatch(text -> text.contains("define output behavior")));
    }

    @Test
    void evidenceOnlyAndMixedRepairUseDifferentRealProducers() throws Exception {
        for (var category : List.of("VERIFICATION_INSUFFICIENT", "IMPLEMENTATION_BUG")) {
            var f = new Fixture(root.resolve(category), category, "tools/render.py");
            f.until("acceptance.generate_final_report");
            assertEquals(category.equals("IMPLEMENTATION_BUG") ? 2 : 1,
                    Collections.frequency(f.calls, "implement.claude_implement"));
            assertEquals(2, Collections.frequency(f.calls, "implement.claude_self_test_report"));
            assertEquals(1, Collections.frequency(f.calls, "intake.claude_brainstorm"));
            assertEquals(2, f.acceptanceCount);
        }
    }

    @Test
    void changedClauseReachesDownstreamOnlyAfterBothApprovals() throws Exception {
        var f = new Fixture(root, "CONTRACT_GAP", "tool.py");
        f.until("acceptance.classify_failure");
        var old = TaskContractProjectionVerifier.resolve(root, f.ws).qualified().orElseThrow();
        f.replaceAcceptanceClause = true;
        f.step();
        f.until("gate.human_requirement_review");
        assertEquals(old.sha256(), TaskContractProjectionVerifier.resolve(root, f.ws).qualified().orElseThrow().sha256());
        f.approve(); f.until("gate.contract_review");
        assertTrue(TaskContractProjectionVerifier.resolve(root, f.ws).qualified().isEmpty());
        assertEquals(1, Collections.frequency(f.calls, "design.claude_design"));
        f.approve();
        var revised = TaskContractProjectionVerifier.resolve(root, f.ws).qualified().orElseThrow();
        assertNotEquals(old.sha256(), revised.sha256());
        assertTrue(revised.contract().acceptanceCriteria().contains("Revised output must include a stable identifier"));
        f.until("acceptance.generate_final_report");
        for (var node : List.of("design.claude_design", "plan.claude_plan", "implement.claude_implement",
                "implement.claude_self_test_report", "acceptance.codex_verifier_review")) {
            assertTrue(f.consumedContracts.get(node).contains("Revised output must include a stable identifier"), node);
        }
        assertEquals(2, f.acceptanceCount);
    }

    static final class Fixture {
        final Path root;
        final String ws = "ws-repair-sequence";
        final String category;
        final String business;
        final WorkstreamPaths paths;
        final WorkflowFileLedger ledger = new WorkflowFileLedger();
        final WorkflowRuntime runtime;
        final List<String> calls = new ArrayList<>();
        final List<String> patchObligations = new ArrayList<>();
        final List<TaskHandler.TaskContext> implementContexts = new ArrayList<>();
        int run;
        int acceptanceCount;
        boolean replaceAcceptanceClause;
        String scopeRequestedPath = "helper.py";
        boolean removeScopeAddition;
        final Map<String, String> consumedContracts = new java.util.LinkedHashMap<>();

        Fixture(Path root, String category, String business) throws Exception {
            this.root = root.toAbsolutePath(); this.category = category; this.business = business;
            Files.createDirectories(this.root);
            git("init"); git("config", "user.name", "Test"); git("config", "user.email", "test@example.invalid");
            Files.writeString(this.root.resolve("README.md"), "Generic utility\n");
            Files.writeString(this.root.resolve(".gitignore"), ".agent/\n");
            git("add", "."); git("commit", "-m", "baseline");
            paths = new WorkstreamPaths(this.root, new DualId(ws));
            var definition = new WorkflowDefinitionLoader().loadOrDefault(null, "contract");
            if (category.equals("SCOPE_ADDITION") && definition.edgesFrom("gate.human_implementation_repair").stream()
                    .noneMatch(edge -> edge.on().equals("request_scope_revision"))) {
                var nodes = definition.nodes().stream().map(node -> {
                    if (!node.id().equals("gate.human_implementation_repair")) return node;
                    var config = new LinkedHashMap<>(node.config());
                    config.put("allowed_actions", List.of("revise", "request_scope_revision"));
                    return new NodeDefinition(node.id(), node.kind(), node.handlerType(), config);
                }).toList();
                var edges = new ArrayList<>(definition.edges());
                edges.add(new EdgeDefinition("gate.human_implementation_repair", "request_scope_revision",
                        "context.intake_claude_brainstorm", EdgeCondition.unconditional(), TransitionMetadata.loop("scope review")));
                definition = new WorkflowDefinition(definition.workflowId(), definition.version(), definition.entryNodeId(), nodes, edges);
            }
            ledger.initialize(paths, definition.workflowId(), definition);
            Files.writeString(paths.workstreamDir().resolve("requirement.md"), "Build a bounded generic utility.");
            JSON.writeValue(paths.workstreamDir().resolve("workstream.json").toFile(), Map.of(
                    "primaryWorkspaceId", "workspace-1", "workstreamId", ws, "title", "Generic utility"));
            Files.createDirectories(paths.workstreamDir().resolve("workspaces"));
            JSON.writeValue(paths.workstreamDir().resolve("workspaces/workspace-1.json").toFile(), Map.of(
                    "baseCommit", git("rev-parse", "HEAD").trim(), "effectiveWorkdir", this.root.toString(),
                    "workspaceId", "workspace-1", "workstreamId", ws));
            var provider = new TaskHandler() {
                public String type() { return "acpx.agent_run"; }
                public NodeResult execute(TaskContext context) throws Exception { return output(context); }
            };
            runtime = new WorkflowRuntime(ledger, new HandlerRegistry(
                    List.of(provider, new ContextBuildPackageHandler(), new ContractCompileHandler(),
                            new ArtifactValidateHandler(), new FailureClassifyHandler(),
                            new ReportGenerateFinalHandler(), new DeliveryPrepareHandler()),
                    List.of(new ReviewDecisionHandler()), List.of(new HumanReviewGateHandler())));
        }

        NodeResult output(TaskHandler.TaskContext context) throws Exception {
            var id = context.definition().id();
            var runId = "test-run-" + (++run);
            var prepared = AgentRunGovernanceContext.ensureQualified(context, runId);
            prepared.trustedTaskContractPayload().ifPresent(body -> consumedContracts.put(id, body));
            calls.add(id);
            var decision = switch (id) {
                case "intake.claude_brainstorm", "intake.codex_product_review" -> "READY_FOR_PRODUCT_REVIEW";
                case "plan.claude_plan" -> "READY_FOR_PLAN_REVIEW";
                case "plan.codex_plan_review" -> "READY_TO_IMPLEMENT";
                case "implement.claude_implement" -> "IMPLEMENTED";
                case "implement.claude_self_test_report" -> "SELF_TEST_COMPLETE";
                case "acceptance.codex_verifier_review" -> ++acceptanceCount == 1 && !category.equals("SCOPE_ADDITION") ? "REJECT" : "ACCEPT";
                default -> "READY_FOR_ARCH_REVIEW";
            };
            var emission = AgentResultPromptContract.ProposalEmissionContext.initial();
            TaskContractRevisionContextResolver.Context revision = null;
            if (id.equals("intake.claude_brainstorm")) {
                var resolved = new TaskContractRevisionContextResolver().resolveForNode(root, ws, context.instance().nodeInstanceId());
                assertTrue(resolved.valid(), resolved.errors().toString());
                revision = resolved.context();
                if (revision.revision()) emission = AgentResultPromptContract.proposalEmissionContext(revision);
            }
            var rendered = AgentResultPromptContract.render(ws, runId, id, decision, Set.of(decision), emission);
            var envelope = (ObjectNode) JSON.readTree(rendered.substring(rendered.lastIndexOf("\n{") + 1));
            var result = (ObjectNode) envelope.get("result");
            if (result.has("must_fix")) result.putArray("must_fix");
            if (result.has("contract_obligations")) result.putArray("contract_obligations");
            if (id.equals("intake.claude_brainstorm")) {
                if (!revision.revision()) {
                    ((ObjectNode) result.get("scope")).putArray("included").addObject().put("path", business);
                    result.putArray("evidence_refs").add("README.md");
                } else {
                    var patches = result.putArray("obligation_patches");
                    for (var obligation : revision.obligations()) {
                        patchObligations.add(obligation.text());
                        var patch = patches.addObject().put("review_obligation_id", obligation.id());
                        patch.putArray("resolved_by_clause_ids").add("AC-1");
                        patch.putArray("clause_changes"); patch.putArray("scope_additions"); patch.putArray("evidence_ref_additions");
                        if (revision.scopeRevision() && patches.size() == 1) {
                            ((com.fasterxml.jackson.databind.node.ArrayNode) patch.get("scope_additions"))
                                    .addObject().put("path", scopeRequestedPath);
                        }
                        if (replaceAcceptanceClause && patches.size() == 1) {
                            ((com.fasterxml.jackson.databind.node.ArrayNode) patch.get("clause_changes")).addObject()
                                    .put("operation", "REPLACE").put("section", "ACCEPTANCE_CRITERIA")
                                    .put("id", "AC-1").put("text", "Revised output must include a stable identifier");
                        }
                    }
                }
            }
            if (result.has("design")) ((ObjectNode) result.at("/design/planned_files/0")).put("path", business);
            if (result.has("plan")) ((ObjectNode) result.get("plan")).putArray("edit_files").add(business);
            if (id.equals("implement.claude_implement")) {
                implementContexts.add(context);
                Files.createDirectories(root.resolve(business).getParent());
                Files.writeString(root.resolve(business), "output\n");
                if (category.equals("SCOPE_ADDITION")) {
                    if (removeScopeAddition) Files.deleteIfExists(root.resolve("helper.py"));
                    else Files.writeString(root.resolve("helper.py"), "helper\n");
                }
            }
            if (id.equals("acceptance.codex_verifier_review") && decision.equals("REJECT")) {
                result.put("lifecycle_status", "REJECTED").put("blocking", true);
                result.putArray("missing_evidence").add("repeat the existing verification");
                var failure = (ObjectNode) result.get("failure");
                failure.put("category_hint", category);
                if (category.startsWith("CONTRACT_")) failure.putArray("contract_gaps").add("define output behavior");
                if (category.equals("IMPLEMENTATION_BUG")) result.putArray("must_fix").add("repair output under existing contract");
            }
            var dir = paths.workstreamDir().resolve("runs/" + runId);
            Files.createDirectories(dir.resolve("outputs")); Files.createDirectories(dir.resolve("prompts"));
            if (prepared.authoredContext().isPresent()) {
                var snapshot = prepared.authoredContext().orElseThrow();
                var body = snapshot.render(root);
                var prompt = dir.resolve("prompts/prompt.md");
                var manifest = dir.resolve("prompts/prompt.md.manifest.json");
                Files.writeString(prompt, body);
                JSON.writeValue(manifest.toFile(), Map.of("authoredContext", snapshot, "workstreamId", ws,
                        "runId", runId, "nodeId", id, "promptPath", prompt.toString(), "sections", List.of(Map.of(
                                "name", AuthoredExecutionContext.SECTION_NAME, "sourceRef", context.instance().nodeInstanceId(),
                                "required", true, "sha256", TaskContractArtifactWriter.sha256(body.getBytes(StandardCharsets.UTF_8))))));
                JSON.writeValue(dir.resolve("run.json").toFile(), Map.of("promptManifestPath", manifest.toString()));
            }
            var output = dir.resolve("outputs/result.json"); JSON.writeValue(output.toFile(), envelope);
            var binding = prepared.repositoryFactBinding();
            return NodeResult.completed("completed", List.of(root.relativize(output).toString(), binding.ref()), Map.of(
                    "run_id", runId, "exit_code", 0, "repository_fact_binding_ref", binding.ref(),
                    "repository_fact_binding_sha256", binding.sha256()));
        }

        String currentNode() throws Exception {
            var current = ledger.loadCurrent(paths);
            assertFalse(current.activeNodeInstanceIds().isEmpty(), current.toString());
            return ledger.loadNode(paths, current.activeNodeInstanceIds().getFirst()).nodeDefinitionId();
        }
        void step() throws Exception {
            runtime.resume(new WorkflowCommand(root, ws));
            var current = ledger.loadCurrent(paths);
            assertNotEquals(WorkflowStatus.WAITING_FOR_RECOVERY, current.status(), () -> {
                try { return ledger.loadNode(paths, current.activeNodeInstanceIds().getFirst()).toString(); }
                catch (Exception e) { return e.toString(); }
            });
        }
        void approve() throws Exception {
            // Opening the real packet precedes a real ledger approval.
            if (ledger.loadCurrent(paths).status() == WorkflowStatus.RUNNING) step();
            ledger.resolveOpenGate(paths, ledger.loadDefinition(paths), "approve", "Approve this exact candidate");
        }
        void until(String target) throws Exception {
            for (int count = 0; count < 100; count++) {
                if (currentNode().equals(target)) return;
                if (ledger.loadCurrent(paths).status() == WorkflowStatus.WAITING_FOR_HUMAN) approve();
                else step();
            }
            fail("target not reached: " + target);
        }
        String git(String... args) throws Exception {
            var command = new ArrayList<>(List.of("git")); command.addAll(List.of(args));
            var process = new ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true).start();
            var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(0, process.waitFor(), output); return output;
        }
    }
}
