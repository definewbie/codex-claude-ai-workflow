package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.dual.core.id.DualId;
import dev.dual.core.workflow.*;
import dev.dual.ledger.file.*;
import dev.dual.runtime.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class AuthoredStageSequenceTest {
    private static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule());
    @TempDir Path root;

    @Test
    void reviewerDrivenPlanRevisionsKeepEveryReviewObligationUntilApproval() throws Exception {
        var fixture = new Fixture(root, "converter/render.py", PlanTopology.AUTOMATIC);
        fixture.advanceToGate(); fixture.approve();
        assertEquals("gate.human_plan_approval", fixture.currentNode(), fixture.currentDocument());
        var inputs = fixture.inputs.get("plan.claude_plan");
        assertEquals(List.of("plan issue 1", "plan issue 2"), inputs.obligations().stream()
                .map(AuthoredExecutionContext.Obligation::text).toList());
        assertNotNull(inputs.parent());
        fixture.approve();
        assertEquals(WorkflowStatus.COMPLETED, fixture.ledger.loadCurrent(fixture.paths).status());
    }

    @Test
    void reviewedHumanPlanShowsNewIssuesThenCarriesThemWithTheHumanNote() throws Exception {
        var fixture = new Fixture(root, "site/home.html", PlanTopology.REVIEWED_HUMAN);
        fixture.advanceToGate(); fixture.approve();
        assertEquals("gate.human_plan_approval", fixture.currentNode(), fixture.currentDocument());
        var view = new AuthoredStageLineageResolver(dev.dual.runtime.RuntimeTestLedger.ledger(), root, fixture.ws, Long.MAX_VALUE)
                .gateView(fixture.ledger.loadCurrent(fixture.paths).activeNodeInstanceIds().getFirst());
        assertEquals(List.of("plan issue 1"), view.projection().get("newMustFix"));
        assertEquals(List.of(), view.projection().get("activeObligations"));
        fixture.revise("Keep links relative");
        assertEquals("gate.human_plan_approval", fixture.currentNode(), fixture.currentDocument());
        assertEquals(List.of("plan issue 1", "Keep links relative"), fixture.inputs.get("plan.claude_plan")
                .obligations().stream().map(AuthoredExecutionContext.Obligation::text).toList());
        fixture.approve();
        assertEquals(WorkflowStatus.COMPLETED, fixture.ledger.loadCurrent(fixture.paths).status());
    }

    @Test
    void twoBusinessDomainsConsumeApprovedDesignAndPlanThroughTheSameRuntime() throws Exception {
        for (var business : List.of("converter/render.py", "site/home.html")) {
            var fixture = new Fixture(root.resolve(business.startsWith("converter") ? "converter" : "site"), business);
            fixture.advanceToGate();
            assertEquals("gate.human_design_approval", fixture.currentNode());
            fixture.approve();
            assertEquals("gate.human_plan_approval", fixture.currentNode());
            fixture.approve();
            assertEquals(WorkflowStatus.COMPLETED, fixture.ledger.loadCurrent(fixture.paths).status(),
                    fixture.ledger.loadCurrent(fixture.paths).activeNodeInstanceIds().isEmpty() ? fixture.calls.toString()
                            : fixture.ledger.loadNode(fixture.paths, fixture.ledger.loadCurrent(fixture.paths).activeNodeInstanceIds().getFirst()).toString());
            assertEquals(6, fixture.calls.size(), "both authored stages require an independent reviewer");
            var plan = fixture.inputs.get("plan.claude_plan");
            assertNotNull(plan.reviewedDesign());
            var implement = fixture.inputs.get("implement.claude_implement");
            assertNotNull(implement.reviewedPlan());
            assertEquals(plan.reviewedDesign(), implement.reviewedDesign());
            assertEquals(implement.reviewedPlan(), fixture.inputs.get("implement.claude_self_test_report").reviewedPlan());
            assertTrue(implement.render(fixture.root).contains(business));
        }
    }

    @Test
    void twoHumanRevisionsKeepBothNotesAndInitialPlanIsNotAPlanRevision() throws Exception {
        for (var notes : List.of(List.of("Preserve formatting", "Keep relative links"),
                List.of("Keep relative links", "Preserve formatting"))) {
            var fixture = new Fixture(root.resolve(notes.getFirst()), "converter/render.py");
            fixture.advanceToGate();
            fixture.revise(notes.getFirst());
            assertEquals(1, fixture.inputs.get("design.claude_design").obligations().size());
            fixture.revise(notes.getLast());
            var latest = fixture.inputs.get("design.claude_design");
            assertEquals(notes, latest.obligations().stream().map(AuthoredExecutionContext.Obligation::text).toList());
            fixture.approve();
            assertNull(fixture.inputs.get("plan.claude_plan").parent());
            assertTrue(fixture.inputs.get("plan.claude_plan").obligations().isEmpty());
        }
    }

    @ParameterizedTest
    @CsvSource({"converter/render.py, tools/format document.py", "site/home.html, public/pages/start.html"})
    void consistentPathRenamePreservesTheCompleteAuthoredSequence(String original, String renamed) throws Exception {
        List<String> originalCalls = null;
        String originalContractSha = null;
        for (var business : List.of(original, renamed)) {
            var fixture = new Fixture(root.resolve(business.equals(original) ? "original" : "renamed"), business);
            fixture.advanceToGate();
            fixture.approve();
            fixture.approve();
            assertEquals(WorkflowStatus.COMPLETED, fixture.ledger.loadCurrent(fixture.paths).status());
            var input = fixture.inputs.get("implement.claude_implement");
            var contract = JSON.readTree(fixture.root.resolve(input.contract().contract().ref()).toFile());
            var design = JSON.readTree(fixture.root.resolve(input.reviewedDesign().candidate().sidecar().ref()).toFile());
            var plan = JSON.readTree(fixture.root.resolve(input.reviewedPlan().candidate().sidecar().ref()).toFile());
            assertEquals(business, contract.at("/scope/included/0/path").asText());
            assertEquals(business, design.at("/design/planned_files/0/path").asText());
            assertEquals(business, plan.at("/plan_artifact/edit_files/0").asText());
            assertEquals(input.reviewedPlan(), fixture.inputs.get("implement.claude_self_test_report").reviewedPlan());
            assertTrue(input.render(fixture.root).contains(business));
            if (business.equals(original)) {
                originalCalls = List.copyOf(fixture.calls);
                originalContractSha = input.contract().contract().sha256();
            } else {
                assertEquals(originalCalls, fixture.calls, "renaming must not change routing or provider call sequence");
                assertNotEquals(originalContractSha, input.contract().contract().sha256());
                assertFalse(input.render(fixture.root).contains(original), "no stale business path may survive in approved input");
            }
        }
    }

    @Test
    void approvedRevisionDoesNotReplaySupersededInputManifests() throws Exception {
        var fixture = new Fixture(root, "site/home.html");
        fixture.advanceToGate();
        var original = fixture.inputs.get("design.claude_design");
        fixture.revise("Keep relative links");
        var approved = fixture.inputs.get("design.claude_design");
        assertNotEquals(original.runId(), approved.runId());
        Files.delete(fixture.runDir(original.runId()).resolve("prompts/prompt.md.manifest.json"));
        Files.delete(fixture.runDir(original.runId()).resolve("prompts/prompt.md"));

        fixture.approve();
        assertEquals("gate.human_plan_approval", fixture.currentNode(), fixture.currentDocument());
        assertEquals(approved.runId(), fixture.inputs.get("plan.claude_plan").reviewedDesign().candidate().runId());
        fixture.approve();
        assertEquals(WorkflowStatus.COMPLETED, fixture.ledger.loadCurrent(fixture.paths).status());
    }

    @Test
    void publishedInputBindingDoesNotDependOnHistoricalPromptOrManifestLocator() throws Exception {
        var fixture = new Fixture(root, "converter/render.py");
        fixture.advanceToGate();
        var design = fixture.inputs.get("design.claude_design");
        var runDir = fixture.runDir(design.runId());
        Files.delete(runDir.resolve("prompts/prompt.md"));
        var run = (ObjectNode) JSON.readTree(runDir.resolve("run.json").toFile());
        run.remove("promptManifestPath");
        JSON.writeValue(runDir.resolve("run.json").toFile(), run);

        fixture.approve();
        assertEquals("gate.human_plan_approval", fixture.currentNode(), fixture.currentDocument());
        fixture.approve();
        assertEquals(WorkflowStatus.COMPLETED, fixture.ledger.loadCurrent(fixture.paths).status());
    }

    @Test
    void deletingApprovedInputManifestBlocksPlanBeforeItsProvider() throws Exception {
        var fixture = new Fixture(root, "site/home.html");
        fixture.advanceToGate();
        var design = fixture.inputs.get("design.claude_design");
        var manifest = fixture.runDir(design.runId()).resolve("prompts/prompt.md.manifest.json");
        var stable = fixture.paths.workstreamDir().resolve("artifacts/latest-prompt.md.manifest.json");
        Files.createDirectories(stable.getParent());
        Files.copy(manifest, stable);
        Files.delete(manifest);
        fixture.approve();
        assertFalse(fixture.calls.contains("plan.claude_plan"));
        assertEquals(WorkflowStatus.WAITING_FOR_RECOVERY, fixture.ledger.loadCurrent(fixture.paths).status());
    }

    @Test
    void planReviseRequiresANoteBeforeAnyLedgerWrite() throws Exception {
        var fixture = new Fixture(root, "site/home.html");
        fixture.advanceToGate(); fixture.approve();
        var current = Files.readString(fixture.paths.workflowCurrentJson());
        var events = Files.readString(fixture.paths.eventsJsonl());
        assertThrows(java.io.IOException.class, () -> fixture.ledger.resolveOpenGate(
                fixture.paths, fixture.ledger.loadDefinition(fixture.paths), "revise", " "));
        assertEquals(current, Files.readString(fixture.paths.workflowCurrentJson()));
        assertEquals(events, Files.readString(fixture.paths.eventsJsonl()));
        fixture.revise("Keep the implementation steps explicit");
        assertEquals(List.of("Keep the implementation steps explicit"), fixture.inputs.get("plan.claude_plan")
                .obligations().stream().map(AuthoredExecutionContext.Obligation::text).toList());
    }

    public enum PlanTopology { DIRECT_HUMAN, REVIEWED_HUMAN, AUTOMATIC }

    @Test
    void unknownContextPublisherCannotRewriteHumanReviewPacket() throws Exception {
        var fixture = new Fixture(root, "site/home.html");
        fixture.advanceToGate();
        var gateId = fixture.ledger.loadCurrent(fixture.paths).activeNodeInstanceIds().getFirst();
        var gate = fixture.ledger.loadCoreNode(fixture.paths, gateId);
        var packet = fixture.paths.workstreamDir().resolve("reviews/" + gateId + ".json");
        var previousPacket = Files.readString(packet);
        try (var nodes = Files.list(fixture.paths.nodesDir())) {
            var publisher = nodes.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .filter(path -> {
                        try { return JSON.readTree(path.toFile()).path("nodeDefinitionId").asText().equals("ctx.design"); }
                        catch (Exception failure) { throw new AssertionError(failure); }
                    }).findFirst().orElseThrow();
            var document = (ObjectNode) JSON.readTree(publisher.toFile());
            document.put("nodeDefinitionId", "missing.context");
            JSON.writeValue(publisher.toFile(), document);
        }
        var failure = assertThrows(IllegalStateException.class, () -> new HumanReviewGateHandler().openOrInspect(
                new GateHandler.GateContext(root,
                fixture.ws,
                fixture.ledger.loadDefinition(fixture.paths)
                        .node(gate.nodeDefinitionId()),
                gate,
                "READY_FOR_ARCH_REVIEW",
                dev.dual.runtime.RuntimeTestLedger.ledger())));
        assertTrue(failure.getMessage().startsWith("AUTHORED_GATE_LINEAGE_INVALID:"), failure.toString());
        assertInstanceOf(java.io.IOException.class, failure.getCause());
        assertTrue(failure.getCause().getMessage().contains("AUTHORED_ROUTE_TARGET_MISMATCH"));
        assertEquals(previousPacket, Files.readString(packet));
    }

    @Test
    void unknownRoutedNodeRejectsImplementationContextBeforePublication() throws Exception {
        var fixture = new Fixture(root, "site/home.html");
        var implement = fixture.before("implement.claude_implement");
        var lines = new ArrayList<String>();
        for (var line : Files.readAllLines(fixture.paths.eventsJsonl())) {
            var event = (ObjectNode) JSON.readTree(line);
            if (event.path("event_type").asText().equals("node_routed")
                    && event.path("payload").path("to").asText().equals("ctx.design"))
                ((ObjectNode) event.get("payload")).put("to", "missing.context");
            lines.add(JSON.writeValueAsString(event));
        }
        Files.write(fixture.paths.eventsJsonl(), lines);
        var definition = new NodeDefinition("ctx.implement", NodeKind.TASK, "context.build_package",
                Map.of("target_node", "implement.claude_implement", "required_contract_phase", "EXECUTION"));
        var result = new ContextBuildPackageHandler().execute(new TaskHandler.TaskContext(root, fixture.ws, definition, implement.instance(), dev.dual.runtime.RuntimeTestLedger.ledger()));
        assertEquals(NodeStatus.FAILED, result.status());
        assertEquals("TASK_CONTRACT_PROJECTION_QUALIFICATION_FAILED", result.result().get("failure_code"), result.toString());
        assertTrue(result.result().toString().contains("unknown node: missing.context"));
        assertTrue(result.outputRefs().isEmpty());
        assertFalse(Files.exists(fixture.paths.workstreamDir().resolve("context/"
                + implement.instance().nodeInstanceId() + ".md")));
        assertFalse(fixture.calls.contains("implement.claude_implement"));
    }

    public static final class Fixture {
        final Path root;
        final String business;
        final String ws;
        final WorkstreamPaths paths;
        final WorkflowFileLedger ledger;
        final WorkflowRuntime runtime;
        final List<String> calls = new ArrayList<>();
        final Map<String, AuthoredExecutionContext.Snapshot> inputs = new HashMap<>();
        dev.dual.core.contract.TaskContract.Reference readiness;
        VerificationReadinessTest.Fixture goalFixture;
        int run;
        int planReviews;
        java.util.function.Consumer<ObjectNode> outputMutation = ignored -> { };
        final PlanTopology topology;
        final boolean operatorVerification;

        Fixture(Path root, String business) throws Exception {
            this(root, business, PlanTopology.DIRECT_HUMAN);
        }

        Fixture(Path root, String business, PlanTopology topology) throws Exception {
            this(root, business, topology, "ws-authored");
        }

        Fixture(Path root, String business, PlanTopology topology, String ws) throws Exception {
            this(root, business, topology, ws, null);
        }

        Fixture(Path root, String business, PlanTopology topology, String ws, WorkflowDefinition declared) throws Exception {
            this(root, business, topology, ws, declared, dev.dual.runtime.RuntimeTestLedger.ledger());
        }

        public Fixture(Path root, String business, PlanTopology topology, String ws, WorkflowDefinition declared,
                       WorkflowFileLedger ledger) throws Exception {
            this(root, business, topology, ws, declared, ledger, null);
        }

        Fixture(Path root, String business, PlanTopology topology, String ws, WorkflowDefinition declared,
                       WorkflowFileLedger ledger, dev.dual.core.contract.TaskContract goalTemplate) throws Exception {
            this(root, business, topology, ws, declared, ledger, goalTemplate, false);
        }

        Fixture(Path root, String business, PlanTopology topology, String ws, WorkflowDefinition declared,
                       WorkflowFileLedger ledger, dev.dual.core.contract.TaskContract goalTemplate, boolean operatorVerification) throws Exception {
            this.operatorVerification = operatorVerification;
            this.ledger = ledger;
            this.ws = ws;
            this.topology = topology;
            Files.createDirectories(root); root = root.toRealPath(); this.root = root; this.business = business;
            if (!Files.exists(root.resolve(".git"))) {
                GitTrackedPathInventory.command(root, "init");
                Files.writeString(root.resolve(".gitignore"), ".agent/\n");
                GitTrackedPathInventory.command(root, "add", ".gitignore");
                GitTrackedPathInventory.command(root, "-c", "user.name=Fixture", "-c", "user.email=fixture@example.invalid", "commit", "-m", "fixture baseline");
            }
            paths = new WorkstreamPaths(root, new DualId(ws));
            var nodes = new ArrayList<>(List.of(
                    agent("design.claude_design", "design_result"),
                    new NodeDefinition("ctx.design", NodeKind.TASK, "context.build_package", Map.of(
                            "target_node", "design.codex_arch_review", "profile", "architecture_review", "required_contract_phase", "GOAL")),
                    agent("design.codex_arch_review", "agent_output_contract"),
                    new NodeDefinition("design.decision", NodeKind.DECISION, "review.decision", Map.of()),
                    gate("gate.human_design_approval", "design"),
                    agent("plan.claude_plan", "plan_result"),
                    gate("gate.human_plan_approval", "plan"),
                    agent("implement.claude_implement", "agent_output_contract"),
                    agent("implement.claude_self_test_report", "self_test_report"),
                    new NodeDefinition("contract.compile_execution", NodeKind.TASK, "contract.compile", Map.of("contract_phase", "EXECUTION", "required_subject", "reviewed_design_and_plan")),
                    gate("gate.contract_review", "contract")));
            var edges = new ArrayList<>(List.of(edge("design.claude_design", "completed", "ctx.design"),
                    edge("ctx.design", "completed", "design.codex_arch_review"),
                    edge("design.codex_arch_review", "completed", "design.decision"),
                    edge("design.decision", "READY_FOR_ARCH_REVIEW", "gate.human_design_approval"),
                    edge("gate.human_design_approval", "approved", "plan.claude_plan"),
                    edge("gate.human_design_approval", "revise", "design.claude_design"),
                    edge("gate.human_plan_approval", "approved", "contract.compile_execution"),
                    edge("gate.human_plan_approval", "revise", "plan.claude_plan"),
                    edge("implement.claude_implement", "completed", "implement.claude_self_test_report"),
                    edge("contract.compile_execution", "completed", "gate.contract_review"),
                    edge("gate.contract_review", "approved", "implement.claude_implement")));
            nodes.add(new NodeDefinition("ctx.plan", NodeKind.TASK, "context.build_package", Map.of(
                    "target_node", "plan.codex_plan_review", "profile", "plan_review", "required_contract_phase", "GOAL")));
            nodes.add(agent("plan.codex_plan_review", "agent_output_contract"));
            nodes.add(new NodeDefinition("plan.decision", NodeKind.DECISION, "review.decision", Map.of("max_revisions", 3)));
            edges.add(edge("plan.claude_plan", "completed", "ctx.plan"));
            edges.add(edge("ctx.plan", "completed", "plan.codex_plan_review"));
            edges.add(edge("plan.codex_plan_review", "completed", "plan.decision"));
            edges.add(edge("plan.decision", "READY_TO_IMPLEMENT", "gate.human_plan_approval"));
            edges.add(edge("plan.decision", "NEEDS_REVISION", topology == PlanTopology.AUTOMATIC
                    ? "plan.claude_plan" : "gate.human_plan_approval"));
            if (declared != null) {
                nodes.clear(); nodes.addAll(declared.nodes().stream().filter(n -> !Set.of("intake.codex_product_review",
                        "gate.human_requirement_review", "contract.compile").contains(n.id())).toList());
                edges.clear(); edges.addAll(declared.edges().stream().filter(e -> !Set.of("intake.codex_product_review",
                        "gate.human_requirement_review", "contract.compile").contains(e.from())).toList());
            }
            edges.add(edge("gate.plan", "approved", "design.claude_design"));
            // The operator has a known test environment before the authored slice starts.
            // This is a real readiness registration after GOAL approval, not a program-evidence claim.
            goalFixture = new VerificationReadinessTest.Fixture(root, nodes, edges, "gate.plan", ws, ledger, goalTemplate);
            var request = goalFixture.request("authored-ready", VerificationReadiness.Action.RECORD, null);
            if (operatorVerification) request = new VerificationReadiness.Request(request.schema(), request.requestId(), request.subject(),
                    request.goal(), request.epoch(), request.criterionIds(), request.method(), dev.dual.core.contract.TaskContract.Executor.OPERATOR,
                    dev.dual.core.contract.TaskContract.EvidenceKind.OPERATOR_DECLARATION_WITH_MATERIAL, request.environment(), request.statement(),
                    request.detailOwner(), request.lifetime(), request.expiresAt(), request.action(), request.supersedes());
            readiness = VerificationReadiness.record(ledger, root, ws, request);
            ledger.resolveOpenGate(paths, ledger.loadDefinition(paths), "approve", "Begin the authored slice with registered test conditions");
            var provider = new TaskHandler() {
                public String type() { return "acpx.agent_run"; }
                public NodeResult execute(TaskContext context) throws Exception { return executeProvider(context); }
            };
            runtime = new WorkflowRuntime(ledger, new HandlerRegistry(
                    List.of(provider, new ContextBuildPackageHandler(), new ContractCompileHandler()),
                    List.of(new ReviewDecisionHandler()), List.of(new HumanReviewGateHandler())));
        }

        public com.fasterxml.jackson.databind.JsonNode verificationBindings() {
            var bindings = goalFixture.bindings(readiness);
            if (operatorVerification) bindings = bindings.stream().map(b -> new dev.dual.core.contract.TaskContract.VerificationBinding(
                    b.criterionId(), b.method(), dev.dual.core.contract.TaskContract.Executor.OPERATOR, b.environmentRefs(), b.authorityRefs(),
                    dev.dual.core.contract.TaskContract.EvidenceKind.OPERATOR_DECLARATION_WITH_MATERIAL, b.feasibilityRefs(), b.detailOwner(), false, null)).toList();
            return JSON.valueToTree(bindings);
        }

        NodeResult executeProvider(TaskHandler.TaskContext context) throws Exception {
            var runId = "setup-run-" + (++run);
            final AuthoredExecutionContext.Snapshot snapshot;
            try { snapshot = AuthoredExecutionContext.prepare(context, runId).orElseThrow(); }
            catch (Exception failure) {
                return AgentExecutionResultFacts.inputPreparationFailed("REQUIRED_AGENT_EVIDENCE_MISSING", "AUTHORED_CONTEXT_INVALID", List.of(failure.toString()));
            }
            inputs.put(context.definition().id(), snapshot);
            var dir = runDir(runId);
            Files.createDirectories(dir.resolve("prompts")); Files.createDirectories(dir.resolve("outputs"));
            var promptPath = dir.resolve("prompts/prompt.md");
            var manifestPath = dir.resolve("prompts/prompt.md.manifest.json");
            var body = snapshot.render(root);
            Files.writeString(promptPath, body);
            JSON.writeValue(manifestPath.toFile(), Map.of("authoredContext", snapshot, "workstreamId", ws,
                    "runId", runId, "nodeId", context.definition().id(), "promptPath", promptPath.toString(),
                    "sections", List.of(Map.of("name", AuthoredExecutionContext.SECTION_NAME,
                            "sourceRef", context.instance().nodeInstanceId(), "required", true,
                            "sha256", TaskContractArtifactWriter.sha256(body.getBytes(java.nio.charset.StandardCharsets.UTF_8))))));
            JSON.writeValue(dir.resolve("run.json").toFile(), Map.of("schemaVersion", 3, "runId", runId, "workstreamId", ws,
                    "nodeId", context.definition().id(), "agent", "fixture", "session", "fixture-session", "status", "prepared", "promptManifestPath", manifestPath.toString()));
            var facts = new LinkedHashMap<String, Object>();
            facts.put("contract_ref", snapshot.contract().contract().ref()); facts.put("contract_sha256", snapshot.contract().contract().sha256());
            facts.put("run", JSON.readTree(dir.resolve("run.json").toFile()));
            var baseline = context.definition().id().startsWith("implement.") ? VerificationEvidence.prepare(context) : null;
            if (baseline != null) facts.put("verification_source", baseline);
            var prepared = AgentRunCommitProtocol.prepare(context, dir, List.of(promptPath, manifestPath), facts);
            AgentRunCommitProtocol.qualifyDispatch(context, prepared);
            AgentRunCommitProtocol.beginExternalCall(context, prepared);
            AuthoredExecutionContext.verifyManifest(root, manifestPath, snapshot);
            calls.add(context.definition().id());
            var contract = ContractRegistry.forNode(context.definition().id());
            var decision = context.definition().id().equals("plan.claude_plan") ? "READY_FOR_PLAN_REVIEW"
                    : context.definition().id().equals("implement.claude_self_test_report") ? "SELF_TEST_COMPLETE"
                    : context.definition().id().equals("implement.claude_implement") ? "IMPLEMENTED" : "READY_FOR_ARCH_REVIEW";
            if (context.definition().id().equals("plan.codex_plan_review")) {
                planReviews++;
                decision = planReviews <= (topology == PlanTopology.AUTOMATIC ? 2 : topology == PlanTopology.REVIEWED_HUMAN ? 1 : 0)
                        ? "NEEDS_REVISION" : "READY_TO_IMPLEMENT";
            }
            var rendered = AgentResultPromptContract.render(ws, runId, context.definition().id(), decision, Set.of(decision));
            var envelope = (ObjectNode) JSON.readTree(rendered.substring(rendered.indexOf('{')));
            var result = (ObjectNode) envelope.get("result");
            if (result.has("must_fix")) result.putArray("must_fix");
            if (context.definition().id().equals("plan.codex_plan_review") && decision.equals("NEEDS_REVISION"))
                result.putArray("must_fix").add("plan issue " + planReviews);
            if (result.has("revision_responses")) {
                var responses = result.putArray("revision_responses");
                snapshot.obligations().forEach(obligation -> responses.addObject().put("obligation_id", obligation.id())
                        .put("disposition", "ADDRESSED").put("evidence", "Kept in this candidate"));
            }
            if (result.has("design")) ((ObjectNode) result.at("/design/planned_files/0")).put("path", business);
            if (result.has("plan")) {
                var plan = (ObjectNode) result.get("plan");
                plan.putArray("edit_files").add(business);
                plan.set("files", JSON.valueToTree(List.of(new dev.dual.core.contract.TaskContract.ScopeEntry(business, dev.dual.core.contract.TaskContract.ScopeOperation.CREATE))));
                plan.set("verification_bindings", verificationBindings());
                plan.putArray("authority_refs");
            }
            var observations = new ArrayList<dev.dual.core.execution.AgentExecutionEvidence.ToolObservation>();
            if (result.has("verification_checks")) {
                var exists = Files.exists(root.resolve(business));
                observations.add(new dev.dual.core.execution.AgentExecutionEvidence.ToolObservation("fixture-session", "inspect-file", "fixture.inspect", "read",
                        JSON.valueToTree(Map.of("path", business)), JSON.valueToTree(Map.of("exists", exists)), "completed", null, null, "", false, List.of()));
                result.set("verification_checks", JSON.valueToTree(goalFixture.bindings(readiness).stream()
                        .map(b -> new AgentResultEnvelope.VerificationCheck(b.criterionId(), "Fixture path inspected", "CURRENT", List.of("inspect-file"), List.of())).toList()));
            }
            outputMutation.accept(result);
            var output = dir.resolve("outputs/result.json"); JSON.writeValue(output.toFile(), envelope);
            var metadata = (ObjectNode) JSON.readTree(dir.resolve("run.json").toFile());
            metadata.put("status", "process_exited");
            metadata.set("processResult", JSON.valueToTree(Map.of("exitCode", 0, "usability", "EXITED_ZERO", "durationMs", 1)));
            metadata.putArray("adapterSignals"); JSON.writeValue(dir.resolve("run.json").toFile(), metadata);
            var candidate = NodeResult.completed("completed", List.of(root.relativize(output).toString()), Map.of("run_id", runId, "exit_code", 0,
                    "run_metadata", root.relativize(dir.resolve("run.json")).toString()));
            return VerificationEvidence.record(context, prepared, baseline, "offline-fixture", observations, candidate);
        }

        void advanceToGate() throws Exception {
            for (int i = 0; i < 40; i++) {
                var result = runtime.resume(new WorkflowCommand(root, ws));
                if (result.status() == WorkflowStatus.WAITING_FOR_HUMAN && currentNode().equals("gate.contract_review")) {
                    ReviewGateActions.resolve(ledger, paths, "approve", "Approve the compiled exact execution scope for this test");
                    continue;
                }
                if (result.status() != WorkflowStatus.RUNNING) return;
            }
            fail("workflow did not pause");
        }
        public TaskHandler.TaskContext before(String target) throws Exception {
            for (int i = 0; i < 40; i++) {
                var current = ledger.loadCurrent(paths);
                assertFalse(current.activeNodeInstanceIds().isEmpty(), "missing target " + target);
                var id = current.activeNodeInstanceIds().getFirst();
                var node = ledger.loadNode(paths, id);
                if (node.nodeDefinitionId().equals(target)) {
                    return new TaskHandler.TaskContext(root, ws, ledger.loadDefinition(paths).node(target), ledger.loadCoreNode(paths, id), ledger);
                }
                if (current.status() == WorkflowStatus.WAITING_FOR_HUMAN) {
                    var revise = "gate.human_plan_approval".equals(node.nodeDefinitionId())
                            && new AuthoredStageLineageResolver(ledger, root, ws, Long.MAX_VALUE).gateView(id)
                                    .decision().decision().equals("NEEDS_REVISION");
                    if (revise) ledger.resolveOpenGate(paths, ledger.loadDefinition(paths), "revise", "Address reviewer issues before approval");
                    else ledger.resolveOpenGate(paths, ledger.loadDefinition(paths), "approve", "Fixture approves this candidate");
                } else {
                    assertEquals(WorkflowStatus.RUNNING, current.status(), node.toString());
                    runtime.resume(new WorkflowCommand(root, ws));
                }
            }
            throw new AssertionError("target not reached: " + target);
        }
        void approve() throws Exception { ledger.resolveOpenGate(paths, ledger.loadDefinition(paths), "approve", "Accept this candidate"); advanceToGate(); }
        void revise(String note) throws Exception { ledger.resolveOpenGate(paths, ledger.loadDefinition(paths), "revise", note); advanceToGate(); }
        String currentNode() throws Exception { return ledger.loadNode(paths, ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst()).nodeDefinitionId(); }
        String currentDocument() throws Exception { return ledger.loadNode(paths, ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst()).toString(); }
        String currentDocumentIfActive() throws Exception { return ledger.loadCurrent(paths).activeNodeInstanceIds().isEmpty() ? "" : currentDocument(); }
        Path runDir(String run) { return paths.workstreamDir().resolve("runs/" + run); }
        static NodeDefinition agent(String id, String type) { return new NodeDefinition(id, NodeKind.TASK, "acpx.agent_run",
                Map.of("expected_artifact", type, "required_contract_phase", id.startsWith("implement") ? "EXECUTION" : "GOAL")); }
        static NodeDefinition gate(String id, String purpose) { return new NodeDefinition(id, NodeKind.GATE, "human.review",
                Map.of("gate_purpose", purpose, "allowed_actions", List.of("approved", "revise"))); }
        static EdgeDefinition edge(String from, String on, String to) { return new EdgeDefinition(from, on, to); }
    }
}
