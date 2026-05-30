package dev.dual.runtime.lifecycle;

import dev.dual.core.workflow.WorkflowDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorkflowVariantSelectorTest {

    private final LifecycleWorkflowDefinitionFactory factory = new LifecycleWorkflowDefinitionFactory();

    @Test
    void forVariant_null_returnsThinMainline() {
        WorkflowDefinition wf = factory.forVariant(null);
        assertEquals("agent-lifecycle", wf.workflowId());
        assertEquals(1, wf.version());
    }

    @Test
    void forVariant_blank_returnsThinMainline() {
        assertEquals("agent-lifecycle", factory.forVariant("").workflowId());
        assertEquals("agent-lifecycle", factory.forVariant("   ").workflowId());
    }

    @Test
    void forVariant_thin_returnsThinMainline() {
        WorkflowDefinition wf = factory.forVariant("thin");
        assertEquals("agent-lifecycle", wf.workflowId());
    }

    @Test
    void forVariant_p5_returnsP5Mainline() {
        WorkflowDefinition wf = factory.forVariant("p5");
        assertEquals("agent-lifecycle-p5", wf.workflowId());
        assertEquals(2, wf.version());
    }

    @Test
    void forVariant_p6_returnsP6Mainline() {
        WorkflowDefinition wf = factory.forVariant("p6");
        assertEquals("agent-lifecycle-p6", wf.workflowId());
        assertEquals(3, wf.version());
    }

    @Test
    void forVariant_normalizesCaseAndWhitespace() {
        assertEquals("agent-lifecycle-p6", factory.forVariant("  P6  ").workflowId());
        assertEquals("agent-lifecycle-p5", factory.forVariant("P5").workflowId());
        assertEquals("agent-lifecycle", factory.forVariant(" THIN ").workflowId());
    }

    @Test
    void forVariant_unknown_throws() {
        var ex = assertThrows(IllegalArgumentException.class, () -> factory.forVariant("p7"));
        assertTrue(ex.getMessage().contains("unknown workflow variant"));
        assertTrue(ex.getMessage().contains("p7"));
    }
}
