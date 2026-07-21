package dev.dual.runtime.handler;

import dev.dual.core.agentoutput.DualControlProtocolParser;

import java.util.Map;

/** Runtime contract facade over the shared dual-control protocol parser. */
public final class DualControlBlockParser {
    private final DualControlProtocolParser protocolParser = new DualControlProtocolParser();

    public DualControlParseResult parse(String markdown, NodeOutputContract contract) {
        var result = parse(markdown);
        if (!result.valid()) {
            return result;
        }
        try {
            contract.validate(result.block());
        } catch (IllegalArgumentException e) {
            return DualControlParseResult.invalid(
                    "CONTRACT_VIOLATION",
                    e.getMessage(),
                    Map.of("node_id", contract.nodeId())
            );
        }
        return result;
    }

    public DualControlParseResult parse(String markdown) {
        var parsed = protocolParser.parse(markdown);
        if (!parsed.valid()) {
            return DualControlParseResult.invalid(parsed.errorCode(), parsed.message(), parsed.details());
        }
        return DualControlParseResult.valid(new DualControlBlock(parsed.block().values()), parsed.details());
    }
}
