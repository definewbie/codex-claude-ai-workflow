package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
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

public final class ArtifactContractValidator {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
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
        if (result.status() != NodeStatus.COMPLETED) {
            return result;
        }
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
                var structured = structuredArtifact(nodeDefinition.id(), markdown, parsed);
                if (structured.failure() != null) {
                    return contractViolation(workspaceRoot, workstreamId, nodeInstanceId, nodeDefinition, result, contract, ref, structured.failure());
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
        return new NodeResult(result.outcome(), result.status(), outputRefs, augmented, result.errorMessage());
    }

    private StructuredArtifactResult structuredArtifact(String nodeId, String markdown, DualControlParseResult parsed) {
        var expectedType = requiredArtifactType(nodeId);
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
            return StructuredArtifactResult.valid(expectedType, artifact);
        } catch (Exception e) {
            return StructuredArtifactResult.failed(DualControlParseResult.invalid(
                    "STRUCTURED_ARTIFACT_PARSE_FAILED",
                    "structured artifact JSON could not be parsed: " + e.getMessage(),
                    Map.of("required_artifact_type", expectedType)
            ));
        }
    }

    private static String requiredArtifactType(String nodeId) {
        return switch (nodeId) {
            case "implement.claude_self_test_report" -> "self_test_report";
            case "acceptance.codex_verifier_review" -> "acceptance_review";
            case "stage.claude_handoff" -> "stage_handoff";
            default -> null;
        };
    }

    private static StructuredAgentArtifact toStructuredArtifact(String expectedType, AgentStructuredArtifactDocument document) {
        return switch (expectedType) {
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
        return new NodeResult(
                "AGENT_OUTPUT_CONTRACT_VIOLATION",
                NodeStatus.FAILED,
                outputRefs,
                failed,
                message
        );
    }

    private sealed interface StructuredAgentArtifact permits
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

    private record StructuredArtifactResult(String type, StructuredAgentArtifact artifact, DualControlParseResult failure) {
        static StructuredArtifactResult none() {
            return new StructuredArtifactResult("", null, null);
        }

        static StructuredArtifactResult valid(String type, StructuredAgentArtifact artifact) {
            return new StructuredArtifactResult(type, artifact, null);
        }

        static StructuredArtifactResult failed(DualControlParseResult failure) {
            return new StructuredArtifactResult("", null, failure);
        }
    }
}
