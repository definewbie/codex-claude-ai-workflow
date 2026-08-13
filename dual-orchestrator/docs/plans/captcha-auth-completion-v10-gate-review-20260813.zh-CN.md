# Captcha-Auth Completion v10 Verification Gate — 独立只读同行评审

日期：2026-08-13
审查对象：`docs/testing/verification-gates/20260813-terminal-delivery-captcha-auth-completion-v10.md`（未提交）
对照：active plan（未提交 diff）、checkpoint `c753cfceac3b28799d6524d3f261492b947b4802`、v9 Gate、ScopeOperation plan/diff review、真实 production 代码与 workflow JSON。
方法：只读核对 Gate 每一项条件能否由 production artifact / ledger / CLI / runner 实际判定。未修改/提交文件，未运行 provider/probe/real-case。

## 结论摘要

| 项 | 判定 |
|---|---|
| P0 | 0 |
| P1 | 0 |
| P2 | 2（均非阻塞） |
| **Boundary Review** | **PASS** |
| Gate 可执行性 | **YES** |
| 只提交两份文档 | **满足** |
| 提交后授权边界 | fresh package + non-provider preflight **可授权**；provider probes / workstream 仍需单独授权 |

---

## 1. Findings（P0 / P1 / P2，附真实 file:line）

### P0 — 无

### P1 — 无

### P2-1（Human Interaction 的 `--action <approve|revise|reject>` 是示意集合，非各 gate 的精确 allowed set）

- **位置**：`20260813-terminal-delivery-captcha-auth-completion-v10.md:102`（命令示例）。
- **现象**：命令语法与 `ReviewDecideCommand` 完全匹配（`--action` required、`--note` optional、`--workdir` optional、positional `<workstream-id>`，`ReviewDecideCommand.java:19/22/25/34`），且 `review decide` 子命令存在于 `ReviewCommand.java:13`。但 `<approve|revise|reject>` 是示意：`reject` 仅在 `gate.contract_review` 的 `allowed_actions` 中合法（`agent-lifecycle.terminal-delivery.json` 该 gate config 为 `["approve","clarify","reduce_scope","reject"]`），且该 gate 还有 `clarify`/`reduce_scope` 未列出；`gate.human_implementation_repair` 仅允许 `revise`；`approve` 在 `gate.human_requirement_review`/`gate.contract_review` 会被 `canonicalGateAction` 规范化为 `approved`（`RoutingEngine.java:359-371`）。
- **为何非阻塞**：Gate 已明确要求 operator「核对 review packet 的 allowed actions」（`:97`），而 `allowedActions` 字段（`HumanReviewGateHandler.java:56-61` / `LedgerUtils.java:32-40`）才是权威集合；命令边界与参数本身正确。建议把示例改为「按 packet 中 `allowed_actions` 选一个 action」以消除歧义。

### P2-2（「proposal 生成时 effective worktree」的可执行证据是间接重建的，非独立固化的 filesystem 快照）

- **位置**：`20260813-terminal-delivery-captcha-auth-completion-v10.md:36-37`（PASS 条件）与 `:62`（Early Boundary 第 2 项）。
- **现象**：runtime 上 resolver 与 durable validator 在同一同步调用内读 effective worktree（已在 diff review 确认），fail-closed 保证 sidecar operation 与生成时 filesystem 一致。但 Gate review 时点 worktree 已不复存在，可执行的复现证据是：frozen target tree `bab065a1`（tracked 仅 `REQUIREMENT.md` + `VERIFICATION-GATE.md`）+ intake deny-all/no-prior-step 不变量 → effective worktree 在 intake 时点即 target tree 内容。因此「existing→MODIFY / absent→CREATE」可按 sidecar durable operation vs target tree 逐项判定，证据链成立，但「proposal 生成时 effective worktree 存在性」措辞稍显 ideal（暗示快照），实际事实来源是 frozen target tree。
- **为何非阻塞**：判定可执行且诚实，target tree 已 hash 固化。建议在 Early Boundary 第 3 项注明「effective worktree 存在性以 frozen target tree 为事实来源」，使复现路径显式。

---

## 2. 14 项核实 + 事实基线

### 1. 唯一主目标是完整交付 captcha-auth — **确认**
Gate `:7` 明确「唯一主目标是完成 captcha-auth 产品」；`:8` ScopeOperation 是「进入 reviewer 前必须满足的早期边界，不是独立 benchmark」，不授权额外 Agent 调用/样本。没有退化为专项 benchmark。

### 2. ScopeOperation 只是首个 reviewer 前的 early boundary，不增加调用/样本 — **确认**
`:` 58 明确「只决定是否允许进入第一个 product reviewer，不替代产品 PASS」；Preflight 顺序 `:116` 只有「首个 intake sidecar/reference 通过后才允许 product reviewer 继续」，无新增 Agent 节点/调用/第二个样本。

### 3. initial Agent shape 只允许 path；旧 `operation` 被 strict parser 拒绝 — **确认**
`AgentResultEnvelopeParser.java:22` `.enable(FAIL_ON_UNKNOWN_PROPERTIES)` + `:18` STRICT_DUPLICATE_DETECTION + `:23` FAIL_ON_TRAILING_TOKENS。`ProposalScopePath(String path)` 仅 path（`AgentResultEnvelope.java:66`）。测试 `intakeOldScopeOperationShapeIsRejectedAsUnknownInput` 注入 `operation` 断言 parse 失败。

### 4. SoT 链准确 — **确认**
`ArtifactContractValidator.validateAgentOutput`：parser（`:92`）→ envelope validator（`:99`）→ resolver（`:161`）→ durable projector（`:169`）→ durable proposal validator（`:195`）→ sidecar + `TaskContractProposalReference` 发布（`:272-292`）。首个 product reviewer `intake.codex_product_review` 配 `review_subject_artifact=task_contract_proposal`，经 `TaskContractProposalReference.resolveFromRefs` 消费 validated reference，缺失则 `REVIEW_SUBJECT_MISSING` fail-closed（`ArtifactContractValidator.java:243-259`）。Gate `:70` 表述准确。

### 5. existing=MODIFY / absent=CREATE 可从 sidecar + baseline 判定 — **确认（见 P2-2 证据来源说明）**
resolver 派生规则 `Files.exists→MODIFY / absent→CREATE`（`TaskContractScopeResolver.java:52-54`）；sidecar durable `scope.included` 含 `{path, operation}`；target tree `bab065a1` tracked 仅 baseline 文件 → `README.md`/`pom.xml`/源码路径 absent→CREATE、`REQUIREMENT.md` present→MODIFY 可逐项判定。

### 6. 不强制模型选特定路径 — **确认**
Gate `:64-65` 明确「Gate 不要求模型必须选择这些具体路径，只对它实际声明的路径逐项验证」。语义正确，无过度约束。

### 7. 错误码职责拆分准确，无下游按 code 分支 — **确认**
全仓 grep：`TASK_CONTRACT_SCOPE_RESOLUTION_FAILED` 仅 `ArtifactContractValidator.java:165`；`TASK_CONTRACT_PROPOSAL_VALIDATION_FAILED` 仅 `:198`。无 recovery/classification/arbiter/routing 按 code 字符串分支（recovery 由 node `FAILED` 状态触发）。Gate `:67-68` 表述准确。

### 8. PASS/FAIL/INVALID/Stop-loss 互斥且可执行 — **确认**
PASS（`COMPLETED`+acceptance+全检查+scope 一致+reviewer 消费 validated ref）；FAIL（operation 字段残留/resolution-validator 失败/CREATE-MODIFY 不符/reviewer 绕过/产品任一失败）；INVALID（dirty/identity-hash-workflow-JAR-probe 不符/复用旧 session/运行中修改/无法证明 clean SHA）；Stop-loss（provider 前停 on freshness 不符；reviewer 前停 on sidecar/operation/reference 失败）。early boundary 失败时 `:48` 明确在 product reviewer 调用前停止，阻断后续 Agent 消耗。

### 9. target HEAD/tree/requirement hash、workflow source hash/shape、checkpoint SHA 与仓库事实一致 — **确认**
实测：
- target HEAD `7c7e35e89a66b14c29a335958ad90c8a92779fa0` ✓（Gate `:87`）
- target tree `bab065a1ff9fe4d7032d1d573b7d1b6c1181a574` ✓（`:88`）
- requirement sha256 `42078b01...31f0` ✓（`:89`）
- target tracked 仅 `REQUIREMENT.md` + `VERIFICATION-GATE.md`，clean ✓（`:26-27`）
- workflow source sha256 `0984ee51...ec44d`、nodes 31 / edges 52 / acpx 9 / handoff 0 ✓（`:81-85`，实测逐一吻合）
- checkpoint `c753cfceac3b28799d6524d3f261492b947b4802` = 当前 HEAD ✓（`:23`）

### 10. fresh-JAR CLI 命令参数匹配；UI/CLI 同一 ledger command boundary — **确认（见 P2-1 集合歧义）**
`ReviewDecideCommand` 参数 `--action`(required)/`--note`/`--workdir` + positional workstream-id 与 Gate 命令匹配；`ReviewCommand` 含 `decide` 子命令；artifact `dual-cli-0.1.0-SNAPSHOT.jar`（`dual-cli/pom.xml:10/13`）名称匹配。最终经 `RoutingEngine.resolveOpenGate`（`WorkflowFileLedger` 转发）同一 ledger boundary，UI/CLI 同源。

### 11. fresh package → PostgreSQL → 双 probes → fresh workstream 顺序正确，诚实区分未授权 — **确认**
Preflight 顺序 `:111-118` 正确；状态头 `:5` 明确 `PREFLIGHT NOT AUTHORIZED / REAL-CASE NOT AUTHORIZED`；active plan 同诚实声明。

### 12. 无平台扩张 — **确认**
Gate `:126` 与 checkpoint `c753cfc`（仅新增 package-private resolver + 类型收窄）均无 runner monitor / retry / inventory / schema fallback / module / registry / engine。

### 13. 可仅提交 v10 Gate + active plan — **确认**
working tree 仅两处未提交：v10 Gate（untracked）+ active plan（modified）；checkpoint `c753cfc` 已提交。提交两份文档即得 clean execution SHA（`c753cfc` 的后代）。

### 14. 成熟度声明诚实 — **确认**
ScopeOperation `c753cfc` `ENFORCED / NOT PROVEN`（deterministic `55/41/1223` + design/diff review PASS，无 fresh real-case）；self-test `b2306882` `ENFORCED / NOT PROVEN`（v9 未自然触发）；captcha-auth `NOT ACHIEVED`。均诚实，无提前 `PROVEN`。

---

## 3. End-to-end invariant（ScopeOperation fact projection，7 维）

| 维度 | 证据 |
|---|---|
| **Declared by** | Gate PASS 条件 + ScopeOperation Early Boundary 1-5 项（`:36-37`、`:60-70`）；`ProposalScopePath(String path)` 唯一 producer `TaskContractScopeResolver` |
| **Proven by** | v10 real-case 尚未运行（`NOT PROVEN`）；deterministic 由 committed tests（focused `55/0/0/0`、ACP `41/0/0/0`、reactor `1223/0/0/0`）+ 独立 diff review PASS 背书 |
| **Consumed by** | durable projector（`ArtifactContractValidator:169`）、durable proposal validator（`:195`）、sidecar/reference（`:272-292`）、reviewer `intake.codex_product_review`（`review_subject_artifact=task_contract_proposal`，`resolveFromRefs`） |
| **Enforced before** | resolver + durable validator 在 sidecar 发布与 reviewer dispatch **之前**同步执行（`ArtifactContractValidator:161-201`） |
| **Failure route** | resolution 失败 → `TASK_CONTRACT_SCOPE_RESOLUTION_FAILED`（`:165`）；durable 篡改 → `TASK_CONTRACT_PROPOSAL_VALIDATION_FAILED`（`:198`）；reviewer 缺 ref → `REVIEW_SUBJECT_MISSING`（`:256`）；均 fail-closed 写 diagnostic |
| **Cannot-bypass evidence** | strict parser unknown-field 拒绝（`AgentResultEnvelopeParser:22`）；tamper 测试 `durableValidatorStillRejectsTamperedResolvedOperation`；sibling sweep（生产 `ScopeEntry` producer 仅 resolver + durable 反序列化） |
| **Residual raw path** | 无：旧 `taskContractProposalMap(ProposalResult)` 已删，projector 在 `resolvedProposal==null` 抛 `IllegalStateException`（`AgentResultDocumentProjector:50-52`） |

---

## 4. 提交后授权边界（事实性说明）

- **提交两份文档 → clean execution SHA**：可立即获得（`c753cfc` 后代，仅 docs）。
- **fresh package + non-provider preflight**（`Preflight :112-113`，JAR hash/mtime、workflow hash/shape、Dual/target identity、PostgreSQL 启动与 `pg_isready`/`SELECT 1`）：deterministic、无 provider 消耗，可在本 review PASS 并提交后授权。
- **双 provider probes**（`Preflight :114`）与 **fresh workstream 创建**（`Preflight :115`）：涉及 provider 调用与真实样本，仍需在上述 preflight 全部 PASS 后**单独授权**；不得在 preflight PASS 前创建 workstream，也不得创建第二个样本。

---

*本报告为只读同行评审证据；未修改、未提交任何文件。*
