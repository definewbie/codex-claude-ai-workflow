# Verification Gate Review: Terminal-Delivery Captcha-Auth Completion v13

日期：2026-08-14
审查对象：
1. `docs/testing/verification-gates/20260814-terminal-delivery-captcha-auth-completion-v13.md`（v13 Gate）
2. `docs/testing/verification-gates/20260814-terminal-delivery-captcha-auth-completion-v12.md`（v12 历史同步）
3. `docs/plans/__ACTIVE__real-case-driven-orchestrator-simplification.zh-CN.md`（active plan）

方法：只读、代码级 + 证据级。直接核对 Git 事实（`1db62088`/`b6bc8e79`/`e64bf7c6`）、captured ledger
（`ws-20260813T235458Z-4RQE` / `run-1786671207221`）、acpx 原始 session stream（record `c41799ab`）、
bundled workflow 资源、target 仓库与 production CLI/ledger 代码。未修改文件、未提交、未调用 provider、
未运行 real-case、未重复 full reactor（按指示核对已提交 surefire/review 证据与 `git diff --check`）。

## 结论摘要

| 项 | 判定 |
|---|---|
| Findings | **P0=0 / P1=0 / P2=3**（均非阻塞） |
| Boundary Review | **PASS** |
| Gate 可执行 | **可执行**（九项完整、机械可判定、frozen baseline 与现实一致） |
| 选择性提交条件 | **满足**（恰好三份文档 dirty；`git diff --check` clean） |
| 提交后授权 | 仅 **fresh package + non-provider preflight** |
| provider probes / fresh workstream | **仍未授权** |
| 成熟度 | `1db62088`: ENFORCED / NOT PROVEN；`b6bc8e79`: ENFORCED / NOT PROVEN；captcha-auth: **NOT ACHIEVED** |

---

## 1. Git / 资源 / target 事实核对（独立验证）

| 声明 | 核对结果 |
|---|---|
| checkpoint `1db62088` 存在且为 HEAD | ✓ `1db620880c9fd8e…` "fix(acpx): enforce turn-scoped permission facts"；adapter + docs only，无 runtime/model/prompt 改动 |
| `b6bc8e79` | ✓ "fix(runtime): close interrupted adapter outcomes"；仅 `AgentExecutionFinalizer` / `AgentExecutionAttemptReconciler` / `AgentRunMetadataFinalizer` + tests |
| `e64bf7c6` | ✓ "fix(contract): project canonical scope path rules"；仅 `AgentResultPromptContract` + test |
| ancestry：`e64bf7c6` ⊂ `b6bc8e79` ⊂ `1db62088` ⊂ HEAD | ✓ `git merge-base --is-ancestor` 三项全过 |
| working tree | 仅 `M active plan`、`M v12 doc`、`?? v13 doc` —— **恰好待提交三份文档**；`git diff --check` **clean** |
| workflow `agent-lifecycle-terminal-delivery@4` | ✓ `dual-runtime/src/main/resources/dev/dual/runtime/lifecycle/agent-lifecycle.terminal-delivery.json`，version=4 |
| source SHA-256 `02c81fe9…` | ✓ `shasum -a 256` 逐字一致 |
| 31 nodes / 51 edges / 9 acpx / 0 handoff | ✓ 实测 31/51；`handlerType=acpx.agent_run` 9 个；handoff 0 |
| target HEAD `7c7e35e8…` / tree `bab065a1…` | ✓ `git rev-parse` 一致，worktree clean |
| target 仅 `REQUIREMENT.md` + `VERIFICATION-GATE.md` 两个 tracked files | ✓ `git ls-files` 恰两项 |
| requirement SHA-256 `42078b01…` | ✓ 实测一致 |
| 已提交 1239/0/0/0 证据 | ✓ delta review（含 reactor 140 suites/1239/0/0/0 独立复跑记录）已随 `1db62088` 提交 |

## 2. v12 `FAIL / CONSUMED` 标记核实（captured ledger + 原始 stream）

| v12 声明 | 证据 |
|---|---|
| 停在 `WAITING_FOR_RECOVERY` | ✓ `workflow/instance.json` 与 `workflow/current.json` 均 `status=WAITING_FOR_RECOVERY` |
| active node `implement.claude_implement` | ✓ `nodes/node-implement-claude_implement-045.json` `status=FAILED`、reason `STALE_RUNNING_PROCESS`、recovery gate `recovery-node-implement-claude_implement-045.json`；前置 context 节点 044 `COMPLETED` |
| `605118ms / exit 5` | ✓ `run-1786671207221/run.json` `processResult`: `durationMs=605118`、`exitCode=5`、command 含 `acpx --timeout 600`、`usability=INTERRUPTED`、`interrupted=true` |
| ACP stream 已接受 prompt | ✓ timeline 含 `prompt_accepted`（但无 `prompt_completed`） |
| 当前 occurrence 没有 permission request | ✓✓ **双重证据**：(a) Dual 侧 timeline 无任何 permission 事件、`adapterSignals` 的 PERMISSION_BLOCKED `source=acpx_exit` 且无 request evidence；(b) acpx 原始 stream（record `c41799ab`，76,626 行，覆盖全部 design/plan/implement turn）：**唯一** `session/request_permission`（id=0）位于 line 13357，属早期 turn；implement turn（最后一个 prompt，line 32384 之后）**零 permission request** |
| 历史 design denial 经累计 stats 污染 + 旧裸 exit-5 映射 | ✓ 全部 Claude run（intake/design/plan/implement）共用同一 named session `claude-main-ws-…4RQE`（run.json session 字段）；record `c41799ab` created 00:01:55、`last_prompt_at=01:33:27.583Z`（与 implement run startedAt 01:33:27.462Z 吻合）、closed 01:43:32 —— 单 record 跨 turn 累计 permission 事实；4 个 design run 均 exit 5（含 `prompt_completed + contract_validated` 的 contained denial）；implement turn 无 request 仍 exit 5 ⇒ 只能来自累计 stats；旧 Dual 从裸 exit 5 合成的 producer（`source=acpx_exit`）已由 `1db62088` 删除 |
| workstream/worktree/session 不得 resume/reuse | ✓ v12/v13 INVALID 条款、active plan、node 045 `recovery_note`（"captured … closed after terminal-owner fix b6bc8e7; no provider retry"）一致；named session `claude-main-…4RQE` 实际 `closed=true` |

v12 的 `FAIL / CONSUMED / SUPERSEDED BY v13` 标记**准确**。

## 3. 逐问题判定（12 项）

1. **v12 标记准确** ✓（§2 全项核实）。
2. **唯一主目标 = 完成产品** ✓：v13 明示不为证明 adapter 修复人为制造 denial/timeout/第二样本；Conditional 边界"只在自然出现时升级"；"不得为升级标签再运行第二个样本"。
3. **九项完整且可机械判定** ✓：Target / Source of truth / Trigger precondition / How checked / PASS / FAIL / INVALID / Stop-loss / Run artifact freshness 齐全；pre-run 项（git identity、hash、tracked files、workflow SHA/shape、JAR SHA/mtime、容器/端口、PG health、probe）与运行中项（typed artifacts、run.json 事实、90min/budget/重复失败）均可机械判定。P2-2 记录一处措辞分级问题。
4. **Frozen baseline 与真实代码/target 一致** ✓（§1 全项逐字核对）。
5. **current-turn permission authority 表达准确** ✓：与 `1db62088` 已验证代码一致——只有 current occurrence 内 request-id 关联的 typed request/result 可生成 PERMISSION_BLOCKED；裸 exit 5/stderr 文本/累计 stats/历史 denial 无 authority（producer 已删除，cannot-bypass 测试覆盖）；contained denial 不覆盖完整 strict result（resolver containment 需 non-zero complete + valid contract + artifacts satisfied）；无 current evidence 时由既有 output/process failure fail closed（v13 §Conditional 2/3 与代码一致）。
6. **failed-turn closure 表达准确** ✓：abnormal return 前 cancel exact named session → 不足则 close → 仅 `CANCELLED/CLOSED_AFTER_CANCEL` 声明 quiescent → `FAILED` 必须 typed `PROCESS_STALLED / MANUAL_ONLY / retrySafe=false`、session id 保真、不得自动 retry/resume（v13 §Conditional 4/5；与 delta review 验证的 `quiesceSession`/`sessionClosureFailureSignal`/RecoveryAssessment 一致）。
7. **两个 owner 区分正确** ✓：transport-owned abnormal-turn named-session closure（Conditional 4/5）与 operator stop-loss（既有 terminate/reconcile/session-reap）分开；v13 明示"该 operator stop-loss 不冒充本 checkpoint 的 transport-owned named-session closure proof"——runtime stop-loss 未实现 named-session cancel 的既知 P2 **没有**被偷换成 Gate 已证明能力，也未因此扩大 Gate。
8. **mid-run rollover 诚实限定** ✓：v13 §Conditional 6 明示为已知 evidence-continuity 限制、不自动否定产品 PASS、不得用于升级 `PROVEN`；且因 authority 需要 current-occurrence typed evidence 而 rollover 丢失观察（fail-closed、无 evidence 即无 authority），不构成权限旁路。
9. **provider/model 仅执行证据** ✓：v13 明示不作为合法性前置、不因 Claude Code 后端模型（GLM/DeepSeek 等）判 INVALID；run.json 以 `providerModelIdentity` 字段记录证据。与历史处理一致。
10. **Human Interaction 可执行** ✓：production 核实——`ReviewDecideCommand`（`review decide --action <a> --note <n> --workdir <dir> <workstream-id>`，参数与 v13 示例一致）与 server `WorkstreamActionService:155` **同调** `ledger.resolveOpenGate`；`RoutingEngine:332-356` 强制 active GATE 节点、action 必须命中 workflow edge 或 `allowedActions`、BLOCKED 必须非空 note；`allowedActions` 由 `LedgerUtils.allowedActions(definition.node(…))` 派生并进入 GateDocument/packet；UI 与 fresh-JAR CLI 同一 ledger boundary，手工编辑 JSON 被禁止。`WAITING_FOR_HUMAN` 为合法暂停点 ✓。
11. **Execution Order 守住授权边界** ✓：先 review → 只提交三份文档获 clean SHA（与当前 dirty 集完全一致）→ fresh package + non-provider preflight（PG/容器/端口/identity）→ 用户授权后一次双 provider probe → probes PASS 后**仅一个** fresh workstream；不复用 v12（INVALID 条款枚举 ws id/worktree/named session）；不为条件性证明创建第二样本。
12. **纯文档 Gate 变更不重跑单测合理** ✓：deterministic SoT = 已提交 independent review（diff + delta，均 PASS）+ full reactor `1239/0/0/0`（后者在 delta review 中独立复跑过）；且 v13 没有把它误写成跳过 freshness——Run artifact freshness 仍要求从最终 clean Gate SHA fresh package、记录 CLI/server JAR SHA-256 与 mtime、禁 `--skip-build`/旧 JAR。

## 4. End-to-end Invariant

| 维度 | 证据 |
|---|---|
| **Declared by** | v13 Gate §Verification Gate + §Conditional ACPX Turn Boundary；owner：transport（occurrence-scoped typed facts + named-session closure）、runtime（interrupted durable closure `b6bc8e79`） |
| **Proven by** | deterministic：12 项 cannot-bypass（`1db62088`，reactor 1239/0/0/0 独立复跑）+ captured v12 反例证据（本审查 §2：裸 exit-5 污染路径在真实 run 中发生并已被删除）。fresh real-case 未运行 ⇒ 全部条件边界保持 **NOT PROVEN** |
| **Consumed by** | `AgentExecutionOutcomeResolver`、`AgentRunMetadataFinalizer`、`RecoveryAssessment`（MANUAL_ONLY/retrySafe=false/rebuild-first）、`RoutingEngine.resolveOpenGate`（human 边界）、reconciler durable 分支 |
| **Enforced before** | transport 返回 terminal 前：baseline 选择（失败即 null→bounded tail 非 current）→ typed request/result correlation → 异常返回 `quiesceSession`（cancel→close→typed disposition）→ finalizer；operator stop-loss 另走既有 terminate/reconcile/session-reap |
| **Failure route** | 无 current evidence → 既有 output/process failure fail closed；current DENIED + 缺 completion/artifact → PERMISSION_BLOCKED/MANUAL_ONLY；closure 双失败 → PROCESS_STALLED（含 session id）/MANUAL_ONLY；历史 denial/裸 exit 5/stats → 无 authority；复用 v12 → INVALID |
| **Cannot-bypass test** | `missingSessionIndexCannotReplayHistoricalPermissionDenialAsCurrent`、`currentPermissionResultWithoutCurrentRequestCannotCreateEvidence`、`failedCancelAndCloseRemainFailClosedAsProcessStalled` + 前轮 9 项 |
| **Residual raw/legacy path** | ① `AgentRunMetadataFinalizer` typed-reason round-trip 映射（无裸 producer）；② operator stop-loss 无 named-session cancel（既知 P2，非本 Gate 能力，不得冒充）；③ mid-run session-id rollover 观察丢失（诚实记录为限制）；④ 遗留 open record：acpx `codex-reviewer-ws-…4RQE`（`019ffd90`）`closed=false` —— 名字随 workstream 作用域、无法被新 run 复用，无旁路，仅 preflight 卫生项（见 P2-3） |

## 5. Findings

### P0：无

### P1：无

### P2（非阻塞）

1. **P2-1 Stop-loss wall-clock 与 `WAITING_FOR_HUMAN` 的计时歧义**
   `20260814-terminal-delivery-captcha-auth-completion-v13.md:51-52`（v12:63-64 同）："wall-clock 达 90 分钟…时由 operator 中断" 未说明计时是否排除 human 暂停。若含暂停时长，operator 会被指引中断一个同句声明的"合法暂停点"。建议明确 90 分钟只计 workflow 活跃执行时间。不阻塞：`WAITING_FOR_HUMAN` 合法性在同句已声明，operator 可合理解释。
2. **P2-2 Trigger precondition 一节混合了提交前检查与授权后动作**
   `…v13.md:23-29`：precondition 列表含 "Claude/Codex minimal probes 必须 PASS"，而该动作按 §How checked（:34-35）与 Execution Order 步骤 4 需在提交与用户授权之后执行。分级语义由后两节澄清，机械可判定性不受影响，但 precondition 一节单独读会产生"probe 先于提交"的误读。
3. **P2-3 preflight 卫生：v12 遗留 open acpx record 未纳入清理**
   实测 acpx index：`codex-reviewer-ws-20260813T235458Z-4RQE`（record `019ffd90`）仍 `closed=false`。无复用可能（session 名含 workstream id、新 workstream 生成新名；INVALID 条款禁止复用），故无旁路；但 v13 preflight 已清理旧 probe 容器/端口，可顺手 `acpx <agent> sessions close <id>` 收口并记录，避免残留 open record 干扰后续 evidence 检索。

## 6. 输出要求逐项回答

1. Findings：P0=0 / P1=0 / P2=3（§5）。
2. **Boundary Review：PASS**。
3. **Gate 可执行**：九项完整、机械可判定；frozen baseline 逐项与真实代码/target 一致；human/CLI 边界与 production 相符。
4. **满足仅提交三份文档的条件**：dirty 集恰为 v13 Gate（新）+ v12 状态同步 + active plan；`git diff --check` clean；1239/0/0/0 与两轮独立 review 已随 `1db62088` 提交。
5. **提交后仅授权 fresh package + non-provider preflight**（identity/JAR hash/PostgreSQL/容器端口清理）。
6. **provider probes 与 fresh workstream 仍未授权**：须 Gate review PASS、三份文档提交获 clean SHA、preflight PASS 且用户显式授权后，方可运行一次双 provider probe；probes PASS 后仅创建一个 fresh `terminal-delivery@4` workstream。
7. **成熟度保持**：`1db62088` ENFORCED / NOT PROVEN；`b6bc8e79` ENFORCED / NOT PROVEN；`e64bf7c6` ENFORCED / NOT PROVEN；captcha-auth **NOT ACHIEVED**。未经 fresh real-case 不得标 PROVEN。

---

## 7. 审查记录

- Git：`merge-base --is-ancestor` ×3 PASS；HEAD=`1db62088`；`git diff --check` clean；dirty 集核对。
- 资源：workflow JSON SHA-256/shape 实测；target HEAD/tree/tracked files/requirement SHA 实测。
- Ledger：`workstream.json`/`workflow/{instance,current,definition}.json`/`nodes/*045*`/`runs/run-1786671207221/run.json` 逐字段读取。
- acpx 原始证据：index.json 4 条 4RQE 记录；record `c41799ab`（76,626 行 stream）中 prompt turn 边界（line 7/7434/15171/20238/23839/25826/32384）与唯一 `session/request_permission`（line 13357）定位 —— 实现轮零 permission request。
- Production：`ReviewDecideCommand`、`WorkstreamActionService:155`、`RoutingEngine.resolveOpenGate`（:332-356）、`GateDocument.allowedActions`、`LedgerUtils.allowedActions`。
- 未修改/提交文件、未调用 provider、未运行 real-case、未重复 full reactor。

---

*本报告为只读同行评审证据。*
