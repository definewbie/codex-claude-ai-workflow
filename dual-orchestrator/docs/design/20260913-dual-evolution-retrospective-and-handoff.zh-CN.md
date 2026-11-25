# Dual 架构演进、失误复盘与新会话交接

日期：2026-09-13。核对基线：`3362eb42`，分支 `codex/recovery-origin-requirements`。
性质：历史重建 + 当前架构地图 + 决策交接；不是全仓独立审计，也不是 production 已修复声明。
本轮仅文档与只读核对，没有创建 agent/session、调用 provider、修改生产代码或恢复真实样本。

## 0. 新 session 先读这里

**最终目标没有变：让确定的开发工作流调用 LLM 完成软件开发，并交付可验证的产品变更。**
不是建设 Agent OS，不是证明所有模型可靠，也不是穷尽第三方沙箱的安全机制。

当前判断：Dual 有真实交付能力，但合法操作的组合一致性仍有缺陷，不能称为稳定闭环。
既不能因 OKX 多次停止而判定整条路线无效，也不能因历史交付和大量绿色测试否认当前缺陷。
建议保留已有架构，修正动作资格与执行阶段的控制边界，并用真实流程组合验证；不重写引擎。

新 session 的最短阅读顺序：

1. 本文：知道为什么走到这里、哪些结论已反转、当前不能做什么。
2. [工作流优先重设计](../plans/20260913-workflow-first-convergence-redesign.zh-CN.md)：目标流程、动作表、写入切面、实施批次与验收。
3. [6NX5 停止报告](../testing/real-case-reports/20260913-okx-retry-diagnostic-e2e.md)：最近两项缺陷的一手定位。
4. 仓库 `AGENTS.md`、[工程清单](../engineering-checklist.md)，然后按实施批次读相应生产入口，而非通读所有历史 Gate。

本文历史表是索引，不是要求重开所有历史 checkpoint。下文的“保留”不代表本轮重跑了其全部测试。
没有另行证据的能力不得从 `DETERMINISTIC` 升为自然 E2E `PROVEN`。

### 0.1 当前停点与授权边界

| 项目 | 截至本次核对的事实 |
|---|---|
| 最近 workstream | `ws-20260913T004742Z-6NX5`，`WAITING_FOR_RECOVERY / evt-000014` |
| 失败节点 | `node-intake-claude_brainstorm-012`；人工 revise 后输入准备失败，`runRef=null`，该节点零 provider 调用 |
| 根因 | requirement gate 允许 READY review 后人工 revise，后继 resolver 却只接受 BLOCKED；之后前置失败又被改写为缺少模型输出 |
| 最近生产提交 | `6df47cff`，人工 retry 传递合同拒绝诊断；后面的 `9370834b/3362eb42` 是 Gate/事故记录 |
| 当前新设计 | 尚未实施、未独立评审；不能据此重试 6NX5 |
| 已有验证 | 上个生产 checkpoint 全 reactor 1508/0/0/0 是历史记录；没有证明 READY 人工修订的正确全链 |
| 产品状态 | 6NX5 尚未进入实现；停止报告记录产品工作区干净，业务 session 已关闭 |
| 下一动作 | 接手者复核磁盘状态，按重设计先补合法路径与原因保真的整链反证，再做限定实现 |
| 不做 | 不改历史 ledger、不救援旧 case、不启动第二样本、不重开网络 Phase B/R4、不新增 model 层、不自动委派 agent |

用户曾允许主 agent 逐门审阅后代执行具体人工动作，不等于启用 autorun，更不等于省略业务审阅。
本次请求是全面复盘与新设计交接，不是新 provider 运行授权。后续真实验证仍遵守既有可复用 Gate 的身份和就绪条件。
模型 quota 到限应通知用户切换后判断 resume/retry；不能仅以耗时长认定仍在运行的模型失败。

### 0.2 术语防串线

| 历史名称 | 实际所指 | 当前处置 |
|---|---|---|
| 7 月 Phase A–D / Direction B | 输出合同、读治理收缩、context 与 handler 收敛 | 历史基础，不是本次计划批次 |
| 8 月末 E1 / R2–R4 | Claude 网络执行策略及沙箱证明 | 已有 transport 保留；R4 停止，不作为本轮前置 |
| S1 / S2 | occurrence-policy session 寿命 / implement local binding | 保留已落地边界，不重新测全沙箱 |
| 9 月 E1–E4 | 合同资格、typed design scope、作者修订批准、approved-plan scope 与旧投影减法 | 保留约束，不混同网络 E1 |
| 网络 Phase A / B | 人工发起一次域名授权 / 自动发现并申请 | A 已实现并有冻结环境 slice；B 未实现，不是理论上不可能 |
| 本次批次 A–D | 工作流优先重设计中的实施、组合回归、自查、真实 E2E | 只以新设计中的定义为准 |

## 1. 复盘方法与可信度

本次交叉读取 git 提交历史、现有架构/复盘/计划、近期事故报告，并核对当前关键代码与 POM。
时间线日期以 git 提交为锚；计划文件名日期可能早于实施提交日。Bash 原型起点来自历史设计笔记，
不杜撰其首次发布时间；项目 Java orchestrator 最早可见提交为 2026-05-24 `9d2b4f83`。

证据分四档：当前源码/原始事故核对；历史 checkpoint 和测试记录；外部原始文档；本轮工程判断。
历史文档里“当前”“已闭环”只在其基线有效。本轮没有重新执行数月所有 case、全仓测试或所有 crash 切片。
新文档覆盖接手决策，不覆盖或删除历史原始证据；同一断言有勘误时以注明基线的后续核证为准。

特别纠正：

- AHBV 曾完成真实交付，不能继续使用“Dual 从未 E2E 成功”这个前提。
- OKX 多个样本的 harness、输入、模型配置不同，不能把失败率当模型能力或任务复杂度的受控实验。
- 34 路径合同、14 项义务属于不同样本，不能拼成每次执行共同背负的负担。
- 接受提案、模型执行 exit 0、schema 合法、workflow COMPLETED、目标仓库实际交付是不同层次。
- amount 字段不是实际账单；订阅未知价格不是零成本。测试总数不是流程可达率。

## 2. 从复制消息到工程工作流：演进时间线

### 2.1 原型与第一版，5 月至 6 月上旬

起点是人工在 Claude 与 Codex 之间复制需求、实现、审查意见和 handoff。
隔离 worktree 与落盘 ledger 先解决“改了什么、能否追查”，随后加入节点、iteration、retry、human gate、
context package、TaskContract、failure classification、review packet、CLI/API/UI。

| 迁移 | 可复核锚点 | 获得的能力 | 遗留/成本 |
|---|---|---|---|
| Bash copy/paste loop -> Java 工作流 | 演进笔记；`9d2b4f83` | 流程可重现，角色和人工停点显式化 | 容易误以为增加节点就等于开发流程闭合 |
| 运行日志 -> node/run/event/iteration | 5 月 `ad6a0750/1285d48c` 等 | 执行与语义返工有了不同记录 | 文件写入顺序与中断语义仍需单独实现 |
| 内建流程 -> 外部 definition/条件边 | `3aa1c2da`、`ae02ad1a/b64d370f` | 目的地和条件可配置 | 代码 registry、动作允许表、resolver 仍可能另设限制 |
| 口头任务 -> TaskContract + 编译批准 | 5 月 `0111aaff/3eec642d` | scope 与验收有共同基准 | 合同若脱离真实仓库或过度枚举，会增加模型负担 |
| 人看日志 -> review packet/read models | 6 月 `070f2cdb/bc9a9e9f/fc915150` | 人可以据证据决定而非盯每次调用 | 早期 UI/CLI 多处解释同一状态，需要后续收敛 |
| 单一 reject -> typed failure attribution | `5cfcdb17`、`03df607a` | 区分代码、合同、证据、工具问题 | 分类正确还必须有可达的修复与重新批准链 |

反思：这些基础多数必要，但早期把“能力列表齐全”当作“流程完成”，同时扩展 calibration、metrics、
scheduler、UI 等旁支。它们有价值，却不能替代同一产品从需求到交付的序列验收。

### 2.2 成本、context、session 治理，6 月下旬至 7 月初

真实运行出现重复读取、长 transcript、session 状态和执行状态不一致，开始引入 memory、repo map、
read reuse、token/cost attribution、provider cache、execution outcome、in-flight reconciliation。
锚点：`f1c680b1` execution outcome，`54ccdcb4` session/memory，`19b4ccf4` managed memory，
`99f54b44` cache-aware cost，`a41770c9` in-flight attempt。

必要部分：区分累计 session usage 与本 run usage；保留 provider/model/billing 来源；复用事实减少重复上下文。
走偏部分：把“最好不要这样读”升级成运行失败，用事后 read observation 冒充事前安全隔离。
同样，进程活着、provider turn 完成、可用业务产物和 workflow 完成曾在多处重算，造成相互覆盖。

本轮判断：观测与成本数据保留；它们不得决定合同有效性或工作流资格。没有受控比较，不能声称 memory
或某个阈值已经降低总体成本。执行事实的唯一 owner 必须保持，准备失败不应伪装成 provider 失败。

### 2.3 输出、scope、context、handler 的收缩，7 月中下旬至 8 月初

| 迁移 | 提交/文档 | 正确方向 | 不能外推的结论 |
|---|---|---|---|
| Markdown/dual-control 提取 -> strict AgentResultEnvelope | `c05283d4`，前置 stream 收敛 `1c0dd20c` | 文本给人读，typed 产物给机器判定 | ACP 的 JSON 传输不保证消息正文是 Dual JSON |
| 失败后自由重写 -> 一次有界 correction | `12335e61/542187a7` | 允许有限表达修复，仍经同一语义校验 | parser 通过不等于未偷改语义，后续曾漏消费拒绝 |
| read enforcement -> telemetry；change scope -> Git 校验 | Direction B；`3f2ee81f/f2410e97/e1c094ba` | 不为多读一个文件烧掉有效实现；最终变更受批准范围约束 | 写范围约束不是沙箱，事后阻止交付不能撤销已发生的读取/写入 |
| 杂糅 context -> typed context boundary | `0902ed97` | 必需事实与可压缩建议分层 | 固定 soft budget 仍可能挡住合法完整合同 |
| handler 各判一遍 -> shared typed decision/evidence | `437b6e2c/2f26cf53/3ba983b8/cf7d4e1a` | 降低第二决策路径 | 一个 shared class 不能证明所有调用者都强制消费 |
| transport/session/status 多解释 -> execution fact 收敛 | `909317a2/dc08cf2e/0c81fbc8` | 单次执行、session 和投影职责更清楚 | 节点的前置失败还需与外部 attempt 分开，6NX5 证明仍有漏面 |

这阶段的重要教训已经出现：**优化建议、安全限制、结果合同、流程决定必须分开**。
后续复发并非没有写过这个原则，而是新改动没有用同一组整链用例检验其所有消费者。

### 2.4 合同修订与人工决定，8 月

模型被要求重写整份复杂 proposal，出现遗漏、重命名、旧字段/ID 不一致。策略逐步迁移为：
typed obligation coverage -> verbatim preservation -> lineage-bound revision patch -> 确定性合并 -> 复评/人工批准。
锚点：`83b01da4/6fb51608/bc080279/edbb37f3/fa1f4818`。

这一迁移有价值：模型只提出改动，机器保留未授权部分，降低整份重写的漂移。
代价是父版本、义务 ID、patch target、当前批准与作者版本成为协议的一部分，模型输出和接线测试更严格。
**这不是理由去删除身份约束，而是要求每个新增身份都有唯一生产者、明确消费者，以及合法修复路径。**

同阶段出现：BLOCKED 回人工门、self-test bounded repair、人工架构决定、requirement revision context。
锚点：`99f827d5/b2306882/d5a36483/eb2ed93c`。
6NX5 的 READY 人工修订漏面来自这一类设计的剩余假设：把“人要求修改”隐含地限定为“模型先判不通过”。

### 2.5 历史交付后，真实性与减法，8 月中下旬

- WA53 的 8 月 18 日记录有完整验收/audit/COMPLETED，以及 99 项测试记录。
- AHBV 的 8 月 28 日 Mailpit 任务记录了代码修复回路、最终 ACCEPT_WITH_NOTES、交付材料与目标 commit。
- 这些证明路线可行，不能证明后来增加的每个边界和每组配置可靠。

交付后的修复不是单纯加防线：

| 边界 | 演进与锚点 | 价值与代价 |
|---|---|---|
| 必需合同输入 | 固定阈值样本缓解 -> 完整必达，soft budget 不冒充硬限制；`e1b6e9e5` | 避免合法合同被治理误伤；不因此承诺无限 context |
| action 可见性 | edge action 与 allowed_actions 对齐；`288dc8fc` | 菜单与图一致；尚未证明菜单与后继 resolver 一致 |
| R1 多文件一致性 | 有界故障评估，延后 crash protocol；`3ecef0c4/f1663cbd` | 明确安全停止，不以新 WAL/事务平台抢主线；自动恢复残余不能宣称关闭 |
| workflow/prompt 减法 | 删除 legacy design loop；small plan-review profile；`a00cc9ce/e369a6e7` | 减少不产生独立价值的循环；small 是明确选择，不默改全部 variant |
| 仓库事实 | 获取事实 -> occurrence ref/SHA/lineage；`cca00827/b25d211c` | 合同从真实文件与 base 出发，避免伪造/串样本 |
| D1 delivery | 当前 worktree/扫描 acceptance -> accepted immutable candidate；`d04d0a58` | 人批准的是 bytes，apply/finalize 重验；身份计算与手工 commit 窗口有成本 |
| D2 completion/projection | filename/mtime/legacy -> routed lineage/current/View；`83d840ed/fdd8a49b` | 报告、CLI、API/UI不再各讲一个完成状态；稳定路径仅展示 |

这里已经得出“先边界正确性、再优化，不继续重复造同类样本”的决策；后续仍反复回到事故驱动 Gate，
说明缺的是把该决策转成开跑前的可达性检查，不是缺一篇新原则。

### 2.6 网络 E1、session 与 S2，8 月末至 9 月初

完整历史见 [网络收敛复盘](e1-controlled-network-access-convergence-retrospective.zh-CN.md)，本文保留关键反转：

1. WebSearch human permission 不等于 Bash 子进程网络；不开放整个 fetch，不用 Bash parser 做安全策略。
2. 官方 acpx 当时的转发面不足，采用 reviewed fork，绑定 policy ref/SHA/lineage；transport 到 SDK 不等于 OS 效果。
3. 宿主 ambient proxy、沙箱自建 proxy、Shadowsocks/TUN 被混为一谈；proxy 变量存在被错判成泄漏。
4. standalone Claude 与 embedded binary、CLI settings 与 SDK managedSettings 被混作同一测量对象；结果不能直接迁移。
5. persistent binding replacement 与 session lifetime 冲突后，收敛 comparator，再把 policy 绑定到 occurrence/run-scoped session。
6. S2 用真实 SDK option 通道验证 local binding，operator 接受 implement 可达宿主 loopback 的含义；其他 profile 不升级。
7. R4 停止：不再为产品开发前置证明第三方沙箱完整内部机制。保留已实现 guard，安全残余诚实标注。
8. 后续网络 Phase A 落地 operator 一次授权 -> claim -> policy -> 新执行；自动发现请求的 Phase B 尚未实现。

锚点：`e8cacb7f/5018790c/20b4e3f1/08229040/d43f4351/3f1f1575`。
S2 的本机服务能力与网络 Phase A 的授权域能力都不能外推成 Codex/容器/宿主总网络安全。
“phase A 可用”不是“所有 sandbox 拒绝都会自动弹出申请”；后者正是暂缺能力。

本轮判断：这一阶段既修了真实 transport/permission 组合缺陷，也明显发生了 scope 扩大。
应保留必要实现，不恢复证明工程，不把公网连通性的小检查再次包装成独立产品。

### 2.7 后合同 E1–E4，9 月 5–6 日

目标是修复“已批准需求并未一路被同一身份强制消费”和“反复修订忘记已关闭要求”，不是增加 OKX 专用规则。

| 名称/commit | 原先的失配 | 收敛后责任 | 代价与当前判断 |
|---|---|---|---|
| E1 `306a555d` | stable contract/初始 status/正文看似合同，却未证明当前批准 | resolved gate + source compile lineage 的唯一 qualified contract | 保留；flag 只是资格声明，不能成为第二批准来源 |
| E2 `a4e38ffe` | 设计自然语言中的文件范围与批准合同漂移 | typed design scope 对 qualified contract 校验 | 保留；文件范围不判断设计质量，合理新增要有人工批准路径 |
| E3 `1e9723ad` | review/human反馈没有持续进入作者、修订与批准基准分离 | authored occurrence、active obligations、revision responses、approved resolver | 保留已实现链；ID/response 是协议负担，不再扩成通用义务平台 |
| E4 `ffe9347f` | plan 不满足 design/contract 子集；canonical plan 供 metrics 另读 | approved plan 消费 + canonical writer/reader 删除 | 以减法保留；metrics 原 reader 属事后投影，不应误称运行时读取准入 |

通用性是“输入来自合同/图/当前 occurrence，替换业务仍成立”，不是“加一个通用类名”。
已有测试与代码不出现业务特判支持该判断，但不证明任意任务、任意模型都能满足协议。

### 2.8 从拒绝转向合法续作，9 月 7–13 日

| 问题族 | 演进 | 不能漏掉的整链要求 |
|---|---|---|
| 自动预算挡住明确人工决定 | declared repair gate、runtime source facts、human-noted override；`a44532de/ecfa7dce/425abf25` | 人每次带 note 决定，不是全局无限循环；模型不能伪造 human |
| 文档缺陷被当合同缺口，返工没有合法批准 | acceptance repair/reapproval；`ee72c654` | 分类 -> 正确作者 -> 新 proposal -> 两级批准 -> 新合同消费者，而非只改优先级 |
| 个别合理文件越 scope 即全流程停止 | scope repair -> 人工合理新增重走合同；`22ae064c/6a868174` | 不自动保留额外文件，不因可审就默认安全；保护区违规仍止损 |
| 缺证据再次回实现 | must_fix/missing_evidence + collect/request | 仅缺证据不能凭 INCOMPLETE 获取写代码任务；未知动作登记不能动态执行 |
| 人有外部证据但无合法提交路径 | source baseline -> prepare/submit -> 自测 -> acceptance；`88107d42/b197ce90` | 材料副本/ref/SHA、源码内容基准、前后重验；上传不是 ACCEPT |
| JSON 表达与身份引用错误 | fence/root closure；有界 authored correction；`4401149d/10cd2fbe` | 只修许可槽位；非目标内容冻结；acquisition 拒绝必须阻断 publication |
| 人工 retry 看不见上次拒绝诊断 | `6df47cff` | 同节点新 execution 获得诊断，不能改变合同/scope 或免除校验 |
| READY 后人工 revise 仍被拒；错误又被重算 | 6NX5，新设计尚未实现 | 入口与后继共享资格；前置失败贯穿最终账本/read model，不虚构模型输出失败 |

最重要的模式不是“模型越来越差”，而是**可用的部件不断增加，部件之间的合法组合没有同时成为稳定契约**。
模型确有输出缺陷；但前置阶段零 provider 调用的失败不能归给模型。

## 3. 当前架构地图：保留什么，哪里仍有冲突

```text
操作入口：CLI / server / UI
        |
        v
实例与决定：WorkflowFileLedger / RoutingEngine
        |                     |
        | 选边请求            | current、node、gate、iteration、events
        v                     |
JSON definition -> FlowOrchestrationKernel -> LiteFlow
        |
        v
节点执行：WorkflowRuntime 或 AgentWorker
        |
        +-- 输入：qualified contract / authored snapshot / 材料与grant
        +-- 动作：handler -> acpx -> Claude/Codex adapter -> 底层模型/工具
        +-- 结果：acquisition -> strict validator -> publication -> finalize
        |
        v
validated sidecar -> decision/classification -> 下一轮选边
        |
        +-- 人工返工 / 同节点执行恢复 / 补证据 / 正常推进
        v
acceptance -> completion audit / candidate -> 最终批准
        v
COMPLETED；目标仓库 apply / manual commit / finalize 是另受约束的交付动作
```

这是职责/调用图，不是 Maven 依赖图。当前 POM：ledger-file 依赖 core，flow-liteflow 依赖 core/ledger，
runtime 依赖 core/ledger/flow/git；不能照抄 8 月架构图中 ledger -> flow 的旧编译依赖箭头。
CLI/server 装配 runtime/adapter，adapter-acpx 处理外部运行，adapter-git 处理实际 Git 副作用。

### 3.1 核心模型及不允许混同的状态

| 概念 | 当前 owner/载体 | 不能代替它的东西 |
|---|---|---|
| 流程定义 Definition | workstream 冻结的 WorkflowDefinition；仓库 JSON 是创建输入 | 当前仓库改过的 JSON 不能改写旧实例语义 |
| 流程/节点实例 Instance | workflow current、node、gate、iteration | workstream 初始 legacy status、目录新旧、模型 summary |
| 节点执行 Execution | Workflow/NodeExecution；worker 的 AgentTask | 节点实例不是某一次进程；队列 task 完成不是业务完成 |
| 外部运行 Attempt/session | adapter process/turn facts、run metadata、policy binding | session 可复用不代表 authority 可跨 run；准备失败不伪造 attempt |
| 输入批准 | resolved gate + compile/作者 occurrence/refs/SHA | latest/canonical 展示文件、note 单独存在、LLM声称获批 |
| 业务结果 | 验证后的 typed artifact/sidecar | exit0、ACP stopReason、JSON语法合法都不足 |
| 实际变更/补证据源码 | Git evidence；EvidenceCollectionSource 的 HEAD/index/路径及原始内容SHA | 仅 HEAD + porcelain 无法区别同状态下的 A→B 内容变化 |
| 网络授权 | operator grant、目标 claim、policy | gate approve、prompt建议、报错中的域名都不是授权 |
| 交付批准 | immutable candidate 与 gate subject；expected tree | apply 时新读到的 worktree、稳定路径 Markdown |
| 展示 | 现有 object-specific read models/RecoveryAssessment/View | UI/CLI 不另判状态、重选证据或推导新动作 |

**当前缺口所在：**动作定义/ledger 接受与 runtime 输入资格不一致；前置 NodeResult 被产物校验/finalizer 重解释。
不是 LiteFlow 没执行图，也不是需要把所有 validator 变成 DSL。收敛对象是共享语义与消费顺序。

### 3.2 四种“合同”和三种“成功”

- TaskContract：人批准的任务范围与验收目标。
- AgentResult contract：模型如何表达当前节点结果，包含语法、类型与适用性。
- Artifact/lineage contract：哪个 occurrence 产生、是否被当前路由引用、bytes 是否一致。
- Execution policy：工具/沙箱/授权的执行约束，不是产品需求。

执行成功仅说明本次调用可用；阶段通过说明该阶段产物合格；产品交付成功还需验收、scope、审计与已批准候选。
这些区别必须留在模型中，但不需要为每个区别再造一个平行平台或第二状态机。

### 3.3 底层模型和角色的演进，不要与领域建模混称“模型”

早期流程沿用“Claude写、Codex审”的产品称呼，但真正应固定的是作者/验证者职责与输出合同。
Claude Code是agent运行环境，其后端可以是Claude兼容接口的GLM/DeepSeek；Codex也有自己的agent、
adapter及原生配置。agent品牌、模型名、transport协议、工具能力不是同一个变量。

历史上曾用隔离CODEX_HOME固定gpt-5.4获得一次health通过，之后用户要求恢复读取原生模型配置。
`38982c40`切换maintained Codex ACP；旧adapter固定其依赖的Codex核心版本，不等于Dual生产transport
本身总是写死一个模型名。应分别检查operator配置、adapter版本与真实run metadata，不笼统归因。
最近样本记录Claude ACP0.70.0/Codex ACP1.10.0、GLM-5.3/gpt-6-astra；只是该样本，不是未来默认pin。

本轮不增加model层。模型可替换的合理承诺是：业务流程不按品牌改边，adapter隔离协议差异，
角色结果仍须满足同一合同；不是保证任何兼容聊天接口都具有同样工具、权限或结构化输出能力。
WebSearch backend不兼容、sandbox组合错误、模型产出坏ID、harness零调用前失败必须分别归因。

### 3.4 主干与旁支：为什么“功能很多”仍不等于交付

早期还建设了goal satisfaction、calibration candidate/apply plan、workstream memory、token/cost、
timeline/attention、worker/scheduler和UI。这些有历史实现记录，但本轮没有逐项重新审计最新消费者。
它们分别帮助目标验收、经验沉淀、成本归因和操作可见性；只有被当前主干消费时才影响开发结果。

本次不删除这些既有能力，也不继续扩建。验收依赖的goal/audit保留为必需结果，memory/metrics/calibration
不能抢占合同、决定或执行的authority；scheduler不能替人创造授权；UI不能为了展示完整而发明状态。
模块清晰和字段齐全不等于它们已在所有组合下正确接线，这也是本次优先工作过程而非能力清单的原因。

## 4. 外部经验：采用了什么，哪里用过头了

以下为 2026-09-13 的一手页面复核或注明的历史来源。论文摘要仅支持下列窄结论，未复现实验；
在线文档不覆盖被冻结的旧 SDK/binary 事实，生产效果仍要以相应版本测试为准。

| 来源 | 可取的经验及本项目落点 | 不能据此推出什么 |
|---|---|---|
| [AI Harness Engineering，2605.13357 v1](https://arxiv.org/abs/2605.13357) | model-harness-environment 共同决定能力；需求、状态、验证、失败归因与干预记录确有职责 | 论文列出多项职责，不等于 Dual 必须同时实现每一项或建多个新模块 |
| [AHE，2604.25850 v4](https://arxiv.org/abs/2604.25850) | 变更应有可证伪预测，并由后续任务结果验证；结构机制比只改 prose 更值得检验 | 其基准改善不证明本仓 calibration/memory 已有效，更不授权自动自我演化平台 |
| [Aider repo map](https://aider.chat/docs/repomap.html) | 用相关符号和预算选 context；本仓 repo/context 建议受此启发 | context 选择不是 read 权限；不能把多读文件变成合同失败 |
| [Token consumption，2604.22750 v2](https://arxiv.org/abs/2604.22750) | 输入、运行方差与实际轨迹要计量，更多token不保证更好 | 不能直接套其比例预测 Dual 成本，也不能用固定软预算截断批准合同 |
| [HERMES 数学推理，2511.18760](https://arxiv.org/abs/2511.18760) | 早期 context 文档记录的跨领域启发来源 | **不是用户后来所指的 Hermes Agent 产品证据**，不能推出后者网络授权或多模型行为 |
| [ACP Prompt Turn](https://agentclientprotocol.com/protocol/v1/prompt-turn) | 流式消息、工具与 turn 完成须和应用结果区分；本仓 transport/acquisition 分层 | `stopReason=end_turn` 不保证 Dual 业务结果合法，更不保证需求完成 |
| [Claude sandbox](https://code.claude.com/docs/en/sandboxing) | proxy 位于沙箱外；strict/managed lockdown 可直接拒绝域名而不请求批准 | 不能从 proxy env 存在推断泄漏；auto mode 不等于绕过 sandbox；文本接口兼容不保证原生WebSearch兼容 |
| [LiteFlow 热刷新](https://liteflow.cc/pages/204d71/) | 已运行请求继续旧规则，后续请求用新规则；声明规则与执行实例要区分 | 修改 JSON 不自动迁移长期暂停的 Dual 实例及其批准证据 |
| [AWS redrive](https://docs.aws.amazon.com/step-functions/latest/dg/redrive-executions.html) | 保留成功步骤，用原输入/原定义重驱失败步骤；启发同定义 retry 与改定义新执行的区分 | 不代表 Dual 已实现 AWS 的耐久运行/幂等语义，也不允许重置旧账本 |
| [Temporal replay](https://github.com/temporalio/documentation/blob/main/docs/develop/safe-deployments.mdx) | 用旧 history 检查新代码兼容性，启发离线多跳回放 | 文件ledger不是Temporal事件溯源；本轮不引入Temporal，也不声称已有其确定性/事务保证 |

历史还引入“模型增强后做 harness subtraction”的文章启发，见 [首个 E2E 复盘 §11](e2e-harness-boundary-retrospective.zh-CN.md)。
其二手文章中的删 prompt/skills/agent 比例不是本仓指标，本轮不引用为已验证收益。
LiteFlow rollback 历史链接本轮获取失败，保留为 [9 月 10 日评估](../plans/20260910-workflow-continuation-and-liteflow-bounded-assessment.zh-CN.md) 的历史引用，
不声称今天重新确认了该 API。Hermes Agent 的具体实现本轮未审，不借类比承诺“所有底层模型无需适配”。

采用规则收缩为一句话：**先说明某条外部经验删除了什么问题，再证明本仓的调用链；引用不能替代开工理由和运行证据。**

## 5. 反复犯的错误：不只写“以后注意”

| 重复模式 | 真实表现 | 为什么持续发生 | 本轮必须改变的开工/停止决策 |
|---|---|---|---|
| 拒绝路径优先，合法路径遗漏 | READY human revise 被禁止；只补采却无 submit 出口 | 从 validator 的局部防护推导产品行为 | 先用用户操作写完整正向序列，再写攻击反例；合法入口到下次审核不可达则不能开真实E2E |
| 单元测试替实现背书 | 旧测试专门断言非BLOCKED人工revise失败 | 预期来自代码分支，不来自角色权限 | 每个关键预期写明来源是任务/动作/定义；删除错误oracle，不以测试数量辩护 |
| 有字段/有校验等于强制生效 | correction拒绝曾只进metadata；policy已传却效果未知 | 未跟到发布/外部副作用前最后消费者 | 必须实际执行到publication/路由/下个review；移除守卫后用例必须失败 |
| 同一事实多次重算 | typed failure被自由文本覆盖；前置失败被改缺JSON | contract、execution、projection交叉拥有原因 | 由原owner产出事实，后续只校验/投影，不重新“猜最像的失败” |
| 事故导致 scope 扩大 | R4、网络代理证明、TR-9全局拦截 | 把更严格当更正确，把P2观察都当开工前置 | 仅产品必经出口或已复现边界漏洞进入本批；安全旁支无阻塞证据则停止 |
| 局部修复后直接烧完整样本 | 同类流程规则数月后仍被自然case发现 | 用昂贵模型执行发现确定性输入矛盾 | 先跑真实runtime+ledger+LiteFlow的离线序列；模型不负责测试我们的菜单合法性 |
| 前置验证成了第二产品 | 多版Gate/runner/evaluator、未冻结脚本和红灯offline | 观测本身缺少最小性与执行自证 | 重用Gate模板；只有版本/目标改变更新冻结；先离线跑真实runner，外部探针只验证本任务所需能力 |
| 让人决定但不给合法完成路径 | 人能批准却后继读不到note/材料；固定CLI提示非法approve | 把人工门当暂停标记，而非可执行操作 | 菜单、资格、材料、决定后继、失败提示一起验；人工负责产品判断，不负责诊断Dual流程 |
| 后端归因过快 | 固定model、standalone/embedded混用、把proxy结果归给模型 | agent/adapter/runtime/model/environment没分层取证 | 记录实际调用面与版本；零provider就是harness前置；观察相关不能称因果 |
| 复盘也未核证 | HEAD+porcelain误称内容身份；metrics reader误称运行准入 | 信任名称/说明/摘要，而非计算与消费 | 给反例并读计算层；不确定注明，同行结论可驳回；独立评审不是免检凭证 |
| 文档持续追加“最新” | active plan 上千行互相覆盖，旧CURRENT架构被继续引用 | 用日志代替现行决策入口 | 本文+新设计为唯一接手入口，旧文只作带日期证据，不再层层复制状态 |
| 代替用户不断重新问/委派 | 小验证也请求、独立agent重复读同链 | 把仪式当质量或授权 | 主agent完成必要检查；不新增agent；真正新authority/超范围才停下说明 |

责任不能转嫁给用户或模型。主 agent 负责错误的开工、终止、归因与收口判断；reviewer 也可能错误，
但实施者必须核对其事实前提。写进 AGENTS.md 的原则仍是行为约束，不是能拦住编码的安全执行器。
本次不新增“原则执行平台”；通过有限范围清单、已存在测试入口与明确合入门槛改变实际行为。

### 5.1 为什么工作流本身简单，项目却越来越难闭合

必要复杂度来自长任务的暂停/重启、版本化输入、外部副作用、人工改变决定和不稳定的外部输出。
但当前额外复杂度有三个可具体指出的来源，并非“Agent天生很难”就可以解释：

1. **同一规则跨层重复。** 图说可revise，ledger接受，resolver另加BLOCKED前提；同一失败又被handler、validator、finalizer解释。
2. **每次只验证一个交叉点。** 已有自动返工、人工门、严格ID、retry各自可用，组合成READY人工返工或被拒后的人工retry时才露缺口。
3. **安全停止成为默认交付标准。** 更多输入被拒确实守住一部分边界，却没有同时设计人怎么合法补齐后继续；严格性掩盖了可用性退化。

因此新设计不是取消严格性，而是让“所有合法输入应有可达后继”和“非法输入不得产生副作用”成为同等强的不变量。
也不能承诺有限场景覆盖未知所有异常：未知必须停在可解释的人工出口，不再默认为代码返工或无限retry。

## 6. 保留、收缩、暂停、未解决

| 决策 | 内容 | 原因 |
|---|---|---|
| 保留 | worktree、typed结果、approved contract/design/plan、SHA/lineage、human gate、Git scope、candidate | 这些承担真实身份与外部副作用边界，删除会损害可信交付 |
| 保留并纠正消费 | action语义、NodeResult/attempt、runtime/worker校验及failure投影 | 当前冲突直接阻塞合法流程，是本轮必要工作 |
| 保留但不升级成熟度 | correction、外部材料链、网络A、local binding、model config继承 | 各自有不同层级证据，不等于最新整条OKX E2E通过 |
| 继续收缩 | 重复prompt、canonical扫描事实、read治理hard-fail、重复状态计算 | 已有减法经验；只清理本批同边界漂移，不全仓顺手重构 |
| 暂停 | R4/网络B、通用DSL/动作插件、模型层、RAG/自演化、全delegate给LiteFlow | 不是完成当前合法流程的必要前置，无新的收益证明 |
| 保留风险 | 多文件write间安全停止与旧状态恢复 | 不承诺WAL/全自动replay；发现安全拒绝也不成立时才提有界设计 |
| 未解决的产品可靠性 | 模型会不会反复错误、语义review是否收敛、复杂合同协议负担 | guard能阻止错误，不能保证模型每次输出正确；自然样本仍需验证 |

精确scope成本是真问题，但本轮不无证据地改成目录通配/语义授权。已有“合理新增->人工重批准”可用路径，
先证明它与正常流程组合成立；若持续成为交付成本主因，再单独以数据比较更粗粒度合同，不能偷偷放宽。

## 7. 新设计如何吸收历史，而不是再补两处

[新设计](../plans/20260913-workflow-first-convergence-redesign.zh-CN.md) 采用一个设计单元、有限实现批次：

- 用户过程先于类：需求、合同、设计、计划、实现、自测、验收、交付，以及各自修订/等待/恢复出口。
- 现有模型不替换：JSON/LiteFlow拥有目的地；ledger拥有实例决定；validator拥有资格与产物；projection只解释。
- A修入口/后继资格和失败阶段保真；B覆盖已有组合，不预造所有未来异常；C全链自查；D才实际E2E。
- 不是每条历史教训都开任务：在场景表中对照已有测试，缺失才补；有可达反例才改production。
- 合法续作与非法拒绝同级验收；终态、写间失败、cold start、variant及inline/worker都在同一完成定义中。
- 老样本只读；只针对当前支持的前向状态修复，不让单样本救援成本吞掉主线。

评价新设计的三个问题：

1. 不靠模型是否“配合”，一个合法人的动作能否到达应该到达的下一作者/评审？
2. 真正调用模型之前、期间、之后失败，最终显示的原因和动作是否仍正确？
3. 模型与环境都满足要求时，整条既有流程能否走到需求验收与候选交付，而不新增人工修账本？

如果只做到其中一个，不能称本轮收口。即使三个离线通过，也只说明确定性组合成立，真实质量另记。

## 8. 接手用的文件、身份与命令纪律

### 8.1 优先代码入口

以下均相对 `dual-orchestrator/`，方法名比历史行号稳定：

| 调查目的 | 最先读的文件 |
|---|---|
| 动作允许与提交 | `dual-core/src/main/java/dev/dual/core/workflow/HumanGateActionSemantics.java`；`dual-ledger-file/src/main/java/dev/dual/ledger/file/RoutingEngine.java` |
| 当前前置失败 | `dual-runtime/src/main/java/dev/dual/runtime/handler/TaskContractRevisionContextResolver.java`；`dual-adapter-acpx/src/main/java/dev/dual/adapter/acpx/AcpxAgentTaskHandler.java` |
| 结果强制消费 | runtime 的 `handler/AgentExecutionResultFacts.java`、`handler/ArtifactContractValidator.java`、`handler/AgentRunMetadataFinalizer.java`；`WorkflowRuntime` 和 `AgentWorker` 的调用顺序 |
| 目的地计算 | `dual-flow-liteflow/.../FlowKernelProvider.java`、`LiteFlowPrototypeKernel.java`；实际冻结 JSON 与 `ReviewDecisionHandler` |
| 已有整链测试复用 | `AcceptanceRepairSequenceTest`、`SelfTestContinuationSequenceTest`、`EvidenceCollectionSequenceTest`、`AuthoredStageSequenceTest`、adapter 的 `AuthoredReferenceCorrectionSequenceTest/RetryFailureContextSequenceTest` |

不是再造总测试框架：在现有测试中组合当前真实入口，让预期由用户流程导出。
读取测试不能只看函数名/总数，特别留意“禁止合法动作”的错误负例。

### 8.2 上个真实样本的冻结身份，仅作对照

| 输入 | 6NX5 Gate/报告中的冻结值 |
|---|---|
| JAR SHA-256 | `b9147829d793dc79d5c3f4c60daa12a4b894b53536c25bd8e4a6f9149ff0aa26` |
| small workflow SHA-256 | `3ea9ea6c567608f4593bfef65f5868d7cbbd1be0925315c650abd8ec3f1896d5`；28节点/51边/8 agent/6 gates |
| target base | `c84807946493a603c789ee40c6a4c615aa846c3b`；tree `81520ac179f17c35c7c358c2a02102007cbe7cba` |
| requirement SHA-256 | `9ac1544b1216eca575456e1b4c639b3d716d6eb9b89693991222192a5a2ac33a` |
| workspace check SHA-256 | `c9401ad2933920406ff643fc39f8c4078a3624e537a09264ba0928d00aa28f23` |

这些不是未来必须永远保持的版本，也不是本轮新算的实验结果。新实现完成后必须重建并由可复用Gate记录新身份。
6NX5工作区：`/Users/kriswang/work/projects/ai/okx-btc-k-line-demo-agent-runs/ws-20260913T004742Z-6NX5`。
证据索引在停止报告，原始输出在 `output/real-cases/20260913-okx-retry-diagnostic-e2e/`，输出可能被 git 忽略，
跨机器迁移时需单独检查是否存在；缺证据不能伪造。新session同机器仍应冷读，不以聊天摘要代替磁盘。

当前运行习惯：Codex 使用原生 `CODEX_HOME=/Users/kriswang/.codex`；acpx指向项目 `.tools/acpx-e1/bin/acpx`；
最近Gate命令超时配置为0，观察实际运行状态而非用耗时直接断失败。底层模型来自用户配置，不能恢复早期固定gpt-5.4。
实际model、adapter、SDK、binary和policy身份在未来preflight重新确认；不把本页当配置解析器或新pin。
敏感provider配置只记来源与必要presence，不复制token/代理凭据进交接文档。

接手时先看 `git status` 与 `git log`。当前存在未跟踪的CC评审、旧Gate、`.playwright-cli/` 与output；
不要 `git add .`，不要清理、覆盖或把它们混入新checkpoint。本交接的两个文档也尚未提交。
用户要求命令使用 `rtk`；手工文件修改使用 `apply_patch`；需要真实权限时按工具的审批机制执行。

## 9. 历史来源导航与本轮结论边界

不需要一次全部阅读，按疑问回溯：

- 原型与早期模型：[设计演进笔记](design-evolution-notes.zh-CN.md)、[8月架构快照](dual-orchestrator-current-architecture.zh-CN.md)。后者不是9月规格。
- read治理反转：[Direction B / B0](../plans/__CHECKPOINT__direction-b-b0-rebase-assessment.md)。
- 首个完整开发样本与减法：[WA53复盘](e2e-harness-boundary-retrospective.zh-CN.md)。
- 网络证明过度与owner修复：[网络复盘](e1-controlled-network-access-convergence-retrospective.zh-CN.md)、[网络Phase A](../plans/operator-network-access-phase-a-20260907.zh-CN.md)。
- LiteFlow取舍：[工作流续作有界评估](../plans/20260910-workflow-continuation-and-liteflow-bounded-assessment.zh-CN.md)。
- 历史E2E结论与勘误：[9月12日整体能力评审](../plans/20260912-dual-end-to-end-development-capability-review.zh-CN.md)。这是同行文档，不是本轮重新跑样本。
- 材料回路：[external evidence checkpoint](../plans/20260911-external-verification-evidence-checkpoint.zh-CN.md)。
- correction强制消费：[authored correction checkpoint](../plans/20260912-authored-reference-correction-checkpoint.zh-CN.md)。
- 当前缺陷与行动：[6NX5报告](../testing/real-case-reports/20260913-okx-retry-diagnostic-e2e.md)、[工作流优先新设计](../plans/20260913-workflow-first-convergence-redesign.zh-CN.md)。

本轮自查结论：历史脉络、事实来源和当前决策已整理；**实现状态仍未改变**。
未完成全仓所有异常/旁路审计，未重新证明全部旧版本能力，也没有以长文替代实施测试。
这份复盘的成功标准是新session能够从正确停点推进有限工作，不再从头争论引擎、模型层或网络安全平台。
