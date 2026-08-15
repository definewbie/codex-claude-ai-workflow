# Captcha-Auth Human Authority v14 Result

日期：2026-08-15  
结论：`FAIL / CONSUMED`  
产品：`NOT ACHIEVED`  
human-authority checkpoint：`ENFORCED / NOT PROVEN`

## 1. 运行边界

- Gate SHA：`820db46d71e1fb6f0264f4ecea965282b1e83660`。
- worktree：`/Users/kriswang/work/projects/ai/captcha-auth-app-agent-runs/captcha-auth-v14-20260815T1124Z`。
- workstream：`ws-20260815T033043Z-Z95C`。
- persisted workflow：`agent-lifecycle-terminal-delivery@5`。
- fresh package、隔离 PostgreSQL、Claude/Codex probes 均通过；旧 captcha-auth worktrees 与 open sessions 已在运行前清理。
- 本轮只创建一个 fresh workstream。失败后未 retry、未 resume、未创建第二样本；本轮 Claude/Codex scoped sessions 已关闭。

## 2. 实际执行

| Agent run | 结果 | duration | tokens |
| --- | --- | ---: | ---: |
| `run-1786764643947` intake initial Claude | completed | 62,308 ms | 9,664 |
| `run-1786764709674` Codex product review | `NEEDS_REVISION` | 110,597 ms | 33,058 |
| `run-1786764825831` intake revision Claude | process exit 0；typed artifact merge failed | 50,744 ms | 12,890 |

workflow 终态为 `WAITING_FOR_RECOVERY`，失败节点为
`node-intake-claude_brainstorm-006`（`intake.claude_brainstorm`，`iter-002`）。流程尚未进入 design，
因此 v14 的 human-authority 条件子目标没有自然触发，不能标记 `PROVEN`。

## 3. Typed failure

- execution outcome：`CONTRACT_FAILED`。
- primary reason：`required_artifact_contract_failed`。
- diagnostic reason：`TASK_CONTRACT_REVISION_PATCH_INVALID`。
- exact error：`DUPLICATE_EVIDENCE_PATCH_TARGET: src/main/resources/application.yml`。
- process：exit `0`、`end_turn`、strict envelope parse PASS、schema PASS、correction `NOT_NEEDED`。
- diagnostic SHA-256：`81ce2aa9b5520251598486bfdeee9294a691ba097a2f9678a9fc91a2cad90cbc`。
- revision output SHA-256：`9a4e2caf8c19d34711fedf2b4ceb77744f3bde499a2ef347d565ecc933067525`。
- revision prompt SHA-256：`9061341f0514672c070a8991743ca65a883a1985b49d4114c8a240af4bcf76fa`。
- review output SHA-256：`969e3911737e1ab75f4eb315ae672740c2d7898e697ba13af824f9e77ceaa4c0`。

## 4. 独立只读归因

### 已确认事实

1. 初始 proposal 的全局 `evidence_refs` 只有 `README.md`；`src/main/resources/application.yml` 是合法的新 evidence ref，
   不是对 baseline 的重复添加。
2. Codex 给出四个独立 obligation。CO-1 要求归一化规则，CO-3 要求可配置、可测试的 rate-limit contract；两者都可合理引用
   `application.yml`。
3. Claude 的 revision JSON 合法且完整覆盖 CO-1..CO-4，但在 CO-1 与 CO-3 的
   `evidence_ref_additions` 中各写了一次同一路径。
4. `TaskContractProposalRevisionMerger` 将所有 obligation patch 的 clause、scope 和 evidence target 视为全局 mutation target，
   并拒绝跨 obligation 重复；最终 durable proposal 的 `evidence_refs` 也由 validator 强制去重。
5. production prompt 只声明“每个 routed obligation 恰好一次”，没有声明
   `clause_changes.id`、`scope_additions.path` 与 `evidence_ref_additions` 在整个 `obligation_patches` 数组中必须全局唯一，
   也没有说明一个共享 evidence ref 应只归属一个 patch、其他 obligation 仅通过 clause mapping 表达覆盖。

### 根因判定

这是 **revision patch contract projection 缺口**，不是 runtime、ACP、权限、strict parser 或业务合同失败。

authoritative mutation 语义由 merger 正确 fail-closed 守住；缺口在 Agent-facing contract 没有投影 merger 已强制的全局唯一性不变量。
模型输出符合一种自然但未被协议排除的解释：同一证据可支持多个 obligation，因此在各 patch 中重复列出。不能把这次失败单独归因为
“模型没有遵守明确约定”，也不应通过自动去重掩盖归属冲突。

## 5. Boundary review

- Declared by：`TaskContractProposalRevisionMerger` 的 lineage-bound finite patch semantics。
- Proven by：typed revision output、verified baseline proposal、contract failure diagnostic。
- Consumed by：`ArtifactContractValidator` 在 sidecar 发布前调用 merger。
- Enforced before：重复 mutation target 阻止 durable proposal、reference 与后续 reviewer run 产生。
- Failure route：`CONTRACT_FAILED -> WAITING_FOR_RECOVERY`，没有业务 successor。
- Cannot-bypass：现有 merger duplicate-target guard 已阻止重复写入，但 prompt projection 尚无对应 deterministic contract test。
- Residual path：Agent-facing prompt 与 merger 的 target uniqueness 语义不一致。

Boundary Review：`FAIL`（projection completeness），但 fail-closed 行为正确。

## 6. 唯一后续 checkpoint

下一步不是重跑 v14，而是一个 bounded contract-projection checkpoint：

1. 由 revision patch 的共享语义 owner 定义 clause/scope/evidence target 的全局唯一性；不在 handler 添加局部字符串判断。
2. 将该规则完整投影到 `AgentResultPromptContract`，明确跨全部 obligation patches 生效，并给出共享 evidence 的唯一归属规则。
3. envelope validator 在 Agent 边界提前给出精确 typed error；merger 保留独立 durable guard，不能降级为自动去重。
4. 用本次 captured revision JSON 做 deterministic replay：原样必须被精确拒绝；将第二个重复 ref 移除后必须 merge PASS，且
   CO-1..CO-4 coverage、lineage、clauses 与唯一 evidence ref 均保真。
5. focused/full tests 与独立代码级 diff review PASS 后，才制定新的 fresh captcha-auth Gate；不得 resume 本 workstream。

本结论不授权修改 parser、增加 correction/retry、增加 Agent 调用、放宽 durable validator 或创建新 phase/module/framework。
