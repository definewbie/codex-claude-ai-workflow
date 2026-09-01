# E1 Proxy Transport Ownership Bounded Comparison 独立评审记录

日期：2026-09-01

## 结论

`PASS / IMPLEMENTATION READY（仅授权只读 sweep 与实施计划）`。

- P0：0
- P1：0
- P2：5
- 推荐候选：Candidate C

## 已核实事实

1. R3 的 proxy owner 冲突成立：Dual 把八个 proxy 名作为必须从 Bash 消失的 credential，同时 Claude sandbox
   可能把同名变量作为自身 network transport。
2. wrapper 在 adapter 启动前已删除八个 proxy 名；`settingSources=[]` 的 SDK 0.3.232 只读复现也显示八项 absent。
3. fork 的 agent child environment 当前按 `process.env -> auth -> session env` 合并，之后没有 E1-specific scrub；
   因此 wrapper/Doctor 仍是唯一防线。
4. Candidate C 正确区分 policy owner、adapter spawn owner 与 Claude sandbox execution owner；不需要自建 proxy。

## P2 及关闭要求

1. 在 fork 最终 child env 增加 Claude-only、E1-binding-only 的八 proxy 名 scrub，使 wrapper/Doctor 降为 defense in depth。
2. 首轮曾建议固定 `CLAUDE_CODE_SUBPROCESS_ENV_SCRUB=1`；该建议已被后续计划评审的官方契约证据推翻，
   以本文“后续计划评审纠正”为准。仍保留“不通配删除 `CLAUDE_CODE_*`”与 acpx-owned
   `CLAUDE_CODE_EXECUTABLE` 的边界。
3. 归档 `resolveSettings({settingSources: []})` 的 presence-only 输出与 SHA。
4. 把“其他 env 不透传”限定为 settings allowlist，不冒充全部 process env 已清空。
5. 新 Gate 必须有 userinfo proxy canary 负向 fixture，证明 probe 不再读取/分类 proxy URL，也不再以 73 退出。

## Boundary Review

```text
Boundary Review: PASS (comparison only)
Source of truth: occurrence-scoped ClaudeExecutionPolicy
Consumed by: reviewed acpx fork + Claude sandbox runtime
Enforced before: E1-bound Claude child spawn / Bash network side effect
Failure route: NETWORK_AUTHORITY_NOT_CONFIGURED before provider prompt
Cannot bypass: process-env + session-env canaries, non-Claude/legacy sibling tests, Gate negative fixture
Residual: general process env inheritance is outside this bounded network-authority checkpoint
```

## 后续计划评审纠正

后续实施计划独立评审发现一个 P0：本报告 P2-2 建议固定
`CLAUDE_CODE_SUBPROCESS_ENV_SCRUB=1`，但 Anthropic settings reference 明确该变量会关闭
`autoAllowBashIfSandboxed`。该建议不成立，因为它会让 env 在运行时否决 policy 声明，并重新阻断 capability Gate。

计划 delta 已反转为：E1 finalizer 在最终 child env 删除 ambient `CLAUDE_CODE_SUBPROCESS_ENV_SCRUB`；provider credential
的 sandboxed Bash 隔离继续由 policy v2 `sandbox.credentials.envVars` 唯一拥有。此纠正不改变 Candidate C 的方向，
但 production 实施必须等待 delta review PASS。
