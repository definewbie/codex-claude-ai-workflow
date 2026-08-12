package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.dual.core.contract.ContractStatus;
import dev.dual.core.contract.TaskContract;
import dev.dual.core.contract.TaskContractValidator;
import dev.dual.core.workflow.NodeResult;
import dev.dual.core.workflow.NodeStatus;
import dev.dual.ledger.file.GateDocument;
import dev.dual.ledger.file.NodeInstanceDocument;
import dev.dual.ledger.file.WorkflowIterationDocument;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ContractCompileHandler implements TaskHandler {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
    private final TaskContractProposalValidator proposalValidator = new TaskContractProposalValidator();
    private final TaskContractRevisionContextResolver revisionContextResolver =
            new TaskContractRevisionContextResolver();

    @Override
    public String type() {
        return "contract.compile";
    }

    @Override
    public NodeResult execute(TaskContext context) throws IOException {
        if (!"approved_task_contract_proposal".equals(
                String.valueOf(context.definition().config().getOrDefault("required_subject", "")))) {
            return proposalFailure(
                    "TASK_CONTRACT_SUBJECT_CONTRACT_MISSING",
                    "contract.compile requires required_subject=approved_task_contract_proposal",
                    List.of(context.definition().id())
            );
        }
        var workstreamDir = context.workspaceRoot()
                .resolve(".agent")
                .resolve("workstreams")
                .resolve(context.workstreamId());

        var requirementPath = workstreamDir.resolve("requirement.md");
        if (!Files.exists(requirementPath)) {
            return new NodeResult(
                    "failed", dev.dual.core.workflow.NodeStatus.FAILED,
                    List.of(),
                    Map.of("error", "requirement.md not found in workstream directory"),
                    "requirement.md not found: " + requirementPath
            );
        }

        var approvedProposal = resolveApprovedProposal(context);
        if (approvedProposal.failed() != null) {
            return approvedProposal.failed();
        }
        var revisionContext = resolveRevisionContext(
                context, approvedProposal.reviewedProposal(), approvedProposal.gate());
        if (revisionContext.failed() != null) {
            return revisionContext.failed();
        }
        var proposalResult = compileFromProposal(
                Optional.of(approvedProposal.sidecarPath()), context, revisionContext.context());
        if (proposalResult.failed() != null) {
            return proposalResult.failed();
        }
        if (proposalResult.contract() == null) {
            return proposalFailure(
                    "TASK_CONTRACT_PROPOSAL_MISSING",
                    "task_contract_proposal is required; strict mode does not compile canonical TaskContract from Markdown",
                    List.of("No validated intake.claude_brainstorm AgentResultEnvelope JSON was found")
            );
        }
        var contract = proposalResult.contract();
        var resolutionResult = resolveContractNegotiation(
                proposalResult.proposal(), approvedProposal.reviewedProposal(), approvedProposal.gate());
        if (resolutionResult.failed() != null) {
            return resolutionResult.failed();
        }

        var errors = TaskContractValidator.validate(contract);
        if (!errors.isEmpty()) {
            var ambiguityNotes = new ArrayList<>(contract.ambiguityNotes());
            ambiguityNotes.addAll(errors);
            var humanQuestions = new ArrayList<>(contract.humanQuestions());
            humanQuestions.add("Contract compilation produced validation errors — please clarify the requirement");
            contract = new TaskContract(
                    contract.id(), contract.version(), contract.createdAt(),
                    contract.objective(), contract.scope(), contract.acceptanceCriteria(),
                    contract.testRequirements(), contract.constraints(),
                    contract.nonGoals(), contract.riskAreas(), contract.evidenceRefs(),
                    ambiguityNotes, humanQuestions,
                    ContractStatus.NEEDS_CLARIFICATION, contract.compilationPass(), contract.source()
            );
        }

        // Write artifacts
        var artifactsDir = workstreamDir.resolve("artifacts");
        TaskContractArtifactWriter.writeJson(contract, artifactsDir);
        TaskContractArtifactWriter.writeMarkdown(contract, artifactsDir);
        var resolutionRefs = writeContractResolutionArtifacts(context, artifactsDir, resolutionResult.resolution());

        var now = Instant.now();
        var result = new LinkedHashMap<String, Object>();
        result.put("contract_id", contract.id());
        result.put("contract_status", contract.status().name());
        result.put("contract_version", contract.version());
        result.put("compilation_pass", contract.compilationPass());
        var outputRefs = new ArrayList<String>();
        outputRefs.add(".agent/workstreams/" + context.workstreamId() + "/artifacts/task-contract.json");
        outputRefs.add(".agent/workstreams/" + context.workstreamId() + "/artifacts/task-contract.md");
        outputRefs.addAll(resolutionRefs);
        result.put("artifacts", List.copyOf(outputRefs));
        result.put("generated_at", now.toString());

        return NodeResult.completed("completed", List.copyOf(outputRefs), result);
    }

    private ProposalCompileResult compileFromProposal(
            Optional<Path> sourcePath,
            TaskContext context,
            RevisionContext revisionContext
    ) {
        if (sourcePath.isEmpty()) {
            return new ProposalCompileResult(null, null, null);
        }
        try {
            var document = AgentOutputContractDocument.read(sourcePath.get(), OBJECT_MAPPER);
            if (document.isEmpty()) {
                return new ProposalCompileResult(null, null, proposalFailure(
                        "TASK_CONTRACT_PROPOSAL_PARSE_FAILED",
                        "validated task_contract_proposal sidecar could not be parsed",
                        List.of(sourcePath.get().toString())));
            }
            var validated = document.get();
            if (AgentOutputLineageVerifier.violation(
                        context.workspaceRoot(), context.workstreamId(), sourcePath.get(), validated).isPresent()
                    || !"intake.claude_brainstorm".equals(validated.nodeId())) {
                return new ProposalCompileResult(null, null, proposalFailure(
                        "TASK_CONTRACT_PROPOSAL_INVALID",
                        "validated task_contract_proposal sidecar lineage is invalid",
                        List.of(sourcePath.get().toString())));
            }
            var proposal = validated.taskContractProposal();
            if (proposal == null) {
                return new ProposalCompileResult(null, null, proposalFailure(
                        "TASK_CONTRACT_PROPOSAL_INVALID",
                        "validated sidecar does not contain task_contract_proposal",
                        List.of(sourcePath.get().toString())));
            }
            var errors = proposalValidator.validate(proposal, context.workspaceRoot().toAbsolutePath().normalize());
            if (!errors.isEmpty()) {
                return new ProposalCompileResult(null, proposal, proposalFailure(
                        "TASK_CONTRACT_PROPOSAL_INVALID",
                        "task_contract_proposal failed mechanical validation",
                        errors
                ));
            }
            var coverageErrors = revisionContext.revision()
                    ? proposalValidator.validateRevisionCoverage(proposal, revisionContext.obligations())
                    : proposalValidator.validateInitialIteration(proposal);
            if (!coverageErrors.isEmpty()) {
                return new ProposalCompileResult(null, proposal, proposalFailure(
                        "TASK_CONTRACT_PROPOSAL_INVALID",
                        "task_contract_proposal failed revision obligation coverage",
                        coverageErrors
                ));
            }
            if (revisionContext.revision()) {
                var deltaErrors = proposalValidator.validateRevisionDelta(
                        revisionContext.previousProposal(), proposal);
                if (!deltaErrors.isEmpty()) {
                    return new ProposalCompileResult(null, proposal, proposalFailure(
                            "TASK_CONTRACT_REVISION_DELTA_INVALID",
                            "task_contract_proposal contains an invalid revision delta",
                            deltaErrors.stream().map(TaskContractProposalValidator.RevisionDeltaError::message).toList()
                    ));
                }
            }
            return new ProposalCompileResult(toTaskContract(proposal, context.workstreamId()), proposal, null);
        } catch (Exception e) {
            return new ProposalCompileResult(null, null, proposalFailure(
                    "TASK_CONTRACT_PROPOSAL_PARSE_FAILED",
                    "task_contract_proposal JSON could not be parsed: " + e.getMessage(),
                    List.of(e.getMessage())
            ));
        }
    }

    private static TaskContract toTaskContract(TaskContractProposal proposal, String workstreamId) {
        return new TaskContract(
                "tc-" + workstreamId + "-v1",
                1,
                Instant.now(),
                proposal.objective().trim(),
                new TaskContract.Scope(
                        List.copyOf(proposal.scope().included()),
                        cleanList(proposal.scope().excluded())
                ),
                cleanClauseTexts(proposal.acceptanceCriteria()),
                cleanClauseTexts(proposal.testRequirements()),
                cleanClauseTexts(proposal.constraints()),
                cleanClauseTexts(proposal.nonGoals()),
                cleanClauseTexts(proposal.riskAreas()),
                cleanList(proposal.evidenceRefs()),
                List.of(),
                List.of(),
                ContractStatus.READY_FOR_REVIEW,
                1,
                new TaskContract.Source(
                        ".agent/workstreams/" + workstreamId + "/requirement.md",
                        "task_contract_proposal"
                )
        );
    }

    private static List<String> cleanClauseTexts(List<TaskContractProposal.ContractClause> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && value.text() != null && !value.text().isBlank())
                .map(value -> value.text().trim())
                .distinct()
                .toList();
    }

    private static List<String> cleanList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private ApprovedProposalResult resolveApprovedProposal(TaskContext context) throws IOException {
        var reviewed = reviewedProposalFromInputs(context);
        if (reviewed.size() != 1) {
            return new ApprovedProposalResult(null, null, null, proposalFailure(
                    "TASK_CONTRACT_PROPOSAL_APPROVAL_MISSING",
                    "exactly one Codex review bound to a task_contract_proposal is required",
                    List.of("review_subject_count=" + reviewed.size())
            ));
        }
        var subject = reviewed.get(0);
        var gate = latestResolvedRequirementGate(context, subject.reviewRef());
        if (gate.isEmpty()) {
            return new ApprovedProposalResult(null, null, null, proposalFailure(
                    "TASK_CONTRACT_PROPOSAL_APPROVAL_MISSING",
                    "the current Codex review was not approved by the requirement gate",
                    List.of(subject.reviewRef())
            ));
        }
        var sidecarPath = subject.reference().resolveArtifact(
                context.workspaceRoot(), context.workstreamId(), OBJECT_MAPPER);
        if (sidecarPath.isEmpty()) {
            return new ApprovedProposalResult(null, null, null, proposalFailure(
                    "TASK_CONTRACT_PROPOSAL_INVALID",
                    "the approved task_contract_proposal reference is stale or tampered",
                    List.of(subject.reference().artifactRef())
            ));
        }
        return new ApprovedProposalResult(sidecarPath.get(), gate.get(), subject, null);
    }

    private RevisionContextResult resolveRevisionContext(
            TaskContext context,
            ReviewedProposal approvedReview,
            ResolvedGate gate
    ) {
        var workstreamDir = context.workspaceRoot().resolve(".agent/workstreams").resolve(context.workstreamId());
        try {
            var resolved = revisionContextResolver.resolveForIteration(
                    context.workspaceRoot(), context.workstreamId(), context.instance().iterationId());
            if (!resolved.valid()) {
                return revisionContextFailure(
                        resolved.errors().isEmpty()
                                ? "revision iteration lineage could not be resolved"
                                : resolved.errors().getFirst(),
                        resolved.errors());
            }
            var iteration = resolved.context().iteration();
            var currentLineageErrors = validateCurrentApprovedLineage(workstreamDir, iteration, approvedReview, gate);
            if (!currentLineageErrors.isEmpty()) {
                return revisionContextFailure("approved review and proposal are outside the current iteration",
                        currentLineageErrors);
            }
            return new RevisionContextResult(
                    new RevisionContext(
                            resolved.context().revision(),
                            resolved.context().previousProposal(),
                            resolved.context().obligations()), null);
        } catch (Exception failure) {
            return revisionContextFailure("revision iteration lineage could not be resolved",
                    List.of(failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage()));
        }
    }

    private List<String> validateCurrentApprovedLineage(
            Path workstreamDir,
            WorkflowIterationDocument iteration,
            ReviewedProposal approvedReview,
            ResolvedGate gate
    ) throws IOException {
        var errors = new ArrayList<String>();
        var reviewDecision = approvedReview.document().decision();
        var eligibleReady = "READY_FOR_PRODUCT_REVIEW".equals(reviewDecision)
                && "READY_FOR_PRODUCT_REVIEW".equals(gate.sourceOutcome());
        var eligibleBlocked = "BLOCKED".equals(reviewDecision)
                && "BLOCKED".equals(gate.sourceOutcome())
                && !gate.note().isBlank();
        if (!"intake.codex_product_review".equals(approvedReview.document().nodeId())
                || (!eligibleReady && !eligibleBlocked)) {
            errors.add("current review is not an approved intake product review");
        }
        if (!TaskContractRevisionContextResolver.nodeBelongsToIteration(
                workstreamDir, approvedReview.document().nodeInstanceId(), iteration.iterationId())) {
            errors.add("current review node does not belong to " + iteration.iterationId());
        }
        if (!TaskContractRevisionContextResolver.nodeBelongsToIteration(
                workstreamDir, approvedReview.reference().producerNodeInstanceId(), iteration.iterationId())) {
            errors.add("approved proposal node does not belong to " + iteration.iterationId());
        }
        return List.copyOf(errors);
    }

    private static RevisionContextResult revisionContextFailure(String message, List<String> errors) {
        return new RevisionContextResult(null, proposalFailure(
                "TASK_CONTRACT_PROPOSAL_INVALID", message, errors));
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private List<ReviewedProposal> reviewedProposalFromInputs(TaskContext context) {
        var root = context.workspaceRoot().toAbsolutePath().normalize();
        var reviewed = new ArrayList<ReviewedProposal>();
        for (var inputRef : context.instance().inputRefs()) {
            if (inputRef == null || !inputRef.endsWith(".contract.json")) {
                continue;
            }
            var path = root.resolve(inputRef).normalize();
            if (!path.startsWith(root) || !Files.isRegularFile(path)) {
                continue;
            }
            var document = AgentOutputContractDocument.read(path, OBJECT_MAPPER);
            if (document.isEmpty()
                    || AgentOutputLineageVerifier.violation(
                            root, context.workstreamId(), path, document.get()).isPresent()
                    || !"intake.codex_product_review".equals(document.get().nodeId())
                    || document.get().reviewSubject() == null) {
                continue;
            }
            reviewed.add(new ReviewedProposal(
                    inputRef, path, document.get(), document.get().reviewSubject()));
        }
        return List.copyOf(reviewed);
    }

    private ContractResolutionResult resolveContractNegotiation(
            TaskContractProposal proposal,
            ReviewedProposal approvedReview,
            ResolvedGate gateResolution
    ) {
        var blockedReview = "BLOCKED".equals(approvedReview.document().decision())
                && "BLOCKED".equals(gateResolution.sourceOutcome());
        var openQuestions = blockedReview
                ? blockedReviewQuestions(approvedReview.document())
                : cleanList(proposal.openQuestions());
        if (blockedReview && (gateResolution.note().isBlank() || openQuestions.isEmpty())) {
            return new ContractResolutionResult(null, proposalFailure(
                    "TASK_CONTRACT_PROPOSAL_INVALID",
                    "a BLOCKED review requires typed questions and a human resolution note",
                    List.of(approvedReview.reviewRef())
            ));
        }
        var resolvedQuestions = new ArrayList<ContractResolutionDocument.ResolvedQuestion>();
        for (var question : openQuestions) {
            resolvedQuestions.add(new ContractResolutionDocument.ResolvedQuestion(
                    question,
                    gateResolution.note().isBlank()
                            ? "Human approved the requirement gate; this question is accepted as a non-blocking contract assumption."
                            : gateResolution.note(),
                    "human"
            ));
        }
        var assumptions = new ArrayList<String>();
        if (resolvedQuestions.isEmpty()) {
            assumptions.add("Human approved the requirement review gate before canonical contract generation.");
        } else {
            assumptions.add("Open proposal questions were resolved by the requirement review gate.");
        }
        var resolution = new ContractResolutionDocument(
                1,
                "APPROVE",
                resolvedQuestions,
                assumptions,
                List.of(),
                gateResolution.gateRef(),
                "human_gate",
                Instant.now()
        );
        return new ContractResolutionResult(resolution, null);
    }

    private static List<String> blockedReviewQuestions(AgentOutputContractDocument review) {
        var questions = new java.util.LinkedHashSet<String>();
        questions.addAll(cleanList(review.mustFix()));
        questions.addAll(cleanList(review.missingEvidence()));
        return List.copyOf(questions);
    }

    private Optional<ResolvedGate> latestResolvedRequirementGate(TaskContext context, String reviewRef) throws IOException {
        var workstreamDir = context.workspaceRoot()
                .resolve(".agent")
                .resolve("workstreams")
                .resolve(context.workstreamId());
        var gatesDir = workstreamDir.resolve("gates");
        if (!Files.isDirectory(gatesDir)) {
            return Optional.empty();
        }
        try (var paths = Files.walk(gatesDir)) {
            var gates = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(path -> path.toAbsolutePath().normalize().toString()))
                    .toList();
            for (int i = gates.size() - 1; i >= 0; i--) {
                var gate = readResolvedGate(context, workstreamDir, gates.get(i), reviewRef);
                if (gate.isPresent()) {
                    return gate;
                }
            }
        }
        return Optional.empty();
    }

    private Optional<ResolvedGate> readResolvedGate(
            TaskContext context,
            Path workstreamDir,
            Path gatePath,
        String reviewRef
    ) {
        try {
            var gate = OBJECT_MAPPER.readValue(gatePath.toFile(), GateDocument.class);
            if (!"resolved".equals(gate.status())) {
                return Optional.empty();
            }
            var nodeInstanceId = gate.nodeInstanceId();
            var nodePath = workstreamDir.resolve("nodes").resolve(nodeInstanceId + ".json");
            if (!Files.isRegularFile(nodePath)) {
                return Optional.empty();
            }
            var node = OBJECT_MAPPER.readValue(nodePath.toFile(), NodeInstanceDocument.class);
            if (!"gate.human_requirement_review".equals(node.nodeDefinitionId())
                    || !context.instance().iterationId().equals(node.iterationId())) {
                return Optional.empty();
            }
            var result = gate.result();
            var subjectRefs = result.get("subject_input_refs") instanceof List<?> values
                    ? values.stream().map(String::valueOf).toList()
                    : List.<String>of();
            if (!subjectRefs.contains(reviewRef)) {
                return Optional.empty();
            }
            var actionValue = result.get("human_action");
            var action = actionValue == null ? "" : String.valueOf(actionValue);
            if (!"approved".equals(action)) {
                return Optional.empty();
            }
            var noteValue = result.get("note");
            var note = noteValue == null ? "" : String.valueOf(noteValue);
            var sourceOutcomeValue = result.get("source_outcome");
            var sourceOutcome = sourceOutcomeValue == null ? "" : String.valueOf(sourceOutcomeValue);
            var gateRef = context.workspaceRoot().relativize(gatePath).toString();
            return Optional.of(new ResolvedGate(gateRef, action, note, sourceOutcome));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private List<String> writeContractResolutionArtifacts(
            TaskContext context,
            Path artifactsDir,
            ContractResolutionDocument resolution
    ) throws IOException {
        if (resolution == null) {
            return List.of();
        }
        Files.createDirectories(artifactsDir);
        var jsonPath = artifactsDir.resolve("contract-resolution.json");
        var markdownPath = artifactsDir.resolve("contract-resolution.md");
        OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(jsonPath.toFile(), resolution);
        Files.writeString(markdownPath, renderContractResolutionMarkdown(resolution), StandardCharsets.UTF_8);
        return List.of(
                ".agent/workstreams/" + context.workstreamId() + "/artifacts/contract-resolution.json",
                ".agent/workstreams/" + context.workstreamId() + "/artifacts/contract-resolution.md"
        );
    }

    private static String renderContractResolutionMarkdown(ContractResolutionDocument resolution) {
        var builder = new StringBuilder();
        builder.append("# Contract Resolution\n\n");
        builder.append("- Decision: ").append(resolution.decision()).append("\n");
        builder.append("- Approved By: ").append(resolution.approvedBy()).append("\n");
        builder.append("- Gate Ref: ").append(resolution.gateRef()).append("\n");
        builder.append("- Created At: ").append(resolution.createdAt()).append("\n\n");
        if (!resolution.resolvedQuestions().isEmpty()) {
            builder.append("## Resolved Questions\n\n");
            for (var item : resolution.resolvedQuestions()) {
                builder.append("- Question: ").append(item.question()).append("\n");
                builder.append("  Resolution: ").append(item.resolution()).append("\n");
                builder.append("  Source: ").append(item.source()).append("\n");
            }
            builder.append("\n");
        }
        if (!resolution.assumptions().isEmpty()) {
            builder.append("## Assumptions\n\n");
            for (var assumption : resolution.assumptions()) {
                builder.append("- ").append(assumption).append("\n");
            }
        }
        return builder.toString();
    }

    private static NodeResult proposalFailure(String reason, String message, List<String> errors) {
        return new NodeResult(
                reason,
                NodeStatus.FAILED,
                List.of(),
                Map.of(
                        "reason", reason,
                        "message", message,
                        "validation_errors", List.copyOf(errors)
                ),
                message
        );
    }

    private record ProposalCompileResult(TaskContract contract, TaskContractProposal proposal, NodeResult failed) {}

    private record ReviewedProposal(
            String reviewRef,
            Path reviewPath,
            AgentOutputContractDocument document,
            TaskContractProposalReference reference
    ) {}

    private record ApprovedProposalResult(
            Path sidecarPath,
            ResolvedGate gate,
            ReviewedProposal reviewedProposal,
            NodeResult failed
    ) {}

    private record RevisionContext(
            boolean revision,
            TaskContractProposal previousProposal,
            List<AgentResultEnvelope.ReviewObligation> obligations
    ) {}

    private record RevisionContextResult(RevisionContext context, NodeResult failed) {}

    private record ContractResolutionResult(ContractResolutionDocument resolution, NodeResult failed) {}

    private record ResolvedGate(String gateRef, String action, String note, String sourceOutcome) {}

}
