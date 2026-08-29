package dev.dual.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dual.runtime.delivery.DeliveryApplicationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Shared delivery projection and command facade for the operator API. */
@Service
public class DeliveryViewService {
    private final DeliveryApplicationService delivery;

    @Autowired
    public DeliveryViewService(ObjectMapper ignored) {
        this(new DeliveryApplicationService());
    }

    DeliveryViewService(DeliveryApplicationService delivery) {
        this.delivery = delivery;
    }

    public Map<String, Object> view(Path workspaceRoot, String workstreamId) {
        return project(workstreamId, delivery.view(workspaceRoot, workstreamId, null));
    }

    public Map<String, Object> merge(Path workspaceRoot, String workstreamId) throws Exception {
        return project(workstreamId, delivery.apply(workspaceRoot, workstreamId, null));
    }

    public Map<String, Object> finalizeCommit(Path workspaceRoot, String workstreamId, String commit) throws Exception {
        return project(workstreamId, delivery.finalizeCommit(workspaceRoot, workstreamId, commit));
    }

    private static Map<String, Object> project(String workstreamId, DeliveryApplicationService.View view) {
        var response = new LinkedHashMap<String, Object>();
        response.put("workstreamId", workstreamId);
        response.put("status", view.status());
        response.put("reasonCode", view.reasonCode());
        response.put("failureReason", view.reason());
        response.put("acceptanceDecision", view.acceptanceDecision());
        response.put("candidateRef", view.candidateRef());
        response.put("candidateSha256", view.candidateSha256());
        response.put("changedFiles", view.changedFiles());
        response.put("excludedFiles", view.excludedFiles());
        response.put("targetRepo", view.targetRepo());
        response.put("commit", view.commit());
        response.put("canMerge", view.canMerge());
        response.put("canFinalize", view.canFinalize());
        response.put("blockedReasons", view.reason().isBlank() ? List.of() : List.of(view.reason()));
        response.put("nextAction", nextAction(view));
        response.put("operatorNextAction", operatorNextAction(view));
        response.put("nextCommands", nextCommands(view, workstreamId));
        return response;
    }

    private static String nextAction(DeliveryApplicationService.View view) {
        if ("WORKFLOW_NOT_DELIVERABLE".equals(view.reasonCode())) {
            return "The workflow is not yet eligible for delivery.";
        }
        return switch (view.status()) {
            case "READY" -> "Apply the accepted candidate to the original repository.";
            case "APPLIED" -> "Review and commit the staged target diff, then finalize that commit.";
            case "FAILED", "BLOCKED" -> "Resolve the recorded boundary failure before retrying delivery.";
            case "COMMITTED" -> "The accepted delivery candidate has been committed.";
            default -> "Refresh the delivery view.";
        };
    }

    private static String operatorNextAction(DeliveryApplicationService.View view) {
        if ("WORKFLOW_NOT_DELIVERABLE".equals(view.reasonCode())) {
            return "当前尚不可交付；请先完成工作流与完成审计。";
        }
        return switch (view.status()) {
            case "READY" -> "候选内容已与人工验收绑定，可以合回原始仓库。";
            case "APPLIED" -> "改动已暂存。检查后手工提交，再登记提交 SHA 完成交付。";
            case "FAILED", "BLOCKED" -> "先处理已记录的边界错误，再重试交付。";
            case "COMMITTED" -> "已验证提交内容与获批候选完全一致。";
            default -> "刷新交付视图。";
        };
    }

    private static List<String> nextCommands(DeliveryApplicationService.View view, String workstreamId) {
        if (view.targetRepo().isBlank()) return List.of();
        var cd = "cd " + shellQuote(view.targetRepo());
        if (view.canMerge()) {
            return List.of(cd, "git status", "git diff --stat");
        }
        if (view.canFinalize()) {
            return List.of(
                    cd,
                    "git diff --cached",
                    "git commit -m " + shellQuote("feat: dual delivery " + workstreamId),
                    "dualctl delivery finalize --commit $(git rev-parse HEAD) --workdir <source-worktree> " + workstreamId
            );
        }
        return "COMMITTED".equals(view.status())
                ? List.of(cd, "git show --stat " + view.commit())
                : List.of();
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }
}
