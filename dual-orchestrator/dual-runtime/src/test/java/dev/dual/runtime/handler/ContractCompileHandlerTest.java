package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeInstance;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContractCompileHandlerTest {
    private static final String WORKSTREAM_ID = "ws-test-001";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    @Test
    void handlerTypeIsContractCompile() {
        assertEquals("contract.compile", new ContractCompileHandler().type());
    }

    @Test
    void missingTypedProposalFailsClosed(@TempDir Path root) throws Exception {
        prepare(root);

        var result = new ContractCompileHandler().execute(context(root));

        assertEquals(NodeStatus.FAILED, result.status());
        assertEquals("TASK_CONTRACT_PROPOSAL_MISSING", result.outcome());
        assertFalse(Files.exists(contractPath(root)));
    }

    @Test
    void reviewMarkdownCannotBecomeCanonicalContract(@TempDir Path root) throws Exception {
        prepare(root);
        var review = outputDir(root).resolve("intake-codex-product-review.md");
        Files.writeString(review, "Objective: silently replace the proposal");

        var result = new ContractCompileHandler().execute(context(root));

        assertEquals("TASK_CONTRACT_PROPOSAL_MISSING", result.outcome());
        assertFalse(Files.exists(contractPath(root)));
    }

    @Test
    void strictProposalCompilesCanonicalContract(@TempDir Path root) throws Exception {
        prepare(root);
        Files.createDirectories(root.resolve("src"));
        Files.writeString(root.resolve("src/issueWorkflow.js"), "export {}\n");
        var proposalRef = writeProposal(root, "run-001", "Add mandatory READY_FOR_REVIEW status",
                List.of("src/issueWorkflow.js"), List.of());

        var result = new ContractCompileHandler().execute(context(root, List.of(proposalRef)));

        assertEquals(NodeStatus.COMPLETED, result.status(), result.result().toString());
        var json = Files.readString(contractPath(root));
        assertTrue(json.contains("Add mandatory READY_FOR_REVIEW status"));
        assertTrue(json.contains("src/issueWorkflow.js"));
    }

    @Test
    void unroutedStrictProposalCannotOverrideRoutedProposal(@TempDir Path root) throws Exception {
        prepare(root);
        Files.createDirectories(root.resolve("src"));
        Files.writeString(root.resolve("src/old.js"), "export {}\n");
        Files.writeString(root.resolve("src/new.js"), "export {}\n");
        var routedRef = writeProposal(root, "run-001", "old objective", List.of("src/old.js"), List.of());
        writeProposal(root, "run-002", "new objective", List.of("src/new.js"), List.of());

        var result = new ContractCompileHandler().execute(context(root, List.of(routedRef)));

        assertEquals(NodeStatus.COMPLETED, result.status(), result.result().toString());
        var json = Files.readString(contractPath(root));
        assertTrue(json.contains("old objective"));
        assertFalse(json.contains("new objective"));
    }

    @Test
    void rawProposalOutputCannotBypassMissingRoutedSidecar(@TempDir Path root) throws Exception {
        prepare(root);
        var sidecarRef = writeProposal(root, "run-001", "raw objective", List.of("README.md"), List.of());
        Files.delete(root.resolve(sidecarRef));

        var result = new ContractCompileHandler().execute(context(root));

        assertEquals(NodeStatus.FAILED, result.status());
        assertEquals("TASK_CONTRACT_PROPOSAL_MISSING", result.outcome());
        assertFalse(Files.exists(contractPath(root)));
    }

    @Test
    void invalidStrictProposalDoesNotFallbackToMarkdown(@TempDir Path root) throws Exception {
        prepare(root);
        var sidecar = outputDir(root).resolve("intake-claude-brainstorm.contract.json");
        Files.writeString(sidecar, "{not-json");
        Files.writeString(outputDir(root).resolve("intake-claude-brainstorm.md"), "Objective: fallback");

        var result = new ContractCompileHandler().execute(context(root, List.of(relative(root, sidecar))));

        assertEquals(NodeStatus.FAILED, result.status());
        assertEquals("TASK_CONTRACT_PROPOSAL_PARSE_FAILED", result.outcome());
        assertFalse(Files.exists(contractPath(root)));
    }

    @Test
    void staleEnvelopeLineageIsRejected(@TempDir Path root) throws Exception {
        prepare(root);
        var proposalRef = writeProposalEnvelope(
                root, "run-001", "run-stale", "objective", List.of("README.md"), List.of());

        var result = new ContractCompileHandler().execute(context(root, List.of(proposalRef)));

        assertEquals(NodeStatus.FAILED, result.status());
        assertEquals("TASK_CONTRACT_PROPOSAL_INVALID", result.outcome());
    }

    @Test
    void openQuestionsRequireHumanResolution(@TempDir Path root) throws Exception {
        prepare(root);
        Files.createDirectories(root.resolve("src"));
        Files.writeString(root.resolve("src/issueWorkflow.js"), "export {}\n");
        var proposalRef = writeProposal(root, "run-001", "Add review", List.of("src/issueWorkflow.js"),
                List.of("Should review be mandatory?"));

        var result = new ContractCompileHandler().execute(context(root, List.of(proposalRef)));

        assertEquals(NodeStatus.FAILED, result.status());
        assertEquals("CONTRACT_RESOLUTION_REQUIRED", result.outcome());
    }

    @Test
    void existingDirectoryScopeIsAccepted(@TempDir Path root) throws Exception {
        prepare(root);
        Files.createDirectories(root.resolve("backend/tests"));
        var proposalRef = writeProposal(
                root, "run-001", "Verify backend", List.of("backend/tests/"), List.of());

        var result = new ContractCompileHandler().execute(context(root, List.of(proposalRef)));

        assertEquals(NodeStatus.COMPLETED, result.status(), result.result().toString());
    }

    private static void prepare(Path root) throws Exception {
        Files.createDirectories(outputDir(root));
        Files.writeString(workstreamDir(root).resolve("requirement.md"), "Strict proposal fixture\n");
    }

    private static String writeProposal(Path root, String runId, String objective,
                                        List<String> included, List<String> questions) throws Exception {
        return writeProposalEnvelope(root, runId, runId, objective, included, questions);
    }

    private static String writeProposalEnvelope(Path root, String directoryRunId, String envelopeRunId,
                                                String objective, List<String> included,
                                                List<String> questions) throws Exception {
        var directory = workstreamDir(root).resolve("runs").resolve(directoryRunId).resolve("outputs");
        Files.createDirectories(directory);
        var envelope = new AgentResultEnvelope(
                1, WORKSTREAM_ID, envelopeRunId, "intake.claude_brainstorm",
                new AgentResultEnvelope.TaskContractProposalResult(
                        "task_contract_proposal", "READY_FOR_PRODUCT_REVIEW", "proposal ready", false,
                        objective, new AgentResultEnvelope.ProposalScope(included, List.of("No unrelated changes")),
                        List.of("The requested behavior is observable", "Focused verification passes"),
                        List.of("Run focused verification"),
                        List.of("Keep the change scoped"), List.of("No unrelated refactor"),
                        List.of("Regression risk"), included, questions));
        var output = directory.resolve("intake-claude-brainstorm.json");
        var outputRef = relative(root, output);
        Files.writeString(output, OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(envelope));
        var document = new AgentResultDocumentProjector().outputContract(
                envelope,
                ContractRegistry.forNode("intake.claude_brainstorm"),
                "acpx.agent_run",
                "inst-intake-" + directoryRunId,
                outputRef);
        AgentContractSemantics.addSidecarFields(document);
        var sidecar = AgentOutputContractSidecar.pathFor(output);
        Files.writeString(sidecar, OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(document));
        AgentOutputLineageFixture.publish(root, WORKSTREAM_ID, sidecar);
        return relative(root, sidecar);
    }

    private static TaskHandler.TaskContext context(Path root) {
        return context(root, List.of());
    }

    private static TaskHandler.TaskContext context(Path root, List<String> inputRefs) {
        return new TaskHandler.TaskContext(
                root, WORKSTREAM_ID,
                new NodeDefinition("contract.compile", NodeKind.TASK, "contract.compile", Map.of()),
                new NodeInstance("inst-contract", "contract.compile", NodeKind.TASK, "contract.compile",
                        NodeStatus.READY, "stage-contract", "iter-1", inputRefs, List.of(), Map.of(), null,
                        Instant.now(), null));
    }

    private static String relative(Path root, Path path) {
        return root.toAbsolutePath().normalize().relativize(path.toAbsolutePath().normalize()).toString();
    }

    private static Path workstreamDir(Path root) {
        return root.resolve(".agent/workstreams").resolve(WORKSTREAM_ID);
    }

    private static Path outputDir(Path root) {
        return workstreamDir(root).resolve("runs/run-001/outputs");
    }

    private static Path contractPath(Path root) {
        return workstreamDir(root).resolve("artifacts/task-contract.json");
    }
}
