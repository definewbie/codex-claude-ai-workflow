package dev.dual.cli;

import dev.dual.adapter.acpx.AgentRunnerKind;
import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.WorkflowDefinition;
import dev.dual.runtime.lifecycle.WorkflowDefinitionLoader;

import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

/**
 * Read-only inspection of the actual runner path a bundled workflow variant uses
 * for its first agent node. The runner kind (native vs acpx session) is derived
 * by {@link AgentRunnerKind} from the node's agent + permission_profile; this
 * command only loads the workflow definition and prints the result so a
 * preflight probe can match the actual runner instead of assuming one.
 */
@Component
@Command(
        name = "runner-kind",
        description = "Resolve the first agent node's actual runner kind (acpx_claude_session / acpx_codex_session) for a bundled workflow variant.",
        mixinStandardHelpOptions = true
)
public final class InspectRunnerKindCommand implements Callable<Integer> {

    @Option(names = "--variant", description = "Workflow variant: full, contract, or terminal-delivery. Default: full.")
    String variant = "full";

    @Override
    public Integer call() throws Exception {
        WorkflowDefinition definition = new WorkflowDefinitionLoader().loadOrDefault(null, variant);
        for (NodeDefinition node : definition.nodes()) {
            if (!"acpx.agent_run".equals(node.handlerType())) {
                continue;
            }
            var agent = text(node.config().get("agent"));
            var profile = text(node.config().get("permission_profile"));
            var kind = AgentRunnerKind.from(agent, profile);
            System.out.println("runner-kind: " + kind.wire());
            System.out.println("node: " + node.id());
            System.out.println("agent: " + agent);
            System.out.println("permission_profile: " + profile);
            return kind == AgentRunnerKind.UNKNOWN ? 1 : 0;
        }
        System.out.println("runner-kind: " + AgentRunnerKind.UNKNOWN.wire());
        System.err.println("no acpx.agent_run node found in variant: " + variant);
        return 1;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
