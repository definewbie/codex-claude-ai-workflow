package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * Builds the per-agent-run governance context consumed by acpx prompts.
 *
 * Workflow context-package nodes still own rich cross-node packets. This class
 * only guarantees that every concrete agent execution receives its own typed
 * exploration plan and capability catalog projection, even when the workflow
 * graph does not include a dedicated context node before that agent.
 */
public final class AgentRunGovernanceContext {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(SerializationFeature.INDENT_OUTPUT);

    private AgentRunGovernanceContext() {
    }

    public static String ensure(TaskHandler.TaskContext context, String runId) throws IOException {
        var workspaceRoot = context.workspaceRoot();
        var workstreamDir = workspaceRoot.resolve(".agent")
                .resolve("workstreams")
                .resolve(context.workstreamId());
        var contextDir = workstreamDir.resolve("context");
        Files.createDirectories(contextDir);

        var manifest = EvidenceManifest.rebuild(workspaceRoot, workstreamDir, context.workstreamId(), OBJECT_MAPPER);
        manifest.write(workstreamDir, OBJECT_MAPPER);
        var governance = RepoScopeGovernance.build(workspaceRoot, workstreamDir, context, manifest, OBJECT_MAPPER);

        var packagePath = contextDir.resolve("agent-governance-"
                + safeFileName(context.instance().nodeInstanceId())
                + "-"
                + safeFileName(runId)
                + ".md");
        var builder = new StringBuilder();
        builder.append("# Agent Run Governance Context\n\n");
        builder.append("- Workstream: ").append(context.workstreamId()).append("\n");
        builder.append("- Target Node: ").append(context.definition().id()).append("\n");
        builder.append("- Agent Node Instance: ").append(context.instance().nodeInstanceId()).append("\n");
        builder.append("- Run Id: ").append(runId == null ? "" : runId).append("\n");
        builder.append("- Generated At: ").append(Instant.now()).append("\n\n");
        RepoScopeGovernance.appendMarkdownGuidance(builder, governance, workspaceRoot, true);
        Files.writeString(packagePath, builder.toString(), StandardCharsets.UTF_8);
        return workspaceRoot.relativize(packagePath).toString();
    }

    private static String safeFileName(String value) {
        var text = value == null || value.isBlank() ? "unknown" : value.trim();
        return text.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
