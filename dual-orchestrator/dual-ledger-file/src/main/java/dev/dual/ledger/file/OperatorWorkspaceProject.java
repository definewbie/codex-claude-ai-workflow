package dev.dual.ledger.file;

import java.time.Instant;
import java.util.List;

public record OperatorWorkspaceProject(
        String projectId,
        String name,
        String repoRoot,
        String agentRunsDir,
        Instant lastOpenedAt,
        List<OperatorWorkspaceWorkstream> workstreams
) {
    OperatorWorkspaceProject normalized() {
        return new OperatorWorkspaceProject(
                blank(projectId),
                blank(name),
                blank(repoRoot),
                blank(agentRunsDir),
                lastOpenedAt,
                workstreams == null ? List.of() : workstreams.stream()
                        .map(OperatorWorkspaceWorkstream::normalized)
                        .toList()
        );
    }

    private static String blank(String value) {
        return value == null ? "" : value;
    }
}
