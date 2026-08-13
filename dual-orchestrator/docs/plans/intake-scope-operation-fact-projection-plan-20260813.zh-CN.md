# Intake ScopeOperation Fact Projection 原子实施计划

日期：2026-08-13

状态：`IMPLEMENTED / DETERMINISTIC PASS / INDEPENDENT DIFF REVIEW REQUIRED / NOT PROVEN`

实现结果：Agent-facing initial/revision scope 已收窄为 exact path；单一
`TaskContractScopeResolver` 根据 effective worktree 派生 durable `CREATE/MODIFY`，随后仍由
`TaskContractProposalValidator` 独立 fail-closed 复核。focused tests `55/0/0/0`、受影响 ACP adapter
tests `41/0/0/0`、full Maven reactor `1223/0/0/0`，`git diff --check` clean。全量 sibling sweep 同步更新
了共享 strict Agent-result fixture；durable `TaskContract.ScopeEntry` shape 未变。未经独立代码级 diff review
和 fresh captcha-auth real-case，本能力不得标记为 `PROVEN`。

## 1. 唯一目标

关闭 v9 fresh real-case 暴露的一个信息边界：seed-only intake 在不能读取仓库时，不再被要求猜测
`TaskContract.ScopeOperation`。Agent 只声明业务相关的精确 repository-relative path；Dual 根据 effective
worktree 的同一 deterministic filesystem fact 唯一派生 `CREATE` 或 `MODIFY`，再写入 durable
`TaskContractProposal`。

本 checkpoint 不完成 captcha-auth 产品，不运行 provider，不新增 workflow/retry/correction，也不建设仓库
inventory、artifact graph、registry、engine 或通用平台能力。

## 2. Captured Failure

v9 workstream：`ws-20260813T115256Z-8G74`；run：`run-1786621976773`。

- process exit `0`，stop reason `end_turn`；strict JSON、schema 与 Agent result contract 均有效；
- Agent 将不存在的 `README.md` 声明为 `MODIFY`；
- `TaskContractProposalValidator.validateWorkspacePathRef()` 从 effective worktree 读取 `Files.exists()`，产生
  `TASK_CONTRACT_PROPOSAL_VALIDATION_FAILED`；
- reviewer 与 implementation 未启动，target/fresh worktree 无业务修改；fail-closed 行为正确；
- 同一 prompt 明确禁止 tool/read/glob；`repo-map.json` 的 source 是
  `task_contract_scope+git_evidence`，而 initial intake 时二者尚不存在，因此 `files=[]`；prompt 没有提供任何
  authoritative existing-path fact。

所以失败不是 parser、transport、provider 或 validator 放宽问题。缺口是：filesystem fact 只被 validator
消费，没有在 Agent 被要求声明 operation 前形成可用且同源的 contract boundary。

## 3. Ownership

### 3.1 当前错误模型

```text
Agent guesses path + operation
        |
        v
AgentResultEnvelope
        |
        v
TaskContractProposalValidator reads filesystem and accepts/rejects operation
```

同一个 `operation` 同时被当作 Agent proposal 与 filesystem fact，存在双 owner。

### 3.2 目标模型

```text
Agent declares exact path intent
        |
        v
AgentResultEnvelope.ProposalScopePath(path)
        |
        v
TaskContractScopeResolver (single owner)
  canonical path + exact-file checks
  Files.exists(effectiveWorktree/path)
  exists -> MODIFY; absent -> CREATE
        |
        v
durable TaskContractProposal.ScopeEntry(path, operation)
        |
        v
existing TaskContractProposalValidator + compiler + change boundary
```

- Authoritative raw fact：effective worktree 中该 exact path 在 proposal projection 时是否存在；
- 唯一 resolver owner：新增一个 package-private 小类 `TaskContractScopeResolver`；
- durable fact：已验证 sidecar 中的 `TaskContract.ScopeEntry(path, operation)`；
- downstream consumer：现有 proposal validator、contract compiler、RepoScopeGovernance 与 change-scope；
- 被阻止的副作用：非法/non-canonical/directory path 在 reviewer、compile、implementation 前失败；
- projection 不扫描目录猜业务相关性，只解析 Agent 已声明的精确路径。

## 4. 原子实现边界

### 4.1 Agent-facing typed contract

修改 `AgentResultEnvelope`：

- 新增 closed record `ProposalScopePath(String path)`；
- initial `ProposalScope.included` 从 `List<TaskContract.ScopeEntry>` 改为
  `List<ProposalScopePath>`；
- revision `ObligationPatch.scopeAdditions` 同样改为 `List<ProposalScopePath>`。
- `AgentResultEnvelopeValidator.validateScopeEntries` 同步改为 path-only 校验；initial 与 revision 两处调用
  都删除 `operation is required`，但继续强制 path 非空。

Agent-facing JSON item 只能是：

```json
{"path":"README.md"}
```

`operation` 不再是可输入字段。strict Jackson unknown-field rule 必须拒绝仍携带 `operation` 的旧形状，避免
Agent 声明与 Dual projection 并存为两个 owner。

### 4.2 单一 operation resolver

新增 `TaskContractScopeResolver`，只处理 Agent 已声明的 exact paths：

1. path 必须非空、repository-relative、canonical，不能越出 workspace；
2. path 不能指向 directory；
3. `Files.exists(workspaceRoot.resolve(path)) == true` -> `MODIFY`；
4. 不存在 -> `CREATE`；
5. 返回 typed `List<TaskContract.ScopeEntry>` 或 finite validation errors；不改文件、不扫描目录、不调用 Git。

该 resolver 是 `ScopeOperation` projection 的唯一 production owner。`TaskContractProposalValidator` 继续验证
durable proposal 的 operation 与当前 workspace 一致，作为 cannot-bypass guard，不重新成为 proposal producer。

### 4.3 Initial 与 revision sibling closure

- `AgentResultDocumentProjector.taskContractProposal(...)` 接收 effective workspace，调用 resolver 后才生成
  durable proposal；
- `TaskContractProposalRevisionMerger.merge(...)` 接收 effective workspace，对每个 `scope_additions.path`
  调用同一 resolver，再合并 verified previous proposal；
- merger 的 scope-addition 去重键由 `(path, operation)` 收敛为 `path`；同一 revision patch 不能以不同
  operation 伪装成两个合法 addition；
- `ArtifactContractValidator` 在 initial/revision 两条路径上传入同一 `root`；resolver 失败继续产生 typed
  artifact contract violation；
- previous proposal 中已持久化的 scope entries 原样保留，revision 只解析新增 path，不能重算或改写历史
  operation。

### 4.4 Prompt projection

修改 `AgentResultPromptContract`：

- initial 与 revision 示例均只显示 `path`；
- 明确 Agent 负责 exhaustive path intent，Dual 负责根据 effective worktree 派生 `CREATE/MODIFY`；
- 删除“Agent 填写 uppercase CREATE/MODIFY”和 path-and-operation 的旧描述；
- 不加入 tracked-file 全量清单，不增加 token 随仓库规模增长的 prompt payload。

## 5. 明确不做

- 不把 entire tracked-files inventory 塞入 prompt：大仓库 token 成本无界，截断清单又会制造错误否定事实；
- 不扩展 `RepoScopeGovernance.repo-map` 扫描目录：该工件继续只表达 task contract / Git evidence 相关文件，
  禁止扫描后猜业务意义；
- 不自动纠正 Agent 已声明的错误 operation：Agent contract 中不再允许声明 operation；
- 不放宽 `TaskContractProposalValidator`；
- 不把 artifact validation 接入 bounded correction，不新增第二次 Agent 调用、retry 或 workflow edge；
- 不使用 filename heuristics、extension rules、自然语言或 Markdown 推断 operation；
- 不改变 durable `TaskContract` schema、change-scope enforcement 或 downstream contracts。

## 6. Required Cannot-Bypass Tests

1. **Captured initial boundary**：空项目只有 `REQUIREMENT.md`/`VERIFICATION-GATE.md` 时，Agent 声明
   `pom.xml`、`README.md` paths；projection 必须分别产生 `CREATE`，proposal mechanical validation PASS。
2. **Existing path**：Agent 声明已存在的 `REQUIREMENT.md`；projection 必须产生 `MODIFY`。
3. **Strict old-shape rejection**：Agent JSON 在 scope item 中加入 `operation`，strict parser 必须因 unknown
   field 拒绝，不能静默忽略。
4. **Invalid path fail-closed**：absolute、`../`、`./`、trailing slash、directory path 均不得形成 durable
   proposal。
5. **Initial sidecar proof**：真实 `ArtifactContractValidator` 链生成的 sidecar 必须包含 resolver 产生的
   operation；reviewer input ref 只能指向该 validated sidecar。
6. **Revision sibling proof**：`scope_additions:[{"path":"new.ext"}]` 经真实 merger 产生 `CREATE`；已存在 path
   产生 `MODIFY`；previous proposal scope entries 字节语义不变。
7. **No raw bypass**：全仓扫描 production，initial/patch Agent-facing records 不得再引用
   `TaskContract.ScopeEntry`；operation producer 只能是 `TaskContractScopeResolver`（durable deserialization 与
   test fixtures 豁免）。
8. **Downstream regression**：compiled TaskContract、RepoScopeGovernance write candidates、change-scope
   evaluation 继续消费 durable `ScopeEntry`，不感知 Agent-facing shape 变化。
9. **Durable guard**：resolver 正确派生后，若 operation 被篡改为与 filesystem 相反的值，现有 durable
   `TaskContractProposalValidator` 必须继续 fail closed，证明 validator 仍是 cannot-bypass verifier。

验证顺序：focused parser/prompt/projector/validator/revision tests -> affected runtime tests -> full Maven reactor ->
`git diff --check` -> independent code-level boundary review。deterministic PASS 只能标 `ENFORCED`；fresh
captcha-auth real-case 通过后才可标 `PROVEN`。

## 7. 预计修改文件

Production：

- `dual-runtime/.../AgentResultEnvelope.java`
- `dual-runtime/.../AgentResultEnvelopeValidator.java`
- `dual-runtime/.../AgentResultPromptContract.java`
- `dual-runtime/.../AgentResultDocumentProjector.java`
- `dual-runtime/.../TaskContractScopeResolver.java`（新增，小型 typed resolver）
- `dual-runtime/.../TaskContractProposalRevisionMerger.java`
- `dual-runtime/.../ArtifactContractValidator.java`

Tests：只修改与上述边界直接相关的 parser/prompt/projector/validator/merger/integration fixtures。大量仅作为
durable sidecar/TaskContract fixture 的 `operation` 不应机械删除。

不修改：workflow JSON、ContractRegistry decisions、Acpx transport/acquirer、correction、recovery、runner、
CLI/UI、Git adapter、RepoScopeGovernance semantics、change-scope evaluator。

## 8. Stop Conditions

遇到以下任一情况停止实现并回到设计：

- 需要全仓 inventory 或 prompt 截断策略才能工作；
- initial 与 revision 无法共用同一 resolver；
- 必须保留 Agent-provided operation 才能维持 downstream；
- 需要放宽 strict parser/validator 或新增 retry/correction；
- resolver 需要推断“业务相关文件”而不是解析已声明 exact paths；
- durable TaskContract schema 或 workflow graph 必须变化。

## 9. Maturity Boundary

- v9 captcha-auth：`FAIL / CONSUMED / PRODUCT NOT ACHIEVED`；
- self-test repair checkpoint `b2306882`：`ENFORCED / NOT PROVEN`；
- 本计划：`DRAFT / IMPLEMENTATION NOT AUTHORIZED`；
- CC review PASS 前不得改 production，implementation review PASS 前不得提交，新的 Verification Gate PASS 前
  不得调用 provider。
