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

    @Test
    void aSuccessfulWriterWithWrongIdentityCannotDispatchProvider() throws Exception {
        assertRejected("identity");
    }

    @Test
    void retainingManifestRefsWithoutRequiredPromptContentCannotDispatchProvider() throws Exception {
        assertRejected("body");
    }

    @Test
    void partialManifestCannotDispatchProvider() throws Exception {
        assertRejected("partial");
    }

    @Test
    void manifestWriteFailureRemainsAnIoFailureBeforeProvider() throws Exception {
        assertWriteFailure("design.claude_design");
    }

    @Test
    void nonAuthoredSiblingPreservesTheSameIoFailureBeforeProvider() throws Exception {
        assertWriteFailure("intake.codex_product_review");
    }

    private void assertWriteFailure(String nodeId) throws Exception {
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
        assertSame(diskFailure, assertThrows(IOException.class, () -> handler.execute(context)));
        assertEquals(1, writes.get());
        assertEquals(0, calls.get());
    }

    @Test
    void prepareOnlyWritesAndReadsTheSameManifestWithoutProvider() throws Exception {
        var context = context();
        var result = new AcpxAgentTaskHandler(forbiddenTransport(new AtomicInteger()), true).execute(context);
        assertEquals(NodeStatus.COMPLETED, result.status(), result.result().toString());
        var run = JSON.readTree(root.resolve((String) result.result().get("run_metadata")).toFile());
        var manifestPath = root.resolve(run.path("promptManifestPath").asText());
        var manifest = JSON.readTree(manifestPath.toFile());
        var snapshot = JSON.treeToValue(manifest.get("authoredContext"), AuthoredExecutionContext.Snapshot.class);
        AuthoredExecutionContext.verifyManifest(root, manifestPath, snapshot);
        assertTrue(result.result().containsKey("prepared"));
        assertFalse(run.has("authoredContext"), "run metadata locates the manifest; it must not copy its payload");
    }

    private void assertRejected(String corruption) throws Exception {
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
        var result = new AcpxAgentTaskHandler(forbiddenTransport(calls), false, new AcpxUsageReader(), writer).execute(context);
        assertEquals(1, writes.get(), "the writer returned; dispatch must independently verify its output");
        assertEquals(0, calls.get());
        assertEquals(NodeStatus.FAILED, result.status());
        assertEquals("REQUIRED_AGENT_EVIDENCE_MISSING", result.outcome());
        assertEquals("AUTHORED_CONTEXT_INVALID", result.result().get("failure_code"));
    }

    private TaskHandler.TaskContext context() throws Exception {
        return context("design.claude_design");
    }

    private TaskHandler.TaskContext context(String nodeId) throws Exception {
        var ws = "ws-manifest";
        var paths = new WorkstreamPaths(root, new DualId(ws));
        var design = new NodeDefinition(nodeId, NodeKind.TASK, "acpx.agent_run", Map.of(
                "agent", "claude", "session", "design", "prompt_template", "design-claude",
                "permission_profile", "observe", "requires_approved_task_contract", true));
        var ledger = new WorkflowFileLedger();
        var definition = new WorkflowDefinition("manifest-fixture", 1, design.id(), List.of(design,
                new NodeDefinition("contract.compile", NodeKind.TASK, "contract.compile", Map.of()),
                new NodeDefinition("gate.contract_review", NodeKind.GATE, "human.review", Map.of("gate_purpose", "contract"))),
                List.of(new EdgeDefinition("contract.compile", "completed", "gate.contract_review"),
                        new EdgeDefinition("gate.contract_review", "approved", design.id())));
        ledger.initialize(paths, "manifest-fixture", definition);
        DesignContractFixture.approve(root, ws);
        Files.writeString(paths.workstreamDir().resolve("requirement.md"), "Design a file conversion tool");
        var instance = ledger.loadNode(paths, ledger.loadCurrent(paths).activeNodeInstanceIds().getFirst());
        return new TaskHandler.TaskContext(root, ws, design, new NodeInstance(
                instance.nodeInstanceId(), instance.nodeDefinitionId(), instance.kind(), instance.handlerType(),
                instance.status(), instance.stageId(), instance.iterationId(), instance.inputRefs(),
                instance.outputRefs(), instance.result(), instance.runRef(), instance.startedAt(), instance.finishedAt()));
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
