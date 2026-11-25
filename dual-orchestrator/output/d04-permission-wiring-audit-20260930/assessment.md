# D04 原生权限接线核对与受影响设计候选

2026-09-30。当前目标依详设 §8.25。完成范围：固定版真实接口的静态核对、既有记录/消费者映射、合法续作与失败恢复的验证设计。**不是产品实现，也不是能力运行证明或冻结结论。** 无 native/provider、注入探针、额外 agent、Rust/内核调查；未重开网络 R4/Phase B。

## 1. 结论与成本判断

两 adapter 均有真实 `session/request_permission` 往返；固定 acpx 还有 `onPermissionRequest` 宿主回调。因此“没有程序化审批入口”不成立。但 **Dual 当前 CLI 路径没有接通该回调，现有队列协议也没有权限回复命令**。直接运行客户端的交互界面、读取日志或改变一个权限参数不能补上它。

推荐提交评审的接线候选：在已有受审 acpx patch 内延伸请求回调与现有 queue IPC，向 Dual 提供真实待决请求和精确 `optionId` 回复；Dual 继续使用现有执行/run owner、应用入口及统一投影。正常批准回复给仍存活的同一个请求，不重发 prompt。调用已结束或结果不明时转 D07，不能把重试整个任务称作原地续作。

这是**有实际维护成本的 adapter 增量**：需要请求身份/取消传递、精确选项回复、队列消息及 Dual 接入；不是已证明的“配置即可”。D06 的短写入会话、run 单 writer、D02 失败保真本就在批准设计中，不能重复记作新平台成本，也不能假定已经实现。下文明确增量及反证，供一次受影响评审判断；本轮不新增 authority，不批准 patch 或应用实现。

| 路径 | 核对结果 | 候选判断 |
|---|---|---|
| 保持现有 CLI 非交互失败，再通用 retry | 能拒绝，但原请求已被回应或取消；已有 WebSearch 是失败后专用重试 | 不能满足通用“批准后同请求续作”。不得用自动重跑掩盖差距 |
| 复用现有 acpx callback + queue IPC，补齐双向请求/回复 | callback、queue owner、generation、旁路控制命令均存在；权限回复尚不存在 | 推荐候选；保留现有 CLI/session/queue，不另起常驻审批服务 |
| 改为 Node runtime embedding，替换 Dual 的 CLI 调用 | runtime API 确有 callback，但 Java transport/session/进程治理仍需重新接 | 本轮不选；没有证据证明替换调用链成本更低 |
| TTY 自动输入或解析模型正文批准 | 缺精确请求/选项身份、可重放的决定与多请求取消语义 | 不选；自然语言不能生成授权 |

成本止损：若上述候选仍需新服务、第二授权账本、替换整个 transport、修改原生引擎，或为一次审批普遍重跑已执行的长任务，停止提交证据与替代，不自动扩大。不能预报工时或 ROI 数值；当前没有实施估算实证。

## 2. 身份与证据范围

- acpx 源仓库 HEAD `fd173f04aa1b56f9e3f5ca5190c034ddcae28792`，包版本 0.13.2，带既有本地 patch；generated build identity 与仓库 patch 为 `4f7b7b8e81adf63b7145907cde01ac26f690113862fd9b933226a7eed4d47c8e`。
- 本地 `dist/live-checkpoint-OIY8CyQl.js.map` 内 client/permissions/types/permission-prompt 的 source content 与盘上源码逐字相同；编译 JS 也定位到相关 callback/fallback。此为本地构建关联，不是可复现构建或运行证明。
- Claude ACP 0.70.0；Codex ACP 1.10.0；acpx 依赖的 ACP SDK 为 1.4.0。分析的是固定缓存包，不绑定作者模型品牌。
- `.tools/acpx-e1/audit` 的旧记录仍是 `41ee5f00…`，未覆盖。当前身份从 generated 文件、实际 patch/构建及本次 SHA 记录取得，不把旧审计文件冒充当前。
- [identity.json](identity.json)、[source-index.json](source-index.json)、[带行号片段](source-excerpts.txt)记录可复核身份。以下源码路径缩写：`acpx/` 指 `.tools/acpx-e1/source/src/`；Claude/Codex 指索引中的缓存 `acp-agent.js` / `index.js`。Java 文件按唯一类名定位。

## 3. 请求、决定、执行：真实接口事实

| 入口与证据 | 事实 | 对 Dual 接线的限制 |
|---|---|---|
| Claude `canUseTool` :4213–4267；`requestPermissionFromClient` :4017–4033 | 带 sessionId/toolCallId/rawInput/options，选 `allow` 仅本次；`allow_always` 可带 updatedPermissions；reject 返回 deny；取消抛工具中止 | 不能把 always 自动缩写成“本次”。SDK 已自动允许/禁止的操作不一定触发 callback，不能承诺每条命令均询问 |
| Claude :4117–4194 | AskUserQuestion 属用户输入；ExitPlanMode 的选择可能改变权限模式 | 用户输入、模式改变、工具单次许可分开。模式改变不能伪装成普通工具许可自动接受 |
| Codex :29199–29228 → :25642–25735 | command/fileChange/additionalPermissions 各有原生请求，再转 ACP；错 turn/缺 handler 取消；按精确 optionId 还原决定 | `allow_once` 的 kind 未必代表“一次命令”：additionalPermissions 的同 kind 选项可授整个 turn；展示并保存实际范围 |
| Codex :25313–25418 | 命令 `decline`（不执行但可继续）与 `cancel` 是不同选项，均可为 reject_once；allow_always 也可能为会话或持久规则 | 只用 kind 或 bool 无法忠实回复。默认不开放持久授权/模式扩权；确需新增授权先提交用户，不新建权限判断器 |
| Codex :26125–26185 | MCP elicitation 可走 elicitation/create，或满足特定条件时转 permission fallback；普通 user input 另一条路径 | 不把所有 elicitation 当批准。未接通的入口明确不支持/取消，不能返回 allow；不新增网络/MCP 能力 |
| acpx `client.ts`:974–978、2000–2018、2101–2156 | 有宿主 callback，先于 mode resolver；callback 参数没有 RPC requestId，ctx 只有按 session 取得的 signal；undefined/异常退回 mode resolver | 原生个别 request 的取消上下文不能假定已传给宿主。桥接启用时必须精确关联并在故障时取消，不能静默 fallback |
| acpx `permissions.ts`:368–420 | policy 优先于 mode；读/search 可自动允许；非交互 escalate 产生拒绝/取消及元数据；fail 分支由客户端记为权限失败。callback 仅返回 kind，`allow_once` 可 fallback 到 allow_always | 要保留原生精确选项，必须补回复契约；不能只包装现有 callback 的五种 outcome。`--non-interactive-permissions fail` 不是所有操作均失败 |
| acpx queue `messages.ts`:26–87、IPC :397–458 | 有 submit/cancel/set_mode/set_model/set_config/close，无 permission reply；控制命令不需等待 prompt 队列执行完 | 可复用通信与 owner，须新增具名消息；权限回复不能排到正等待它完成的 prompt 后面。现有可选 generation 对权限决定必须变成必验 |
| `AcpxAgentTransport`:245–288、435–466 | Java 启动 CLI，stdout/stderr 重定向；常规权限带 fail，无宿主 callback 接线 | 流日志是观测面，不是回传通道。不能给已结束的请求补一个假回复 |

### 等待、多请求与超时

acpx queue owner :483–518 串行执行 prompt，但不等于一个 prompt 内只有一个权限请求。ACP JSON-RPC 按 ID 存请求；本轮不从这一点推导两引擎任意并发能力，验证必须覆盖合法并发或排队，未支持组合明确拒绝。

Claude 转发工具 signal；Codex handler 绑定 activePrompt.signal（:34320–34332）。acpx SDK `jsonrpc.js`:544–580/851–863 提供逐 RPC 取消通知/信号，但 acpx permission handler 没有把 requestId/request signal 向宿主传递。当前 callback 使用 session 级 controller，prompt 收尾/cancel/reset 会 abort；**“有 session cancel”不能证明逐 request 取消已闭合**。

在所读权限往返中未见专用固定人工等待期限；这不等于原生保证无限等待。CLI `runtime.ts`:1116 有可配置 prompt timeout；Dual `AgentExecutionHealth`:88、105–114 仅把请求记为一次进展，之后仍可能触发停滞超时。候选须复用本次调用已有的有限总预算作为默认等待上限，记录实际 deadline 与来源；不另建预算体系，也不因等人自动延长预算。若入口当前没有有限总预算，先明确本次有效上限，未明确不得宣称支持有界在线等待。实际有效等待受原生取消、连接终止与该期限中先发生者约束。仅有已持久化、当前有效待决请求时可以区分“等人”与“模型停滞”；不能无限延长总运行预算。超时取消不能记为用户 deny，迟到批准不能回复旧请求。

## 4. 现有记录与消费者对照

| 事实 / owner | 今天的落点和缺口 | 候选落点 / 唯一控制消费者 |
|---|---|---|
| Definition：角色、原生配置和业务约束 | profile/config、TaskContract/qualification 已在既有链；不是每次工具决定 | 不新增 workflow gate 或 DSL；配置身份仍进 frozen run |
| Instance：当前 node/task/execution/合同 epoch | runtime/worker 已有这些身份；D06 短会话尚待实现 | 当前唯一 claim 授 run 活动资格；完成仍由原 L03 消费，原生许可不能完成业务节点 |
| Execution：原生请求 | `AcpxSessionStreamProjector`:86–89 可观察 REQUESTED；transport :522–527 只把许可决定等写入 timeline；投影 evidence 截短且丢完整参数 | run owner 保存完整请求及内容 SHA，现有 executionTimeline 提供索引；只有当前接线消费者能回复。日志裁剪文本只用于展示 |
| 人的决定 | `permission_approval` 当前是 PUBLIC_WEBSEARCH 专用，要求 FAILED 后 retry；`HumanActionRequest` 明示仅建议，不是授权 | 扩充现有 execution/run 内 typed 决定记录与应用操作，owner 仍为 run writer；不能复用建议记录或伪造业务 gate。新增字段/操作是实质增量，不能只塞无校验 Map |
| 回复及执行结果 | permission_decision 是观测，不证明谁批准或请求仍待决；没有通用用户决定→回传桥 | 分开记录人的决定、回复提交意图/送出观测、原生后续工具/turn 结果；transport 唯一发送，不能把“已批准”写成“已执行” |
| 失败与恢复 | D02 signals/outcome → RecoveryAssessment；当前通用 permission blocked 不安全自动重试，WebSearch 是特例 | 保留明确禁止/用户拒绝/无交互入口/超时/取消/送达未知的来源，D07 决定恢复出口；不改写成缺 JSON |
| 投影 | WorkstreamAttention/RecoveryAssessment 与 run timeline 是既有落点，但尚无在线多请求 read model | 统一投影逐请求身份、选项范围、期限与是否仍可回复；CLI/API/UI/report 消费同一结果，不自行扫描日志判定可批准 |

两个具体兄弟缺口必须随接线处理：

1. `AcpxSessionStreamProjector`:131–137 目前只认 `allow`/`allow_always` 和 `reject*`。Codex 原生 optionId 是 `allow_once`/`allow_for_session`/`decline`/`cancel`（:25192–25196），会落入 CANCELLED。应以该请求提供的 optionId→kind/实际范围映射分类，不能再补另一批字符串条件；没有原请求映射就标未知，不从名字猜。
2. ACP 宿主 `fs/write_text_file` 与 `terminal/create` 有独立 `confirmWrite/confirmExecute`（filesystem :169–183、terminal-manager :430–444），不自动经过 onPermissionRequest。部署若开放这些入口，须走同一决定消费语义；未开放则保留明确拒绝。不能因为 session/request_permission 接通而让宿主操作默认放行。

## 5. 候选接线规格及边界

以下是**待 CC 评审的设计选择**，不是现状。范围限定在 W03/W05/W08，不增加新工作体系。

### 5.1 通道与身份

保留现有 acpx queue owner 与本地 IPC；在受审 patch 中接入 permission callback，携带原 RPC requestId、逐请求 cancellation signal、当前 queue prompt requestId 和 owner generation。所有实际 dispatch/correction/session 创建入口使用同一绑定；没有装配该绑定时明确拒绝交互要求，不能回落 approve-all。

请求键至少组合：workstream/node/task/execution/run/attempt、实际 ACP session、queue owner generation、prompt requestId、permission RPC id。toolCallId 用于关联工具，不单独当请求唯一键；父子 session 要绑定当前已观察的原生委派谱系，不能借另一个 workstream 的 session。请求原文/options、输入 epoch、角色/配置/业务批准引用固定 SHA。相同键相同内容重复观察幂等；同键异内容拒绝为关联损坏。

保留现有原生/受审配置的自动允许和明确拒绝；桥接不能因为 callback 今天先于 mode resolver 就绕过显式禁止。接线须复用既有许可判定，仅将真正需要人工的请求转交用户；进入人工分支后 callback 异常/undefined 必须取消，不再退回默认允许。此处是现有resolver与回调的接线次序调整，不重做命令危险分类。

回复使用原始、确实提供的 **optionId**，并保存其 kind 与真实许可范围；不经 `decisionToResponse` 的 kind fallback。不预选会话/永久允许，不把 additionalPermissions turn grant 显示成单命令允许。当前任务授权不足、出现模式/持久规则变更时停到既有授权决定；不让 LLM 分类后自行放权。人的 approve/deny 与程序因不可用而 cancel 分开记。

### 5.2 单 writer 与决定持久化

请求/决定/回复状态归现有 execution/run，而非新授权目录或 gate。`AgentRunMetadataWriter` 目前 append 是读改写，单文件原子替换不等于并发安全（:374–441）；必须随 D06 既有 run 写锁串行化进度、决定和最终结果，避免完成覆盖人决定。应用入口提交具名用户动作，不能让 UI 或 acpx 直接改 run 事实。

采用 D06 的短 workstream 会话核对 claim/epoch/业务批准，释放后向 run owner 提交带固定引用的决定；两锁不嵌套。run owner 核对自身仍 active 和请求仍待决后持久化。跨 owner 没有原子事务：若期间失去 claim/变更 epoch/终止，不把残留决定当有效可发送许可；原生发送前仍须按既有 dispatch 语义复核当前资格。禁止承诺“已发出的许可可以即时撤销”——发送后发生终止或范围变化走外部结果未知/取消核对。竞态不能靠两次读取冒充跨 owner 原子性，须在验证中实际触发并证明没有发送新的失效许可。

当前 CLI RunCommand:35、WorkerRunOnceCommand:30、server resume:76 的长锁不能原样保留，否则在线决定会 BUSY。解锁依已经冻结的 D06 §6.3/冻结附录 §6.1，不能临时绕锁写文件。**W03 的在线接线依赖 W05 的 D06/D07 实现，不能单独声称完成。**

### 5.3 多请求与回复窗口

同一 turn 可有多个不同请求键；逐项呈现、决定、回复，一项 deny 不给其他项隐式 allow，也不凭一项取消推断整 turn 全停。原生若明确终止整个 turn，剩余请求全部失效。permission reply 走已有控制分派的同类旁路，不排在被它阻塞的 prompt 后面。

决定落盘后、回传前先记录一次回复意图；只有持有当前活跃请求的 acpx owner 可以消费。重复同键同选项查询既有结果，不重复回复；同键异选项拒绝。连接/owner 死亡后不恢复旧 Promise，也不把新 owner 当旧请求继承者。存在发送意图但缺回执时按 UNKNOWN；只补本地事实，不重发。原生回复通常没有独立“执行完成回执”，pipe/IPC 写成功最多是发送观测，工具结果/turn 完成另行关联。

### 5.4 范围诚实性

不对现有两客户端增加一套命令危险分类；不恢复硬链接/沙箱实验；不把原生许可等同精确 scope。必要自动操作仍按受审原生模式/现有角色配置执行。只有真实进入 ask 的动作才走用户决定。不能新增“全部 ask 一律拒绝”的产品捷径；确需人工的合法操作必须有批准并继续的正向测试。

具名未决项：跨 owner 的资格变化与回复提交如何线性化，当前仅列出义务与竞态反证，没有将“两次读取”当已定提交协议；须在冻结前确定具体序列，不能留给实施者临场选择。原生请求保活上限无运行证明，当前只依据固定源码和既有调用预算设计。

本候选仍须评审确认跨 owner 决定/发送顺序、精确回复增量与成本是否足够明确；本轮不预称所有字段/接线已可实施，也不把设计发现转成默认开工授权。

## 6. 完整 transition 与写入失败切面

R 指现有 run owner 的本地提交；L 指 D06 ledger 短会话。二者无跨库原子性；下面不是新增业务状态机。

| authoritative 前态 / 触发 | 顺序、owner、后态及下一消费者 | 失败、重启、取消/重试 |
|---|---|---|
| L 中有效 claim/epoch；准备 dispatch | L核资格→R01 prepared→R02 intent→原生调用；请求绑定当前 attempt | intent 前可证明零启动；intent 后缺结果 UNKNOWN。旧版/不支持身份 INVALID，只读诊断 |
| 原生提供有效请求，尚未回复 | adapter捕获完整请求→R原子保存→统一投影呈现；下一消费者为用户决定入口 | 保存失败零 allow，取消该请求/核对调用；不能仅靠截短日志恢复待决。取消已发生则仅保存历史 |
| 多个 live 请求，用户决定其中一项 | L核业务上下文→R校验请求/范围/选项并保存人决定；其他请求保持各自状态 | 决定落盘失败零 allow；同键重复幂等、异选项拒绝；不持长 workflow 锁等人 |
| 决定有效且请求仍 live | 复核当前资格→R回复意图→acpx唯一消费并回原RPC一次→R保存发送观测 | 意图前失败可重新处理本地请求，但先确认仍 live；意图后任何不明窗口不自动重发；回复成功而观测落盘失败仍 UNKNOWN |
| 用户拒绝 / 原生拒绝 / 超时 / request cancel | 分开保存来源；回精确拒绝或cancel；消费原生实际结果 | 拒绝不是零副作用证明；原生拒绝某工具可继续不越权的其他路径。超时不伪造用户 deny；迟到批准只记无效提交 |
| reply 后工具继续/turn结束 | 原生结果→R03固定完成ref/SHA→L03校验claim/epoch并路由→自测/复评/验收/交付 | 同步落盘/路由失败只补本地；权限事实不授业务成功。scope/证据失效仍阻止采纳和交付 |
| 连接断开/进程重启/owner换代 | 保留原请求与决定；核对旧执行者及可信结果→D07 | 不对旧request补答；可信完成只补本地；不明交人工裁定。新 attempt 必须新请求/身份，不带旧一次性许可；明确停止后可按已有 AUTHORIZE_NEW_ATTEMPT 裁定继续，而非自动重放 |
| abort/终态 | 先取消并核对执行者；按D07记录未决外部效果→L06 | 终态不可再批准/dispatch；取消父进程不证明原生子任务全停；无跨 workstream 借权 |

## 7. 合法流程导出的验证设计（本轮未执行）

协议/队列/应用桩优先，测试数据从固定原生请求格式与合法用户操作构造，不从当前 Java 投影结果生成期望。测试目标是接线，不执行任意命令或真实模型。真实接口仍未知且影响结论时，另交有界 Gate/预算。

| 归属 | 正向 | 拒绝与失败切面 / 判据 |
|---|---|---|
| W03：正常自动许可 | 合格角色完成必要读取/合法产品修改，原生无需ask则不造人工门 | 缺业务批准零dispatch；不得靠全拒绝通过N1 |
| W03/W08：在线原请求 | 真实格式ask→保存→用户选本次→同RPC继续→正常完成 | 发送前未持久化决定必拒；assert 同prompt只发一次，不可通过重跑prompt替代 |
| W03/W08：精确选项 | Claude allow；Codex allow_once；分别正确投影 | allow_once缺失不得转allow_always；Codex两个reject_once/两个allow_once按ID与范围区分；未提供ID拒绝 |
| W03/W05：多请求 | 同turn A批准、B拒绝；倒序决定仍精确匹配；原生串行入口用排队序列 | 同toolCallId的不同RPC、跨session/run、同键异内容、旧owner、旧epoch均不得串用；一项取消不任意改其他状态 |
| W05：等待与取消 | 合法人工等待不被误判模型停滞；预算内决定可继续 | 单request取消、整turn取消、连接关断、人工等待/总预算到期；迟到回调和迟到UI提交零allow，来源不混为用户deny |
| W05：每次写前后 | 请求写、决定写、回复intent、发送观测、R03、L03各前缀冷启动 | pre-intent零回复；post-intent缺事实UNKNOWN且零自动重发；pending只补本地零provider；并发run进度/决定/完成无覆盖 |
| W05/W08：业务资格竞态 | 在有效claim/epoch下决定可消费 | 决定核验与run提交间、run提交与回复间分别终止/改epoch；失效许可不能新发送；已经发出必须留外部未知事实，不能声称撤销成功 |
| W03/W08：原生/宿主兄弟入口 | 实际开放的 session权限、宿主fs/terminal走同一允许语义 | 未开放的宿主入口明确拒绝；callback抛错/undefined/持久化失败零fallback放行；elicitation不当授权 |
| W05：安全停后继续 | 有可信完成只补本地；确已停止、用户结构化裁定的新attempt走新请求 | 普通retry/自由note不解除未知；历史批准不答新request；拒绝同一操作不得换工具/模式绕过 |
| W08：入口与投影 | inline/worker/correction/reused或fresh session；CLI/API/UI/report显示相同两项请求及不同决定 | 不允许某个入口遗漏装配；原生批准不能变reviewer READY/最终交付批准；S/D正反与源码漂移仍归W04/W06 |

cannot-bypass 反证：删除决定落盘前置、去掉请求键某个身份检查、启用kind fallback、移除取消检查、把未知设成自动retry、让reply进入被阻塞的prompt队列、保留长锁，分别必须使相关序列失败。正向全部被拒也失败。

现有测试定位仅供复用：acpx permissions/client/queue tests；Dual AcpxSessionStreamProjectorTest、AcpxExecutionFactOwnershipBoundaryTest、AgentExecutionHealthTest、RecoveryAssessmentTest 与既有生命周期序列测试。未宣称它们已覆盖上表，也未重跑。

## 8. 六维、自查与评审出口

| 维度 | 本轮结论 |
|---|---|
| Declared by | §8.25、用户确认的收敛目标；本候选没有恢复沙箱义务 |
| Proven by | 固定源码/包/构建关联及现有消费者事实；合法接线运行证据不存在，NOT RUN |
| Consumed by | 原生adapter消费精确option；acpx消费请求关联；run owner记录执行/决定；L03与恢复消费固定事实；投影无授权能力 |
| Enforced before | 候选要求批准回复/dispatch前校验及决定耐久化；当前通用接线尚无此执行点，不能称ENFORCED |
| Failure route | D02保真、D06本地提交、D07未知裁定；不引入旧请求复活或新的子任务恢复系统 |
| Cannot-bypass test | §7已给成对序列与反证；全部为未来验证义务，尚非PROVEN |

Boundary Review：**WARN / DESIGN_GAP**，含义是接线候选仍待独立审查，不是本轮材料内容自判有能力证明。发现的实际缺口为精确回复通道、逐请求取消/身份、活跃决定持久化与统一投影；均已定位到既有owner，未用日志或全拒绝替代闭合。

反思：此前把“原生已有批准”容易等同为“Dual已有通用批准接线”；既有WebSearch特例与Claude选项投影又掩盖了兄弟客户端差异。本轮用完整原请求、返回选项和下一消费者重建，避免再次只补条件。

未核验：原生慢速人工响应的实际最长存活时间、两引擎实际并发/委派下请求行为、产品接线运行与UI；新patch的取消竞态和D06实施效果；全量未提交diff与无关历史。未穷尽客户端内部安全性，属于已收敛的依赖边界。

本轮三项案头工作已产出，**不等于D04已冻结**。建议合并评审本报告、详设§8.25–§8.26、受影响的R3 I3与N清单，重点判断推荐通道及必要增量是否仍符合成本边界、跨owner写序能否实施、是否还欠具名冻结决定。若需扩大范围，停交用户；PASS也不授权开工。

## CC 提示词

请对 `/Users/kriswang/work/projects/ai/codex-claude-ai-workflow/dual-orchestrator` 的 D04 原生权限接线候选做独立只读评审，遵守 AGENTS.md。

入口：`output/d04-permission-wiring-audit-20260930/assessment.md`，同目录含源码身份与片段；详设 §8.25–§8.26、主清单 §9.18、R3 I3 与 N 验证清单为受影响范围。

请自行重建请求→决定→回复→执行→恢复及写入失败链，检查真实接口、现有 owner/消费者、多请求与取消、合法续作和兄弟入口。判断证据是否支持断言，候选增量是否必要且成本合适，是否具备设计冻结条件；按 Declared by / Proven by / Consumed by / Enforced before / Failure route / Cannot-bypass test 列分级发现、证据和未核验范围，不预设结论。

不修改文件，不运行 native/provider/注入探针，不启动额外 agent，不恢复底层沙箱证明。评审 PASS 不授权实施。
