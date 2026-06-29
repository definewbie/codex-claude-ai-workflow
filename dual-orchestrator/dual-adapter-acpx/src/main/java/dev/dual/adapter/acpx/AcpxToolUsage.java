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
        List<String> toolIds,
        List<String> readFiles,
        List<String> directReadFiles,
        List<String> writeFiles,
        List<String> bashCommands,
        List<String> commandExecutables
) {
    AcpxToolUsage(
            int schemaVersion,
            int toolCallCount,
            int readCount,
            int writeCount,
            int bashCount,
            int otherCount,
            Map<String, Integer> byTool,
            List<String> toolIds,
            List<String> readFiles,
            List<String> writeFiles,
            List<String> bashCommands,
            List<String> commandExecutables
    ) {
        this(
                schemaVersion,
                toolCallCount,
                readCount,
                writeCount,
                bashCount,
                otherCount,
                byTool,
                toolIds,
                readFiles,
                readFiles,
                writeFiles,
                bashCommands,
                commandExecutables
        );
    }

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
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
    }
}
