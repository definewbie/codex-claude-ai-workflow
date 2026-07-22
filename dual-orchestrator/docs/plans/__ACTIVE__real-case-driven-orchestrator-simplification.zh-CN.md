# __ACTIVE__ Real-Case-Driven Orchestrator Simplification

## 总体目标

1. 简化 Dual Orchestrator。
2. 让 node executor/handler 只负责编排，领域规则归属唯一 domain owner。
3. LLM workflow 结果只通过 strict `AgentResultEnvelope` 进入状态流转；Markdown 仅供人阅读，不参与控制。

当前成熟度：strict agent-result boundary 已达到 `ENFORCED + PROVEN`；typed proposal lineage 已通过 controlled contract slice。Orchestrator 简化和 handler 瘦身尚未闭环。

## 执行纪律

每个 real-case 失败都先按以下顺序处理：

```text
typed failure fact
  -> 定位唯一 owner
  -> 区分 local defect / missing domain owner
  -> 最小修复或抽取 domain service
  -> 重跑同一个 case
```

不得因为单个失败立即增加通用 framework、policy engine 或新的兼容解析器。只有同一规则被多个 executor 消费、且现有 owner 无法承载时，才允许抽取 domain service。

每个 phase 必须独立完成 Analyze -> Modify -> Review -> Reflect -> Analyze；未达到本 phase 的 PASS，不激活下一 phase。

## Phase A：完整小案例

### A1 README Full Lifecycle（当前唯一 active objective）

目标：在 `full` variant 中证明：

```text
intake -> contract -> design -> plan -> implement
-> self-test -> acceptance -> delivery
```

PASS：

- 最终状态 `COMPLETED`；
- acceptance 最终决策为 `ACCEPT` 或 `ACCEPT_WITH_NOTES`；
- Claude/Codex 最终消息均通过 strict `AgentResultEnvelope`；
- 无 Markdown/自然语言控制路由；
- 无手工修改 ledger、artifact 或 worktree；
- 目标仓库只发生 contract 允许的 README 变更；
- fresh build、provider probe 和运行工件完整。

FAIL：任何 workflow/recovery/contract/scope/acceptance/delivery 边界失败。

INVALID：provider、配置、旧 jar、探针或外部运行时导致本轮无法证明代码目标。

Stop-loss：首次出现 recovery 即停止；不调预算、不切模型、不顺手修 Scope/Context/handler。

当前证据：

- `20260722T075423Z` 首次 A1 运行在 `intake.codex_product_review` 连续两次返回 `NEEDS_REVISION`，随后 Claude session reconnect；主因是 review context 只携带 proposal reference/summary，完整 typed proposal 未进入 prompt。
- 直接边界修复：workflow context 节点声明 `required_input_artifact=task_contract_proposal`；context builder 在 reviewer 启动前验证 reference/hash/lineage，并投影完整 proposal；完整 context package 不再被 adapter 的 6000 字符 preview 截断。
- deterministic closure：篡改 reference/sidecar 时 context 节点失败且 reviewer 启动数为零；`full`/`contract` sibling definitions 一致；full reactor 绿色。A1 real-case 重跑待执行。

### A2 小 Bug

仅在 A1 PASS 后激活。证明代码修改、定向测试和 acceptance 闭环。

### A3 简单 API

仅在 A2 PASS 后激活。证明有限多文件、跨层 contract/scope 和测试闭环。

Phase A 完成条件：A1/A2/A3 各有一个可复现的 `COMPLETED + ACCEPT` real-case，且失败修复没有引入自然语言控制 fallback。

## Phase B：Scope Boundary

仅在 Phase A 暴露 scope owner 缺口或 A3 PASS 后显式激活。

目标结构：

```text
TaskContract.scope
+ typed proposal evidence refs
+ typed scope expansion
+ repository capability/index result
-> ScopePolicy
-> ExplorationPlan
```

删除 requirement prose 到 execution scope 的 keyword、filename guessing 和 regex inference。路径规范化、安全校验、明确文件类型匹配可以保留；不得以“消灭所有 contains/regex”为目标。

验收：`RepoScopeGovernance` 变成薄协调器或被唯一 ScopePolicy owner 替代；越界读取 cannot-bypass tests 继续成立。

## Phase C：Context Boundary

仅在 Phase B PASS 后显式激活。

目标结构：

```text
ledger facts + typed artifact refs + verified lineage
-> bounded ArtifactIndex / LineageQuery
-> ContextPackage
```

filesystem 只验证已知 artifact 的存在和 hash，不通过目录扫描猜测业务 evidence。不得建设通用 graph platform。

## Phase D：Handler 收敛

仅在 Phase C PASS 后显式激活。

目标不是机械地把“68 个类”改成固定数量 executor，而是：

- node executor 只取 typed input、调用 domain service、写 typed result；
- contract、scope、context、review/gate、validation、delivery 各有唯一 owner；
- handler package 不再承载大块 policy、filesystem discovery 或 projection；
- 删除的旧逻辑多于新增 glue code；
- workflow/contract/handler consistency tests 和 full reactor 保持绿色。

## 当前锁定范围

允许：提交 typed proposal lineage checkpoint；运行 A1 README full real-case；只修复 A1 证明的直接边界缺陷。

禁止：提前启动 Scope Engine、Context Engine、handler 重构、多轮 review、多 Agent proposal merge、自动 rollback、policy engine、预算/provider 优化。
