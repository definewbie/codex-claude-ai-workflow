# Repository Fact Occurrence Ownership 与 Boundary Discovery 计划

日期：2026-08-28  
状态：`IMPLEMENTED / DETERMINISTIC ENFORCED / AWAITING INDEPENDENT DIFF REVIEW / REAL-CASE NOT PROVEN`

## 1. 决策摘要

当前 Mailpit fresh workstream `ws-20260827T232026Z-EFCB` 不继续运行，样本判定为
`INVALID_EVIDENCE_DURABILITY`。它已经证明 initial fact acquisition 的执行链能够工作，但没有证明该事实链在
后续 sibling node 执行后仍可审计。

根因不是 provider、模型、strict parser 或 proposal validator 失效，而是 repository fact 的生命周期 owner 错误：

1. contract-discovery occurrence 产生的 Git tracked inventory 已经成为 proposal validation 的权威输入；
2. 它仍写入历史遗留的 workstream-global `memory/repo-map.json`；
3. 后续任意 context/governance build 都会无条件覆盖这个路径；
4. validator 在当时通常仍能读到正确内容，但最终审计无法再从 proposal lineage 重建“当时用的是哪份 inventory”；
   node-scoped plan 仍残留 inventory HEAD/SHA identity，却不含 proposal 引用过的完整路径事实，identity 残留不能替代
   已被覆盖的事实文档；
5. actual governance report 又按“最新文件”和固定全局路径选择 plan/policy/catalog/repo-map，扩大了 occurrence 混淆面。

本计划采用一个原子方向：**execution/occurrence 事实不可变，current projection 可替换但不得成为 authority，累计 memory
保留显式 merge owner。** 不采用“不再覆盖”“验证后复制”“扫描目录猜最新”等局部补丁。

## 2. Captured 事实

### 2.1 已确认

- workstream：`ws-20260827T232026Z-EFCB`；
- persisted workflow：`small-plan@1`，`28 nodes / 46 edges / 8 agent nodes / 6 human gates`；
- execution-workspace preflight：`PASS`，baseline `99/0/0/0`；
- Claude initial intake：`17` 次 read/tool usage，`0` write，`0` Bash；
- initial actual governance report：`actualReadEvidence` 有 `16` 个 path/SHA 记录；
- initial proposal：strict parse、schema、scope resolution、inventory re-capture、evidence traceability 全部通过；
- Codex product review：`READY_FOR_PRODUCT_REVIEW`、`blocking=false`、`must_fix=[]`；
- workflow 随后正常停在 `gate.human_requirement_review / WAITING_FOR_HUMAN`；
- workstream-scoped Claude/Codex sessions 已关闭；未批准 gate、未 resume、未创建第二样本。

early boundary diagnostic：

`dual-orchestrator/target/verification-preflight/captcha-auth-mailpit-fact-acquisition/workspace-ws-20260827T232026Z-EFCB/early-boundary-diagnostic.json`

SHA-256：`64c139d57e5c269aa106d92dae75ee90a71143ca753efcb709df1e3ea69524f8`

### 2.2 代码锚点

- `RepoScopeGovernance.java:61-76`：contract discovery capture Git inventory 并构造 repo map；
- `RepoScopeGovernance.java:92-121`：plan/policy/catalog 部分按 node key 写入，但 repo-map、working-set、
  file-summaries、read-reuse-contract 与 workstream-memory 使用共享路径并被每次 build 覆盖；
- `TaskContractProposalValidator.java:72-132`：validator 固定读取 `memory/repo-map.json`，重捕获 Git identity 后检查
  proposal evidence refs；
- `ContextGovernanceActualReportWriter.java:67-80`：actual report 以 last-modified “latest”选择 plan/policy/catalog，
  并固定读取全局 repo map；
- `ContextGovernanceActualReportWriter.java:174-192`：latest 选择依赖目录枚举、mtime 与 node definition，而不是 run binding；
- `AgentRunGovernanceContext.java:51-75`：每个 Agent run 会创建 governance context，但返回值只携带 context ref 和
  qualified task contract，没有携带 governance artifact identity；
- `ArtifactContractValidator.java:105-121, 211`：validator 已拥有 run/node/result 边界，但 evidence traceability 没有接收
  occurrence binding。

### 2.3 准确的成熟度

| 能力 | 当前状态 |
|---|---|
| read/search-only initial fact acquisition | `ENFORCED` |
| proposal 在 validation 时引用 tracked repository fact | `ENFORCED` |
| initial fact acquisition real-case execution | `VALIDATED` |
| occurrence fact 在 sibling build 后仍可追溯 | `NOT MODELED` |
| final audit 从 proposal lineage 重建 inventory | `NOT ENFORCED` |
| Mailpit 产品与 8-call 完整 workstream | `NOT PROVEN` |

因此本次 INVALID 不能改写成 fact acquisition FAIL，也不能把 product review READY 改写成完整 PASS。

## 3. Artifact ownership inventory

| Artifact family | 当前路径语义 | 正确生命周期 | 正确 owner | 本轮处理 |
|---|---|---|---|---|
| repo map / tracked inventory | workstream-global，可覆盖 | Agent occurrence 不可变 | repository fact projection + validator binding | 改为 occurrence-scoped authority |
| file relevance plan | node-scoped，但同 node retry 可覆盖/被 latest 猜测 | occurrence 不可变 | governance planner | 纳入同一 binding |
| stage scope policy | node-scoped，但同 node retry 可覆盖/被 latest 猜测 | occurrence 不可变 | governance planner | 纳入同一 binding |
| capability catalog | node-scoped，但同 node retry 可覆盖/被 latest 猜测 | occurrence 不可变 | capability catalog | 纳入同一 binding |
| working set | workstream-global，可覆盖 | occurrence projection | governance planner | 改为 occurrence-scoped；不得作历史 authority |
| file summaries | workstream-global，可覆盖 | occurrence projection | governance planner | 改为 occurrence-scoped |
| read-reuse contract | workstream-global，可覆盖 | occurrence policy projection | read-reuse policy owner | 改为 occurrence-scoped |
| context governance report | planned 按 node、actual 按 run | occurrence 不可变 | context/adapter telemetry | 保留，绑定同一 artifact set |
| workstream memory | workstream-global，merge/update | workstream 累计状态 | `WorkstreamMemoryUpdater`/明确 merge 语义 | 保留可变，但 refs 只能标为 latest projection |
| memory metrics | workstream-global latest projection | operator projection | report writer | 不进入 authority；本轮 sibling sweep 核对 |

关键区别：共享路径本身不是错误。`workstream-memory.json` 的领域语义就是累计状态；repo-map 的新领域语义却是一次
proposal 判定所依据的不可变事实。两者不能采用同一覆盖策略。

## 4. 目标模型

### 4.1 单一 occurrence binding

新增一个小型 typed value，暂名 `RepositoryFactBinding`，不新增 module、registry、engine 或通用 artifact framework。
它至少包含：

- `workstream_id`
- `node_instance_id`
- `run_id`（context-only build 可为空，使用 builder node instance identity）
- `repo_map_ref` + `repo_map_sha256`
- `inventory_head` + `inventory_sha256` + `inventory_path_count`
- `file_relevance_plan_ref` + SHA
- `stage_scope_policy_ref` + SHA
- `capability_catalog_ref` + SHA
- `created_at`

binding 自身写入 occurrence-scoped immutable path。所有 ref 必须是 canonical workspace ref，所有 SHA 在消费前重验。

推荐路径形态：

```text
.agent/workstreams/<ws>/artifacts/context-governance/bindings/<occurrence-key>.json
.agent/workstreams/<ws>/artifacts/context-governance/repository-facts/<occurrence-key>.json
```

`occurrence-key` 由 ledger node instance + run id 构造，不使用 mtime，不扫描目录猜 latest。

### 4.1.1 唯一持久化落点

reader/writer sweep 后锁定如下：

- **唯一 authoritative persistence**：上述 immutable binding JSON；
- `AgentRunGovernanceContext.PreparedContext` 在当前 Java 调用栈中携带 typed binding，属于 transient transfer；
- `AcpxAgentTaskHandler` 把 binding ref 加入 adapter-owned `NodeResult.outputRefs`，供 validator 定位；模型不能写入或选择它；
- proposal `AgentOutputContract` sidecar 只保存 `binding_ref + binding_sha256`，作为 proposal lineage anchor，不复制
  binding 内容；
- actual governance report 只保存同一 `binding_ref + binding_sha256`，是 telemetry projection；
- `run.json` 维持现有 `contextGovernanceActualReportPath`，**不新增 binding 字段**，避免与 sidecar 形成双持久化 owner；
- context-only builder 把 binding ref/SHA 写入既有 context-package metadata；该 metadata 仍是 package binding，不能作为
  Agent proposal lineage owner。

sidecar 与 actual report 中的 ref/SHA 是对同一 authority 的引用，不是第二事实副本。任何消费者都必须重新读取 binding
并校验其自身 SHA 与内部 artifact SHA。

### 4.2 生产与消费链

```text
Git HEAD + tracked paths
  -> RepoScopeGovernance builds immutable occurrence artifacts
  -> RepositoryFactBinding(ref + SHA)
  -> AgentRunGovernanceContext.PreparedContext (transient typed transfer)
  -> adapter-owned NodeResult output ref
  -> ArtifactContractValidator verifies binding + re-captures Git identity
  -> AgentOutputContract sidecar records verified binding
  -> TaskContractProposalReference hashes that sidecar
  -> reviewer / compile / final audit follow proposal lineage to the exact binding
```

唯一 authority 是 sidecar lineage 所绑定的 immutable occurrence fact。模型的 `evidence_refs` 仍只表达它选择了哪些业务事实，
不能选择或伪造 inventory snapshot。

### 4.3 Actual report 不再猜测

`ContextGovernanceActualReportWriter` 必须接收本 run 的 binding：

- 删除对 `latestDocument(...mtime...)` 的 production authority；
- 不读取固定 `memory/repo-map.json`；
- plan/policy/catalog/repo-map 全部由 binding ref 定位并重验 SHA；
- actual report 写入 binding ref/SHA、inventory identity、actual read evidence；
- binding 缺失、ref 越界、SHA 不符时 fail closed，不能退回 latest/global 文件。

### 4.4 Current projection

`workstream-memory.json.refs` 可以继续指向最近一次 governance artifacts，供 prompt/operator 快速查看，但必须：

- 字段语义明确为 `latestProjectionRefs`，不能叫 historical evidence；
- 不被 validator、reviewer、compile、routing 或 final audit 当作 SoT；
- 指向的 occurrence artifact 本身不可变；
- 若没有实际 consumer，删除 global `memory/repo-map.json`、`working-set.json`、`file-summaries.json` 与
  `read-reuse-contract.json`，不保留兼容镜像。

旧 frozen workstream 的只读兼容只能显式标注 `repository_fact_binding=not_configured`，不得把 global map 推断成新 binding。

## 5. 不能采用的方案

1. **已有 repo-map 时不覆盖**：会把第一次 context 的空 map 永久保留，也不能表达 revision/retry occurrence。
2. **validator PASS 后复制一份**：复制发生在 authority 消费之后，仍没有证明 validator 与复制对象是同一字节。
3. **只把 repo-map 改成 node-scoped 文件名**：actual writer 与 validator 仍会猜 latest，retry 同 node 仍冲突。
4. **扫描目录找最新文件**：mtime、目录顺序和重试会产生第二 identity owner。
5. **把完整 inventory 嵌入 proposal**：让模型输出复制 deterministic facts，扩大 token 与伪造面。
6. **只在 final audit 加 warning**：无法阻止不可审计结果被当作 PASS。
7. **为此建设通用 evidence graph/platform**：当前问题只需要一份 bounded typed binding。

## 6. 实施边界

### 6.1 预计 production 变更

- `RepoScopeGovernance`：caller-provided occurrence identity、immutable paths、SHA binding；
- `AgentRunGovernanceContext`：返回 binding ref/identity；
- `AcpxAgentTaskHandler`：将 binding ref 作为 adapter-owned output ref 传给 validator，不暴露给模型修改；
- `AgentRunMetadataWriter`：把 typed binding 直接传给 actual report writer；`run.json` 不新增 binding persistence；
- `ContextGovernanceActualReportWriter`：只按 binding 读取；
- `ArtifactContractValidator`、`TaskContractProposalValidator`：消费 verified binding，而不是 fixed global path；
- `AgentResultDocumentProjector` / `AgentOutputContractDocument`：持久化 additive repository-fact binding；
- `TaskContractProposalReference.readVerified/resolveArtifact`：在读取 proposal sidecar 时验证
  sidecar -> binding -> repo-map 全链；
- `ContextBuildPackageHandler`：context-only metadata 记录本次 binding ref/SHA；
- `WorkstreamMemoryProjection` 与 context renderer：只把 current refs 当 projection。

reader/writer sweep 已确认 sidecar 是 proposal lineage 的唯一合适 anchor；使用 additive typed
`repository_fact_binding` 字段，不修改模型面对的 `AgentResultEnvelope`。不得塞入自由文本或 `control` 字段冒充领域语义。

### 6.1.1 Reader/writer/sibling sweep 关闭记录

| 路径 | 现状 | 目标 |
|---|---|---|
| `RepoScopeGovernance` | 唯一 repo-map writer；一次 build 同时覆盖四个 global projections | 产生 immutable binding/artifacts |
| `AgentRunGovernanceContext` | run 前已经持有 `runId`，但 PreparedContext 不返回 artifact identity | transient 携带 typed binding |
| `AcpxAgentTaskHandler` | PreparedContext 生命周期覆盖 prompt、transport、metadata completion 与 NodeResult 构造 | outputRefs 传 binding ref |
| `AgentRunMetadataWriter` | run.json 只持有 actual report path | 不增加 binding 字段；直接传 typed binding 给 writer |
| `ContextGovernanceActualReportWriter` | fixed repo-map + mtime latest + legacy catalog fallback | 只消费传入 binding；删除三类猜测 |
| `ArtifactContractValidator` | 已接收 NodeResult/outputRefs/run/node，traceability 却读 global map | 从 outputRefs 解析恰好一个 binding |
| proposal sidecar/reference | sidecar 已由 projector/typed reader 拥有；reference 已 hash sidecar | sidecar anchor binding；readVerified 重验全链 |
| `WorkstreamMemoryProjection` | hard-code global read-reuse ref | 从 latest projection refs 渲染，不作 authority |
| `CompletionAuditWriter` | 只审 final report/handoff/next-stage prompt，不审 repository fact | 不修改，不冒充 repository fact audit owner |

terminal cold audit 的 production authority 是 `TaskContractProposalReference.readVerified/resolveArtifact` 的全链验证；real-case
Gate 保存 initial proposal reference，并在 human gate 与 terminal 前重新调用等价 verifier/逐 SHA 交叉核对。现有
`CompletionAuditDocument` 只继续审它原有的 lifecycle/report gaps，本 checkpoint 不把 runtime repository semantics 塞进
ledger-file 模块。

### 6.2 明确不改

- workflow graph、node 数量、human gate、revision budget；
- `AgentResultEnvelope` 的模型输出 schema；
- parser、correction/retry、provider 配置；
- TaskContract scope owner 与 reviewer/human authority；
- Mailpit target 产品代码；
- ledger multi-file transaction protocol。

### 6.3 原子性

repo-map immutable path、run binding、validator 消费、sidecar lineage、actual report 和 final audit 是一个原子 checkpoint。
只改 producer 而不改消费者会留下猜测路径；只改 audit 不改 validator 则会产生第二 owner。working-set/file-summaries/
read-reuse-contract 的路径迁移属于同一 sibling boundary，但不改变其业务规则。

## 7. Cannot-bypass tests

至少覆盖以下序列，而不是只测单个 build：

1. `context build(empty legacy map) -> contract-discovery run(non-empty inventory) -> proposal validate -> product-review context build`
   后，initial binding/ref/SHA 仍可解析，proposal audit PASS；
2. sibling context build 不能改变旧 occurrence artifact 字节；
3. 同 node instance 的第二个 run 产生不同 binding，actual report 各自绑定正确 plan/policy/catalog/repo-map；
4. binding ref 被替换、SHA 被篡改、repo-map 被篡改、Git inventory drift 均在 sidecar publication 前 fail closed；
5. latest/global projection 与 binding 冲突时，以 binding 为 authority并报告 projection drift；
6. 删除 binding 后，即使 global repo-map 完整也必须失败；
7. `TaskContractProposalReference.readVerified` 在新 JVM/新 reader、无内存对象条件下重建全链；Gate 的 terminal cold
   audit 复用同一 identity 并逐 SHA 交叉核对；
8. revision proposal 绑定自己的 occurrence fact，同时保留 base proposal lineage；
9. old frozen workstream 显式投影 `not_configured`，不得猜测；
10. sibling artifact sweep 证明 mutable workstream memory 不会被错误冻结，occurrence artifacts 不会被错误合并。

## 8. 最大化问题发现的验证设计

目标不是承诺“发现所有缺陷”，而是在不无限增加 provider 调用和架构机制的前提下，提高每次 review/real-case 对边界缺陷的
发现密度。

### 8.1 Boundary Coverage Matrix

每个进入 Gate 的 authoritative artifact/state 必须有一行矩阵：

| 维度 | 必答问题 |
|---|---|
| Owner | 谁唯一写入；是否有 sibling writer |
| Lifetime | definition / workstream / iteration / node / run occurrence / current projection |
| Identity | ref、SHA、ledger id 中哪个构成 identity；是否依赖 latest/mtime/目录扫描 |
| Consumer | 哪个下游唯一消费；是否还有 fallback/global alias |
| Enforcement | 不满足时，哪个副作用在何处被阻止 |
| Durability | 后续 sibling 写入、human pause、revision、retry、restart 后是否仍可复算 |
| Projection | UI/CLI/report/prompt 是否只消费 read model；是否出现同事实矛盾值 |
| Audit | 冷启动 reader 能否仅靠 durable lineage 重建结论 |
| Test | 哪条 sequence/cannot-bypass test 会在 owner 漂移时失败 |

同行评审不能只回答字段是否存在、单测是否绿色；必须至少选择一条 authority，从 producer 追到最终 audit，并做一次 sibling
writer sweep。

### 8.2 五层发现协议

#### L1 静态 ownership sweep

- 对每个 authoritative path/type 清点全部 producer、consumer、fallback、current alias；
- 搜索相同目录下的 sibling artifact；
- 标出 `latest`、mtime、目录扫描、固定全局文件名、raw map/string field 等高风险选择器。

#### L2 生命周期序列测试

把单步测试升级为最小 temporal sequence：

```text
produce A -> consume A -> sibling writes B -> pause/restart -> audit A
```

针对 revision/retry 再加：

```text
produce A1 -> retry/revision -> produce A2 -> audit A1 and A2 independently
```

这类测试优先于再增加同类字段 UT，因为本次缺陷正是在所有局部测试 PASS 时出现。

#### L3 冷启动与反事实审计

- 用新进程/新 reader 从 ledger 重建 PASS/FAIL，不允许复用内存对象；
- 篡改 current projection，authoritative conclusion 必须不变并出现 drift；
- 篡改 immutable fact 或 binding，结论必须 fail closed；
- 删除 fallback 后测试必须失败，证明没有隐性旁路。

#### L4 Real-case early boundary sentinels

每个 real-case 不增加 Agent node，而是在既有 Gate 中设置少量机械 sentinel：

1. 关键 authority 产生后立即记录 ref/SHA/owner/lifetime；
2. 每个 sibling stage 完成后重验此前 immutable refs 仍存在且 SHA 不变；
3. prompt/report 出现同一事实的冲突投影时立即 INVALID；
4. human gate 前执行一次 cold audit；
5. terminal completion 前再执行完整 lineage audit；
6. sentinel 失败立即 stop-loss，不靠后续模型调用“补回来”。

sentinel 只验证既有 typed/durable facts，不新增业务判定 owner，也不读取模型正文。

#### L5 任务选择与样本纪律

real-case 按 boundary coverage 选择，而不是单纯选最大任务：

- cold start + repository discovery；
- 至少一个 human pause；
- 真实文件修改与 deterministic verification；
- 能自然覆盖 revision/retry/recovery 中最多一种，不人为制造；
- final audit 可机械判定；
- 单一业务目标，防止多个实验目标互相污染。

同类 failure 连续两次后停止 provider 样本，回到 owner/lifetime/consumer 模型；不得继续调 prompt 或增加 review 轮次。

### 8.3 同行评审输出要求

评审结论除 P0/P1/P2 外，必须给出：

- 一条独立重建的 end-to-end authority chain；
- 至少一个可能覆盖/重放/恢复的 temporal counterexample；
- sibling writer/reader sweep 结果；
- 哪些结论是代码证明、哪些只是 real-case 推断；
- 一个最小反证测试：删除或篡改哪个 owner 后必须失败；
- `MODELED / PROJECTED / VALIDATED / ENFORCED / PROVEN` 分层；
- 是否值得继续实现：长期正确性收益与短期复杂度成本。

评审 prompt 不预填预期结论、文件行号答案或测试数字。提供范围和核心问题即可，让 reviewer 独立取证。

## 9. Verification Gate 模板增量

后续 Gate 在既有九字段之外增加一个 `Boundary Coverage` 附表，不改变 runner parser：

```text
Boundary Coverage:
- Authority under test:
- Occurrence identity:
- Sibling writers crossed:
- Pre-human cold audit:
- Terminal cold audit:
- Projection contradiction check:
- Natural lifecycle branch:
```

该附表是 Gate 设计纪律，不是新 production schema。缺少某项时可明确写 `not_applicable + reason`，不能写“预计”。

## 10. PASS / FAIL / INVALID

### Deterministic checkpoint PASS

- 第 7 节 sequence/cannot-bypass tests 全部通过；
- validator、actual report、sidecar lineage 与 final audit 消费同一 binding；
- 全部 global/latest authority 路径零命中；
- sibling mutable/current projection 语义有测试；
- focused + full reactor + diff-check PASS；
- 独立代码级 diff review PASS。

### FAIL

- 仍有 consumer 从 global repo-map 或 mtime latest 取得 authority；
- sibling build 能改变旧 occurrence evidence；
- final audit 只能靠 prompt、console、Markdown 或 operator 猜测还原；
- 为兼容旧 workstream而静默回退到 global map。

### INVALID

- captured evidence 未冻结或 hash 不符；
- 测试只验证 JSON 字段，没有真实生命周期顺序；
- 通过新增 provider retry/第二样本替代 deterministic closure；
- 实施期间顺带修改 workflow/product/provider，无法归因。

## 11. 执行顺序与授权边界

1. 独立只读架构评审本计划；
2. production reader/writer/sibling sweep 已完成并锁定 §4.1.1 的唯一落点；
3. 获得实施授权后，作为一个原子 checkpoint 实施第 4-7 节；
4. focused sequence tests -> full reactor -> diff-check；
5. 独立代码级 diff review；
6. clean SHA 后制定新的 cold-start Gate，并应用第 8-9 节 discovery protocol；
7. 只有新 Gate 独立 PASS 且用户重新授权，才创建唯一 fresh real-case。

当前 checkpoint 已完成 deterministic 实施与本地验证，正在等待独立代码级 diff review。仍不授权 provider probe、
resume `EFCB`、人工批准其 gate 或创建第二个 workstream。

## 12. 退出条件

若 reader/writer sweep 证明 repository fact 只需在 actual report 内完整内嵌即可闭合 sidecar lineage，且引入独立 binding
反而增加重复 owner，则回到更小方案；但必须同时证明 validator、proposal reference、revision 与 final audit 都消费同一
字节身份。不能仅因实现较短而省略 durability。

若新 discovery protocol 只增加文档和人工成本、连续两个 checkpoint 没有发现新的 sibling/lifecycle 缺口，则停止扩张，
保留 L1-L3 deterministic checks，把 L4 sentinel 限定为高风险 authority，不建设通用 verification framework。

## 13. 实施与验证记录

实施保持 §6 的原子边界：新增 runtime-owned immutable `RepositoryFactBinding`；context build 将八类 occurrence artifact
迁移到 `artifacts/context-governance/occurrences/<node-instance>-<run>/`；adapter transient 传递 binding，并通过
`NodeResult.outputRefs` 与 machine-owned result facts 交给 validator；proposal sidecar 保存 binding ref/SHA；actual report
只按 verified binding 读取；`TaskContractProposalReference` 冷读取时重验 sidecar -> binding -> artifact 全链。
`run.json`、workflow graph、Agent envelope schema、provider 与 product 均未修改。

实施中由测试发现并关闭两处真实 bypass：

1. `ArtifactContractValidator` 原先倒序扫描所有 JSON output ref，新增 binding 后会把 `binding.json` 误当 Agent envelope；
   现在按 adapter-owned binding ref 精确排除，strict Agent parser authority 不变；
2. sidecar 原先在 repository binding 注入前完成 typed projection、写盘与 reference SHA，导致磁盘 sidecar 不含 binding；
   现在 binding 在 semantic validation 和持久化前注入，cold reader 删除 binding 后必然失败。

验证结果：focused runtime `128/0/0/0`，focused adapter `47/0/0/0`，非沙箱 clean full reactor
`1248/0/0/0`，`git diff --check` clean。沙箱内 full reactor 的两条 process-tree 测试曾因 macOS `sysctl` 权限失败；
同一代码在非沙箱完整运行通过，故分类为验证环境限制而非 checkpoint 回归。

成熟度：occurrence binding 与 proposal/actual-report 消费链为 `ENFORCED`；尚未用新的 cold-start real-case 证明最终
产品流程与 terminal cold audit，因此保持 `REAL-CASE NOT PROVEN`。
