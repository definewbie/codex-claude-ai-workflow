# Dual Orchestrator 架构评估与简化方案 · 2026-11-25

状态：评估与设计提案，未实施，未获批准。基线为 HEAD `189e41ab`。
标注约定：**[事实]** 可由仓库命令复核；**[判断]** 为本文作者的分析；**[待证]** 需要后续验证。

## 0. 结论

项目的核心骨架（Definition → Instance → Execution、文件账本、人工审批点、执行事实归因）是对的，规模也不大，`dual-core` 只有约 3.5k 行。问题出在骨架之上的领域层：

1. 流程语义没有抽象成少量角色，而是按节点 ID 逐个写死。每个节点都有专属的提示词条款、校验器、恢复分支和身份链，所以边界问题无法泛化，修一个点就会在兄弟节点再出现一次。
2. 身份由模型抄写、程序核对。这一责任放反了。程序已经知道的 ID、SHA、条款原文，被要求模型逐字回写，偏一个字符就判失败。
3. 为节省 token，系统把 agent 做成每节点无状态，并把上下文压缩到 3k–6k token。这样省下的上下文，又要靠一整套上下文重建、记忆投影、读复用合同、谱系解析和交接文档补回来。这套机制本身成了失败源。
4. 判定标准没有分级。形状错误、转写错误、质量问题与授权违规用同一种方式处理，都会拒绝并停在人工恢复，导致流程无法自然流动。

简化方向：**程序拥有身份，模型只产出语义；节点按角色泛化；校验分三级；上下文按需充足，用整体预算止损，不逐段限流；授权只有一个模型。**目标是在不放松授权、账本完整性和执行事实这三条底线的前提下，删掉大约一半的领域代码，并让自然端到端运行能跑通。

## 1. 现状事实

| 维度 | 数据 |
|---|---|
| 生产代码 | 约 61k 行 Java。`runtime/handler` 一个包 89 个类、22.2k 行；`adapter/acpx` 11.4k 行；`ledger/file` 10.8k 行；`dual-core` 3.5k 行 |
| 测试 | 212 个测试类，约 55k 行，基线 1,834 项 |
| 文档 | `docs/` 下 662 个文件、约 10.7 万行，多于生产代码；活动计划 3,242 行；验证门文档 77 份 |
| full 流程 | 34 个节点、61 条边。顺利路径上有 10 次 agent 调用、6 个 `context.*` 构建节点、5 个 DECISION 节点和 3 个必经人工审批点 |
| 节点 ID 写死 | Java 中出现 195 处节点 ID 字面量，分布在 25 个文件里。其中 `ContractRegistry` 34 处，`TaskContractRevisionContextResolver` 27 处 |
| 拒绝码 | 398 个不同的大写错误/状态码字面量，953 处 `throw` |
| 上下文预算 | `ContextProfiles`：intake 3,000 token，实现 6,000 token，产品评审 4,000 token，架构评审 5,000 token；人工反馈只保留最近 3 到 5 条 |
| 会话 | 每个 agent 节点使用 run 级会话，不跨节点保留记忆（README） |
| 权限 | intake 仅 Read/Grep/Glob；设计和计划用 `observe`；Codex 评审和验收用 `review`，只自动批准读和搜索，不能执行测试；只有实现使用 `implement` |
| 交付成果 | 2026-09 的 6 个 OKX 自然样本全部 FAIL 或 STOP，至今没有自然 E2E PASS |

真实样本失败点和对应的结构性根因：

| 样本 | 失败点 | 根因类别 |
|---|---|---|
| GCTW | 人工备注 ID 的 SHA 段为 65 位 | 模型转写身份 |
| KPPZ | 设计只列了 34 个批准路径中的 8 个，外加一个占位文件 | 精确范围要求模型完整复述 |
| 7SNA | 验收项的方法正文是登记内容的前缀加追加说明，被逐字相等规则拒绝 | 精确相等判定 |
| 6NX5 | 合法人工修订被上下文资格规则拒绝，恢复诊断又误判为模型没返回 JSON | 规则没有分级，恢复分类依赖节点特例 |
| 245D / BXWV | 验收方不能执行，外部证据续作链缺失或路由回到实现 | 验收方无执行权，靠证据搬运机制补位 |
| 6KVE | intake 权限边界止损 | 为省 token 收窄权限 |

## 2. 架构缺陷

### D1 流程定义不是真正的 source of truth（抽象层级错误）

**[事实]** JSON 只描述了节点、边和配置键。节点语义写在 Java 里，靠节点 ID 分派，例如 `AgentResultPromptContract` 中的 `if ("intake.claude_brainstorm".equals(nodeId))`，以及 `ContractRegistry.forNode(nodeId)`。

**[判断]** 结构上反复出现的是同一个模式：**作者 → 评审 → 决定（→ 人工）**。intake、design、plan、acceptance 都是它。但每个阶段的输出合同、修订规则、谱系解析和恢复路径都是单独写的。后果有三：

- 同一类问题（修订响应、身份引用、范围核对）在每个阶段各有一份实现，修一处漏另一处，`Sibling Boundary Sweep` 只能靠人工纪律弥补；
- 新增或调整阶段必须改 Java，JSON 定义名不副实；
- `runtime/handler` 成了大杂烩，89 个类没有子领域边界。

### D2 身份责任倒置（失败的头号来源）

**[事实]**

- 模型的输出信封要求回写 `schema_version`、`workstream_id`、`run_id`、`node_id`（`AgentResultEnvelopeValidator`）。
- 修订时，模型要"逐字符复制"未修改条款的 `id` 和 `text`，逐个复制 obligation ID，人工备注 ID 的形式是 `sha256:<64位>#human-note:N`（`AgentResultPromptContract`）。
- 系统专门有一条 correction 路径，在模型写错 `SCHEMA_VERSION / WORKSTREAM_ID / RUN_ID / NODE_ID` 时让它重写。

这些值程序本来就知道。

**[判断]** 让模型充当转写器，再由程序核对转写结果，在概率模型上必然产生长尾失败。为此又建了 correction、lineage、binding、projection verifier 等补偿层（`TaskContractRevisionContextResolver`、`TaskContractProjectionVerifier`、`AuthoredStageLineageResolver`、`AgentOutputLineageVerifier`、`ContractRevisionBinding`、`RepositoryFactBinding` 等，合计数千行）。身份正确性是程序的职责，不应以模型输出为前提。

### D3 授权和审批没有统一模型

**[事实]** 现在至少并存这几套绑定方式：approve 用 `expectedGate`，revise 用 `subject`，request-action 用 `gateNodeInstanceId`，补证据用 baseline，网络和 WebSearch 授权各有 grant 存储，autorun 代批另有路径。另外还有 GOAL 批准、EXECUTION 批准、交付批准等不同凭据。

**[判断]** 缺少一个统一的"决定（Decision）"对象，即"谁、对哪个对象、做了什么、凭什么权限"，以及一个统一的"授权消费"接口。每次事故补一种绑定，兄弟入口就会漏。本次审批绑定缺陷（编号跨任务撞号、null 等于任意审批点、网页在点击时才读状态）就是直接结果。

### D4 合同与范围模型过度精确

**[事实]**

- TaskContract 分 GOAL 和 EXECUTION 两个阶段，配套 proposal、revision patch、obligation、coverage、merger、projection verifier、qualification checks 等，约 5k 行。
- EXECUTION 批准要求精确到文件路径和操作；设计和计划必须完整声明 `planned_files`；实现越界时进入人工修复审批点。
- `gate.human_design_approval` 和 `gate.human_plan_approval` 上，批准和打回都回到作者节点。

**[判断]** 真实开发中，文件集是在实现过程中才逐渐明确的。在实现前要求模型精确复述并锁定文件集，既容易出现 KPPZ 式失败，也会让正常演进都变成需要人工的越界事件。真正需要硬性管住的只有：禁止路径（`.git`、`.agent`、密钥）、显式排除项，以及最终交付时人看到完整 diff。

### D5 token 限流替代了 token 治理

**[事实]**

- agent 每个节点都是新会话。
- 上下文包只有 3k–6k token，各段有降级和截断策略。
- 设计修订的提示词明确预期"上下文里可能没有上一版设计正文"，要求作者凭合同和评审意见重建一份完整的替换设计。
- 为此有 6 个 `context.*` 节点、`ContextBuildPackageHandler`（1,279 行）、`RepoScopeGovernance`（1,069 行，读取上限 24 个文件）、`WorkstreamMemoryProjection/Updater`、读复用合同、会话压缩策略、交接与下一阶段提示词节点。

**[判断]** 被限的是单次调用的输入长度，付出的代价是：

- 修订看不到原稿，于是需要"完整替换"和逐条复述；
- 跨节点没有记忆，于是需要记忆投影和谱系；
- 读取受限，于是需要读复用合同。

现代 provider 的缓存读取很便宜，P0.5 已能归因 cachedRead。而一次失败的真实样本浪费的是整轮调用。从"每次交付合格的总成本"看，这种限流大概率是负收益。**[待证]** 需要用已有的 A3 和 OKX 归因数据核算"失败轮次的 token"与"限流节省的 token"。

### D6 验收方不能执行，催生证据搬运子系统

**[事实]**

- Codex 验收使用 `review` profile，只能读和搜索，不能执行测试。
- 自测证据由 Claude 产出，验收方只能核对引用：`verification_checks` 要求带 `tool_call_ids`、`material_refs`（含 sha256）。
- 为此有 `EvidenceManifest`、`EvidenceCollection`、`VerificationEvidence`、`ExternalVerificationEvidence`、`VerificationReadiness`、collect/submit evidence 两个审批点动作及 baseline，合计约 1.6k 行以上。

**[判断]** 独立验收的价值在于验收方自己观察结果。不给执行权，就只能核对作者给的引用，于是要用身份链证明"引用没被篡改"。worktree 隔离本身已经是副作用边界：验收方在同一 worktree 里只读执行测试，风险可控。

### D7 校验没有严重性分级

**[事实]** 校验结果只有 `NONE / SCHEMA_REPAIRABLE / NOT_REPAIRABLE` 三种。只有回写身份值和 obligation ID 属于可修复，其余一律不可修复，进入 `WAITING_FOR_RECOVERY`。

**[判断]** 拒绝原因应该按"谁有能力纠正"来分：

- 授权和完整性违规：只能拒绝；
- 形状和引用错误：应自动退回同一 agent 修正；
- 质量和语义问题：应交给评审方判断，而不是由程序拦截。

现在第三类常被当成第一类处理，所以流程不流动。

### D8 账本状态文档冗余

**[事实]** 每个工作流有 workstream、current stage、current iteration、checkpoint、workflow instance、workflow current、node、gate、iteration、stage、design stage、workflow/node execution、agent task、run 等多份文档，另有 events、timeline 和 metrics 投影。`LedgerSession` 按固定字节批量写入。

**[判断]** 多个"current"指针并存，需要分类器和一致性检查来防止漂移，例如 `ExecutionConsistencyClassifier` 和 `statusSource=legacy_initial`。**[待证]** 需要逐一确认哪些文档是权威、哪些可以由事件和节点文档派生。

### D9 操作面直接计算动作目标

**[事实]** `console.js` 约 2,000 行，`mgt/app.js` 约 1,400 行，操作目标在点击时才从全局可变状态读取；前端测试没有接进回归；`WorkstreamQueryService` 有 1,055 行。

**[判断]** 可执行动作应该由服务端在 read model 中给出，每个动作自带对象令牌；页面只负责渲染和回传，不自己拼目标。

### D10 流程治理成本高于产品推进

**[事实]** 2026-09 以来约一半提交是文档或验证门记录；每个修复都要走边界分析、全量回归、独立评审、用户批准、重冻发行的全套流程；一次授权只跑一个样本，失败就止损。

**[判断]** D1、D2、D7 让每次真实运行几乎必然撞上新的特例；严格的流程纪律又让每次撞上的代价是好几天。两者叠加，形成"跑一次、修一点、再跑一次"的慢循环。流程纪律本身没错，错在它被用来逐个处理本应在模型层泛化消除的问题。

## 3. 简化设计

### 3.1 不变的底线（简化不得触碰）

1. 授权：业务决定只能由有权的人，或有显式委托凭据的执行者作出；决定必须绑定到它所见的对象。
2. 账本完整性：状态只由账本产生；写入在锁内原子完成；可冷启动重放。
3. 执行事实：`AgentExecutionAttempt → AgentExecutionOutcome` 的唯一结果链（P0 已 Promoted），以及 provider/model/token 归因（P0.5）。
4. 隔离：每个工作流在独立 worktree 中运行；禁止路径不可写；交付前人看到完整 diff。

### 3.2 节点角色化：4 种角色加阶段模板

| 角色 | 职责 | 输出合同（模型只写语义） |
|---|---|---|
| AUTHOR | 产出或修订一个阶段产物 | `{status: READY｜BLOCKED, body: markdown, planned_files?: [...], responses: [{finding: "F1", resolution}] , questions?: [...]}` |
| REVIEWER | 审阅产物并给出结论 | `{verdict: APPROVE｜REVISE｜BLOCK, findings: [{handle?, severity: MUST｜SHOULD｜NOTE, text}], resolved: ["F1", ...]}` |
| CHECK | 程序化检查，例如构建、测试、diff、禁止路径 | 程序产出，无模型参与 |
| GATE | 人工决定 | 统一的 Decision，见 3.4 |

阶段模板 `stage(name, author, reviewer, maxRounds, gatePolicy)` 由 loader 展开成"作者 → 评审 → 按结论路由"，DECISION 节点并入评审结论的路由。full 流程收敛为：

```text
requirement(stage) → [人工: 目标与验收标准]
solution(stage：设计与计划合一) → [人工: 仅在评审 BLOCK 或用户配置要求时]
implement(AUTHOR，含自测，同一会话) → verify(CHECK：程序跑构建与测试) → acceptance(REVIEWER，可执行)
→ deliver(CHECK：diff 与禁止路径) → [人工: 最终验收与交付]
```

顺利路径从 10 次 agent 调用降到 5 到 6 次；`context.*`、交接节点和下一阶段提示词节点不再是工作流节点，上下文构建变成 agent 执行器内部的一个函数。节点语义只来自"角色 + 定义里的配置"，Java 中不再出现阶段节点 ID。

### 3.3 身份归程序：信封外置，引用用短句柄

- 信封（workstream、run、node、schema、基线产物摘要、上下游引用）由编排器在模型输出之外生成并写入账本，模型永远不回写。
- 需要引用的对象（评审意见、合同条款、人工备注、验收项）由编排器在提示词里给出短句柄（`F1`、`C3`、`H2`、`AC-1`），模型只引用句柄。编排器把句柄映射回真实 ID 和 SHA。
- 修订时直接提供上一版完整产物；作者返回完整新版本，由编排器计算 diff 并登记版本。不再要求逐字复制未改动的条款。
- 删除"回写身份的 correction"这一类别，因为错误来源已经不存在。

预期可删除或大幅收缩：Lineage/Binding/ProjectionVerifier/RevisionContextResolver/RevisionMerger/PatchTargetValidator 这一族，以及信封身份校验。

### 3.4 统一决定与授权

```text
DecisionSubject = { workstreamId, nodeInstanceId, artifactDigest }   // 账本生成，读模型下发
Decision        = { subject, action, actor, authorityRef, note }     // 唯一写入入口
Authority       = 人本人 ｜ Delegation{ 授权人, 可代决的 gatePurpose 集合, 预算/次数上限, 有效期 }
```

- 每个人工审批点在 read model 中下发 `availableActions[]`，每个动作带一个 `subjectToken`。页面、管理页和 CLI 只回传这个令牌。服务端在写锁内校验令牌与当前状态一致，不一致就以类型化 409 拒绝。
- autorun 代批、预算授权、网络授权、WebSearch 授权都变成 `Delegation` 或 `Grant` 的实例，经同一个 `Authority` 接口消费。现有的 `expectedGate`、`subject`、`gateNodeInstanceId` 及各种 grant 存储收敛到这一处。
- 本批"审批绑定"修复就是这一模型的第一个落点。建议按这个形状实现，避免再增加一种绑定。

### 3.5 校验三级

| 级别 | 例子 | 处置 |
|---|---|---|
| L1 不可放行 | 授权或对象不符、写禁止路径、账本或执行事实不一致、越过排除项 | 拒绝，进入人工；不自动重试 |
| L2 可自动纠正 | JSON 形状错误、未知句柄、缺必填字段、句柄重复 | 带诊断退回同一 agent 修正（默认 2 次，共享阶段轮次预算）；耗尽后转人工 |
| L3 交给评审 | 文件集与计划不一致、修订没逐条回应、证据不充分、方法描述有追加 | 写成 finding 交给评审方或验收方判断；不由程序拦截 |

规则只登记在一个 `ValidationCatalog` 里（错误码 → 级别 → 处置），handler 不再各自决定"可不可修"。这样也直接落实了 AGENTS.md 里 TRANSIENT/BOUNDARY 分级的意图。

### 3.6 上下文与 token：从限流改为预算止损

- **会话：** 同一阶段、同一角色在一轮迭代内复用会话，利用 provider 缓存；阶段切换时用产物交接，而不是依赖会话记忆。
- **上下文：** 修订时提供上一版产物、评审意见和人工备注的完整内容；仓库读取不设文件数上限，由 agent 自行探索。
- **治理：** 保留 P0.5 的逐次归因；增加工作流级预算信封（token 或金额上限、轮次上限），触顶即停在人工审批点。不再使用逐段预算、读复用合同和探索计划。
- 会话压缩只在单次上下文接近模型窗口时触发，作为执行器内部策略。

这与现行 Phase Lock 冲突：改变默认会话和上下文策略属于 P1，目前被锁。需要负责人显式解锁。

### 3.7 合同与范围

- 合同只保留：目标、验收标准（带句柄）、显式排除项。人在需求阶段批准；之后的修订由编排器生成版本 diff，交给人确认。
- `planned_files` 只作信息用途，不做授权。实现中的偏离由验收方在 L3 层面判断，最终由人在交付审批点看完整 diff 决定。
- 删除 EXECUTION 阶段的精确文件授权，以及 `request_scope_revision` 的往返流程。保留 GOAL 级的人工批准。

### 3.8 验收可执行

- 验收方使用"验收执行"profile：可读，可在 worktree 中运行构建和测试命令，不能写源码，默认禁网。
- 程序化 CHECK 先跑构建和测试，把原始结果交给验收方；验收方可以复核或补跑。
- 证据清单、baseline、collect/submit 只保留给沙箱确实无法完成的外部证据（例如浏览器），作为一个通用的"外部证据"审批点动作。

### 3.9 账本瘦身

- 权威状态只保留：`events.jsonl` 加节点、审批点和 run 文档，再加唯一的 `current.json`。
- stage、iteration、checkpoint 的 current 文档改为由事件派生的投影，或者直接删除。**[待证]** 需要先完成 D8 的权威性清点。
- 新旧账本不做长期兼容：旧工作流判为 INVALID（只读归档）；需要保留的样本走一次性离线迁移。

## 4. 迁移路线

每一步都可以单独验收，前一步不通过就不进入下一步。真实 provider 运行仍按 Verification Gate 授权。

| 步 | 内容 | 主要证明 | 预计删除 |
|---|---|---|---|
| S0 | 失败分类：把历史 FAIL/STOP 样本和 398 个拒绝码按 L1/L2/L3 及"转写/范围/限流/真实缺陷"分类 | 一张分类表，说明多少失败属于结构性问题 | 0 |
| S1 | 统一决定与授权（3.4），吸收本批审批绑定修复 | 跨任务、乱序、过期、重复提交、冷启动等测试；所有入口只走一个 API | 多套绑定与 grant 写法 |
| S2 | 身份归程序（3.3） | 用 GCTW、KPPZ、7SNA、6NX5 的原始输入做离线回放（需要历史账本原件），证明这些转写失败不再发生 | Lineage/Binding/Revision 一族，约 3–5k 行 |
| S3 | 校验三级与统一目录（3.5） | 每个错误码有级别；L2 自动纠正测试；L3 进入 finding | 各 handler 内的分散判定 |
| S4 | 角色化与阶段模板（3.2），设计与计划合并 | Java 中阶段节点 ID 字面量清零；同一套测试覆盖所有阶段 | `context.*` 等节点、按 ID 分派的代码 |
| S5 | 上下文与会话策略（3.6），需解锁 P1 | 同一 case 前后对照：自然 E2E 通过率、每次合格交付的 token 和耗时 | ContextBuild/RepoScope/Memory/ReadReuse 大部分 |
| S6 | 合同与范围简化、验收可执行（3.7、3.8） | 自然 E2E PASS；越界改动在最终 diff 中可见并由人决定 | EXECUTION 精确授权、证据搬运子系统大部分 |
| S7 | 账本瘦身、操作面改为消费 `availableActions`（3.9、D9） | 冷启动重放；UI 和 CLI 不再自己计算目标 | 冗余 current 文档、前端目标计算 |
| S8 | 文档治理：一个活动计划（≤300 行）、ADR、其余归档 | — | 大量文档 |

**[判断]** 价值最高、风险最低的是 S1 和 S2。S2 预计能消除近期过半的真实失败，而且可以完全离线证明。

## 5. 衡量口径

- 主指标：固定 case 集上的自然 E2E 通过率（`COMPLETED`、产品验收通过、授权链完整）。
- 每次合格交付的非业务人工干预次数、已核销 token 和耗时（沿用 P0.5 归因；订阅未定价的单独标注）。
- 结构指标：领域层代码行数、拒绝码数量、Java 中节点 ID 字面量数量、`WAITING_FOR_RECOVERY` 中属于 L2/L3 的比例。

## 6. 风险与取舍

| 取舍 | 风险 | 缓解 |
|---|---|---|
| 取消执行前的精确文件授权 | 人在实现前管得少了 | 排除项和禁止路径仍是硬边界；最终交付必须由人看完整 diff；验收方报告范围偏离 |
| 验收方获得执行权 | 测试副作用 | worktree 隔离、禁止写源码、默认禁网 |
| 会话复用、上下文放宽 | 单次调用 token 上升 | 预算信封止损；缓存读取归因；用 S5 的前后对照决定是否保留 |
| 不兼容旧账本 | 历史样本无法继续运行 | 只读归档，保留证据；需要的样本做一次性离线迁移 |
| 大范围删除 | 回归风险 | 按步推进，每步都有全模块回归和独立评审；先离线回放，再真实运行 |

## 7. 需要负责人决定

1. 是否接受第 3.1 节的四条底线，作为简化时唯一不可动的约束。
2. 是否接受取消 EXECUTION 精确文件授权，改为"排除项硬约束 + 最终 diff 人工决定"。
3. 是否允许验收方在 worktree 中执行构建和测试。
4. 是否为 S5 解除 P1 Phase Lock（会话与上下文默认策略）。
5. 旧账本判为 INVALID 只读归档，还是对指定样本做一次性迁移。
6. 本批审批绑定修复：按 3.4 的统一模型实现（推荐），还是先做最小修复再在 S1 中收敛。
7. 提供 GCTW、KPPZ、7SNA、6NX5、9YYF 的历史 `.agent/` 原件，用于 S0 和 S2 的离线回放。
