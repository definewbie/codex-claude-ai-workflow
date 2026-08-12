# Captcha-Auth Completion v7 Verification Gate — Peer Review

日期：2026-08-12

审查对象：`20260812-terminal-delivery-captcha-auth-completion-v7.md`
参照物：
- Human-decision checkpoint：`99f827d5`（已提交，38 文件，1210/0/0/0）
- v6 历史证据：`01eca0e6`（已提交）
- Active plan：`__ACTIVE__real-case-driven-orchestrator-simplification.zh-CN.md`
- Human-decision design v2：`human-decision-gate-convergence-20260812.zh-CN.md`
- Implementation diff review：`human-decision-gate-convergence-implementation-review-20260812.md`（PASS）
审查方式：独立、只读、代码事实驱动。未执行 package、probe、provider 或 real-case。

---

## 1. Boundary Review：PASS

12 项核实全部通过，无 P0/P1 发现。

### 1.1 Gate 目标（核实项 1）✅

Gate 第 7-9 行明确声明两个相互依赖的目标：
> (1) 完成 captcha-auth 产品；(2) 若 typed review 自然产生 BLOCKED，证明 checkpoint 99f827d5 能把它路由到 human gate

- 设计 §1 的同一目标链已在实现中完整落地 ✅
- 目标不包含"调高 revision limit""修改 model""resume v6"等越权操作 ✅

### 1.2 禁止人为制造 BLOCKED（核实项 2）✅

Gate 第 8-9 行：
> 不得人为制造 BLOCKED，也不得复用或 resume v6 workstream

Gate 第 48 行：
> 若没有自然 BLOCKED，产品 Gate 仍可判 PASS/FAIL，但 human-decision checkpoint 只能保持 ENFORCED / NOT PROVEN，不得伪造证明。

- 设计 §9 同样声明同一约束 ✅
- 与 active plan §"Current Active Step"第 37 行 "不 resume v6，不调高 revision limit，不改 provider/model/prompt" 一致 ✅

### 1.3 WAITING_FOR_HUMAN 语义（核实项 3）✅

Gate 第 50-51 行：
> WAITING_FOR_HUMAN 是本 Gate 的合法暂停点，不触发 quiesce/recovery；等待人类查看 packet 并提交决定

Gate Human Interaction Procedure §1-5 完整描述了跨 human gate 的 5 步操作流程 ✅

代码验证：
- `WorkflowStatus.WAITING_FOR_HUMAN` 是独立枚举值（`WorkflowStatus.java:6`），位于 `COMPLETED`/`WAITING_FOR_RECOVERY`/`BLOCKED` 之外 ✅
- `AutoRunCommand.requiresHumanDecision("BLOCKED")` 返回 `true`，autorun 在 `WAITING_FOR_HUMAN` + BLOCKED source 时退出（`AutoRunCommand.java:89-95`） ✅
- `RoutingEngine.resolveOpenGate()` 对 BLOCKED gate 强制 non-empty note，note 缺失时抛 IOException 且 gate 保持 open（`RoutingEngine.java:342-345`） ✅

### 1.4 CLI/UI 双入口（核实项 4）✅

Gate 第 63-73 行列出 5 步操作，首选 management UI/console，CLI 为等价后备：
- `dualctl start --run` ✅
- `dualctl autorun` ✅
- Management UI / operator console ✅
- `dualctl review decide --action approve --note <decision>` ✅
- `dualctl resume` / `dualctl autorun` ✅

代码验证：
- `ReviewDecideCommand` 已有 `--note` 选项（`ReviewDecideCommand.java:25`） ✅
- `ReviewApproveCommand` 已有 `--note` 选项（`ReviewApproveCommand.java:27`） ✅
- `console.js:1175-1184` + `mgt/app.js:1292-1300` 两个 UI 均实现 BLOCKED sourceOutcome 检查 ✅

### 1.5 Runner 局限性诚实声明（核实项 5）✅

Gate 第 60-61 行：
> 现有 run-real-case.sh 会把 autorun 在 WAITING_FOR_HUMAN 的有意停止当作失败并执行收尾，因此本轮不修改 harness，也不使用它跨越 human gate。

代码验证（`scripts/run-real-case.sh`）：
- 第 661-670 行：`run_autorun()` 调用 `dualctl autorun`，任何非零退出码判为 failure ✅
- 第 672-678 行：非零退出时打印日志并进入 stop-loss 流程 ✅
- `AutoRunCommand` 在 BLOCKED gate 返回 exit 1（line 94） ✅

Gate 的诚实声明与代码行为完全一致。不修改 harness 是正确的设计约束。

### 1.6 Human-decision PASS 标准来自 typed ledger facts（核实项 6）✅

Gate 第 37-40 行的 BLOCKED PASS 条件全部绑定 typed ledger facts：

| 条件 | 对应 typed fact | 代码锚点 |
|------|----------------|----------|
| decision node execution=COMPLETED, outcome=BLOCKED | `NodeResult.completed("BLOCKED", ...)` | `ReviewDecisionHandler.java:56` |
| GateDocument source_outcome=BLOCKED | `GateDocument.result.get("source_outcome")` | `RoutingEngine.java:192` |
| workflow=WAITING_FOR_HUMAN 而非 recovery | `WorkflowStatus.WAITING_FOR_HUMAN` | `AutoRunCommand.java:89` |
| UI/CLI 提交非空 human note | `resolveOpenGate` note param → gate resolution | `RoutingEngine.java:342-345` |
| resolved gate 逐字保存 note | `GateDocument.resolved(action, note)` | `GateDocument.java:36-51` |
| compiler 从 must_fix/missing_evidence 生成 contract-resolution | `blockedReviewQuestions()` | `ContractCompileHandler.java:420-424` |

所有条件均为 typed 事实，无自然语言匹配或 heuristic 推断 ✅

### 1.7 SoT（核实项 7）✅

Gate 第 20-24 行定义了完整的 SoT 链：
- target `REQUIREMENT.md` 与 Git baseline/final diff
- persisted workflow definition/instance
- review sidecar
- open/resolved `GateDocument`（source_outcome, subject_input_refs, action, note）
- review packet
- compiled contract 与 contract-resolution
- proposal/revision lineage
- run.json
- change-scope evaluation
- completion audit
- terminal acceptance
- PostgreSQL-backed verification、`mvn test` 和 README

第 23-24 行显式排除：
> UI 文案、Markdown 自述或进程退出码不能单独证明 PASS。

SoT 覆盖了从 review sidecar 到 terminal acceptance 的完整链条，与设计 §4 "Owner 与 SoT" 表一致 ✅

### 1.8 PROVEN 成熟度诚实声明（核实项 8）✅

Gate 第 108 行：
> checkpoint 99f827d5 当前为 ENFORCED / NOT PROVEN；只有自然 human-decision 链继续到 compile 才能升级为 PROVEN

Gate 第 48 行：
> 若没有自然 BLOCKED，产品 Gate 仍可判 PASS/FAIL，但 human-decision checkpoint 只能保持 ENFORCED / NOT PROVEN，不得伪造证明。

代码验证：
- 设计 §9 同样声明 "只有自然 real-case 真实到达 human gate...才可标 PROVEN" ✅
- 实现 review §9 同样判定 "ENFORCED（via deterministic tests）/ NOT PROVEN（no fresh human-gate real-case）" ✅

### 1.9 Frozen workflow definition（核实项 9）✅

Gate 第 79-88 行冻结了 workflow identity：

| 字段 | Gate 声明 | Git 验证 |
|------|-----------|----------|
| variant | terminal-delivery | ✅ |
| workflow | agent-lifecycle-terminal-delivery@2 | ✅（version=2） |
| source SHA-256 | `3b13d6c64a42870cc0a4c9c921a8315a6cc879b83527de20f8ed16a79906717e` | ✅（匹配） |
| nodes | 29 | ✅ |
| edges | 48 | ✅（v1=45 + 3 BLOCKED） |
| nominal acpx agent nodes | 9 | ✅（见下方验证） |
| handoff nodes | 0 | ✅ |

9 个 nominal agent nodes 验证：
```
intake.claude_brainstorm
intake.codex_product_review
design.claude_design
design.codex_arch_review
plan.claude_plan
plan.codex_plan_review
implement.claude_implement
implement.claude_self_test_report
acceptance.codex_verifier_review
```
其余 11 个 TASK 节点为 context builder、contract compile、artifact validation、failure classify、final report 和 delivery prepare —— 均非 ACPX agent dispatch ✅

### 1.10 Mechanical preflight 不判断模型合规（核实项 10）✅

Gate 第 101-102 行：
> probe 只验证 provider/ACP 可用，不验证模型会遵守业务合同。不得根据 provider 背后的模型名称改变 Gate 判定。

Preflight 第 93-99 行列出的 4 项均为基础设施验证：
1. Codex probe（验证 ACP 可用） ✅
2. Claude probe（验证 ACP 可用） ✅
3. PostgreSQL 容器启动 + `pg_isready` + `SELECT 1`（验证数据库可用） ✅
4. Dual/target/workflow/JAR identity 记录（验证 artifact freshness） ✅

没有任何一项要求验证模型行为或输出质量 ✅

### 1.11 Markdown vs business facts（核实项 11）✅

Gate 第 23-24 行：
> UI 文案、Markdown 自述或进程退出码不能单独证明 PASS

Gate 第 73 行：
> UI 和 CLI 只是 command 入口，不是事实 owner

SoT 链（第 20-23 行）全部由 typed ledger facts 组成：
- `GateDocument` 的 `source_outcome`、`subject_input_refs`、action、note
- review sidecar
- compiled contract 与 contract-resolution
- run.json
- change-scope evaluation
- completion audit

设计 §6.4 同样规定：
> handler 不扫描 gate 目录、不从 reviewer 正文或 decision 再推断该值

与设计一致 ✅

### 1.12 不新增 harness/platform 能力（核实项 12）✅

Gate 第 112 行：
> 不新增 runner monitor、human-decision schema、module、registry、engine 或平台层。

设计 §10 "明确不做"：
> 不增加专用 product-choice schema、question registry 或通用规则引擎
> 不在本 checkpoint 修改 provider、model、strict parser、revision patch 或 prompt 内容

Human Interaction Procedure 第 60-61 行：
> 本轮不修改 harness

全部使用既有 CLI/UI 能力 ✅

---

## 2. Findings

### P0：无

### P1：无

### P2-1：External preflight 容器清理的 v7 probe 容器是前向占位

- **Gate 第 97 行**：`清理旧 v5/v6/v7 probe 容器`
- **事实**：v7 Gate 尚未执行，不存在 v7 probe 容器。清理指令在首次执行时无害（`docker rm -f` 对不存在的容器仅输出 warning），但措辞暗示存在旧 v7 容器，不够精确。
- **建议**：改为 "清理旧 v5/v6 probe 容器"，或将 v7 保留但注明 "如果存在"。

### P2-2：Preflight 输出路径 `target/verification-preflight/captcha-auth-v7/` 未经 .gitignore 验证

- **Gate 第 93 行**：写入 `dual-orchestrator/target/verification-preflight/captcha-auth-v7/`
- **事实**：`target/` 是 Maven 输出目录，通常在 `.gitignore` 中。preflight 工件不应被提交，因为它们是运行事实而非 Gate 定义。但 Gate 未显式要求确认路径被 Git 忽略。
- **建议**：Preflight 末尾增加一步 "确认 preflight 输出目录被 Git 忽略，不会意外提交"。

### P2-3："TASK" vs "acpx.agent_run" 术语差异

- **Gate 第 86 行**：`nominal acpx.agent_run nodes: 9`
- **事实**：实际 workflow JSON 中这些节点的 `kind` 为 `TASK`，而非 `AGENT_RUN`。`acpx.agent_run` 是业务语义描述。熟悉 JSON schema 的读者可能短暂困惑。
- **建议**：在 frozen workflow 块中增加一行注释说明 "9 个 TASK 节点实际调度 ACPX agent，其余 TASK 节点为 context build / compile / validation / report / delivery"。

---

## 3. Gate 可执行性：YES

Human Interaction Procedure（第 63-73 行）的 5 个步骤全部映射到既有 CLI 命令：

| 步骤 | 命令 | 验证 |
|------|------|------|
| 1. 创建 fresh workstream | `dualctl start --run` | ✅ 既有命令 |
| 2. 驱动普通 gate | `dualctl autorun` | ✅ 既有命令 |
| 3. Human decision（UI 首选 / CLI 后备） | UI：management UI/console / CLI：`dualctl review decide --action approve --note <decision>` | ✅ 既有 UI + CLI |
| 4. 继续同一 workstream | `dualctl resume` / `dualctl autorun` | ✅ 既有命令 |
| 5. 生成证据 | `dualctl summary` / 既有 report 脚本 | ✅ |

所有命令均为生产既有命令，operator 无需学习新接口 ✅

Trigger precondition（第 25-33 行）的检查项全部可执行：
- Git identity/ancestry/hash 验证：标准 `git` 命令 ✅
- Workflow identity/shape 验证：标准文件 hash + JSON 解析 ✅
- Fresh package 验证：`mvn package` ✅
- PostgreSQL health：`pg_isready` + `SELECT 1` ✅
- Provider probe：既有 probe 机制 ✅

---

## 4. UI/CLI Human Interaction 可行性：FEASIBLE

Human Interaction Procedure 正确覆盖了 5 条入口路径：

| 入口 | procedure 覆盖 | 代码验证 |
|------|---------------|----------|
| Management UI | "首选 management UI/console" | `mgt/app.js:1292-1300` BLOCKED 检查 ✅ |
| Operator console | "阅读完整 review packet" | `console.js:1175-1184` BLOCKED 检查 ✅ |
| CLI `review decide --action approve --note` | "CLI 等价后备" | `ReviewDecideCommand.java:25` ✅ |
| CLI `review approve --note` | 未显式列出但等价 | `ReviewApproveCommand.java:27` ✅ |
| Autorun | BLOCKED 时停止 | `AutoRunCommand.java:89-95` ✅ |

所有 5 条路径最终经过统一 enforcement point：`RoutingEngine.resolveOpenGate()` → 空 note + BLOCKED → IOException → gate 保持 open ✅

procedure 中 operator 需手动判断 "输出表明需要 human decision"（步骤 2 的 `WAITING_FOR_HUMAN`），该状态来自 autorun 的 exit 1 + 输出文本 `"Autorun stopped: the current review requires a human decision note."`，operator 可识别 ✅

---

## 5. SoT 与 End-to-End Invariant

### SoT 链：完整且无重推断

```
target REQUIREMENT.md (SHA-256: 42078b01...)
→ proposal sidecar (intake.claude_brainstorm output)
→ review sidecar (intake.codex_product_review output, decision=BLOCKED)
→ decision node: COMPLETED, outcome=BLOCKED
→ RoutingEngine: GateDocument(source_outcome=BLOCKED, subject_input_refs=reviewer sidecar ref)
→ HumanReviewGateHandler: review packet(sourceOutcome=BLOCKED)
→ UI/CLI: human action + note
→ resolveOpenGate: note enforcement + gate resolution
→ ContractCompileHandler: blockedReviewQuestions() → contract-resolution
→ terminal acceptance → completion audit → mvn test → README
```

每个环节的 consumer 只读取上一环节的 typed output，不重新扫描或推断。设计 §11 "Boundary Closure" 已完整闭合 ✅

### End-to-End Invariant 验证

| 环节 | Gate 覆盖 | 代码锚点 |
|------|----------|----------|
| Declared by | PASS 条件第 37-40 行 | workflow BLOCKED edge + review sidecar decision=BLOCKED |
| Proven by | v6 ledger（历史）+ fresh real-case（待运行） | `node-intake-decision-020.json` |
| Consumed by | GateDocument.source_outcome | `RoutingEngine.java:192` |
| Enforced before | resolveOpenGate note enforcement | `RoutingEngine.java:342-345` |
| Failure route | WAITING_FOR_HUMAN → 不进 recovery | `AutoRunCommand.java:89-95` |
| Cannot-bypass | deterministic tests 1210/0/0 | 设计 §7 所列 12 项测试 |

---

## 6. Stop-loss 与 INVALID 判定

### Stop-loss：完整且不会误杀 WAITING_FOR_HUMAN

Gate 第 49-53 行的 stop-loss 条件：

| 条件 | 判定 | 说明 |
|------|------|------|
| freshness/identity/probe 不符 | 在 provider 前停止 | 正确：preflight 失败不应消耗 provider token ✅ |
| runtime typed failure | 出现后不得继续 | 正确 ✅ |
| scope/lineage violation | 出现后不得继续 | 正确 ✅ |
| failed correction | 出现后不得继续 | 正确 ✅ |
| non-zero process | 出现后不得继续 | 正确 ✅ |
| WAITING_FOR_HUMAN | 合法暂停点，不触发 quiesce/recovery | 正确：明确区分 stop-loss 与合法暂停 ✅ |
| wall-clock 90 分钟 | 终止 | 保守但合理 ✅ |
| ledger 与副作用矛盾 | 终止 | 正确 ✅ |
| 同类技术失败重复两次 | 终止 | 防止死循环 ✅ |

关键区分：WAITING_FOR_HUMAN 是唯一合法的"非终态暂停"，其余均为 stop-loss 终止 ✅

### INVALID：覆盖全面

Gate 第 45-48 行的 INVALID 条件：

| 条件 | 验证 |
|------|------|
| Dual/target dirty | ✅ 由 trigger precondition 检查 |
| baseline/hash/workflow/fresh JAR/probe 不符 | ✅ 由 trigger precondition 检查 |
| 复用旧 session/workstream | ✅ "不修改 harness" 保证不 resume |
| 使用非 terminal-delivery workflow | ✅ |
| 启动前 target 已有实现 | ✅ 已验证 target 无 src/ pom.xml |
| operator 在运行中修改代码/prompt/parser/workflow/revision limit/provider/model | ✅ |
| 无自然 BLOCKED 时不得标 PROVEN | ✅ |

---

## 7. 是否允许仅提交 Gate + active plan：YES，满足条件

条件检查：

1. **Gate 已通过同行评审** → 本文即为本次评审，结果为 PASS ✅
2. **Gate 不包含对已 PASS 实现的重新审查** → Gate 引用 `99f827d5` 作为 baseline，不重新审查代码 ✅
3. **Gate 明确标注 `DRAFT / PEER REVIEW REQUIRED / NOT AUTHORIZED TO RUN`** → 需在提交前更新状态 ✅
4. **Gate 不授权 package、probe、provider 或 real-case** → 第 115 行显式禁止 ✅
5. **active plan 的变更仅包含 Gate 引用更新** → `git diff` 显示 active plan 仅有 6 insertions、3 deletions，均为 Gate 状态与引用更新 ✅

**建议提交前更新**：
- Gate 状态从 `DRAFT / PEER REVIEW REQUIRED / NOT AUTHORIZED TO RUN` 改为 `REVIEW PASS / COMMIT AUTHORIZED / PREFLIGHT: NOT YET AUTHORIZED`
- 解决 P2-1（容器清理措辞）和 P2-2（.gitignore 验证）
- 在 active plan 中记录本次 review 的结论与 review 文件路径

---

## 8. 提交后是否允许执行 deterministic/external preflight：YES，条件满足

提交后（clean SHA），以下操作均为只读/infrastructure，不消耗 provider token，不违反 Gate 约束：

### 允许的操作

| 操作 | 理由 |
|------|------|
| `mvn package` → 记录 JAR hash/mtime | Gate trigger precondition §"How precondition is checked before run" 要求 |
| Git identity/ancestry check | 同上 |
| Workflow definition hash/shape check | 同上 |
| Target identity/hash check | 同上 |
| `docker run postgres:16-alpine` + health check | 同上 |
| Codex/Claude minimal probe | Gate External Preflight §1-2 |

### 不允许的操作

| 操作 | 理由 |
|------|------|
| 创建 workstream | Gate 第 32-33 行："任一证据缺失时不得创建 workstream" |
| 调用 provider 执行 business task | Gate 第 115 行："当前不授权 package、probe、provider 或 real-case" |
| 修改 harness | Gate 第 60-61 行："本轮不修改 harness" |
| 修改 prompt/parser/model/workflow | 设计 §10 明确禁止 |

**注意**：Gate External Preflight 的 4 项操作（probes + PostgreSQL + identity recording）与 trigger precondition 的前 4 项完全重叠。preflight 可以在 Gate review PASS 后、获得 provider 授权前独立执行。但 preflight PASS 后**仍需显式授权**才能创建 workstream 并调用 provider。

---

## 9. Gate ↔ Active Plan 一致性

| 维度 | Gate | Active Plan |
|------|------|-------------|
| 目标 | 完成 captcha-auth + 自然 BLOCKED 时证明 human-decision | 同一目标 ✅ |
| 不 resume v6 | 第 9 行 "不得复用或 resume v6 workstream" | 第 38 行 "不 resume v6" ✅ |
| 不调高 revision limit | 隐含于 "不改 provider/model/prompt" | 第 38 行 "不调高 revision limit" ✅ |
| run-real-case.sh 局限性 | 第 60-61 行 | 隐含于 existing script behavior ✅ |
| human decision 入口 | UI 首选 + CLI 后备 | 第 37 行 "用户通过 UI 或 CLI 提交决定" ✅ |
| PROVEN 条件 | 第 108 行 | 第 37-38 行 "才可把该边界标记为 PROVEN" ✅ |
| 当前授权范围 | 仅同行评审 | 第 36 行 "当前只授权同行评审 Gate" ✅ |

文档间无矛盾 ✅

---

## 10. 总结

| 维度 | 结果 |
|------|------|
| Boundary Review | **PASS**（12/12 项通过） |
| P0 Findings | 0 |
| P1 Findings | 0 |
| P2 Findings | 3（措辞/路径/术语，不阻塞执行） |
| Gate 可执行性 | **YES** |
| UI/CLI 可行性 | **FEASIBLE** |
| SoT 完整性 | **完整，无重推断** |
| End-to-End Invariant | **DECLARED → ENFORCED 链闭合** |
| Stop-loss | **完整，正确区分 WAITING_FOR_HUMAN** |
| INVALID | **覆盖全面** |
| 只提交 Gate + active plan | **YES（建议更新状态与措辞）** |
| 提交后允许 preflight | **YES（只允许 infrastructure 操作，不允许 provider）** |
| 允许创建 workstream | **NO（需 preflight PASS 后的显式授权）** |

### 提交前建议（不阻塞）

1. **S-1**：Gate 状态从 `DRAFT / PEER REVIEW REQUIRED / NOT AUTHORIZED TO RUN` 改为 `REVIEW PASS / COMMIT AUTHORIZED / PREFLIGHT NOT YET AUTHORIZED`
2. **S-2**：P2-1 — External preflight "清理旧 v5/v6/v7 probe 容器" → "清理旧 v5/v6 probe 容器（v7 首次执行，无旧容器）"
3. **S-3**：P2-2 — External preflight 增加步骤 5 "确认 preflight 输出目录被 Git 忽略"
4. **S-4**：P2-3 — Frozen Workflow 块增加注释："9 个 TASK 节点实际调度 ACPX agent（其余 TASK 节点为 context/compile/validation/report/delivery）"
5. **S-5**：active plan 增加本次 review 的引用：`Gate review：docs/testing/verification-gates/20260812-terminal-delivery-captcha-auth-completion-v7-review.md（PASS）`
