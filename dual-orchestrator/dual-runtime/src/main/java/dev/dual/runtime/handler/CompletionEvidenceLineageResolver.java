package dev.dual.runtime.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.dual.core.id.DualId;
import dev.dual.core.workflow.NodeInstance;
import dev.dual.core.workflow.NodeStatus;
import dev.dual.core.workflow.WorkflowDefinition;
import dev.dual.ledger.file.GoalDomainOutcome;
import dev.dual.ledger.file.GoalSatisfactionDocument;
import dev.dual.ledger.file.NodeInstanceDocument;
import dev.dual.ledger.file.WorkflowDefinitionDocument;
import dev.dual.ledger.file.WorkflowFileLedger;
import dev.dual.ledger.file.WorkflowInstanceDocument;
import dev.dual.ledger.file.WorkstreamPaths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Resolves final completion evidence exclusively through persisted ledger lineage. */
public final class CompletionEvidenceLineageResolver {
    public static final String GOAL_NOT_CONFIGURED = "COMPLETION_GOAL_NOT_CONFIGURED";
    public static final String GOAL_NOT_FINAL = "COMPLETION_GOAL_NOT_FINAL";
    public static final String GOAL_INVALID = "COMPLETION_GOAL_INVALID";
    public static final String ACCEPTANCE_INVALID = "COMPLETION_ACCEPTANCE_LINEAGE_INVALID";
    public static final String CONTEXT_INVALID = "COMPLETION_CONTEXT_LINEAGE_INVALID";
    public static final String SELF_TEST_INVALID = "COMPLETION_SELF_TEST_LINEAGE_INVALID";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private CompletionEvidenceLineageResolver() {
    }

    public static Resolution resolveCurrent(Path workspaceRoot, String workstreamId) {
        var root = workspaceRoot.toAbsolutePath().normalize();
        var paths = new WorkstreamPaths(root, new DualId(workstreamId));
        if (!Files.isRegularFile(paths.goalSatisfactionJson())) {
            return Resolution.notConfigured("goal satisfaction document is not configured");
        }
        try {
            var goal = OBJECT_MAPPER.readValue(paths.goalSatisfactionJson().toFile(), GoalSatisfactionDocument.class);
            if (!workstreamId.equals(goal.workstreamId())) {
                return Resolution.invalid(GOAL_INVALID, "goal workstream_id does not match", goal);
            }
            var ledger = new WorkflowFileLedger();
            var current = ledger.loadCurrent(paths);
            var instance = OBJECT_MAPPER.readValue(paths.workflowInstanceJson().toFile(), WorkflowInstanceDocument.class);
            var definitionDocument = OBJECT_MAPPER.readValue(
                    paths.workflowDefinitionJson().toFile(), WorkflowDefinitionDocument.class);
            var definition = new WorkflowDefinition(
                    definitionDocument.workflowId(),
                    definitionDocument.version(),
                    definitionDocument.entryNodeId(),
                    definitionDocument.nodes(),
                    definitionDocument.edges());
            if (!goal.workflowInstanceId().equals(current.workflowInstanceId())
                    || !goal.workflowInstanceId().equals(instance.workflowInstanceId())
                    || !current.workflowId().equals(definition.workflowId())
                    || current.definitionVersion() != definition.version()
                    || !instance.workflowId().equals(definition.workflowId())
                    || instance.definitionVersion() != definition.version()) {
                return Resolution.invalid(GOAL_INVALID, "goal workflow identity does not match persisted workflow", goal);
            }
            if (!"acceptance.decision".equals(goal.nodeDefinitionId())
                    || !Set.of(GoalDomainOutcome.SATISFIED, GoalDomainOutcome.SATISFIED_WITH_NOTES)
                    .contains(goal.domainOutcome())) {
                return Resolution.notFinal(goal, "current goal is not a final satisfied acceptance");
            }

            var decisionNode = ledger.loadNode(paths, goal.nodeInstanceId());
            var decisionViolation = decisionViolation(goal, decisionNode);
            if (!decisionViolation.isBlank()) {
                return Resolution.invalid(GOAL_INVALID, decisionViolation, goal);
            }

            var acceptance = acceptanceEvidence(root, workstreamId, paths, definition, goal, decisionNode);
            if (acceptance.failure() != null) {
                return Resolution.invalid(ACCEPTANCE_INVALID, acceptance.failure(), goal);
            }
            var context = contextNode(root, workstreamId, paths, definition, goal, acceptance.evidence());
            if (context.failure() != null) {
                return Resolution.invalid(CONTEXT_INVALID, context.failure(), goal);
            }
            var selfTest = selfTestEvidence(
                    root, workstreamId, paths, definition, goal, context.node(), context.definitionConfig());
            if (selfTest.failure() != null) {
                return Resolution.invalid(SELF_TEST_INVALID, selfTest.failure(), goal);
            }
            return Resolution.available(goal, decisionNode, context.node(), acceptance.evidence(), selfTest.evidence());
        } catch (Exception e) {
            return Resolution.invalid(GOAL_INVALID, "completion evidence cannot be resolved: " + e.getMessage(), null);
        }
    }

    public static Resolution resolveForReport(
            Path workspaceRoot,
            String workstreamId,
            NodeInstance reportNode
    ) {
        var resolution = resolveCurrent(workspaceRoot, workstreamId);
        if (resolution.status() != Status.AVAILABLE) {
            return resolution;
        }
        if (!"acceptance.generate_final_report".equals(reportNode.nodeDefinitionId())) {
            return Resolution.invalid(GOAL_INVALID, "report node definition is not acceptance.generate_final_report",
                    resolution.goal());
        }
        if (!resolution.goal().iterationId().equals(reportNode.iterationId())) {
            return Resolution.invalid(GOAL_INVALID, "report node iteration does not match final goal",
                    resolution.goal());
        }
        if (!sameRefs(resolution.goal().evidenceRefs(), reportNode.inputRefs())) {
            return Resolution.invalid(GOAL_INVALID, "report routed refs do not match final goal evidence refs",
                    resolution.goal());
        }
        return resolution;
    }

    private static String decisionViolation(GoalSatisfactionDocument goal, NodeInstanceDocument node) {
        if (!goal.nodeInstanceId().equals(node.nodeInstanceId())
                || !goal.nodeDefinitionId().equals(node.nodeDefinitionId())) {
            return "goal does not identify the persisted acceptance decision node";
        }
        if (node.status() != NodeStatus.COMPLETED || !goal.iterationId().equals(node.iterationId())) {
            return "acceptance decision node is not completed in the goal iteration";
        }
        if (!goal.outcome().equals(text(node.result().get("decision")))) {
            return "goal outcome does not match acceptance decision node result";
        }
        if (!sameRefs(goal.evidenceRefs(), node.outputRefs())) {
            return "goal evidence refs do not match acceptance decision output refs";
        }
        return "";
    }

    private static EvidenceRead acceptanceEvidence(
            Path root,
            String workstreamId,
            WorkstreamPaths paths,
            WorkflowDefinition definition,
            GoalSatisfactionDocument goal,
            NodeInstanceDocument decisionNode
    ) {
        try {
            var predecessors = definition.edges().stream()
                    .filter(edge -> "acceptance.decision".equals(edge.to()))
                    .map(edge -> edge.from())
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            var matches = AgentEvidenceIndex.fromRefs(root, workstreamId, decisionNode.outputRefs(), OBJECT_MAPPER)
                    .recent(Math.max(1, decisionNode.outputRefs().size()))
                    .stream()
                    .filter(entry -> predecessors.contains(entry.document().nodeId()))
                    .filter(entry -> ContractRegistry.hasContract(entry.document().nodeId()))
                    .filter(entry -> ContractRegistry.forNode(entry.document().nodeId())
                            .emitsArtifact(entry.document().sourceArtifactType()))
                    .filter(entry -> "acceptance_review".equals(entry.document().sourceArtifactType()))
                    .filter(entry -> goal.outcome().equals(entry.document().decision()))
                    .toList();
            if (matches.size() != 1) {
                return EvidenceRead.failed("expected exactly one routed final acceptance sidecar, found " + matches.size());
            }
            var entry = matches.getFirst();
            var producer = new WorkflowFileLedger().loadNode(paths, entry.document().nodeInstanceId());
            if (!goal.iterationId().equals(producer.iterationId())) {
                return EvidenceRead.failed("acceptance sidecar producer is from another iteration");
            }
            return EvidenceRead.found(evidence(root, entry));
        } catch (Exception e) {
            return EvidenceRead.failed("acceptance evidence is invalid: " + e.getMessage());
        }
    }

    private static ContextRead contextNode(
            Path root,
            String workstreamId,
            WorkstreamPaths paths,
            WorkflowDefinition definition,
            GoalSatisfactionDocument goal,
            Evidence acceptance
    ) {
        try {
            var ledger = new WorkflowFileLedger();
            var producer = ledger.loadNode(paths, acceptance.document().nodeInstanceId());
            var packageRefs = producer.inputRefs().stream()
                    .filter(ref -> canonicalContextPackageRef(workstreamId, ref))
                    .toList();
            if (packageRefs.size() != 1) {
                return ContextRead.failed("expected exactly one acceptance context package ref, found "
                        + packageRefs.size());
            }
            var packageRef = normalizeRef(packageRefs.getFirst());
            var packagePath = root.resolve(packageRef).normalize();
            if (!regularFileWithin(root, packagePath)) {
                return ContextRead.failed("acceptance context package is not a regular workspace file");
            }
            var fileName = packagePath.getFileName().toString();
            var contextNodeId = fileName.substring(0, fileName.length() - ".md".length());
            var contextNode = ledger.loadNode(paths, contextNodeId);
            if (!contextNodeId.equals(contextNode.nodeInstanceId())
                    || contextNode.status() != NodeStatus.COMPLETED
                    || !goal.iterationId().equals(contextNode.iterationId())
                    || !"context.build_package".equals(contextNode.handlerType())
                    || !containsRef(contextNode.outputRefs(), packageRef)) {
                return ContextRead.failed("context package is not published by the completed context node occurrence");
            }
            var contextDefinition = definition.node(contextNode.nodeDefinitionId());
            var profile = text(contextDefinition.config().get("profile"));
            var target = text(contextDefinition.config().get("target_node"));
            if (!"acceptance".equals(profile) || !acceptance.document().nodeId().equals(target)) {
                return ContextRead.failed("context node definition does not target the acceptance producer");
            }
            var metadataRef = packageRef.substring(0, packageRef.length() - ".md".length()) + ".json";
            if (!containsRef(producer.inputRefs(), metadataRef)
                    || !containsRef(contextNode.outputRefs(), metadataRef)
                    || !regularFileWithin(root, root.resolve(metadataRef).normalize())) {
                return ContextRead.failed("acceptance context metadata is not published by the same context occurrence");
            }
            return ContextRead.found(contextNode, contextDefinition.config());
        } catch (Exception e) {
            return ContextRead.failed("acceptance context lineage is invalid: " + e.getMessage());
        }
    }

    private static EvidenceRead selfTestEvidence(
            Path root,
            String workstreamId,
            WorkstreamPaths paths,
            WorkflowDefinition definition,
            GoalSatisfactionDocument goal,
            NodeInstanceDocument contextNode,
            Map<String, Object> contextConfig
    ) {
        try {
            var required = requiredEvidence(contextConfig.get("required_agent_evidence"));
            var requiredDefinition = definition.node(required.nodeId());
            if (!ContractRegistry.hasContract(required.nodeId())
                    || !ContractRegistry.forNode(required.nodeId()).emitsArtifact(required.sourceArtifactType())
                    || !"acpx.agent_run".equals(requiredDefinition.handlerType())) {
                return EvidenceRead.failed("required self-test evidence is not compatible with its workflow contract");
            }
            var matches = AgentEvidenceIndex.fromRefs(root, workstreamId, contextNode.inputRefs(), OBJECT_MAPPER)
                    .recent(Math.max(1, contextNode.inputRefs().size()))
                    .stream()
                    .filter(entry -> required.nodeId().equals(entry.document().nodeId()))
                    .filter(entry -> required.sourceArtifactType().equals(entry.document().sourceArtifactType()))
                    .filter(entry -> required.decision().equals(entry.document().decision()))
                    .filter(entry -> entry.document().hasRequiredTypedPayload())
                    .toList();
            if (matches.size() != 1) {
                return EvidenceRead.failed("expected exactly one routed self-test sidecar, found " + matches.size());
            }
            var entry = matches.getFirst();
            var producer = new WorkflowFileLedger().loadNode(paths, entry.document().nodeInstanceId());
            if (!goal.iterationId().equals(producer.iterationId())) {
                return EvidenceRead.failed("self-test sidecar producer is from another iteration");
            }
            return EvidenceRead.found(evidence(root, entry));
        } catch (Exception e) {
            return EvidenceRead.failed("self-test evidence is invalid: " + e.getMessage());
        }
    }

    private static RequiredEvidence requiredEvidence(Object configured) {
        if (!(configured instanceof Map<?, ?> values)) {
            throw new IllegalArgumentException("required_agent_evidence must be an object");
        }
        return new RequiredEvidence(
                requiredText(values, "node_id"),
                requiredText(values, "source_artifact_type"),
                requiredText(values, "decision"));
    }

    private static String requiredText(Map<?, ?> values, String key) {
        var value = text(values.get(key));
        if (value.isBlank()) {
            throw new IllegalArgumentException("required_agent_evidence." + key + " is required");
        }
        return value;
    }

    private static Evidence evidence(Path root, AgentEvidenceIndex.Entry entry) {
        return new Evidence(
                normalizeRef(root.relativize(entry.path()).toString()),
                entry.path(),
                entry.document());
    }

    private static boolean canonicalContextPackageRef(String workstreamId, String ref) {
        var normalized = normalizeRef(ref);
        var prefix = ".agent/workstreams/" + workstreamId + "/context/";
        if (!normalized.startsWith(prefix) || !normalized.endsWith(".md")) {
            return false;
        }
        var name = normalized.substring(prefix.length(), normalized.length() - ".md".length());
        return !name.isBlank() && !name.contains("/") && !name.contains("\\");
    }

    private static boolean regularFileWithin(Path root, Path path) {
        try {
            return path.startsWith(root)
                    && Files.isRegularFile(path)
                    && !Files.isSymbolicLink(path)
                    && path.toRealPath().startsWith(root.toRealPath());
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean sameRefs(List<String> left, List<String> right) {
        return left.stream().map(CompletionEvidenceLineageResolver::normalizeRef).toList()
                .equals(right.stream().map(CompletionEvidenceLineageResolver::normalizeRef).toList());
    }

    private static boolean containsRef(List<String> refs, String expected) {
        var normalized = normalizeRef(expected);
        return refs.stream().map(CompletionEvidenceLineageResolver::normalizeRef).anyMatch(normalized::equals);
    }

    private static String normalizeRef(String value) {
        return value == null ? "" : value.replace('\\', '/');
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    public enum Status {
        AVAILABLE,
        NOT_CONFIGURED,
        NOT_FINAL,
        INVALID
    }

    public record Evidence(String ref, Path path, AgentOutputContractDocument document) {
    }

    public record Resolution(
            Status status,
            String failureCode,
            String detail,
            GoalSatisfactionDocument goal,
            NodeInstanceDocument decisionNode,
            NodeInstanceDocument contextNode,
            Evidence acceptance,
            Evidence selfTest
    ) {
        static Resolution available(
                GoalSatisfactionDocument goal,
                NodeInstanceDocument decisionNode,
                NodeInstanceDocument contextNode,
                Evidence acceptance,
                Evidence selfTest
        ) {
            return new Resolution(Status.AVAILABLE, "", "", goal, decisionNode, contextNode, acceptance, selfTest);
        }

        static Resolution notConfigured(String detail) {
            return new Resolution(Status.NOT_CONFIGURED, GOAL_NOT_CONFIGURED, detail,
                    null, null, null, null, null);
        }

        static Resolution notFinal(GoalSatisfactionDocument goal, String detail) {
            return new Resolution(Status.NOT_FINAL, GOAL_NOT_FINAL, detail,
                    goal, null, null, null, null);
        }

        static Resolution invalid(String code, String detail, GoalSatisfactionDocument goal) {
            return new Resolution(Status.INVALID, code, detail, goal, null, null, null, null);
        }
    }

    private record EvidenceRead(Evidence evidence, String failure) {
        static EvidenceRead found(Evidence evidence) {
            return new EvidenceRead(evidence, null);
        }

        static EvidenceRead failed(String failure) {
            return new EvidenceRead(null, failure);
        }
    }

    private record ContextRead(NodeInstanceDocument node, Map<String, Object> definitionConfig, String failure) {
        static ContextRead found(NodeInstanceDocument node, Map<String, Object> definitionConfig) {
            return new ContextRead(node, definitionConfig, null);
        }

        static ContextRead failed(String failure) {
            return new ContextRead(null, null, failure);
        }
    }

    private record RequiredEvidence(String nodeId, String sourceArtifactType, String decision) {
    }
}
