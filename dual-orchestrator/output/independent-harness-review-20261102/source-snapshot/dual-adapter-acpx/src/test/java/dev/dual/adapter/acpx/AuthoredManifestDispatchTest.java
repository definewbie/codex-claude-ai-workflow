package dev.dual.adapter.acpx;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.dual.core.id.DualId;
import dev.dual.core.workflow.*;
import dev.dual.ledger.file.*;
import dev.dual.runtime.handler.AuthoredExecutionContext;
import dev.dual.runtime.handler.TaskHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import dev.dual.runtime.*;
import dev.dual.runtime.handler.HandlerRegistry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AuthoredManifestDispatchTest {
    private static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule());
    @TempDir Path root;

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void aSuccessfulWriterWithWrongIdentityCannotDispatchProvider(boolean queued) throws Exception {
        assertRejected("identity", queued);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void retainingManifestRefsWithoutRequiredPromptContentCannotDispatchProvider(boolean queued) throws Exception {
        assertRejected("body", queued);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void partialManifestCannotDispatchProvider(boolean queued) throws Exception {
        assertRejected("partial", queued);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void manifestWriteFailureRemainsAnIoFailureBeforeProvider(boolean queued) throws Exception {
        assertWriteFailure("design.claude_design", queued);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void nonAuthoredSiblingPreservesTheSameIoFailureBeforeProvider(boolean queued) throws Exception {
        assertWriteFailure("intake.codex_product_review", queued);
    }

    private void assertWriteFailure(String nodeId, boolean queued) throws Exception {
        var context = context(nodeId);
        var calls = new AtomicInteger();
        var writes = new AtomicInteger();
        var diskFailure = new IOException("simulated manifest write failure");
        var writer = new PromptManifestWriter() {
            @Override
            void write(Path path, String ws, String run, String node, String agent, String template,
                       Path prompt, List<AgentPromptRenderer.PromptSection> sections,
                       AuthoredExecutionContext.Snapshot snapshot) throws IOException {
                assertEquals(nodeId.equals("design.claude_design"), snapshot != null);
                writes.incrementAndGet();
                throw diskFailure;
            }
        };
        var handler = new AcpxAgentTaskHandler(forbiddenTransport(calls), false, new AcpxUsageReader(), writer);
        var result = execute(context, handler, queued);
        assertEquals("INPUT_PREPARATION_FAILED", result.result().get("reason"));
        assertEquals(IOException.class.getName(), result.result().get("failure_code"));
        assertEquals(List.of(diskFailure.getMessage()), result.result().get("errors"));
        assertEquals(1, writes.get());
        assertEquals(0, calls.get());
    }

    @Test
    void prepareOnlyWritesAndReadsTheSameManifestWithoutProvider() throws Exception {
        var context = context();
        var result = new AcpxAgentTaskHandler(forbiddenTransport(new AtomicInteger()), true).execute(AcpxTestLedger.claim(context));
        assertEquals(NodeStatus.COMPLETED, result.status(), result.result().toString());
        var run = JSON.readTree(root.resolve((String) result.result().get("run_metadata")).toFile());
        var manifestPath = root.resolve(run.path("promptManifestPath").asText());
        var manifest = JSON.readTree(manifestPath.toFile());
        var snapshot = JSON.treeToValue(manifest.get("authoredContext"), AuthoredExecutionContext.Snapshot.class);
        AuthoredExecutionContext.verifyManifest(root, manifestPath, snapshot);
        assertTrue(result.result().containsKey("prepared"));
        assertFalse(run.has("authoredContext"), "run metadata locates the manifest; it must not copy its payload");
    }

    private void assertRejected(String corruption, boolean queued) throws Exception {
        var context = context();
        var writes = new AtomicInteger();
        var calls = new AtomicInteger();
        var writer = new PromptManifestWriter() {
            @Override
            void write(Path path, String ws, String run, String node, String agent, String template,
                       Path prompt, List<AgentPromptRenderer.PromptSection> sections,
                       AuthoredExecutionContext.Snapshot snapshot) throws IOException {
                super.write(path, ws, run, node, agent, template, prompt, sections, snapshot);
                if (corruption.equals("identity")) {
                    var json = (ObjectNode) JSON.readTree(path.toFile());
                    ((ObjectNode) json.get("authoredContext")).put("runId", "run-forged");
                    JSON.writeValue(path.toFile(), json);
                } else if (corruption.equals("partial")) {
                    Files.writeString(path, "{");
                } else {
                    Files.writeString(prompt, "Only refs remain; required authored input was lost.");
                }
                writes.incrementAndGet();
            }
        };
        var result = execute(context, new AcpxAgentTaskHandler(forbiddenTransport(calls), false, new AcpxUsageReader(), writer), queued);
        assertEquals(1, writes.get(), "the writer returned; dispatch must independently verify its output");
        assertEquals(0, calls.get());
        assertEquals(NodeStatus.FAILED, result.status());
        assertEquals("REQUIRED_AGENT_EVIDENCE_MISSING", result.result().get("reason"));
        assertEquals("AUTHORED_CONTEXT_INVALID", result.result().get("failure_code"));
    }

    private NodeInstanceDocument execute(TaskHandler.TaskContext context, AcpxAgentTaskHandler handler, boolean queued) throws Exception {
        var ledger = AcpxTestLedger.ledger();
        var paths = new WorkstreamPaths(root, new DualId(context.workstreamId()));
        var handlers = new HandlerRegistry(List.of(handler), List.of(), List.of());
        var command = new WorkflowCommand(root, context.workstreamId());
        new WorkflowRuntime(ledger, handlers, queued ? AgentTaskDispatchMode.WORKER : AgentTaskDispatchMode.INLINE).resume(command);
        if (queued) new AgentWorker(ledger, handlers).runOne(command);
        var current = ledger.loadCurrent(paths);
        assertEquals(WorkflowStatus.WAITING_FOR_RECOVERY, current.status());
        var node = ledger.loadNode(paths, current.activeNodeInstanceIds().getFirst());
        assertTrue(node.outputRefs().isEmpty());
        assertFalse(node.result().containsKey("parse_error"));
        assertEquals("input_preparation_failure", RecoveryAssessment.from(node, root, context.workstreamId()).classification());
        return node;
    }

    private TaskHandler.TaskContext context() throws Exception {
        return context("design.claude_design");
    }

    private TaskHandler.TaskContext context(String nodeId) throws Exception {
        var ws = "ws-manifest";
        var paths = new WorkstreamPaths(root, new DualId(ws));
        var ledger = AcpxTestLedger.ledger();
        if (nodeId.equals("intake.codex_product_review")) {
            var intake = new NodeDefinition(nodeId, NodeKind.TASK, "acpx.agent_run", Map.of(
                    "agent", "codex", "session", "intake", "prompt_template", "intake-codex-product-review", "permission_profile", "observe"));
            AcpxTestLedger.initialize(ledger, paths, "manifest-fixture", new WorkflowDefinition("manifest-fixture", 1, nodeId, List.of(intake), List.of()));
            return new TaskHandler.TaskContext(root, ws, intake, ledger.loadCoreNode(paths, ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst()), ledger);
        }
        new dev.dual.runtime.handler.AuthoredStageSequenceTest.Fixture(root, "repo/relative/new.ext",
                dev.dual.runtime.handler.AuthoredStageSequenceTest.PlanTopology.DIRECT_HUMAN, ws, null, ledger)
                .before(nodeId);
        var definition = ledger.loadDefinition(paths).nodes().stream().filter(n -> n.id().equals(nodeId)).findFirst().orElseThrow();
        var config = new java.util.LinkedHashMap<>(definition.config());
        config.putAll(Map.of("agent", "claude", "session", "design", "prompt_template", "design-claude", "permission_profile", "observe"));
        definition = new NodeDefinition(definition.id(), definition.kind(), definition.handlerType(), config);
        // Runtime consumes the declared adapter binding too, not just this returned context.
        var workflow = ledger.loadDefinition(paths);
        var bound = definition;
        var declaredDocument = (ObjectNode) JSON.readTree(paths.workflowDefinitionJson().toFile());
        declaredDocument.set("nodes", JSON.valueToTree(workflow.nodes().stream().map(n -> n.id().equals(nodeId) ? bound : n).toList()));
        JSON.writeValue(paths.workflowDefinitionJson().toFile(), declaredDocument);
        var instance = ledger.loadCoreNode(paths, ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst());
        return new TaskHandler.TaskContext(root, ws, definition, instance, ledger);
    }

    private AcpxAgentTransport forbiddenTransport(AtomicInteger calls) {
        return new AcpxAgentTransport() {
            @Override public AcpxRunResult runClaudeDesign(AcpxRunRequest request) {
                calls.incrementAndGet();
                throw new AssertionError("provider must not be invoked");
            }
        };
    }
}
