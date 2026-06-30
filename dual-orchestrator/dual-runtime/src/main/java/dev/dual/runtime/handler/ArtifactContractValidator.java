package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.dual.core.execution.AdapterSignal;
import dev.dual.core.execution.AdapterSignalType;
import dev.dual.core.execution.AgentExecutionAttempt;
import dev.dual.core.execution.AgentExecutionAttemptStatus;
import dev.dual.core.execution.AgentExecutionFinalizer;
import dev.dual.core.execution.AgentExecutionEvidence;
import dev.dual.core.execution.AgentExecutionOutcome;
import dev.dual.core.execution.AgentExecutionOutcomeResolver;
import dev.dual.core.execution.AgentExecutionProcessStatus;
import dev.dual.core.execution.AgentExecutionRetryPolicy;
import dev.dual.core.execution.ArtifactContractResult;
import dev.dual.core.execution.FinalizedAgentExecution;
import dev.dual.core.execution.ProcessResult;
import dev.dual.core.execution.ProcessUsability;
import dev.dual.core.execution.TerminalControlResult;
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
    private static final AgentExecutionOutcomeResolver OUTCOME_RESOLVER = new AgentExecutionOutcomeResolver();
    private static final AgentExecutionFinalizer EXECUTION_FINALIZER = new AgentExecutionFinalizer(OUTCOME_RESOLVER);
    private final DualControlBlockParser parser = new DualControlBlockParser();
    private final AgentArtifactEnvelopeParser artifactParser = new AgentArtifactEnvelopeParser();

    public NodeResult validate(Path workspaceRoot, NodeDefinition nodeDefinition, NodeResult result) throws Exception {
        return validate(workspaceRoot, "", "", nodeDefinition, result);
    }

    public NodeResult validate(
            Path workspaceRoot,
            String workstreamId,
            String nodeInstanceId,
            NodeDefinition nodeDefinition,
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
        return validateAgentOutput(workspaceRoot, workstreamId, nodeInstanceId, nodeDefinition, result);
    }

    private NodeResult validateAgentOutput(
            Path workspaceRoot,
            String workstreamId,
            String nodeInstanceId,
            NodeDefinition nodeDefinition,
            NodeResult result
    ) throws Exception {
        var root = workspaceRoot.toAbsolutePath().normalize();
        var contract = ContractRegistry.forNode(nodeDefinition.id());
        DualControlParseResult lastInvalid = null;
        String lastSourceRef = "";
        for (int i = result.outputRefs().size() - 1; i >= 0; i--) {
            var ref = result.outputRefs().get(i);
            if (ref.startsWith("artifact://")) {
                continue;
            }
            var path = root.resolve(ref).normalize();
            if (!path.startsWith(root) || !Files.isRegularFile(path)) {
                continue;
            }
            var markdown = Files.readString(path, StandardCharsets.UTF_8);
            var parsed = parser.parse(markdown, contract);
            if (parsed.valid()) {
                var structured = structuredArtifact(root, nodeDefinition, markdown, parsed);
                if (structured.failure() != null) {
                    return contractViolation(workspaceRoot, workstreamId, nodeInstanceId, nodeDefinition, result, contract, ref, structured.failure());
                }
                var planArtifactFailure = validatePlanArtifact(nodeDefinition, markdown);
                if (planArtifactFailure != null) {
                    return contractViolation(workspaceRoot, workstreamId, nodeInstanceId, nodeDefinition, result, contract, ref, planArtifactFailure);
                }
                var rereadCoverageFailure = validateRereadCoverage(root, workstreamId, nodeDefinition, result, structured);
                if (rereadCoverageFailure != null) {
                    return contractViolation(workspaceRoot, workstreamId, nodeInstanceId, nodeDefinition, result, contract, ref, rereadCoverageFailure);
                }
                return withContractSidecar(
                        root,
                        workstreamId,
                        nodeInstanceId,
                        nodeDefinition,
                        result,
                        contract,
                        parsed,
                        structured,
                        ref,
                        path,
                        markdown
                );
            }
            lastInvalid = parsed;
            lastSourceRef = ref;
            break;
        }
        return contractViolation(workspaceRoot, workstreamId, nodeInstanceId, nodeDefinition, result, contract, lastSourceRef, lastInvalid);
    }

    /**
     * Validates the typed {@code plan_artifact} envelope for {@code plan.claude_plan}.
     * This replaces Markdown-section parsing: the six required plan-body fields live
     * in the typed artifact (source of truth); Markdown plan prose is display-only.
     */
    private DualControlParseResult validatePlanArtifact(NodeDefinition nodeDefinition, String markdown) {
        if (!"plan.claude_plan".equals(nodeDefinition.id())) {
            return null;
        }
        var envelope = artifactParser.latest(markdown, "plan_artifact");
        if (envelope.isEmpty()) {
            return DualControlParseResult.invalid(
                    "PLAN_ARTIFACT_MISSING",
                    "plan.claude_plan requires a typed plan_artifact envelope. The Markdown plan sections are human-readable display only and are no longer the source of truth for plan body validation.",
                    Map.of()
            );
        }
        Map<String, Object> raw;
        try {
            raw = OBJECT_MAPPER.readValue(envelope.get().json(), MAP_TYPE);
        } catch (Exception parseFailure) {
            return DualControlParseResult.invalid(
                    "PLAN_ARTIFACT_PARSE_FAILED",
                    "plan.claude_plan plan_artifact envelope could not be parsed: " + parseFailure.getMessage(),
                    Map.of()
            );
        }
        var missing = PlanArtifactDocument.from(raw).missingRequiredField();
        if (missing.isPresent()) {
            return DualControlParseResult.invalid(
                    "PLAN_ARTIFACT_BODY_MISSING",
                    "plan.claude_plan plan_artifact is missing required field: " + missing.get(),
                    Map.of("missing_field", missing.get())
            );
        }
        return null;
    }

    private static DualControlParseResult validateRereadCoverage(
            Path root,
            String workstreamId,
            NodeDefinition nodeDefinition,
            NodeResult result,
            StructuredArtifactResult structured
    ) {
        if (!(structured.artifact() instanceof AgentOutputArtifact artifact) || workstreamId == null || workstreamId.isBlank()) {
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
        var invalidatedReads = invalidatedReadFiles(root, workstreamId);
        var expectedEdits = allowedRereadFiles(root, workstreamId, nodeDefinition, artifact);
        var contractAllowedReads = allowedRereadFiles(root, workstreamId, nodeDefinition);
        // Harness-computed policy: justified by expected edit/reference plan,
        // invalidation, or typed read-reuse contract allowance only.
        // agent_output_contract.reread_reasons is a supplementary explanation, NOT the justification.
        var policy = RereadPolicyCalculator.compute(
                actualReads,
                reusableReads,
                invalidatedReads,
                expectedEdits,
                contractAllowedReads
        );
        if (policy.denied().isEmpty()) {
            return null;
        }
        return DualControlParseResult.invalid(
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

    /**
     * Objective allowed-reread set: files this run is entitled to re-read because
     * it either edits them now ({@code changed_files}) or the typed plan declared
     * them as implementation edit/reference files. Task scope is intentionally
     * excluded — scope is user intent, not a reread permission boundary.
     *
     * <p>The canonical plan artifact is an orchestrator-owned read model written
     * only after {@code plan.claude_plan} passes contract validation. Do not scan
     * historical run folders or infer from Markdown prose.</p>
     */
    private static Set<String> allowedRereadFiles(
            Path root,
            String workstreamId,
            NodeDefinition nodeDefinition,
            AgentOutputArtifact artifact
    ) {
        var allowed = new LinkedHashSet<String>();
        artifact.changedFiles().stream()
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

    private static NodeResult withContractSidecar(
            Path root,
            String workstreamId,
            String nodeInstanceId,
            NodeDefinition nodeDefinition,
            NodeResult result,
            NodeOutputContract contract,
            DualControlParseResult parsed,
            StructuredArtifactResult structured,
            String outputRef,
            Path outputPath,
            String markdown
    ) throws Exception {
        var block = parsed.block();
        var sidecarPath = AgentOutputContractSidecar.pathFor(outputPath);
        var sidecarRef = root.relativize(sidecarPath).toString();
        var document = new LinkedHashMap<String, Object>();
        document.put("artifact_type", "agent_output_contract");
        document.put("node_id", nodeDefinition.id());
        document.put("handler_type", nodeDefinition.handlerType());
        document.put("decision", block.required("decision"));
        document.put("summary", block.required("summary"));
        document.put("required_keys", List.copyOf(contract.requiredKeys()));
        document.put("allowed_decisions", List.copyOf(contract.allowedDecisions()));
        document.put("source_output_ref", outputRef);
        document.put("generated_at", Instant.now().toString());
        document.put("control", block.values());
        document.put("control_source", parsed.details().getOrDefault("control_source", Map.of()));
        addEvidenceFields(document, structured, markdown);
        AgentContractSemantics.addSidecarFields(document);
        var semanticFailure = AgentContractSemantics.validateOutputContract(AgentOutputContractDocument.from(document));
        if (semanticFailure.isPresent()) {
            return contractViolation(root, workstreamId, nodeInstanceId, nodeDefinition, result, contract, outputRef, semanticFailure.get());
        }
        Files.writeString(
                sidecarPath,
                OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(document) + "\n",
                StandardCharsets.UTF_8
        );

        var outputRefs = new ArrayList<>(result.outputRefs());
        if (!outputRefs.contains(sidecarRef)) {
            outputRefs.add(sidecarRef);
        }
        var planArtifactRef = writePlanArtifactSidecarIfPresent(
                root,
                workstreamId,
                nodeInstanceId,
                nodeDefinition,
                outputPath,
                outputRef,
                markdown
        );
        planArtifactRef.ifPresent(ref -> {
            if (!outputRefs.contains(ref)) {
                outputRefs.add(ref);
            }
        });
        var explorationFailure = refreshExplorationPolicyProjection(root, result, AgentOutputContractDocument.from(document));
        if (explorationFailure.isPresent()) {
            return contractViolation(root, workstreamId, nodeInstanceId, nodeDefinition, result, contract, outputRef,
                    explorationFailure.get());
        }
        var augmented = new LinkedHashMap<String, Object>();
        augmented.putAll(result.result());
        augmented.put("decision", block.required("decision"));
        augmented.put("summary", block.required("summary"));
        augmented.put("contract_sidecar_ref", sidecarRef);
        planArtifactRef.ifPresent(ref -> augmented.put("plan_artifact_ref", ref));
        var outcome = OUTCOME_RESOLVER.resolve(new AgentExecutionEvidence(
                processResult(result),
                TerminalControlResult.valid(block.required("decision")),
                List.of(ArtifactContractResult.requiredValid(requiredArtifactTypeOrDefault(nodeDefinition))),
                adapterSignals(result)
        ));
        augmented.put("agent_execution_outcome", outcomeToMap(outcome));
        updateRunMetadata(
                root,
                result,
                TerminalControlResult.valid(block.required("decision")),
                List.of(ArtifactContractResult.requiredValid(requiredArtifactTypeOrDefault(nodeDefinition))),
                document.get("reread_reasons")
        );
        return new NodeResult(
                outcome.nodeMaySucceed() ? result.outcome() : outcome.primaryReason(),
                nodeStatusFor(outcome),
                outputRefs,
                augmented,
                outcome.nodeMaySucceed() ? result.errorMessage() : outcome.operatorMessage()
        );
    }

    private static Optional<String> writePlanArtifactSidecarIfPresent(
            Path root,
            String workstreamId,
            String nodeInstanceId,
            NodeDefinition nodeDefinition,
            Path outputPath,
            String outputRef,
            String markdown
    ) throws Exception {
        if (!"plan.claude_plan".equals(nodeDefinition.id())) {
            return Optional.empty();
        }
        var envelope = new AgentArtifactEnvelopeParser().latest(markdown, "plan_artifact");
        if (envelope.isEmpty()) {
            return Optional.empty();
        }
        var raw = OBJECT_MAPPER.readValue(envelope.get().json(), MAP_TYPE);
        var sidecarPath = PlanArtifactSidecar.pathFor(outputPath);
        Files.writeString(
                sidecarPath,
                OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(raw) + "\n",
                StandardCharsets.UTF_8
        );
        if (workstreamId != null && !workstreamId.isBlank()) {
            var canonicalPath = canonicalPlanArtifactPath(root, workstreamId);
            Files.createDirectories(canonicalPath.getParent());
            var canonical = new LinkedHashMap<String, Object>();
            canonical.putAll(raw);
            canonical.put("workstream_id", workstreamId);
            canonical.put("node_instance_id", nodeInstanceId == null ? "" : nodeInstanceId);
            canonical.put("source_output_ref", outputRef == null ? "" : outputRef);
            canonical.put("generated_at", Instant.now().toString());
            canonical.put("canonical_source", "plan.claude_plan");
            Files.writeString(
                    canonicalPath,
                    OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(canonical) + "\n",
                    StandardCharsets.UTF_8
            );
        }
        return Optional.of(root.relativize(sidecarPath).toString());
    }

    private StructuredArtifactResult structuredArtifact(
            Path workspaceRoot,
            NodeDefinition nodeDefinition,
            String markdown,
            DualControlParseResult parsed
    ) {
        var expectedType = requiredArtifactType(nodeDefinition);
        if (expectedType == null) {
            return StructuredArtifactResult.none();
        }
        var envelope = artifactParser.latest(markdown, expectedType);
        if (envelope.isEmpty()) {
            return StructuredArtifactResult.failed(DualControlParseResult.invalid(
                    "STRUCTURED_ARTIFACT_MISSING",
                    "Required structured artifact missing: " + expectedType,
                    Map.of("required_artifact_type", expectedType)
            ));
        }
        try {
            var rawDocument = OBJECT_MAPPER.readValue(envelope.get().json(), MAP_TYPE);
            var structuredDocument = AgentStructuredArtifactDocument.from(rawDocument);
            var artifact = toStructuredArtifact(
                    expectedType,
                    structuredDocument,
                    parsed.block().required("decision"),
                    parsed.block().required("summary")
            );
            if (!expectedType.equals(artifact.artifactType())) {
                return StructuredArtifactResult.failed(DualControlParseResult.invalid(
                        "STRUCTURED_ARTIFACT_CONTRACT_VIOLATION",
                        "structured artifact has wrong artifact_type: " + artifact.artifactType(),
                        Map.of("required_artifact_type", expectedType, "actual_artifact_type", artifact.artifactType())
                ));
            }
            var decision = artifact.decision();
            var controlDecision = parsed.block().required("decision");
            if (decision.isBlank()) {
                return StructuredArtifactResult.failed(DualControlParseResult.invalid(
                        "STRUCTURED_ARTIFACT_CONTRACT_VIOLATION",
                        "structured artifact missing required field: decision",
                        Map.of("required_artifact_type", expectedType)
                ));
            }
            if (!controlDecision.equals(decision)) {
                return StructuredArtifactResult.failed(DualControlParseResult.invalid(
                        "CONTROL_ARTIFACT_DECISION_MISMATCH",
                        "CONTROL_ARTIFACT_DECISION_MISMATCH: dual-control decision " + controlDecision
                                + " differs from " + expectedType + " decision " + decision,
                        Map.of("control_decision", controlDecision, "artifact_decision", decision)
                ));
            }
            if (artifact.summary().isBlank()) {
                return StructuredArtifactResult.failed(DualControlParseResult.invalid(
                        "STRUCTURED_ARTIFACT_CONTRACT_VIOLATION",
                        "structured artifact missing required field: summary",
                        Map.of("required_artifact_type", expectedType)
                ));
            }
            var semanticFailure = validateStructuredArtifact(expectedType, structuredDocument, rawDocument, workspaceRoot);
            if (semanticFailure.isPresent()) {
                return StructuredArtifactResult.failed(semanticFailure.get());
            }
            var rereadFailure = AgentContractSemantics.validateRereadReasons(rawDocument.get("reread_reasons"));
            if (rereadFailure.isPresent()) {
                return StructuredArtifactResult.failed(rereadFailure.get());
            }
            var scopeExpansionFailure = AgentContractSemantics.validateScopeExpansionRequest(rawDocument.get("scope_expansion_request"));
            if (scopeExpansionFailure.isPresent()) {
                return StructuredArtifactResult.failed(scopeExpansionFailure.get());
            }
            return StructuredArtifactResult.valid(expectedType, artifact, structuredDocument.lifecycleStatus());
        } catch (Exception e) {
            return StructuredArtifactResult.failed(DualControlParseResult.invalid(
                    "STRUCTURED_ARTIFACT_PARSE_FAILED",
                    "structured artifact JSON could not be parsed: " + e.getMessage(),
                    Map.of("required_artifact_type", expectedType)
            ));
        }
    }

    private static Optional<DualControlParseResult> validateStructuredArtifact(
            String expectedType,
            AgentStructuredArtifactDocument structuredDocument,
            Map<String, Object> rawDocument,
            Path workspaceRoot
    ) {
        if ("task_contract_proposal".equals(expectedType)) {
            try {
                var proposal = OBJECT_MAPPER.convertValue(rawDocument, TaskContractProposal.class);
                var errors = new TaskContractProposalValidator().validate(proposal, workspaceRoot);
                if (!errors.isEmpty()) {
                    return Optional.of(DualControlParseResult.invalid(
                            "STRUCTURED_ARTIFACT_CONTRACT_VIOLATION",
                            "task_contract_proposal failed mechanical validation: " + errors,
                            Map.of("errors", errors)
                    ));
                }
                return Optional.empty();
            } catch (IllegalArgumentException e) {
                return Optional.of(DualControlParseResult.invalid(
                        "STRUCTURED_ARTIFACT_PARSE_FAILED",
                        "task_contract_proposal JSON could not be parsed: " + e.getMessage(),
                        Map.of("required_artifact_type", expectedType)
                ));
            }
        }
        return AgentContractSemantics.validateStructuredArtifact(expectedType, structuredDocument);
    }

    private static String requiredArtifactType(NodeDefinition nodeDefinition) {
        var configured = text(nodeDefinition.config().get("expected_artifact"));
        if (!configured.isBlank()) {
            return configured;
        }
        return switch (nodeDefinition.id()) {
            case "implement.claude_self_test_report" -> "self_test_report";
            case "acceptance.codex_verifier_review" -> "acceptance_review";
            case "stage.claude_handoff" -> "stage_handoff";
            default -> ContractRegistry.hasContract(nodeDefinition.id()) ? "agent_output_contract" : null;
        };
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static StructuredAgentArtifact toStructuredArtifact(
            String expectedType,
            AgentStructuredArtifactDocument document,
            String controlDecision,
            String controlSummary
    ) {
        return switch (expectedType) {
            case "task_contract_proposal" -> new TaskContractProposalArtifact(
                    document.artifactType(),
                    controlDecision,
                    controlSummary
            );
            case "agent_output_contract" -> new AgentOutputArtifact(
                    document.artifactType(),
                    document.decision(),
                    document.summary(),
                    document.blocking(),
                    document.checks(),
                    document.evidence(),
                    document.missingEvidence(),
                    document.mustFix(),
                    document.acceptanceNotes(),
                    document.openRisks(),
                    document.changedFiles(),
                    document.verificationSummary(),
                    document.failure(),
                    document.rereadReasons(),
                    document.scopeExpansionRequest()
            );
            case "self_test_report" -> new SelfTestReportArtifact(
                    document.artifactType(),
                    document.decision(),
                    document.summary(),
                    document.checksRun(),
                    document.checksSkipped(),
                    document.evidence(),
                    document.missingEvidence(),
                    document.acceptanceNotes(),
                    document.verificationSummary()
            );
            case "acceptance_review" -> new AcceptanceReviewArtifact(
                    document.artifactType(),
                    document.decision(),
                    document.summary(),
                    document.blocking(),
                    document.checks(),
                    document.evidence(),
                    document.missingEvidence(),
                    document.mustFix(),
                    document.acceptanceNotes(),
                    document.openRisks(),
                    document.changedFiles(),
                    document.verificationSummary(),
                    document.failure()
            );
            case "stage_handoff" -> new StageHandoffArtifact(
                    document.artifactType(),
                    document.decision(),
                    document.summary(),
                    document.completedWork(),
                    document.importantDecisions(),
                    document.changedFiles(),
                    document.verificationState(),
                    document.openRisks(),
                    document.recommendedNextStage(),
                    document.suggestedNextPrompt()
            );
            default -> throw new IllegalArgumentException("unsupported structured artifact type: " + expectedType);
        };
    }

    private static void addEvidenceFields(
            Map<String, Object> document,
            StructuredArtifactResult structured,
            String markdown
    ) {
        if (structured.artifact() != null) {
            document.put("source_artifact_type", structured.type());
            structured.artifact().writeSidecarFields(document);
            if (!structured.declaredLifecycleStatus().isBlank()) {
                document.put("lifecycle_status", structured.declaredLifecycleStatus());
            }
            return;
        }
        document.put("must_fix", MarkdownArtifactFields.listSection(
                markdown,
                "Must Fix",
                "Gaps / Must Fix",
                "Must Fix Before Implementation"
        ));
        document.put("open_risks", MarkdownArtifactFields.listSection(
                markdown,
                "Open Risks",
                "Residual Risks",
                "Residual Risk",
                "Risks",
                "Product Risks",
                "Architecture Risks",
                "Scope Risks",
                "Test Coverage Risks",
                "Known Risks"
        ));
        document.put("changed_files", MarkdownArtifactFields.listSection(markdown, "Changed Files"));
        document.put("checks_run", MarkdownArtifactFields.listSection(
                markdown,
                "Checks Run",
                "Commands Run",
                "Manual Checks",
                "Validation Performed"
        ));
        document.put("checks_skipped", MarkdownArtifactFields.listSection(
                markdown,
                "Checks Skipped",
                "Failed / Skipped Checks",
                "Skipped Checks",
                "Not Tested"
        ));
        document.put("evidence", MarkdownArtifactFields.listSection(
                markdown,
                "Evidence",
                "Acceptance Evidence",
                "Verification Evidence"
        ));
        document.put("missing_evidence", MarkdownArtifactFields.listSection(
                markdown,
                "Missing Evidence",
                "Missing Tests",
                "Verification Gaps",
                "Evidence Gaps"
        ));
        document.put("acceptance_notes", MarkdownArtifactFields.listSection(
                markdown,
                "Acceptance Notes",
                "Notes",
                "Non-blocking Notes"
        ));
        document.put("verification_summary", MarkdownArtifactFields.textSection(
                markdown,
                "Verification Summary",
                "Verification State",
                "Verification Adequacy",
                "Self-Test Performed"
        ));
    }

    private static NodeResult contractViolation(
            Path workspaceRoot,
            String workstreamId,
            String nodeInstanceId,
            NodeDefinition nodeDefinition,
            NodeResult result,
            NodeOutputContract contract,
            String sourceOutputRef,
            DualControlParseResult lastInvalid
    ) throws Exception {
        var reason = lastInvalid == null || lastInvalid.isMissing()
                ? "MISSING_DUAL_CONTROL"
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
        var outcome = OUTCOME_RESOLVER.resolve(new AgentExecutionEvidence(
                processResult(result),
                terminalResultFor(reason, message),
                List.of(artifactResultFor(nodeDefinition, reason, message)),
                adapterSignals(result)
        ));
        failed.put("agent_execution_outcome", outcomeToMap(outcome));
        updateRunMetadata(
                workspaceRoot,
                result,
                terminalResultFor(reason, message),
                List.of(artifactResultFor(nodeDefinition, reason, message)),
                null
        );
        return new NodeResult(
                "AGENT_OUTPUT_CONTRACT_VIOLATION",
                NodeStatus.FAILED,
                outputRefs,
                failed,
                message
        );
    }

    private static ProcessResult processResult(NodeResult result) {
        var usability = processUsability(result);
        var exitCode = result.result().get("exit_code");
        if (exitCode instanceof Number number && number.intValue() != 0) {
            if (usability == ProcessUsability.EXITED_NON_ZERO_WITH_COMPLETE_OUTPUT) {
                return ProcessResult.nonZeroComplete(number.intValue());
            }
            if (usability == ProcessUsability.KILLED_AFTER_RECOVERABLE_SIGNAL) {
                return ProcessResult.killedAfterRecoverableSignal();
            }
            return ProcessResult.nonZeroIncomplete(number.intValue());
        }
        if (usability == ProcessUsability.INTERRUPTED) {
            return ProcessResult.interruptedProcess();
        }
        if (usability == ProcessUsability.TIMED_OUT || usability == ProcessUsability.STALLED) {
            return ProcessResult.timedOutProcess();
        }
        return ProcessResult.exitedZero();
    }

    private static ProcessUsability processUsability(NodeResult result) {
        var explicit = text(result.result().get("process_usability"));
        if (!explicit.isBlank()) {
            try {
                return ProcessUsability.valueOf(explicit);
            } catch (IllegalArgumentException ignored) {
                // Fall back to legacy inference below for historical ledgers.
            }
        }
        var reason = text(result.result().get("acpx_failure.reason"));
        if ("RECOVERABLE_AGENT_OUTPUT_INTERRUPTED".equals(reason)) {
            return ProcessUsability.INTERRUPTED;
        }
        var exitCode = result.result().get("exit_code");
        if (exitCode instanceof Number number && number.intValue() != 0) {
            return result.status() == NodeStatus.COMPLETED
                    ? ProcessUsability.EXITED_NON_ZERO_WITH_COMPLETE_OUTPUT
                    : ProcessUsability.EXITED_NON_ZERO_WITH_INCOMPLETE_OUTPUT;
        }
        return ProcessUsability.EXITED_ZERO;
    }

    private static TerminalControlResult terminalResultFor(String reason, String message) {
        if (reason == null || reason.isBlank() || "MISSING_DUAL_CONTROL".equals(reason)) {
            return TerminalControlResult.missing();
        }
        if (reason.startsWith("STRUCTURED_") || "CONTROL_ARTIFACT_DECISION_MISMATCH".equals(reason)) {
            return TerminalControlResult.valid("UNKNOWN");
        }
        return TerminalControlResult.invalid(message);
    }

    private static ArtifactContractResult artifactResultFor(
            NodeDefinition nodeDefinition,
            String reason,
            String message
    ) {
        if (reason != null && (reason.startsWith("STRUCTURED_") || "CONTROL_ARTIFACT_DECISION_MISMATCH".equals(reason))) {
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

    private static String requiredArtifactTypeOrDefault(NodeDefinition nodeDefinition) {
        var required = requiredArtifactType(nodeDefinition);
        return required == null || required.isBlank() ? "agent_output_contract" : required;
    }

    private static List<AdapterSignal> adapterSignals(NodeResult result) {
        var reason = text(result.result().get("acpx_failure.reason"));
        if (reason.isBlank()) {
            return List.of();
        }
        var type = switch (reason) {
            case "RECOVERABLE_AGENT_RECONNECT" -> AdapterSignalType.AGENT_RECONNECT_REQUIRED;
            case "RECOVERABLE_SESSION_BOOTSTRAP_FAILED" -> AdapterSignalType.SESSION_BOOTSTRAP_FAILED;
            case "RECOVERABLE_SESSION_MISSING" -> AdapterSignalType.SESSION_RESUME_FAILED;
            case "RECOVERABLE_AGENT_AUTH_FAILED" -> AdapterSignalType.AUTH_FAILED;
            case "RECOVERABLE_RATE_LIMIT", "RECOVERABLE_AGENT_OVERLOADED" -> AdapterSignalType.RATE_LIMITED;
            case "RECOVERABLE_CONTEXT_WINDOW" -> AdapterSignalType.CONTEXT_WINDOW_EXCEEDED;
            case "RECOVERABLE_AGENT_PERMISSION_BLOCKED" -> AdapterSignalType.PERMISSION_BLOCKED;
            case "RECOVERABLE_AGENT_OUTPUT_INTERRUPTED" -> AdapterSignalType.PROCESS_STALLED;
            default -> null;
        };
        return type == null ? List.of() : List.of(new AdapterSignal(type, reason, 0));
    }

    private static Map<String, Object> outcomeToMap(AgentExecutionOutcome outcome) {
        var map = new LinkedHashMap<String, Object>();
        map.put("status", outcome.status().name());
        map.put("primaryReason", outcome.primaryReason());
        map.put("nodeMaySucceed", outcome.nodeMaySucceed());
        map.put("retryPolicy", outcome.retryPolicy().name());
        map.put("retrySafe", outcome.retryPolicy() != AgentExecutionRetryPolicy.NONE);
        map.put("recoveryCategory", outcome.recoveryCategory().name());
        map.put("operatorMessage", outcome.operatorMessage());
        map.put("secondarySignals", outcome.secondarySignals().stream()
                .map(signal -> Map.of(
                        "type", signal.type().name(),
                        "severity", signal.type().severity().name(),
                        "message", signal.message(),
                        "observedSequence", signal.observedSequence(),
                        "source", signal.source(),
                        "sessionId", signal.sessionId(),
                        "superseded", signal.superseded()
                ))
                .toList());
        return map;
    }

    private static void updateRunMetadata(
            Path workspaceRoot,
            NodeResult result,
            TerminalControlResult terminalControlResult,
            List<ArtifactContractResult> artifactContractResults,
            Object rereadReasons
    ) {
        var runMetadataRef = text(result.result().get("run_metadata"));
        if (runMetadataRef.isBlank()) {
            return;
        }
        var root = workspaceRoot.toAbsolutePath().normalize();
        var path = root.resolve(runMetadataRef).normalize();
        if (!path.startsWith(root) || !Files.isRegularFile(path)) {
            return;
        }
        try {
            var json = (ObjectNode) OBJECT_MAPPER.readTree(path.toFile());
            var processResult = mergedProcessResult(json, processResult(result));
            var evidence = new AgentExecutionEvidence(
                    processResult(result),
                    terminalControlResult,
                    artifactContractResults,
                    adapterSignals(result)
            );
            var finalized = EXECUTION_FINALIZER.finalize(baseAttempt(json, result), evidence);
            json.put("schemaVersion", Math.max(json.path("schemaVersion").asInt(0), 3));
            json.put("status", finalized.legacyStatus());
            json.put("failureReason", finalized.legacyFailureReason());
            json.put("recoverable", finalized.legacyRecoverable());
            json.set("processResult", OBJECT_MAPPER.valueToTree(processResult));
            json.set("adapterSignals", OBJECT_MAPPER.valueToTree(adapterSignals(result).stream()
                    .map(ArtifactContractValidator::adapterSignalToMap)
                    .toList()));
            json.set("terminalControlResult", OBJECT_MAPPER.valueToTree(terminalControlToMap(terminalControlResult)));
            json.set("artifactContractResults", OBJECT_MAPPER.valueToTree(artifactContractResults.stream()
                    .map(ArtifactContractValidator::artifactContractToMap)
                    .toList()));
            if (rereadReasons != null) {
                json.set("reread_reasons", OBJECT_MAPPER.valueToTree(rereadReasons));
            }
            json.set("executionAttempt", OBJECT_MAPPER.valueToTree(executionAttemptToMap(finalized.attempt())));
            json.set("executionOutcome", OBJECT_MAPPER.valueToTree(outcomeToMap(finalized.outcome())));
            OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), json);
        } catch (Exception ignored) {
            // Run metadata is an observability artifact. Contract validation result
            // remains authoritative even if the historical run file is damaged.
        }
    }

    @SuppressWarnings("unchecked")
    private static Optional<DualControlParseResult> refreshExplorationPolicyProjection(
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
                return Optional.of(DualControlParseResult.invalid(
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

    private static NodeStatus nodeStatusFor(AgentExecutionOutcome outcome) {
        return outcome.nodeMaySucceed() ? NodeStatus.COMPLETED : NodeStatus.FAILED;
    }

    private sealed interface StructuredAgentArtifact permits
            TaskContractProposalArtifact,
            AgentOutputArtifact,
            SelfTestReportArtifact,
            AcceptanceReviewArtifact,
            StageHandoffArtifact {
        String artifactType();

        String decision();

        String summary();

        void writeSidecarFields(Map<String, Object> document);
    }

    private record TaskContractProposalArtifact(
            String artifactType,
            String decision,
            String summary
    ) implements StructuredAgentArtifact {
        @Override
        public void writeSidecarFields(Map<String, Object> document) {
            document.put("must_fix", List.of());
            document.put("open_risks", List.of());
            document.put("changed_files", List.of());
            document.put("checks_run", List.of("task_contract_proposal mechanical validation"));
            document.put("checks_skipped", List.of());
            document.put("evidence", List.of("canonical task_contract_proposal artifact"));
            document.put("missing_evidence", List.of());
            document.put("acceptance_notes", List.of());
            document.put("verification_summary", "Task contract proposal emitted and mechanically validated.");
            document.put("blocking", false);
            document.put("failure", AgentOutputContractDocument.FailureFields.empty().toMap());
        }
    }

    private record SelfTestReportArtifact(
            String artifactType,
            String decision,
            String summary,
            List<String> checksRun,
            List<String> checksSkipped,
            List<String> evidence,
            List<String> missingEvidence,
            List<String> acceptanceNotes,
            String verificationSummary
    ) implements StructuredAgentArtifact {
        @Override
        public void writeSidecarFields(Map<String, Object> document) {
            document.put("must_fix", List.of());
            document.put("open_risks", List.of());
            document.put("changed_files", List.of());
            document.put("checks_run", checksRun);
            document.put("checks_skipped", checksSkipped);
            document.put("evidence", evidence);
            document.put("missing_evidence", missingEvidence);
            document.put("acceptance_notes", acceptanceNotes);
            document.put("verification_summary", verificationSummary);
            document.put("blocking", false);
            document.put("failure", AgentOutputContractDocument.FailureFields.empty().toMap());
        }
    }

    private record AgentOutputArtifact(
            String artifactType,
            String decision,
            String summary,
            boolean blocking,
            List<String> checks,
            List<String> evidence,
            List<String> missingEvidence,
            List<String> mustFix,
            List<String> acceptanceNotes,
            List<String> openRisks,
            List<String> changedFiles,
            String verificationSummary,
            AgentOutputContractDocument.FailureFields failure,
            List<AgentOutputContractDocument.RereadReason> rereadReasons,
            AgentOutputContractDocument.ScopeExpansionRequest scopeExpansionRequest
    ) implements StructuredAgentArtifact {
        @Override
        public void writeSidecarFields(Map<String, Object> document) {
            document.put("must_fix", mustFix);
            document.put("open_risks", openRisks);
            document.put("changed_files", changedFiles);
            document.put("checks_run", checks);
            document.put("checks_skipped", List.of());
            document.put("evidence", evidence);
            document.put("missing_evidence", missingEvidence);
            document.put("acceptance_notes", acceptanceNotes);
            document.put("verification_summary", verificationSummary);
            document.put("blocking", blocking);
            document.put("failure", failure.toMap());
            document.put("reread_reasons", rereadReasons.stream()
                    .map(AgentOutputContractDocument.RereadReason::toMap).toList());
            if (!scopeExpansionRequest.isEmpty()) {
                document.put("scope_expansion_request", scopeExpansionRequest.toMap());
            }
        }
    }

    private record AcceptanceReviewArtifact(
            String artifactType,
            String decision,
            String summary,
            boolean blocking,
            List<String> checks,
            List<String> evidence,
            List<String> missingEvidence,
            List<String> mustFix,
            List<String> acceptanceNotes,
            List<String> openRisks,
            List<String> changedFiles,
            String verificationSummary,
            AgentOutputContractDocument.FailureFields failure
    ) implements StructuredAgentArtifact {
        @Override
        public void writeSidecarFields(Map<String, Object> document) {
            document.put("must_fix", mustFix);
            document.put("open_risks", openRisks);
            document.put("changed_files", changedFiles);
            document.put("checks_run", checks);
            document.put("checks_skipped", List.of());
            document.put("evidence", evidence);
            document.put("missing_evidence", missingEvidence);
            document.put("acceptance_notes", acceptanceNotes);
            document.put("verification_summary", verificationSummary);
            document.put("blocking", blocking);
            document.put("failure", failure.toMap());
        }
    }

    private record StageHandoffArtifact(
            String artifactType,
            String decision,
            String summary,
            List<String> completedWork,
            List<String> importantDecisions,
            List<String> changedFiles,
            String verificationState,
            List<String> openRisks,
            String recommendedNextStage,
            String suggestedNextPrompt
    ) implements StructuredAgentArtifact {
        @Override
        public void writeSidecarFields(Map<String, Object> document) {
            document.put("must_fix", List.of());
            document.put("open_risks", openRisks);
            document.put("changed_files", changedFiles);
            document.put("checks_run", List.of());
            document.put("checks_skipped", List.of());
            document.put("evidence", completedWork);
            document.put("missing_evidence", List.of());
            document.put("acceptance_notes", List.of());
            document.put("verification_summary", verificationState);
            document.put("blocking", false);
            document.put("failure", AgentOutputContractDocument.FailureFields.empty().toMap());
            document.put("completed_work", completedWork);
            document.put("important_decisions", importantDecisions);
            document.put("recommended_next_stage", recommendedNextStage);
            document.put("suggested_next_prompt", suggestedNextPrompt);
        }
    }

    private record StructuredArtifactResult(
            String type,
            StructuredAgentArtifact artifact,
            String declaredLifecycleStatus,
            DualControlParseResult failure
    ) {
        static StructuredArtifactResult none() {
            return new StructuredArtifactResult("", null, "", null);
        }

        static StructuredArtifactResult valid(String type, StructuredAgentArtifact artifact, String declaredLifecycleStatus) {
            return new StructuredArtifactResult(type, artifact, declaredLifecycleStatus == null ? "" : declaredLifecycleStatus, null);
        }

        static StructuredArtifactResult failed(DualControlParseResult failure) {
            return new StructuredArtifactResult("", null, "", failure);
        }
    }

    private static Map<String, Object> processResultToMap(ProcessResult process) {
        var map = new LinkedHashMap<String, Object>();
        map.put("command", process.command());
        map.put("pid", process.pid());
        map.put("exitCode", process.exitCode());
        map.put("usability", process.usability().name());
        map.put("durationMs", process.durationMs());
        map.put("stdoutPath", process.stdoutPath());
        map.put("stderrPath", process.stderrPath());
        map.put("interrupted", process.interrupted());
        map.put("timedOut", process.timedOut());
        return map;
    }

    private static AgentExecutionAttempt baseAttempt(ObjectNode json, NodeResult result) {
        var attempt = json.path("executionAttempt");
        var runId = firstNonBlank(json.path("runId").asText(""), text(result.result().get("run_id")), "run-unknown");
        return new AgentExecutionAttempt(
                firstNonBlank(attempt.path("attemptId").asText(""), "att-" + runId),
                runId,
                firstNonBlank(json.path("workstreamId").asText(""), attempt.path("workstreamId").asText("")),
                firstNonBlank(json.path("nodeId").asText(""), attempt.path("nodeId").asText("")),
                firstNonBlank(json.path("agent").asText(""), attempt.path("agent").asText(""), text(result.result().get("agent"))),
                enumValue(AgentExecutionAttemptStatus.class, attempt.path("status").asText(""),
                        AgentExecutionAttemptStatus.PROCESS_EXITED),
                enumValue(AgentExecutionProcessStatus.class, attempt.path("processStatus").asText(""),
                        AgentExecutionProcessStatus.EXITED),
                enumValue(AdapterSignalType.class, attempt.path("failureSignal").asText(""), null),
                attempt.path("recoverable").asBoolean(false),
                attempt.path("retrySafe").asBoolean(false),
                firstNonBlank(attempt.path("message").asText(""), "process exited"),
                attempt.path("stderrPath").asText(""),
                attempt.path("transcriptPath").asText(""),
                attempt.path("sessionName").asText(""),
                attempt.path("sessionId").asText(""),
                attempt.path("sessionScope").asText(""),
                attempt.path("sessionSegmentKey").asText(""),
                instantOrNull(attempt.path("startedAt").asText("")),
                instantOrNull(attempt.path("finishedAt").asText(""))
        );
    }

    private static Map<String, Object> executionAttemptToMap(AgentExecutionAttempt attempt) {
        var map = new LinkedHashMap<String, Object>();
        map.put("attemptId", attempt.attemptId());
        map.put("runId", attempt.runId());
        map.put("workstreamId", attempt.workstreamId());
        map.put("nodeId", attempt.nodeId());
        map.put("agent", attempt.agent());
        map.put("status", attempt.status().name());
        map.put("processStatus", attempt.processStatus().name());
        map.put("failureSignal", attempt.failureSignal() == null ? null : attempt.failureSignal().name());
        map.put("recoverable", attempt.recoverable());
        map.put("retrySafe", attempt.retrySafe());
        map.put("message", attempt.message());
        map.put("stderrPath", attempt.stderrPath());
        map.put("transcriptPath", attempt.transcriptPath());
        map.put("sessionName", attempt.sessionName());
        map.put("sessionId", attempt.sessionId());
        map.put("sessionScope", attempt.sessionScope());
        map.put("sessionSegmentKey", attempt.sessionSegmentKey());
        map.put("startedAt", attempt.startedAt() == null ? null : attempt.startedAt().toString());
        map.put("finishedAt", attempt.finishedAt() == null ? null : attempt.finishedAt().toString());
        return map;
    }

    private static Map<String, Object> mergedProcessResult(ObjectNode json, ProcessResult process) {
        var merged = new LinkedHashMap<String, Object>();
        var existing = json.path("processResult");
        if (existing.isObject()) {
            existing.fields().forEachRemaining(entry -> merged.put(entry.getKey(), OBJECT_MAPPER.convertValue(entry.getValue(), Object.class)));
        }
        var semantic = processResultToMap(process);
        for (var entry : semantic.entrySet()) {
            if (entry.getValue() != null && !String.valueOf(entry.getValue()).isBlank()) {
                merged.put(entry.getKey(), entry.getValue());
            }
        }
        return merged;
    }

    private static Map<String, Object> adapterSignalToMap(AdapterSignal signal) {
        var map = new LinkedHashMap<String, Object>();
        map.put("type", signal.type().name());
        map.put("severity", signal.type().severity().name());
        map.put("source", signal.source());
        map.put("message", signal.message());
        map.put("sessionId", signal.sessionId());
        map.put("observedSequence", signal.observedSequence());
        map.put("superseded", signal.superseded());
        return map;
    }

    private static Instant instantOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value, E fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, value);
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static Map<String, Object> terminalControlToMap(TerminalControlResult terminal) {
        var map = new LinkedHashMap<String, Object>();
        map.put("present", terminal.present());
        map.put("valid", terminal.valid());
        map.put("decision", terminal.decision());
        map.put("summary", terminal.summary());
        map.put("parseError", terminal.parseError());
        return map;
    }

    private static Map<String, Object> artifactContractToMap(ArtifactContractResult artifact) {
        var map = new LinkedHashMap<String, Object>();
        map.put("contractType", artifact.contractType());
        map.put("required", artifact.required());
        map.put("present", artifact.present());
        map.put("valid", artifact.valid());
        map.put("path", artifact.path());
        map.put("errorCode", artifact.errorCode());
        map.put("errorMessage", artifact.errorMessage());
        return map;
    }
}
