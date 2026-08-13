# Intake ScopeOperation Fact Projection 计划 — 独立只读架构评审

日期：2026-08-13
审查对象：`docs/plans/intake-scope-operation-fact-projection-plan-20260813.zh-CN.md`
审查方式：逐文件阅读 production 代码 + captured v9 evidence；未修改任何文件，未运行 provider，未提出新 phase/module/registry/engine。

## 结论摘要

| 项 | 判定 |
|---|---|
| P0 | 0 |
| P1 | 0 |
| P2 | 4（均非阻塞，见 Findings） |
| **Boundary Review** | **PASS** |
| 是否过度设计 | **否**（最小、单一 owner、durable sidecar 形状不变） |
| 是否可作为原子 checkpoint | **是** |
| Implementation READY | **是** |

---

## 1. Findings（P0 / P1 / P2，附真实 file:line）

### P0 — 无

### P1 — 无

### P2-1（TOCTOU：resolver 引入第二个 `Files.exists` 读取点，未声明同快照不变量）

- **位置**：`TaskContractProposalValidator.validateWorkspacePathRef()` `dual-runtime/.../TaskContractProposalValidator.java:371-376`（现存唯一 `Files.exists` 读取），计划将新增 `TaskContractScopeResolver` 在 projection 阶段再读一次 `Files.exists`。
- **现象**：resolver 在 projection 时读一次 filesystem 派生 operation，validator 在 validate 时再读一次做 cannot-bypass 校验。若两次读取之间 effective worktree 变化，二者会分歧。当前无 Agent 工具运行、单节点单线程，窗口理论存在但不现实。
- **建议**：在 resolver/validator 上注释「同一 node 的 projection→validation 必须观察同一 effective worktree 快照」即可，非阻塞设计变更。

### P2-2（revision merger scope 去重键语义改变，计划未显式说明）

- **位置**：`TaskContractProposalRevisionMerger.merge()` `dual-runtime/.../TaskContractProposalRevisionMerger.java:54-60`（当前去重键 `entry.path() + "\0" + entry.operation()`）。
- **现象**：`scopeAdditions` 改为 path-only 后，去重键从 `(path, operation)` 收敛为 `(path)`。语义变化：旧形状下同一 path 用不同 operation 声明两次不会去重，新形状下会去重。这是一次严格性提升（解析后同一 path 不可能有合法双 operation），但计划未点名这一行为差异。
- **建议**：计划第 4.3 节补一句「merger 去重键由 (path, operation) 收敛为 path」。

### P2-3（envelope validator 的 operation-required 校验需同步移除，计划仅列文件未点明）

- **位置**：`AgentResultEnvelopeValidator.validateScopeEntries()` `dual-runtime/.../AgentResultEnvelopeValidator.java:345-362`，其中 `entry.operation() == null` 时 `reject("result.scope.included.operation is required")`（第 358-360 行），且 initial（第 122 行）与 revision（第 242 行）两处调用。
- **现象**：`ProposalScopePath` 只有 `path` 字段后，这条「operation is required」校验必须改为「path 必填、无 operation 字段」。文件已列入计划第 7 节，但第 4 节未显式写出这条校验的替换，实现时容易遗漏导致 Agent 侧 shape 收窄后仍被 validator 拒绝。
- **建议**：计划第 4.1 节补充「`AgentResultEnvelopeValidator.validateScopeEntries` 同步改为 path-only 校验，删除 operation-required」。

### P2-4（cannot-bypass 测试缺少「resolver 产出后被篡改 operation 仍被 durable validator 拒绝」一条）

- **位置**：计划第 6 节 8 类测试；第 5 类（initial sidecar proof）证明 resolver 产物进入 validated sidecar，第 8 类证明下游不感知变化。
- **现象**：缺少一条直接证明「durable `TaskContractProposalValidator` 仍是 cannot-bypass 守卫」的新增测试——即 resolver 正确派生为 `CREATE` 后，若 sidecar 中 operation 被篡改为 `MODIFY`（或反之），validate 必须仍按 `Files.exists` 拒绝（`TaskContractProposalValidator.java:371-376`）。该逻辑本就存在且有历史测试覆盖，但本 checkpoint 恰恰动了 projection→validation 的中间链路，值得一条显式回归测试锁定守卫地位不被削弱。
- **建议**：在第 6 节补第 9 类「resolver/validator 不一致 must fail-closed」，或并入第 5 类。

---

## 2. 逐项核实结论

### 问题 1 — v9 根因确实是 ScopeOperation 双 owner：**确认**

- Agent 被要求声明 `CREATE/MODIFY`：`AgentResultEnvelope.ProposalScope(List<TaskContract.ScopeEntry> included, …)` `AgentResultEnvelope.java:65`；prompt 明确写「each entry names one exact repository-relative path and `CREATE` or `MODIFY`」`AgentResultPromptContract.java:92-93`。
- seed-only intake 禁止工具：captured prompt 第 27 行「do not call tools, do not ask to list directories, do not request Bash/Grep/Glob/Read」。
- prompt/repo-map 无 authoritative existing-path fact：`repo-map.json` 的 `source="task_contract_scope+git_evidence"`、`files=[]`（`RepoScopeGovernance.buildRepoMap()` `RepoScopeGovernance.java:199`；initial intake 时二者尚不存在）。
- validator 独立读 filesystem 后拒绝：`validateWorkspacePathRef()` `TaskContractProposalValidator.java:371-376`（`MODIFY && !Files.exists` → error）。
- captured evidence：`README.md` 被声明 `MODIFY` 但实际不存在，报 `TASK_CONTRACT_PROPOSAL_VALIDATION_FAILED: scope.included MODIFY path does not exist: README.md`。**双 owner 根因成立。**

### 问题 2 — `operation` 是 deterministic effective-worktree fact：**确认**

现存 validator 的判定就是纯 `Files.exists`：`exists -> MODIFY`（第 371 行分支）、`absent -> CREATE`（第 374 行分支）。Agent 对 operation 的意见从未被采纳，只被验证。计划把它前置为确定性派生，语义正确。

### 问题 3 — 单一 `TaskContractScopeResolver` 比备选更通用且更小：**确认**

- 全量 tracked-file inventory：token 随仓库规模无界，截断制造错误否定事实 → 拒绝合理。
- repo-map 扫描目录：`RepoScopeGovernance` 明确只表达 `task_contract_scope+git_evidence`，扫描猜业务意义违反既有边界 → 拒绝合理。
- validator 自动纠错：会成为第二个 operation 生产者，掩盖 Agent 错误 → 拒绝合理。
- artifact bounded correction：新增第二次 Agent 调用，未解决信息边界（Agent 仍无 existing-path fact）→ 拒绝合理。
- runtime retry：新增循环，无新事实 → 拒绝合理。

resolver 方案 O(1)（与仓库规模无关）、确定性、单一 owner，确为最小且正确的修复。

### 问题 4 — durable `TaskContract.ScopeEntry(path, operation)` 应保持不变：**确认**

下游均消费 durable `operation`：`ChangeScopeEvaluation`（`ChangeScopeEvaluation.java:16,59,116,163,174` 用 declaredOperation 比对实际变更）、`TaskContractValidator.validateIncludedRefs`（`dual-core/.../TaskContractValidator.java:65`）、`WorkstreamMemoryUpdater`（`WorkstreamMemoryUpdater.java:131,154` 读 path）、`RepoScopeGovernance.contractScopePaths`（读 path）。operation 是 change-scope 校验的 durable 事实，只是不该由 Agent 决定。计划第 4.2/5 节明确 durable schema 不变，正确。

### 问题 5 — initial 与 revision 必须同时改；无第三条被遗漏的 producer：**确认**

Agent-facing `ScopeEntry` 生产者恰好两处：
- initial：`ProposalScope.included` `AgentResultEnvelope.java:65`
- revision：`ObligationPatch.scopeAdditions` `AgentResultEnvelope.java:91`

其余触及 `TaskContract.ScopeEntry` 的均为消费者/示例，均已在计划文件清单内：
- `AgentResultEnvelopeValidator.validateScopeEntries`（第 345-362 行）——消费者，需随 shape 改（见 P2-3）。
- `AgentResultPromptContract.exampleResult` + `contractRevisionInstruction`（第 79-80、190-192、200-206 行）——示例/文案生产者，计划第 4.4 节已覆盖。
- `AgentResultDocumentProjector.taskContractProposal/taskContractProposalMap`（第 108-185 行）——projection 消费者，计划第 4.3 节已覆盖。

**无第三条 Agent-facing ScopeEntry producer 被遗漏。**

### 问题 6 — resolver 输入/输出/错误边界充分：**确认**

计划第 4.2 节六项边界（exact declared paths only / canonical relative path / workspace containment / directory rejection / exists→MODIFY / absent→CREATE，不扫描、不猜相关性）与现存 validator 的检查完全同构（`TaskContractProposalValidator.java:341-377`：metadata noise、`new:` 前缀、`isCanonicalPath`、containment、`Files.isDirectory`、exists 分支）。resolver 可复用 `TaskContractValidator.isCanonicalPath`（`dual-core/.../TaskContractValidator.java:148`），无需重实现。边界充分。

### 问题 7 — owner 真正唯一：**确认（有一个可接受的理论窗口，见 P2-1）**

- projector 只委托 resolver（计划第 4.3 节）→ 不自行派生。
- merger 对新增 path 只委托 resolver、对 previous entries 原样保留（计划第 4.3 节 + 现存 `TaskContractProposalRevisionMerger.java:110` `previous.scope().included()` 不变）→ 不重算历史 operation。
- validator 只做 durable cannot-bypass 验证（仍读 `Files.exists`，但验证的是 resolver 已物化的 operation，不再接受 Agent 意见）→ 是守卫而非生产者。
- 全仓仅一个 `Files.exists -> ScopeOperation` 生产者（resolver），validator 的 `Files.exists` 是验证读取。符合项目既有「单一 producer + cannot-bypass verifier」模式（类比 `AgentEvidenceIndex` 生产、下游 lineage 校验）。

### 问题 8 — strict parser 会拒绝旧 `{path, operation}` 形状：**确认，不会静默忽略**

`AgentResultEnvelopeParser` 显式 `.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)`（`AgentResultEnvelopeParser.java:22`）。`ProposalScopePath` 收窄为只有 `path` 后，旧 shape 的 `operation` 会触发 unknown-field 拒绝 → `AGENT_RESULT_PARSE_FAILED / INVALID_JSON`，不会静默忽略。计划第 4.1 节「strict Jackson unknown-field rule 必须拒绝」成立。

### 问题 9 — revision merge 对 previous operation 原样保留、只解析新增 path：**确认**

现存 merger `TaskContractProposalRevisionMerger.java:110` 以 `previous.scope().included()` 为基准，第 114 行 `scope.addAll(scopeAdditions)` 只追加。改为 `ProposalScopePath` 后只需对新增 path 调 resolver 再 `addAll`，previous 条目字节语义不变。计划第 4.3 节（「previous proposal 中已持久化的 scope entries 原样保留」）与代码一致。

### 问题 10 — 八类 cannot-bypass 测试基本足够，缺一条守卫回归：**基本足够（见 P2-4）**

第 5 类已覆盖真实 `ArtifactContractValidator -> validated sidecar -> reviewer ref` 链路（`ArtifactContractValidator.validateAgentOutput` 第 92-118 行走真实 parser→validator→projector，`withResultSidecars` 第 264-271 行写 sidecar 并 add ref）。唯一缺口是「resolver 产物被篡改后 durable validator 仍拒绝」的显式守卫回归（P2-4），建议补第 9 类，不阻塞。

### 问题 11 — 破坏性检查：**均不破坏**

- **revision-delta integrity**：`validateRevisionDelta`（`TaskContractProposalValidator.java:110-160`）只对 clause 快照比对，不涉及 scope operation。不破坏。
- **contract compile**：消费 durable `ScopeEntry`，不变。不破坏。
- **RepoScopeGovernance write candidates**：`contractScopePaths`（`RepoScopeGovernance.java:564-580`）读编译后 `artifacts/task-contract.json` 的 `.path()`，不读 envelope。不破坏。
- **ChangeScopeEvaluation**：消费 durable `ScopeEntry`（path+operation）。不破坏。
- **lineage**：`TaskContractProposalReference` 对 sidecar 做 sha256；durable sidecar 的 `task_contract_proposal.scope.included` 仍投影为 `{path, operation}`（`AgentResultDocumentProjector.taskContractProposalMap` 第 113-117 行不变），字节形状不变。不破坏。
- **existing durable sidecar compatibility**：durable sidecar 形状不变，无需迁移。不破坏。

关键结论：**只有 Agent-facing envelope 形状变，durable sidecar / compiled contract 形状完全不变**，这是该计划最小性与正确性的最强证据。

### 问题 12 — 更小但同样可靠的方案：**不存在（已逐一排除）**

- 仅改 prompt 文案（如「declare CREATE for new files」）：**无 SoT**——seed-only intake 无工具、repo-map 空，Agent 无法获得 existing-path fact，纯文案依赖模型合规，不可靠。
- validator 自动纠错（MODIFY→CREATE）：**第二 owner**，掩盖 Agent 错误，违反「单一 producer + cannot-bypass verifier」哲学。
- 全量 inventory 进 prompt：**token 无界**。
- repo-map 扫描：**越权猜业务意义**，违反既有边界。

resolver 是唯一「确定、O(1)、单一 owner、可 cannot-bypass 验证」的方案，计划第 5 节的排除清单正确。

---

## 3. End-to-end invariant（逐项代码证据）

| 维度 | 证据 |
|---|---|
| **Declared by** | `ProposalScopePath(String path)`（计划第 4.1 节）+ 唯一 resolver owner `TaskContractScopeResolver`（计划第 4.2 节）；durable `TaskContract.ScopeEntry(path, operation)`（`dual-core/.../TaskContract.java:86-89`）保持不变 |
| **Proven by** | 计划第 6 节 8+1 类 cannot-bypass tests；第 5 类走真实 `ArtifactContractValidator` 链（`ArtifactContractValidator.java:92-118`） |
| **Consumed by** | `AgentResultDocumentProjector.taskContractProposal`（第 141-156 行）与 `TaskContractProposalRevisionMerger.merge`（第 110-114 行）调用 resolver 产出 durable `ScopeEntry`；下游 `ChangeScopeEvaluation` / `TaskContractValidator` / `RepoScopeGovernance` / `WorkstreamMemoryUpdater` 消费 durable entry |
| **Enforced before** | resolver 在 projection（`ArtifactContractValidator.resolveProposalCandidate` 第 160/169 行触发 projector/merger）时派生 operation，早于 durable proposal validator（第 186-187 行）、reviewer dispatch 与 compile |
| **Failure route** | resolver 失败（非 canonical/越界/directory）→ typed artifact contract violation，`ArtifactContractValidator.contractViolation`（第 382-442 行）fail-closed；validator 与 resolver 不一致 → `TASK_CONTRACT_PROPOSAL_VALIDATION_FAILED`（第 189-193 行） |
| **Cannot-bypass test** | 计划第 6 节第 3 类（strict parser 拒绝旧 shape，`AgentResultEnvelopeParser.java:22`）+ 第 7 类（全仓扫描仅 resolver 可产 operation）+ P2-4 建议补的守卫回归 |
| **Residual raw/legacy path** | 无。durable deserialization（`AgentOutputContractDocument.scopeEntries` 第 217-234 行）与 test fixtures 读 durable `ScopeEntry` 豁免，但不再是 Agent-facing 输入通道 |

---

## 4. 明确回答

- **Boundary Review：PASS**（0 P0 / 0 P1 / 4 非阻塞 P2）。
- **过度设计：否**。仅新增一个 package-private 小类 + 类型收窄 + 三处 wiring（projector/merger/validator），durable sidecar 与 compiled contract 形状零变化。
- **原子 checkpoint：是**。边界单一——把 ScopeOperation 投影从 Agent 迁移到 Dual；不触及 workflow/retry/correction/downstream schema。
- **Implementation READY：是**。设计完整、可落地、cannot-bypass 路径闭合。

## 5. 必须修改的计划项

1. （P2-3）第 4.1 节补写：`AgentResultEnvelopeValidator.validateScopeEntries`（第 345-362 行）随 shape 改为 path-only 校验，删除 `operation is required`（第 358-360 行）；initial（第 122 行）与 revision（第 242 行）两处调用同步。
2. （P2-2）第 4.3 节补写：merger scope 去重键由 `(path, operation)` 收敛为 `path`。

## 6. 建议但不阻塞

1. （P2-1）在 resolver/validator 注释「同一 node 的 projection→validation 观察同一 effective worktree 快照」。
2. （P2-4）第 6 节补第 9 类 cannot-bypass 测试：resolver 派生后被篡改的 operation 仍被 durable validator 拒绝。
3. resolver 复用 `TaskContractValidator.isCanonicalPath`（`dual-core/.../TaskContractValidator.java:148`），不重实现。

## 7. 明确拒绝的替代方案

| 方案 | 拒绝理由 |
|---|---|
| 全量 tracked-file inventory 进 prompt | token 无界，截断制造错误否定事实 |
| repo-map 扫描目录 | 违反「不扫描猜业务意义」既有边界（`RepoScopeGovernance.java:244`） |
| validator 自动纠错 | 第二 operation owner，掩盖 Agent 错误 |
| artifact bounded correction | 第二次 Agent 调用，不解决信息边界（Agent 仍无 existing-path fact） |
| runtime retry | 新增循环，无新事实 |
| 仅改 prompt 文案 | 无 SoT，seed-only 下模型无法获得 existing-path fact，依赖模型合规 |

---

*本报告为只读架构评审证据；未修改、未提交任何 production 文件。*
