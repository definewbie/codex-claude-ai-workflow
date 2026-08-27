package dev.dual.adapter.acpx;

import java.util.List;

/**
 * acpx/Claude Code 工具权限的 adapter 边界模型。
 *
 * Workflow node 只声明语义化 profile；这里负责把 profile 翻译成两层权限：
 * acpx 的 ACP 权限种类（read/search/execute）和 Claude Code 的免确认工具集。
 *
 * acpx 的 `--permission-policy` 不理解 `Bash(ls *)` 这类 Claude Code 模式；
 * 它只能按 ACP 工具种类或标题批准权限请求。Claude Code 的
 * `--allowed-tools` 实际表示工具可以无需询问即执行，不是可见性白名单。
 * 它不能弥补 ACP 层对 execute 的过度批准。因此 discovery/observe/review
 * 不自动批准 execute；
 * intake contract discovery 只批准 read/search，并继续关闭 terminal；它不依赖
 * deny-all 与 permission-policy 的组合优先级来获得基本读取能力。
 */
record AcpxPermissionProfile(
        String name,
        boolean approveAll,
        boolean denyAll,
        boolean terminalEnabled,
        boolean simpleClaudeCode,
        List<String> autoApprovedTools,
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
        autoApprovedTools = autoApprovedTools == null ? List.of() : List.copyOf(autoApprovedTools);
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
        return new AcpxPermissionProfile("contract_discovery", false, false, false, true, List.of(
                "Read",
                "Grep",
                "Glob"
        ), List.of("read", "search"), 1);
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
                observe.autoApprovedTools(),
                observe.autoApproveKinds(),
                observe.promptRetries()
        );
    }

    static AcpxPermissionProfile implement() {
        return new AcpxPermissionProfile("implement", true, false, true, false, List.of(), List.of(), 0);
    }

    static AcpxPermissionProfile contractCorrection() {
        return new AcpxPermissionProfile(
                "contract_correction",
                false,
                true,
                false,
                false,
                List.of(),
                List.of(),
                0
        );
    }

    String autoApprovedToolsArgument() {
        return String.join(",", autoApprovedTools);
    }

    boolean approveReads() {
        return autoApproveKinds.contains("read");
    }

    boolean requiresFreshSession() {
        return simpleClaudeCode;
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
