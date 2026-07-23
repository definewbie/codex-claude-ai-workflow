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

### A1 README Full Lifecycle（PASS-WARN，等待 checkpoint）

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
- deterministic closure：篡改 reference/sidecar 时 context 节点失败且 reviewer 启动数为零；`full`/`contract` sibling definitions 一致；full reactor 绿色。
- `20260722T083641Z` A1 重跑已通过 intake、contract 和 human requirement review，在 `design.claude_design` 停止。ACP prompt 正常完成（exit 0），但 final assistant message 为 prose + JSON，唯一 strict `AgentResultEnvelopeParser` 正确拒绝并产生 `AGENT_RESULT_PARSE_FAILED`。
- 当前 checkpoint：只实现一次有界 agent-result contract correction。ACP adapter 负责同一 session 的第二次 prompt；唯一 strict parser 决定 wire contract 是否可解码；`ArtifactContractValidator` 继续唯一负责 lineage、node contract 和 semantic validation。不得提取 prose 中的 JSON，不得增加通用 execution pipeline、`TaskHandler.reinvoke()` 或 workflow retry。
- `20260722T134655Z` controlled real-case（workstream `ws-20260722T134943Z-Z5FK`）完成 `full` lifecycle：最终状态 `COMPLETED`，最终 acceptance `ACCEPT`，16 个 Agent run 全部 `SUCCESS`，目标 worktree 仅修改 `README.md`，`node --test` 通过，fresh build 与 provider probe 均通过。
- 同一 real-case 真实触发一次 bounded correction：`design.claude_design` occurrence 1 为 `AGENT_RESULT_PARSE_FAILED`，occurrence 2 在相同 `claude-main-ws-20260722T134943Z-Z5FK` session 中 strict decode 成功；没有第三次调用，最终 usage 为两个 prompt occurrence 的显式聚合。
- 运行中未出现 `RECOVERABLE_RATE_LIMIT`、`PROCESS_STALLED` 或 reconnect。此前将 session UUID 中的 `429b` 误判为 HTTP 429 的 matcher 已改为仅接受显式 429 形态，并由 catalog、health cannot-bypass tests 与本次 replay 证明。
- 本次成熟度为 `PASS-WARN`：A1 的 typed result、lineage 与 full lifecycle 边界达到 `ENFORCED + PROVEN`；两次 acceptance `REJECT_CONTEXT_INSUFFICIENT` 经既有 repair loop 后收敛为 `ACCEPT`。

#### A1.1 Acceptance Verification Evidence Lineage（PASS）

目标：收口 A1 暴露的 acceptance evidence 路由与失败分类缺口，不扩张为通用 Context Engine。

Authoritative fact：已通过 `ArtifactContractValidator` 且绑定 durable node/execution result 的 self-test contract sidecar，其中 `node_id=implement.claude_self_test_report`、`source_artifact_type=self_test_report`、`decision=SELF_TEST_COMPLETE`，并包含 `checks_run`、`evidence` 与 `verification_summary`。

唯一消费链：

```text
self-test validated sidecar
  -> ArtifactValidateHandler（与 Git evidence 聚合并转发）
  -> ContextBuildPackageHandler（启动 reviewer 前要求 SELF_TEST_COMPLETE）
  -> acceptance prompt typed projection
```

分类规则：当 acceptance review 同时声明 `missing_tests` 与泛化 `context_gaps`，`FailureArbiter` 必须优先分类为 `VERIFICATION_INSUFFICIENT`，并由既有 workflow edge 路由回 `implement.claude_self_test_report`，不得回到 implementation。

Cannot-bypass：

- 已验证 self-test sidecar 经 artifact validation 后仍存在于 output refs，并进入 acceptance context；
- 缺少 `SELF_TEST_COMPLETE` 时 context node 失败，acceptance reviewer 启动数为零；
- `missing_tests + context_gaps` 选择 `REJECT_VERIFICATION_INSUFFICIENT`；
- `MISSING_TEST_EVIDENCE` 是精确 typed alias，不依赖 summary 正文；
- full workflow 的 verification edge 仍只路由到 self-test report。

完成条件：targeted tests、`dual-runtime` full suite、`dual-adapter-acpx` full suite、`git diff --check` 与独立架构 review 全部 PASS。通过后提交 A1.1 checkpoint，再评估 A2；A2 未满足条件时不得启动 real-case。

关闭证据：

- `ArtifactValidateHandler` 只从显式 input refs 转发通过 `AgentOutputLineageVerifier` 的 sidecar；不扫描 runs。
- `AgentOutputLineageVerifier` 将 sidecar decision 同时绑定 node 与 node-execution durable result；发布后篡改 cannot-bypass test 通过。
- acceptance requirement 绑定 `implement.claude_self_test_report + self_test_report + SELF_TEST_COMPLETE`，错误 producer/type/decision 均不能满足。
- evidence 缺失在 reviewer 前产生 typed `REQUIRED_AGENT_EVIDENCE_MISSING`；显式缺测优先分类为 `VERIFICATION_INSUFFICIENT` 并路由回 self-test。
- targeted tests、`dual-runtime` full suite、`dual-adapter-acpx` full suite、`git diff --check` 均通过；第二次独立架构 review 为 `PASS`。

### A2 小 Bug（PASS）

目标：证明 source-code 修改、定向测试、A1.1 self-test evidence lineage 和 acceptance 闭环。

当前 case：`issue-flow-app` 的 `summarizeIssues` 对非数组输入泄漏迭代器 `TypeError`。限定只修改 `src/dashboard.js` 与 `test/issueWorkflow.test.js`，要求在函数边界抛出精确的 `issues must be an array`，并保持现有行为与 `node --test` 全绿。

Readiness：A1.1 为 `ENFORCED + PROVEN` 且已提交为 `16fe713`；目标仓库干净、现有测试通过、缺陷由失败的精确断言复现；Verification Gate 已锁定 source of truth、PASS/FAIL/INVALID 与 stop-loss。允许启动一次 controlled `full` real-case。

关闭证据：

- controlled run `ws-20260722T150030Z-MP3G`，报告 `docs/testing/real-case-reports/20260722T145746Z/report.md`；fresh build 与 provider probe 均通过。
- workflow 一次完成 `COMPLETED`，final acceptance=`ACCEPT`，acceptance reject=0，repair loop=0，benchmark grade=`PASS`。
- `ArtifactValidateHandler` output refs 保留 ledger-bound self-test sidecar；acceptance context 明确投影 `SELF_TEST_COMPLETE`、`node --test` 5/5 与 verification summary。
- 目标 worktree 只修改 `src/dashboard.js` 和 `test/issueWorkflow.test.js`；新增边界校验与 focused test，`node --test` 5/5 通过。
- 未触发 `REQUIRED_AGENT_EVIDENCE_MISSING`、自然语言控制 fallback、manual ledger/worktree repair 或范围外修改。

### A3 简单应用 API（ACTIVE）

目标：使用外部 `issue-flow-app` JavaScript 测试项目，证明 Dual 能组织 Claude Code 与 Codex 完成一个有限多文件、跨模块的应用 API 任务，并守住 contract、scope、self-test evidence 和 acceptance 闭环。该 JavaScript 项目只是受控 real-case 目标，不是 Dual 的运行时组件或依赖。

当前 case：保持 `src/issueWorkflow.js` 中的 `ALLOWED_TRANSITIONS` 为唯一状态迁移 SoT；新增 `availableTransitions(status)` 查询和 `src/issueApi.js` 的 `getIssueState(issue)` 稳定 DTO projection，并在独立测试中验证各状态、非法状态及返回数组隔离。限定只修改 `src/issueWorkflow.js`、`src/issueApi.js` 与 `test/issueApi.test.js`；不引入 HTTP server、网络、第三方依赖或第二份迁移规则。

Readiness：A2 controlled real-case 已达到 `COMPLETED + ACCEPT + PASS`；目标仓库干净、基线测试通过，且 precheck 已证明 `issueApi.js` 与 `availableTransitions` 尚不存在。允许使用固化的 Verification Gate 启动一次 controlled `full` real-case。

当前证据：

- `20260723T024557Z` 首次 A3 运行在 `intake.claude_brainstorm` 停止。严格 `AgentResultEnvelope` 正确拒绝了无法表达“修改现有文件”和“创建新文件”区别的 proposal；旧模型只能依赖 `new:` 字符串前缀，CREATE/MODIFY 语义没有进入 typed contract。
- `TaskContract.ScopeEntry(path, operation)` 现为 proposal 与 canonical TaskContract 共享的唯一 scope value object，`operation` 仅允许 `MODIFY|CREATE`。`MODIFY` 必须已存在，`CREATE` 必须尚不存在；旧 `new:` 前缀被显式拒绝。
- `task_contract_proposal` schema 已升级为 v2。旧 v1 sidecar 与“schema v2 但 included 仍为 string[]”均 fail closed，不提供 compatibility fallback，也不会生成 `task-contract.json`。
- proposal -> review -> human approval -> contract.compile -> context/memory/governance/report 的 production consumers 已迁移到 typed `ScopeEntry`；mechanical proposal failure 保留 valid AgentResult，并以 artifact contract failure 路由，不伪装成 output missing。
- controlled lifecycle smoke 以真实 ACP JSON-RPC 形态注入 strict `AgentResultEnvelope`，31 个节点、6 个 gate、11 个 agent run 全部完成；显式证明 intake proposal schema v2、最终 scope typed shape，以及 `main.py/MODIFY + README.md/MODIFY` 精确保真。
- semantic smoke 的 missing plan body、review prose noise、cross-stage reread 三个场景均通过；正式 smoke 不再生成 `dual-artifact` / `dual-control` Markdown。
- `20260723T074957Z` A3 重跑通过 intake、contract.compile 与 design，在 `plan.codex_plan_review` 返回 typed `NEEDS_REVISION` 后按 stop-loss 中止。Claude 生成的 `plan-claude.plan.json` 内容完整，但 review context 只信任 `plan-claude.contract.json` 的摘要；renderer 将同一 validated `PlanResult` 的派生 plan projection 误标为 `unvalidated agent result omitted from context`，导致 Codex 缺少实施步骤与验收映射。
- 修复不新增 evidence module 或 artifact state machine：`AgentResultDocumentProjector` 将已经由 `AgentResultEnvelopeValidator` 验证的 typed plan body 写入现有 `agent_output_contract` sidecar；`AgentEvidenceIndex` 将 payload 完整性纳入 required-evidence 判断；summary/full context renderer 从同一 validated entry 渲染并去重，伪造 derived plan 不能覆盖它。
- reviewer 启动门禁已收口：`source_artifact_type=plan_result` 但 typed plan body 缺失或不完整时，context build 返回 `REQUIRED_AGENT_EVIDENCE_MISSING` 且不发布 reviewer context。characterization/cannot-bypass tests、full Maven reactor（9/9 模块）与 `git diff --check` 均通过。
- A3 provider real-case 仍需使用同一 Verification Gate 重跑，因此 A3 保持 `ACTIVE`，不得标记 `PASS`。

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

允许：运行 A3 简单应用 API controlled `full` real-case；仅修复 A3 直接证明的 contract、scope、typed evidence 或 acceptance 边界缺陷。

禁止：提前启动 Phase B Scope Engine、Phase C Context Engine、Phase D handler 重构，以及多轮 review、多 Agent proposal merge、自动 rollback、policy engine、预算/provider 优化或 HTTP/runtime 扩张。
