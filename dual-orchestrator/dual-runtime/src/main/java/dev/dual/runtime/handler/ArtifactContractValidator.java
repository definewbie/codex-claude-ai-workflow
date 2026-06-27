package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.dual.core.execution.AdapterSignal;
import dev.dual.core.execution.AdapterSignalType;
import dev.dual.core.execution.AgentExecutionEvidence;
import dev.dual.core.execution.AgentExecutionOutcome;
import dev.dual.core.execution.AgentExecutionOutcomeResolver;
import dev.dual.core.execution.AgentExecutionRetryPolicy;
import dev.dual.core.execution.ArtifactContractResult;
import dev.dual.core.execution.ProcessResult;
import dev.dual.core.execution.ProcessUsability;
import dev.dual.core.execution.TerminalControlResult;
import dev.dual.core.workflow.NodeDefinition;
import dev.dual.core.workflow.NodeResult;
import dev.dual.core.workflow.NodeStatus;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class ArtifactContractValidator {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final Pattern STRUCTURED_ARTIFACT_BLOCK = Pattern.compile(
            "(?s)<dual-artifact\\b[^>]*>.*?</dual-artifact>\\s*"
    );
    private static final Pattern DUAL_CONTROL_BLOCK = Pattern.compile(
            "(?s)```dual-control\\b.*?```\\s*"
    );
    private static final List<String> PLAN_BODY_SECTIONS = List.of(
            "Implementation Steps",
            "File-Level Changes",
            "Validation Plan",
            "Rollback Notes",
            "Known Risks",
            "Human Review Checklist"
    );
    private static final AgentExecutionOutcomeResolver OUTCOME_RESOLVER = new AgentExecutionOutcomeResolver();
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
                var structured = structuredArtifact(nodeDefinition, markdown, parsed);
                if (structured.failure() != null) {
                    return contractViolation(workspaceRoot, workstreamId, nodeInstanceId, nodeDefinition, result, contract, ref, structured.failure());
                }
                var bodyFailure = validateFormalArtifactBody(nodeDefinition, markdown);
                if (bodyFailure != null) {
                    return contractViolation(workspaceRoot, workstreamId, nodeInstanceId, nodeDefinition, result, contract, ref, bodyFailure);
                }
                var rereadCoverageFailure = validateRereadCoverage(root, workstreamId, result, structured);
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

    private static DualControlParseResult validateFormalArtifactBody(NodeDefinition nodeDefinition, String markdown) {
        if (!"plan.claude_plan".equals(nodeDefinition.id())) {
            return null;
        }
        var body = formalBody(markdown);
        var missing = PLAN_BODY_SECTIONS.stream()
                .filter(section -> sectionText(body, section).isBlank())
                .toList();
        if (!missing.isEmpty()) {
            return DualControlParseResult.invalid(
                    "FORMAL_ARTIFACT_BODY_MISSING",
                    "plan.claude_plan formal artifact body is missing required plan sections: " + missing
                            + ". Plan content must be in the workflow output artifact, not only in the acpx transcript.",
                    Map.of("missing_sections", missing)
            );
        }
        return null;
    }

    private static DualControlParseResult validateRereadCoverage(
            Path root,
            String workstreamId,
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
        var declared = artifact.rereadReasons().stream()
                .map(reason -> normalizeRepoPath(reason.path()))
                .filter(path -> !path.isBlank())
                .collect(java.util.stream.Collectors.toSet());
        var missing = actualReads.stream()
                .filter(reusableReads::contains)
                .filter(path -> !declared.contains(path))
                .toList();
        if (missing.isEmpty()) {
            return null;
        }
        return DualControlParseResult.invalid(
                "REREAD_REASON_MISSING",
                "agent re-read reusable files without matching agent_output_contract.reread_reasons entries: " + missing,
                Map.of("missing_reread_reason_paths", missing)
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

    private static List<String> reusableReadFiles(Path root, String workstreamId) {
        var path = root.resolve(".agent/workstreams").resolve(workstreamId)
                .resolve("memory/read-reuse-contract.json").normalize();
        if (!path.startsWith(root) || !Files.isRegularFile(path)) {
            return List.of();
        }
        try {
            var document = OBJECT_MAPPER.readValue(path.toFile(), MAP_TYPE);
            var value = document.get("reusableReadFiles");
            if (!(value instanceof List<?> list)) {
                return List.of();
            }
            var out = new ArrayList<String>();
            for (var item : list) {
                if (item instanceof Map<?, ?> map) {
                    var normalized = normalizeRepoPath(text(map.get("path")));
                    if (!normalized.isBlank()) {
                        out.add(normalized);
                    }
                }
            }
            return out.stream().distinct().toList();
        } catch (Exception ignored) {
            return List.of();
        }
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

    private static String formalBody(String markdown) {
        var withoutArtifacts = STRUCTURED_ARTIFACT_BLOCK.matcher(markdown == null ? "" : markdown).replaceAll("");
        return DUAL_CONTROL_BLOCK.matcher(withoutArtifacts).replaceAll("").strip();
    }

    private static String sectionText(String markdown, String heading) {
        var lines = markdown == null ? List.<String>of() : markdown.lines().toList();
        int start = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (isHeading(lines.get(i), heading)) {
                start = i + 1;
                break;
            }
        }
        if (start < 0) {
            return "";
        }
        int end = lines.size();
        for (int i = start; i < lines.size(); i++) {
            if (lines.get(i).stripLeading().matches("^#{1,6}\\s+.+")) {
                end = i;
                break;
            }
        }
        return String.join("\n", lines.subList(start, end)).strip();
    }

    private static boolean isHeading(String line, String heading) {
        if (line == null) {
            return false;
        }
        var normalized = line.stripLeading()
                .replaceFirst("^#{1,6}\\s+", "")
                .replaceFirst("^\\d+[.)]\\s+", "")
                .strip();
        return normalized.equalsIgnoreCase(heading);
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
        var augmented = new LinkedHashMap<String, Object>();
        augmented.putAll(result.result());
        augmented.put("decision", block.required("decision"));
        augmented.put("summary", block.required("summary"));
        augmented.put("contract_sidecar_ref", sidecarRef);
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
                outcome,
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

    private StructuredArtifactResult structuredArtifact(NodeDefinition nodeDefinition, String markdown, DualControlParseResult parsed) {
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
            var artifact = toStructuredArtifact(expectedType, structuredDocument);
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
            var semanticFailure = AgentContractSemantics.validateStructuredArtifact(expectedType, structuredDocument);
            if (semanticFailure.isPresent()) {
                return StructuredArtifactResult.failed(semanticFailure.get());
            }
            var rereadFailure = AgentContractSemantics.validateRereadReasons(rawDocument.get("reread_reasons"));
            if (rereadFailure.isPresent()) {
                return StructuredArtifactResult.failed(rereadFailure.get());
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

    private static StructuredAgentArtifact toStructuredArtifact(String expectedType, AgentStructuredArtifactDocument document) {
        return switch (expectedType) {
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
                    document.rereadReasons()
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
                outcome,
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
            AgentExecutionOutcome outcome,
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
            json.put("schemaVersion", Math.max(json.path("schemaVersion").asInt(0), 3));
            json.put("status", outcome.nodeMaySucceed() ? "completed" : "failed");
            json.put("failureReason", outcome.nodeMaySucceed() ? null : outcome.primaryReason());
            json.put("recoverable", !outcome.nodeMaySucceed()
                    && outcome.retryPolicy() != AgentExecutionRetryPolicy.NONE);
            json.set("processResult", OBJECT_MAPPER.valueToTree(mergedProcessResult(json, processResult(result))));
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
            json.set("executionOutcome", OBJECT_MAPPER.valueToTree(outcomeToMap(outcome)));
            OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), json);
        } catch (Exception ignored) {
            // Run metadata is an observability artifact. Contract validation result
            // remains authoritative even if the historical run file is damaged.
        }
    }

    private static NodeStatus nodeStatusFor(AgentExecutionOutcome outcome) {
        return outcome.nodeMaySucceed() ? NodeStatus.COMPLETED : NodeStatus.FAILED;
    }

    private sealed interface StructuredAgentArtifact permits
            AgentOutputArtifact,
            SelfTestReportArtifact,
            AcceptanceReviewArtifact,
            StageHandoffArtifact {
        String artifactType();

        String decision();

        String summary();

        void writeSidecarFields(Map<String, Object> document);
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
            List<AgentOutputContractDocument.RereadReason> rereadReasons
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
