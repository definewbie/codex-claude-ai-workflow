package dev.dual.ledger.file;

import java.util.List;

public record IterationDocument(
        int schemaVersion,
        String iterationId,
        String stageId,
        int number,
        String status,
        String objective,
        String claudeRunId,
        String codexRunId,
        List<String> inputArtifactRefs,
        List<String> outputArtifactRefs
) {
    public static IterationDocument initialDesign() {
        return new IterationDocument(
                1,
                "iter-001",
                "stage-design",
                1,
                "created",
                "Create the initial design for the user requirement.",
                "",
                "",
                List.of("artifact-requirement"),
                List.of()
        );
    }
}
