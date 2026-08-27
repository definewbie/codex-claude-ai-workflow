# Initial Contract Fact Acquisition 修复计划

日期：2026-08-27  
状态：`IMPLEMENTED / DETERMINISTIC PASS / INDEPENDENT DIFF REVIEW PASS / READY TO COMMIT`

## 1. 目标

修复首次 `intake.claude_brainstorm` 的事实获取边界，使 Agent 在 fresh worktree、尚无 TaskContract、尚无 Git diff、
用户未提供文件路径时，仍能基于真实仓库证据生成精确且穷尽的 `TaskContract.scope`。

本计划优先保证正确性，再治理读取范围、token 和耗时。不得用降低 reviewer、validator 或 typed scope 严格性换取一次通过。

## 2. Captured 事实

唯一样本：`ws-20260827T142330Z-FX2Y`。

1. workspace preflight 的 capture/verify 均为 `passed`，source HEAD/tree/check SHA 未漂移；
2. Claude intake occurrence 完成，strict parser/schema/artifact validation PASS，`toolCallCount=0`；
3. intake prompt 明确禁止 `Bash/Grep/Glob/Read`；
4. Agent 获得的 `repo-map.files=[]`、`contractScopeCandidates=[]`、recommended reads 为空；
5. Claude 仍被要求输出精确、穷尽的 typed scope，因此只能按惯例推测路径；
6. Codex product review 以 `NEEDS_REVISION` 拒绝未经仓库事实核实的 scope，并要求确定性 Mailpit 邮件隔离；
7. Gate 的首次 semantic revision stop-loss 已触发；Dual 已创建 revision execution 并把 node 标成 `RUNNING`，但
   captured `run.json` 的 `command=[]`、`startedAt=null`、`processResult=null`、timeline 为空，未记录 PID、
   `process_started` 或 provider occurrence。stop-loss 后 stale reconciler 将这条 prepared execution 分类为
   `TIMEOUT`，workstream 已收口到 `WAITING_FOR_RECOVERY`，sessions 已关闭；
8. small-profile `8 calls / 0 revision` 为 `FAIL`，Mailpit 产品未完成。本样本不得重解释为模型格式失败或环境失败。

## 3. 根因与所有权

### 3.1 当前循环依赖

`RepoScopeGovernance.buildFileRelevancePlan()` 的 initial candidates 主要来自已有 TaskContract scope 和 Git changed files。
首次 intake 两者均为空；与此同时 prompt 禁止 Agent 获取新事实：

```text
TaskContract.scope 不存在
  -> contractScopeCandidates 为空
  -> Agent 禁止搜索/读取仓库
  -> Agent 仍须生成 TaskContract.scope
  -> reviewer 只能拒绝猜测
  -> revision 没有新增事实来源
```

设计文档曾声明 `contractScopeCandidates` 与 `seedEvidenceDigests` 会提供替代事实，但当前 production initial path
没有生成足以支撑 scope selection 的 payload。存在 projection 文件不等于事实闭环。

### 3.2 正确 owner

| 事实/决定 | Owner | 禁止承担的责任 |
|---|---|---|
| tracked path、存在性、文件字节、HEAD/tree | Git + effective workspace | 不判断业务相关性 |
| bounded inventory、允许读取范围、证据引用 | Context Builder / repo-scope governance | 不替 Agent 选择最终 scope |
| 哪些文件完成任务所必需 | contract-discovery Agent 的 proposal | 不伪造路径存在性 |
| 路径规范、存在性、shape、lineage | validator | 不自动纠错或扩 scope |
| scope 是否完整表达业务任务 | product reviewer | 不自行修改 proposal |
| 实施授权 | compiled TaskContract | 不从 Markdown/risk 推断额外路径 |

不新增第二个 TaskContract owner，也不让文件名启发式直接成为业务 scope authority。

## 4. 设计原则

1. **正确性优先**：成本预算只能约束获取方式，不能删除生成权威合同所需的事实来源；
2. **分层事实**：deterministic projection 提供仓库事实，Agent 负责业务相关性判断；
3. **bounded discovery**：允许 effective workspace 内只读搜索和精确读取，继续禁止写入、Terminal execute 与业务修改；
4. **fail closed**：机械边界违规不得进入 reviewer；scope 事实仍不足或存在猜测时，reviewer/human 不得接受，
   contract compile 与 business write 必须保持不可达；
5. **strict authority unchanged**：不放宽 parser、scope validator、lineage、reviewer 或 change-scope enforcement；
6. **先证明再优化**：先证明 cold-start contract 可满足，再评估是否减少 inventory、reads 或 token。

## 5. 候选方案

### A. 仅 deterministic inventory

Context Builder 从 effective workspace 的 Git tracked tree 生成路径 inventory，并在 context 中投影 bounded 路径。

- 优点：路径存在性权威、无 provider 工具依赖；
- 缺点：路径列表不能证明配置语义或是否需要修改某个主源码；大仓库完整内嵌有 token 风险；
- 判定：`INSUFFICIENT ALONE`。

### B. 仅恢复自由 read/search

删除 seed-only 禁令，恢复仓库探索。

- 优点：Agent 可获得语义事实；
- 缺点：会重新引入 cancer-clinic 中已观测的无界读取、耗时与 token 问题；
- 判定：`REJECT`。

### C. Git-backed inventory + bounded semantic discovery

1. Context Builder 从 persisted effective workspace 和 frozen Git identity 生成 authoritative path inventory；
2. prompt 只投影有界摘要与 inventory identity，不把完整大仓库内容无界塞入 prompt；
3. `contract_discovery` 允许 search 与 direct read，但继续禁止 terminal、execute、write/edit；
4. discovery 只用于确认 scope 所需的路径与最少配置语义，实际 ToolUsage 必须持久化；
5. 第一 checkpoint 的“bounded”只表示能力类型和 workspace 边界受限：只读/搜索、无 terminal、无 execute、无写入，
   并由 provider 既有 timeout/token 边界止损；当前 acpx 不提供可在每次工具调用前强制的 read-count budget，不能用
   post-run 数字阈值在事实已经读取后假装节省成本，也不能再次以成本上限删除正确性所需事实；
6. stale identity、越出 effective workspace、write/execute 或 adapter timeout 等机械违规走 typed execution failure；
   scope 业务完整性不足仍由 product reviewer/human authority 拒绝，revision occurrence 必须保留同一事实获取能力，
   不新增 initial `BLOCKED` artifact 或 workflow edge；
7. reviewer 与 compiled TaskContract 继续作为原有 authority，不接收 inventory 自动生成的 scope。

判定：`RECOMMENDED`。

## 6. 实施边界

以下代码事实已核实，并作为实现约束：

1. `AcpxToolUsage`、`AcpxTranscriptToolUsageReader` 与 `run.json` 已持久化 read/search 数量、工具名、读取路径和 direct-read
   路径；`ContextGovernanceActualReportWriter` 目前只投影实际值，没有 read-count 执行预算 owner。因此本 checkpoint 不新增
   一个 post-run hard count，也不把 `MAX_SCOPE_FILES=24` 的 context projection ceiling 冒充工具执行预算；
2. 本仓代码不能证明 acpx 0.11.0 中 permission policy 与 `--deny-all` 的优先级，因此本 checkpoint 不依赖该组合语义。
   `contract_discovery` 直接使用非 deny-all、terminal disabled 的 read/search profile；fake adapter 测试必须同时钉住
   不传 `--deny-all`、传 `--no-terminal`、无 write/execute approval、Read/Grep/Glob session option 和 read/search policy；
3. workflow 虽给 intake task 声明 `allowed_stage_outcomes=[READY,BLOCKED]`，`ContractRegistry.INTAKE_BRAINSTORM` 却只允许
   `READY_FOR_PRODUCT_REVIEW`，且 initial `ArtifactContractValidator` 无条件要求可验证的 `task_contract_proposal`。
   因而 initial semantic `BLOCKED` 当前不可表达。为避免把本轮扩大为 schema/graph checkpoint，本轮不新增该路径：机械边界
   违规走既有 typed execution failure；proposal 完整性继续由 reviewer/human 拒绝并阻止 compile/business write；
4. repo-map/file-relevance-plan 的 durable shape 可承载 inventory 文件项、identity 和 evidence refs，但 initial builder 当前只从
   TaskContract scope 与 changed-files 取候选。实现应由 effective workspace 的 frozen Git identity 生成 tracked path inventory，
   复用现有 artifact，不新增 artifact 类型；inventory 只证明路径存在，不选择最终 scope；
5. `contract_discovery` sibling 恰为 bundled full、contract、terminal-delivery 和 external small experiment 四个 intake 节点，
   必须统一消费同一 profile；`AgentCapabilityCatalog` 还显式禁止 intake direct read，必须与 adapter/profile 同步修改并加
   definition/catalog/adapter consistency test，不能只改 prompt 或 transport。

预期 production change surface 仅限：

- repo-scope/context fact projection；
- contract-discovery permission/exploration policy；
- `AgentCapabilityCatalog` 的同一 capability projection 与 sibling consistency；
- intake prompt 的事实获取纪律；
- 既有 ToolUsage/context-governance actual evidence 的读取路径与 SHA 投影；
- 直接测试与 sibling consistency tests。

禁止：新 workflow phase/node、第二 scope mapper、目录名业务推断、自动扩 scope、parser fallback、reviewer 放宽、
provider retry、针对 Mailpit 的路径白名单、通用索引服务或新 module/framework。

## 7. End-to-End Invariant

| 维度 | 必须证明 |
|---|---|
| Declared by | contract-discovery stage policy 声明允许的事实来源、只读能力和边界 |
| Proven by | frozen Git identity、tracked inventory、实际 read/search ToolUsage 与读取文件 SHA |
| Consumed by | 当 tracked inventory 非空时，initial/revision proposal 的既有 `evidence_refs` 必须至少有一项可追溯到该 inventory；reviewer 再消费 proposal、inventory 与实际读取 evidence 判断 exact scope 是否完整 |
| Enforced before | reviewer 接受 proposal、contract compile 和任何 business write 之前 |
| Failure route | stale identity、越界、write/execute、timeout 等机械违规 typed FAILED；语义 scope 不完整由 reviewer/human 拒绝，不能 compile 或 business write |
| Cannot-bypass | cold-start、large-repo projection bounded、stale identity、forged path、forbidden tool use、sibling workflow tests |
| Residual | Agent 仍负责业务 relevance；人/Reviewer 仍可能合法要求 semantic revision，不承诺所有任务零 revision |

## 8. Deterministic Tests

至少覆盖：

1. fresh repo、无 contract、无 Git diff、需求未列文件路径，仍能获得 non-empty authoritative inventory；
2. Agent-facing context 不把 inventory 冒充最终 scope，也不允许不存在路径通过 validator；
3. contract discovery 可搜索并读取 workspace 内候选，但不能 terminal/execute/write/edit；adapter 参数测试必须证明
   command 不依赖 `--deny-all` 与 permission policy 的未公开优先级，也不能以 captured `toolCallCount=0` 推断权限语义；
4. 实际读取及 SHA 进入 execution evidence，proposal scope 可追溯到 inventory/read evidence；
5. stale HEAD/tree、workspace 越界、write/execute、缺 evidence、伪造路径均在 review/compile/business write 前拒绝；
6. full/contract/terminal-delivery/external small workflow sibling 行为一致；
7. 构造 Mailpit 类 cold-start fixture，scope 至少能依据真实仓库区分配置文件、已有 SMTP 实现和测试位置；
8. 删除 inventory 或重新禁止全部 fact acquisition 时 cannot-bypass 测试必须失败；
9. `AgentCapabilityCatalog`、四个 sibling definitions、adapter command 与 prompt 对 read/search 能力的声明一致；
10. full reactor 与 `git diff --check` PASS。

测试只证明事实链与不可绕过性，不把某个模型一次返回 READY 当作 deterministic PASS 条件。

## 9. 成熟度与后续 Gate

- 当前：`DEFECT PROVEN BY REAL-CASE / FIX NOT MODELED`；
- deterministic checkpoint 通过后：`FACT ACQUISITION ENFORCED / REAL-CASE NOT PROVEN`；
- 必须经新的 cold-start real-case 自然生成 evidence-backed scope，才可标 `PROVEN`；
- 当前 Mailpit workstream 保留在 recovery 作为证据，不 resume、不创建第二样本，直到设计与实现 review 完成并作出新的
  verification 决策。

## 10. 实施顺序

1. 对本次事实核实 delta 做一次独立架构复审，重点检查“不新增 initial BLOCKED、无数字 read hard cap、四层 capability
   一致性”是否仍能保证 fail-closed；
2. 关闭 P0/P1 后，实施单一原子 checkpoint；
3. focused tests、full reactor、diff-check；
4. 独立代码级 diff review；
5. 再制定一次最小 cold-start Verification Gate，决定恢复 captured workstream 还是创建 fresh sample。

## 11. 实施结果

- Git tracked-path inventory 由 effective worktree 的 `HEAD` 与 `git ls-files -z` 生成，repo-map 持久化 count、SHA 与路径；
- artifact validation 前重算 inventory identity，HEAD/path-set 漂移 fail-closed；
- `contract_discovery` 使用 terminal-disabled、无 write/execute approval 的明确 Read/Grep/Glob profile，不依赖
  `--deny-all` 与 permission policy 的未公开优先级；capability catalog、prompt 与 adapter 参数保持一致；
- 非空 inventory 下，proposal 的 `evidence_refs` 至少一项必须命中真实 tracked file；其他既有 durable evidence refs 保持合法；
- 实际读取路径、SHA-256 与字节数进入 context-governance actual evidence；inventory 只证明存在性，仍不替 Agent 选择 scope；
- focused runtime + adapter：`116/0/0/0`；revision/compile 补充切片：`41/0/0/0`；
- 非沙箱 full Maven reactor：9/9 modules，`1246/0/0/0`；`git diff --check` clean；
- 一次 full run 的既有 reconnect 时序断言因系统调度耗时 12.81 秒失败，未修改该测试；同一条单独复跑 10.02 秒 PASS，
  随后的完整 reactor 中该类 `33/0/0/0`，不计为本 checkpoint 回归。

当前成熟度：`FACT ACQUISITION ENFORCED / REAL-CASE NOT PROVEN`。独立代码级 diff review 已
`PASS（P0=0 / P1=0 / P2=2 知会）`，并独立复现 focused `180/0/0/0`、full reactor
`1246/0/0/0` 与 clean diff-check。checkpoint 满足选择性提交条件；提交后只制定新的 cold-start Gate，不直接调用 provider。
