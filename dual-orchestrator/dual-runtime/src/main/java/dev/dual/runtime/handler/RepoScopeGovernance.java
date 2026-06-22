package dev.dual.runtime.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

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
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

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
    private static final int MAX_REPO_FILES = 300;
    private static final int MAX_SCOPE_FILES = 24;
    private static final Pattern PATHISH = Pattern.compile("^[\\w./@+\\-]+\\.[\\w+\\-]+$|^[\\w./@+\\-]+/.*$");
    private static final Set<String> IGNORED_DIRS = Set.of(
            ".git", ".agent", "node_modules", "target", "build", "dist", ".next", ".nuxt",
            ".venv", "venv", "__pycache__", ".idea", ".vscode", ".gradle", ".mvn"
    );

    private RepoScopeGovernance() {
    }

    static Documents build(
            Path workspaceRoot,
            Path workstreamDir,
            TaskHandler.TaskContext context,
            EvidenceManifest manifest,
            ObjectMapper objectMapper
    ) throws IOException {
        var stageId = stageId(context);
        var repoMap = buildRepoMap(workspaceRoot, workstreamDir, context, objectMapper);
        var plan = buildFileRelevancePlan(workspaceRoot, workstreamDir, context, manifest, repoMap, objectMapper, stageId);
        var policy = buildStageScopePolicy(context, stageId, plan);
        var report = buildInitialReport(context, stageId, plan, policy);

        var memoryDir = workstreamDir.resolve("memory");
        var governanceDir = workstreamDir.resolve("artifacts").resolve("context-governance");
        Files.createDirectories(memoryDir);
        Files.createDirectories(governanceDir);

        var nodeKey = safeFileName(context.instance().nodeInstanceId());
        var repoMapPath = memoryDir.resolve("repo-map.json");
        var planPath = memoryDir.resolve("file-relevance-plan-" + nodeKey + ".json");
        var policyPath = memoryDir.resolve("stage-scope-policy-" + nodeKey + ".json");
        var reportPath = governanceDir.resolve(nodeKey + ".json");

        objectMapper.writerWithDefaultPrettyPrinter().writeValue(repoMapPath.toFile(), repoMap);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(planPath.toFile(), plan);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(policyPath.toFile(), policy);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(reportPath.toFile(), report);

        return new Documents(repoMap, plan, policy, report, repoMapPath, planPath, policyPath, reportPath);
    }

    static void appendMarkdownGuidance(StringBuilder builder, Documents documents, Path workspaceRoot) {
        if (documents == null) {
            return;
        }
        builder.append("## Repo Scope Guidance\n\n");
        builder.append("This section is a passive harness guardrail. It guides repository exploration but does not replace task contract, artifact validation, or human approval.\n\n");
        builder.append("- Stage: ").append(text(documents.policy().get("stageId"))).append("\n");
        builder.append("- Scope policy: ").append(text(documents.policy().get("scopeExceedPolicy"))).append("\n");
        builder.append("- Repo map: ").append(ref(workspaceRoot, documents.repoMapPath())).append("\n");
        builder.append("- File relevance plan: ").append(ref(workspaceRoot, documents.fileRelevancePlanPath())).append("\n");
        builder.append("- Stage scope policy: ").append(ref(workspaceRoot, documents.stageScopePolicyPath())).append("\n");
        builder.append("- Governance report: ").append(ref(workspaceRoot, documents.governanceReportPath())).append("\n\n");
        appendPathList(builder, "Recommended read files", textList(documents.plan().get("readCandidates")), 8);
        appendPathList(builder, "Allowed write files", textList(documents.policy().get("writeScope")), 8);
        appendPathList(builder, "Forbidden scope", textList(documents.policy().get("forbiddenScope")), 8);
        builder.append("If the task requires reading or editing outside this scope, explain why in the structured artifact so Codex and the human gate can review the scope expansion.\n\n");
    }

    private static Map<String, Object> buildRepoMap(
            Path workspaceRoot,
            Path workstreamDir,
            TaskHandler.TaskContext context,
            ObjectMapper objectMapper
    ) throws IOException {
        var files = new ArrayList<Map<String, Object>>();
        try (Stream<Path> stream = Files.walk(workspaceRoot, 8)) {
            for (var path : stream
                    .filter(Files::isRegularFile)
                    .filter(path -> includeRepoFile(workspaceRoot, path))
                    .sorted(Comparator.comparing(Path::toString))
                    .limit(MAX_REPO_FILES)
                    .toList()) {
                var relative = workspaceRoot.relativize(path.toAbsolutePath().normalize()).toString();
                var item = new LinkedHashMap<String, Object>();
                item.put("path", relative);
                item.put("kind", fileKind(relative));
                item.put("sizeBytes", safeSize(path));
                files.add(item);
            }
        }
        var document = new LinkedHashMap<String, Object>();
        document.put("schemaVersion", 1);
        document.put("artifactType", "repo_map");
        document.put("workstreamId", context.workstreamId());
        document.put("generatedAt", Instant.now().toString());
        document.put("effectiveWorkdir", workspaceRoot.toAbsolutePath().normalize().toString());
        document.put("baseCommit", readBaseCommit(workstreamDir, objectMapper));
        document.put("fileLimit", MAX_REPO_FILES);
        document.put("files", files);
        document.put("fileCount", files.size());
        document.put("truncated", files.size() >= MAX_REPO_FILES);
        return document;
    }

    private static Map<String, Object> buildFileRelevancePlan(
            Path workspaceRoot,
            Path workstreamDir,
            TaskHandler.TaskContext context,
            EvidenceManifest manifest,
            Map<String, Object> repoMap,
            ObjectMapper objectMapper,
            String stageId
    ) throws IOException {
        var candidates = new LinkedHashSet<String>();
        addInputRefs(candidates, context);
        addContractPaths(candidates, workstreamDir, objectMapper);
        addEvidenceFiles(candidates, manifest);
        addGitChangedFiles(candidates, workstreamDir);
        addRepoMapMatches(candidates, repoMap, readRequirement(workstreamDir));
        var readCandidates = bounded(candidates, MAX_SCOPE_FILES);
        var writeCandidates = stageAllowsBusinessWrites(stageId)
                ? readCandidates.stream().filter(RepoScopeGovernance::looksEditable).limit(MAX_SCOPE_FILES).toList()
                : List.<String>of();

        var document = new LinkedHashMap<String, Object>();
        document.put("schemaVersion", 1);
        document.put("artifactType", "file_relevance_plan");
        document.put("workstreamId", context.workstreamId());
        document.put("stageId", stageId);
        document.put("nodeInstanceId", context.instance().nodeInstanceId());
        document.put("nodeDefinitionId", context.definition().id());
        document.put("iterationId", context.instance().iterationId());
        document.put("source", "task_contract+evidence_manifest+git_evidence+repo_map");
        document.put("readCandidates", readCandidates);
        document.put("writeCandidates", writeCandidates);
        document.put("expansionRules", List.of(
                "Prefer files in readCandidates before broad repository search.",
                "When a candidate calls an API or imports a nearby module, read that direct dependency before editing.",
                "If reading outside readCandidates is necessary, explain the reason in the structured artifact."
        ));
        document.put("generatedAt", Instant.now().toString());
        return document;
    }

    private static Map<String, Object> buildStageScopePolicy(
            TaskHandler.TaskContext context,
            String stageId,
            Map<String, Object> plan
    ) {
        var readCandidates = textList(plan.get("readCandidates"));
        var writeCandidates = textList(plan.get("writeCandidates"));
        var policy = new LinkedHashMap<String, Object>();
        policy.put("schemaVersion", 1);
        policy.put("artifactType", "stage_scope_policy");
        policy.put("workstreamId", context.workstreamId());
        policy.put("stageId", stageId);
        policy.put("nodeInstanceId", context.instance().nodeInstanceId());
        policy.put("nodeDefinitionId", context.definition().id());
        policy.put("iterationId", context.instance().iterationId());
        policy.put("readScope", readCandidates);
        policy.put("writeScope", writeCandidates);
        policy.put("forbiddenScope", forbiddenScope(stageId));
        policy.put("explorationBudget", Map.of(
                "extraReadFiles", stageAllowsBroadExploration(stageId) ? 12 : 6,
                "extraWriteFiles", stageAllowsBusinessWrites(stageId) ? 3 : 0
        ));
        policy.put("scopeExceedPolicy", scopeExceedPolicy(stageId));
        policy.put("generatedAt", Instant.now().toString());
        return policy;
    }

    private static Map<String, Object> buildInitialReport(
            TaskHandler.TaskContext context,
            String stageId,
            Map<String, Object> plan,
            Map<String, Object> policy
    ) {
        var report = new LinkedHashMap<String, Object>();
        report.put("schemaVersion", 1);
        report.put("artifactType", "context_governance_report");
        report.put("workstreamId", context.workstreamId());
        report.put("stageId", stageId);
        report.put("nodeInstanceId", context.instance().nodeInstanceId());
        report.put("nodeDefinitionId", context.definition().id());
        report.put("iterationId", context.instance().iterationId());
        report.put("reportPhase", "planned");
        report.put("plannedReadFiles", textList(plan.get("readCandidates")));
        report.put("plannedWriteFiles", textList(policy.get("writeScope")));
        report.put("actualReadFiles", List.of());
        report.put("actualWriteFiles", List.of());
        report.put("outsideScopeReads", List.of());
        report.put("outsideScopeWrites", List.of());
        report.put("telemetryPending", true);
        report.put("generatedAt", Instant.now().toString());
        return report;
    }

    private static void addInputRefs(Set<String> candidates, TaskHandler.TaskContext context) {
        for (var ref : context.instance().inputRefs()) {
            addPathCandidate(candidates, ref);
        }
    }

    private static void addContractPaths(Set<String> candidates, Path workstreamDir, ObjectMapper objectMapper) throws IOException {
        var contractPath = workstreamDir.resolve("artifacts/task-contract.json");
        if (!Files.isRegularFile(contractPath)) {
            return;
        }
        var json = objectMapper.readValue(contractPath.toFile(), MAP_TYPE);
        var scope = mapValue(json.get("scope"));
        for (var item : textList(scope.get("included"))) {
            addPathCandidate(candidates, item);
        }
        for (var item : textList(json.get("acceptance_criteria"))) {
            addPathCandidate(candidates, item);
        }
    }

    private static void addEvidenceFiles(Set<String> candidates, EvidenceManifest manifest) {
        if (manifest == null) {
            return;
        }
        for (var item : manifest.items()) {
            item.files().forEach(file -> addPathCandidate(candidates, file));
        }
    }

    private static void addGitChangedFiles(Set<String> candidates, Path workstreamDir) throws IOException {
        var path = workstreamDir.resolve("artifacts/git-evidence/changed-files.txt");
        if (!Files.isRegularFile(path)) {
            return;
        }
        for (var line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            addPathCandidate(candidates, line);
        }
    }

    private static void addRepoMapMatches(Set<String> candidates, Map<String, Object> repoMap, String requirement) {
        var terms = requirementTerms(requirement);
        for (var item : mapList(repoMap.get("files"))) {
            var path = text(item.get("path"));
            if (path.isBlank()) {
                continue;
            }
            var lower = path.toLowerCase(java.util.Locale.ROOT);
            if (terms.stream().anyMatch(lower::contains)) {
                addPathCandidate(candidates, path);
            }
            if (candidates.size() >= MAX_SCOPE_FILES) {
                return;
            }
        }
    }

    private static List<String> requirementTerms(String requirement) {
        if (requirement == null || requirement.isBlank()) {
            return List.of();
        }
        var terms = new LinkedHashSet<String>();
        for (var token : requirement.toLowerCase(java.util.Locale.ROOT).split("[^\\p{IsAlphabetic}\\p{IsDigit}_/-]+")) {
            if (token.length() >= 4) {
                terms.add(token);
            }
        }
        return terms.stream().limit(16).toList();
    }

    private static void addPathCandidate(Set<String> candidates, String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        for (var token : raw.split("[\\s,;:()\\[\\]\"'`]+")) {
            var cleaned = token.strip();
            if (cleaned.startsWith(".agent/") || cleaned.startsWith("/")) {
                continue;
            }
            if (PATHISH.matcher(cleaned).matches() && !ignoredPath(cleaned)) {
                candidates.add(cleaned);
            }
        }
    }

    private static boolean includeRepoFile(Path workspaceRoot, Path path) {
        var relative = workspaceRoot.toAbsolutePath().normalize().relativize(path.toAbsolutePath().normalize());
        for (var part : relative) {
            if (IGNORED_DIRS.contains(part.toString())) {
                return false;
            }
        }
        var ref = relative.toString();
        if (ignoredPath(ref)) {
            return false;
        }
        return safeSize(path) <= 512_000;
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

    private static boolean looksEditable(String path) {
        return !path.startsWith("docs/")
                && !path.endsWith(".md")
                && !path.endsWith(".lock")
                && !path.contains("/generated/");
    }

    private static boolean stageAllowsBusinessWrites(String stageId) {
        return "implement".equals(stageId) || "self_test".equals(stageId);
    }

    private static boolean stageAllowsBroadExploration(String stageId) {
        return "intake".equals(stageId) || "design".equals(stageId) || "plan".equals(stageId);
    }

    private static List<String> forbiddenScope(String stageId) {
        var base = new ArrayList<>(List.of(".git/**", ".agent/workstreams/**/workflow/**", ".agent/workstreams/**/nodes/**"));
        if (!stageAllowsBusinessWrites(stageId)) {
            base.add("business source writes");
        }
        return List.copyOf(base);
    }

    private static String scopeExceedPolicy(String stageId) {
        return switch (stageId) {
            case "delivery" -> "block_business_code_edits";
            case "implement", "self_test" -> "warn";
            default -> "record_only";
        };
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

    private static String readRequirement(Path workstreamDir) throws IOException {
        var path = workstreamDir.resolve("requirement.md");
        return Files.isRegularFile(path) ? Files.readString(path, StandardCharsets.UTF_8) : "";
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

    private static String safeFileName(String value) {
        return value == null || value.isBlank() ? "unknown" : value.replaceAll("[^A-Za-z0-9._-]", "_");
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

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    record Documents(
            Map<String, Object> repoMap,
            Map<String, Object> plan,
            Map<String, Object> policy,
            Map<String, Object> report,
            Path repoMapPath,
            Path fileRelevancePlanPath,
            Path stageScopePolicyPath,
            Path governanceReportPath
    ) {
    }
}
