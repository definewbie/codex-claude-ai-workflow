package dev.dual.core.workflow;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EdgeDefinitionTest {

    @Test
    void threeArgConstructor_hasUnconditionalCondition() {
        var edge = new EdgeDefinition("a", "PASS", "b");
        assertTrue(edge.condition().evaluate(Map.of()));
        assertTrue(edge.condition() == EdgeCondition.unconditional());
    }

    @Test
    void fourArgConstructor_preservesCondition() {
        var cond = EdgeCondition.configEquals("x", "y");
        var edge = new EdgeDefinition("a", "PASS", "b", cond);
        assertEquals(cond, edge.condition());
    }

    @Test
    void nullCondition_becomesUnconditional() {
        var edge = new EdgeDefinition("a", "PASS", "b", null);
        assertTrue(edge.condition().evaluate(Map.of()));
    }

    @Test
    void rejectsBlankFrom() {
        assertThrows(IllegalArgumentException.class, () -> new EdgeDefinition("", "PASS", "b"));
    }

    @Test
    void rejectsBlankOn() {
        assertThrows(IllegalArgumentException.class, () -> new EdgeDefinition("a", "", "b"));
    }

    @Test
    void rejectsBlankTo() {
        assertThrows(IllegalArgumentException.class, () -> new EdgeDefinition("a", "PASS", ""));
    }

    @Test
    void workflowDefinition_conditionalEdgeRouting() {
        var def = new WorkflowDefinition("test", 1, "a",
                List.of(
                        new NodeDefinition("a", NodeKind.DECISION, "dec", Map.of()),
                        new NodeDefinition("b", NodeKind.TASK, "t1", Map.of()),
                        new NodeDefinition("c", NodeKind.TASK, "t2", Map.of())
                ),
                List.of(
                        new EdgeDefinition("a", "PASS", "b", EdgeCondition.configEquals("mode", "strict")),
                        new EdgeDefinition("a", "PASS", "c", EdgeCondition.configEquals("mode", "relaxed"))
                )
        );

        var strict = def.edgesFrom("a", "PASS", Map.of("mode", "strict"));
        assertEquals(1, strict.size());
        assertEquals("b", strict.get(0).to());

        var relaxed = def.edgesFrom("a", "PASS", Map.of("mode", "relaxed"));
        assertEquals(1, relaxed.size());
        assertEquals("c", relaxed.get(0).to());

        var none = def.edgesFrom("a", "PASS", Map.of("mode", "unknown"));
        assertTrue(none.isEmpty());
    }
}
