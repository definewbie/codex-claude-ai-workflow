package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dual.runtime.lifecycle.WorkflowDefinitionLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ReadinessRoutingDefinitionTest {
    @TempDir Path temporary;
    private static final List<Path> DEFINITIONS = List.of(
            Path.of("dual-runtime/src/main/resources/dev/dual/runtime/lifecycle/agent-lifecycle.full.json"),
            Path.of("dual-runtime/src/main/resources/dev/dual/runtime/lifecycle/agent-lifecycle.contract.json"),
            Path.of("dual-runtime/src/main/resources/dev/dual/runtime/lifecycle/agent-lifecycle.terminal-delivery.json"),
            Path.of("dual-runtime/src/test/resources/dev/dual/runtime/lifecycle/agent-lifecycle.small-plan-human-review-experiment.json"));
    private Path path(Path p) { return Files.exists(p) ? p : Path.of("..").resolve(p); }

    @Test void completePrerequisitesAdvanceWhileMissingPrerequisitesReachExistingHumanGateInEveryVariant() throws Exception {
        var loader = new WorkflowDefinitionLoader();
        for (var file : DEFINITIONS) {
            var definition = loader.load(path(file));
            var ready = definition.edgesFrom("plan.decision").stream().filter(e -> e.on().equals("READY_TO_IMPLEMENT")).toList();
            assertEquals(1, ready.size());
            assertEquals("contract.compile_execution", ready.getFirst().to());
            var missing = definition.edgesFrom("plan.decision").stream().filter(e -> e.on().equals(VerificationReadiness.REQUIRED)).toList();
            assertEquals(1, missing.size());
            assertEquals("plan", definition.node(missing.getFirst().to()).config().get("gate_purpose"));
            for (var decision : List.of("design.decision", "plan.decision"))
                assertTrue(definition.edgesFrom(decision).stream().filter(e -> e.on().startsWith("READY_"))
                        .noneMatch(e -> definition.node(e.to()).kind() == dev.dual.core.workflow.NodeKind.GATE));
        }
        assertFalse(ContractRegistry.forNode("plan.codex_plan_review").allowedDecisions().contains(VerificationReadiness.REQUIRED),
                "a model cannot emit the program-owned readiness outcome");
    }

    @Test void removingMissingPrerequisiteExitIsRejectedInsteadOfLeavingADeclaredDeadEnd() throws Exception {
        var json = new ObjectMapper();
        for (var file : DEFINITIONS) {
            var tree = json.readTree(path(file).toFile());
            var edges = (com.fasterxml.jackson.databind.node.ArrayNode) tree.path("edges");
            for (int i = edges.size() - 1; i >= 0; i--)
                if (VerificationReadiness.REQUIRED.equals(edges.get(i).path("on").asText())) edges.remove(i);
            var changed = temporary.resolve(file.getFileName()); json.writeValue(changed.toFile(), tree);
            assertThrows(java.io.IOException.class, () -> new WorkflowDefinitionLoader().load(changed));
        }
    }
}
