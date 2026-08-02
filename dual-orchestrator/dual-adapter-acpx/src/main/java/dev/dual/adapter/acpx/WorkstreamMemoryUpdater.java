package dev.dual.adapter.acpx;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.dual.core.contract.TaskContract;
import dev.dual.core.execution.AdapterSignal;
import dev.dual.runtime.handler.AgentOutputContractDocument;
import dev.dual.runtime.handler.AgentOutputContractSidecar;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Updates the per-workstream semantic memory from typed execution facts.
 *
 * <p>This is the single writer for orchestrator-owned semantic memory. It
 * consumes only typed sources: {@link AcpxToolUsage#readFiles()} for reads,
 * {@code artifacts/task-contract.json} for decided scope / non-goals, and the
 * run's validated {@code agent_output_contract} sidecar for decisions / verdicts / open
 * issues / stage outcome, plus the classified failure for prior failures. It
 * does not parse agent prose, stdout, or markdown summaries as fact. The memory
 * file is a durable artifact owned by the workstream; prompt manifests, context
 * package projections, and trace commands remain projections over it.</p>
 */
final class WorkstreamMemoryUpdater {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final int MAX_DECISIONS = 12;
    private static final int MAX_PRIOR_VERDICTS = 8;
    private static final int MAX_OPEN_ISSUES = 24;
    private static final int MAX_PRIOR_FAILURES = 8;
    private static final int MAX_READ_FILES = 64;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT);

    Path memoryPath(Path workspaceRoot, String workstreamId) {
        return workspaceRoot.resolve(".agent").resolve("workstreams").resolve(workstreamId)
                .resolve("memory").resolve("workstream-memory.json");
    }

    /**
     * Single entry point for all semantic-memory updates after one agent run.
     * Reads existing memory, merges typed facts, and atomically rewrites the
     * file. Every branch tolerates missing inputs (no task contract, no
     * envelope, no failure) so a partial run still records what it can.
     */
    void updateMemory(
            Path workspaceRoot,
            String workstreamId,
            String runId,
            String nodeId,
            String stage,
            String agent,
            Path outputPath,
            AcpxToolUsage toolUsage,
            List<AdapterSignal> adapterSignals
    ) throws IOException {
        if (workspaceRoot == null || workstreamId == null || workstreamId.isBlank()) {
            return;
        }
        var memoryPath = memoryPath(workspaceRoot, workstreamId);
        var memory = readExisting(memoryPath, workstreamId);
        var workstreamDir = workspaceRoot.resolve(".agent").resolve("workstreams").resolve(workstreamId);

        var taskContract = readContract(workstreamDir);
        mergeContractScope(memory, taskContract);
        var keywords = contractKeywords(taskContract);

        var readFiles = byPath(memory.get("readFiles"));
        refreshInvalidation(workspaceRoot, readFiles);
        mergeReadFiles(memory, workspaceRoot, runId, nodeId, stage, toolUsage, keywords, readFiles);

        extractContract(outputPath).ifPresent(
                contract -> mergeRunOutcome(memory, contract, runId, nodeId, stage, agent));
        var activeSignal = adapterSignals == null ? null : adapterSignals.stream()
                .filter(signal -> !signal.superseded() && signal.type().blocksSuccess())
                .findFirst()
                .orElse(null);
        if (activeSignal != null) {
            mergeFailure(memory, activeSignal, runId, nodeId);
        }

        writeMemory(memoryPath, workstreamId, memory);
    }

    /**
     * Legacy entry retained so existing callers/tests keep compiling. Delegates
     * to {@link #updateMemory} with no output/failure context (reads only).
     */
    void updateReadFiles(
            Path workspaceRoot,
            String workstreamId,
            String runId,
            String nodeId,
            String stage,
            AcpxToolUsage toolUsage
    ) throws IOException {
        updateMemory(workspaceRoot, workstreamId, runId, nodeId, stage, "", null, toolUsage, null);
    }

    private void mergeContractScope(Map<String, Object> memory, Optional<TaskContract> contract) {
        if (contract.isEmpty()) {
            memory.putIfAbsent("decidedScope", List.of());
            memory.putIfAbsent("nonGoals", List.of());
            return;
        }
        var document = contract.orElseThrow();
        var decidedScope = document.scope() == null
                ? List.<String>of()
                : document.scope().included().stream()
                        .map(TaskContract.ScopeEntry::path)
                        .toList();
        memory.put("decidedScope", decidedScope);
        memory.put("nonGoals", document.nonGoals());
    }

    private Optional<TaskContract> readContract(Path workstreamDir) {
        var contractPath = workstreamDir.resolve("artifacts/task-contract.json");
        if (!Files.isRegularFile(contractPath)) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(contractPath.toFile(), TaskContract.class));
        } catch (IOException ignored) {
            return Optional.empty();
        }
    }

    private static Set<String> contractKeywords(Optional<TaskContract> contract) {
        var phrases = new ArrayList<String>();
        contract.ifPresent(document -> {
            if (document.scope() != null) {
                document.scope().included().stream()
                        .map(TaskContract.ScopeEntry::path)
                        .forEach(phrases::add);
            }
            phrases.addAll(document.acceptanceCriteria());
        });
        var tokens = new LinkedHashSet<String>();
        for (var phrase : phrases) {
            for (var token : phrase.toLowerCase(Locale.ROOT).split("[^\\p{IsAlphabetic}\\p{IsDigit}_/-]+")) {
                if (token.length() >= 4) {
                    tokens.add(token);
                }
            }
        }
        return tokens;
    }

    private void mergeReadFiles(
            Map<String, Object> memory,
            Path workspaceRoot,
            String runId,
            String nodeId,
            String stage,
            AcpxToolUsage toolUsage,
            Set<String> keywords,
            Map<String, Map<String, Object>> readFiles
    ) {
        var files = toolUsage == null ? List.<String>of() : normalizedRepoFiles(toolUsage.readFiles());
        var now = Instant.now().toString();
        for (var path : files) {
            var absolute = workspaceRoot.resolve(path).normalize();
            if (!Files.isRegularFile(absolute)) {
                continue;
            }
            var entry = readFiles.computeIfAbsent(path, ignored -> new LinkedHashMap<>());
            entry.put("path", path);
            entry.put("evidenceRole", evidenceRole(path));
            entry.put("readAt", now);
            entry.put("readRunId", runId);
            entry.put("readNodeId", nodeId);
            entry.put("lastSeenStage", stage == null ? "" : stage);
            try {
                entry.put("sha", sha256(absolute));
                entry.put("summary", semanticSummary(absolute, path, keywords));
                entry.put("trusted", true);
                entry.put("invalidationReason", "");
            } catch (IOException ignored) {
                entry.put("trusted", false);
                entry.put("invalidationReason", "file metadata unavailable");
            }
        }
        memory.put("readFiles", readFiles.values().stream()
                .sorted(Comparator.comparing(item -> String.valueOf(item.get("path"))))
                .limit(MAX_READ_FILES)
                .toList());
    }

    private Optional<AgentOutputContractDocument> extractContract(Path outputPath) {
        if (outputPath == null || !Files.isRegularFile(outputPath)) {
            return Optional.empty();
        }
        try {
            return AgentOutputContractSidecar.read(outputPath, objectMapper);
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private void mergeRunOutcome(
            Map<String, Object> memory,
            AgentOutputContractDocument contract,
            String runId,
            String nodeId,
            String stage,
            String agent
    ) {
        if (contract.hasDecision()) {
            var decision = new LinkedHashMap<String, Object>();
            decision.put("stage", text(stage));
            decision.put("nodeId", text(nodeId));
            decision.put("runId", text(runId));
            decision.put("decision", contract.decision());
            decision.put("summary", contract.summary());
            appendDedup(memory, "decisions", decision,
                    d -> text(d.get("nodeId")) + "|" + text(d.get("decision")) + "|" + text(d.get("summary")),
                    MAX_DECISIONS);

            if (isReviewVerdictNode(nodeId, stage)) {
                var verdict = new LinkedHashMap<String, Object>();
                verdict.put("iterationId", "");
                verdict.put("nodeId", text(nodeId));
                verdict.put("stage", text(stage));
                verdict.put("decision", contract.decision());
                verdict.put("blocking", contract.blocking());
                verdict.put("mustFix", contract.mustFix());
                appendDedup(memory, "priorVerdicts", verdict,
                        v -> text(v.get("nodeId")) + "|" + text(v.get("decision")),
                        MAX_PRIOR_VERDICTS);
            }
        }

        if (isOpenVerdict(contract) || contract.blocking()) {
            addOpenIssues(memory, contract, runId, nodeId, agent);
        } else if (isPositiveVerdict(contract)) {
            resolveOpenIssuesFor(memory, nodeId);
        }

        var outcome = new LinkedHashMap<String, Object>();
        outcome.put("stage", text(stage));
        outcome.put("nodeId", text(nodeId));
        outcome.put("runId", text(runId));
        outcome.put("outcome", contract.canonicalStageOutcome());
        outcome.put("decision", contract.decision());
        outcome.put("verificationSummary", contract.verificationSummary());
        memory.put("lastStageOutcome", outcome);
    }

    private void addOpenIssues(
            Map<String, Object> memory,
            AgentOutputContractDocument contract,
            String runId,
            String nodeId,
            String agent
    ) {
        var byId = new LinkedHashMap<String, Map<String, Object>>();
        for (var item : listValue(memory.get("openIssues"))) {
            var id = text(item.get("id"));
            if (!id.isBlank()) {
                byId.put(id, new LinkedHashMap<>(item));
            }
        }
        var raisedBy = !text(agent).isBlank() ? agent : text(nodeId);
        for (var fact : combinedOpenIssueFacts(contract)) {
            var desc = fact.desc();
            if (desc.isBlank()) {
                continue;
            }
            var id = shortHash(desc);
            var issue = byId.computeIfAbsent(id, ignored -> new LinkedHashMap<String, Object>());
            issue.put("id", id);
            issue.put("sourceField", fact.sourceField());
            issue.put("desc", desc);
            issue.put("raisedBy", raisedBy);
            issue.put("raisedNodeId", text(nodeId));
            issue.put("raisedRunId", text(runId));
            issue.put("raisedIterationId", "");
            issue.put("status", "open");
        }
        memory.put("openIssues", byId.values().stream().limit(MAX_OPEN_ISSUES).toList());
    }

    private void resolveOpenIssuesFor(Map<String, Object> memory, String nodeId) {
        var existing = listValue(memory.get("openIssues"));
        if (existing.isEmpty()) {
            return;
        }
        var updated = new ArrayList<Map<String, Object>>();
        var changed = false;
        for (var item : existing) {
            if ("open".equals(text(item.get("status"))) && text(nodeId).equals(text(item.get("raisedNodeId")))) {
                var copy = new LinkedHashMap<>(item);
                copy.put("status", "resolved");
                updated.add(copy);
                changed = true;
            } else {
                updated.add(item);
            }
        }
        if (changed) {
            memory.put("openIssues", updated);
        }
    }

    private static List<OpenIssueFact> combinedOpenIssueFacts(AgentOutputContractDocument contract) {
        var all = new ArrayList<OpenIssueFact>();
        contract.mustFix().forEach(item -> all.add(new OpenIssueFact("must_fix", item)));
        contract.openRisks().forEach(item -> all.add(new OpenIssueFact("open_risks", item)));
        contract.missingEvidence().forEach(item -> all.add(new OpenIssueFact("missing_evidence", item)));
        return all;
    }

    private record OpenIssueFact(String sourceField, String desc) {
    }

    private void mergeFailure(
            Map<String, Object> memory,
            AdapterSignal signal,
            String runId,
            String nodeId
    ) {
        var entry = new LinkedHashMap<String, Object>();
        entry.put("node", text(nodeId));
        entry.put("runId", text(runId));
        entry.put("reason", signal.type().name());
        entry.put("resolved", false);
        appendDedup(memory, "priorFailures", entry,
                f -> text(f.get("node")) + "|" + text(f.get("reason")),
                MAX_PRIOR_FAILURES);
    }

    @SuppressWarnings("unchecked")
    private void appendDedup(
            Map<String, Object> memory,
            String key,
            Map<String, Object> newEntry,
            Function<Map<String, Object>, String> keyFn,
            int max
    ) {
        var existing = new ArrayList<Map<String, Object>>();
        for (var item : listValue(memory.get(key))) {
            existing.add(new LinkedHashMap<>(item));
        }
        var newKey = keyFn.apply(newEntry);
        existing.removeIf(item -> newKey.equals(keyFn.apply(item)));
        existing.add(newEntry);
        if (existing.size() > max) {
            existing = new ArrayList<>(existing.subList(existing.size() - max, existing.size()));
        }
        memory.put(key, existing);
    }

    private void writeMemory(Path memoryPath, String workstreamId, Map<String, Object> memory) throws IOException {
        memory.put("schemaVersion", 1);
        memory.put("artifactType", "workstream_memory");
        memory.put("workstreamId", workstreamId);
        memory.putIfAbsent("decidedScope", List.of());
        memory.putIfAbsent("nonGoals", List.of());
        memory.putIfAbsent("decisions", List.of());
        memory.putIfAbsent("readFiles", List.of());
        memory.putIfAbsent("openIssues", List.of());
        memory.putIfAbsent("priorVerdicts", List.of());
        memory.putIfAbsent("priorFailures", List.of());
        memory.putIfAbsent("lastStageOutcome", Map.of());
        memory.putIfAbsent("fieldSemantics", fieldSemantics());
        memory.put("updatedAt", Instant.now().toString());
        Files.createDirectories(memoryPath.getParent());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(memoryPath.toFile(), memory);
    }

    private Map<String, Object> readExisting(Path memoryPath, String workstreamId) throws IOException {
        if (!Files.isRegularFile(memoryPath)) {
            var memory = new LinkedHashMap<String, Object>();
            memory.put("schemaVersion", 1);
            memory.put("artifactType", "workstream_memory");
            memory.put("workstreamId", workstreamId);
            memory.put("decidedScope", List.of());
            memory.put("nonGoals", List.of());
            memory.put("decisions", List.of());
            memory.put("readFiles", List.of());
            memory.put("openIssues", List.of());
            memory.put("priorVerdicts", List.of());
            memory.put("priorFailures", List.of());
            memory.put("lastStageOutcome", Map.of());
            memory.put("fieldSemantics", fieldSemantics());
            return memory;
        }
        try {
            return new LinkedHashMap<>(objectMapper.readValue(memoryPath.toFile(), MAP_TYPE));
        } catch (IOException ignored) {
            var memory = new LinkedHashMap<String, Object>();
            memory.put("schemaVersion", 1);
            memory.put("artifactType", "workstream_memory");
            memory.put("workstreamId", workstreamId);
            memory.put("readFiles", List.of());
            memory.put("fieldSemantics", fieldSemantics());
            return memory;
        }
    }

    private static Map<String, Object> fieldSemantics() {
        return Map.of(
                "machineDecisionFields", List.of("decision", "stage_outcome", "lifecycle_status", "blocking", "sourceField", "status"),
                "projectionOnlyFields", List.of("summary", "desc", "verificationSummary")
        );
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> byPath(Object value) {
        var byPath = new LinkedHashMap<String, Map<String, Object>>();
        if (!(value instanceof List<?> list)) {
            return byPath;
        }
        for (var item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            var path = text(map.get("path"));
            if (path.isBlank()) {
                continue;
            }
            byPath.put(path, new LinkedHashMap<>((Map<String, Object>) map));
        }
        return byPath;
    }

    private static void refreshInvalidation(Path workspaceRoot, Map<String, Map<String, Object>> readFiles) {
        for (var entry : readFiles.values()) {
            var path = text(entry.get("path"));
            if (path.isBlank()) {
                continue;
            }
            var absolute = workspaceRoot.resolve(path).normalize();
            if (!Files.isRegularFile(absolute)) {
                entry.put("trusted", false);
                entry.put("invalidationReason", "file missing since prior read");
                continue;
            }
            try {
                var currentSha = sha256(absolute);
                var priorSha = text(entry.get("sha"));
                if (!priorSha.isBlank() && !priorSha.equals(currentSha)) {
                    entry.put("trusted", false);
                    entry.put("invalidationReason", "file changed since prior read");
                }
            } catch (IOException ignored) {
                entry.put("trusted", false);
                entry.put("invalidationReason", "file hash unavailable");
            }
        }
    }

    private static boolean isReviewVerdictNode(String nodeId, String stage) {
        var n = text(nodeId).toLowerCase(Locale.ROOT);
        var s = text(stage).toLowerCase(Locale.ROOT);
        return n.contains("review") || n.contains("verifier") || n.contains("acceptance")
                || s.contains("review") || s.contains("acceptance");
    }

    private static boolean isOpenVerdict(AgentOutputContractDocument contract) {
        var d = contract.decision();
        return "REJECT".equals(d) || "REJECTED".equals(d)
                || "NEEDS_REVISION".equals(d) || "BLOCKED".equals(d)
                || "NEEDS_CLARIFICATION".equals(d) || "NEEDS_SCOPE_REDUCTION".equals(d);
    }

    private static boolean isPositiveVerdict(AgentOutputContractDocument contract) {
        var d = contract.decision();
        if (d.isBlank() || contract.blocking() || !contract.mustFix().isEmpty()) {
            return false;
        }
        return d.startsWith("READY_") || d.equals("HANDOFF_READY")
                || d.equals("ACCEPT") || d.equals("ACCEPT_WITH_NOTES")
                || d.equals("ACCEPTED") || d.equals("ACCEPTED_WITH_NOTES");
    }

    private static List<String> normalizedRepoFiles(List<String> files) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }
        var normalized = new LinkedHashSet<String>();
        for (var file : files) {
            var path = normalize(file);
            if (!path.isBlank() && !path.startsWith("/") && !path.startsWith(".agent/") && !ignoredPath(path)) {
                normalized.add(path);
            }
        }
        return new ArrayList<>(normalized);
    }

    private static boolean ignoredPath(String path) {
        return path.contains("/node_modules/")
                || path.contains("/target/")
                || path.contains("/dist/")
                || path.contains("/build/")
                || path.startsWith(".git/");
    }

    private static String normalize(String value) {
        var text = value == null ? "" : value.trim();
        while (text.startsWith("./")) {
            text = text.substring(2);
        }
        return text;
    }

    /**
     * Structural, deterministic summary of a read file (symbols / headings /
     * test names), scoped by task-contract keywords. Falls back to a metadata
     * summary when no structure can be extracted. Never throws.
     */
    private static String semanticSummary(Path absolute, String path, Set<String> keywords) {
        var content = readBounded(absolute, 8192);
        var summary = content == null ? "" : ReadFileSummary.summarize(content, path, keywords);
        if (!summary.isBlank()) {
            return summary;
        }
        try {
            return metadataSummary(absolute, path);
        } catch (IOException e) {
            return "Previously read " + evidenceRole(path) + " file.";
        }
    }

    private static String readBounded(Path path, int maxChars) {
        try {
            var content = Files.readString(path);
            return content.length() <= maxChars ? content : content.substring(0, maxChars);
        } catch (IOException e) {
            return null;
        }
    }

    private static String metadataSummary(Path absolute, String path) throws IOException {
        return "Previously read " + evidenceRole(path) + " file (" + Files.size(absolute) + " bytes).";
    }

    private static String evidenceRole(String path) {
        var lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".md") || lower.startsWith("docs/")) {
            return "documentation";
        }
        if (lower.contains("/test/") || lower.contains("/tests/")
                || lower.endsWith(".test.js") || lower.endsWith(".test.ts")
                || lower.endsWith(".spec.js") || lower.endsWith(".spec.ts")) {
            return "test";
        }
        if (lower.endsWith(".json") || lower.endsWith(".yaml") || lower.endsWith(".yml")
                || lower.endsWith(".toml") || lower.endsWith(".xml")) {
            return "configuration";
        }
        return "source";
    }

    private static String shortHash(String text) {
        try {
            var sha = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(sha).substring(0, 12);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(text.hashCode());
        }
    }

    private static String sha256(Path path) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 digest is not available", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> listValue(Object value) {
        if (!(value instanceof List<?> list)) {
            return new ArrayList<>();
        }
        var out = new ArrayList<Map<String, Object>>();
        for (var item : list) {
            if (item instanceof Map<?, ?> map) {
                var copy = new LinkedHashMap<String, Object>();
                for (var e : map.entrySet()) {
                    if (e.getKey() instanceof String key) {
                        copy.put(key, e.getValue());
                    }
                }
                out.add(copy);
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        var copy = new LinkedHashMap<String, Object>();
        for (var e : map.entrySet()) {
            if (e.getKey() instanceof String key) {
                copy.put(key, e.getValue());
            }
        }
        return copy;
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        var out = new ArrayList<String>();
        for (var item : list) {
            var t = text(item);
            if (!t.isBlank()) {
                out.add(t);
            }
        }
        return List.copyOf(out);
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
