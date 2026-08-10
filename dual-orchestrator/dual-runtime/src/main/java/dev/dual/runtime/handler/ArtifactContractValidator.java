package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.dual.core.execution.ArtifactContractResult;
import dev.dual.core.execution.AgentResultContractResult;
import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeResult;
import dev.dual.core.workflow.NodeStatus;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

public final class ArtifactContractValidator {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private final AgentResultEnvelopeParser resultParser = new AgentResultEnvelopeParser();
    private final AgentResultEnvelopeValidator resultValidator = new AgentResultEnvelopeValidator();
    private final AgentResultDocumentProjector resultProjector = new AgentResultDocumentProjector();
    private final TaskContractRevisionContextResolver revisionContextResolver =
            new TaskContractRevisionContextResolver();
    private final TaskContractProposalRevisionMerger revisionMerger =
            new TaskContractProposalRevisionMerger();

    public NodeResult validate(Path workspaceRoot, NodeDefinition nodeDefinition, NodeResult result) throws Exception {
        return validate(workspaceRoot, "", "", nodeDefinition, List.of(), result);
    }

    public NodeResult validate(
            Path workspaceRoot,
            String workstreamId,
            String nodeInstanceId,
            NodeDefinition nodeDefinition,
            NodeResult result
    ) throws Exception {
        return validate(workspaceRoot, workstreamId, nodeInstanceId, nodeDefinition, List.of(), result);
    }

    public NodeResult validate(
            Path workspaceRoot,
            String workstreamId,
            String nodeInstanceId,
            NodeDefinition nodeDefinition,
            List<String> inputRefs,
            NodeResult result
    ) throws Exception {
        if (!"acpx.agent_run".equals(nodeDefinition.handlerType())) {
            return result;
        }
        if (!ContractRegistry.hasContract(nodeDefinition.id())) {
            return result;
        }
        if (Boolean.TRUE.equals(result.result().get("prepared"))) {
            return result;
        }
        return validateAgentOutput(workspaceRoot, workstreamId, nodeInstanceId, nodeDefinition, inputRefs, result);
    }

    private NodeResult validateAgentOutput(
            Path workspaceRoot,
            String workstreamId,
            String nodeInstanceId,
            NodeDefinition nodeDefinition,
            List<String> inputRefs,
            NodeResult result
    ) throws Exception {
        var root = workspaceRoot.toAbsolutePath().normalize();
        var contract = ContractRegistry.forNode(nodeDefinition.id());
        ContractViolation lastInvalid = null;
        String lastSourceRef = "";
        for (int i = result.outputRefs().size() - 1; i >= 0; i--) {
            var ref = result.outputRefs().get(i);
            if (ref.startsWith("artifact://") || !ref.endsWith(".json")) {
                continue;
            }
            var path = root.resolve(ref).normalize();
            if (!path.startsWith(root) || !Files.isRegularFile(path)) {
                continue;
            }
            var candidate = Files.readString(path, StandardCharsets.UTF_8);
            var parsed = resultParser.parse(candidate);
            if (!parsed.valid()) {
                lastInvalid = ContractViolation.of(parsed.errorCode(), parsed.message(), Map.of());
                lastSourceRef = ref;
                break;
            }
            var expectedRunId = text(result.result().get("run_id"));
            var validation = resultValidator.validate(parsed.envelope(), workstreamId, expectedRunId, contract);
            if (!validation.valid()) {
                lastInvalid = ContractViolation.of(
                        "AGENT_RESULT_CONTRACT_VIOLATION",
                        "AgentResultEnvelope failed contract validation: " + validation.errors(),
                        Map.of("errors", validation.errors())
                );
                lastSourceRef = ref;
                break;
            }
            var proposalResolution = resolveProposalCandidate(
                    root, workstreamId, nodeInstanceId, nodeDefinition, parsed.envelope(), ref);
            if (proposalResolution.failure() != null) {
                return contractViolation(root, workstreamId, nodeInstanceId, nodeDefinition, result, contract, ref,
                        parsed.envelope(), proposalResolution.failure());
            }
            return withResultSidecars(
                    root, workstreamId, nodeInstanceId, nodeDefinition, inputRefs, result, contract,
                    parsed.envelope(), ref, path, proposalResolution
            );
        }
        return contractViolation(workspaceRoot, workstreamId, nodeInstanceId, nodeDefinition, result, contract,
                lastSourceRef, null, lastInvalid);
    }

    private ProposalResolution resolveProposalCandidate(
            Path root,
            String workstreamId,
            String nodeInstanceId,
            NodeDefinition nodeDefinition,
            AgentResultEnvelope envelope,
            String sourceOutputRef
    ) {
        var isProposal = envelope.result() instanceof AgentResultEnvelope.TaskContractProposalResult;
        var isPatch = envelope.result() instanceof AgentResultEnvelope.TaskContractRevisionPatchResult;
        if (!isProposal && !isPatch) {
            return ProposalResolution.none();
        }
        if (!"intake.claude_brainstorm".equals(nodeDefinition.id())) {
            return ProposalResolution.failure(ContractViolation.of(
                    "TASK_CONTRACT_PROPOSAL_INVALID",
                    "task contract proposal output is only allowed for intake",
                    Map.of()));
        }
        var resolved = revisionContextResolver.resolveForNode(root, workstreamId, nodeInstanceId);
        if (!resolved.valid()) {
            return ProposalResolution.failure(ContractViolation.of(
                    "TASK_CONTRACT_REVISION_CONTEXT_INVALID",
                    "task contract proposal revision context could not be resolved",
                    Map.of("errors", resolved.errors())));
        }
        var context = resolved.context();
        TaskContractProposal proposal;
        AgentOutputContractDocument.ProposalDerivation derivation = null;
        if (isProposal) {
            if (context.revision()) {
                return ProposalResolution.failure(ContractViolation.of(
                        "TASK_CONTRACT_REVISION_SHAPE_INVALID",
                        "revision iteration requires task_contract_revision_patch",
                        Map.of()));
            }
            proposal = resultProjector.taskContractProposal(
                    (AgentResultEnvelope.TaskContractProposalResult) envelope.result());
        } else {
            if (!context.revision()) {
                return ProposalResolution.failure(ContractViolation.of(
                        "TASK_CONTRACT_REVISION_SHAPE_INVALID",
                        "initial iteration requires task_contract_proposal",
                        Map.of()));
            }
            var merged = revisionMerger.merge(
                    context, (AgentResultEnvelope.TaskContractRevisionPatchResult) envelope.result());
            if (!merged.valid()) {
                return ProposalResolution.failure(ContractViolation.of(
                        "TASK_CONTRACT_REVISION_PATCH_INVALID",
                        "task contract revision patch could not be merged",
                        Map.of("errors", merged.errors())));
            }
            proposal = merged.proposal();
            derivation = new AgentOutputContractDocument.ProposalDerivation(
                    "revision_patch_merge",
                    context.previousReference().proposalId(),
                    context.previousReference().sha256(),
                    context.previousReference(),
                    sourceOutputRef);
        }

        var proposalValidator = new TaskContractProposalValidator();
        var errors = proposalValidator.validate(proposal, root);
        if (!errors.isEmpty()) {
            return ProposalResolution.failure(ContractViolation.of(
                    "TASK_CONTRACT_PROPOSAL_VALIDATION_FAILED",
                    "task_contract_proposal failed mechanical validation: " + errors,
                    Map.of("errors", errors)));
        }
        var coverageErrors = context.revision()
                ? proposalValidator.validateRevisionCoverage(proposal, context.obligations())
                : proposalValidator.validateInitialIteration(proposal);
        if (!coverageErrors.isEmpty()) {
            return ProposalResolution.failure(ContractViolation.of(
                    "TASK_CONTRACT_REVISION_COVERAGE_INVALID",
                    "task_contract_proposal failed revision obligation coverage",
                    Map.of("errors", coverageErrors)));
        }
        if (context.revision()) {
            var deltaErrors = proposalValidator.validateRevisionDelta(context.previousProposal(), proposal);
            if (!deltaErrors.isEmpty()) {
                return ProposalResolution.failure(ContractViolation.of(
                        "TASK_CONTRACT_REVISION_DELTA_INVALID",
                        "task_contract_proposal contains an invalid revision delta",
                        Map.of(
                                "error_codes", deltaErrors.stream()
                                        .map(TaskContractProposalValidator.RevisionDeltaError::code).distinct().toList(),
                                "errors", deltaErrors.stream()
                                        .map(TaskContractProposalValidator.RevisionDeltaError::message).toList())));
            }
        }
        return new ProposalResolution(proposal, derivation, null);
    }

    private NodeResult withResultSidecars(
            Path root,
            String workstreamId,
            String nodeInstanceId,
            NodeDefinition nodeDefinition,
            List<String> inputRefs,
            NodeResult result,
            NodeOutputContract contract,
            AgentResultEnvelope envelope,
            String outputRef,
            Path outputPath,
            ProposalResolution proposalResolution
    ) throws Exception {
        var document = resultProjector.outputContract(
                envelope, contract, nodeDefinition.handlerType(), nodeInstanceId, outputRef,
                proposalResolution.proposal(), proposalResolution.derivation());
        var requiredReviewSubject = text(nodeDefinition.config().get("review_subject_artifact"));
        if (!requiredReviewSubject.isBlank()) {
            if (!"task_contract_proposal".equals(requiredReviewSubject)) {
                return contractViolation(root, workstreamId, nodeInstanceId, nodeDefinition, result, contract, outputRef,
                        envelope,
                        ContractViolation.of("REVIEW_SUBJECT_UNSUPPORTED",
                                "unsupported review_subject_artifact: " + requiredReviewSubject,
                                Map.of("review_subject_artifact", requiredReviewSubject)));
            }
            var reviewSubject = TaskContractProposalReference.resolveFromRefs(
                    root, workstreamId, inputRefs, OBJECT_MAPPER);
            if (reviewSubject.isEmpty()) {
                return contractViolation(root, workstreamId, nodeInstanceId, nodeDefinition, result, contract, outputRef,
                        envelope,
                        ContractViolation.of("REVIEW_SUBJECT_MISSING",
                                "exactly one validated task_contract_proposal reference is required",
                                Map.of("review_subject_artifact", requiredReviewSubject)));
            }
            document.put("review_subject", reviewSubject.get().reference().toMap());
        }
        AgentContractSemantics.addSidecarFields(document);
        var validatedDocument = AgentOutputContractDocument.from(document);
        var semanticFailure = AgentContractSemantics.validateOutputContract(validatedDocument);
        if (semanticFailure.isPresent()) {
            return contractViolation(root, workstreamId, nodeInstanceId, nodeDefinition, result, contract, outputRef,
                    envelope,
                    semanticFailure.get());
        }

        var sidecarPath = AgentOutputContractSidecar.pathFor(outputPath);
        Files.writeString(sidecarPath,
                OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(document) + "\n",
                StandardCharsets.UTF_8);
        var sidecarRef = root.relativize(sidecarPath).toString();
        var outputRefs = new ArrayList<>(result.outputRefs());
        if (!outputRefs.contains(sidecarRef)) {
            outputRefs.add(sidecarRef);
        }

        Optional<TaskContractProposalReference.Published> proposalReference = Optional.empty();
        if (proposalResolution.proposal() != null) {
            proposalReference = Optional.of(
                    envelope.result() instanceof AgentResultEnvelope.TaskContractRevisionPatchResult
                            ? TaskContractProposalReference.publishRevisionDerived(
                            root, workstreamId, sidecarPath, validatedDocument, OBJECT_MAPPER)
                            : TaskContractProposalReference.publishInitial(
                            root, workstreamId, sidecarPath, validatedDocument, OBJECT_MAPPER));
            if (!outputRefs.contains(proposalReference.get().referenceRef())) {
                outputRefs.add(proposalReference.get().referenceRef());
            }
        }

        var planArtifactRef = writePlanArtifactSidecars(
                root, workstreamId, nodeInstanceId, envelope, outputPath, outputRef);
        planArtifactRef.ifPresent(ref -> {
            if (!outputRefs.contains(ref)) {
                outputRefs.add(ref);
            }
        });

        var augmented = new LinkedHashMap<String, Object>();
        augmented.putAll(result.result());
        augmented.put("decision", envelope.result().decision());
        augmented.put("summary", envelope.result().summary());
        augmented.put("contract_sidecar_ref", sidecarRef);
        augmented.put("contract_sidecar_sha256", AgentOutputContractSidecar.sha256(sidecarPath));
        proposalReference.ifPresent(reference -> {
            augmented.put("task_contract_proposal_ref", reference.referenceRef());
            augmented.put("task_contract_proposal_id", reference.reference().proposalId());
        });
        planArtifactRef.ifPresent(ref -> augmented.put("plan_artifact_ref", ref));
        return AgentExecutionResultFacts.attach(new NodeResult(
                        result.outcome(), result.status(), outputRefs, augmented, result.errorMessage()),
                AgentResultContractResult.valid(envelope.result().decision()),
                List.of(ArtifactContractResult.requiredValid(envelope.result().artifactType())),
                document.get("reread_reasons"));
    }

    private record ProposalResolution(
            TaskContractProposal proposal,
            AgentOutputContractDocument.ProposalDerivation derivation,
            ContractViolation failure
    ) {
        static ProposalResolution none() {
            return new ProposalResolution(null, null, null);
        }

        static ProposalResolution failure(ContractViolation failure) {
            return new ProposalResolution(null, null, failure);
        }
    }

    private Optional<String> writePlanArtifactSidecars(
            Path root,
            String workstreamId,
            String nodeInstanceId,
            AgentResultEnvelope envelope,
            Path outputPath,
            String outputRef
    ) throws Exception {
        if (!(envelope.result() instanceof AgentResultEnvelope.PlanResult planResult)) {
            return Optional.empty();
        }
        var document = resultProjector.planArtifact(planResult);
        var sidecarPath = PlanArtifactSidecar.pathFor(outputPath);
        Files.writeString(sidecarPath,
                OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(document) + "\n",
                StandardCharsets.UTF_8);
        if (workstreamId != null && !workstreamId.isBlank()) {
            var canonicalPath = canonicalPlanArtifactPath(root, workstreamId);
            Files.createDirectories(canonicalPath.getParent());
            var canonical = new LinkedHashMap<String, Object>(document);
            canonical.put("workstream_id", workstreamId);
            canonical.put("node_instance_id", nodeInstanceId == null ? "" : nodeInstanceId);
            canonical.put("source_output_ref", outputRef == null ? "" : outputRef);
            canonical.put("generated_at", Instant.now().toString());
            canonical.put("canonical_source", "plan.claude_plan");
            Files.writeString(canonicalPath,
                    OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(canonical) + "\n",
                    StandardCharsets.UTF_8);
        }
        return Optional.of(root.relativize(sidecarPath).toString());
    }

    private static Path canonicalPlanArtifactPath(Path root, String workstreamId) {
        return root.resolve(".agent/workstreams").resolve(workstreamId)
                .resolve("artifacts/plan/plan_artifact.json").normalize();
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(item -> item != null && !String.valueOf(item).isBlank())
                .map(item -> String.valueOf(item).trim())
                .toList();
    }

    private static String normalizeRepoPath(String value) {
        var text = value == null ? "" : value.trim();
        while (text.startsWith("./")) {
            text = text.substring(2);
        }
        return text;
    }

    private static NodeResult contractViolation(
            Path workspaceRoot,
            String workstreamId,
            String nodeInstanceId,
            NodeDefinition nodeDefinition,
            NodeResult result,
            NodeOutputContract contract,
            String sourceOutputRef,
            AgentResultEnvelope validatedEnvelope,
            ContractViolation lastInvalid
    ) throws Exception {
        var reason = lastInvalid == null
                ? "AGENT_RESULT_MISSING"
                : lastInvalid.errorCode();
        var message = lastInvalid == null
                ? "No readable agent output artifact found for contract validation."
                : lastInvalid.message();
        var details = lastInvalid == null ? Map.<String, Object>of() : lastInvalid.details();
        var diagnostic = ContractFailureDiagnosticWriter.write(
                workspaceRoot,
                workstreamId,
                nodeInstanceId,
                nodeDefinition,
                contract,
                sourceOutputRef,
                reason,
                message,
                details
        );
        var failed = new LinkedHashMap<String, Object>();
        failed.putAll(result.result());
        failed.put("reason", "AGENT_OUTPUT_CONTRACT_VIOLATION");
        failed.put("contract_node_id", nodeDefinition.id());
        failed.put("contract_required_keys", List.copyOf(contract.requiredKeys()));
        failed.put("contract_allowed_decisions", List.copyOf(contract.allowedDecisions()));
        failed.put("parse_error", Map.of(
                "code", reason,
                "message", message,
                "details", details
        ));
        if (diagnostic.present()) {
            failed.put("contract_failure_diagnostic_ref", diagnostic.jsonRef());
            failed.put("contract_failure_diagnostic_markdown_ref", diagnostic.markdownRef());
        }
        failed.put("message", message);
        var outputRefs = new ArrayList<>(result.outputRefs());
        if (diagnostic.present() && !outputRefs.contains(diagnostic.jsonRef())) {
            outputRefs.add(diagnostic.jsonRef());
        }
        if (diagnostic.present() && !outputRefs.contains(diagnostic.markdownRef())) {
            outputRefs.add(diagnostic.markdownRef());
        }
        return AgentExecutionResultFacts.attach(new NodeResult(
                "AGENT_OUTPUT_CONTRACT_VIOLATION",
                NodeStatus.FAILED,
                outputRefs,
                failed,
                message
        ), terminalResultFor(reason, message, validatedEnvelope),
                List.of(artifactResultFor(nodeDefinition, reason, message, validatedEnvelope)), null);
    }

    private static AgentResultContractResult terminalResultFor(
            String reason,
            String message,
            AgentResultEnvelope validatedEnvelope
    ) {
        if (validatedEnvelope != null) {
            return AgentResultContractResult.valid(validatedEnvelope.result().decision());
        }
        if (reason == null || reason.isBlank() || "AGENT_RESULT_MISSING".equals(reason)) {
            return AgentResultContractResult.missing();
        }
        return AgentResultContractResult.invalid(message);
    }

    private static ArtifactContractResult artifactResultFor(
            NodeDefinition nodeDefinition,
            String reason,
            String message,
            AgentResultEnvelope validatedEnvelope
    ) {
        if (validatedEnvelope != null) {
            return new ArtifactContractResult(
                    requiredArtifactTypeOrDefault(nodeDefinition),
                    true,
                    true,
                    false,
                    "",
                    reason,
                    message
            );
        }
        if (reason != null && (reason.startsWith("AGENT_RESULT_") && !"AGENT_RESULT_MISSING".equals(reason))) {
            return new ArtifactContractResult(
                    requiredArtifactTypeOrDefault(nodeDefinition),
                    true,
                    false,
                    false,
                    "",
                    reason,
                    message
            );
        }
        return ArtifactContractResult.optionalMissing(requiredArtifactTypeOrDefault(nodeDefinition));
    }

    private static String requiredArtifactType(NodeDefinition nodeDefinition) {
        var configured = text(nodeDefinition.config().get("expected_artifact"));
        if (!configured.isBlank()) {
            return configured;
        }
        var contract = ContractRegistry.forNode(nodeDefinition.id());
        return contract.outputArtifactTypes().size() == 1
                ? contract.outputArtifactTypes().iterator().next()
                : "agent_output_contract";
    }

    private static String requiredArtifactTypeOrDefault(NodeDefinition nodeDefinition) {
        var required = requiredArtifactType(nodeDefinition);
        return required == null || required.isBlank() ? "agent_output_contract" : required;
    }

    private static Optional<Path> resolveWorkspaceRef(Path root, String ref) {
        if (ref == null || ref.isBlank()) {
            return Optional.empty();
        }
        var path = root.resolve(ref).normalize();
        if (!path.startsWith(root)) {
            return Optional.empty();
        }
        return Optional.of(path);
    }

    private static Object fieldOrLegacy(ObjectNode node, String field, String legacyField) {
        if (node.has(field)) {
            return OBJECT_MAPPER.convertValue(node.get(field), Object.class);
        }
        return OBJECT_MAPPER.convertValue(node.path(legacyField), Object.class);
    }

    private static String firstNonBlank(String first, String second) {
        return firstNonBlank(new String[]{first, second});
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (var value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static Optional<Map<String, Object>> readMap(Path path) {
        try {
            return Optional.of(OBJECT_MAPPER.readValue(path.toFile(), MAP_TYPE));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private static List<String> capabilityKinds(ObjectNode report) {
        var values = new LinkedHashSet<String>();
        for (var item : mapList(OBJECT_MAPPER.convertValue(report.path("actualCapabilityUsages"), Object.class))) {
            var kind = text(item.get("kind"));
            if (!kind.isBlank()) {
                values.add(kind);
            }
        }
        return List.copyOf(values);
    }

    private static List<Map<String, Object>> mapList(Object value) {
        if (!(value instanceof Iterable<?> iterable)) {
            return List.of();
        }
        var values = new ArrayList<Map<String, Object>>();
        for (var item : iterable) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            var copy = new LinkedHashMap<String, Object>();
            for (var entry : map.entrySet()) {
                if (entry.getKey() instanceof String key) {
                    copy.put(key, entry.getValue());
                }
            }
            values.add(copy);
        }
        return List.copyOf(values);
    }

    private static Map<String, String> stringMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        var copy = new LinkedHashMap<String, String>();
        for (var entry : map.entrySet()) {
            if (entry.getKey() instanceof String key && entry.getValue() != null) {
                var text = String.valueOf(entry.getValue()).trim();
                if (!text.isBlank()) {
                    copy.put(key, text);
                }
            }
        }
        return Map.copyOf(copy);
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

}
