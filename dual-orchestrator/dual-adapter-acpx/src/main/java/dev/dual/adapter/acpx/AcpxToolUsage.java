package dev.dual.adapter.acpx;

import java.util.List;
import java.util.Map;

record AcpxToolUsage(
        int schemaVersion,
        int toolCallCount,
        int readCount,
        int writeCount,
        int bashCount,
        int otherCount,
        Map<String, Integer> byTool,
        List<String> readFiles,
        List<String> writeFiles,
        List<String> bashCommands
) {
    static AcpxToolUsage empty() {
        return new AcpxToolUsage(
                1,
                0,
                0,
                0,
                0,
                0,
                Map.of(),
                List.of(),
                List.of(),
                List.of()
        );
    }
}
