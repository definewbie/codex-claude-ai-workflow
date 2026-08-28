package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

/**
 * Builds the per-agent-run governance context consumed by acpx prompts.
 *
 * Workflow context-package nodes still own rich cross-node packets. This class
 * only guarantees that every concrete agent execution receives its own typed
 * exploration plan and capability catalog projection, even when the workflow
 * graph does not include a dedicated context node before that agent.
 */
public final class AgentRunGovernanceContext {
    public static final String QUALIFICATION_FAILURE_OUTCOME =
            TaskContractProjectionVerifier.FAILURE_OUTCOME;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(SerializationFeature.INDENT_OUTPUT);

    private AgentRunGovernanceContext() {
    }

    public static String ensure(TaskHandler.TaskContext context, String runId) throws IOException {
        return ensureQualified(context, runId).contextRef();
    }

    public static PreparedContext ensureQualified(TaskHandler.TaskContext context, String runId) throws IOException {
        var workspaceRoot = context.workspaceRoot();
        var workstreamDir = workspaceRoot.resolve(".agent")
                .resolve("workstreams")
                .resolve(context.workstreamId());
        var contractResolution = TaskContractProjectionVerifier.resolveRoutedAcceptance(
                workspaceRoot,
                context.workstreamId(),
                context.definition().id(),
                context.instance().inputRefs());
        if (contractResolution.failure() != null) {
            throw qualificationFailure(contractResolution.failure());
        }
        var expectedArtifact = String.valueOf(
                context.definition().config().getOrDefault("expected_artifact", "")).trim();
        if ("acceptance_review".equals(expectedArtifact) && contractResolution.qualified().isEmpty()) {
            throw new QualificationException(TaskContractProjectionVerifier.FAILURE_OUTCOME
                    + ": CONTEXT_BINDING_MISSING: acceptance agent has no routed task contract binding");
        }
        Optional<TaskContractProjectionVerifier.QualifiedTaskContract> qualifiedContract =
                contractResolution.qualified();

        var contextDir = workstreamDir.resolve("context");
        Files.createDirectories(contextDir);

        var manifest = EvidenceManifest.rebuild(workspaceRoot, workstreamDir, context.workstreamId(),
                context.instance().inputRefs(), OBJECT_MAPPER, qualifiedContract);
        manifest.write(workstreamDir, OBJECT_MAPPER);
        var governance = RepoScopeGovernance.build(
                workspaceRoot, workstreamDir, context, OBJECT_MAPPER, qualifiedContract, runId);

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
        return new PreparedContext(
                workspaceRoot.relativize(packagePath).toString(),
                qualifiedContract.map(TaskContractProjectionVerifier.QualifiedTaskContract::wrappedPayload),
                governance.repositoryFactBinding());
    }

    public static Optional<String> trustedTaskContractPayload(
            Path workspaceRoot,
            String workstreamId,
            String nodeId,
            java.util.List<String> inputRefs
    ) {
        return TaskContractProjectionVerifier.trustedWrappedPayload(
                workspaceRoot, workstreamId, nodeId, inputRefs);
    }

    private static QualificationException qualificationFailure(TaskContractProjectionVerifier.Failure failure) {
        return new QualificationException(TaskContractProjectionVerifier.FAILURE_OUTCOME
                + ": " + failure.reason() + ": " + failure.message());
    }

    public static final class QualificationException extends IOException {
        QualificationException(String message) {
            super(message);
        }
    }

    public record PreparedContext(
            String contextRef,
            Optional<String> trustedTaskContractPayload,
            RepositoryFactBinding.Verified repositoryFactBinding
    ) {
    }

    private static String safeFileName(String value) {
        var text = value == null || value.isBlank() ? "unknown" : value.trim();
        return text.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
