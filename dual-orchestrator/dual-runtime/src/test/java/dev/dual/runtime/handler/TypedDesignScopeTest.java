package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeInstance;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeResult;
import dev.dual.core.workflow.NodeStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TypedDesignScopeTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String WS = "ws-design";
    @TempDir Path root;

    @Test
    void runtimeNeverInvokesReviewerAfterOutOfScopeDesign() throws Exception {
        runtimeChain(true, false);
    }

    @Test
    void runtimeNeverInvokesReviewerWithoutContractApproval() throws Exception {
        runtimeChain(false, false);
    }

    @Test
    void runtimeInvokesReviewerForAnAuthorizedDesign() throws Exception {
        runtimeChain(true, true);
    }

    private void runtimeChain(boolean approved, boolean authorizedPath) throws Exception {
        var paths = new dev.dual.ledger.file.WorkstreamPaths(root, new dev.dual.core.id.DualId(WS));
        var definition = new dev.dual.core.workflow.WorkflowDefinition("typed-design-chain", 1, "design.claude_design",
                List.of(new NodeDefinition("design.claude_design", NodeKind.TASK, "acpx.agent_run", Map.of(
                                "expected_artifact", "design_result", "requires_approved_task_contract", true)),
                        new NodeDefinition("ctx.design", NodeKind.TASK, "context.build_package", Map.of(
                                "profile", "architecture_review", "target_node", "design.codex_arch_review",
                                "requires_approved_task_contract", true)),
                        new NodeDefinition("design.codex_arch_review", NodeKind.TASK, "acpx.agent_run", Map.of())),
                List.of(new dev.dual.core.workflow.EdgeDefinition("design.claude_design", "completed", "ctx.design"),
                        new dev.dual.core.workflow.EdgeDefinition("ctx.design", "completed", "design.codex_arch_review")));
        var ledger = new dev.dual.ledger.file.WorkflowFileLedger();
        ledger.initialize(paths, "workflow-design", definition);
        if (approved) {
            approve("src/approved.ext");
            // The approval fixture appends history; keep the runtime event cursor consistent.
            var lastEvent = JSON.readTree(Files.readAllLines(paths.eventsJsonl()).getLast());
            var current = (ObjectNode) JSON.readTree(paths.workflowCurrentJson().toFile());
            current.put("lastEventId", lastEvent.path("event_id").asText());
            JSON.writeValue(paths.workflowCurrentJson().toFile(), current);
        }
        var calls = new java.util.ArrayList<String>();
        var fakeProvider = new TaskHandler() {
            public String type() { return "acpx.agent_run"; }
            public NodeResult execute(TaskContext context) throws Exception {
                calls.add(context.definition().id());
                if ("design.codex_arch_review".equals(context.definition().id())) {
                    var ref = ".agent/workstreams/" + WS + "/runs/run-review/outputs/review.json";
                    var prompt = AgentResultPromptContract.render(WS, "run-review", context.definition().id(),
                            "READY_FOR_ARCH_REVIEW", Set.of("READY_FOR_ARCH_REVIEW"));
                    Files.createDirectories(root.resolve(ref).getParent());
                    Files.writeString(root.resolve(ref), prompt.substring(prompt.indexOf('{')));
                    return NodeResult.completed("completed", List.of(ref), Map.of("run_id", "run-review", "exit_code", 0));
                }
                var ref = ".agent/workstreams/" + WS + "/runs/run-runtime/outputs/design.json";
                Files.createDirectories(root.resolve(ref).getParent());
                JSON.writeValue(root.resolve(ref).toFile(), candidate("run-runtime",
                        authorizedPath ? "src/approved.ext" : "src/unauthorized.ext", "CREATE"));
                return NodeResult.completed("completed", List.of(ref), Map.of("run_id", "run-runtime", "exit_code", 0));
            }
        };
        var runtime = new dev.dual.runtime.WorkflowRuntime(ledger,
                new HandlerRegistry(List.of(fakeProvider, new ContextBuildPackageHandler()), List.of(), List.of()));
        for (var step = 0; step < 3; step++) {
            var result = runtime.resume(new dev.dual.runtime.WorkflowCommand(root, WS));
            if (result.status() != dev.dual.core.workflow.WorkflowStatus.RUNNING) {
                break;
            }
        }
        if (authorizedPath) {
            assertEquals(List.of("design.claude_design", "design.codex_arch_review"), calls,
                    ledger.loadCurrent(paths).toString());
            assertEquals(dev.dual.core.workflow.WorkflowStatus.COMPLETED, ledger.loadCurrent(paths).status());
            return;
        }
        assertFalse(calls.contains("design.codex_arch_review"), calls.toString());
        // The fake replaces the adapter handler; this slice tests the runtime publication guard.
        assertEquals(List.of("design.claude_design"), calls);
        assertFalse(Files.exists(wsDir().resolve("runs/run-runtime/outputs/design.contract.json")));
        assertEquals(dev.dual.core.workflow.WorkflowStatus.WAITING_FOR_RECOVERY, ledger.loadCurrent(paths).status());
    }

    @Test
    void twoBusinessesAndConsistentRenamesUseTheSamePublicationAndContextChain() throws Exception {
        var businesses = Map.of("converter/render.py", "Convert documents to HTML",
                "renamed/convert.py", "Convert documents to HTML",
                "site/home.html", "Publish a static portfolio",
                "renamed/index.html", "Publish a static portfolio",
                "docs/转换 说明.md", "Document a conversion tool");
        for (var entry : businesses.entrySet()) {
            var path = entry.getKey();
            approve(path, entry.getValue());
            var result = publish("run-" + Math.abs(path.hashCode()), path, "CREATE");
            assertEquals(NodeStatus.COMPLETED, result.status(), result.result().toString());
            assertFalse(Files.exists(root.resolve(path)), "CREATE must not require an existing file");
            var sidecarRef = result.outputRefs().stream().filter(ref -> ref.endsWith(".contract.json")).findFirst().orElseThrow();
            var sidecar = root.resolve(sidecarRef);
            AgentOutputLineageFixture.publish(root, WS, sidecar);
            var doc = AgentEvidenceIndex.validatedDocument(root, WS, sidecarRef, JSON).orElseThrow();
            assertEquals(path, doc.design().plannedFiles().getFirst().path());
            assertTrue(doc.changedFiles().isEmpty(), "planned files must not become actual changes");
            var context = context(result.outputRefs());
            assertEquals(NodeStatus.COMPLETED, context.status(), context.result().toString());
            assertTrue(Files.readString(root.resolve((String) context.result().get("context_package")))
                    .contains("CREATE `" + path + "`: business responsibility"));
            var manifest = EvidenceManifest.rebuild(root, wsDir(), WS, result.outputRefs(), JSON);
            assertTrue(manifest.reviewItems().stream().anyMatch(item ->
                    item.type().equals("design_result") && item.sourceRef().equals(sidecarRef)));
            var denied = publish("denied-" + Math.abs(path.hashCode()), path + ".unauthorized", "CREATE");
            assertRejected(denied, "DESIGN_SCOPE_NOT_AUTHORIZED");
        }
    }

    @Test
    void unauthorizedOrMalformedDeclarationsCannotPublishEvenWhenProseClaimsCompliance() throws Exception {
        approve("src/approved.ext");
        for (var path : List.of("../escape", "/absolute", "./src/approved.ext", "src//approved.ext",
                " src/approved.ext", ".git/config", ".agent/private", "other.ext")) {
            assertRejected(publish("bad-" + Math.abs(path.hashCode()), path, "CREATE"),
                    path.equals("other.ext") ? "DESIGN_SCOPE_NOT_AUTHORIZED" : "DESIGN_SCOPE_PATH_INVALID");
        }
        assertRejected(publish("wrong-operation", "src/approved.ext", "MODIFY"), "DESIGN_SCOPE_OPERATION_MISMATCH");
        var duplicate = candidate("duplicate", "src/approved.ext", "CREATE");
        var files = (com.fasterxml.jackson.databind.node.ArrayNode) duplicate.at("/result/design/planned_files");
        files.add(files.get(0).deepCopy());
        assertRejected(publish("duplicate", duplicate), "DESIGN_SCOPE_PATH_DUPLICATE");
        var empty = candidate("empty", "src/approved.ext", "CREATE");
        ((com.fasterxml.jackson.databind.node.ArrayNode) empty.at("/result/design/planned_files")).removeAll();
        assertEquals(NodeStatus.COMPLETED, publish("empty", empty).status());
    }

    @Test
    void absentOrStaleContractCannotPublishOrStartReviewer() throws Exception {
        assertRejected(publish("no-contract", "src/approved.ext", "CREATE"), "DESIGN_CONTRACT_NOT_QUALIFIED");
        approve("src/approved.ext");
        var contract = wsDir().resolve("artifacts/task-contract.json");
        Files.writeString(contract, Files.readString(contract).replace("Generic file delivery", "Unapproved recompile"));
        assertRejected(publish("tamper", "src/approved.ext", "CREATE"), "DESIGN_CONTRACT_NOT_QUALIFIED");
        approve("src/approved.ext");
        Files.delete(wsDir().resolve("gates/gate-node-gate-contract-review.json"));
        assertRejected(publish("no-approval", "src/approved.ext", "CREATE"), "DESIGN_CONTRACT_NOT_QUALIFIED");
    }

    @Test
    void reviewerRejectsGenericMissingTamperedAndAmbiguousSubjectsButDeduplicatesRefs() throws Exception {
        approve("src/approved.ext");
        assertEquals(NodeStatus.FAILED, context(List.of()).status());
        var first = publish("first", "src/approved.ext", "CREATE");
        var second = publish("second", "src/approved.ext", "CREATE");
        var a = first.outputRefs().stream().filter(ref -> ref.endsWith(".contract.json")).findFirst().orElseThrow();
        var b = second.outputRefs().stream().filter(ref -> ref.endsWith(".contract.json")).findFirst().orElseThrow();
        AgentOutputLineageFixture.publish(root, WS, root.resolve(a));
        AgentOutputLineageFixture.publish(root, WS, root.resolve(b));
        assertEquals(NodeStatus.COMPLETED, context(List.of(a, a)).status());
        assertEquals(NodeStatus.FAILED, context(List.of(a, b)).status());
        assertEquals(NodeStatus.FAILED, context(List.of(b, a)).status());
        var typed = (ObjectNode) JSON.readTree(root.resolve(a).toFile());
        typed.put("source_artifact_type", "agent_output_contract");
        JSON.writeValue(root.resolve(a).toFile(), typed);
        AgentOutputLineageFixture.publish(root, WS, root.resolve(a));
        assertEquals(NodeStatus.FAILED, context(List.of(a)).status());
        Files.writeString(root.resolve(b), Files.readString(root.resolve(b)).replace("business responsibility", "tampered"));
        assertThrows(IllegalStateException.class, () -> context(List.of(b)));
    }

    @Test
    void genericDesignIsRejectedWhileDesignShapeRemainsClosed() throws Exception {
        var generic = AgentResultPromptContract.render(WS, "generic", "design.codex_arch_review",
                "READY_FOR_ARCH_REVIEW", Set.of("READY_FOR_ARCH_REVIEW"));
        var candidate = (ObjectNode) JSON.readTree(generic.substring(generic.indexOf('{')));
        candidate.put("node_id", "design.claude_design");
        assertRejected(publish("generic", candidate), "artifact_type");
        var typed = candidate("closed", "src/file.ext", "CREATE");
        ((ObjectNode) typed.get("result")).putArray("revision_responses");
        assertFalse(new AgentResultEnvelopeParser().parse(typed.toString()).valid());
    }

    private NodeResult publish(String run, String path, String operation) throws Exception {
        return publish(run, candidate(run, path, operation));
    }

    private NodeResult publish(String run, ObjectNode candidate) throws Exception {
        var ref = ".agent/workstreams/" + WS + "/runs/" + run + "/outputs/design.json";
        Files.createDirectories(root.resolve(ref).getParent());
        JSON.writeValue(root.resolve(ref).toFile(), candidate);
        return new ArtifactContractValidator().validate(root, WS, "node-" + run,
                new NodeDefinition("design.claude_design", NodeKind.TASK, "acpx.agent_run",
                        Map.of("expected_artifact", "design_result", "requires_approved_task_contract", true)),
                NodeResult.completed("completed", List.of(ref), Map.of("run_id", run)));
    }

    private ObjectNode candidate(String run, String path, String operation) throws Exception {
        var prompt = AgentResultPromptContract.render(WS, run, "design.claude_design",
                "READY_FOR_ARCH_REVIEW", Set.of("READY_FOR_ARCH_REVIEW"));
        var candidate = (ObjectNode) JSON.readTree(prompt.substring(prompt.indexOf('{')));
        ((ObjectNode) candidate.get("result")).put("summary", "Fully compliant with every business requirement");
        var file = (ObjectNode) candidate.at("/result/design/planned_files/0");
        file.put("path", path).put("operation", operation).put("responsibility", "business responsibility");
        return candidate;
    }

    private void assertRejected(NodeResult result, String reason) throws Exception {
        assertEquals(NodeStatus.FAILED, result.status(), result.result().toString());
        assertTrue(result.result().toString().contains(reason), result.result().toString());
        assertFalse(result.outputRefs().stream().anyMatch(ref -> ref.endsWith(".contract.json")));
        assertEquals(NodeStatus.FAILED, context(result.outputRefs()).status(),
                "no usable architecture review context may be published from a rejected candidate");
    }

    private NodeResult context(List<String> refs) throws Exception {
        Files.createDirectories(wsDir());
        Files.writeString(wsDir().resolve("requirement.md"), "Generic file delivery");
        return new ContextBuildPackageHandler().execute(new TaskHandler.TaskContext(root, WS,
                new NodeDefinition("ctx.design", NodeKind.TASK, "context.build_package", Map.of(
                        "target_node", "design.codex_arch_review", "profile", "architecture_review",
                        "requires_approved_task_contract", true)),
                new NodeInstance("node-context", "ctx.design", NodeKind.TASK, "context.build_package",
                        NodeStatus.READY, "design", "iter-001", refs, List.of(), Map.of(), null, null, null)));
    }

    private Path wsDir() { return root.resolve(".agent/workstreams/" + WS); }

    private void approve(String path) throws Exception {
        approve(path, "Generic file delivery");
    }

    private void approve(String path, String objective) throws Exception {
        Files.createDirectories(wsDir().resolve("artifacts"));
        Files.writeString(wsDir().resolve("artifacts/task-contract.json"), """
                {"id":"tc-1","version":1,"created_at":"2026-08-26T00:00:00Z",
                 "objective":"Generic file delivery","status":"READY_FOR_REVIEW",
                 "scope":{"included":[{"path":"%s","operation":"CREATE"}],"excluded":[]},
                 "acceptance_criteria":["output works","scope preserved"],"test_requirements":["run tests"],
                 "constraints":["Keep file scope"],"non_goals":[],"risk_areas":[],"ambiguity_notes":[],"human_questions":[],
                 "compilation_pass":1,"source":{"raw_request_ref":"requirement.md","created_by":"test"}}
                """.formatted(path));
        var contract = (ObjectNode) JSON.readTree(wsDir().resolve("artifacts/task-contract.json").toFile());
        contract.put("objective", objective);
        JSON.writeValue(wsDir().resolve("artifacts/task-contract.json").toFile(), contract);
        TaskContractProjectionFixture.seedCompileBinding(root, WS);
    }
}
