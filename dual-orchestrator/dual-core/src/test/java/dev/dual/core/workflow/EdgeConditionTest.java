package dev.dual.core.workflow;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EdgeConditionTest {

    @Test
    void unconditional_alwaysTrue() {
        var cond = EdgeCondition.unconditional();
        assertTrue(cond.evaluate(Map.of()));
        assertTrue(cond.evaluate(null));
        assertTrue(cond.evaluate(Map.of("x", "y")));
    }

    @Test
    void configEquals_matchesWhenValueEquals() {
        var cond = EdgeCondition.configEquals("mode", "strict");
        assertTrue(cond.evaluate(Map.of("mode", "strict")));
    }

    @Test
    void configEquals_noMatchWhenValueDiffers() {
        var cond = EdgeCondition.configEquals("mode", "strict");
        assertFalse(cond.evaluate(Map.of("mode", "relaxed")));
    }

    @Test
    void configEquals_noMatchWhenKeyMissing() {
        var cond = EdgeCondition.configEquals("mode", "strict");
        assertFalse(cond.evaluate(Map.of()));
    }

    @Test
    void configEquals_convertsNonStringValue() {
        var cond = EdgeCondition.configEquals("count", "42");
        assertTrue(cond.evaluate(Map.of("count", 42)));
    }

    @Test
    void configEquals_nullContextReturnsFalse() {
        var cond = EdgeCondition.configEquals("key", "val");
        assertFalse(cond.evaluate(null));
    }
}
