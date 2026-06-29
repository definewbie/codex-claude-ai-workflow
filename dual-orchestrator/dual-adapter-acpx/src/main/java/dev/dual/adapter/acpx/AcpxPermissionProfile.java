package dev.dual.adapter.acpx;

import java.util.List;

/**
 * acpx/Claude Code 工具权限的 adapter 边界模型。
 *
 * Workflow node 只声明语义化 profile；这里负责把 profile 翻译成两层权限：
 * acpx 的 ACP 权限种类（read/search/execute）和 Claude Code 的工具白名单。
 *
 * acpx 的 `--permission-policy` 不理解 `Bash(ls *)` 这类 Claude Code 模式；
 * 它只能按 ACP 工具种类或标题批准权限请求。Claude Code 的
 * `allowedTools` 只能进一步限制 Claude Code 工具名，不能弥补 ACP 层
 * 对 execute 的过度批准。因此 discovery/observe/review 不自动批准 execute；
 * intake contract discovery 也不自动批准 direct Read，避免实现级探索在单个
 * external run 内先烧 token、后置 validator 才拦截。
 */
record AcpxPermissionProfile(
        String name,
        boolean approveAll,
        boolean denyAll,
        boolean terminalEnabled,
        boolean simpleClaudeCode,
        List<String> allowedTools,
        List<String> autoApproveKinds,
        int promptRetries
) {
    AcpxPermissionProfile {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("permission profile name is required");
        }
        if (promptRetries < 0) {
            throw new IllegalArgumentException("promptRetries must be zero or positive");
        }
        allowedTools = allowedTools == null ? List.of() : List.copyOf(allowedTools);
        autoApproveKinds = autoApproveKinds == null ? List.of() : List.copyOf(autoApproveKinds);
    }

    static AcpxPermissionProfile fromName(String name, boolean approveAllFallback) {
        if (approveAllFallback) {
            return implement();
        }
        if (name == null || name.isBlank()) {
            return observe();
        }
        return switch (name) {
            case "contract_discovery" -> contractDiscovery();
            case "observe" -> observe();
            case "review" -> review();
            case "implement" -> implement();
            default -> throw new IllegalArgumentException("unsupported acpx permission profile: " + name);
        };
    }

    static AcpxPermissionProfile contractDiscovery() {
        return new AcpxPermissionProfile("contract_discovery", false, true, false, true, List.of(
                "Grep",
                "Glob"
        ), List.of("search"), 1);
    }

    static AcpxPermissionProfile observe() {
        return new AcpxPermissionProfile("observe", false, false, true, false, List.of(
                "Read",
                "Grep",
                "Glob"
        ), List.of("read", "search"), 1);
    }

    static AcpxPermissionProfile review() {
        var observe = observe();
        return new AcpxPermissionProfile(
                "review",
                false,
                false,
                true,
                false,
                observe.allowedTools(),
                observe.autoApproveKinds(),
                observe.promptRetries()
        );
    }

    static AcpxPermissionProfile implement() {
        return new AcpxPermissionProfile("implement", true, false, true, false, List.of(), List.of(), 0);
    }

    String allowedToolsArgument() {
        return String.join(",", allowedTools);
    }

    boolean approveReads() {
        return autoApproveKinds.contains("read");
    }

    String permissionPolicyJson() {
        if (autoApproveKinds.isEmpty()) {
            return "";
        }
        var autoApprove = autoApproveKinds.stream()
                .map(kind -> "\"" + kind + "\"")
                .collect(java.util.stream.Collectors.joining(","));
        return "{\"autoApprove\":[" + autoApprove + "],\"defaultAction\":\"deny\"}";
    }
}
