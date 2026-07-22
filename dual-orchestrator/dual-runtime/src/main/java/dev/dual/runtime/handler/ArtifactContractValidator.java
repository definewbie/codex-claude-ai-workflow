package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.dual.core.execution.ArtifactContractResult;
import dev.dual.core.execution.AgentResultContractResult;
import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeResult;
import dev.dual.core.workflow.NodeStatus;
import dev.dual.runtime.governance.ExplorationPolicyEvaluator;

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
import java.util.Set;
import java.util.regex.Pattern;

public final class ArtifactContractValidator {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private final AgentResultEnvelopeParser resultParser = new AgentResultEnvelopeParser();
    private final AgentResultEnvelopeValidator resultValidator = new AgentResultEnvelopeValidator();
    private final AgentResultDocumentProjector resultProjector = new AgentResultDocumentProjector();

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
            var semanticFailure = validateAgentResult(root, parsed.envelope());
            if (semanticFailure != null) {
                return contractViolation(root, workstreamId, nodeInstanceId, nodeDefinition, result, contract, ref, semanticFailure);
            }
            var rereadCoverageFailure = validateRereadCoverage(root, workstreamId, nodeDefinition, result, parsed.envelope());
            if (rereadCoverageFailure != null) {
                return contractViolation(root, workstreamId, nodeInstanceId, nodeDefinition, result, contract, ref, rereadCoverageFailure);
            }
            return withResultSidecars(
                    root, workstreamId, nodeInstanceId, nodeDefinition, inputRefs, result, contract,
                    parsed.envelope(), ref, path
            );
        }
        return contractViolation(workspaceRoot, workstreamId, nodeInstanceId, nodeDefinition, result, contract, lastSourceRef, lastInvalid);
    }

    private ContractViolation validateAgentResult(Path root, AgentResultEnvelope envelope) {
        if (envelope.result() instanceof AgentResultEnvelope.TaskContractProposalResult proposalResult) {
            var proposal = resultProjector.taskContractProposal(proposalResult);
            var errors = new TaskContractProposalValidator().validate(proposal, root);
            if (!errors.isEmpty()) {
                return ContractViolation.of(
                        "AGENT_RESULT_CONTRACT_VIOLATION",
                        "task_contract_proposal failed mechanical validation: " + errors,
                        Map.of("errors", errors)
                );
            }
        }
        return null;
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
            Path outputPath
    ) throws Exception {
        var document = resultProjector.outputContract(
                envelope, contract, nodeDefinition.handlerType(), nodeInstanceId, outputRef);
        var requiredReviewSubject = text(nodeDefinition.config().get("review_subject_artifact"));
        if (!requiredReviewSubject.isBlank()) {
            if (!"task_contract_proposal".equals(requiredReviewSubject)) {
                return contractViolation(root, workstreamId, nodeInstanceId, nodeDefinition, result, contract, outputRef,
                        ContractViolation.of("REVIEW_SUBJECT_UNSUPPORTED",
                                "unsupported review_subject_artifact: " + requiredReviewSubject,
                                Map.of("review_subject_artifact", requiredReviewSubject)));
            }
            var reviewSubject = TaskContractProposalReference.resolveFromRefs(
                    root, workstreamId, inputRefs, OBJECT_MAPPER);
            if (reviewSubject.isEmpty()) {
                return contractViolation(root, workstreamId, nodeInstanceId, nodeDefinition, result, contract, outputRef,
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
        if (envelope.result() instanceof AgentResultEnvelope.TaskContractProposalResult) {
            proposalReference = Optional.of(TaskContractProposalReference.publish(
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

        var explorationFailure = refreshExplorationPolicyProjection(root, result, validatedDocument);
        if (explorationFailure.isPresent()) {
            return contractViolation(root, workstreamId, nodeInstanceId, nodeDefinition, result, contract, outputRef,
                    explorationFailure.get());
        }

        var augmented = new LinkedHashMap<String, Object>();
        augmented.putAll(result.result());
        augmented.put("decision", envelope.result().decision());
        augmented.put("summary", envelope.result().summary());
        augmented.put("contract_sidecar_ref", sidecarRef);
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

    private static ContractViolation validateRereadCoverage(
            Path root,
            String workstreamId,
            NodeDefinition nodeDefinition,
            NodeResult result,
            AgentResultEnvelope envelope
    ) {
        if (!(envelope.result() instanceof AgentResultEnvelope.AgentOutputResult output)
                || workstreamId == null || workstreamId.isBlank()) {
            return null;
        }
        var actualReads = actualReadFiles(root, result);
        if (actualReads.isEmpty()) {
            return null;
        }
        var reusableReads = reusableReadFiles(root, workstreamId);
        if (reusableReads.isEmpty()) {
            return null;
        }
        var expectedEdits = allowedRereadFiles(root, workstreamId, nodeDefinition, output.changedFiles());
        var policy = RereadPolicyCalculator.compute(
                actualReads,
                reusableReads,
                invalidatedReadFiles(root, workstreamId),
                expectedEdits,
                allowedRereadFiles(root, workstreamId, nodeDefinition)
        );
        if (policy.denied().isEmpty()) {
            return null;
        }
            return ContractViolation.of(
                "REREAD_POLICY_DENIED",
                "agent re-read reusable files without an expected edit or invalidation: " + policy.denied()
                        + ". (Re-read justification is harness-computed; reread_reasons is supplementary, not the source of truth.)",
                Map.of("denied_reread_paths", policy.denied())
        );
    }

    private static List<String> actualReadFiles(Path root, NodeResult result) {
        var runMetadataRef = text(result.result().get("run_metadata"));
        if (runMetadataRef.isBlank()) {
            return List.of();
        }
        var path = root.resolve(runMetadataRef).normalize();
        if (!path.startsWith(root) || !Files.isRegularFile(path)) {
            return List.of();
        }
        try {
            var document = OBJECT_MAPPER.readValue(path.toFile(), MAP_TYPE);
            var toolUsage = document.get("toolUsage");
            if (!(toolUsage instanceof Map<?, ?> map)) {
                return List.of();
            }
            return stringList(map.get("readFiles")).stream()
                    .map(ArtifactContractValidator::normalizeRepoPath)
                    .filter(item -> !item.isBlank())
                    .distinct()
                    .toList();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private static Set<String> reusableReadFiles(Path root, String workstreamId) {
        return readReuseContractFiles(root, workstreamId, "reusableReadFiles");
    }

    private static Set<String> invalidatedReadFiles(Path root, String workstreamId) {
        return readReuseContractFiles(root, workstreamId, "invalidatedReadFiles");
    }

    private static Set<String> allowedRereadFiles(Path root, String workstreamId, NodeDefinition nodeDefinition) {
        return readReuseContractFiles(root, workstreamId, "allowedRereadFiles", nodeDefinition.id());
    }

    private static Set<String> readReuseContractFiles(Path root, String workstreamId, String key) {
        return readReuseContractFiles(root, workstreamId, key, "");
    }

    private static Set<String> readReuseContractFiles(Path root, String workstreamId, String key, String expectedTargetNode) {
        var path = root.resolve(".agent/workstreams").resolve(workstreamId)
                .resolve("memory/read-reuse-contract.json").normalize();
        if (!path.startsWith(root) || !Files.isRegularFile(path)) {
            return Set.of();
        }
        try {
            var document = OBJECT_MAPPER.readValue(path.toFile(), MAP_TYPE);
            if (!expectedTargetNode.isBlank() && !expectedTargetNode.equals(text(document.get("targetNodeDefinitionId")))) {
                return Set.of();
            }
            var value = document.get(key);
            if (!(value instanceof List<?> list)) {
                return Set.of();
            }
            var out = new LinkedHashSet<String>();
            for (var item : list) {
                if (item instanceof Map<?, ?> map) {
                    var normalized = normalizeRepoPath(text(map.get("path")));
                    if (!normalized.isBlank()) {
                        out.add(normalized);
                    }
                }
            }
            return out;
        } catch (Exception ignored) {
            return Set.of();
        }
    }

    private static Set<String> allowedRereadFiles(
            Path root,
            String workstreamId,
            NodeDefinition nodeDefinition,
            List<String> changedFiles
    ) {
        var allowed = new LinkedHashSet<String>();
        changedFiles.stream()
                .map(ArtifactContractValidator::normalizeRepoPath)
                .filter(path -> !path.isBlank())
                .forEach(allowed::add);
        if (!mayUsePlanArtifactForRereadPolicy(nodeDefinition)) {
            return allowed;
        }
        canonicalPlanArtifact(root, workstreamId).ifPresent(plan -> {
            plan.editFiles().stream()
                    .map(ArtifactContractValidator::normalizeRepoPath)
                    .filter(path -> !path.isBlank())
                    .forEach(allowed::add);
            plan.referenceFiles().stream()
                    .map(ArtifactContractValidator::normalizeRepoPath)
                    .filter(path -> !path.isBlank())
                    .forEach(allowed::add);
        });
        return allowed;
    }

    private static boolean mayUsePlanArtifactForRereadPolicy(NodeDefinition nodeDefinition) {
        var id = nodeDefinition.id();
        return id.startsWith("implement.") || "stage.claude_handoff".equals(id);
    }

    private static Optional<PlanArtifactDocument> canonicalPlanArtifact(Path root, String workstreamId) {
        if (workstreamId == null || workstreamId.isBlank()) {
            return Optional.empty();
        }
        var path = canonicalPlanArtifactPath(root, workstreamId);
        if (!path.startsWith(root) || !Files.isRegularFile(path)) {
            return Optional.empty();
        }
        return PlanArtifactDocument.read(path, OBJECT_MAPPER);
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
        ), terminalResultFor(reason, message), List.of(artifactResultFor(nodeDefinition, reason, message)), null);
    }

    private static AgentResultContractResult terminalResultFor(String reason, String message) {
        if (reason == null || reason.isBlank() || "AGENT_RESULT_MISSING".equals(reason)) {
            return AgentResultContractResult.missing();
        }
        return AgentResultContractResult.invalid(message);
    }

    private static ArtifactContractResult artifactResultFor(
            NodeDefinition nodeDefinition,
            String reason,
            String message
    ) {
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

    @SuppressWarnings("unchecked")
    private static Optional<ContractViolation> refreshExplorationPolicyProjection(
            Path workspaceRoot,
            NodeResult result,
            AgentOutputContractDocument validatedContract
    ) {
        var runMetadataRef = text(result.result().get("run_metadata"));
        if (runMetadataRef.isBlank()) {
            return Optional.empty();
        }
        var root = workspaceRoot.toAbsolutePath().normalize();
        var runMetadataPath = root.resolve(runMetadataRef).normalize();
        if (!runMetadataPath.startsWith(root) || !Files.isRegularFile(runMetadataPath)) {
            return Optional.empty();
        }
        try {
            var runJson = (ObjectNode) OBJECT_MAPPER.readTree(runMetadataPath.toFile());
            var governanceReportPath = resolveWorkspaceRef(
                    root,
                    firstNonBlank(
                            runJson.path("contextGovernanceActualReportPath").asText(""),
                            runJson.path("contextGovernanceReportPath").asText("")
                    )
            );
            if (governanceReportPath.isEmpty() || !Files.isRegularFile(governanceReportPath.get())) {
                return Optional.empty();
            }
            var report = (ObjectNode) OBJECT_MAPPER.readTree(governanceReportPath.get().toFile());
            var explorationPlanPath = resolveWorkspaceRef(root, report.path("agentExplorationPlanPath").asText(""));
            var explorationPlan = explorationPlanPath
                    .filter(Files::isRegularFile)
                    .map(path -> readMap(path).orElse(Map.<String, Object>of()))
                    .orElse(Map.of());
            var actualDirectReads = stringList(fieldOrLegacy(report, "actualDirectReadFiles", "actualReadFiles"));
            var actualCapabilities = capabilityKinds(report);
            var actualDirectReadKinds = stringMap(fieldOrLegacy(report, "actualDirectReadFileKinds", "actualReadFileKinds"));
            var actualCapabilityUsages = mapList(OBJECT_MAPPER.convertValue(report.path("actualCapabilityUsages"), Object.class));
            var refreshed = ExplorationPolicyEvaluator.evaluate(
                    explorationPlan,
                    actualDirectReads,
                    actualCapabilities,
                    validatedContract.scopeExpansionRequest().requestedPaths(),
                    actualDirectReadKinds,
                    actualCapabilityUsages
            );
            report.set("explorationPolicyResult", OBJECT_MAPPER.valueToTree(refreshed));
            OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(governanceReportPath.get().toFile(), report);

            var resultPath = resolveWorkspaceRef(root, report.path("explorationPolicyResultPath").asText(""));
            if (resultPath.isPresent()) {
                Files.createDirectories(resultPath.get().getParent());
                OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(resultPath.get().toFile(), refreshed);
            }
            runJson.set("explorationPolicyResult", OBJECT_MAPPER.valueToTree(refreshed));
            resultPath.ifPresent(path -> runJson.put("explorationPolicyResultPath", root.relativize(path).toString()));
            OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(runMetadataPath.toFile(), runJson);
            if (explorationPolicyFailed(refreshed)) {
            return Optional.of(ContractViolation.of(
                        "EXPLORATION_POLICY_DENIED",
                        "Agent exploration exceeded the typed exploration plan boundary.",
                        Map.of("explorationPolicyResult", refreshed)
                ));
            }
        } catch (Exception ignored) {
            // Contract validation remains authoritative. Exploration projection is
            // refreshed opportunistically after the validated sidecar exists.
        }
        return Optional.empty();
    }

    private static boolean explorationPolicyFailed(Map<String, Object> result) {
        return "failed".equals(text(result.get("status")));
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
