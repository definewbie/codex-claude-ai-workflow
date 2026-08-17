# Peer Review: Captcha-Auth Completion v16 Verification Gate

日期：2026-08-17
评审基线：Dual `e7939f1dd83fd501c3644df5148c5b208eed0517`（HEAD；工作树仅 Gate 草案 + active plan delta）
性质：独立、只读、代码级。未修改 Gate/active plan/production；未运行 Maven/package；未调用 provider；未创建 workstream。九字段解析用 `scripts/lib/verification-gate.sh` 实测执行。

---

## 1. Frozen Baseline 独立复算（全部吻合，除 P1-1 所述 SHA 清单措辞）

| 声明 | 独立复算 | 结果 |
|---|---|---|
| Dual planning baseline `e7939f1d` | `git rev-parse HEAD` = `e7939f1d…0517` | ✔ |
| `0c77ce0c` / `d5a36483` / `1db62088` 均为 ancestor | `git merge-base --is-ancestor` 三者 YES | ✔ |
| workflow @5 / SHA `bcf1763a…` / 31/51/9/0 | 重算 `shasum` + 计数一致（`agent-lifecycle.terminal-delivery.json`） | ✔ |
| target HEAD `7c7e35e8…` / tree `bab065a1…` / requirement SHA `42078b01…` / 恰 2 tracked files | `git rev-parse`+`ls-files`+`shasum` 全部一致，target clean | ✔ |
| v15 状态 | worktree 存在（target repo worktree，branch `ai/ws-20260817T141747Z-HUAX`）；ledger 完整在盘；archive 父目录已有 v14 先例 | ✔ |
| 成熟度表述 | prompt projection `VALIDATED / NO IMPLEMENTATION`、strict rejection+单次 correction `ENFORCED`（v14/v15 双形态 typed 证据）、provider compliance `NOT PROVEN`、三个 checkpoint `ENFORCED / NOT PROVEN`——与 adequacy review 及历史 result 一致，诚实 | ✔ |

## 2. Findings

### P1

**P1-1：v15 归档与 SHA 对账的两处缺口（文字级修复，必须先于提交）。**
(a) **SHA 清单与 result 事实不符**：Gate `:25` 说"复算 v15 result 记录的 initial/correction/run/stream SHA"——但 v15 result（`20260817-…-v15-result.md:21-22`）**只记录两个 SHA**（occurrence 1/2 candidate：`a21143f6…`、`b520d585…`）；run.json 与 wire stream 的 SHA 从未被记录（本轮我现算：run.json `18f1f15d…`、stream `69212068…`）。措辞应改为"复算 result 已记录的两个 occurrence SHA，并在对账时现算并登记 run.json 与 scoped stream 的 SHA"。
(b) **wire stream / scoped session records 在 ledger 外且不在归档范围内**：authoritative provider raw fact 位于 `~/.acpx/sessions/eeac7643-….stream.ndjson`（及 `01a01019-…`、`381aa06f-…` 两个 HUAX-scoped record），**不在** `ws-20260817T141747Z-HUAX/.agent/workstreams/…` ledger 内。Preflight 步骤 2 只复制 workstream ledger；Gate `:26` 的"不得删除 wire stream"是禁令而非保存程序——后续任何 session 清理/reap 都可能销毁 revisit trigger 依赖的原始 wire 证据。修复（纯文字）：步骤 2 增加一步——把 scoped session record + `.stream.ndjson`（至少失败 run 的 stream）复制进 archive 并登记 SHA；或在 Observation Discipline 声明这些 stream 的保留义务与记录其 SHA。

### P2

1. **`verification_gate_field_value` 只取字段首物理行**（`verification-gate.sh:5-15` awk `exit`）：v16 各字段值为多行段落时，续行内容（如 Run artifact freshness 第二行的"不得 `--skip-build`"）对 parser/tradeoff 检查不可见。本 Gate 九字段均正确解析、无 flag（**已实测**：missing=[] flags=[]），v15 同形态亦通过——预置库限制，非本 Gate 缺陷；记录为后续脚本卫生项（tradeoff 词出现在续行时会被漏检）。
2. **"90 分钟 active runtime"为 operator 执行约束**：无 production 计时器度量"扣除 WAITING_FOR_HUMAN 的活跃时长"；可由 ledger started/finished 时间戳人工推导，可执行，但建议注明"由 ledger 时间戳推导"以免被当作自动机制。
3. **观察项措辞已足够防误读**：`ROOT_CLOSURE_REVISIT_TRIGGERED` 经全仓 grep **不是任何 production schema/enum 字段**（dual-runtime/acpx/ledger/cli/core 零命中）；Observation Discipline `:110-114` 明确"判定只用于后续设计，不进入本轮 workflow 控制"、未触发记 `NOT_TRIGGERED` 且不得据此宣称 compliance `PROVEN`——不会与 production typed fact 混淆。

### P0

无。

## 3. 二十项逐问回答

1. **九字段解析**：✔ 实测通过（missing=[] flags=[]）；P2-1 的首行限制记录在案。
2. **target 事实**：✔ 全部复算一致。
3. **workflow 事实**：✔ @5 / `bcf1763a` / 31/51/9/0。
4. **checkpoints ancestry + 成熟度**：✔ 三者均为 HEAD ancestor；成熟度表述与全部前序文档一致、诚实（compliance 明确 `NOT PROVEN`）。
5. **v15 四 SHA 可执行性**：⚠ 部分——两个 occurrence SHA 可对账；run/stream SHA 需现算（P1-1a）；worktree/ledger/branch/sessions 均真实存在，归档可执行。
6. **双 manifest 完整性 + ledger 外 stream**：双 manifest（同规格、排序、逐行一致）足以证明 ledger 复制完整（较 v15 Gate 已补 source 侧 manifest）；**stream 在 ledger 外，Gate 未给保存程序**（P1-1b）。
7. **删除顺序 fail-closed**：✔ 对账失败不得删 source；对账 PASS→archive 只读→sessions closed 核对→保存 worktree list→删 worktree；明确不删 branch/archive/wire stream/result。顺序无窗口误删 archive（独立目录）。唯一缺口是 stream 未进 archive（P1-1b）。
8. **probe vs compliance**：✔ 准确——Target `:11-12`、INVALID `:39-40`（"probe PASS 只证明可调用…不能把 strict failure 改判 INVALID"）、Preflight `:85`（"不携带业务 contract，不预测 JSON compliance"）三处一致；无相反文字。
9. **root-only EOF 纯观测**：✔ FAIL 使本轮失败 + 观察标签；不参与 routing/PASS/恢复；非 production 字段（P2-3 证据）。
10. **标签误读风险**：✔ 低——命名带 `_TRIGGERED`、限定"仅当 Jackson EOF-at-EOF + 离线结构检查 depth=1"、且明确排除 fence/prose/多文档/depth>1（`:113`）；文案已足够。
11. **strict failure 分类**：✔ FAIL（产品/边界失败）与 INVALID（identity/诱导/缺证据）互斥；probe PASS 不改判 INVALID 的澄清正确。
12. **stop-loss 时长口径**：✔ 区分 active runtime 与 `WAITING_FOR_HUMAN`；但为 operator 执行（P2-2）。
13. **human UI/CLI**：✔ 与 production 一致——`review decide --action --note --workdir <ws> <id>` 逐字匹配 `ReviewDecideCommand.java:23-33`；`<packet-allowed-action>` 对应 `RoutingEngine.resolveOpenGate:342` 的 allowedActions 校验；UI 经 `WorkstreamActionService:155` 同一 boundary。
14. **CLI 卫生**：✔ fresh JAR 绝对路径、禁 PATH `dualctl`、显式禁 `--run`（选项存在且默认关，`ReviewDecideCommand.java:29-30`）。
15. **是否伪称 preflight 保证 compliance**：否——多处反向声明（见第 8 项）。
16. **人为诱导**：否——INVALID 明确禁止诱导 revision/verdict/JSON failure；条件子结论均"自然发生"。
17. **隐含授权**：否——FAIL 明确"不得自动补 `}`"；Maturity `:121` "revisit trigger 自然出现只授权重新做设计评审，不自动授权代码修改"；无 retry/resume/第二样本/parser fallback 措辞。
18. **PASS=产品完成**：✔ 要求 acceptance typed 结论 + PostgreSQL verification + `mvn test` + README + scope + lineage + completion audit + final diff——产品级，非仅 workflow COMPLETED。
19. **不可执行/矛盾条件**：无（90 分钟口径见 P2-2；SHA 措辞见 P1-1a）。
20. **新增能力**：无——纯一次性 verification procedure；复用既有 scripts/handlers；`ROOT_CLOSURE_REVISIT_TRIGGERED` 是 Gate 词汇表而非 production schema。

## 4. End-to-end invariant（五条边界）

| 边界 | Declared by | Proven by | Consumed by | Enforced before | Failure route | Cannot-bypass | Residual raw path |
|---|---|---|---|---|---|---|---|
| **产品完成** | target `REQUIREMENT.md` + terminal acceptance sidecar + completion audit | PostgreSQL verification、`mvn test`、final diff | Gate PASS 判定 | PASS 前所有产品证据齐备 | 任一失败 → FAIL | acceptance/scope/lineage/completion typed tests + 本 Gate 清单 | target repo 自身 |
| **provider execution / strict output** | ACP wire stream 终消息 | run.json occurrence raw/candidate SHA + process facts | strict parser → validator → merger | sidecar/reference/routing 前 | typed parse failure → 单次 correction → 仍败 → `OUTPUT_INCOMPLETE/MANUAL_ONLY`（`AgentExecutionOutcomeResolver`） | parser（dup-key/trailing/unknown/EOF）+ `normalizationCannotBypass…` 测试族 | transcript/acpx record history 仅为调查材料，非控制事实 |
| **human decision** | human gate action ledger（human_action+note） | canonical `resolveOpenGate` 校验 | successor/iteration 创建 | plan/revision successor 前 | 非法 action/空 note revise 拒绝 | gate resolution tests + autorun 三态停测试 | UI/API/CLI/autorun 四入口单 boundary |
| **v15 evidence archive** | source/archive 双 manifest + SHA 对账（含 stream——待 P1-1 修复） | 逐行一致的排序 manifest + 复算 SHA | 未来 root-closure 重评 | worktree 删除前 | 对账失败 → INVALID 且不得删 source | 双 manifest + fail-closed 顺序 | branch/archive/result 保留 |
| **root-only EOF 观察** | `ROOT_CLOSURE_REVISIT_TRIGGERED`（Gate 词汇，非 schema） | Jackson EOF-at-EOF + 离线 depth=1 结构检查 + 原始 wire/candidate 保留 | 仅后续设计评审授权 | 不进入本轮 workflow 控制 | 复现 → 本轮 FAIL + 证据保存 | 观察条件精确排除其他形态 | 未触发记 `NOT_TRIGGERED`，不得宣称 compliance PROVEN |

## 5. Gate 可执行性

可执行。Preflight 8 步均有明确输入输出与 fail-closed 出口；归档沿用 v14/v15 已实践的 manifest 模式；PG 细节完整；双 probe 与 workstream 分离授权清晰；`verification-gate.sh` 实测通过。

## 6. archive/cleanup 顺序安全性

顺序 fail-closed 正确（对账→只读→sessions→worktree list→删 worktree；任何不符不删 source）。两处补强即闭合：stream/session record 纳入归档（P1-1b）；SHA 措辞对齐 result 事实（P1-1a）。

## 7. provider probe 与 runtime compliance 边界

准确且贯穿一致（Target/INVALID/Preflight 三处）。probes 不携带业务 contract、不预测 compliance；strict failure 属 FAIL 而非 INVALID——与 "parser fail-closed ≠ provider 问题已解决" 的成熟度口径一致。

## 8. UI/CLI human interaction 可执行性

与 production 逐字一致（`ReviewDecideCommand:23-33`；`resolveOpenGate` allowedActions/note 强制；`--run` 显式禁用；fresh JAR 绝对路径）。autorun 禁令与 `d5a36483` 的 `HumanGateSemantics.requiresOperatorDecision` 行为一致。

## 9-11. 结论

| 项 | 结论 |
|---|---|
| **只提交 Gate + active plan** | **P1-1 两处文字修复后 YES**（工作树恰为两份文档；active plan delta 与 Gate/代码事实一致） |
| **提交后授权边界** | 仅 **fresh package + non-provider preflight**（步骤 2-5：归档对账/清理、package、临时 evidence、PG 实例）；**provider probes（步骤 6）与 fresh workstream（步骤 7）各自需单独授权**——Gate 与 active plan 均如此声明 |
| **Boundary Review** | **WARN**（P1-1 为文字级缺口，不修复则会误导执行与未来重评的证据链；其余全部核实通过） |
| **Implementation/Execution READY** | 修复 P1-1 后 **READY**（纯 Gate 文档 delta，无 production 改动） |
| **当前成熟度** | captcha-auth `NOT ACHIEVED`；uniqueness/human-authority/ACP-turn `ENFORCED / NOT PROVEN`；prompt projection `VALIDATED / NO IMPLEMENTATION`；strict rejection+单次 correction `ENFORCED`；provider compliance `NOT PROVEN` |
| **下一步唯一允许动作** | 按 P1-1 修订 Gate 两处文字（SHA 清单措辞 + stream/session 归档步骤），随后选择性提交 Gate + active plan；不授权 package/probe/workstream |
