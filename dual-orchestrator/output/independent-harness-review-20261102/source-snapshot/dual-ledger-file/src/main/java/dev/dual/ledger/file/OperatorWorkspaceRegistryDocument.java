package dev.dual.ledger.file;

import java.util.List;

public record OperatorWorkspaceRegistryDocument(
        int schemaVersion,
        String lastProjectId,
        List<OperatorWorkspaceProject> projects
) {
    static OperatorWorkspaceRegistryDocument empty() {
        return new OperatorWorkspaceRegistryDocument(1, "", List.of());
    }

    OperatorWorkspaceRegistryDocument normalized() {
        return new OperatorWorkspaceRegistryDocument(
                schemaVersion <= 0 ? 1 : schemaVersion,
                lastProjectId == null ? "" : lastProjectId,
                projects == null ? List.of() : projects.stream()
                        .map(OperatorWorkspaceProject::normalized)
                        .toList()
        );
    }
}
