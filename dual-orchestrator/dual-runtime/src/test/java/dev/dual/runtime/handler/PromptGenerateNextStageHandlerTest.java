package dev.dual.runtime.handler;

import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeInstance;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the next-stage handoff is produced from the typed {@code stage_handoff}
 * artifact (via the agent_output_contract sidecar), never from raw agent prose.
 */
class PromptGenerateNextStageHandlerTest {
    @TempDir
    Path tempDir;

    @Test
    void handoffJsonComesFromTypedStageHandoffArtifactNotMarkdown() throws Exception {
        var wsDir = tempDir.resolve(".agent/workstreams/ws-handoff");
        var outputDir = wsDir.resolve("runs/run-1/outputs");
        Files.createDirectories(outputDir);
        Files.writeString(outputDir.resolve("handoff.json"), "{\"untrusted\":true}", StandardCharsets.UTF_8);
        // Typed stage_handoff sidecar — the source of truth.
        Files.writeString(outputDir.resolve("handoff.contract.json"), """
                {
                  "artifact_type": "agent_output_contract",
                  "source_artifact_type": "stage_handoff",
                  "decision": "HANDOFF_READY",
                  "summary": "Stage handoff ready.",
                  "completed_work": ["implemented feature X"],
                  "important_decisions": ["chose approach A"],
                  "changed_files": ["src/feature.js"],
                  "open_risks": ["edge case Y untested"],
                  "verification_summary": "self-test passed",
                  "recommended_next_stage": "delivery",
                  "suggested_next_prompt": "Prepare delivery."
                }
                """, StandardCharsets.UTF_8);
        addLineage(outputDir.resolve("handoff.contract.json"), outputDir.resolve("handoff.json"),
                "stage.claude_handoff");

        var inputRef = ".agent/workstreams/ws-handoff/runs/run-1/outputs/handoff.json";
        var handler = new PromptGenerateNextStageHandler();
        var definition = new NodeDefinition("prompt.generate_next_stage", NodeKind.TASK,
                "prompt.generate_next_stage", Map.of());
        var instance = new NodeInstance("node-1", "prompt.generate_next_stage", NodeKind.TASK,
                "prompt.generate_next_stage", NodeStatus.READY, "stage-handoff", "iter-1",
                List.of(inputRef), List.of(), Map.of(), null, null, null);

        var result = handler.execute(new TaskHandler.TaskContext(tempDir, "ws-handoff", definition, instance));

        assertEquals("completed", result.outcome());
        assertEquals("structured-handoff", result.result().get("mode"),
                "the typed stage_handoff sidecar must be the main path, not the Markdown fallback");

        var handoffJson = Files.readString(tempDir.resolve(".agent/workstreams/ws-handoff/artifacts/next-stage/handoff.json"));
        // Typed fields come from the sidecar...
        assertTrue(handoffJson.contains("implemented feature X"), handoffJson);
        assertTrue(handoffJson.contains("src/feature.js"), handoffJson);
        assertTrue(handoffJson.contains("chose approach A"), handoffJson);
        // ...and the localized Markdown prose does NOT leak into the structured handoff.
        assertFalse(handoffJson.contains("untrusted"), handoffJson);
    }

    @Test
    void handoffVerificationStateUsesLatestAcceptanceEvidence() throws Exception {
        var wsDir = tempDir.resolve(".agent/workstreams/ws-handoff");
        var handoffOutputDir = wsDir.resolve("runs/run-3/outputs");
        var selfTestOutputDir = wsDir.resolve("runs/run-2/outputs");
        var acceptanceOutputDir = wsDir.resolve("runs/run-4/outputs");
        var unrelatedOutputDir = wsDir.resolve("runs/run-9/outputs");
        Files.createDirectories(handoffOutputDir);
        Files.createDirectories(selfTestOutputDir);
        Files.createDirectories(acceptanceOutputDir);
        Files.createDirectories(unrelatedOutputDir);
        Files.writeString(handoffOutputDir.resolve("handoff.md"), "# Handoff\n", StandardCharsets.UTF_8);
        Files.writeString(handoffOutputDir.resolve("handoff.contract.json"), """
                {
                  "artifact_type": "agent_output_contract",
                  "source_artifact_type": "stage_handoff",
                  "decision": "HANDOFF_READY",
                  "summary": "Stage handoff ready.",
                  "completed_work": ["implemented feature X"],
                  "changed_files": ["src/feature.js"],
                  "verification_summary": "DEFERRED: build/typecheck still pending.",
                  "recommended_next_stage": "delivery",
                  "suggested_next_prompt": "Prepare delivery."
                }
                """, StandardCharsets.UTF_8);
        Files.writeString(selfTestOutputDir.resolve("implement-claude-self-test.contract.json"), """
                {
                  "artifact_type": "agent_output_contract",
                  "decision": "SELF_TEST_COMPLETE",
                  "summary": "npm run build passed.",
                  "verification_summary": "npm ci exit 0; npm run build:mp-weixin exit 0."
                }
                """, StandardCharsets.UTF_8);
        Files.writeString(acceptanceOutputDir.resolve("acceptance-codex-review.contract.json"), """
                {
                  "artifact_type": "agent_output_contract",
                  "decision": "ACCEPT_WITH_NOTES",
                  "summary": "Build evidence accepted.",
                  "verification_summary": "Blocking build/typecheck gap has been closed."
                }
                """, StandardCharsets.UTF_8);
        Files.writeString(unrelatedOutputDir.resolve("acceptance-codex-review.contract.json"), """
                {
                  "artifact_type": "agent_output_contract",
                  "decision": "ACCEPT_WITH_NOTES",
                  "summary": "Unrouted later run.",
                  "verification_summary": "This unrelated later run must not be selected."
                }
                """, StandardCharsets.UTF_8);
        addLineage(handoffOutputDir.resolve("handoff.contract.json"), handoffOutputDir.resolve("handoff.md"),
                "stage.claude_handoff");
        addLineage(selfTestOutputDir.resolve("implement-claude-self-test.contract.json"),
                selfTestOutputDir.resolve("implement-claude-self-test.json"), "implement.claude_self_test_report");
        addLineage(acceptanceOutputDir.resolve("acceptance-codex-review.contract.json"),
                acceptanceOutputDir.resolve("acceptance-codex-review.json"), "acceptance.codex_verifier_review");
        addLineage(unrelatedOutputDir.resolve("acceptance-codex-review.contract.json"),
                unrelatedOutputDir.resolve("acceptance-codex-review.json"), "acceptance.codex_verifier_review");

        var inputRef = ".agent/workstreams/ws-handoff/runs/run-3/outputs/handoff.md";
        var selfTestRef = ".agent/workstreams/ws-handoff/runs/run-2/outputs/implement-claude-self-test.contract.json";
        var acceptanceRef = ".agent/workstreams/ws-handoff/runs/run-4/outputs/acceptance-codex-review.contract.json";
        var handler = new PromptGenerateNextStageHandler();
        var definition = new NodeDefinition("prompt.generate_next_stage", NodeKind.TASK,
                "prompt.generate_next_stage", Map.of());
        var instance = new NodeInstance("node-1", "prompt.generate_next_stage", NodeKind.TASK,
                "prompt.generate_next_stage", NodeStatus.READY, "stage-handoff", "iter-1",
                List.of(selfTestRef, acceptanceRef, inputRef), List.of(), Map.of(), null, null, null);

        handler.execute(new TaskHandler.TaskContext(tempDir, "ws-handoff", definition, instance));

        var handoffJson = Files.readString(tempDir.resolve(".agent/workstreams/ws-handoff/artifacts/next-stage/handoff.json"));
        assertTrue(handoffJson.contains("Acceptance (ACCEPT_WITH_NOTES): Blocking build/typecheck gap has been closed."), handoffJson);
        assertTrue(handoffJson.contains("Self-test: npm ci exit 0; npm run build:mp-weixin exit 0."), handoffJson);
        assertFalse(handoffJson.contains("DEFERRED: build/typecheck still pending."), handoffJson);
        assertFalse(handoffJson.contains("This unrelated later run must not be selected."), handoffJson);
    }

    @Test
    void handoffVerificationStatePreservesNegativeAcceptanceEvidence() throws Exception {
        var wsDir = tempDir.resolve(".agent/workstreams/ws-handoff");
        var handoffOutputDir = wsDir.resolve("runs/run-3/outputs");
        var acceptanceOutputDir = wsDir.resolve("runs/run-4/outputs");
        Files.createDirectories(handoffOutputDir);
        Files.createDirectories(acceptanceOutputDir);
        Files.writeString(handoffOutputDir.resolve("handoff.md"), "# Handoff\n", StandardCharsets.UTF_8);
        Files.writeString(handoffOutputDir.resolve("handoff.contract.json"), """
                {
                  "artifact_type": "agent_output_contract",
                  "source_artifact_type": "stage_handoff",
                  "decision": "HANDOFF_READY",
                  "summary": "Stage handoff ready.",
                  "completed_work": ["implemented feature X"],
                  "changed_files": ["src/feature.js"],
                  "verification_summary": "stale handoff verification",
                  "recommended_next_stage": "delivery",
                  "suggested_next_prompt": "Prepare delivery."
                }
                """, StandardCharsets.UTF_8);
        Files.writeString(acceptanceOutputDir.resolve("acceptance-codex-review.contract.json"), """
                {
                  "artifact_type": "agent_output_contract",
                  "decision": "REJECT",
                  "summary": "Build evidence missing.",
                  "verification_summary": "Required build/typecheck evidence is still missing."
                }
                """, StandardCharsets.UTF_8);
        addLineage(handoffOutputDir.resolve("handoff.contract.json"), handoffOutputDir.resolve("handoff.md"),
                "stage.claude_handoff");
        addLineage(acceptanceOutputDir.resolve("acceptance-codex-review.contract.json"),
                acceptanceOutputDir.resolve("acceptance-codex-review.json"), "acceptance.codex_verifier_review");

        var inputRef = ".agent/workstreams/ws-handoff/runs/run-3/outputs/handoff.md";
        var acceptanceRef = ".agent/workstreams/ws-handoff/runs/run-4/outputs/acceptance-codex-review.contract.json";
        var handler = new PromptGenerateNextStageHandler();
        var definition = new NodeDefinition("prompt.generate_next_stage", NodeKind.TASK,
                "prompt.generate_next_stage", Map.of());
        var instance = new NodeInstance("node-1", "prompt.generate_next_stage", NodeKind.TASK,
                "prompt.generate_next_stage", NodeStatus.READY, "stage-handoff", "iter-1",
                List.of(acceptanceRef, inputRef), List.of(), Map.of(), null, null, null);

        handler.execute(new TaskHandler.TaskContext(tempDir, "ws-handoff", definition, instance));

        var handoffJson = Files.readString(tempDir.resolve(".agent/workstreams/ws-handoff/artifacts/next-stage/handoff.json"));
        assertTrue(handoffJson.contains("Acceptance (REJECT): Required build/typecheck evidence is still missing."), handoffJson);
        assertFalse(handoffJson.contains("stale handoff verification"), handoffJson);
    }

    private void addLineage(Path sidecar, Path source, String nodeId) throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var object = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(sidecar.toFile());
        object.put("workstream_id", "ws-handoff");
        object.put("run_id", runId(source));
        object.put("node_id", nodeId);
        object.put("node_instance_id", "node-" + nodeId.replace('.', '-') + "-" + runId(source));
        object.put("source_output_ref", tempDir.relativize(source).toString());
        mapper.writerWithDefaultPrettyPrinter().writeValue(sidecar.toFile(), object);
        AgentOutputLineageFixture.publish(tempDir, "ws-handoff", sidecar);
    }

    private static String runId(Path path) {
        for (int index = 0; index + 1 < path.getNameCount(); index++) {
            if ("runs".equals(path.getName(index).toString())) {
                return path.getName(index + 1).toString();
            }
        }
        throw new IllegalArgumentException("fixture path has no run id: " + path);
    }
}
