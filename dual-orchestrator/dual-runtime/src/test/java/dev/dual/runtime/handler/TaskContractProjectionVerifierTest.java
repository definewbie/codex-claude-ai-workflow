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
    void resolvedClarifyOrRejectIsNotContractApproval() throws Exception {
        var mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        var gatePath = workstreamDir.resolve("gates/gate-node-gate-contract-review.json");
        var nodePath = workstreamDir.resolve("nodes/node-gate-contract-review.json");
        for (var action : List.of("clarify", "reject")) {
            var gate = mapper.readValue(gatePath.toFile(), dev.dual.ledger.file.GateDocument.class);
            var gateResult = new LinkedHashMap<String, Object>(gate.result());
            gateResult.put("human_action", action);
            mapper.writeValue(gatePath.toFile(), new dev.dual.ledger.file.GateDocument(
                    gate.schemaVersion(), gate.gateId(), gate.nodeInstanceId(), gate.gateType(), gate.status(),
                    gate.allowedActions(), gate.openedAt(), gate.resolvedAt(), gateResult));

            var node = mapper.readValue(nodePath.toFile(), NodeInstanceDocument.class);
            var nodeResult = new LinkedHashMap<String, Object>(node.result());
            nodeResult.put("human_action", action);
            mapper.writeValue(nodePath.toFile(), new NodeInstanceDocument(
                    node.schemaVersion(), node.nodeInstanceId(), node.nodeDefinitionId(), node.kind(),
                    node.handlerType(), node.status(), node.stageId(), node.iterationId(), node.inputRefs(),
                    node.inputTransition(), node.outputRefs(), nodeResult, node.runRef(), node.latestExecutionId(),
                    node.startedAt(), node.finishedAt()));

            assertEquals("CONTRACT_NOT_APPROVED",
                    TaskContractProjectionVerifier.resolve(root, "ws-test").failure().reason());
        }
    }

    @Test
    void rejectsGateSourceIdentityMismatch() throws Exception {
        var mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        var gatePath = workstreamDir.resolve("gates/gate-node-gate-contract-review.json");
        var gate = mapper.readValue(gatePath.toFile(), dev.dual.ledger.file.GateDocument.class);
        var result = new LinkedHashMap<String, Object>(gate.result());
        result.put("source_node_instance_id", "node-forged");
        mapper.writeValue(gatePath.toFile(), new dev.dual.ledger.file.GateDocument(
                gate.schemaVersion(), gate.gateId(), gate.nodeInstanceId(), gate.gateType(), gate.status(),
                gate.allowedActions(), gate.openedAt(), gate.resolvedAt(), result));

        assertEquals("GATE_SOURCE_MISMATCH",
                TaskContractProjectionVerifier.resolve(root, "ws-test").failure().reason());
    }

    @Test
    void rejectsCompletedRecompileUntilItIsApproved() throws Exception {
        var mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        var original = mapper.readValue(
                workstreamDir.resolve("nodes/node-contract-compile.json").toFile(), NodeInstanceDocument.class);
        mapper.writeValue(workstreamDir.resolve("nodes/node-contract-recompile.json").toFile(), new NodeInstanceDocument(
                original.schemaVersion(), "node-contract-recompile", original.nodeDefinitionId(), original.kind(),
                original.handlerType(), original.status(), original.stageId(), "iter-002", original.inputRefs(),
                original.inputTransition(), original.outputRefs(), original.result(), original.runRef(),
                original.latestExecutionId(), Instant.parse("2026-08-26T00:01:01Z"),
                Instant.parse("2026-08-26T00:01:02Z")));

        assertEquals("UNAPPROVED_RECOMPILE",
                TaskContractProjectionVerifier.resolve(root, "ws-test").failure().reason());
    }

    @Test
    void reapprovalSwitchesQualificationToTheNewCompileOccurrence() throws Exception {
        var mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        var originalCompile = mapper.readValue(
                workstreamDir.resolve("nodes/node-contract-compile.json").toFile(), NodeInstanceDocument.class);
        var newCompileId = "node-contract-recompile";
        mapper.writeValue(workstreamDir.resolve("nodes/" + newCompileId + ".json").toFile(), new NodeInstanceDocument(
                originalCompile.schemaVersion(), newCompileId, originalCompile.nodeDefinitionId(), originalCompile.kind(),
                originalCompile.handlerType(), originalCompile.status(), originalCompile.stageId(), "iter-002",
                originalCompile.inputRefs(), originalCompile.inputTransition(), originalCompile.outputRefs(),
                originalCompile.result(), originalCompile.runRef(), originalCompile.latestExecutionId(),
                Instant.parse("2026-08-26T00:01:01Z"), Instant.parse("2026-08-26T00:01:02Z")));

        var oldGate = mapper.readValue(
                workstreamDir.resolve("gates/gate-node-gate-contract-review.json").toFile(),
                dev.dual.ledger.file.GateDocument.class);
        var newGateNodeId = "node-gate-contract-reapproval";
        var newGateId = "gate-" + newGateNodeId;
        var gateNodeResult = new LinkedHashMap<String, Object>();
        gateNodeResult.put("source_node_instance_id", newCompileId);
        gateNodeResult.put("human_action", "approved");
        gateNodeResult.put("requested_action", "approve");
        gateNodeResult.put("note", "reapproved");
        mapper.writeValue(workstreamDir.resolve("nodes/" + newGateNodeId + ".json").toFile(), new NodeInstanceDocument(
                1, newGateNodeId, "gate.contract_review", NodeKind.GATE, "human.review", NodeStatus.COMPLETED,
                "gate", "iter-002", originalCompile.outputRefs(), Map.of(), originalCompile.outputRefs(),
                Map.copyOf(gateNodeResult), null, "", Instant.parse("2026-08-26T00:01:03Z"),
                Instant.parse("2026-08-26T00:01:04Z")));
        mapper.writeValue(workstreamDir.resolve("gates/" + newGateId + ".json").toFile(),
                new dev.dual.ledger.file.GateDocument(
                        1, newGateId, newGateNodeId, oldGate.gateType(), "resolved", oldGate.allowedActions(),
                        Instant.parse("2026-08-26T00:01:03Z"), Instant.parse("2026-08-26T00:01:04Z"),
                        Map.of(
                                "subject_input_refs", originalCompile.outputRefs(),
                                "source_outcome", "completed",
                                "source_node_instance_id", newCompileId,
                                "human_action", "approved",
                                "note", "reapproved")));
        Files.writeString(workstreamDir.resolve("events.jsonl"), mapper.writeValueAsString(Map.of(
                "event_id", "evt-000002",
                "ts", "2026-08-26T00:01:03Z",
                "event_type", "node_routed",
                "payload", Map.of(
                        "from", "contract.compile",
                        "outcome", "completed",
                        "to", "gate.contract_review",
                        "node_instance_id", newGateNodeId,
                        "gate_id", newGateId,
                        "source_node_instance_id", newCompileId))) + System.lineSeparator(),
                java.nio.file.StandardOpenOption.APPEND);

        var qualified = TaskContractProjectionVerifier.resolve(root, "ws-test").qualified().orElseThrow();

        assertEquals(newCompileId, qualified.compileNodeInstanceId());
        assertEquals("evt-000002", qualified.approvalEventId());
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
