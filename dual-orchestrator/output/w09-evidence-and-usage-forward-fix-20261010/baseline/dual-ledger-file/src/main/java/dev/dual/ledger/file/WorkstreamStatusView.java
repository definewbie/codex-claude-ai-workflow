package dev.dual.ledger.file;

import java.nio.file.Path;

public record WorkstreamStatusView(
        Path workstreamDir,
        WorkstreamDocument workstream,
        WorkspaceDocument workspace,
        StageDocument stage,
        IterationDocument iteration,
        CheckpointDocument checkpoint
) {
}
