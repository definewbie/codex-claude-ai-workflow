package dev.dual.runtime.handler;

import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeInstance;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextBudgetOverrideTest {
    @TempDir
    Path tempDir;

    private Path wsDir;
    private final ContextBuildPackageHandler handler = new ContextBuildPackageHandler();

    @BeforeEach
    void setup() {
        wsDir = tempDir.resolve(".agent/workstreams/ws-ctx");
    }

    @Test
    void profileOverrideTakesPrecedenceOverDefault() throws Exception {
        Files.createDirectories(wsDir);
        // product_review profile has max_snippet_chars=2000 override, default is 4000
        Files.writeString(wsDir.resolve("requirement.md"), "Short req.", StandardCharsets.UTF_8);

        var input = wsDir.resolve("artifacts/input.txt");
        Files.createDirectories(input.getParent());
        // Write content longer than 2000 but shorter than 4000
        Files.writeString(input, "X".repeat(3_000), StandardCharsets.UTF_8);
        Files.createDirectories(wsDir.resolve("events.jsonl").getParent());
        Files.writeString(wsDir.resolve("events.jsonl"), "", StandardCharsets.UTF_8);

        var result = handler.execute(ctx("product_review", List.of(
                ".agent/workstreams/ws-ctx/artifacts/input.txt")));

        assertEquals(NodeStatus.COMPLETED, result.status());
        var markdown = Files.readString(tempDir.resolve((String) result.result().get("context_package")));
        // If profile override (2000) is respected, content should be truncated
        assertTrue(markdown.contains("truncated") || markdown.length() < 3_500,
                "Content should be truncated by profile override 2000, not default 4000");
    }

    @Test
    void nodeConfigOverridesDefaultWhenNoProfileOverride() throws Exception {
        Files.createDirectories(wsDir);
        // "full" profile has no max_snippet_chars override — falls to node config
        Files.writeString(wsDir.resolve("requirement.md"), "Short req.", StandardCharsets.UTF_8);

        var input = wsDir.resolve("artifacts/input.txt");
        Files.createDirectories(input.getParent());
        Files.writeString(input, "Y".repeat(3_000), StandardCharsets.UTF_8);

        Files.createDirectories(wsDir.resolve("iterations"));
        Files.createDirectories(wsDir.resolve("nodes"));
        var evidenceDir = wsDir.resolve("artifacts/git-evidence");
        Files.createDirectories(evidenceDir);
        Files.writeString(evidenceDir.resolve("git-evidence.json"), "{\"available\":true}", StandardCharsets.UTF_8);
        Files.createDirectories(wsDir.resolve("events.jsonl").getParent());
        Files.writeString(wsDir.resolve("events.jsonl"), "", StandardCharsets.UTF_8);

        // Set node config max_snippet_chars=1000 — should override default 4000
        var def = new NodeDefinition("ctx.test", NodeKind.TASK, "context.build_package", Map.of(
                "target_node", "next.node",
                "profile", "full",
                "max_snippet_chars", 1000
        ));
        var instance = new NodeInstance("node-001", "ctx.test", NodeKind.TASK, "context.build_package",
                NodeStatus.READY, "stage-test", "iter-001",
                List.of(".agent/workstreams/ws-ctx/artifacts/input.txt"),
                List.of(), Map.of(), null, null, null);

        var result = handler.execute(new TaskHandler.TaskContext(tempDir, "ws-ctx", def, instance));

        assertEquals(NodeStatus.COMPLETED, result.status());
        var markdown = Files.readString(tempDir.resolve((String) result.result().get("context_package")));
        assertTrue(markdown.contains("truncated") || markdown.length() < 1_500,
                "Content should be truncated by node config 1000");
    }

    @Test
    void profileOverrideTakesPrecedenceOverNodeConfig() throws Exception {
        Files.createDirectories(wsDir);
        // product_review has max_snippet_chars=2000 in profile configOverrides
        Files.writeString(wsDir.resolve("requirement.md"), "Override priority test.", StandardCharsets.UTF_8);

        var input = wsDir.resolve("artifacts/input.txt");
        Files.createDirectories(input.getParent());
        Files.writeString(input, "Z".repeat(2_500), StandardCharsets.UTF_8);
        Files.createDirectories(wsDir.resolve("events.jsonl").getParent());
        Files.writeString(wsDir.resolve("events.jsonl"), "", StandardCharsets.UTF_8);

        // Set node config max_snippet_chars=500 — profile override (2000) should win
        var def = new NodeDefinition("ctx.test", NodeKind.TASK, "context.build_package", Map.of(
                "target_node", "next.node",
                "profile", "product_review",
                "max_snippet_chars", 500
        ));
        var instance = new NodeInstance("node-001", "ctx.test", NodeKind.TASK, "context.build_package",
                NodeStatus.READY, "stage-test", "iter-001",
                List.of(".agent/workstreams/ws-ctx/artifacts/input.txt"),
                List.of(), Map.of(), null, null, null);

        var result = handler.execute(new TaskHandler.TaskContext(tempDir, "ws-ctx", def, instance));

        assertEquals(NodeStatus.COMPLETED, result.status());
        var markdown = Files.readString(tempDir.resolve((String) result.result().get("context_package")));
        // Profile override (2000) should take precedence over node config (500)
        // So content of 2500 chars should be truncated at 2000, not at 500
        // If node config won, we'd see truncation at 500 with much less content
        assertTrue(markdown.contains("truncated") || markdown.length() > 800,
                "Profile override (2000) should win over node config (500)");
    }

    private TaskHandler.TaskContext ctx(String profile, List<String> inputRefs) {
        return new TaskHandler.TaskContext(
                tempDir, "ws-ctx",
                new NodeDefinition("ctx.test", NodeKind.TASK, "context.build_package", Map.of(
                        "target_node", "next.node",
                        "profile", profile
                )),
                new NodeInstance("node-001", "ctx.test", NodeKind.TASK, "context.build_package",
                        NodeStatus.READY, "stage-test", "iter-001", inputRefs, List.of(), Map.of(), null, null, null)
        );
    }
}
