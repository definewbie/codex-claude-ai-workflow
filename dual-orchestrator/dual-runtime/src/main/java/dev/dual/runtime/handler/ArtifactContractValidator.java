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
        return validateAgentOutput(workspaceRoot, nodeDefinition, result);
    }

    private NodeResult validateAgentOutput(Path workspaceRoot, NodeDefinition nodeDefinition, NodeResult result) throws Exception {
        var root = workspaceRoot.toAbsolutePath().normalize();
        var contract = ContractRegistry.forNode(nodeDefinition.id());
        DualControlParseResult lastInvalid = null;
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
                    return contractViolation(nodeDefinition, result, contract, structured.failure());
                }
                return withContractSidecar(root, nodeDefinition, result, contract, parsed, structured, ref, path, markdown);
            }
            lastInvalid = parsed;
            break;
        }
        return contractViolation(nodeDefinition, result, contract, lastInvalid);
    }

    private static NodeResult withContractSidecar(
            Path root,
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
            var document = OBJECT_MAPPER.readValue(envelope.get().json(), MAP_TYPE);
            var artifactType = text(document.get("artifact_type"));
            if (!expectedType.equals(artifactType)) {
                return StructuredArtifactResult.failed(DualControlParseResult.invalid(
                        "STRUCTURED_ARTIFACT_CONTRACT_VIOLATION",
                        "structured artifact has wrong artifact_type: " + artifactType,
                        Map.of("required_artifact_type", expectedType, "actual_artifact_type", artifactType)
                ));
            }
            var decision = text(document.get("decision"));
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
            if (text(document.get("summary")).isBlank()) {
                return StructuredArtifactResult.failed(DualControlParseResult.invalid(
                        "STRUCTURED_ARTIFACT_CONTRACT_VIOLATION",
                        "structured artifact missing required field: summary",
                        Map.of("required_artifact_type", expectedType)
                ));
            }
            return StructuredArtifactResult.valid(expectedType, document);
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

    private static void addEvidenceFields(
            Map<String, Object> document,
            StructuredArtifactResult structured,
            String markdown
    ) {
        if (structured.document() != null) {
            document.put("source_artifact_type", structured.type());
            putStructured(document, structured.document(), "must_fix");
            putStructured(document, structured.document(), "open_risks");
            putStructured(document, structured.document(), "changed_files");
            putStructured(document, structured.document(), "checks_run", "checks_run", "checks");
            putStructured(document, structured.document(), "checks_skipped");
            putStructured(document, structured.document(), "evidence");
            putStructured(document, structured.document(), "missing_evidence");
            putStructured(document, structured.document(), "acceptance_notes");
            putStructured(document, structured.document(), "verification_summary");
            putStructured(document, structured.document(), "blocking");
            putStructured(document, structured.document(), "failure");
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

    private static void putStructured(Map<String, Object> target, Map<String, Object> source, String key) {
        putStructured(target, source, key, key);
    }

    private static void putStructured(Map<String, Object> target, Map<String, Object> source, String targetKey, String... sourceKeys) {
        for (var sourceKey : sourceKeys) {
            if (source.containsKey(sourceKey)) {
                target.put(targetKey, source.get(sourceKey));
                return;
            }
        }
        target.put(targetKey, List.of());
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static NodeResult contractViolation(
            NodeDefinition nodeDefinition,
            NodeResult result,
            NodeOutputContract contract,
            DualControlParseResult lastInvalid
    ) {
        var reason = lastInvalid == null || lastInvalid.isMissing()
                ? "MISSING_DUAL_CONTROL"
                : lastInvalid.errorCode();
        var message = lastInvalid == null
                ? "No readable agent output artifact found for contract validation."
                : lastInvalid.message();
        var details = lastInvalid == null ? Map.<String, Object>of() : lastInvalid.details();
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
        failed.put("message", message);
        return new NodeResult(
                "AGENT_OUTPUT_CONTRACT_VIOLATION",
                NodeStatus.FAILED,
                result.outputRefs(),
                failed,
                message
        );
    }

    private record StructuredArtifactResult(String type, Map<String, Object> document, DualControlParseResult failure) {
        static StructuredArtifactResult none() {
            return new StructuredArtifactResult("", null, null);
        }

        static StructuredArtifactResult valid(String type, Map<String, Object> document) {
            return new StructuredArtifactResult(type, document, null);
        }

        static StructuredArtifactResult failed(DualControlParseResult failure) {
            return new StructuredArtifactResult("", null, failure);
        }
    }
}
