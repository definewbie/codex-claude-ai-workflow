# Captcha-Auth Completion v8 Verification Gate — 独立代码审查

日期：2026-08-12

审查对象：
1. `dual-orchestrator/docs/testing/verification-gates/20260812-terminal-delivery-captcha-auth-completion-v8.md`
2. `dual-orchestrator/docs/plans/__ACTIVE__real-case-driven-orchestrator-simplification.zh-CN.md`

审查方式：独立、只读、逐行核对。未修改文件，未运行 package/provider/probe/real-case。
每项声明均对照 production code、workflow definition、CLI 实现、checkpoint 023924b6 和 target baseline 核实。

---

## 1. Boundary Review：PASS

**0 P0 / 0 P1 / 2 P2**

18 项核实全部通过。Gate 与 active plan 一致、可执行、授权边界清晰。

---

## 2. Findings

### P0：无

### P1：无

### P2-1：`gate.human_acceptance` 的 `approve` 动作缺少显式 workflow edge

- **Gate 行**：L34-37 PASS 要求 `terminal acceptance=ACCEPT`
- **Production**：`agent-lifecycle.terminal-delivery.json`
  - `gate.human_acceptance` 有 `allowed_actions=["approve", "revise"]`，但只有一条 `--[revise]--> context.implement_claude` edge
  - 没有 `--[approved]--> ...` 或 `--[approve]--> ...` edge
- **实际行为**：`RoutingEngine.resolveOpenGate()` L338-341 验证 `hasEdge || actionAllowed`，因此 `approve` 在 `allowed_actions` 中 → 合法。但 `completeAndRoute` 没有匹配 edge → 工作流隐式终止（无更多节点执行）
- **影响**：operator 在 acceptance gate 执行 `--action approve` 后，工作流正常终止为 COMPLETED。行为正确但 edge 缺失是隐式的——建议 workflow definition 显式添加 `approved` 到终端/COMPLETED 的 edge，或 Gate 文档注释说明 acceptance approve 的隐式终止语义
- **不阻塞**：`RoutingEngine` 的 `hasEdge || actionAllowed` 双重验证 + `allowed_actions` 包含 `approve` 确保 CLI 可执行

### P2-2：target baseline 中的 `VERIFICATION-GATE.md` 用途声明不够明确

- **Gate 行**：L25-26 target 保持 HEAD `7c7e35e8`、tree `bab065a1`，"无实现文件"
- **Production**：target 目录实际包含两个文件：
  ```
  REQUIREMENT.md  (3249 bytes)
  VERIFICATION-GATE.md  (3130 bytes)
  ```
- **事实**：`VERIFICATION-GATE.md` 是 v7（或更早）Verification Gate 文档的副本，不是 captcha-auth 业务实现文件。它被放置为 target baseline 的参考文档
- **建议**：Gate 文档显式说明 `VERIFICATION-GATE.md` 是参考文档、非实现文件；或在 preflight 中明确列出允许的非实现文件清单
- **不阻塞**：两个文件都不是 Java/Spring Boot/Thymeleaf/PostgreSQL 实现文件，baseline 确实"没有实现文件"

---

## 3. Verification Gate 九字段逐项可执行性

| 字段 | Gate 声明 | Production 核实 | 可执行 |
|------|----------|----------------|--------|
| **Target** | 使用 `agent-lifecycle-terminal-delivery@2` 完整交付 captcha-auth-app | workflow definition version=2, 9 agent nodes, 0 handoff | ✅ |
| **Source of truth** | `run.json`、`agentResultAcquisition`、`artifactContractResults`、`executionOutcome`、change-scope、completion audit、terminal acceptance、PostgreSQL tests、`mvn test`、README | 所有字段均存在于 production `AgentResultAcquisitionDocument`（`AcpxAgentResultAcquirer.java:439-494`）、`AgentExecutionOutcome`（`AgentExecutionOutcomeResolver.java:15`）、change-scope evaluation、completion audit | ✅ |
| **Trigger precondition** | Dual 包含 `023924b6`、Dual 与 target 均 clean、target HEAD/tree/requirement hash 精确匹配 | `023924b6` 是 HEAD 祖先 ✅、target HEAD=`7c7e35e8`/tree=`bab065a1`/req=`42078b01...` 全部匹配 ✅、target `git status --porcelain` 空 ✅ | ✅ |
| **Preflight 记录** | Git identity、clean、checkpoint ancestry、requirement hash、workflow hash/shape、build、JAR path/hash/mtime、PostgreSQL container/health、双 provider probes | preflight 步骤覆盖所有 `run-real-case.sh` 和相关脚本中的检查点 | ✅ |
| **PASS** | workflow=COMPLETED、ACCEPT/ACCEPT_WITH_NOTES、全部功能+测试+README+scope+lineage+completion 通过、handoff 不存在 | workflow定义中 handoff=0 ✅、PASS 条件完整覆盖所有 terminal delivery 产出 | ✅ |
| **FAIL** | workflow 未完成、acceptance 拒绝、业务/测试/scope/lineage/completion 失败、strict parser/schema 失败、permission denial 为 primary outcome、timeout/interruption、human gate 绕过 | 所有失败路径均对应 production 规则：`AgentExecutionOutcomeResolver` 的 blocking signal/process usability/contract 检查链（L23-121）、`RoutingEngine` routing | ✅ |
| **INVALID** | Dual/target dirty、hash/workflow/JAR/probe 不符、复用旧 session/workstream、target 已有实现、operator 修改 prompt/parser/workflow/revision limit/provider、无法证明 clean SHA | preflight 检查清单完整、禁止复用旧 workstream、禁止修改运行中配置 | ✅ |
| **Stop-loss** | freshness/identity/probe 不符→停止；strict contract/scope/lineage 失败→停止；primary permission failure→停止；timeout/interruption→停止；90min wall clock→停止；revision budget 耗尽→停止；同类失败两次→停止 | `WorkflowStatus.WAITING_FOR_HUMAN` 不在 stop-loss 触发条件中（`CliWorkflowLoop.java:13-17` TERMINAL_OR_PAUSED 包含 WAITING_FOR_HUMAN）✅、`max_revisions=3` 由 decision node config 管理 ✅ | ✅ |
| **Run artifact freshness** | Gate review PASS → 提交 Gate + active plan → clean SHA → fresh package → JAR/workflow hashes | 流程 closed-loop：提交→SHA→package→hash 记录 | ✅ |

---

## 4. 产品主结论与三个条件性技术结论

### 产品主结论

```
captcha-auth 产品完成 = workflow COMPLETED
    + terminal acceptance ACCEPT/ACCEPT_WITH_NOTES(blocking=false, must_fix=[])
    + 全部业务功能 + PostgreSQL-backed tests + mvn test
    + README + change-scope + lineage + completion audit
    + handoff 不存在
```

门禁正确。产品主结论不依赖任何条件性技术结论。**通过 P2-1**。

### 条件性技术结论 A：Assistant Presentation

```
触发条件：自然出现 prose + 单个完整 json fence + prose
PASS/PROVEN：SINGLE_FENCED_JSON_EXTRACTED + candidate=fence payload + 同一 strict chain 通过 + 无 correction
FAIL：多 fence/未闭合/嵌套/空/non-json → reject, raw Markdown → routing
未触发：023924b6 保持 ENFORCED/NOT PROVEN
```

与 `AcpxAgentResultAcquirer.singleJsonFencePayload()`（L306-331）一致。✅

### 条件性技术结论 B：Contained Permission Denial

```
触发条件：自然出现 PERMISSION_BLOCKED + non-zero complete process
PASS/PROVEN：三条件全满足 → SUCCESS_WITH_WARNINGS + PERMISSION_BLOCKED 在 secondarySignals
FAIL：任一条件缺失但节点成功，或 signal 被删除，或全部满足但旧路径覆盖为 FAILED
未触发：023924b6 保持 ENFORCED/NOT PROVEN
```

与 `AgentExecutionOutcomeResolver.isContainedPermissionDenial()`（L128-139）一致。✅

### 条件性技术结论 C：Human Decision

```
触发条件：review 自然产生 BLOCKED
PASS/PROVEN：decision=COMPLETED/BLOCKED, workflow=WAITING_FOR_HUMAN, GateDocument.source_outcome=BLOCKED
未触发：99f827d5 保持 ENFORCED/NOT PROVEN
```

与 `RoutingEngine.resolveOpenGate()`（L319-352）一致：`BLOCKED` gate 要求非空 note（L344-346），gate document 保存 source_outcome。✅

**结论**：产品主结论正确独立于三个条件性技术结论。三个条件性结论各自有明确的触发条件、PASS/PROVEN 标准、FAIL 标准和未触发时的正确标记（ENFORCED/NOT PROVEN）。

---

## 5. Human UI/CLI 路径可行性

### CLI 路径

Gate 描述（L93-94）：
```
dualctl review decide --action approve --note <decision> --workdir <workspace> <workstream>
```

**Production 核实**（`ReviewDecideCommand.java:17-66`）：

| CLI 要素 | Gate | Production | 匹配 |
|----------|------|------------|------|
| 命令 | `dualctl review decide` | `@Command(name = "decide")` → `dualctl review decide` | ✅ |
| `--action` | `approve` / `revise` | `@Option(names = "--action", required = true)` | ✅ |
| `--note` | `<decision>` | `@Option(names = "--note")` | ✅ |
| `--workdir` | `<workspace>` | `@Option(names = "--workdir")` | ✅ |
| `<workstream>` | positional | `@Parameters(index = "0")` | ✅ |
| `--action revise` | 重做 proposal | `requireAction()` 接受任意非空字符串 | ✅ |
| BLOCKED gate + 空 note | 必须填写 non-empty note | `resolveOpenGate()` L344-346 throws if BLOCKED + blank note | ✅ |

**`--run` flag**（L93）：`ReviewDecideCommand` 支持 `--run` + `--max-steps`（L28-32），决定后立即 resume。Gate 没有显式要求 `--run`，但 CLI 支持。✅

### UI 路径

Gate 描述（L92）：
> 打开 management UI 的 workstream gate/review packet，核对 sourceOutcome、review subject、must_fix/missing_evidence，填写明确的非空 decision note，再选择 approve 或 revise

UI 是 management server 的前端消费 `WorkstreamAttention` API（`WorkstreamAttentionEvaluator.java` L65-72 将 `WAITING_FOR_HUMAN` 分类为 `WAITING_FOR_HUMAN` category、"Human gate is open" label、`openGateIds` 列表）。最终决定通过同一 `WorkflowFileLedger.resolveOpenGate()` API 提交。✅

**结论**：UI 和 CLI 路径均可执行，最终写入同一 ledger API。通过 P2-1。

---

## 6. Frozen Workflow 与 Target Baseline 核实

### Frozen Workflow

| 属性 | Gate 声明 | Production 核实 | 匹配 |
|------|----------|----------------|------|
| variant | `terminal-delivery` | ✅ | ✅ |
| workflow | `agent-lifecycle-terminal-delivery@2` | `version: 2` | ✅ |
| source path | `dual-runtime/src/main/resources/.../agent-lifecycle.terminal-delivery.json` | 文件存在 | ✅ |
| source SHA-256 | `3b13d6c64a42870cc0a4c9c921a8315a6cc879b83527de20f8ed16a79906717e` | `shasum -a 256` 精确匹配 | ✅ |
| nodes | 29 | `len(nodes) = 29` | ✅ |
| edges | 48 | `len(edges) = 48` | ✅ |
| agent nodes | 9 | 9 nodes with `config.agent` set (5 Claude + 4 Codex) | ✅ |
| handoff nodes | 0 | No handoff-related nodes | ✅ |

**9 agent nodes**：
1. `intake.claude_brainstorm` (claude)
2. `intake.codex_product_review` (codex)
3. `design.claude_design` (claude)
4. `design.codex_arch_review` (codex)
5. `plan.claude_plan` (claude)
6. `plan.codex_plan_review` (codex)
7. `implement.claude_implement` (claude)
8. `implement.claude_self_test_report` (claude)
9. `acceptance.codex_verifier_review` (codex)

**Decision nodes**：intake/design/plan 均为 `max_revisions=3`，acceptance 为 `max_revisions=1`。Gate 正确投影了 `max_revisions=3` 作为语义 revision 的唯一预算来源。

### Target Baseline

| 属性 | Gate 声明 | Production 核实 | 匹配 |
|------|----------|----------------|------|
| HEAD commit | `7c7e35e89a66b14c29a335958ad90c8a92779fa0` | `7c7e35e` (short) | ✅ |
| tree | `bab065a1ff9fe4d7032d1d573b7d1b6c1181a574` | `git rev-parse HEAD^{tree}` 精确匹配 | ✅ |
| requirement SHA-256 | `42078b01418be3079852869891730a9b55f62ebf572c17b187059c1cd03138f0` | `shasum -a 256 REQUIREMENT.md` 精确匹配 | ✅ |
| Clean state | 无未提交改动 | `git status --porcelain` 空 | ✅ |
| 实现文件 | 无 | 只有 REQUIREMENT.md + VERIFICATION-GATE.md | ✅ |
| `.git` | present | 存在 | ✅ |

**结论**：frozen workflow identity 与 target baseline 全部精确匹配。✅

---

## 7. 是否满足只提交 Gate + Active Plan 的条件：YES

- Gate 文档状态：`DRAFT / AWAITING INDEPENDENT REVIEW / PROVIDER NOT AUTHORIZED / NOT YET RUN`
- Active plan 已正确将 v7 标记为 consumed、v8 标记为唯一 active objective
- 两个文档均为 doc-only 变更，不涉及 production code
- 提交后的 clean SHA 将作为 preflight 的基准

建议提交前：Gate 文档的 `状态` 行（L5）应更新为 `READY / INDEPENDENT REVIEW PASS / PROVIDER NOT AUTHORIZED / NOT YET RUN`。

---

## 8. 提交后是否允许执行 Fresh Package 和 External Preflight：YES

Gate 的 Preflight And Execution Order（L113-125）明确分阶段：

1. **当前授权**：独立审查 + 修订 Gate + active plan → 提交 → clean execution SHA ✅
2. **提交后**：fresh Maven package → 记录 JAR freshness → external preflight（Git identity、workflow hash、Claude/Codex minimal probes、PostgreSQL container） ✅
3. **Preflight PASS 后**：创建唯一 fresh workstream ✅

当前 active plan（L28-31）也正确声明：
> 当前只授权 Gate/active-plan 同行评审；未授权 package、probe、provider 或 real-case。

**结论**：Gate review PASS 后，fresh package 与 external preflight 是下一个明确授权的步骤。Provider/real-case 授权需在 preflight PASS 后单独确认。✅

---

## 9. Provider/Real-Case 是否仍未授权：是

Gate 文档（L5, L137-138）和 active plan（L28-31）均明确声明：
- `PROVIDER NOT AUTHORIZED`
- `NOT YET RUN`
- "不授权 package、probe、provider 或 real-case"

三个条件性技术边界的 `NOT PROVEN` 状态保持不变。✅

---

## Appendix A：18 项核实一缆表

| # | 核实点 | 结果 | 关键证据 |
|---|--------|------|----------|
| 1 | 主目标 = captcha-auth 产品，非证明 presentation/permission 修复 | ✅ | Gate L7-9 |
| 2 | 明确禁止人为制造 prose+fence、permission denial、BLOCKED、revision | ✅ | Gate L8-9, INVALID L42-44 |
| 3 | PASS = COMPLETED + ACCEPT + 全部业务/测试/scope/lineage/completion | ✅ | Gate L34-37 |
| 4 | FAIL 正确区分 strict parser/schema、primary permission、contained warning、timeout、业务失败 | ✅ | Gate L37-43 + Conditional Verdicts |
| 5 | checkpoint 023924b6 三条件准确投影 | ✅ | Gate L73-75 ↔ `AgentExecutionOutcomeResolver.java:135-138` |
| 6 | Contained PERMISSION_BLOCKED → secondarySignals + SUCCESS_WITH_WARNINGS | ✅ | Gate L74-75 ↔ `AgentExecutionOutcomeResolver.java:103-111` |
| 7 | 无"exit 5 = 无条件 FAIL"旧口径 | ✅ | Gate FAIL 无此规则；条件 B 覆盖 contained 降级 |
| 8 | Presentation 条件：SINGLE_FENCED_JSON_EXTRACTED, candidate=fence payload, 同一 strict chain, 不自动修 JSON | ✅ | Gate L62-66 ↔ `AcpxAgentResultAcquirer.java:289-303, 46-68` |
| 9 | 未触发 presentation/permission/BLOCKED 时产品 Gate 仍可 PASS | ✅ | Gate L42-44 |
| 10 | Semantic revision 只服从 max_revisions=3 | ✅ | Gate L47 ↔ decision nodes config: intake/design/plan=3 |
| 11 | Human Gate UI/CLI 可执行 | ✅ | Gate L92-97 ↔ `ReviewDecideCommand.java` |
| 12 | WAITING_FOR_HUMAN 合法暂停点 | ✅ | `WorkflowStatus.java:12-14` isTerminal=COMPLETED only; `CliWorkflowLoop.java:13-17` TERMINAL_OR_PAUSED |
| 13 | Preflight 检查清单完整 | ✅ | Gate L113-123 |
| 14 | Target baseline 只有 REQUIREMENT.md + VERIFICATION-GATE.md | ✅ | `ls` output; no .java/.xml/.properties files |
| 15 | Stop-loss 可执行，不因业务可继续而提前终止 | ✅ | Gate L44-49 |
| 16 | 无新增 platform capability | ✅ | Gate L133 |
| 17 | Active plan: v7 consumed, v8 sole active | ✅ | Active plan L13-31 |
| 18 | Gate PASS 后只授权提交文档+preflight，不得声称产品完成或 PROVEN | ✅ | Gate L137-138; active plan L28-31 |

---

## Appendix B：Production Anchor 对照表

| 生产锚点 | 文件 | Gate 相关行 | 状态 |
|----------|------|------------|------|
| `AgentExecutionOutcomeResolver.isContainedPermissionDenial()` | `dual-core/.../AgentExecutionOutcomeResolver.java:128-139` | Conditional B L73-77 | ✅ |
| `AcpxAgentResultAcquirer.singleJsonFencePayload()` | `dual-adapter-acpx/.../AcpxAgentResultAcquirer.java:306-331` | Conditional A L62-66 | ✅ |
| `AcpxPermissionProfile.autoApprovedTools` | `dual-adapter-acpx/.../AcpxPermissionProfile.java:25` | (v7 修复, checkpoint 023924b6) | ✅ |
| `ReviewDecideCommand` | `dual-cli/.../ReviewDecideCommand.java:17-66` | Human Procedure L93-94 | ✅ |
| `RoutingEngine.resolveOpenGate()` | `dual-ledger-file/.../RoutingEngine.java:319-352` | Conditional C L82-85 | ✅ |
| `agent-lifecycle.terminal-delivery.json` | `dual-runtime/.../agent-lifecycle.terminal-delivery.json` | Frozen Workflow L100-110 | ✅ |
| Checkpoint `023924b6` | git | Trigger Precondition L26, Conditional A/B | ✅ |
| Target | `/Users/kriswang/work/projects/ai/captcha-auth-app` | Target L13-15 | ✅ |

---

## Appendix C：Active Plan v7→v8 过渡核实

Active plan (`__ACTIVE__real-case-driven-orchestrator-simplification.zh-CN.md`)：

- **Current Active Step** (L11-31)：标题 "Captcha-Auth Completion v8 / GATE DRAFT, AWAITING REVIEW" ✅
- **v7 consumed** (L13-16)：明确记录 `ws-20260812T035340Z-E3P3` 已消费、stop 原因、两条边界已由 `023924b6` 修复、独立 review 为 PASS ✅
- **v8 唯一 active** (L27-31)：新 Gate 路径明确，主目标 = captcha-auth 产品，未授权 package/probe/provider/real-case ✅
- **Historical steps** (L33-261)：v6 FAIL CONSUMED、v5 FAIL CONSUMED、v4 FAIL CONSUMED、v3 FAIL CONSUMED、v2 FAIL CONSUMED、revision-verbatim-probe FAIL CONSUMED。全部正确标注 CONSUMED/SUPERSEDED ✅
- **Maturity tracking** (L25)：`023924b6` = `ENFORCED / NOT PROVEN` ✅

**结论**：active plan 准确反映当前状态——v7 consumed、v8 唯一 active、所有历史 step 正确标注。

---

## 最终裁定

### 当前授权

- ✅ 提交 Gate v8 + active plan（更新 v7 consumed + v8 唯一 active）
- ✅ 获得 clean execution SHA
- ✅ Fresh Maven package + external preflight

### 未授权

- ❌ Provider/real-case 执行
- ❌ 将 deterministic `ENFORCED` 标记为 real-case `PROVEN`
- ❌ 声称 captcha-auth 产品已完成
- ❌ Resume v7 workstream 或复用旧 Agent sessions

### 不变量

> deterministic tests 通过最多只能证明 `ENFORCED`，不能把 captcha-auth workflow 标记为 `PROVEN`。captcha-auth 只有完整业务、验证和 terminal acceptance 同时通过才可标记 `ACHIEVED`。
