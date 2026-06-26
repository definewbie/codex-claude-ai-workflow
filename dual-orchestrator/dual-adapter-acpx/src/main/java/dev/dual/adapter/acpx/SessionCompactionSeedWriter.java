package dev.dual.adapter.acpx;

import dev.dual.runtime.handler.WorkstreamMemoryProjection;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

final class SessionCompactionSeedWriter {
    Optional<Path> write(
            Path workspaceRoot,
            String workstreamId,
            Path runDir,
            SessionCompactionPolicy.Decision decision
    ) throws IOException {
        if (decision == null || !decision.enabled()) {
            return Optional.empty();
        }
        var builder = new StringBuilder();
        builder.append("# Session Compaction Seed\n\n");
        builder.append("Mode: ").append(decision.mode().wire()).append("\n");
        builder.append("Reason: ").append(decision.reason()).append("\n\n");
        var workstreamDir = workspaceRoot.resolve(".agent").resolve("workstreams").resolve(workstreamId);
        if ("review_evidence_brief".equals(decision.seedKind())) {
            builder.append("## Review Evidence Brief\n\n");
            WorkstreamMemoryProjection.appendReviewEvidenceBrief(builder, workstreamDir, workspaceRoot);
        } else {
            builder.append("## Agent Handoff\n\n");
            WorkstreamMemoryProjection.appendAgentHandoff(builder, workstreamDir, workspaceRoot);
        }
        var path = runDir.resolve("prompts").resolve("session-compaction-seed.md");
        Files.createDirectories(path.getParent());
        Files.writeString(path, builder.toString().stripTrailing() + "\n", StandardCharsets.UTF_8);
        return Optional.of(path);
    }
}
