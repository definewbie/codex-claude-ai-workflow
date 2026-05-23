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

    public static IterationDocument designRevision(int number) {
        var suffix = "%03d".formatted(number);
        return new IterationDocument(
                1,
                "iter-" + suffix,
                "stage-design",
                number,
                "created",
                "Revise the design using Codex review and human feedback.",
                "",
                "",
                List.of("artifact-requirement", "artifact-codex-review-001", "artifact-human-feedback-001"),
                List.of()
        );
    }

    IterationDocument withStatus(String nextStatus) {
        return new IterationDocument(
                schemaVersion,
                iterationId,
                stageId,
                number,
                nextStatus,
                objective,
                claudeRunId,
                codexRunId,
                inputArtifactRefs,
                outputArtifactRefs
        );
    }
}
