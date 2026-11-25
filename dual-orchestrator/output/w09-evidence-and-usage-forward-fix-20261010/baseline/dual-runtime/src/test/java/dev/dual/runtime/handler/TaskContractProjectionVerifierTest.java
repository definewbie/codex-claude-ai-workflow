package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeInstance;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeStatus;
import dev.dual.ledger.file.NodeInstanceDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskContractProjectionVerifierTest {
    @TempDir
    Path root;

    private Path workstreamDir;

    private AuthoredStageSequenceTest.Fixture fixture;
    private TaskContractProjectionVerifier.QualifiedTaskContract qualified;
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() throws Exception {
        fixture = new AuthoredStageSequenceTest.Fixture(root, "src/Auth.java",
                AuthoredStageSequenceTest.PlanTopology.AUTOMATIC, "ws-test");
        fixture.before("implement.claude_implement");
        root = fixture.root;
        workstreamDir = fixture.paths.workstreamDir();
        qualified = resolve().qualified().orElseThrow();
    }

    private TaskContractProjectionVerifier.Resolution resolve() {
        return TaskContractProjectionVerifier.resolve(fixture.ledger, root, fixture.ws);
    }

    @Test
    void resolvesOneQualifiedSnapshotFromActualReviewedAndApprovedSequence() throws Exception {
        assertEquals(dev.dual.core.contract.TaskContract.Phase.EXECUTION, qualified.contract().phase());
        assertTrue(qualified.contractRef().contains("/contracts/" + qualified.compileNodeInstanceId() + "/"));
        assertEquals(TaskContractArtifactWriter.sha256(root.resolve(qualified.contractRef())), qualified.sha256());
        assertTrue(qualified.contract().scope().included().stream().anyMatch(e -> e.path().equals("src/Auth.java")));
        assertTrue(fixture.calls.containsAll(List.of("design.codex_arch_review", "plan.codex_plan_review")));
        assertTrue(!fixture.calls.contains("implement.claude_implement"));
    }

    @Test
    void rejectsContractMutationAfterCompile() throws Exception {
        var path = root.resolve(qualified.contractRef());
        Files.writeString(path, Files.readString(path).replace("Verify output", "Mutated objective"));
        assertEquals("HASH_MISMATCH", resolve().failure().reason());
    }

    @Test
    void rejectsArtifactWithoutCompletedCompileBinding() throws Exception {
        Files.delete(fixture.paths.nodeJson(qualified.compileNodeInstanceId()));
        assertEquals("COMPILE_BINDING_MISSING", resolve().failure().reason());
    }

    @Test
    void rejectsUnreadableNodeLedgerInsteadOfSelectingPartialHistory() throws Exception {
        Files.writeString(workstreamDir.resolve("nodes/broken.json"), "{");
        assertEquals("NODE_LEDGER_UNREADABLE", resolve().failure().reason());
    }

    @Test
    void resolvedClarifyOrRejectCannotBeUsedAsApproval() throws Exception {
        var gatePath = root.resolve(qualified.approvalGateRef());
        var gate = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(gatePath.toFile());
        var nodePath = fixture.paths.nodeJson(gate.path("nodeInstanceId").asText());
        var node = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(nodePath.toFile());
        for (var action : List.of("clarify", "reject")) {
            ((com.fasterxml.jackson.databind.node.ObjectNode)gate.path("result")).put("human_action", action);
            ((com.fasterxml.jackson.databind.node.ObjectNode)node.path("result")).put("human_action", action);
            mapper.writeValue(gatePath.toFile(), gate); mapper.writeValue(nodePath.toFile(), node);
            assertEquals("CONTRACT_NOT_APPROVED", resolve().failure().reason());
        }
    }

    @Test
    void rejectsGateSourceIdentityMismatch() throws Exception {
        var gatePath = root.resolve(qualified.approvalGateRef());
        var gate = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(gatePath.toFile());
        ((com.fasterxml.jackson.databind.node.ObjectNode) gate.path("result")).put("source_node_instance_id", "node-forged");
        mapper.writeValue(gatePath.toFile(), gate);
        assertEquals("GATE_SOURCE_MISMATCH", resolve().failure().reason());
    }

    @Test
    void copiedNewerCompilerCannotBorrowTheOriginalApproval() throws Exception {
        var original = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(fixture.paths.nodeJson(qualified.compileNodeInstanceId()).toFile());
        original.put("nodeInstanceId", "node-unapproved-recompile");
        original.put("startedAt", Instant.now().plusSeconds(1).toString());
        original.put("finishedAt", Instant.now().plusSeconds(2).toString());
        mapper.writeValue(fixture.paths.nodeJson("node-unapproved-recompile").toFile(), original);
        assertEquals("UNAPPROVED_RECOMPILE", resolve().failure().reason());
    }

    @Test
    void realContractRepairSwitchesQualificationOnlyAfterTheNewApproval() throws Exception {
        var f = new AcceptanceRepairSequenceTest.Fixture(root.resolve("repair"), "CONTRACT_GAP", "site/home.html");
        f.until("acceptance.classify_failure");
        var old = TaskContractProjectionVerifier.resolve(f.ledger, f.root, f.ws).qualified().orElseThrow();
        f.step(); f.until("gate.human_requirement_review");
        assertEquals(old.sha256(), TaskContractProjectionVerifier.resolve(f.ledger, f.root, f.ws).qualified().orElseThrow().sha256());
        f.approve(); f.until("gate.contract_review");
        assertTrue(TaskContractProjectionVerifier.resolve(f.ledger, f.root, f.ws).qualified().isEmpty());
        f.approve();
        var fresh = TaskContractProjectionVerifier.resolve(f.ledger, f.root, f.ws).qualified().orElseThrow();
        org.junit.jupiter.api.Assertions.assertNotEquals(old.compileNodeInstanceId(), fresh.compileNodeInstanceId());
        org.junit.jupiter.api.Assertions.assertNotEquals(old.approvalEventId(), fresh.approvalEventId());
        assertTrue(Files.isRegularFile(f.root.resolve(old.contractRef())), "historical contract remains immutable");
    }

    @Test
    void qualifiedSnapshotFeedsAllContextConsumersAfterDiskMutation() throws Exception {
        var qualified = TaskContractProjectionVerifier.resolve(fixture.ledger, root, "ws-test").qualified().orElseThrow();
        var contractPath = root.resolve(qualified.contractRef());
        Files.writeString(contractPath, Files.readString(contractPath)
                .replace("Verify output", "MUTATED-DISK-OBJECTIVE")
                .replace("src/Auth.java", "src/Mutated.java"));
        var mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        var definition = new NodeDefinition("ctx.acceptance", NodeKind.TASK, "context.build_package", Map.of(
                "profile", "acceptance", "target_node", "acceptance.codex_verifier_review"));
        var context = new TaskHandler.TaskContext(root, "ws-test", definition, new NodeInstance("node-context", definition.id(), NodeKind.TASK, definition.handlerType(),
                        NodeStatus.READY, "acceptance", "iter-001", List.of(), List.of(), Map.of(),
                        null, null, null), fixture.ledger);
        var snapshot = Optional.of(qualified);

        var manifest = EvidenceManifest.rebuild(fixture.ledger, root, workstreamDir, "ws-test", List.of(), mapper, snapshot);
        var governance = RepoScopeGovernance.build(root, workstreamDir, context, mapper, snapshot);
        var stateEvidence = new StringBuilder();
        ContextStateEvidenceBoundaryRenderer.append(stateEvidence, context, workstreamDir, mapper, snapshot);
        var contractSection = new StringBuilder();
        ContextPackageSectionRenderer.appendQualifiedTaskContract(contractSection, qualified);

        assertTrue(manifest.items().stream().anyMatch(item -> item.summary().contains("Verify output")));
        assertTrue(governance.plan().toString().contains("src/Auth.java"));
        assertTrue(stateEvidence.toString().contains("Verify output"));
        assertTrue(contractSection.toString().contains("Verify output"));
        assertTrue(!manifest.toMap().toString().contains("MUTATED-DISK-OBJECTIVE"));
        assertTrue(!governance.plan().toString().contains("src/Mutated.java"));
    }
}
