package dev.dual.ledger.file;

import java.time.Instant;

public record OperatorWorkspaceWorkstream(
        String workstreamId,
        String effectiveWorkdir,
        String title,
        String status,
        Instant updatedAt
) {
    OperatorWorkspaceWorkstream normalized() {
        return new OperatorWorkspaceWorkstream(
                blank(workstreamId),
                blank(effectiveWorkdir),
                blank(title),
                blank(status),
                updatedAt
        );
    }

    private static String blank(String value) {
        return value == null ? "" : value;
    }
}
