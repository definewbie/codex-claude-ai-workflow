# Review: Repository Fact Occurrence Ownership 与 Boundary Discovery 计划

日期：2026-08-28
评审性质：独立、只读、代码级；未修改 production/test/计划；未调用 provider；未 resume/审批任何 workstream
取证范围：production code（§2.2 全部锚点）、captured `ws-20260827T232026Z-EFCB` durable ledger、
early boundary diagnostic、node-scoped sibling artifacts 实读

## Findings

**P0：无。P1：无。**

### P2-1 §2.1 一处 decision 名不准确
计划写 product review decision 为 `READY_FOR_REQUIREMENT_REVIEW`；ledger 实测为
`READY_FOR_PRODUCT_REVIEW`（`nodes/node-intake-codex_product_review-*.json` result.decision，
亦为 `ContractRegistry.INTAKE_CODEX_PRODUCT_REVIEW` 的实际 enum）。一词修正，不影响任何判定。

### P2-2 §1.4/§2 宜补一句"identity 残留但事实文档不可重建"的精确表述
实测 `memory/file-relevance-plan-node-intake_claude_brainstorm-002.json`（agent-run governance
的 node-scoped plan）仍保留 `trackedInventoryHead=dc0ea5925f06`、`trackedInventorySha256=af1983f98e61`、
`source=git_tracked_inventory+…`——inventory **identity** 可从 sibling 残留部分恢复，但
proposal lineage 无法重建其引用的**事实文档**（路径清单已随 `memory/repo-map.json` 被覆盖为
`files:0`）。补这一句使诊断更精确，也更强地支持"binding 缺失即 fail closed"（残存 identity
不构成 authority）。

### P2-3（知会）binding 的 adapter 侧持久化落点留待 reader sweep
§6.1 已声明"sidecar 已有 machine-owned typed metadata 位置则复用、不得塞 control/自由文本"——
正确的自我约束；实施 sweep 必须给出唯一落点（sidecar metadata vs run.json），避免两处都写。

## 独立取证结论（不复述计划）

**1. Captured 事实全部复算吻合** — EFCB：WAITING_FOR_HUMAN @ `gate.human_requirement_review-006` ✓；
intake `toolCallCount=17 / readCount=17 / writeCount=0 / bashCount=0` ✓（run.json toolUsage）；
review `READY_FOR_PRODUCT_REVIEW / COMPLETED` ✓；runs=2；diagnostic 文件 SHA
`64c139d5…24f8` 逐位一致，classification=`INVALID_EVIDENCE_DURABILITY` ✓。

**2. 根因经实证成立（非推断）** — 当前 `memory/repo-map.json` 实测为
`source=task_contract_scope+git_evidence / files:0 / inventoryHead:""`：initial
contract-discovery 写入的 inventory 已被 product-review context build **无条件覆盖**，而它正是
proposal validation 的权威输入（`TaskContractProposalValidator` 固定读该路径）。
"validator 当时读到正确内容、事后不可审计"的时序描述精确。

**3. §2.2 六个代码锚点全部核实** —
- `RepoScopeGovernance`：contract-discovery capture（`:61-76` 区，前轮 diff 已核）+ repo-map/
  working-set/file-summaries/read-reuse/workstream-memory 共享全局路径（memory/ 目录实listing：
  plan/policy/catalog 带 node key，其余无 key）✓；
- `TaskContractProposalValidator:72-132` 固定 `memory/repo-map.json` + re-capture ✓（实施 diff
  已核）；
- `ContextGovernanceActualReportWriter:67-80`：plan/policy/catalog 用 mtime 降序**目录扫描**
  `latestDocument` + catalog 空时回落**全局** `agent-capability-catalog.json` + repo-map 固定
  全局读 ✓（`:174-192` lastModified 选择器实读）；
- `AgentRunGovernanceContext` PreparedContext 仅携带 contextRef + qualified contract，无
  governance artifact identity ✓；
- `ArtifactContractValidator` 拥有 run/node/result 边界但 traceability 未接收 occurrence
  binding ✓。

**4. occurrence binding 最小且完整，无 global/latest/fallback 残留路径** —
- §5 七项拒绝逐一有据：#3 的实证基础我在 EFCB 直接观察——agent-run governance 的 plan 是
  **node-keyed 非 run-keyed**（`-002`），同 node retry 必覆盖；#4 的 mtime 猜测就是
  ActualReportWriter 现行 production 代码；
- binding = occurrence-key（node instance + run id）immutable path + refs/SHA manifest +
  消费前 SHA 重验——这不是新平台而是把"哪些字节构成本次事实"formalize；context-only build 用
  builder node identity 处理无 run 情形，sound；
- §4.3 删除 latest authority 与固定全局读、§4.4 删除无消费者全局文件且禁兼容镜像、旧
  workstream 显式 `not_configured`——无静默回退；
- §12 保留更小方案（actual report 内嵌）的退出条件且要求证明全消费方同字节身份——自我设限
  正确。**更小方案评估**：仅 occurrence-scoped 路径 + sidecar 记 ref 可行，但 actual report
  还需 plan/policy/catalog 的 identity——binding 的打包恰是最小闭包；无被遗漏的更小等价方案。

**5. §3 sibling 分类正确** — 与 memory/ 实listing 逐项吻合；"共享路径本身不是错误，
workstream-memory 的累计语义 vs repo-map 的 occurrence 不可变语义必须分治"是本次诊断的核心
洞见，分类表如实反映。

**6. 四个消费方同源可行** — §4.2 链（binding → PreparedContext → run metadata → validator →
sidecar → TaskContractProposalReference → reviewer/compile/audit）与已核实的代码形状吻合：
validator 已有 re-capture、sidecar 已有 machine-owned 区、proposal reference 已 hash sidecar、
audit 已沿 lineage——binding 是把它们接到同一 identity 上的最小粘合。

**7. sequence tests 与 sentinel 提升发现率且不形成新平台** — §7 十项全部为 temporal
sequence（sibling 写后重解析、retry 双 binding、篡改/删除 fail-closed、冷 JVM 重建）——直接
针对"局部 UT 全绿仍失败"的本次缺陷类别；§8 L1-L5 有明确止损（§12：连续两个 checkpoint 无
新发现即停止扩张、L4 限定高风险 authority、不建通用 framework）；§9 Gate 附表是文档纪律非
production schema。**风险知会**：§8 是迄今最大的流程增量，其价值依赖 §12 止损被执行——评审
将此列为后续 checkpoint 的核对项。

**8. 遗漏扫描** — repo-map 的 writer 仅 `RepoScopeGovernance`（前轮 sweep）；reader =
validator（固定路径）、ActualReportWriter（固定路径）、prompt guidance（projection refs）、
WorkstreamMemoryProjection（refs）——全部被 §6.1 变更面覆盖；无遗漏 writer/reader。兼容风险
（旧 frozen workstream）由 not_configured 投影处理。第二 SoT 风险（binding 与被引 artifact
漂移）由消费前 SHA 重验消解。

## End-to-end invariant（目标模型，评审后确认成立）

| 维度 | 结论 |
|---|---|
| Declared by | occurrence-scoped immutable binding（node instance + run id 键，无 mtime/目录扫描） |
| Proven by | binding 内 refs + SHA + Git identity，消费前重验；sibling 写入不得触碰 immutable 路径 |
| Consumed by | validator、actual report、sidecar lineage、final audit 四方消费同一 binding identity |
| Enforced before | sidecar publication（validator binding 校验）与 business write（compile 链）之前 |
| Failure route | binding 缺失/ref 越界/SHA 不符/inventory drift → fail closed（禁回退 global/latest）；旧 workstream → 显式 not_configured |
| Cannot-bypass | §7 十项 sequence tests（含删除 binding 必败、sibling 写后字节不变、retry 双 binding、冷 JVM 重建） |
| Residual | binding 持久化落点待 sweep 定夺（P2-3）；§8 流程增量的价值依赖 §12 止损；单 binding 不承诺发现所有 lifecycle 缺陷 |

## 判定

- **Boundary Review: PASS**
- **Implementation READY: NO**——按计划自身顺序，先完成 production reader/writer/sibling
  sweep 锁定 binding 落点（§11.2），再实施单一原子 checkpoint。诊断与设计已具备实施前提，
  sweep 是收尾而非重开。
- **最小文件范围**（与 §6.1 一致，评审确认无遗漏）：`RepoScopeGovernance`、
  `AgentRunGovernanceContext`、`AcpxAgentTaskHandler`（binding 传递）、
  `ContextGovernanceActualReportWriter`、`ArtifactContractValidator`、
  `TaskContractProposalValidator`、`AgentResultDocumentProjector`/`AgentOutputContractDocument`
  （additive binding）、final audit lineage reader、`WorkstreamMemoryProjection`/context renderer
  （projection 语义）+ 对应 sequence tests。零 workflow/schema/provider/product 变更。
- **成熟度分层（§2.3）核实为准确**：fact acquisition `ENFORCED`、real-case 执行 `VALIDATED`、
  occurrence durability `NOT MODELED`、audit 重建 `NOT ENFORCED`；EFCB 判
  `INVALID_EVIDENCE_DURABILITY`（非 FAIL 非 PASS）正确——identity/freshness/执行均成立，
  破坏的是可审计性。
- **反证测试（本评审要求的最小项）**：删除 occurrence binding 文件后，即使全局 repo-map 内容
  完整，validator/audit 必须失败（§7.6）——这是区分"binding authority"与"global 残留"的
  决定性断言。
