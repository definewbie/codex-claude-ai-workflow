package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeInstance;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskContractProjectionVerifierTest {
    @TempDir
    Path root;

    private Path workstreamDir;

    @BeforeEach
    void setUp() throws Exception {
        workstreamDir = root.resolve(".agent/workstreams/ws-test");
        Files.createDirectories(workstreamDir.resolve("artifacts"));
        Files.writeString(workstreamDir.resolve("artifacts/task-contract.json"), """
                {
                  "id":"tc-1","version":1,"created_at":"2026-08-26T00:00:00Z",
                  "objective":"Build authentication","status":"READY_FOR_REVIEW",
                  "scope":{"included":[{"path":"src/Auth.java","operation":"CREATE"}],"excluded":[]},
                  "acceptance_criteria":["registration works","login works"],
                  "test_requirements":["Run auth tests"],"constraints":["Keep scope narrow"],
                  "non_goals":[],"risk_areas":[],"evidence_refs":["src/Auth.java"],
                  "ambiguity_notes":[],"human_questions":[],"compilation_pass":1,
                  "source":{"raw_request_ref":"requirement.md","created_by":"contract_compiler"}
                }
                """);
        TaskContractProjectionFixture.seedCompileBinding(root, "ws-test");
    }

    @Test
    void resolvesOneQualifiedSnapshotFromLatestCompileBinding() throws Exception {
        var resolution = TaskContractProjectionVerifier.resolve(root, "ws-test");

        assertTrue(resolution.qualified().isPresent());
        assertEquals("tc-1", resolution.qualified().orElseThrow().contract().id());
        assertEquals(TaskContractArtifactWriter.sha256(
                        workstreamDir.resolve("artifacts/task-contract.json")),
                resolution.qualified().orElseThrow().sha256());
    }

    @Test
    void rejectsContractMutationAfterCompile() throws Exception {
        var path = workstreamDir.resolve("artifacts/task-contract.json");
        Files.writeString(path, Files.readString(path).replace("Build authentication", "Mutated objective"));

        var resolution = TaskContractProjectionVerifier.resolve(root, "ws-test");

        assertEquals("HASH_MISMATCH", resolution.failure().reason());
    }

    @Test
    void rejectsArtifactWithoutCompletedCompileBinding() throws Exception {
        Files.delete(workstreamDir.resolve("nodes/node-contract-compile.json"));

        var resolution = TaskContractProjectionVerifier.resolve(root, "ws-test");

        assertEquals("COMPILE_BINDING_MISSING", resolution.failure().reason());
    }

    @Test
    void rejectsUnreadableNodeLedgerInsteadOfSelectingPartialHistory() throws Exception {
        Files.writeString(workstreamDir.resolve("nodes/broken.json"), "{");

        var resolution = TaskContractProjectionVerifier.resolve(root, "ws-test");

        assertEquals("NODE_LEDGER_UNREADABLE", resolution.failure().reason());
    }

    @Test
    void qualifiedSnapshotFeedsAllContextConsumersAfterDiskMutation() throws Exception {
        var qualified = TaskContractProjectionVerifier.resolve(root, "ws-test").qualified().orElseThrow();
        var contractPath = workstreamDir.resolve("artifacts/task-contract.json");
        Files.writeString(contractPath, Files.readString(contractPath)
                .replace("Build authentication", "MUTATED-DISK-OBJECTIVE")
                .replace("src/Auth.java", "src/Mutated.java"));
        var mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        var definition = new NodeDefinition("ctx.acceptance", NodeKind.TASK, "context.build_package", Map.of(
                "profile", "acceptance", "target_node", "acceptance.codex_verifier_review"));
        var context = new TaskHandler.TaskContext(
                root,
                "ws-test",
                definition,
                new NodeInstance("node-context", definition.id(), NodeKind.TASK, definition.handlerType(),
                        NodeStatus.READY, "acceptance", "iter-001", List.of(), List.of(), Map.of(),
                        null, null, null));
        var snapshot = Optional.of(qualified);

        var manifest = EvidenceManifest.rebuild(root, workstreamDir, "ws-test", List.of(), mapper, snapshot);
        var governance = RepoScopeGovernance.build(root, workstreamDir, context, mapper, snapshot);
        var stateEvidence = new StringBuilder();
        ContextStateEvidenceBoundaryRenderer.append(stateEvidence, context, workstreamDir, mapper, snapshot);
        var contractSection = new StringBuilder();
        ContextPackageSectionRenderer.appendQualifiedTaskContract(contractSection, qualified);

        assertTrue(manifest.items().stream().anyMatch(item -> item.summary().contains("Build authentication")));
        assertTrue(governance.plan().toString().contains("src/Auth.java"));
        assertTrue(stateEvidence.toString().contains("Build authentication"));
        assertTrue(contractSection.toString().contains("Build authentication"));
        assertTrue(!manifest.toMap().toString().contains("MUTATED-DISK-OBJECTIVE"));
        assertTrue(!governance.plan().toString().contains("src/Mutated.java"));
    }
}
