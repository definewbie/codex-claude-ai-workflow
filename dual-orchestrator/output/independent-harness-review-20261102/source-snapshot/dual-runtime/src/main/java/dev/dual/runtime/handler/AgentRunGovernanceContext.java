package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.dual.core.contract.TaskContract;

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
        var expectedArtifact = String.valueOf(
                context.definition().config().getOrDefault("expected_artifact", "")).trim();
        var requiresQualification = requiresApprovedTaskContract(context);
        var contractResolution = requiresQualification || "acceptance_review".equals(expectedArtifact)
                ? ("acceptance_review".equals(expectedArtifact)
                        ? TaskContractProjectionVerifier.resolveRoutedAcceptance(
                                context.ledger(), workspaceRoot,
                                context.workstreamId(),
                                context.definition().id(),
                                context.instance().inputRefs())
                        : TaskContractProjectionVerifier.resolve(context.ledger(), workspaceRoot, context.workstreamId(), TaskContractProjectionVerifier.requiredPhase(context.definition())))
                : TaskContractProjectionVerifier.Resolution.absent();
        if (contractResolution.failure() != null) {
            throw qualificationFailure(context, contractResolution.failure(), "AgentRunGovernanceContext.ensureQualified");
        }
        if (requiresQualification && contractResolution.qualified().isEmpty()) {
            throw missingBinding(context, ContractQualificationTrace.Rule.APPROVAL_REQUIRED, "APPROVAL_BINDING_MISSING", "node requires an approved task contract");
        }
        if ("acceptance_review".equals(expectedArtifact) && contractResolution.qualified().isEmpty()) {
            throw missingBinding(context, ContractQualificationTrace.Rule.CONTEXT_REQUIRED, "CONTEXT_BINDING_MISSING", "acceptance agent has no routed task contract binding");
        }
        try {
            VerificationEvidence.verifyAcceptanceInputs(context);
        } catch (IOException invalid) {
            throw new QualificationException("VERIFICATION_EVIDENCE_INVALID", invalid.getMessage());
        }
        Optional<TaskContractProjectionVerifier.QualifiedTaskContract> qualifiedContract =
                contractResolution.qualified();
        final Optional<AuthoredExecutionContext.Snapshot> authoredContext;
        try {
            authoredContext = AuthoredExecutionContext.prepare(context, runId);
        } catch (IOException failure) {
            throw new AuthoredContextException(failure.getMessage(), failure);
        }

        var contextDir = workstreamDir.resolve("context");
        Files.createDirectories(contextDir);

        var manifest = EvidenceManifest.rebuild(context.ledger(), workspaceRoot, workstreamDir, context.workstreamId(),
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
        if (qualifiedContract.isPresent() && qualifiedContract.get().contract().phase() == TaskContract.Phase.GOAL)
            builder.append(VerificationReadiness.context(context.ledger(), workspaceRoot, context.workstreamId(), qualifiedContract.get()));
        Files.writeString(packagePath, builder.toString(), StandardCharsets.UTF_8);
        return new PreparedContext(
                workspaceRoot.relativize(packagePath).toString(),
                qualifiedContract.map(TaskContractProjectionVerifier.QualifiedTaskContract::wrappedPayload),
                governance.repositoryFactBinding(),
                qualifiedContract.map(TaskContractProjectionVerifier.QualifiedTaskContract::contract),
                qualifiedContract.map(TaskContractProjectionVerifier.QualifiedTaskContract::contractRef).orElse(""),
                qualifiedContract.map(TaskContractProjectionVerifier.QualifiedTaskContract::sha256).orElse(""),
                authoredContext);
    }

    public static Optional<String> trustedTaskContractPayload(
            dev.dual.ledger.file.WorkflowFileLedger ledger,
            Path workspaceRoot,
            String workstreamId,
            String nodeId,
            java.util.List<String> inputRefs
    ) {
        return TaskContractProjectionVerifier.trustedWrappedPayload(
                ledger, workspaceRoot, workstreamId, nodeId, inputRefs);
    }

    static QualificationException qualificationFailure(TaskHandler.TaskContext context, TaskContractProjectionVerifier.Failure failure, String consumer) {
        return new QualificationException(failure.reason(), failure.message(),
                ContractQualificationTrace.continuation(context, failure.reason(), failure.check(), consumer));
    }
    private static QualificationException missingBinding(TaskHandler.TaskContext context, ContractQualificationTrace.Rule rule, String code, String message) {
        var trace = new ContractQualificationTrace(context.ledger(), context.workspaceRoot());
        trace.rejects(rule, true, dev.dual.ledger.file.ContractQualificationChecks.values(
                "requiredPhase", context.definition().config().get("required_contract_phase"),
                "target", context.instance().nodeInstanceId(), "inputRefs", context.instance().inputRefs()));
        return qualificationFailure(context, trace.failure(rule, code, message), "AgentRunGovernanceContext.ensureQualified");
    }

    public static boolean requiresApprovedTaskContract(TaskHandler.TaskContext context) {
        return TaskContractProjectionVerifier.requiresContract(context.definition());
    }

    public static final class QualificationException extends IOException {
        private final String reason;
        private final dev.dual.ledger.file.ContractQualificationChecks.ContinuationFailure diagnostic;
        QualificationException(String reason, String message) { this(reason, message, null); }
        QualificationException(String reason, String message, dev.dual.ledger.file.ContractQualificationChecks.ContinuationFailure diagnostic) {
            super(TaskContractProjectionVerifier.FAILURE_OUTCOME + ": " + reason + ": " + message);
            this.reason = reason; this.diagnostic = diagnostic;
        }
        public String reason() { return reason; }
        public dev.dual.core.workflow.NodeResult result() { return AgentExecutionResultFacts.inputPreparationFailed(QUALIFICATION_FAILURE_OUTCOME, reason, java.util.List.of(getMessage()), diagnostic); }
    }

    public record PreparedContext(
            String contextRef,
            Optional<String> trustedTaskContractPayload,
            RepositoryFactBinding.Verified repositoryFactBinding,
            Optional<TaskContract> qualifiedTaskContract,
            String taskContractRef,
            String taskContractSha256,
            Optional<AuthoredExecutionContext.Snapshot> authoredContext
    ) {
    }

    public static final class AuthoredContextException extends IOException {
        AuthoredContextException(String message, Throwable cause) { super(message, cause); }
    }

    private static String safeFileName(String value) {
        var text = value == null || value.isBlank() ? "unknown" : value.trim();
        return text.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
