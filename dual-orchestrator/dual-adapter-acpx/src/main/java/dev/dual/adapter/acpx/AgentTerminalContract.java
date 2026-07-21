package dev.dual.adapter.acpx;

import dev.dual.core.agentoutput.DualControlProtocolParser;
import dev.dual.core.agentoutput.DualControlTerminalPolicy;

final class AgentTerminalContract {
    private final DualControlProtocolParser parser = new DualControlProtocolParser();
    private final DualControlTerminalPolicy terminalPolicy = new DualControlTerminalPolicy();

    boolean isComplete(String stdout) {
        var parsed = parser.parse(stdout);
        return parsed.valid()
                && !parsed.block().values().getOrDefault("decision", "").isBlank()
                && !parsed.block().values().getOrDefault("summary", "").isBlank()
                && terminalPolicy.isComplete(parsed);
    }
}
