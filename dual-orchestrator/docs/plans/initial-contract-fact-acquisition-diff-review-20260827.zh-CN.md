# Diff Review: Initial Contract Fact Acquisition Checkpoint

日期：2026-08-28
评审性质：独立、只读、代码级；未修改文件；未调用 provider；未运行 real-case
基线：HEAD `e9d99320`；`git diff --check` clean（本评审独立复验）

## Findings

**P0：无。P1：无。**

### P2-1（知会）validator 内的第二次 `git` 子进程
`TaskContractProposalValidator.validateEvidenceTraceability` 在校验时重新执行
`GitTrackedPathInventory.capture(workspaceRoot)` 做身份比对——每次 proposal 校验多一次 git
ls-files 子进程。大仓库下为毫秒-秒级、纯只读；正确性必要（stale identity 检测）。仅记录，无行动。

### P2-2（知会）plan §8 test 8 的"删除 inventory 必须失败"以间接形式满足
cold-start 测试断言 non-empty inventory + `initialEvidenceMustReferenceTrackedInventory`
断言缺 repo-map/缺 traced fact 即拒绝——删除 inventory 后两者必失败。反证覆盖成立，只是没有
单一以"删除"命名的测试。无行动。

## 逐项核实（控制流自行追踪）

**1. Inventory 只拥有路径存在性 ✓（未成为第二 scope owner）**
`GitTrackedPathInventory.java:14` javadoc 明示 "does not infer task relevance"；`:22-37`
capture = `rev-parse --show-toplevel` 根校验（fail-closed）+ HEAD + `ls-files -z --cached -- . :!.agent`
+ canonical 过滤 + 排序 + SHA-256——纯快照，无任何相关性判断。repo-map/plan 只投影
identity（head/sha/count）与路径（`RepoScopeGovernance` diff：`source=git_tracked_inventory`、
`trackedInventoryHead/Sha256/PathCount`）；scope 选择仍归 proposal（prompt 明示 "Evidence
references support scope reasoning but never authorize edits"；validator 只要求可追溯、不生成
scope）。

**2. 事实链闭合且不可绕过 ✓**
- **Declared**：contract-discovery stage policy（profile + prompt）声明只读发现边界；
- **Proven**：context build 时 inventory identity 落入 durable repo-map；
  `ContextGovernanceActualReportWriter` 新增 `actualReadEvidence`（path+sha256+size，含
  root-escape 防护）持久化实际读取事实；
- **Enforced before compile**：`ArtifactContractValidator` 在 revision 分支**之前**调用
  `validateEvidenceTraceability`（diff `:208+`）——缺失 repo-map、inventory 身份漂移
  （re-capture 比对 head/sha/count）、evidence_refs 无任何 inventory 事实、引用不存在文件、
  非规范路径 → typed `TASK_CONTRACT_EVIDENCE_TRACEABILITY_INVALID`，compile 与 business
  write 不可达；
- **Revision 同规**：接线位置在 revision 判断之前 → revision proposal 同样必须 grounding
  （plan §5C.6 "revision 保留同一事实获取能力"落实）；
- **既有边界不放宽**：revision delta/patch 校验、reviewer、change-scope 全部原样。
- Cannot-bypass 测试实证：`initialEvidenceMustReferenceTrackedInventory`、
  `rejectsTrackedInventoryThatChangedAfterProjection`（stale）、
  `rejectsEscapingAndInternalPathsBeforeWorkspaceEvidenceChecks`（forged/escaping）、
  `coldStartProjectsTrackedInventoryWithoutSelectingFinalScope`。

**3. 四层一致性 ✓**
- profile：`contractDiscovery()` 改为 `denyAll=false` + `[Read,Grep,Glob]` +
  kinds `[read,search]` + terminal disabled——**不再依赖 deny-all×policy 优先级组合**（注释
  如实更新；前轮 P2-2 以构造方式消解）；
- catalog：删除 intake 专属 direct-read 禁止（`allowsDirectFileRead` 移除），
  `contract_discovery → CapabilityPermission(true,false)`；shell 仍禁；
- prompt：改为 targeted Read/Grep/Glob discovery + 禁 terminal/Bash/execute/write/edit +
  evidence 要求 + 禁止仅凭文件名/需求文本返回 READY；
- adapter：测试断言 `--deny-all` **缺席**、`--allowed-tools Read,Grep,Glob`、`--no-terminal`；
- 四个 sibling：`contractDiscovery(context)` 以 `permission_profile` 为键（非 node id）——
  full/contract/terminal-delivery/external small 的 intake 节点自动一致；custom/frozen
  definition 使用同 profile 亦一致；**零 workflow JSON 变更**（git status 证实）。

**4. evidence 要求正确且兼容 ✓**
要求仅在 inventory 非空时生效；既有 durable evidence refs 保留（"additional durable evidence
references remain allowed"）；revision 同规不降级。

**5. 覆盖充分 ✓**
cold start（无 contract/diff → non-empty inventory）；large-repo（`MAX_SCOPE_FILES` 仍界
readCandidates 投影、prompt 只投 identity、全量路径在 durable repo-map）；stale identity
（re-capture 比对）；sibling（profile 键控四 workflow 一致；非 contract-discovery 节点行为
原样——source 字符串与 empty inventory 保持）。

**6. 无多余机制 ✓**
无 schema/workflow/JSON 变更；无 retry/fallback；无 Mailpit 特判；新类为 package-private
record（非 module/framework）；变更面 7 个 production 文件与计划 §6 清单一致
（ArtifactContractValidator 为证据接线的必要新增）。

**7. 测试证明行为边界 ✓**
断言对象是 adapter 参数集合、durable 文档字段、typed 拒绝原因、identity 漂移检测——非实现
细节；反证（P2-2）间接成立。

## 独立复跑

- focused（9 类）：runtime 64/0/0/0 + adapter 116/0/0/0 = **180/0/0/0**；
- full reactor：**BUILD SUCCESS**，surefire XML 独立聚合（147 files）= **1246/0/0/0**——与
  声明精确一致（上轮 1236 + 10 新测试）；
- `git diff --check` clean。

## End-to-end invariant

| 维度 | 证据 |
|---|---|
| Declared by | contract-discovery profile/prompt 声明只读发现边界（`AcpxPermissionProfile`/intake prompt） |
| Proven by | durable repo-map 的 inventory head/sha/count + `actualReadEvidence`（path+SHA） |
| Consumed by | proposal `evidence_refs` ≥1 个 inventory 内现存文件（`initialEvidenceMustReferenceTrackedInventory`） |
| Enforced before | reviewer 接受、contract compile、business write（`ArtifactContractValidator:208+` typed 拒绝，先于 revision 分支） |
| Failure route | 缺 repo-map/stale/forged/无 traced fact → `TASK_CONTRACT_EVIDENCE_TRACEABILITY_INVALID`；机械违规 → 既有 typed execution failure |
| Cannot-bypass test | cold-start/stale/escaping/no-trace 四类 + adapter 参数一致性 + catalog 测试 |
| Residual | Agent 仍负责业务 relevance；reviewer/human 仍可合法 revision（§7 Residual 保留）；validator 二次 git 子进程（P2-1） |

## 判定

- **Boundary Review: PASS**
- **选择性提交条件：满足** —— 建议 16 modified + 2 untracked（plan + 新测试）作为单一原子
  checkpoint 提交（含本评审文件）。
- **成熟度：`FACT ACQUISITION ENFORCED / REAL-CASE NOT PROVEN` 可标记** —— deterministic
  证据链（inventory→evidence→compile 前 typed 强制）完整；FX2Y 的 defect 循环（禁获取→空
  投影→猜测→被拒）六环中"禁获取"与"空投影"两环已机械消除，其余环节（validator/reviewer
  严格性）不变；real-case 价值依计划 §9 留待新 Gate。
- **下一次 cold-start Verification Gate 前提：已具备** —— 建议沿用 Mailpit Gate 骨架
  （冻结 identity + workspace preflight + 8-call 判定 + stop-loss），新增断言：intake run 的
  `toolUsage.readCount>0`、`actualReadEvidence` 非空、proposal `evidence_refs` 含 repo-map
  路径、reviewer 不再以"未经仓库事实"拒绝。恢复 FX2Y 或 fresh 样本由该 Gate 文档决策。
