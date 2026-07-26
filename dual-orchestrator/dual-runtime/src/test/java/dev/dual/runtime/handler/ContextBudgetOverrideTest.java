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
    void productReviewProfileDeclaresSnippetOverride() {
        assertEquals(
                2_000,
                ContextProfiles.budgetFor("product_review").configOverrides().get("max_snippet_chars")
        );
    }

    @Test
    void nodeConfigOverridesDefaultWhenNoProfileOverride() throws Exception {
        Files.createDirectories(wsDir);
        // "full" profile has no max_snippet_chars override — falls to node config
        Files.writeString(wsDir.resolve("requirement.md"), "Short req.", StandardCharsets.UTF_8);

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
                "include_sections", List.of("requirement", "git_evidence"),
                "max_snippet_chars", 1000
        ));
        var instance = new NodeInstance("node-001", "ctx.test", NodeKind.TASK, "context.build_package",
                NodeStatus.READY, "stage-test", "iter-001",
                List.of(),
                List.of(), Map.of(), null, null, null);

        var result = handler.execute(new TaskHandler.TaskContext(tempDir, "ws-ctx", def, instance));

        assertEquals(NodeStatus.COMPLETED, result.status());
        var markdown = Files.readString(tempDir.resolve((String) result.result().get("context_package")));
        assertTrue(markdown.contains("Max Snippet Chars: 1000"),
                "full profile should use the node max_snippet_chars override");
    }

}
