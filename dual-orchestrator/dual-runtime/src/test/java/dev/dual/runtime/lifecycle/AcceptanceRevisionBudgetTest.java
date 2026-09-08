package dev.dual.runtime.lifecycle;

import dev.dual.core.id.DualId;
import dev.dual.core.workflow.EdgeDefinition;
import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeKind;
import dev.dual.core.workflow.NodeStatus;
import dev.dual.core.workflow.TransitionMetadata;
import dev.dual.core.workflow.WorkflowDefinition;
import dev.dual.core.workflow.WorkflowStatus;
import dev.dual.ledger.file.NodeInstanceDocument;
import dev.dual.ledger.file.WorkflowFileLedger;
import dev.dual.ledger.file.WorkstreamPaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class AcceptanceRevisionBudgetTest {
    private static final String CLASSIFIER = "acceptance.classify_failure";
    @TempDir Path root;

    @Test
    void eachTargetStopsBeforeFourthRepairAfterColdReadsForAllDefinitions() throws Exception {
        for (var variant : AcceptanceRepairWorkflowDefinitionTest.RESOURCES) {
            var definition = budgetSlice(variant);
            for (var edge : definition.edgesFrom(CLASSIFIER)) {
                var paths = initialize(definition, variant + "-" + edge.on().toLowerCase());
                for (int round = 0; round < 3; round++) {
                    repairAndReturn(paths, definition, edge.on());
                }
                assertStoppedBeforeNextRepair(paths, definition, edge.on(), 3);
            }
        }
    }

    @Test
    void differentCategoriesSharingATargetShareTheSameBudget() throws Exception {
        var definition = budgetSlice("full");
        var contracts = initialize(definition, "contract-shared");
        repairAndReturn(contracts, definition, "REJECT_CONTRACT_GAP");
        repairAndReturn(contracts, definition, "REJECT_CONTRACT_AMBIGUITY");
        repairAndReturn(contracts, definition, "REJECT_CONTRACT_GAP");
        assertStoppedBeforeNextRepair(contracts, definition, "REJECT_CONTRACT_AMBIGUITY", 3);

        var implementation = initialize(definition, "implementation-shared");
        for (var outcome : List.of("REJECT_IMPLEMENTATION_BUG", "REJECT_CONTEXT_INSUFFICIENT", "REJECT_TOOL_LIMITATION")) {
            repairAndReturn(implementation, definition, outcome);
        }
        assertStoppedBeforeNextRepair(implementation, definition, "REJECT_IMPLEMENTATION_BUG", 3);
    }

    @Test
    void alternatingFourTargetsAllowsTwelveRepairsNotAGlobalThree() throws Exception {
        var definition = budgetSlice("full");
        var paths = initialize(definition, "alternating");
        var outcomes = List.of("REJECT_CONTRACT_GAP", "REJECT_IMPLEMENTATION_BUG",
                "REJECT_VERIFICATION_INSUFFICIENT", "REJECT_REVIEW_NOISE");
        assertEquals(4, definition.edgesFrom(CLASSIFIER).stream().map(EdgeDefinition::to).distinct().count());
        for (int round = 0; round < 3; round++) {
            for (var outcome : outcomes) repairAndReturn(paths, definition, outcome);
        }
        assertStoppedBeforeNextRepair(paths, definition, "REJECT_REVIEW_NOISE", 12);
    }

    @Test
    void separateJvmRunsCannotResetPersistedRevisionBudget() throws Exception {
        var definition = budgetSlice("full");
        var paths = initialize(definition, "cold-jvm");
        for (int round = 0; round < 3; round++) {
            var child = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                    "-cp", System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
                    AcceptanceRevisionBudgetTest.class.getName(), root.toString(), "ws-cold-jvm")
                    .redirectErrorStream(true).start();
            try {
                assertTrue(child.waitFor(30, TimeUnit.SECONDS), "cold ledger run timed out");
                assertEquals(0, child.exitValue(), new String(child.getInputStream().readAllBytes(),
                        java.nio.charset.StandardCharsets.UTF_8));
            } finally {
                if (child.isAlive()) child.destroyForcibly().waitFor();
            }
        }
        assertStoppedBeforeNextRepair(paths, definition, "REJECT_REVIEW_NOISE", 3);
    }

    public static void main(String[] args) throws Exception {
        var paths = new WorkstreamPaths(Path.of(args[0]), new DualId(args[1]));
        var definition = new WorkflowFileLedger().loadDefinition(paths);
        complete(paths, definition, "REJECT_REVIEW_NOISE");
        assertEquals("context.acceptance_codex_verifier_review", active(paths).nodeDefinitionId());
        complete(paths, definition, "completed");
        assertEquals(CLASSIFIER, active(paths).nodeDefinitionId());
    }

    @Test
    void explicitHumanRevisionKeepsItsExistingBudgetExemption() throws Exception {
        var definition = new WorkflowDefinition("human-revision", 1, "work", List.of(
                new NodeDefinition("work", NodeKind.TASK, "noop.task", Map.of()),
                new NodeDefinition("gate.review", NodeKind.GATE, "human.review",
                        Map.of("allowed_actions", List.of("revise"), "max_revisions", 0))), List.of(
                new EdgeDefinition("work", "completed", "gate.review"),
                new EdgeDefinition("gate.review", "revise", "work", null, TransitionMetadata.loop("human revision"))));
        var paths = initialize(definition, "human-revision");
        for (int i = 0; i < 4; i++) {
            complete(paths, definition, "completed");
            new WorkflowFileLedger().resolveOpenGate(paths, definition, "revise", "explicit human revision " + i);
            assertEquals("work", active(paths).nodeDefinitionId());
            assertEquals(WorkflowStatus.RUNNING, new WorkflowFileLedger().loadCurrent(paths).status());
        }
    }

    // Real classifier config and loop edges; repair work is completed explicitly, without providers.
    private WorkflowDefinition budgetSlice(String variant) throws Exception {
        var bundled = AcceptanceRepairWorkflowDefinitionTest.loadResource(root, variant);
        var edges = new ArrayList<>(bundled.edgesFrom(CLASSIFIER));
        var nodes = new ArrayList<NodeDefinition>();
        nodes.add(bundled.node(CLASSIFIER));
        edges.stream().map(EdgeDefinition::to).distinct().forEach(target -> nodes.add(bundled.node(target)));
        for (var target : nodes.stream().map(NodeDefinition::id).filter(id -> !CLASSIFIER.equals(id)).toList()) {
            edges.add(new EdgeDefinition(target, "completed", CLASSIFIER));
        }
        return new WorkflowDefinition("budget-" + variant, 1, CLASSIFIER, nodes, edges);
    }

    private WorkstreamPaths initialize(WorkflowDefinition definition, String id) throws Exception {
        var paths = new WorkstreamPaths(root, new DualId("ws-" + id));
        Files.createDirectories(paths.workstreamDir());
        new WorkflowFileLedger().initialize(paths, "wf-" + id, definition);
        return paths;
    }

    private void repairAndReturn(WorkstreamPaths paths, WorkflowDefinition definition, String outcome) throws Exception {
        assertEquals(CLASSIFIER, active(paths).nodeDefinitionId());
        var target = definition.edgesFrom(CLASSIFIER).stream().filter(edge -> outcome.equals(edge.on()))
                .findFirst().orElseThrow().to();
        complete(paths, definition, outcome);
        var repair = active(paths);
        assertEquals(target, repair.nodeDefinitionId());
        assertEquals(NodeStatus.READY, repair.status());
        assertEquals("loop", repair.inputTransition().get("transition_type"));
        complete(paths, definition, "completed");
        assertEquals(CLASSIFIER, active(paths).nodeDefinitionId());
    }

    private void assertStoppedBeforeNextRepair(WorkstreamPaths paths, WorkflowDefinition definition,
                                               String outcome, int totalRepairs) throws Exception {
        var trigger = active(paths);
        long nodesBefore;
        try (var files = Files.list(paths.nodesDir())) { nodesBefore = files.count(); }
        complete(paths, definition, outcome);
        var ledger = new WorkflowFileLedger();
        var current = ledger.loadCurrent(paths);
        assertEquals(WorkflowStatus.WAITING_FOR_RECOVERY, current.status());
        assertEquals(List.of(trigger.nodeInstanceId()), current.activeNodeInstanceIds());
        var blocked = active(paths);
        assertEquals(NodeStatus.BLOCKED, blocked.status());
        assertEquals("revision_limit_reached", blocked.result().get("reason"));
        assertEquals(3, blocked.result().get("max_revisions"));
        assertEquals(3, blocked.result().get("current_revision_count"));
        assertEquals(List.of("recovery-" + trigger.nodeInstanceId()), current.openGateIds());
        try (var files = Files.list(paths.nodesDir())) { assertEquals(nodesBefore, files.count()); }
        try (var files = Files.list(paths.iterationsDir())) { assertEquals(totalRepairs + 1, files.count()); }
        var events = Files.readString(paths.eventsJsonl());
        assertTrue(events.contains("revision_limit_reached"));
        assertFalse(Files.exists(paths.iterationJson("iter-" + String.format("%03d", totalRepairs + 2))));
    }

    private static NodeInstanceDocument active(WorkstreamPaths paths) throws Exception {
        var ledger = new WorkflowFileLedger();
        var current = ledger.loadCurrent(paths);
        assertEquals(1, current.activeNodeInstanceIds().size());
        return ledger.loadNode(paths, current.activeNodeInstanceIds().getFirst());
    }

    private static void complete(WorkstreamPaths paths, WorkflowDefinition definition, String outcome) throws Exception {
        var node = active(paths);
        new WorkflowFileLedger().completeAndRoute(paths, definition, node.nodeInstanceId(), NodeStatus.COMPLETED,
                outcome, List.of("evidence-" + node.nodeInstanceId()), Map.of("decision", outcome));
    }
}
