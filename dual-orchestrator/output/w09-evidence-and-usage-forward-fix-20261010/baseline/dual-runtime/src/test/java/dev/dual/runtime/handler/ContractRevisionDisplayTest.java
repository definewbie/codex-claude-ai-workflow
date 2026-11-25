package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeInstance;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ContractRevisionDisplayTest {
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
    @TempDir Path root;

    @Test
    void realHumanRevisionShowsSameCumulativeEvidenceInContextAndBothPackets() throws Exception {
        var f = new AcceptanceRepairSequenceTest.Fixture(root, "CONTRACT_GAP", "docs/output.txt");
        f.until("acceptance.classify_failure");
        f.step();
        f.until("intake.codex_product_review");
        LateContractRevisionContextTest.rejectReview(f);
        f.step();
        f.until("gate.human_requirement_review");
        f.step();
        f.ledger.resolveOpenGate(f.paths, f.ledger.loadDefinition(f.paths), "revise", "Preserve the output example");
        f.until("context.intake_codex_product_review");
        var contextNode = active(f);
        var authoritativeBefore = authorityHashes(f);
        f.step();
        var completedContext = f.ledger.loadNode(f.paths, contextNode.nodeInstanceId());
        var markdown = Files.readString(root.resolve(completedContext.result().get("context_package").toString()));
        var projection = revisionJson(markdown);
        assertEquals(3, projection.path("obligations").size(), projection.toPrettyString());
        assertTrue(projection.path("obligations").toString().contains("define output behavior"));
        assertTrue(projection.path("obligations").toString().contains("Review B obligation"));
        assertTrue(projection.path("obligations").toString().contains("Preserve the output example"));
        assertEquals(projection.path("obligations").size(), projection.path("revision_responses").size());
        assertNotEquals(projection.at("/direct_parent/proposal_id"), projection.at("/candidate/proposal_id"));
        assertNotEquals(projection.at("/late_root/approvedProposalReference/proposal_id"),
                projection.at("/direct_parent/proposal_id"));
        assertEquals(authoritativeBefore, authorityHashes(f));
        var metadata = MAPPER.readTree(root.resolve(completedContext.result().get("context_package_metadata").toString()).toFile());
        assertTrue(metadata.at("/requiredTypedSubjectTokens/contract_revision").asInt() > 0);

        f.until("gate.human_requirement_review");
        assertPacket(f, projection);
        f.approve();
        f.until("gate.contract_review");
        assertPacket(f, projection);
    }

    @Test
    void tamperedRootBlocksRealContextAndPacketsEvenWithIntactCandidate() throws Exception {
        for (var target : List.of("context.intake_codex_product_review", "gate.human_requirement_review", "gate.contract_review")) {
            var f = new AcceptanceRepairSequenceTest.Fixture(root.resolve(target), "CONTRACT_GAP", "docs/output.txt");
            f.until("acceptance.classify_failure");
            f.step();
            f.until(target);
            var node = active(f);
            var resolved = new TaskContractRevisionContextResolver(dev.dual.runtime.RuntimeTestLedger.ledger()).resolveForIteration(f.root, f.ws, node.iterationId());
            assertTrue(resolved.valid(), resolved.errors().toString());
            Files.writeString(f.root.resolve(resolved.context().lateRoot().acceptanceRef()), "{}");
            var instance = new NodeInstance(node.nodeInstanceId(), node.nodeDefinitionId(), node.kind(), node.handlerType(),
                    node.status(), node.stageId(), node.iterationId(), node.inputRefs(), node.outputRefs(), node.result(),
                    node.runRef(), node.startedAt(), node.finishedAt());
            var definition = f.ledger.loadDefinition(f.paths).node(node.nodeDefinitionId());
            var before = authorityHashes(f);
            var calls = List.copyOf(f.calls);
            if (target.startsWith("context.")) {
                var result = new ContextBuildPackageHandler().execute(new TaskHandler.TaskContext(f.root, f.ws, definition, instance, dev.dual.runtime.RuntimeTestLedger.ledger()));
                assertEquals(NodeStatus.FAILED, result.status());
                assertEquals("CONTRACT_REVISION_CONTEXT_INVALID", result.result().get("failure_code"));
                assertFalse(Files.exists(f.paths.workstreamDir().resolve("context/" + node.nodeInstanceId() + ".md")));
            } else {
                var packetPath = f.paths.workstreamDir().resolve("reviews/" + node.nodeInstanceId() + ".json");
                var markdownPath = f.paths.workstreamDir().resolve("reviews/" + node.nodeInstanceId() + ".md");
                var packetBefore = Files.readAllBytes(packetPath);
                var markdownBefore = Files.readAllBytes(markdownPath);
                assertThrows(IOException.class, () -> new HumanReviewGateHandler().openOrInspect(
                        new GateHandler.GateContext(f.root,
                f.ws,
                definition,
                instance,
                f.ledger.loadOpenGateForNode(f.paths, node.nodeInstanceId()).result().get("source_outcome").toString(),
                dev.dual.runtime.RuntimeTestLedger.ledger())));
                assertArrayEquals(packetBefore, Files.readAllBytes(packetPath));
                assertArrayEquals(markdownBefore, Files.readAllBytes(markdownPath));
            }
            assertEquals(before, authorityHashes(f));
            assertEquals(calls, f.calls);
        }
    }

    private void assertPacket(AcceptanceRepairSequenceTest.Fixture f, com.fasterxml.jackson.databind.JsonNode expected)
            throws Exception {
        var node = active(f);
        var before = authorityHashes(f);
        var instance = new NodeInstance(node.nodeInstanceId(), node.nodeDefinitionId(), node.kind(), node.handlerType(),
                node.status(), node.stageId(), node.iterationId(), node.inputRefs(), node.outputRefs(), node.result(),
                node.runRef(), node.startedAt(), node.finishedAt());
        var result = new HumanReviewGateHandler().openOrInspect(new GateHandler.GateContext(f.root,
                f.ws,
                f.ledger.loadDefinition(f.paths).node(node.nodeDefinitionId()),
                instance,
                f.ledger.loadOpenGateForNode(f.paths, node.nodeInstanceId()).result().get("source_outcome").toString(),
                dev.dual.runtime.RuntimeTestLedger.ledger()));
        var packet = MAPPER.readTree(f.root.resolve(result.result().get("review_packet_json").toString()).toFile());
        assertEquals(expected, packet.path("contractRevision"));
        var markdown = Files.readString(f.root.resolve(result.result().get("review_packet").toString()));
        assertEquals(expected, revisionJson(markdown));
        assertTrue(markdown.contains("claims, not verified closure or approval"));
        assertEquals(before, authorityHashes(f));
    }

    @Test
    void currentContractPacketRejectsGateSourceOrSubjectDrift() throws Exception {
        for (var key : List.of("source_node_instance_id", "subject_input_refs")) {
            var f = new AcceptanceRepairSequenceTest.Fixture(root.resolve(key), "CONTRACT_GAP", "docs/output.txt");
            f.until("acceptance.classify_failure"); f.step();
            f.until("gate.human_requirement_review"); f.approve();
            f.until("gate.contract_review");
            var node = active(f);
            var gateId = f.ledger.loadCurrent(f.paths).openGateIds().getFirst();
            var gatePath = f.paths.workstreamDir().resolve("gates/" + gateId + ".json");
            var gate = (com.fasterxml.jackson.databind.node.ObjectNode) MAPPER.readTree(gatePath.toFile());
            var result = (com.fasterxml.jackson.databind.node.ObjectNode) gate.get("result");
            if (key.equals("subject_input_refs")) result.putArray(key);
            else result.put(key, "forged-source");
            MAPPER.writeValue(gatePath.toFile(), gate);
            assertThrows(IOException.class, () -> new TaskContractRevisionContextResolver(dev.dual.runtime.RuntimeTestLedger.ledger())
                    .proposalForContractGate(f.root, f.ws, node.nodeInstanceId()));
        }
    }

    private static dev.dual.ledger.file.NodeInstanceDocument active(AcceptanceRepairSequenceTest.Fixture f) throws Exception {
        return f.ledger.loadNode(f.paths, f.ledger.loadCurrent(f.paths).activeNodeInstanceIds().getFirst());
    }

    @Test
    void contractPacketRejectsChangedCompileBytesBeforeReplacingEitherPacket() throws Exception {
        for (boolean late : List.of(false, true)) {
            for (var name : List.of("task-contract.json", "contract-resolution.json")) {
                var f = new AcceptanceRepairSequenceTest.Fixture(root.resolve(late + name), "CONTRACT_GAP", "tool.py");
                if (late) { f.until("acceptance.classify_failure"); f.step(); }
                f.until("gate.human_requirement_review"); f.approve();
                f.until("gate.contract_review");
                var node = active(f);
                var packet = f.paths.workstreamDir().resolve("reviews/" + node.nodeInstanceId() + ".json");
                var markdown = f.paths.workstreamDir().resolve("reviews/" + node.nodeInstanceId() + ".md");
                var before = Files.readAllBytes(packet);
                var beforeMarkdown = Files.readAllBytes(markdown);
                var file = f.root.resolve(node.inputRefs().stream().filter(ref -> ref.endsWith("/" + name)).findFirst().orElseThrow());
                var changed = (com.fasterxml.jackson.databind.node.ObjectNode) MAPPER.readTree(file.toFile());
                changed.put(name.equals("task-contract.json") ? "objective" : "summary", "NOT THE COMPILED CANDIDATE");
                MAPPER.writeValue(file.toFile(), changed);
                var instance = new NodeInstance(node.nodeInstanceId(), node.nodeDefinitionId(), node.kind(), node.handlerType(),
                        node.status(), node.stageId(), node.iterationId(), node.inputRefs(), node.outputRefs(), node.result(),
                        node.runRef(), node.startedAt(), node.finishedAt());
                assertThrows(IOException.class, () -> new HumanReviewGateHandler().openOrInspect(new GateHandler.GateContext(f.root,
                f.ws,
                f.ledger.loadDefinition(f.paths).node(node.nodeDefinitionId()),
                instance,
                "completed",
                dev.dual.runtime.RuntimeTestLedger.ledger())));
                assertArrayEquals(before, Files.readAllBytes(packet));
                assertArrayEquals(beforeMarkdown, Files.readAllBytes(markdown));
            }
        }
    }

    private static Map<String, String> authorityHashes(AcceptanceRepairSequenceTest.Fixture f) throws Exception {
        var hashes = new java.util.TreeMap<String, String>();
        try (var files = Files.walk(f.paths.workstreamDir().resolve("artifacts/contracts"))) {
            for (var path : files.filter(Files::isRegularFile).toList())
                hashes.put(f.root.relativize(path).toString(), TaskContractArtifactWriter.sha256(path));
        }
        assertFalse(hashes.isEmpty());
        return hashes;
    }

    private static com.fasterxml.jackson.databind.JsonNode revisionJson(String markdown) throws Exception {
        var section = markdown.indexOf("## Contract Revision Obligations And Candidate Responses");
        assertTrue(section >= 0, markdown);
        var start = markdown.indexOf("```json\n", section) + "```json\n".length();
        return MAPPER.readTree(markdown.substring(start, markdown.indexOf("\n```", start)));
    }

    @Test
    void initialContextAndBothPacketsKeepExistingDisplayWithoutRevisionSection() throws Exception {
        var reviewRef = initialProposal();
        var candidate = AgentEvidenceIndex.validatedDocument(dev.dual.runtime.RuntimeTestLedger.ledger(), root, "ws", reviewRef, MAPPER)
                .orElseThrow().reviewSubject();
        var proposalRef = ".agent/workstreams/ws/artifacts/proposal-reference.json";
        MAPPER.writeValue(root.resolve(proposalRef).toFile(), candidate.toMap());
        var context = context("iter-001", List.of(proposalRef), false);
        var result = new ContextBuildPackageHandler().execute(context);
        assertEquals(NodeStatus.COMPLETED, result.status(), result.result().toString());
        assertFalse(Files.readString(root.resolve(result.outputRefs().getFirst()))
                .contains("Contract Revision Obligations"));
        for (var purpose : List.of("requirement")) {
            var packet = new HumanReviewGateHandler().openOrInspect(gate(purpose, "iter-001", List.of(reviewRef), Map.of()));
            assertEquals(NodeStatus.WAITING, packet.status());
            assertFalse(MAPPER.readTree(root.resolve(packet.result().get("review_packet_json").toString()).toFile())
                    .has("contractRevision"));
        }
        var f = new AcceptanceRepairSequenceTest.Fixture(root.resolve("initial-contract"), "CONTRACT_GAP", "tool.py");
        f.until("gate.human_requirement_review"); f.approve(); f.until("gate.contract_review");
        var node = active(f);
        var packet = MAPPER.readTree(f.paths.workstreamDir().resolve("reviews/" + node.nodeInstanceId() + ".json").toFile());
        assertFalse(packet.has("contractRevision"));
        assertTrue(packet.toString().contains("unapproved_candidate"));
    }

    @Test
    void missingIterationCannotPublishEitherPacket() throws Exception {
        initialProposal();
        for (var purpose : List.of("requirement", "contract")) {
            var failure = assertThrows(IOException.class, () -> new HumanReviewGateHandler()
                    .openOrInspect(gate(purpose, "iter-missing", List.of(), Map.of())));
            assertTrue(failure.getMessage().contains(purpose.equals("contract")
                    ? "GOAL_CONTRACT_NOT_QUALIFIED" : "CONTRACT_REVISION_CONTEXT_INVALID"), failure.toString());
        }
        assertFalse(Files.exists(root.resolve(".agent/workstreams/ws/reviews")));
    }

    @Test
    void missingCandidateIterationFailsBeforeWritingContext() throws Exception {
        var reviewRef = initialProposal();
        var candidate = AgentEvidenceIndex.validatedDocument(dev.dual.runtime.RuntimeTestLedger.ledger(), root, "ws", reviewRef, MAPPER)
                .orElseThrow().reviewSubject();
        var proposalRef = ".agent/workstreams/ws/artifacts/proposal-reference.json";
        MAPPER.writeValue(root.resolve(proposalRef).toFile(), candidate.toMap());
        Files.delete(root.resolve(".agent/workstreams/ws/iterations/iter-001.json"));
        var result = new ContextBuildPackageHandler().execute(context("iter-001", List.of(proposalRef), false));
        assertEquals(NodeStatus.FAILED, result.status());
        assertEquals("CONTRACT_REVISION_CONTEXT_INVALID", result.result().get("failure_code"));
        assertFalse(Files.exists(root.resolve(".agent/workstreams/ws/context")));
    }

    private String initialProposal() throws Exception {
        Files.writeString(root.resolve("README.md"), "Initial requirement");
        Files.createDirectories(root.resolve(".agent/workstreams/ws/artifacts"));
        return ApprovedTaskContractProposalFixture.publish(root, "ws", """
                {"artifact_type":"task_contract_proposal","schema_version":3,"objective":"Review documentation",
                 "scope":{"included":[{"path":"README.md","operation":"MODIFY"}],"excluded":[]},
                 "acceptance_criteria":[{"id":"AC-1","text":"Documentation is correct"}],
                 "test_requirements":[{"id":"TEST-1","text":"Inspect documentation"}],
                 "constraints":[],"non_goals":[],"risk_areas":[],"evidence_refs":["README.md"],
                 "open_questions":[],"revision_responses":[]}
                """);
    }

    private TaskHandler.TaskContext context(String iteration, List<String> refs, boolean governed) {
        var config = new java.util.LinkedHashMap<String, Object>();
        config.put("profile", "product_review");
        config.put("target_node", "intake.codex_product_review");
        config.put("required_input_artifact", "task_contract_proposal");
        if (!governed) config.put("include_sections", List.of());
        return new TaskHandler.TaskContext(root, "ws", new NodeDefinition("ctx.review", NodeKind.TASK, "context.build_package", config), node("ctx.review", NodeKind.TASK, "context.build_package", iteration, refs, Map.of()), dev.dual.runtime.RuntimeTestLedger.ledger());
    }

    private GateHandler.GateContext gate(String purpose, String iteration, List<String> refs, Map<String, Object> result) {
        var id = "gate." + purpose;
        return new GateHandler.GateContext(root,
                "ws",
                new NodeDefinition(id, NodeKind.GATE, "human.review", Map.of("gate_purpose", purpose,
                        "allowed_actions", List.of("approve", "revise", "reject"))),
                node(id, NodeKind.GATE, "human.review", iteration, refs, result),
                "READY_FOR_PRODUCT_REVIEW",
                dev.dual.runtime.RuntimeTestLedger.ledger());
    }

    private static NodeInstance node(String id, NodeKind kind, String handler, String iteration,
                                     List<String> refs, Map<String, Object> result) {
        return new NodeInstance("display-" + id, id, kind, handler, NodeStatus.READY,
                "stage-review", iteration, refs, List.of(), result, null, null, null);
    }
}
