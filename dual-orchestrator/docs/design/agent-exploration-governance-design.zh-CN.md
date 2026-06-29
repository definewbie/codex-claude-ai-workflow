# Agent 探索治理设计：让 intake 停止实现级仓库探索

> 状态：P0/P1 已实现；P2 intake BLOCK enforcement 已启用；review 仍保持 WARN。
> 触发：cancer-clinic real-case 中 `intake.claude_brainstorm` 用 293s/380s 做了接近实现级的仓库探索，读取大量 docs/source/API 文件，导致 token 和耗时失控。
> 目标：不靠 prompt 硬喊“少读文件”，而是由 orchestrator 显式管理阶段探索意图、可用能力和实际读写边界。

## 0. 结论

之前的 memory / read-reuse 没守住 intake，是因为它只解决了 **“读过什么，能不能复用”**，没有解决 **“这个阶段是否应该探索、应该用什么方式探索、探索到什么程度必须停”**。

现在缺的是一个更上层的边界对象：

> `ExplorationPlan`：orchestrator 为每个 agent run 生成的阶段探索计划，声明本阶段的探索目标、首选能力、允许读取证据、预算、越界处理和升级路径。

同时需要一个能力抽象：

> `AgentCapabilityCatalog`：把 codegraph、rtk、grep、read、test runner 等外部能力归一化成能力类别，而不是在 prompt / policy 里散落工具名。

推荐方案是 **方案 B：Capability-aware ExplorationPlan + harness 校验**。它比继续堆 memory 更接近根因，也比一次性自建完整代码索引风险低。

---

## 1. 为什么之前没守住

### 1.1 已经做过的事

现有系统已经实现：

- `WorkstreamMemory`：记录 decidedScope / nonGoals / readFiles / priorVerdicts / openIssues。
- `AgentHandoff` / `ReviewEvidenceBrief`：从 memory 派生给 Claude / Codex。
- `ReadReuseContract`：提示 agent 复用已读文件，并要求重读 reusable 文件给出 typed reason。
- `stage_scope_policy`：包含 `readScope` / `writeScope` / `explorationBudget` / `scopeExceedPolicy`。

这些机制解决的是：

- 跨 run 记忆；
- 已读文件复用；
- 重读理由；
- 部分 read/write scope 观测。

### 1.2 没守住的原因

它们没有回答以下问题：

| 问题 | 当前状态 | 后果 |
|---|---|---|
| intake 阶段到底允许探索什么？ | 没有 typed 阶段探索意图 | intake 被 agent 理解成“先读仓库搞清楚实现路径” |
| 应该先用索引 / codegraph / summary，还是直接 Read 文件？ | 没有 capability routing | LLM 自由选择 Read，按经验读 docs/source/API |
| 读到多少应该停？ | `extraReadFiles` 是 passive guidance | 不能阻断实现级探索 |
| 缺证据时该怎么办？ | prompt 要求“说明原因” | agent 会继续探索，而不是输出 scope expansion request |
| Codex 审查应全量还是增量？ | 有 ReviewEvidenceBrief，但无 exploration policy | Codex 仍可能全量读上下文 |

关键点：**memory 是“已知事实”的 SoT，不是“允许探索行为”的 SoT。**
把探索边界放进 memory/read-reuse 里，本质上是用下游记忆机制补上游行为策略，所以守不住。

### 1.3 现有代码事实（复核）

`RepoScopeGovernance` 里存在三条和 intake 探索边界相关的事实：

1. Agent-facing guidance 明确声明 repo scope guidance 是 **passive harness guardrail**：
   `RepoScopeGovernance.appendMarkdownGuidance(...)` 写入：
   `"This section is a passive harness guardrail. It guides repository exploration but does not replace task contract, artifact validation, or human approval."`
2. `stageAllowsBroadExploration(stageId)` 对 `intake/design/plan` 返回 `true`。
   该值用于 `explorationBudget.extraReadFiles`：宽探索阶段为 `12`，其他阶段为 `6`。
3. `scopeExceedPolicy(stageId)` 对除 `delivery/implement/self_test` 外的阶段返回 `record_only`。
   因此 `intake` 的 scope exceed policy 是 `record_only`。

需要精确表述：这三条并不等于代码显式授权 agent 读取 16 个文件或进行实现级探索；它们说明的是 **当前治理层对 intake 的探索约束是被动、较宽、且越界只记录不阻断**。所以 cancer-clinic 中 intake 走向实现级探索，不是某个单点 if-else bug，而是现有模型缺少“阶段探索意图 / 能力路由 / 可执行阻断”的直接后果。

---

## 2. 边界模型

### 2.1 对象归属

| 对象 | 边界 | SoT | 说明 |
|---|---|---|---|
| Workflow Definition | Definition | 是 | 声明阶段、节点、profile、artifact contract |
| Stage Exploration Policy | Definition | 是 | 每个阶段允许的探索意图、预算、越界策略 |
| AgentCapabilityCatalog | Definition / Environment Projection | 是，按运行环境生成 | 当前 agent 可用能力的归一化视图 |
| ExplorationPlan | Execution input artifact | 是，per run | 本次 agent run 可执行的探索计划 |
| WorkstreamMemory | Instance artifact | 是 | 当前 workstream 已知事实 |
| ReadReuseContract | Projection | 否 | 从 memory 派生的重读约束 |
| ToolUsage | Execution fact | 是 | agent 实际读写/执行了什么 |
| ExplorationPolicyResult | Execution validation result | 是 | actual tool usage 是否符合 ExplorationPlan |

### 2.2 不再混淆的边界

- `WorkstreamMemory` 管 **记住什么**。
- `ExplorationPlan` 管 **这一步允许如何发现新事实**。
- `AgentCapabilityCatalog` 管 **当前有哪些合理能力可用**。
- `ToolUsage` 管 **agent 实际做了什么**。
- `ArtifactContractValidator` / 新 validator 管 **实际行为是否符合计划**。

---

## 3. 方案对比

### 方案 A：Prompt-only 约束 intake 少读

做法：

- 在 intake / review prompt 里加强文字：
  - 不要实现级探索；
  - 优先用 memory / seed；
  - 缺证据就请求扩展；
  - 如果有 codegraph/rtk skill，优先使用。

收益：

- 实现最快；
- 风险最低；
- 可以作为过渡文案。

问题：

- 仍然依赖 LLM 记住和遵守；
- 无法解释为什么某次多读；
- 无法自动判定违反策略；
- 不能解决 “LLM 自由发挥选工具” 的根因；
- 容易继续演变成 prompt if-else。

结论：**不作为主方案。只能作为方案 B/C 的 agent-facing projection。**

---

### 方案 B：Capability-aware ExplorationPlan + harness 校验（推荐）

做法：

新增两个 typed artifact / policy：

1. `agent-capability-catalog.json`
2. `exploration-plan-<node>.json`

#### AgentCapabilityCatalog

将当前环境中可用能力归一化为能力类别，而不是在 policy 中写死工具名。

示例：

```json
{
  "schemaVersion": 1,
  "capabilities": [
    {
      "id": "code_symbol_index",
      "kind": "symbol_index",
      "provider": "codegraph",
      "available": true,
      "preferredFor": ["locate_component", "find_callers", "dependency_trace"]
    },
    {
      "id": "token_filtered_shell",
      "kind": "token_filtered_shell",
      "provider": "rtk",
      "available": true,
      "preferredFor": ["file_listing", "grep", "bounded_read"]
    },
    {
      "id": "direct_file_read",
      "kind": "direct_file_read",
      "provider": "Read",
      "available": true,
      "preferredFor": ["exact_content_before_edit"]
    }
  ]
}
```

注意：

- `provider=codegraph/rtk/Read` 是环境事实，不是业务 policy。
- policy 使用 `kind`，例如 `symbol_index`、`token_filtered_shell`、`direct_file_read`。
- 如果某环境没有 codegraph，就不会生成对应 capability；agent-facing 文案只展示实际可用能力。

#### ExplorationPlan

每个 agent run 前由 context builder 生成：

```json
{
  "schemaVersion": 1,
  "stage": "intake",
  "nodeId": "intake.claude_brainstorm",
  "explorationIntent": "contract_discovery",
  "primaryQuestions": [
    "用户目标是否清楚？",
    "候选业务范围是否能形成 task contract？",
    "是否需要向人请求范围扩展？"
  ],
  "preferredCapabilityKinds": [
    "symbol_index",
    "token_filtered_shell"
  ],
  "directReadPolicy": {
    "allowedWhen": [
      "seed file listed by orchestrator",
      "exact current content needed to validate contract boundary"
    ],
    "maxDirectReads": 3
  },
  "forbiddenIntent": [
    "implementation_design",
    "full_repo_audit",
    "test_strategy_design"
  ],
  "scopeExpansion": {
    "artifactField": "agent_output_contract.scope_expansion_request",
    "requiredWhen": "required evidence is missing from seed/memory/capability index"
  },
  "failurePolicy": "block_on_unjustified_direct_reads"
}
```

#### 阶段语义

| 阶段 | explorationIntent | 允许探索 | 不允许 |
|---|---|---|---|
| intake | `contract_discovery` | seed / memory / symbol index / 少量边界验证 | 实现设计、全量 API 阅读、测试方案 |
| design | `architecture_discovery` | 目标模块结构、依赖、接口边界 | 修改代码、跑实现级验证 |
| plan | `change_planning` | 已选方案涉及的文件、风险、验证路径 | 重新做需求发现 |
| implement | `edit_execution` | 编辑文件前精确读取、局部依赖 | 扩展需求范围 |
| acceptance | `incremental_review` | diff、must-fix、changed files | 全量仓库审计 |

#### Harness 校验

run 完成后，根据 `ToolUsage` 与 `ExplorationPlan` 生成 `ExplorationPolicyResult`：

- direct Read 超过预算；
- Read 不在 seed / readScope / changedFiles / scopeExpansion 中；
- intake 出现实现级大量 source/API 读取；
- 没有使用可用的 preferred capability，直接大范围 Read；
- 缺证据但没有 `scope_expansion_request`。

这些不直接靠自然语言判断，而是由：

- actual `toolUsage.readFiles`
- `exploration-plan.json`
- `agent-capability-catalog.json`
- typed `agent_output_contract`

计算。

收益：

- 直接解决 intake 失控；
- 不硬编码 codegraph/rtk，而是 capability-driven；
- 可解释每次 agent 为什么越界；
- 可用作 stop-loss；
- 与现有 WorkstreamMemory / ReadReuseContract 互补。

风险：

- 初期可能误杀合法探索；
- Capability discovery 需要谨慎，不应依赖不稳定命令输出；
- 校验策略需要分阶段 rollout：先 WARN，再 BLOCK。

适合当前阶段：**是。**

---

### 方案 C：Orchestrator-owned 代码索引 / Graph Summary 先行

做法：

- orchestrator 自己在 context build 前构建 repo index：
  - symbol map；
  - route map；
  - API surface map；
  - dependency/caller summary；
  - changed file neighborhood；
  - module summaries。
- agent 先消费 index，只有 index 不足时才读文件。
- codegraph/rtk 变成 orchestrator 自己的前置能力，而不是 agent 自己调用。

收益：

- 最强控制；
- token 最可控；
- 对 agent 行为依赖最小；
- 大项目收益最大。

风险：

- 实现成本高；
- 需要维护索引正确性；
- 容易变成自建 GraphRAG/代码平台；
- 如果索引质量不足，会降低任务准确性；
- 当前 dual 还没稳定 execution attempt / terminal outcome，过早做会扩大复杂度。

适合当前阶段：**不作为第一步。可以作为 B 的后续增强。**

---

## 4. 推荐路线

推荐采用 **方案 B**，分三步。

### P0：设计和 projection，不阻断

目标：先让 agent 看见探索计划和能力目录，但不改变结果。

改动边界：

- 新增 `AgentCapabilityCatalog` 生成器。
- 新增 `ExplorationPlan` 生成器。
- `ContextBuildPackageHandler` 注入 `## Exploration Plan` section。
- `PromptManifest` 增加 `sourceType=exploration_plan`。
- metrics/trace 显示本 run 的 exploration plan ref。

验收：

- intake prompt 中出现 contract discovery intent；
- codegraph/rtk 如果可用，会以 capability 形式出现；
- 没有 hardcoded “必须使用 codegraph” 文案；
- run 行为不变。

### P1：校验 actual tool usage，先 WARN

目标：证明能识别失控探索。

改动边界：

- 新增 `ExplorationPolicyEvaluator`。
- 输入：
  - `exploration-plan.json`
  - `agent-capability-catalog.json`
  - `run.json.toolUsage`
  - `agent_output_contract.scope_expansion_request`
- 输出：
  - `exploration-policy-result.json`
  - run.json `explorationPolicyResult`
  - metrics `explorationViolations`

先不 fail workflow，只 WARN。

验收：

- cancer-clinic 当前这种 intake 大量读 docs/source/API，应被标为 `IMPLEMENTATION_LEVEL_EXPLORATION_IN_INTAKE` 或等价 typed violation。
- 合理少量 seed read 不报警。
- implement 编辑前 direct Read 不报警。

### P2：对 intake / review 逐步 BLOCK

目标：真正守住边界。

策略：

- intake：超过 direct read budget 且无 `scope_expansion_request` → contract failure。
- codex review：未先使用 incremental review evidence、直接全量读多个 source/docs → warning；稳定后 block。
- implement：仅对非 changed_files / 非 invalidated / 非 scope expansion 的 reusable reread 阻断，保留编辑前精确读。

验收：

- simple semantic real-case 能闭环；
- cancer-clinic intake 不再做实现级探索；
- 如果证据不足，agent 输出 scope expansion request，而不是继续读仓库；
- token/read/toolcall 明显下降，质量不退化。

---

## 5. 为什么不是继续改 memory

memory/read-reuse 仍然必要，但它是下游机制：

- memory 负责“你已经知道什么”；
- read-reuse 负责“已读内容能否复用”；
- exploration plan 负责“这一步允许你如何发现新东西”。

当前问题发生在 intake 的首次探索阶段：它读的是很多“还没进入 memory 的新文件”。
所以继续强化 read-reuse 不会阻止首次大范围探索。

---

## 6. 如何“合理触发 skill”，而不是 hardcode

不在 prompt 里写：

> 你必须使用 codegraph。

而是：

1. orchestrator 生成 capability catalog：
   - `symbol_index` capability 可用，provider 是 codegraph；
   - `token_filtered_shell` capability 可用，provider 是 rtk；
   - `direct_file_read` capability 可用，provider 是 Read。
2. exploration plan 声明：
   - intake 首选 `symbol_index` / `token_filtered_shell`；
   - direct file read 只用于 seed 或精确边界验证。
3. agent-facing projection 展示：
   - “For contract discovery, use available `symbol_index` capability before direct file reads.”
   - 如果 provider 是 codegraph，则展示具体入口名；如果不是，则展示可用替代能力。
4. harness 在 run 后评价：
   - 如果 direct Read 大量发生且没有 capability 尝试或 scope expansion，记录/阻断。

这样触发 skill 的方式是 **capability-driven**，不是 if-else prompt。

---

## 7. 预期收益

基于 cancer-clinic 本次事实：

- 第一轮 Claude intake 读了 16 个文件，包含 docs/specs、API、页面、backend；
- 产生 `616k` tokens；
- 第二轮 intake 仍有 `707k` tokens；
- 问题集中在“contract discovery 阶段做了 implementation discovery”。

如果方案 B 生效，预期：

| 指标 | 预期 |
|---|---|
| intake direct Read 文件数 | 从 16 降到 3-5 |
| intake tool calls | 降 40%-70% |
| intake elapsed time | 降 30%-60%，取决于 provider 推理速度 |
| cachedRead token | 不一定同比下降，因为 provider cache 仍可能命中，但 transcript 增长会减缓 |
| 质量 | 不应下降；缺证据时转 scope expansion，而不是少审 |

重要：收益不是靠“少看证据”，而是靠 **先用索引/seed 判断范围，缺证据时结构化请求扩展**。

---

## 8. 风险和边界

### 风险 1：过度限制 intake 导致合同质量下降

缓解：

- P1 先 WARN；
- P2 只 block 明显实现级探索；
- 缺证据允许 `scope_expansion_request`，由 human/Codex 决定是否扩展。

### 风险 2：capability catalog 误判能力可用

缓解：

- catalog 是 best-effort environment projection；
- capability unavailable 时 plan 退化到 token-filtered search / bounded read；
- 不让 workflow routing 依赖 capability 名称。

### 风险 3：LLM 仍不使用推荐能力

缓解：

- 先评价 actual tool usage；
- 再逐步 block；
- 不靠 prompt 自觉。

### 风险 4：设计膨胀成 GraphRAG

缓解：

- 本阶段不做向量、不做跨项目、不做长期知识库；
- capability catalog 只描述可用工具，不存检索结果；
- ExplorationPlan 是 run-local execution input。

---

## 9. 验证方案

### 单测

- capability catalog 生成：
  - 有 codegraph/rtk → 归一化到 capability kind；
  - 无 codegraph → 不生成 symbol_index，plan 有 fallback。
- exploration plan：
  - intake intent = `contract_discovery`；
  - implement intent = `edit_execution`；
  - acceptance intent = `incremental_review`。
- evaluator：
  - intake 读 16 个 source/docs 且无 scope expansion → violation；
  - intake 只读 seed + capability search → pass；
  - implement 读 changed_files → pass；
  - codex review 全量读多个 source 且无 diff reason → warning。

### real-case

1. simple semantic case：验证不会误杀。
2. cancer-clinic same task：
   - stop-loss：intake direct Read > 5 且无 scope expansion → stop；
   - stop-loss：GLM token > 3M before acceptance → stop；
   - 验证：intake 不再读 16 个文件；如果证据不足，输出 scope expansion request。

### 观测

- `explorationPolicyResult`
- `toolMetrics.readCount`
- `tokenAttribution.nodeHotspots`
- `promptManifest.sections[sourceType=exploration_plan]`
- `agent-capability-catalog.json`
- `exploration-plan-<node>.json`

---

## 10. Boundary Review

**Boundary Review: PASS for P2 intake BLOCK；review BLOCK 仍为 WARN rollout。**

Touched boundaries:

- Definition：Stage Exploration Policy
- Execution input：ExplorationPlan
- Environment projection：AgentCapabilityCatalog
- Execution fact：ToolUsage
- Artifact contract：scope expansion request
- Projection：metrics / trace / prompt manifest

Source of truth:

- 阶段允许探索什么：Stage Exploration Policy
- 当前可用能力：AgentCapabilityCatalog
- 本次 run 探索计划：ExplorationPlan
- 实际行为：ToolUsage
- 越界判定：ExplorationPolicyResult

Required fixes before enforcement:

1. 先做 P0/P1，证明 evaluator 能识别真实 cancer-clinic intake 失控。
2. P2 block 前必须有误杀回归测试。
3. `scope_expansion_request` 必须 typed，不允许从自然语言理由推断。

Accepted debt:

- P0/P1 阶段只 WARN，不 BLOCK。这是为了避免在策略未校准前误杀合法探索。

---

## 11. 推荐决策

推荐：**PROCEED_WITH_GUARDS**

执行顺序：

1. P0：能力目录 + 探索计划 projection。
2. P1：tool usage evaluator，先 WARN。
3. 用 cancer-clinic 已失败样本回放验证 evaluator。
4. P2：只对 intake 明显实现级探索启用 BLOCK。

不推荐：

- 继续只改 memory/read-reuse；
- 直接切 session；
- 在 prompt 里硬写 codegraph/rtk；
- 自建完整代码图谱作为第一步。

## 12. 实现状态（2026-06-29）

已实现：

- P0：`AgentCapabilityCatalog` sidecar、`ExplorationPlan` sidecar、agent-facing `## Exploration Plan` projection、prompt manifest `sourceType=exploration_plan`。
- P1：`ExplorationPolicyEvaluator`、execution actual report、`exploration-policy-result-*.json` sidecar、`run.json.explorationPolicyResult`、metrics/trace violation 投影。
- P1 capability usage：actual usage 只从原始/native tool id 或 command executable 事实匹配 capability provider；不得扫描整条 Bash 命令参数，否则 `grep codegraph docs`、`echo rtk` 这类文本会被误判为能力使用。
- P1 provider attribution：`byTool` 是分类后的行为统计，不能单独证明 provider；例如 `rtk read` 可以统计为 read 行为，但不能证明 native `Read` capability。`ExplorationPolicyResult`、actual report、metrics 和 trace 必须保留 `actualCapabilityUsages={kind,provider,source}`，避免只保留 kind 后丢失 provider 证据。
- P1 scope expansion attribution：outside-plan read 的 waiver 只能来自已验证的 `agent_output_contract` sidecar；raw stdout、transcript 或 agent 正文里的 `scope_expansion_request` 不是 SoT。若 actual report 写入时 sidecar 尚未生成，则本次 report 只能作为 provisional projection，不放行该读取；`ArtifactContractValidator` 写入 validated sidecar 后必须用该 sidecar/read model 回刷 actual report、`exploration-policy-result-*.json` 和 `run.json.explorationPolicyResult`，不能从自然语言补判。
- 小预算 context profile 使用 compact governance projection；完整 sidecar 仍然是 source of truth，compact projection 只保留必要 refs 和探索约束。

已启用：

- P2 intake BLOCK：`ExplorationPlan.failureMode=intake:block`；`ArtifactContractValidator` 在 validated `agent_output_contract` sidecar 可用后回刷 actual report / result / run.json，并将 `status=failed` 的 exploration policy result 转为 contract failure。放行依据只来自 typed `scope_expansion_request.requested_paths`，不能从 stdout/transcript/prose 推断。
- P2 误杀保护：`actualDirectReadFiles=[]` 必须保持为空，不得 fallback 到 `actualReadFiles`；token-filtered search/list 不计入 direct read；scope expansion 只放行请求的 bounded files，不是全局 waiver。
- P2 execution permission：`intake.claude_brainstorm` 在 bundled `full` / `contract` workflow 中使用 `permission_profile=contract_discovery`。该 profile 在 acpx adapter 层只允许 `Grep,Glob`，只 auto-approve ACP `search`，不允许 direct `Read`。这是执行前边界，用于阻止单个长 run 在后置 validator 生效前做实现级文件读取。

尚未启用：

- P2 review BLOCK：codex review 全量读多个 source/docs 仍保持 WARN，待 real-case 校准后再切。
- `repo-map.kind` 目前是集中式 file-kind policy，可支撑 intake BLOCK 的 source/test 识别；继续不能把它描述成完整语义代码索引。
