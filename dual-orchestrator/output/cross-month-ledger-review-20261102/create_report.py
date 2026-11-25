"""Render the adjudicated review using machine-derived counts and raw references."""
from pathlib import Path
from collections import Counter
import json
import re

HERE=Path(__file__).resolve().parent
D=json.loads((HERE/'inventory.json').read_text())
E={e['id']:e for e in json.loads((HERE/'evidence-index.json').read_text())}
CASES=[c for c in D['cases'] if c['population']=='OPERATIONAL_RETAINED']
MONTHS=[]
for month in sorted({c['start'][:7] for c in CASES}):
    cs=[c for c in CASES if c['start'][:7]==month]
    count=Counter(c['status'] for c in cs)
    MONTHS.append(f'| {month} | {len(cs)} | {sum(c["runCount"] for c in cs)} | '
                  f'{count["COMPLETED"]} | {count["WAITING_FOR_RECOVERY"]} | '
                  f'{count["WAITING_FOR_HUMAN"]} | {count["RUNNING"]} | {count["FAILED"]} |')

TEXT=r'''# 跨月执行 ledger：流程失败归因与架构交付审查

审查日期：2026-11-02。范围：本机可访问、去除快照重复后的 2026-05—10 保留记录。

**结论：已确认的共同问题是跨边界的职责错位和后续动作不匹配；还不能证明它们解释了全部未交付，更不能证明“多重试、放宽校验、缩短 session”就是正确路线。** 历史中既有真实流程缺陷，也有模型输出错误、外部环境阻塞和必要的权限拒绝。当前源码已修复其中一部分，不能再按旧账本重复建设。

**当前最具体的缺口是：取证被要求不改源码，但仍获得 implement 写权限；基线 guard 能拒收污染结果，不能阻止本次写入。** 第二个接线缺口是 typed 失败策略仍产出 `MANUAL_ONLY`，而 runtime 只消费 `AUTO_ALLOWED` 来自动重试。机器已知身份/固定方法仍需模型转抄，则是另一项待验证设计风险。修复应先补副作用前的权限边界，再接安全恢复，不以放松拒绝能力为代价。

本次交付是取证、设计分析与按专家标准的作者自审；没有生产代码修改、真实 provider 消耗、旧案例恢复或独立评审人。当前离线测试 197 项通过，只证明所列切面；取证“写前禁止”边界判 **FAIL**，整体交付改善仍未证。

## 1. 专家审查增加了什么

在原要求之外，本次加入了四个必要检查：主动寻找成功和正确拒绝的反例；还原同一次运行中各 occurrence 的先后关系；把历史故障与当前源码分开；检查下一消费者是否在外部副作用之前强制消费事实。它们用于否定过度归因，成本低于继续按猜测改代码。

原始入口为 `events.jsonl`、`run.json`、节点/人工门 JSON、模型输入输出及 stderr/transcript、批准与 readiness 工件、Git 对象和实际目标仓库提交。既有审计报告只作为待反驳命题，不作为统计输入。

方法与工具：文件系统清点、SHA256 固证、按身份和事件前缀去重、JSONL 逐条解析、状态/节点/run 分层统计、失败 IO 标记检索、具名案例时序重建、Java 符号与消费者追踪、Git 历史差异、离线 Maven 定向序列测试。所有命令通过 RTK 执行；检索和脚本均随报告保留。

本轮题目收紧为：**哪些机制在具体版本和具名案例中阻断过正确推进，哪些拒绝是必要保护，哪些缺口当前仍存在；最小前向修复怎样在保留安全边界的同时，获得 CI、冻结身份重放和独立产品判分的证据。**

## 2. 先把总体事实对齐

发现 185 个事件文件：176 个 workflow 副本，9 个非 workflow 协议流另列。去除前缀快照复制、拆分重复命名的受控试件后，有 158 个保留执行案例和 11 个受控 ledger 试件；下表排除后者。

| 案例开始月 | 案例数 | 所属案例全部 run | COMPLETED | 等恢复 | 等人工 | RUNNING | FAILED |
|---|---:|---:|---:|---:|---:|---:|---:|
{{MONTHS}}
| 合计 | 158 | 990 | 12 | 105 | 10 | 30 | 1 |

“所属案例全部 run”按案例开始月分组，不是当月调用量。状态是保留快照的末态，不是对今天仍在运行的服务做实时探测。RUNNING/等待人工不能自动等价于流程故障。

- 选定执行 ledger 有 9,055 条事件，时间从 5 月 25 日到 10 月 26 日；解析错误 0。存在 WA53 一个事件 ID 重复出现两次，已保留两行并标记，未擅自去重。
- 990 条 run 中：821 条标记 completed、165 条 failed、4 条 prepared。completed 表示执行层通过，不代表产品验收或交付；7 条 failed 实际 `NOT_STARTED`。
- ledger 有 170 次 `node_failed`、95 次 `node_retry_requested`、16 次 `revision_limit_reached`、12 次 `workflow_completed`。这些是不同统计层，不能互相替代。
- 只在当前保留记录中找到一个 `delivery_committed`：9YYF。目标仓库 HEAD 本次复查与 SHA `f8ec3d41cbe1cac554a78c3131372af434440691` 一致。{{E10:0}} 旧 schema 没有这个事件，不能据此断言其他历史完成案例没有真实交付。

因此，“12 条 OKX 链没有闭环”是特定队列的观察，**“几个月来全部零闭环”不成立**。跨项目任务、版本、人工门和运行窗口不同，本报告不计算混合整体成功率。

覆盖限制：注册表中 83 个项目根目录未找到可读 ledger；158 案例中 10 个没有保留 run 元数据，只有 9 个带 `frozenRuntimeIdentity`。逐条检查了 165 条失败 run 的可定位 IO，但有 85 个文件角色引用无法解析（含同一输出的 occurrence 引用重复），不能说 165 次都有完整原始模型 IO。没有完整身份和工件的历史执行，不能宣称可确定性复现。

## 3. 失败归类：先分“表象”，再判“机制”

以下八类覆盖 165 条 failed run，按原记录的 primary reason/旧失败字段归类。它们不是八种已证实根因。

| 记录的失败表象 | run 数 | 可以确定什么 | 不能直接推导什么 |
|---|---:|---|---|
| 旧版/未类型化失败 | 62 | 其中 reconnect 48 次 | 都是网络问题，或都是假失败 |
| 结果协议缺失/非法 | 40 | 18 次旧 control、22 次 envelope 类 | 模型不听话、解析器有 bug |
| 执行中断/超时/停滞/输出不完整 | 22 | 11 中断、7 超时、3 停滞、1 不完整 | 可以无副作用重跑 |
| 必要工件契约失败 | 15 | 输出未满足具体契约 | 验证器应该放宽 |
| 启动/CLI 兼容性 | 8 | 7 bootstrap、1 adapter 不兼容 | 修 envelope 可以解决 |
| 输入准入不足 | 7 | 进程未启动，缺证据可见性证明 | 已经浪费了本次 provider 调用 |
| 权限/探索范围 | 6 | 4 探索、2 权限拒绝 | 都是架构缺陷 |
| 新版 provider/session | 5 | 2 rate limit、3 reconnect | 自动重试必然安全或有效 |

真正的机制判定需要跨文件与时序。当前可确认和未确认的代表案例如下；完整绝对路径、行号和哈希见 [原始证据索引](evidence-index.md)。

| 案例 / 原始引用 | 时序与判决 | 当前处置定位 |
|---|---|---|
| PRF7，run `1782180664429`；{{E02:0}}、{{E02:1}} | harness 记录 `interrupt_process=true`，顶层仍落在 control 缺失。历史执行失败被压缩成结果协议失败 | 历史分类缺陷成立；当前 typed resolver 已区分执行阶段，需保留回归，不能再按旧类目统计模型服从率 |
| AYU5，run `1783479932615`；{{E03:0}}、{{E03:1}} | review_evidence_packet 没有合格 prompt visibility claim，启动前停止。同类七次，分布在五案 | gate 正确；应修上游证据装配/可见性证明，不得禁用 gate。同一错误重复重试无帮助 |
| 9S3P，run `1782746818017`；{{E04:0}} | scope 中写了“在 discovery 阶段定位”等自然语言，证据字段夹入 context 元数据 | 输出契约拒绝正确；应审查 discovery 与输出生产者，不能把中文描述转换成猜测路径 |
| BXWV，ledger 316、326、327；{{E06:2}}、{{E06:3}}、{{E06:4}} | 验收已 `EVIDENCE_COLLECTION`、must_fix=[]，自测仅缺浏览器/联网证据，却回实现 | 历史动作类型错误成立；当前 continuation 与 evidence guard 已有实现和序列测试，不应另建第二套恢复路由 |
| MBC2，节点原文；{{E07:0}}、{{E07:1}} | 已知基线 SHA 被模型转写错误，验证器正确拒绝 | “让模型重抄机器身份”是职责错位；当前已用程序绑定，保留错误 SHA 拒绝，勿为旧单例加兼容 |
| HHZL，run `1788476621271`；{{E05:0}}、{{E05:1}}、{{E05:2}} | 先语法错→唯一一次纠正→非法枚举。Jackson 按白名单正确拒绝 | 不能以最终枚举异常倒推先前纠正提示丢诊断；精确诊断局部有效，但原两次输出序列仍未证明可闭环 |
| V9W8；{{E08:0}}、{{E08:1}} | exit 0，但越过探索范围且没申请扩权 | 正确安全拒绝；不能用“进程成功”覆盖权限判决 |
| KNJM；{{E01:0}}、{{E01:1}}、{{E01:2}} | reconnect 失败与正文 end_turn 并存；48 次旧 reconnect 中 11 次有类似字面标记 | 只列待核验的优先级/完成事实争议；正文不是 request-scoped 原生完成证据，不能认定 11 次是假失败 |
| 3B2P；{{E11:0}}、{{E11:1}} | revise 上限耗尽，但修订要求包含解析前零 SQLite 写入等实质问题 | 不支持“所有 revision cap 都该放宽”；先区分产品缺陷、证据缺口和能力不可用 |
| 7SNA；{{E09:0}}、{{E09:1}}、{{E09:2}} | 固定 method 被扩写；当前资格校验严格相等，但账本停在下一评审 READY | 当前设计风险成立，历史失败因果尚未成立。不能把新 validator 检查旧输入冒称历史重放 |

这套分类支持“存在跨月重复机制”，**不支持给 165 次失败全部分配最终因果，也不支持宣称某机制贡献了零交付的某个百分比**。62 条旧版失败尤其需要保留未定状态。

## 4. 架构解释：四个边界，而非一个万能根因

权威边界应分别核对：Definition 的 workflow/contract/注册声明，Instance 的节点、批准与迭代状态，Execution 的准备、调用 intent、原生完成和副作用事实。ledger/domain object 是状态依据；run.json、UI 与总结报告不能通过自己的显示值授予批准或重发权限。缺少完成事实时，不能让模型正文替代。

```mermaid
flowchart TD
    D[定义与契约] --> I[实例状态与有效批准]
    I --> Q[资格及权限固化]
    Q --> X[调用 intent 与原生执行事实]
    X --> V[typed 结果与工件校验]
    V --> R[评审和下一动作]
    R -->|代码缺陷| A[获准实现]
    R -->|只缺证据| E[受限取证或合法等待]
    A --> Q
    E --> Q
    R -->|验收通过| C[交付资格与提交事实]
    X -->|失败且安全性待核| U[恢复资格判定]
    U -->|安全且有额度| Q
    U -->|不安全或未知| H[停止并人工处置]
```

历史 PRF7 出错于 X→V 的分类，BXWV 出错于 R→A/E，MBC2 出错于 I→Q 的身份生产。当前新增发现是 E→Q 没把“仅取证”变成限制写入的执行权限；typed 自动恢复政策则在 U→Q 尚有接线缺口。这样才能区分修复落点，避免每次都补 parser 或 prompt。

### 4.1 执行是否完成、结果是否合法、业务是否通过，曾被混到同一个失败出口

PRF7 证明，没信封有时是进程已被停止的结果，不是最早原因。V9W8 又证明，进程 exit 0 也不能覆盖范围拒绝。正确链路必须分别保留：原生 execution completion → typed execution outcome → 工件/业务评审结果 → workflow 路由。谁有较高优先级，应由核心模型决定，不能由日志末行或模型正文决定。

当前已有这些边界对象及提交协议；本次检验了其中的定向切面。下一步是确认实际部署使用的 frozen binary 及强制消费者，而不是继续新增同义失败字段。{{C01:0}} {{C04:0}}

### 4.2 程序事实被交给模型转抄，形成了不必要的随机失败面

MBC2 的 SHA 是明确历史例子。program 已知的基线身份，应由程序绑定；模型负责变更意图与内容。当前已沿此方向修复。{{C06:0}}

7SNA 暴露同类风险：readiness 已有固定 method，模型又把逐 AC 细节拼进去。当前校验器要求完全相等，有合理的权限与身份目的；因此不应改成 startsWith/模糊相等。更小的结构方案是由现有注册引用解析固定字段，逐项细节放到已有内容字段。**先证明该重复字段可由 owner 唯一推导，再决定是否改契约**，不能直接将一个静态风险提升为下一轮生产改造理由。{{C05:0}}

### 4.3 识别阻塞物与选择可消除阻塞物的动作，曾经脱节

BXWV 是本轮最强的路由证据：已有 typed 取证语义，后续却按“缺证据＝实现不完整”回写代码。进一步实现不能创造缺失的外网和人工浏览器证据。

正确不变量是“取证阶段不得获得产品源码修改能力”，而不是“不能进入名字以 implement 开头的节点”。只有程序验证的新代码缺陷事实才允许重新进入实现。当前 `ReviewDecisionHandler` 与 `EvidenceCollection` 已提供 continuation 分类和 guard；环境不足时合法等待也算正确路由，不能强迫它交付。{{C02:0}} {{C03:0}}

**但此前“这项能力基本已建成”的说法需要再收紧。** 三个内置 sibling 的 self_test_report 都使用 implement profile；adapter 从节点定义选择权限，取证标记只追加提示文本。implement 自动允许 Write/Edit/Bash，E1 文件系统 allow_write 包含整个工作树。{{C07:0}} {{C07:1}} {{C07:2}} {{C07:3}} {{C07:6}} {{C07:7}} {{C07:8}}

`EvidenceCollection.execute` 是先运行，再 verify；现有序列测试会真实改 source.txt，然后断言节点失败、不发布可用 sidecar，同时明确断言磁盘上的修改仍在。{{C07:4}} {{C07:5}} 因而已有能力是“污染不能当有效证据进入下游”，并非“取证不能写源码”。这里的生成后拒绝有必要的完整性价值，但不能代替副作用前授权。这个当前设计缺口不依赖对历史失败原因的猜测，也不能由 197 项测试绿色抵消。

### 4.4 失败的可描述性，不等于恢复动作的可执行性

95 次 retry 事件足以推翻“几个月来从无恢复”；它们本身也不能证明无人值守恢复。当前代码显示：typed 失败分支使用 MANUAL_ONLY，runtime 因而不进入自动重试；旧 `recoverable` 标记不会覆盖 typed 判决。**明确缺口在策略产出与消费的接线，不是已经证明缺一个 recovery worker。** {{C01:0}} {{C01:1}}

但改成 AUTO_ALLOWED 还需要五项事实：授权涵盖本次动作；旧执行及副作用状态已核清；输入身份仍有效；重复执行安全；重试次数和预算未耗尽。未知副作用不是 TRANSIENT。应让现有执行/恢复入口强制消费这些事实，不能以“recoverable=true”或“父进程退出了”代替。

这些机制可以共同阻断具体路径，但不是逻辑上足以解释所有未交付的充分原因。业务拒绝、环境不可用、操作者主动停止、未发现缺陷尚未排除。

## 5. 纪律演化：有增长证据，尚无“仪式导致失败”的因果证据

Git 对象显示：2026-06-11 的 AGENTS/checklist 为 32/90 行；2026-09-12 为 145/316 行。这是规则增长，不是浪费度量。[提交与行数](git-evolution.json)。9 月 4 日增加完整 transition/失败切面要求，[原始 diff](git-a1a70d9d.patch)；9 月 12 日增加真实运行前必须有可执行验证出口，[原始 diff](git-b197ce90.patch)。BXWV 等案例正说明部分要求有保护价值。

因此，“失败驱动的仪式棘轮”目前是一个**待检验的维护成本假说**。要证实它，必须在同类失败中比较处置档位、额外 gate/全量回归/重冻次数、等待时长、调用和实际风险变化。仅用文档行数与失败同向增长不成立。

本次能提出的具体反例是：若一个被程序确认安全的 TRANSIENT 仍只能 MANUAL_ONLY，就说明文档承诺没有完整落实到执行政策；不能由此反推所有人工门和完整边界审查都多余。合理修法是消费现有严重性分类并限制处置范围，不是再加一份统一检查清单。

## 6. 为什么原来的可靠性和 token 推导不足以选路线

`单 run 成功率^10` 不能直接用于这里：任务和阶段异构，失败有关联，存在 review loop、人工门、主动停测，且数据是保留样本。未完成也可能是观察窗口内尚未完成。应按任务/版本/授权/环境分层，分别测启动准入、执行完成、工件有效、业务通过和提交交付的条件转移；对循环和等待使用带吸收态的转移模型，样本不足时不外推数字。

可以证伪的修复主张应是：相同冻结输入下，修复准确改变了哪一个下一动作，并且没有增加越权或重复副作用。它不等于“修两项，闭环率就会达到 85%”。

成本也须先核销。9BZG 一条 201ms run 与另一条都记录 404,593 token，归因为 `LATEST_PROMPT_IN_SESSION`；这是归属可疑证据，不能只按数值相同删除。{{E12:0}} {{E12:1}} {{E12:2}} 必须用 provider request/prompt ID 建唯一归属，区分缺失、可疑、已核销与未定价。旧总量同时有漏计和疑似重复，不是统一上界或下界。

每有效交付单位经济应同时报告：核销后的输入/输出/cache token、已定价金额、subscription/未知价用量、耗时、独立质量结果和实际交付数。分母为“符合质量判据且已提交的交付”，并单列预算内失败成本。零交付队列不能据此估计未来平均成本；观察期可报告无可用有限单交付成本。cachedRead 占比不是浪费占比；删除 review、缩 session、放宽 revision cap 都需相同质量预算下的对照证据。

## 7. 修复路线：先验证存量，再做一个前向结构改动

本轮主线只涉及 P0 正确推进和 P0.5 可靠执行/成本证据。不启动新平台，不对历史坏状态增加默认 production 兼容。

| 顺序 | 必须消除的不确定性 / 最小动作 | 既有 owner 与验收落点 | 不应做的扩大 |
|---|---|---|---|
| 1. 补取证写前权限 | 从既有 typed collection binding 推导有效权限，准备/dispatch 前固化；产品源码、测试、依赖和 Git 状态不能写。保留现有基线 guard | permission policy → prepared invocation → 原生工具执行；允许指定运行工件/构建临时目录，越界写在发生前拒绝 | 禁掉所有测试命令、只加提示、执行后回滚、另建一套 collection 状态 |
| 2. 验收已建控制链 | 冻结候选 binary/schema/validator；BXWV 型证据分流与 MBC2 型身份错误做正反序列。已修项只补遗漏的消费者证据 | continuation、EvidenceCollection、ContractRevisionBinding、AgentRunCommitProtocol；错身份不 dispatch | 重建路由平台、重复添加模型或报告字段 |
| 3. 接通安全恢复政策 | 在既有 typed 分类/授权/执行记录上定义可自动恢复的有限子集，持久化额度并经既有合法入口执行 | resolver → runtime → commit protocol；安全子集有界推进，未知副作用、终态、授权不足不重发 | 一律 AUTO_ALLOWED、无条件续传、新建第二调度器 |
| 4. 删除经证明冗余的转抄 | 对 method 等字段做 owner/消费者清点：能否从现有注册引用唯一解析？能才由程序组装，模型仅生成任务内容；不能则先厘清领域语义 | readiness 身份与现有 validator；过期/错目标/错执行者仍拒绝 | 提示词越写越长、模糊匹配、按正文扩权 |
| 5. 以核销与对照决定优化 | 先唯一归属，再比较 review 次数、上下文和 revision 策略对质量/成本的影响 | request 级用量事实和既有预算止损；相同任务/预算/权限的盲测 | 把 cached token 当全部浪费、承诺 8 倍或固定成功率 |

顺序 1 使已有“不得写源码”的语义在副作用前成立，构建和测试生成物需有明确出口，不能一概只读后宣称取证可运行。原生工具写入、shell 间接写入、冷启动和 direct dispatch 都须验证；prepared identity 必须绑定该有效权限。顺序 2 防止重做；顺序 3 不得越过未知副作用；顺序 4 仍需证实冗余后再改契约。成本核销可并行，不必等整个成本体系完美才修已证实控制缺陷。不能为救 HHZL/MBC2 单例把兼容分支写入 production：默认保留证据、在支持身份上开 fresh case；只有正式支持且损失反复出现时，再比较一次性离线迁移与永久恢复复杂度。

### 恢复政策的完整 transition 与失败切面（设计验收表，尚非实现结论）

| authoritative pre-state | 合法动作、owner 与写序要求 | 成功后唯一消费者 | 故障 / 重启 / 拒绝解释 |
|---|---|---|---|
| 原生执行尚未发送，资格不足 | 准入 owner 拒绝；先记录 typed 原因；不产生 provider intent | runtime 只消费拒绝/合法准备事实 | 身份过期或工件缺失不能靠重试模型补齐；replay 重算资格 |
| 准备已固化，intent 未写 | commit protocol 检查批准、输入 hash、执行者身份；先写不可变 intent，再外部调用 | 唯一原生执行入口 | 写 intent 前失败可按资格重判；写后送达不明须 UNKNOWN，不以 projection 缺失重发 |
| 调用发生，原生完成/副作用未知 | 先核验原执行和副作用；由原恢复 owner 固化事实 | typed 恢复判定 | 父进程 exit、文件 diff 不足以证明后台任务/网络副作用结束；无法证明则人工处置 |
| 已证实停止、授权有效、安全可重复、额度剩余 | 原恢复入口原子地固化本次重试额度及新执行身份，再按已有协议发送 | runtime/commit protocol 消费同一份恢复资格 | 额度写与排队之间 crash 要么可恢复为同一执行，要么明确拒绝；不得重复扣/重复发 |
| 仅缺证据且无 typed 代码缺陷 | continuation owner 绑定取证基线，先确定允许能力再 dispatch | EvidenceCollection guard / 后续评审 | 不可用环境走合法等待/失败；有新 typed 缺陷才可回实现；冷加载仍检查 |
| 验收通过，交付资格/批准未全 | 交付 owner 校验后 intent → apply/observe → authoritative commit | 统一交付 read model | UNKNOWN 不伪装完成；终态禁止 retry/resume 重开；abort 不等于自动撤销已发生远程副作用 |

这张表约束未来实现；其中未被当前测试覆盖的恢复额度与远程副作用故障点，必须标记未闭合，不能从局部类存在推定 PASS。

## 8. 交付 review：什么已经证明，什么还没有

本次新跑两组离线测试，共 **197 tests，0 failures，0 errors**，归档 XML、源码哈希与命令日志均在本目录。版本是当前工作区：Git HEAD `3362eb42` 外加未提交源码；不能把 HEAD 当作完整运行身份。[当前检验身份](current-verification.json)。

| 边界 | Declared / Proven by | Consumed by / Enforced before | Failure route / Cannot-bypass | 本次评级 |
|---|---|---|---|---|
| 结果与执行分类 | typed outcome；Resolver 22 + Acquirer 37 + Registry 50 项 | resolver/acquirer → runtime；结果发布与路由前 | 分类与契约拒绝；所列原生/纠正序列测试 | 定向切面 PROVEN；历史同一 binary 未重放 |
| 取证和实现分流 | continuation/evidence baseline；AcceptanceRepair 7、SelfTestContinuation 3、EvidenceCollection 7 项 | 路由前消费 continuation；执行前检查既有基线，执行后/发布前再次检查 | 已证明污染结果拒收、兄弟路径与冷加载；未证明工具写前拒绝 | 发布完整性切面 PROVEN；取证写权限边界 FAIL |
| 外部调用与恢复 | execution claim/prepared/intent/native facts；CommitProtocol 19、RunningStateGuard 27 项 | commit protocol；provider 调用前 | identity/活执行/既有 intent 拦截，UNKNOWN 不重发 | 所列序列 PROVEN；安全自动恢复政策未完整证明 |
| 程序身份与 readiness | RevisionIdentityWire 3、VerificationReadiness 8 项 | program binding / readiness validator；dispatch/资格前 | 错身份、过期和方法变化拒绝 | 拒绝切面 PROVEN；降低模型转抄失败未证明 |
| 用量归属 | AcpxCurrentTranscriptUsage 14 项 | 当前 usage 解析/归属消费者 | 过期/缺失不得冒充本次用量；以归档测试为范围 | 局部 PROVEN；历史全账核销未完成 |

总数校验：22+37+50+7+3+7+19+27+3+8+14=197。这些测试使用本地 fixture、脚本执行和仓库操作，未调用真实 provider。测试和审查均由主 agent 执行，**不构成独立同行评审**。

**Boundary Review：FAIL（取证写前权限）；其余未证整体切面为 WARN。Architecture decision：DESIGN_GAP，先补既有权限 owner 的消费义务，不据此重写恢复架构。** “不得改源码”是当前提示明确宣告的边界，而实现允许发生后再拒收，缺少 Enforced before。未证明这项约束和预算内持续推进前，不能称整体闭环；当前 SOURCE 的发现与历史已修项必须分离。此为按已声明目标的设计审查判决，不是声称真实 provider 已复现了新的事故。

### 三层独立可验证的收口条件

1. **CI 不变量**：按上述六维落实到现有真实入口。安全取证不写产品源码；错身份/过期批准/未停旧执行/额度耗尽不发外部调用；安全可恢复子集在储备准入给定的时限内推进。故障注入覆盖每个 durable write 前后与冷重启，检查 sibling 路径，不能只测试函数返回值。
2. **冻结身份重放**：同一历史输入先在原 binary/schema/validator 上复现原判决；缺工件或身份则 INVALID。候选版本跑同一输入属于“反事实对照”，单独标记；预期改变必须提前声明，同时保持所有负例拒绝。对 HHZL 必须保持 occurrence 顺序，不能挑最后一个输出作为原输入。
3. **独立外部 grader**：先定义匹配任务和基线。固定需求、数据、授权、环境、provider/model、预算及版本身份；外部判分器按产品行为和实际提交判定，尽量不读取 harness 的自评结果。旧版无法匹配时只报分层结果，不做异构历史排名。记录质量、交付、成本及 INVALID；盲测多案后才判断是否改善。

这三层可由 CI 与其他审查者分别执行；本次只完成第一层的所列既有切面和原始证据归档，未宣称新跑了完整历史重放或外部 grader。

## 9. 反思后重新分析：防止审计自己制造新棘轮

本轮主动降级了三项结论：end_turn 文本不能证明原生执行成功；HHZL 最终错误不能倒灌到此前的纠正输入；7SNA 后续风险不能算成已经发生的节点失败。它们共同要求：**先重建时间与身份，再判断哪一层有责任。**

因此当前建议不是“继续把五步全做完”，而是把已有机制的消费义务验收到真实入口，选择一个仍存在且可证伪的缺口。若它不能改变下一合法动作、减少不必要的模型生成或降低安全恢复的人为依赖，就不应进入本轮实现。仍未完成的是：不可读历史补齐、旧执行身份重建、历史成本核销、无人值守恢复的全路径证明和同条件外部产品对照。

## 10. 复核入口

- [inventory.json](inventory.json)：案例、run、状态、特殊事件、异常和不可读项目。
- [io-forensics.json](io-forensics.json)：165 条失败 run 的 IO 文件、字面信号、缺失引用与行号；标记不是根因。
- [evidence-index.md](evidence-index.md) / [机器可读证据](evidence-index.json)：19 项具体证据及限制，逐项链接原始文件。
- [source-sha256.json](source-sha256.json)：原始证据哈希；[current-verification.json](current-verification.json)：当前源码身份、11 组测试 XML 与交付提交复查。
- [collect.py](collect.py) → [inspect_io.py](inspect_io.py) → [assemble_review.py](assemble_review.py) → [create_report.py](create_report.py)：可重新计算。collector 会重写 source manifest，重跑时需按此顺序；测试须另按日志对应命令执行，assemble 只归档已有结果。

复核原始证据可以否定本报告的具名判决；独立产品基准可以否定修法的交付价值。二者都比审计作者的确信更有约束力。
'''

def evidence_link(match):
    r=E[match[1]]['references'][int(match[2])]
    p=Path(r['path'])
    return f'[{match[1]} {p.name}:{r["line"]}]({p}:{r["line"]})'

TEXT=TEXT.replace('{{MONTHS}}','\n'.join(MONTHS))
TEXT=re.sub(r'\{\{(E\d+|C\d+):(\d+)\}\}',evidence_link,TEXT)
TEXT=re.sub(r'\]\(([^)]+)\)',lambda m: ']('+str(HERE/m[1])+')'
            if not m[1].startswith(('/','https://','http://','#')) else m[0],TEXT)
assert '{{' not in TEXT
(HERE/'review.zh-CN.md').write_text(TEXT)
print(json.dumps({'report':str(HERE/'review.zh-CN.md'),'lines':len(TEXT.splitlines()),'characters':len(TEXT)},ensure_ascii=False))
