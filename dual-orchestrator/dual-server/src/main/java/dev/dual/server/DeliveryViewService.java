package dev.dual.server;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dual.adapter.git.GitPatchMergeService;
import dev.dual.core.id.DualId;
import dev.dual.ledger.file.DeliveryMergePlan;
import dev.dual.ledger.file.DeliveryMergePlanWriter;
import dev.dual.ledger.file.DeliveryMergeStatus;
import dev.dual.ledger.file.LockOwner;
import dev.dual.ledger.file.WorkstreamLock;
import dev.dual.ledger.file.WorkstreamPaths;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds and mutates the delivery view boundary.
 *
 * <p>Delivery is a post-completion command surface. It reads workflow and
 * delivery ledger facts, but it never resumes workflow execution. Target repo
 * writes happen only in {@link #merge(Path, String)} after an explicit operator
 * action.</p>
 */
@Service
public class DeliveryViewService {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final GitPatchMergeService gitPatchMergeService;
    private final DeliveryMergePlanWriter planWriter;

    @Autowired
    public DeliveryViewService(ObjectMapper objectMapper) {
        this(objectMapper, new GitPatchMergeService(), new DeliveryMergePlanWriter());
    }

    DeliveryViewService(ObjectMapper objectMapper, GitPatchMergeService gitPatchMergeService, DeliveryMergePlanWriter planWriter) {
        this.objectMapper = objectMapper;
        this.gitPatchMergeService = gitPatchMergeService;
        this.planWriter = planWriter;
    }

    public Map<String, Object> view(Path workspaceRoot, String workstreamId) {
        var sourceWorktree = workspaceRoot.toAbsolutePath().normalize();
        var paths = paths(sourceWorktree, workstreamId);
        var response = baseResponse(paths, sourceWorktree, workstreamId);
        var targetRepo = targetRepo(paths);
        response.put("targetRepo", targetRepo == null ? "" : targetRepo.toString());

        var existingPlan = readMap(paths.deliveryMergePlanJson());
        if (!existingPlan.isEmpty()) {
            var status = text(existingPlan.get("status"), "UNKNOWN");
            var planTargetRepo = text(existingPlan.get("targetRepo"));
            if (!planTargetRepo.isBlank()) {
                response.put("targetRepo", planTargetRepo);
            }
            response.put("mergePlan", existingPlan);
            response.put("status", status);
            response.put("acceptanceDecision", text(existingPlan.get("acceptanceDecision")));
            response.put("changedFiles", list(existingPlan.get("changedFiles")));
            response.put("excludedFiles", list(existingPlan.get("excludedFiles")));
            response.put("failureReason", text(existingPlan.get("failureReason")));
            response.put("canMerge", "READY".equals(status));
            response.put("nextAction", nextAction(status));
            response.put("operatorNextAction", operatorNextAction(status));
            response.put("nextCommands", nextCommands(status, text(response.get("targetRepo")), workstreamId, paths));
            return response;
        }

        var blocked = new ArrayList<String>();
        var acceptanceDecision = acceptanceDecision(paths, blocked);
        response.put("acceptanceDecision", acceptanceDecision);
        verifyCompleted(paths, blocked);
        verifyCompletionAudit(paths, blocked);
        if (!"ACCEPT".equals(acceptanceDecision) && !"ACCEPT_WITH_NOTES".equals(acceptanceDecision)) {
            blocked.add(acceptanceDecision.isBlank()
                    ? "Final acceptance decision is missing."
                    : "Final acceptance does not allow delivery merge: " + acceptanceDecision + ".");
        }
        if (targetRepo == null) {
            blocked.add("Original repo path is unavailable in the workstream ledger.");
        }

        if (blocked.isEmpty()) {
            try {
                var preview = gitPatchMergeService.preview(sourceWorktree, targetRepo);
                response.put("status", "READY");
                response.put("changedFiles", preview.changedFiles());
                response.put("excludedFiles", preview.excludedFiles());
                response.put("canMerge", true);
                response.put("nextAction", "Click Merge to original repo, then inspect the target staged diff before committing.");
                response.put("operatorNextAction", "确认目标仓库干净后，点击“合回原始仓库”。合回后只会暂存改动，不会自动提交。");
                response.put("nextCommands", nextCommands("READY", targetRepo.toString(), workstreamId, paths));
            } catch (Exception error) {
                blocked.add(error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
            }
        }

        if (!blocked.isEmpty()) {
            response.put("status", "BLOCKED");
            response.put("blockedReasons", blocked);
            response.put("canMerge", false);
            response.put("nextAction", "Fix the blocked reasons, then refresh the delivery view.");
            response.put("operatorNextAction", "先处理阻塞原因，再刷新交付视图。");
            response.put("nextCommands", List.of());
        }
        return response;
    }

    public Map<String, Object> merge(Path workspaceRoot, String workstreamId) throws Exception {
        var sourceWorktree = workspaceRoot.toAbsolutePath().normalize();
        var paths = paths(sourceWorktree, workstreamId);
        try (var ignored = WorkstreamLock.acquire(paths.workstreamDir(), LockOwner.currentProcess(workstreamId, "api delivery merge"))) {
            var blocked = new ArrayList<String>();
            var targetRepo = targetRepo(paths);
            var acceptanceDecision = acceptanceDecision(paths, blocked);
            verifyCompleted(paths, blocked);
            verifyCompletionAudit(paths, blocked);
            if (!"ACCEPT".equals(acceptanceDecision) && !"ACCEPT_WITH_NOTES".equals(acceptanceDecision)) {
                blocked.add("Final acceptance does not allow delivery merge: " + (acceptanceDecision.isBlank() ? "missing" : acceptanceDecision) + ".");
            }
            if (targetRepo == null) {
                blocked.add("Original repo path is unavailable in the workstream ledger.");
            }
            if (!blocked.isEmpty()) {
                throw new IllegalStateException(String.join(" ", blocked));
            }

            var preview = gitPatchMergeService.preview(sourceWorktree, targetRepo);
            writePreview(paths, preview);
            var readyPlan = DeliveryMergePlan.create(
                    workstreamId,
                    sourceWorktree.toString(),
                    targetRepo.toString(),
                    acceptanceDecision,
                    preview.changedFiles(),
                    preview.excludedFiles(),
                    DeliveryMergeStatus.READY,
                    ""
            );
            planWriter.write(paths, readyPlan);

            var apply = gitPatchMergeService.apply(targetRepo, preview.patch());
            Files.writeString(paths.deliveryApplyStdoutLog(), apply.stdout(), StandardCharsets.UTF_8);
            Files.writeString(paths.deliveryApplyStderrLog(), apply.stderr(), StandardCharsets.UTF_8);
            if (!apply.succeeded()) {
                var failed = readyPlan.withStatus(DeliveryMergeStatus.FAILED, apply.stderr().stripTrailing());
                planWriter.write(paths, failed);
                return view(sourceWorktree, workstreamId);
            }
            planWriter.write(paths, readyPlan.withStatus(DeliveryMergeStatus.APPLIED, ""));
            return view(sourceWorktree, workstreamId);
        }
    }

    private Map<String, Object> baseResponse(WorkstreamPaths paths, Path sourceWorktree, String workstreamId) {
        var response = new LinkedHashMap<String, Object>();
        response.put("workstreamId", workstreamId);
        response.put("sourceWorktree", sourceWorktree.toString());
        response.put("status", "UNAVAILABLE");
        response.put("acceptanceDecision", "");
        response.put("goalOutcome", text(readMap(paths.goalSatisfactionJson()).get("domainOutcome")));
        response.put("changedFiles", List.of());
        response.put("excludedFiles", List.of());
        response.put("blockedReasons", List.of());
        response.put("failureReason", "");
        response.put("canMerge", false);
        response.put("mergePlanPath", paths.deliveryMergePlanJson().toString());
        response.put("mergePatchPath", paths.deliveryMergePatch().toString());
        response.put("nextAction", "Delivery status is unavailable.");
        response.put("operatorNextAction", "交付状态不可用。");
        response.put("nextCommands", List.of());
        return response;
    }

    private void verifyCompleted(WorkstreamPaths paths, List<String> blocked) {
        var workflow = readMap(paths.workflowCurrentJson());
        var status = text(workflow.get("status"));
        if (!"COMPLETED".equals(status)) {
            blocked.add(status.isBlank() ? "Workflow status is unavailable." : "Workflow is not completed: " + status + ".");
        }
    }

    private void verifyCompletionAudit(WorkstreamPaths paths, List<String> blocked) {
        var audit = readMap(paths.completionAuditJson());
        var status = text(audit.get("status"));
        if (!"ready".equalsIgnoreCase(status)) {
            blocked.add(status.isBlank() ? "Completion audit is unavailable." : "Completion audit is not ready: " + status + ".");
        }
    }

    private String acceptanceDecision(WorkstreamPaths paths, List<String> blocked) {
        var decisions = new ArrayList<String>();
        if (Files.isDirectory(paths.runsDir())) {
            try (var stream = Files.walk(paths.runsDir(), 4)) {
                var contracts = stream
                        .filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().equals("acceptance-codex-review.contract.json"))
                        .sorted()
                        .toList();
                for (var contract : contracts) {
                    var decision = text(readMap(contract).get("decision"));
                    if (!decision.isBlank()) {
                        decisions.add(decision);
                    }
                }
            } catch (Exception error) {
                blocked.add("Failed to read final acceptance decision: " + error.getMessage());
            }
        }
        return decisions.isEmpty() ? "" : decisions.get(decisions.size() - 1);
    }

    private Path targetRepo(WorkstreamPaths paths) {
        var workstream = readMap(paths.workstreamJson());
        var repository = map(workstream.get("repository"));
        var original = text(repository.get("originalWorkdir"));
        if (!original.isBlank()) {
            return Path.of(original).toAbsolutePath().normalize();
        }
        return null;
    }

    private void writePreview(WorkstreamPaths paths, GitPatchMergeService.MergePreview preview) throws Exception {
        Files.createDirectories(paths.deliveryDir());
        Files.writeString(paths.deliveryMergePatch(), preview.patch(), StandardCharsets.UTF_8);
        Files.writeString(paths.deliveryChangedFilesTxt(), String.join(System.lineSeparator(), preview.changedFiles())
                + System.lineSeparator(), StandardCharsets.UTF_8);
        Files.writeString(paths.deliveryExcludedFilesTxt(), String.join(System.lineSeparator(), preview.excludedFiles())
                + (preview.excludedFiles().isEmpty() ? "" : System.lineSeparator()), StandardCharsets.UTF_8);
    }

    private String nextAction(String status) {
        return switch (status) {
            case "READY" -> "Click Merge to original repo, then inspect the target staged diff before committing.";
            case "APPLIED" -> "Review the target staged diff and commit from the original repo when ready.";
            case "FAILED" -> "Inspect merge-plan.json and apply.stderr.log, then retry delivery merge after fixing the cause.";
            case "COMMITTED" -> "Delivery merge has been committed.";
            default -> "Refresh the delivery view.";
        };
    }

    private String operatorNextAction(String status) {
        return switch (status) {
            case "READY" -> "确认目标仓库干净后，点击“合回原始仓库”。合回后只会暂存改动，不会自动提交。";
            case "APPLIED" -> "改动已暂存到原始仓库。请检查 staged diff，确认无误后手工 commit；需要 PR 时再手工 push/create PR。";
            case "FAILED" -> "查看合回失败日志，修复原因后再次执行合回。";
            case "COMMITTED" -> "交付合回已提交，可继续查看提交或创建 PR。";
            default -> "刷新交付视图。";
        };
    }

    private List<String> nextCommands(String status, String targetRepo, String workstreamId, WorkstreamPaths paths) {
        if (targetRepo == null || targetRepo.isBlank()) {
            return List.of();
        }
        var cdTarget = "cd " + shellQuote(targetRepo);
        return switch (status) {
            case "READY" -> List.of(
                    cdTarget,
                    "git status",
                    "git diff --stat"
            );
            case "APPLIED" -> List.of(
                    cdTarget,
                    "git status",
                    "git diff --cached",
                    "git commit -m " + shellQuote("feat: dual delivery " + workstreamId),
                    "# 可选：git push -u origin $(git branch --show-current)",
                    "# 可选：gh pr create --fill"
            );
            case "FAILED" -> List.of(
                    "cat " + shellQuote(paths.deliveryApplyStderrLog().toString())
            );
            default -> List.of();
        };
    }

    private String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private Map<String, Object> readMap(Path path) {
        try {
            if (!Files.isRegularFile(path)) {
                return Map.of();
            }
            return objectMapper.readValue(path.toFile(), MAP_TYPE);
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    @SuppressWarnings("unchecked")
    private List<String> list(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private String text(Object value) {
        return text(value, "");
    }

    private String text(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private WorkstreamPaths paths(Path root, String workstreamId) {
        return new WorkstreamPaths(root, new DualId(workstreamId));
    }
}
