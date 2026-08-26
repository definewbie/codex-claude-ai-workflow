package dev.dual.runtime.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dual.core.contract.TaskContract;
import dev.dual.runtime.governance.AgentCapabilityCatalog;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Builds passive repository-scope governance artifacts for one context package.
 *
 * These documents are execution guardrails and evidence projections, not
 * workflow truth. They are rebuildable from the effective workdir, task
 * contract, evidence manifest, and node context, then consumed by prompts,
 * trace, and operator views to compare planned scope with actual agent
 * behavior.
 */
final class RepoScopeGovernance {
    static final String REPO_MAP_REF = "memory/repo-map.json";
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final int MAX_SCOPE_FILES = 24;

    private RepoScopeGovernance() {
    }

    static Documents build(
            Path workspaceRoot,
            Path workstreamDir,
            TaskHandler.TaskContext context,
            ObjectMapper objectMapper
    ) throws IOException {
        return build(workspaceRoot, workstreamDir, context, objectMapper, Optional.empty());
    }

    static Documents build(
            Path workspaceRoot,
            Path workstreamDir,
            TaskHandler.TaskContext context,
            ObjectMapper objectMapper,
            Optional<TaskContractProjectionVerifier.QualifiedTaskContract> qualifiedContract
    ) throws IOException {
        var stageId = stageId(context);
        var targetNodeDefinitionId = targetNodeDefinitionId(context);
        var memoryDir = workstreamDir.resolve("memory");
        var workstreamMemoryPath = memoryDir.resolve("workstream-memory.json");
        var existingMemory = readWorkstreamMemory(workstreamMemoryPath, objectMapper);
        var plan = buildFileRelevancePlan(
                workstreamDir, context, objectMapper, stageId, targetNodeDefinitionId, qualifiedContract);
        var repoMap = buildRepoMap(
                workspaceRoot,
                workstreamDir,
                context,
                objectMapper,
                textList(plan.get("readCandidates"))
        );
        var policy = buildStageScopePolicy(context, stageId, plan, targetNodeDefinitionId);
        var report = buildInitialReport(context, stageId, plan, policy, targetNodeDefinitionId);
        var workingSet = buildWorkingSet(context, stageId, plan, policy, targetNodeDefinitionId);
        var fileSummaries = buildFileSummaries(context, stageId, workingSet);
        var readReuseContract = buildReadReuseContract(context, stageId, plan, policy, targetNodeDefinitionId, existingMemory);
        var capabilityCatalog = AgentCapabilityCatalog.detect(
                context.workstreamId(),
                targetNodeDefinitionId,
                context.definition().config()
        );
        var governanceDir = workstreamDir.resolve("artifacts").resolve("context-governance");
        Files.createDirectories(memoryDir);
        Files.createDirectories(governanceDir);

        var nodeKey = safeFileName(context.instance().nodeInstanceId());
        var repoMapPath = memoryDir.resolve("repo-map.json");
        var planPath = memoryDir.resolve("file-relevance-plan-" + nodeKey + ".json");
        var policyPath = memoryDir.resolve("stage-scope-policy-" + nodeKey + ".json");
        var workingSetPath = memoryDir.resolve("working-set.json");
        var fileSummariesPath = memoryDir.resolve("file-summaries.json");
        var readReuseContractPath = memoryDir.resolve("read-reuse-contract.json");
        var capabilityCatalogPath = memoryDir.resolve("agent-capability-catalog-" + nodeKey + ".json");
        var reportPath = governanceDir.resolve(nodeKey + ".json");
        var workstreamMemory = buildWorkstreamMemory(
                context,
                workspaceRoot,
                stageId,
                existingMemory,
                repoMapPath,
                planPath,
                policyPath,
                workingSetPath,
                fileSummariesPath,
                readReuseContractPath
        );

        objectMapper.writerWithDefaultPrettyPrinter().writeValue(repoMapPath.toFile(), repoMap);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(planPath.toFile(), plan);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(policyPath.toFile(), policy);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(workingSetPath.toFile(), workingSet);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(fileSummariesPath.toFile(), fileSummaries);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(readReuseContractPath.toFile(), readReuseContract);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(capabilityCatalogPath.toFile(), capabilityCatalog.toDocument());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(workstreamMemoryPath.toFile(), workstreamMemory);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(reportPath.toFile(), report);

        return new Documents(
                repoMap,
                plan,
                policy,
                report,
                workingSet,
                fileSummaries,
                readReuseContract,
                capabilityCatalog.toDocument(),
                repoMapPath,
                planPath,
                policyPath,
                reportPath,
                workingSetPath,
                fileSummariesPath,
                readReuseContractPath,
                capabilityCatalogPath,
                workstreamMemoryPath
        );
    }

    static void appendMarkdownGuidance(StringBuilder builder, Documents documents, Path workspaceRoot) {
        appendMarkdownGuidance(builder, documents, workspaceRoot, false);
    }

    static void appendMarkdownGuidance(StringBuilder builder, Documents documents, Path workspaceRoot, boolean compact) {
        if (documents == null) {
            return;
        }
        builder.append("## Repo Scope Guidance\n\n");
        builder.append("This section is a passive harness guardrail. It guides repository exploration but does not replace task contract, artifact validation, or human approval.\n\n");
        builder.append("- Stage: ").append(text(documents.policy().get("stageId"))).append("\n");
        if (compact) {
            appendCompactMarkdownGuidance(builder, documents, workspaceRoot);
            return;
        }
        builder.append("- Repo map: ").append(ref(workspaceRoot, documents.repoMapPath())).append("\n");
        builder.append("- File relevance plan: ").append(ref(workspaceRoot, documents.fileRelevancePlanPath())).append("\n");
        builder.append("- Stage scope policy: ").append(ref(workspaceRoot, documents.stageScopePolicyPath())).append("\n");
        builder.append("- Workstream memory: ").append(ref(workspaceRoot, documents.workstreamMemoryPath())).append("\n");
        builder.append("- Working set: ").append(ref(workspaceRoot, documents.workingSetPath())).append("\n");
        builder.append("- File summaries: ").append(ref(workspaceRoot, documents.fileSummariesPath())).append("\n");
        builder.append("- Read reuse contract: ").append(ref(workspaceRoot, documents.readReuseContractPath())).append("\n");
        builder.append("- Agent capability catalog: ").append(ref(workspaceRoot, documents.agentCapabilityCatalogPath())).append("\n");
        builder.append("- Governance report: ").append(ref(workspaceRoot, documents.governanceReportPath())).append("\n\n");
        appendPathList(builder, "Recommended read files", textList(documents.plan().get("readCandidates")), 8);
        appendPathList(builder, "Contract scope candidates", textList(documents.plan().get("contractScopeCandidates")), 12);
        appendPathList(builder, "Allowed write files", textList(documents.policy().get("writeScope")), 8);
        appendPathList(builder, "Forbidden scope", textList(documents.policy().get("forbiddenScope")), 8);
        appendPathList(builder, "Already inspected files", textList(documents.plan().get("previouslyReadFiles")), 8);
        appendReadAvoidanceRules(builder, documents);
        builder.append("Read recommendations are context only. Declared edits must match TaskContract.scope and are verified from Git evidence.\n\n");
    }

    private static void appendCompactMarkdownGuidance(StringBuilder builder, Documents documents, Path workspaceRoot) {
        builder.append("- Governance refs: capabilityCatalog=")
                .append(ref(workspaceRoot, documents.agentCapabilityCatalogPath()))
                .append("; readReuseContract=")
                .append(ref(workspaceRoot, documents.readReuseContractPath()))
                .append("\n\n");
        builder.append("- Available context capabilities: ")
                .append(inlineList(capabilitySummaries(documents.agentCapabilityCatalog()), 4))
                .append("\n\n");
        appendPathList(builder, "Recommended read files", textList(documents.plan().get("readCandidates")), 4);
        appendPathList(builder, "Expected edit files", textList(documents.policy().get("writeScope")), 4);
    }

    private static Map<String, Object> buildRepoMap(
            Path workspaceRoot,
            Path workstreamDir,
            TaskHandler.TaskContext context,
            ObjectMapper objectMapper,
            List<String> knownPaths
    ) throws IOException {
        var files = new ArrayList<Map<String, Object>>();
        var root = workspaceRoot.toAbsolutePath().normalize();
        for (var relative : new LinkedHashSet<>(knownPaths)) {
            var path = root.resolve(relative).normalize();
            if (!path.startsWith(root) || !Files.isRegularFile(path) || ignoredPath(relative)) {
                continue;
            }
            var item = new LinkedHashMap<String, Object>();
            item.put("path", relative);
            item.put("kind", fileKind(relative));
            item.put("sizeBytes", safeSize(path));
            files.add(item);
        }
        files.sort(Comparator.comparing(item -> text(item.get("path"))));
        var document = new LinkedHashMap<String, Object>();
        document.put("schemaVersion", 1);
        document.put("artifactType", "repo_map");
        document.put("workstreamId", context.workstreamId());
        document.put("generatedAt", Instant.now().toString());
        document.put("effectiveWorkdir", workspaceRoot.toAbsolutePath().normalize().toString());
        document.put("baseCommit", readBaseCommit(workstreamDir, objectMapper));
        document.put("source", "task_contract_scope+git_evidence");
        document.put("files", files);
        document.put("fileCount", files.size());
        document.put("truncated", false);
        return document;
    }

    private static Map<String, Object> buildFileRelevancePlan(
            Path workstreamDir,
            TaskHandler.TaskContext context,
            ObjectMapper objectMapper,
            String stageId,
            String targetNodeDefinitionId,
            Optional<TaskContractProjectionVerifier.QualifiedTaskContract> qualifiedContract
    ) throws IOException {
        var contractScope = contractScopePaths(workstreamDir, objectMapper, qualifiedContract);
        var candidates = new LinkedHashSet<String>();
        candidates.addAll(contractScope);
        addGitChangedFiles(candidates, workstreamDir);
        var readCandidates = bounded(candidates, MAX_SCOPE_FILES);
        var writeCandidates = stageAllowsBusinessWrites(stageId)
                ? contractScope
                : List.<String>of();
        var previousReads = previousReadCounts(workstreamDir, "", objectMapper);
        var evidenceFiles = evidenceFiles(workstreamDir, objectMapper);
        var repeatedReadAvoidance = repeatedReadAvoidance(readCandidates, previousReads, evidenceFiles);

        var document = new LinkedHashMap<String, Object>();
        document.put("schemaVersion", 1);
        document.put("artifactType", "file_relevance_plan");
        document.put("workstreamId", context.workstreamId());
        document.put("stageId", stageId);
        document.put("nodeInstanceId", context.instance().nodeInstanceId());
        document.put("nodeDefinitionId", context.definition().id());
        document.put("builderNodeDefinitionId", context.definition().id());
        document.put("targetNodeDefinitionId", targetNodeDefinitionId);
        document.put("iterationId", context.instance().iterationId());
        document.put("source", "task_contract_scope+git_evidence");
        document.put("readCandidates", readCandidates);
        document.put("contractScopeCandidates", contractScope);
        document.put("writeCandidates", writeCandidates);
        document.put("previouslyReadFiles", previousReads.keySet().stream().limit(MAX_SCOPE_FILES).toList());
        document.put("repeatedReadAvoidance", repeatedReadAvoidance);
        document.put("expansionRules", List.of(
                "Use only routed artifacts and canonical task-contract or Git facts as prepared context.",
                "Do not re-read previously inspected files unless this stage needs fresh file contents.",
                "Do not infer task relevance from filenames, directory layout, or requirement prose."
        ));
        document.put("generatedAt", Instant.now().toString());
        return document;
    }

    private static Map<String, Object> buildStageScopePolicy(
            TaskHandler.TaskContext context,
            String stageId,
            Map<String, Object> plan,
            String targetNodeDefinitionId
    ) {
        var writeCandidates = textList(plan.get("writeCandidates"));
        var previouslyReadFiles = textList(plan.get("previouslyReadFiles"));
        var policy = new LinkedHashMap<String, Object>();
        policy.put("schemaVersion", 1);
        policy.put("artifactType", "stage_scope_policy");
        policy.put("workstreamId", context.workstreamId());
        policy.put("stageId", stageId);
        policy.put("nodeInstanceId", context.instance().nodeInstanceId());
        policy.put("nodeDefinitionId", context.definition().id());
        policy.put("builderNodeDefinitionId", context.definition().id());
        policy.put("targetNodeDefinitionId", targetNodeDefinitionId);
        policy.put("iterationId", context.instance().iterationId());
        policy.put("writeScope", writeCandidates);
        policy.put("previouslyReadFiles", previouslyReadFiles);
        policy.put("readReusePolicy", Map.of(
                "preferExistingEvidence", true,
                "avoidRepeatedReads", previouslyReadFiles.stream().limit(12).toList(),
                "requireReasonWhenRereading", targetSupportsRereadReasonField(targetNodeDefinitionId),
                "reasonField", targetSupportsRereadReasonField(targetNodeDefinitionId)
                        ? "agent_output_contract.reread_reasons"
                        : ""
        ));
        policy.put("forbiddenScope", forbiddenScope(stageId));
        policy.put("generatedAt", Instant.now().toString());
        return policy;
    }

    private static Map<String, Object> buildInitialReport(
            TaskHandler.TaskContext context,
            String stageId,
            Map<String, Object> plan,
            Map<String, Object> policy,
            String targetNodeDefinitionId
    ) {
        var report = new LinkedHashMap<String, Object>();
        report.put("schemaVersion", 1);
        report.put("artifactType", "context_governance_report");
        report.put("workstreamId", context.workstreamId());
        report.put("stageId", stageId);
        report.put("nodeInstanceId", context.instance().nodeInstanceId());
        report.put("nodeDefinitionId", context.definition().id());
        report.put("builderNodeDefinitionId", context.definition().id());
        report.put("targetNodeDefinitionId", targetNodeDefinitionId);
        report.put("iterationId", context.instance().iterationId());
        report.put("reportPhase", "planned");
        report.put("plannedReadFiles", textList(plan.get("readCandidates")));
        report.put("plannedWriteFiles", textList(policy.get("writeScope")));
        report.put("plannedPreviouslyReadFiles", textList(plan.get("previouslyReadFiles")));
        report.put("plannedRepeatedReadAvoidance", mapList(plan.get("repeatedReadAvoidance")));
        report.put("actualReadFiles", List.of());
        report.put("actualWriteFiles", List.of());
        report.put("telemetryPending", true);
        report.put("generatedAt", Instant.now().toString());
        return report;
    }

    private static Map<String, Object> buildWorkingSet(
            TaskHandler.TaskContext context,
            String stageId,
            Map<String, Object> plan,
            Map<String, Object> policy,
            String targetNodeDefinitionId
    ) {
        var repeatedByPath = new LinkedHashMap<String, Map<String, Object>>();
        for (var item : mapList(plan.get("repeatedReadAvoidance"))) {
            var path = text(item.get("path"));
            if (!path.isBlank()) {
                repeatedByPath.put(path, item);
            }
        }

        var coreFiles = new ArrayList<Map<String, Object>>();
        var supportingFiles = new ArrayList<Map<String, Object>>();
        for (var path : textList(plan.get("readCandidates"))) {
            var file = memoryFile(path, repeatedByPath.get(path), "Read candidate from task contract, evidence, git, or repo map.");
            if (coreFiles.size() < 12) {
                coreFiles.add(file);
            } else {
                supportingFiles.add(file);
            }
        }

        var document = new LinkedHashMap<String, Object>();
        document.put("schemaVersion", 1);
        document.put("artifactType", "working_set");
        document.put("workstreamId", context.workstreamId());
        document.put("stageId", stageId);
        document.put("nodeInstanceId", context.instance().nodeInstanceId());
        document.put("nodeDefinitionId", context.definition().id());
        document.put("targetNodeDefinitionId", targetNodeDefinitionId);
        document.put("iterationId", context.instance().iterationId());
        document.put("coreFiles", coreFiles);
        document.put("supportingFiles", supportingFiles);
        document.put("allowedWriteFiles", textList(policy.get("writeScope")));
        document.put("forbiddenScope", textList(policy.get("forbiddenScope")));
        document.put("generatedAt", Instant.now().toString());
        return document;
    }

    private static Map<String, Object> buildFileSummaries(
            TaskHandler.TaskContext context,
            String stageId,
            Map<String, Object> workingSet
    ) {
        var files = new ArrayList<Map<String, Object>>();
        for (var item : mapList(workingSet.get("coreFiles"))) {
            files.add(fileSummary(item, "core"));
        }
        for (var item : mapList(workingSet.get("supportingFiles"))) {
            files.add(fileSummary(item, "supporting"));
        }

        var document = new LinkedHashMap<String, Object>();
        document.put("schemaVersion", 1);
        document.put("artifactType", "file_summaries");
        document.put("workstreamId", context.workstreamId());
        document.put("stageId", stageId);
        document.put("nodeInstanceId", context.instance().nodeInstanceId());
        document.put("iterationId", context.instance().iterationId());
        document.put("files", files);
        document.put("generatedAt", Instant.now().toString());
        return document;
    }

    private static Map<String, Object> buildReadReuseContract(
            TaskHandler.TaskContext context,
            String stageId,
            Map<String, Object> plan,
            Map<String, Object> policy,
            String targetNodeDefinitionId,
            Map<String, Object> workstreamMemory
    ) {
        var reusePolicy = mapValue(policy.get("readReusePolicy"));
        var repeated = mapList(plan.get("repeatedReadAvoidance"));
        var reusableReadFiles = reusableReadFiles(workstreamMemory);
        var invalidatedReadFiles = invalidatedReadFiles(workstreamMemory);
        var allowedRereadFiles = allowedRereadFiles(targetNodeDefinitionId, reusableReadFiles);
        var document = new LinkedHashMap<String, Object>();
        document.put("schemaVersion", 1);
        document.put("artifactType", "read_reuse_contract");
        document.put("workstreamId", context.workstreamId());
        document.put("stageId", stageId);
        document.put("nodeInstanceId", context.instance().nodeInstanceId());
        document.put("nodeDefinitionId", context.definition().id());
        document.put("targetNodeDefinitionId", targetNodeDefinitionId);
        document.put("iterationId", context.instance().iterationId());
        document.put("preferExistingEvidence", true);
        document.put("avoidRepeatingReads", repeated);
        document.put("reusableReadFiles", reusableReadFiles);
        document.put("invalidatedReadFiles", invalidatedReadFiles);
        document.put("allowedRereadFiles", allowedRereadFiles);
        document.put("reuseSource", reusableReadFiles.isEmpty() ? "run_history" : "workstream_memory.readFiles");
        var requireReason = Boolean.TRUE.equals(reusePolicy.getOrDefault(
                "requireReasonWhenRereading",
                targetSupportsRereadReasonField(targetNodeDefinitionId)
        ));
        document.put("requireReasonWhenRereading", requireReason);
        document.put("reasonField", requireReason
                ? reusePolicy.getOrDefault("reasonField", "agent_output_contract.reread_reasons")
                : "");
        if (requireReason) {
            document.put("allowedRepeatedReadReasons", ReadReuseContractReasons.ALLOWED_REPEATED_READ_REASONS);
        }
        document.put("generatedAt", Instant.now().toString());
        return document;
    }

    private static Map<String, Object> buildWorkstreamMemory(
            TaskHandler.TaskContext context,
            Path workspaceRoot,
            String stageId,
            Map<String, Object> existingMemory,
            Path repoMapPath,
            Path planPath,
            Path policyPath,
            Path workingSetPath,
            Path fileSummariesPath,
            Path readReuseContractPath
    ) {
        var document = new LinkedHashMap<String, Object>(existingMemory);
        document.put("schemaVersion", 1);
        document.put("artifactType", "workstream_memory");
        document.put("workstreamId", context.workstreamId());
        document.put("stageId", stageId);
        document.put("nodeInstanceId", context.instance().nodeInstanceId());
        document.put("iterationId", context.instance().iterationId());
        document.putIfAbsent("decidedScope", List.of());
        document.putIfAbsent("nonGoals", List.of());
        document.putIfAbsent("decisions", List.of());
        document.putIfAbsent("readFiles", List.of());
        document.putIfAbsent("openIssues", List.of());
        document.putIfAbsent("priorVerdicts", List.of());
        document.putIfAbsent("priorFailures", List.of());
        document.put("refs", Map.of(
                "repoMap", ref(workspaceRoot, repoMapPath),
                "fileRelevancePlan", ref(workspaceRoot, planPath),
                "stageScopePolicy", ref(workspaceRoot, policyPath),
                "workingSet", ref(workspaceRoot, workingSetPath),
                "fileSummaries", ref(workspaceRoot, fileSummariesPath),
                "readReuseContract", ref(workspaceRoot, readReuseContractPath)
        ));
        document.put("generatedAt", Instant.now().toString());
        return document;
    }

    private static Map<String, Object> readWorkstreamMemory(Path workstreamMemoryPath, ObjectMapper objectMapper) {
        if (!Files.isRegularFile(workstreamMemoryPath)) {
            return new LinkedHashMap<>();
        }
        try {
            return new LinkedHashMap<>(objectMapper.readValue(workstreamMemoryPath.toFile(), MAP_TYPE));
        } catch (IOException ignored) {
            return new LinkedHashMap<>();
        }
    }

    private static List<Map<String, Object>> reusableReadFiles(Map<String, Object> workstreamMemory) {
        var files = new ArrayList<Map<String, Object>>();
        for (var item : mapList(workstreamMemory.get("readFiles"))) {
            if (!Boolean.TRUE.equals(item.get("trusted")) || !text(item.get("invalidationReason")).isBlank()) {
                continue;
            }
            files.add(readReuseFile(item, "reuse_existing_summary"));
        }
        return files.stream()
                .sorted(Comparator.comparing(file -> text(file.get("path"))))
                .limit(24)
                .toList();
    }

    private static List<Map<String, Object>> invalidatedReadFiles(Map<String, Object> workstreamMemory) {
        var files = new ArrayList<Map<String, Object>>();
        for (var item : mapList(workstreamMemory.get("readFiles"))) {
            if (text(item.get("invalidationReason")).isBlank()) {
                continue;
            }
            files.add(readReuseFile(item, "fresh_read_allowed"));
        }
        return files.stream()
                .sorted(Comparator.comparing(file -> text(file.get("path"))))
                .limit(24)
                .toList();
    }

    private static List<Map<String, Object>> allowedRereadFiles(
            String targetNodeDefinitionId,
            List<Map<String, Object>> reusableReadFiles
    ) {
        if (!requiresEvidenceReview(targetNodeDefinitionId)) {
            return List.of();
        }
        return reusableReadFiles.stream()
                .filter(item -> "source".equals(text(item.get("evidenceRole"))))
                .map(item -> {
                    var file = new LinkedHashMap<String, Object>();
                    file.put("path", text(item.get("path")));
                    file.put("reason", "previous evidence does not cover the selected stage");
                    file.put("source", "workstream_memory.readFiles");
                    file.put("evidenceRole", text(item.get("evidenceRole")));
                    return (Map<String, Object>) file;
                })
                .limit(12)
                .toList();
    }

    private static boolean requiresEvidenceReview(String targetNodeDefinitionId) {
        if (!ContractRegistry.hasContract(targetNodeDefinitionId)) {
            return false;
        }
        var decisions = ContractRegistry.forNode(targetNodeDefinitionId).allowedDecisions();
        return decisions.contains("NEEDS_REVISION")
                || decisions.contains("REJECT")
                || decisions.contains("ACCEPT_WITH_NOTES");
    }

    private static Map<String, Object> readReuseFile(Map<String, Object> item, String recommendation) {
        var file = new LinkedHashMap<String, Object>();
        file.put("path", text(item.get("path")));
        file.put("sha", text(item.get("sha")));
        file.put("summary", text(item.get("summary")));
        file.put("evidenceRole", text(item.get("evidenceRole")));
        file.put("readRunId", text(item.get("readRunId")));
        file.put("lastSeenStage", text(item.get("lastSeenStage")));
        file.put("invalidationReason", text(item.get("invalidationReason")));
        file.put("recommendation", recommendation);
        return file;
    }

    private static Map<String, Object> memoryFile(String path, Map<String, Object> repeatedRead, String reason) {
        var file = new LinkedHashMap<String, Object>();
        file.put("path", path);
        file.put("reason", reason);
        file.put("previousReadCount", intValue(repeatedRead == null ? null : repeatedRead.get("previousCount")));
        file.put("evidenceKnown", repeatedRead != null && Boolean.TRUE.equals(repeatedRead.get("evidenceKnown")));
        return file;
    }

    private static Map<String, Object> fileSummary(Map<String, Object> item, String relevance) {
        var summary = new LinkedHashMap<String, Object>();
        summary.put("path", text(item.get("path")));
        summary.put("taskRelevance", relevance);
        summary.put("summary", "Task-relevant file. Reuse existing evidence before re-reading.");
        summary.put("previousReadCount", intValue(item.get("previousReadCount")));
        summary.put("evidenceKnown", Boolean.TRUE.equals(item.get("evidenceKnown")));
        summary.put("staleness", "unknown");
        return summary;
    }

    private static List<String> contractScopePaths(
            Path workstreamDir,
            ObjectMapper objectMapper,
            Optional<TaskContractProjectionVerifier.QualifiedTaskContract> qualifiedContract
    ) throws IOException {
        if (qualifiedContract.isPresent()) {
            return contractScopePaths(qualifiedContract.get().contract());
        }
        var contractPath = workstreamDir.resolve("artifacts/task-contract.json");
        if (!Files.isRegularFile(contractPath)) {
            return List.of();
        }
        var contract = objectMapper.readValue(contractPath.toFile(), TaskContract.class);
        return contractScopePaths(contract);
    }

    private static List<String> contractScopePaths(TaskContract contract) {
        var paths = new LinkedHashSet<String>();
        if (contract.scope() != null) {
            for (var item : contract.scope().included()) {
                var path = text(item.path());
                if (!path.isBlank()) {
                    paths.add(path);
                }
            }
        }
        return List.copyOf(paths);
    }

    private static void addGitChangedFiles(Set<String> candidates, Path workstreamDir) throws IOException {
        var path = workstreamDir.resolve("artifacts/git-evidence/changed-files.txt");
        if (!Files.isRegularFile(path)) {
            return;
        }
        for (var line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            var changedPath = line.strip();
            if (!changedPath.isBlank()
                    && !changedPath.startsWith("/")
                    && !changedPath.startsWith(".agent/")
                    && !ignoredPath(changedPath)) {
                candidates.add(changedPath);
            }
        }
    }

    private static boolean ignoredPath(String ref) {
        return ref.contains("/node_modules/")
                || ref.contains("/target/")
                || ref.contains("/dist/")
                || ref.contains("/build/")
                || ref.startsWith(".agent/")
                || ref.startsWith(".git/");
    }

    private static String fileKind(String relative) {
        var lower = relative.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("/test/") || lower.contains("/tests/") || lower.endsWith(".spec.ts") || lower.endsWith(".test.ts")) {
            return "test";
        }
        if (lower.endsWith(".md") || lower.startsWith("docs/")) {
            return "doc";
        }
        if (lower.endsWith(".json") || lower.endsWith(".yaml") || lower.endsWith(".yml") || lower.endsWith(".toml") || lower.endsWith(".xml")) {
            return "config";
        }
        return "source";
    }

    private static boolean stageAllowsBusinessWrites(String stageId) {
        return "implement".equals(stageId) || "self_test".equals(stageId);
    }

    private static boolean stageAllowsBroadExploration(String stageId) {
        return false;
    }

    private static List<String> forbiddenScope(String stageId) {
        var base = new ArrayList<>(List.of(".git/**", ".agent/workstreams/**/workflow/**", ".agent/workstreams/**/nodes/**"));
        if (!stageAllowsBusinessWrites(stageId)) {
            base.add("business source writes");
        }
        return List.copyOf(base);
    }

    private static String stageId(TaskHandler.TaskContext context) {
        var configured = text(context.definition().config().get("stage_id"));
        if (!configured.isBlank()) {
            return configured;
        }
        var profile = text(context.definition().config().get("profile"));
        if (!profile.isBlank()) {
            return switch (profile) {
                case "product_review" -> "intake";
                case "architecture_review" -> "design";
                case "plan_review" -> "plan";
                case "implementation" -> "implement";
                case "acceptance" -> "acceptance";
                default -> profile;
            };
        }
        var nodeId = context.definition().id();
        var dot = nodeId.indexOf('.');
        return dot > 0 ? nodeId.substring(0, dot) : nodeId;
    }

    private static String targetNodeDefinitionId(TaskHandler.TaskContext context) {
        var configured = text(context.definition().config().get("target_node"));
        return configured.isBlank() ? context.definition().id() : configured;
    }

    private static boolean targetSupportsRereadReasonField(String targetNodeDefinitionId) {
        return ContractRegistry.supportsRereadReasonField(targetNodeDefinitionId);
    }

    private static String readBaseCommit(Path workstreamDir, ObjectMapper objectMapper) {
        var gitEvidence = workstreamDir.resolve("artifacts/git-evidence/git-evidence.json");
        if (!Files.isRegularFile(gitEvidence)) {
            return "";
        }
        try {
            var json = objectMapper.readValue(gitEvidence.toFile(), MAP_TYPE);
            return text(json.get("baseCommit"));
        } catch (IOException ignored) {
            return "";
        }
    }

    private static long safeSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException ignored) {
            return 0;
        }
    }

    private static List<String> bounded(Set<String> values, int limit) {
        return values.stream()
                .filter(value -> !value.isBlank())
                .distinct()
                .limit(limit)
                .toList();
    }

    private static Map<String, Integer> previousReadCounts(
            Path workstreamDir,
            String currentRunId,
            ObjectMapper objectMapper
    ) throws IOException {
        var runsDir = workstreamDir.resolve("runs");
        if (!Files.isDirectory(runsDir)) {
            return Map.of();
        }
        var counts = new LinkedHashMap<String, Integer>();
        try (var stream = Files.list(runsDir)) {
            for (var runDir : stream.filter(Files::isDirectory)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList()) {
                if (!currentRunId.isBlank() && runDir.getFileName().toString().equals(currentRunId)) {
                    continue;
                }
                var runJson = runDir.resolve("run.json");
                if (!Files.isRegularFile(runJson)) {
                    continue;
                }
                Map<String, Object> run;
                try {
                    run = objectMapper.readValue(runJson.toFile(), MAP_TYPE);
                } catch (IOException ignored) {
                    continue;
                }
                for (var file : textList(mapValue(run.get("toolUsage")).get("readFiles"))) {
                    var normalized = normalize(file);
                    if (!normalized.isBlank() && !ignoredPath(normalized) && !normalized.startsWith("/")) {
                        counts.merge(normalized, 1, Integer::sum);
                    }
                }
            }
        }
        return counts;
    }

    private static List<Map<String, Object>> repeatedReadAvoidance(
            List<String> readCandidates,
            Map<String, Integer> previousReads,
            List<String> evidenceFiles
    ) {
        if (previousReads.isEmpty()) {
            return List.of();
        }
        var candidateSet = new LinkedHashSet<>(readCandidates);
        var evidenceSet = new LinkedHashSet<>(evidenceFiles);
        var items = new ArrayList<Map<String, Object>>();
        for (var entry : previousReads.entrySet()) {
            var file = entry.getKey();
            var inScope = candidateSet.contains(file);
            var evidenceKnown = evidenceSet.contains(file);
            if (!inScope && !evidenceKnown) {
                continue;
            }
            var item = new LinkedHashMap<String, Object>();
            item.put("path", file);
            item.put("previousCount", entry.getValue());
            item.put("inReadScope", inScope);
            item.put("evidenceKnown", evidenceKnown);
            item.put("guidance", evidenceKnown
                    ? "Use the existing evidence brief first; re-read only if fresh contents are needed."
                    : "This file was already inspected; re-read only with a stage-specific reason.");
            items.add(item);
            if (items.size() >= 12) {
                break;
            }
        }
        return List.copyOf(items);
    }

    private static List<String> evidenceFiles(Path workstreamDir, ObjectMapper objectMapper) throws IOException {
        var manifest = workstreamDir.resolve("memory").resolve("evidence-manifest.json");
        if (!Files.isRegularFile(manifest)) {
            return List.of();
        }
        Map<String, Object> document;
        try {
            document = objectMapper.readValue(manifest.toFile(), MAP_TYPE);
        } catch (IOException ignored) {
            return List.of();
        }
        var files = new LinkedHashSet<String>();
        for (var item : mapList(document.get("items"))) {
            for (var file : textList(item.get("files"))) {
                var normalized = normalize(file);
                if (!normalized.isBlank() && !ignoredPath(normalized) && !normalized.startsWith("/")) {
                    files.add(normalized);
                }
            }
        }
        return List.copyOf(files);
    }

    private static void appendReadAvoidanceRules(StringBuilder builder, Documents documents) {
        var reusable = mapList(documents.readReuseContract().get("reusableReadFiles"));
        if (!reusable.isEmpty()) {
            builder.append("- Read reuse contract: the following files were already inspected and their recorded sha is still trusted. Reuse the summary first; re-read only if you need exact current contents for editing or verification.\n");
            for (var item : reusable.stream().limit(8).toList()) {
                builder.append("  - ")
                        .append(text(item.get("path")))
                        .append(": ")
                        .append(text(item.get("summary")))
                        .append("\n");
            }
            var reasonField = text(documents.readReuseContract().get("reasonField"));
            if (!reasonField.isBlank()) {
                var allowedReasons = String.join("`, `", ReadReuseContractReasons.ALLOWED_REPEATED_READ_REASONS);
                builder.append("  If you Read any file listed above, you must record a matching entry in ")
                        .append(reasonField)
                        .append(" (path must match exactly). An empty reread_reasons is only valid when you reuse these summaries and do not re-read any of them.\n");
                builder.append("  Entry shape: {\"path\":\"<exact reusableReadFiles path>\",\"reason\":\"<one allowed reason>\",\"stage\":\"<optional, non-blank>\",\"evidence\":\"<optional, non-blank>\"}. Allowed reasons: `")
                        .append(allowedReasons)
                        .append("`.\n");
            } else {
                builder.append("  Do not re-read these files unless this stage needs exact current contents; this target artifact does not declare a reread reason field, so the harness will judge re-reads from changed_files, invalidation, and allowed-reread policy.\n");
            }
        }
        var invalidated = mapList(documents.readReuseContract().get("invalidatedReadFiles"));
        if (!invalidated.isEmpty()) {
            builder.append("- Fresh read allowed for invalidated files:\n");
            for (var item : invalidated.stream().limit(6).toList()) {
                builder.append("  - ")
                        .append(text(item.get("path")))
                        .append(" (")
                        .append(text(item.get("invalidationReason")))
                        .append(")\n");
            }
        }
        var repeated = mapList(documents.plan().get("repeatedReadAvoidance"));
        if (repeated.isEmpty()) {
            if (reusable.isEmpty()) {
                builder.append("- Read reuse rule: use the recommended read files first; no repeated-read risk is known yet.\n");
            }
            return;
        }
        builder.append("- Read reuse rule: use existing evidence before re-reading files already inspected in this workstream.\n");
        builder.append("- Re-read a listed file only when this stage needs fresh contents; record the reason in the structured artifact.\n");
        builder.append("- Repeated-read risk:\n");
        for (var item : repeated.stream().limit(6).toList()) {
            builder.append("  - ")
                    .append(text(item.get("path")))
                    .append(" (previous reads: ")
                    .append(text(item.get("previousCount")))
                    .append(", evidence known: ")
                    .append(text(item.get("evidenceKnown")))
                    .append(")\n");
        }
    }

    private static String safeFileName(String value) {
        return value == null || value.isBlank() ? "unknown" : value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static String normalize(String value) {
        var text = value == null ? "" : value.trim();
        while (text.startsWith("./")) {
            text = text.substring(2);
        }
        return text;
    }

    private static String ref(Path workspaceRoot, Path path) {
        return workspaceRoot.toAbsolutePath().normalize().relativize(path.toAbsolutePath().normalize()).toString();
    }

    private static void appendPathList(StringBuilder builder, String label, List<String> paths, int limit) {
        if (paths.isEmpty()) {
            builder.append("- ").append(label).append(": (none planned)\n");
            return;
        }
        builder.append("- ").append(label).append(":\n");
        for (var path : paths.stream().limit(limit).toList()) {
            builder.append("  - ").append(path).append("\n");
        }
    }

    private static String inlineList(List<String> values, int limit) {
        if (values.isEmpty()) {
            return "(none planned)";
        }
        var shown = values.stream().limit(limit).toList();
        var suffix = values.size() > shown.size() ? " +" + (values.size() - shown.size()) + " more" : "";
        return String.join(", ", shown) + suffix;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> mapList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(item -> (Map<String, Object>) item)
                .toList();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapValue(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private static List<String> textList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .map(RepoScopeGovernance::text)
                .filter(text -> !text.isBlank())
                .toList();
    }

    private static List<String> capabilitySummaries(Map<String, Object> capabilityCatalog) {
        if (capabilityCatalog == null || !(capabilityCatalog.get("capabilities") instanceof List<?> list)) {
            return List.of();
        }
        var values = new ArrayList<String>();
        for (var item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            var kind = text(map.get("kind"));
            var provider = text(map.get("provider"));
            if (!kind.isBlank() && !provider.isBlank()) {
                values.add(kind + ":" + provider);
            }
        }
        return List.copyOf(values);
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(text(value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    record Documents(
            Map<String, Object> repoMap,
            Map<String, Object> plan,
            Map<String, Object> policy,
            Map<String, Object> report,
            Map<String, Object> workingSet,
            Map<String, Object> fileSummaries,
            Map<String, Object> readReuseContract,
            Map<String, Object> agentCapabilityCatalog,
            Path repoMapPath,
            Path fileRelevancePlanPath,
            Path stageScopePolicyPath,
            Path governanceReportPath,
            Path workingSetPath,
            Path fileSummariesPath,
            Path readReuseContractPath,
            Path agentCapabilityCatalogPath,
            Path workstreamMemoryPath
    ) {
    }
}
