package dev.dual.cli;

import dev.dual.adapter.acpx.AgentRunnerKind;
import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.WorkflowDefinition;
import dev.dual.runtime.lifecycle.WorkflowDefinitionLoader;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Guards against silent runner-kind drift: if a bundled workflow's first agent
 * node or its permission_profile changes, this test forces the change to be
 * acknowledged (the asserted kind updates) instead of silently changing the
 * preflight probe path.
 */
class InspectRunnerKindConsistencyTest {

    @Test
    void fullVariantFirstAgentRunIsAcpxClaudeSession() throws Exception {
        var node = firstAgentRun(new WorkflowDefinitionLoader().loadOrDefault(null, "full"));
        assertEquals("intake.claude_brainstorm", node.id());
        assertEquals(AgentRunnerKind.ACPX_CLAUDE_SESSION,
                AgentRunnerKind.from(text(node, "agent"), text(node, "permission_profile")));
    }

    @Test
    void contractVariantFirstAgentRunIsAcpxClaudeSession() throws Exception {
        var node = firstAgentRun(new WorkflowDefinitionLoader().loadOrDefault(null, "contract"));
        assertEquals("intake.claude_brainstorm", node.id());
        assertEquals(AgentRunnerKind.ACPX_CLAUDE_SESSION,
                AgentRunnerKind.from(text(node, "agent"), text(node, "permission_profile")));
    }

    private static NodeDefinition firstAgentRun(WorkflowDefinition definition) {
        for (var node : definition.nodes()) {
            if ("acpx.agent_run".equals(node.handlerType())) {
                return node;
            }
        }
        return fail("bundled workflow must have at least one acpx.agent_run node");
    }

    private static String text(NodeDefinition node, String key) {
        var value = node.config().get(key);
        return value == null ? "" : String.valueOf(value);
    }
}
