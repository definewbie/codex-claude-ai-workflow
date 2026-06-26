package dev.dual.runtime.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Read-only projector from {@code workstream-memory.json} into the agent-facing
 * context-package memory sections.
 *
 * <p>The {@code working_memory} section (Claude implement) renders an
 * {@link #appendAgentHandoff AgentHandoff}; the {@code review_evidence} section
 * (Codex review) renders a {@link #appendReviewEvidenceBrief ReviewEvidenceBrief}.
 * Both are projections of the orchestrator-owned {@code WorkstreamMemory} (the
 * source of truth), not of the rebuilt {@code EvidenceManifest}. They embed real
 * ledger evidence refs so accuracy does not depend on dump volume: scope source,
 * prior verdict artifact path, changed-files/diff path, and readFiles with
 * path+sha+summary+trusted/invalidationReason.</p>
 *
 * <p>When memory is empty or key fields are missing, only minimal fallback refs
 * are emitted. The old top-N evidence dump is never restored.</p>
 */
final class WorkstreamMemoryProjection {
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final int MAX_READS = 12;
    private static final int MAX_OPEN_ISSUES = 12;
    private static final int MAX_CHANGED_FILES = 16;

    private WorkstreamMemoryProjection() {
    }

    /**
     * Claude implement handoff: decided scope, non-goals, reusable + invalidated
     * reads, last stage outcome, open issues, the reuse directive, and evidence
     * refs. Appends section body only (the caller writes the {@code ##} heading).
     */
    static void appendAgentHandoff(StringBuilder builder, Path workstreamDir, Path workspaceRoot) {
        var memory = readMemory(workstreamDir);
        var decidedScope = strings(memory.get("decidedScope"));
        var nonGoals = strings(memory.get("nonGoals"));
        var reads = list(memory.get("readFiles"));
        var openIssues = openIssues(list(memory.get("openIssues")));
        var lastOutcome = map(memory.get("lastStageOutcome"));
        var decisions = list(memory.get("decisions"));
        var hasAny = !decidedScope.isEmpty() || !nonGoals.isEmpty() || !reads.isEmpty()
                || !openIssues.isEmpty() || !decisions.isEmpty() || !lastOutcome.isEmpty();

        builder.append("Orchestrator-owned memory for this workstream. Reuse it instead of re-reading files or re-deriving scope.\n\n");

        appendBullets(builder, "Decided scope", decidedScope, MAX_OPEN_ISSUES);
        appendBullets(builder, "Non-goals", nonGoals, MAX_OPEN_ISSUES);
        appendReusableReads(builder, reads, "Reusable reads (summary trusted, do not re-read)");
        appendInvalidatedReads(builder, reads);
        appendLastOutcome(builder, lastOutcome, decisions);
        appendBullets(builder, "Open issues (resolve before claiming done)", issueTexts(openIssues), MAX_OPEN_ISSUES);

        builder.append("\nDo NOT re-explore from README/docs/source/test to re-derive this scope. ")
                .append("Re-read a listed file only when editing or verifying it, its sha changed, or the summary does not cover this stage — ")
                .append("and record the reason in your structured artifact.\n\n");

        if (!hasAny) {
            builder.append("(memory has no recorded scope, reads, or decisions yet for this workstream; ")
                    .append("proceed with the task contract and read-reuse contract below.)\n\n");
        }
        appendEvidenceRefs(builder, workstreamDir, workspaceRoot);
    }

    /**
     * Codex review brief: prior verdict + unresolved must-fix, open issues,
     * acceptance criteria, this round's changed files, a compact reusable-read /
     * decision summary, the incremental-audit directive, and evidence refs.
     * Appends section body only (the caller writes the {@code ##} heading).
     */
    static void appendReviewEvidenceBrief(StringBuilder builder, Path workstreamDir, Path workspaceRoot) {
        var memory = readMemory(workstreamDir);
        var contract = readContract(workstreamDir);
        var verdicts = list(memory.get("priorVerdicts"));
        var openIssues = openIssues(list(memory.get("openIssues")));
        var acceptance = strings(contract.get("acceptance_criteria"));
        var changedFiles = readChangedFiles(workstreamDir);
        var reusablePaths = reusableReadPaths(memory);

        var hasAny = !verdicts.isEmpty() || !openIssues.isEmpty() || !acceptance.isEmpty()
                || !changedFiles.isEmpty() || !reusablePaths.isEmpty();

        if (!hasAny) {
            builder.append("(no prior verdict, open issues, changed files, or memory recorded yet) Evidence: ")
                    .append(ref(workspaceRoot, workstreamDir.resolve("memory/workstream-memory.json")))
                    .append("; ")
                    .append(ref(workspaceRoot, workstreamDir.resolve("artifacts/task-contract.json")))
                    .append(".\n\n");
            return;
        }

        builder.append("Incremental acceptance from orchestrator memory; do NOT re-audit the whole repo.\n\n");

        if (!verdicts.isEmpty()) {
            var verdict = verdicts.get(verdicts.size() - 1);
            var line = new StringBuilder();
            if (!text(verdict.get("stage")).isBlank()) {
                line.append(text(verdict.get("stage"))).append(" / ");
            }
            line.append(text(verdict.get("decision")));
            if (Boolean.TRUE.equals(verdict.get("blocking"))) {
                line.append(" (blocking)");
            }
            builder.append("**Prior verdict:** ").append(line).append("\n\n");
            appendBullets(builder, "Prior must-fix (verify resolved)", strings(verdict.get("mustFix")), MAX_OPEN_ISSUES);
        }
        appendBullets(builder, "Open issues to confirm", issueTexts(openIssues), MAX_OPEN_ISSUES);
        appendBullets(builder, "Acceptance criteria", acceptance, MAX_OPEN_ISSUES);
        appendBullets(builder, "Changed files this round", changedFiles, MAX_CHANGED_FILES);
        if (!reusablePaths.isEmpty()) {
            builder.append("- Relevant memory (reuse, do not re-read): ").append(String.join("; ", reusablePaths)).append("\n\n");
        }

        builder.append("Confirm prior must-fix against this round's diff and acceptance criteria; surface new risks. ")
                .append("Re-read a file only with a reason (edit/verify/sha-changed/stage-gap).\n");
        builder.append("Evidence: memory ").append(ref(workspaceRoot, workstreamDir.resolve("memory/workstream-memory.json")))
                .append("; diff ").append(ref(workspaceRoot, workstreamDir.resolve("artifacts/git-evidence/diff.patch")))
                .append("; changed ").append(ref(workspaceRoot, workstreamDir.resolve("artifacts/git-evidence/changed-files.txt")))
                .append(".\n\n");
    }

    private static List<String> reusableReadPaths(Map<String, Object> memory) {
        var out = new ArrayList<String>();
        for (var read : list(memory.get("readFiles")).stream()
                .filter(r -> Boolean.TRUE.equals(r.get("trusted")) && text(r.get("invalidationReason")).isBlank())
                .limit(5).toList()) {
            var path = text(read.get("path"));
            if (!path.isBlank()) {
                out.add(path);
            }
        }
        return out;
    }

    private static void appendReusableReads(StringBuilder builder, List<Map<String, Object>> reads, String label) {
        var items = new ArrayList<String>();
        for (var read : reads.stream().limit(MAX_READS).toList()) {
            if (!Boolean.TRUE.equals(read.get("trusted")) || !text(read.get("invalidationReason")).isBlank()) {
                continue;
            }
            items.add(text(read.get("path")) + " — " + text(read.get("summary"))
                    + " [sha " + shortSha(text(read.get("sha"))) + ", read " + text(read.get("readRunId")) + "]"
                    + " reuse unless editing/verifying");
        }
        appendBullets(builder, label, items, MAX_READS);
    }

    private static void appendInvalidatedReads(StringBuilder builder, List<Map<String, Object>> reads) {
        var items = new ArrayList<String>();
        for (var read : reads.stream().limit(MAX_READS).toList()) {
            var reason = text(read.get("invalidationReason"));
            if (reason.isBlank()) {
                continue;
            }
            items.add(text(read.get("path")) + " — fresh read allowed (" + reason + ")");
        }
        appendBullets(builder, "Invalidated reads (fresh read allowed)", items, MAX_READS);
    }

    private static void appendLastOutcome(StringBuilder builder, Map<String, Object> outcome, List<Map<String, Object>> decisions) {
        Map<String, Object> latest = outcome.isEmpty() ? latestDecision(decisions) : outcome;
        if (latest.isEmpty()) {
            return;
        }
        var stage = text(latest.getOrDefault("stage", latest.get("stageId")));
        var decision = text(latest.get("decision"));
        var summary = text(latest.get("summary"));
        var verification = text(latest.get("verificationSummary"));
        var line = new StringBuilder();
        if (!stage.isBlank()) {
            line.append(stage).append(" / ");
        }
        if (!decision.isBlank()) {
            line.append(decision);
        }
        if (!summary.isBlank()) {
            line.append(line.isEmpty() ? "" : " — ").append(summary);
        }
        if (!verification.isBlank()) {
            line.append(" (verification: ").append(verification).append(")");
        }
        if (!line.isEmpty()) {
            builder.append("**Last stage outcome:**\n- ").append(line).append("\n\n");
        }
    }

    private static Map<String, Object> latestDecision(List<Map<String, Object>> decisions) {
        if (decisions.isEmpty()) {
            return Map.of();
        }
        return decisions.get(decisions.size() - 1);
    }

    private static List<Map<String, Object>> openIssues(List<Map<String, Object>> issues) {
        var open = new ArrayList<Map<String, Object>>();
        for (var issue : issues) {
            if (!"resolved".equalsIgnoreCase(text(issue.get("status")))) {
                open.add(issue);
            }
        }
        return open;
    }

    private static List<String> issueTexts(List<Map<String, Object>> issues) {
        var texts = new ArrayList<String>();
        for (var issue : issues) {
            var desc = text(issue.get("desc"));
            if (!desc.isBlank()) {
                texts.add(desc);
            }
        }
        return texts;
    }

    private static void appendBullets(StringBuilder builder, String label, List<String> items, int limit) {
        var bounded = items.stream().filter(s -> !s.isBlank()).distinct().limit(limit).toList();
        if (bounded.isEmpty()) {
            return;
        }
        builder.append("**").append(label).append(":**\n");
        for (var item : bounded) {
            builder.append("- ").append(item).append("\n");
        }
        builder.append("\n");
    }

    private static void appendEvidenceRefs(StringBuilder builder, Path workstreamDir, Path workspaceRoot) {
        builder.append("Evidence refs:\n");
        builder.append("- Memory: ").append(ref(workspaceRoot, workstreamDir.resolve("memory/workstream-memory.json"))).append("\n");
        builder.append("- Scope source: ").append(ref(workspaceRoot, workstreamDir.resolve("artifacts/task-contract.json"))).append("\n");
        builder.append("- Read-reuse contract: ").append(ref(workspaceRoot, workstreamDir.resolve("memory/read-reuse-contract.json"))).append("\n\n");
    }

    private static Map<String, Object> readMemory(Path workstreamDir) {
        return readJson(workstreamDir.resolve("memory/workstream-memory.json"));
    }

    private static Map<String, Object> readContract(Path workstreamDir) {
        return readJson(workstreamDir.resolve("artifacts/task-contract.json"));
    }

    private static Map<String, Object> readJson(Path path) {
        if (!Files.isRegularFile(path)) {
            return Map.of();
        }
        try {
            return MAPPER.readValue(path.toFile(), MAP_TYPE);
        } catch (IOException | RuntimeException ignored) {
            return Map.of();
        }
    }

    private static List<String> readChangedFiles(Path workstreamDir) {
        var path = workstreamDir.resolve("artifacts/git-evidence/changed-files.txt");
        var out = new ArrayList<String>();
        if (!Files.isRegularFile(path)) {
            return out;
        }
        try {
            for (var line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                var trimmed = line.trim();
                if (!trimmed.isBlank()) {
                    out.add(trimmed);
                }
            }
        } catch (IOException ignored) {
            return out;
        }
        return out;
    }

    private static String shortSha(String sha) {
        return sha.length() > 8 ? sha.substring(0, 8) : sha;
    }

    private static String ref(Path workspaceRoot, Path path) {
        return workspaceRoot.toAbsolutePath().normalize()
                .relativize(path.toAbsolutePath().normalize()).toString();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Object value) {
        if (!(value instanceof List<?> raw) || raw.isEmpty()) {
            return List.of();
        }
        var out = new ArrayList<Map<String, Object>>();
        for (var item : raw) {
            if (item instanceof Map<?, ?> map) {
                var copy = new java.util.LinkedHashMap<String, Object>();
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

    private static Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> raw)) {
            return Map.of();
        }
        var copy = new java.util.LinkedHashMap<String, Object>();
        for (var e : raw.entrySet()) {
            if (e.getKey() instanceof String key) {
                copy.put(key, e.getValue());
            }
        }
        return copy;
    }

    private static List<String> strings(Object value) {
        if (!(value instanceof List<?> raw)) {
            return List.of();
        }
        var out = new ArrayList<String>();
        for (var item : raw) {
            var t = text(item);
            if (!t.isBlank()) {
                out.add(t);
            }
        }
        return out;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
