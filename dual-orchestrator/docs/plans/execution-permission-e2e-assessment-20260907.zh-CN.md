# 执行权限完整链路评估

日期：2026-09-07。独立评审后修订：当前仍为 DESIGN GAP；Phase A（operator 发起）具备进入最小实施计划的前提，Phase B（自动捕获）待接口验证。不得让 Phase B 阻塞 Phase A，也不把既有组件存在说成新链已闭合。

本轮只读 production、已安装 adapter 与 SPM9 证据；只新增评估文档并更新 active plan。未调用 provider、未 retry/revise、未改策略、未运行新样本。
基线 HEAD `6784b662851eb82ff7b0397ba656ee70089a854a`；工作树已有未提交的进展/总 deadline 修复，本评估读取当前工作树，不冒称 clean commit。

## 1. 目标与结论

目标是让合法任务在需要额外执行权限时，能解释原因、停止错误重试、取得授权、实际应用权限、继续验证产品；不是建设完整网络安全平台。

当前主要缺口不是 curl 不会联网，也不是模型不理解 approve，而是：

1. 策略明确禁止外网，同时没有向 Dual 提供完整的域名请求/人工授权入口。
2. Bash 内部网络拒绝没有进入现有 ACP 工具权限审批链，环境阻塞被带入语义修复循环。
3. 即使只补审批界面，policy writer 仍写空域名集；普通 human revise 不会改变它。
4. 即使补入域名，实现/自测的依赖准备职责仍不明确，产品验证未必能继续。

因此需要修的是一条有边界的执行恢复链，不是单个 permission 列表，也不需要重开 E1 全安全证明。
不能承诺一次发现 Dual 所有未知缺陷；可以要求本链的每个消费者和退出路径在开工前明确。

## 2. 事实与证据分层

### 自然样本

工作区：`/Users/kriswang/work/projects/ai/okx-btc-k-line-demo-agent-runs/ws-20260906T130900Z-SPM9`。
以下路径相对该工作区的 `.agent/workstreams/ws-20260906T130900Z-SPM9/`。

- `workflow/current.json`：`WAITING_FOR_HUMAN`，`evt-000053`，`node-gate-human_implementation_repair-053`；没有恢复运行。
- `runs/run-1788708435180/run.json`：`executionOutcome.status=SUCCESS`、`agentResultContract.valid=true`、`decision=SELF_TEST_INCOMPLETE`、`adapterSignals=[]`、`permission_decision` 数量为 0。
- 同 run 的 `outputs/implement-claude-self-test.transcript.log:248-257` 有工具返回的 `HTTP 403` 与 `X-Proxy-Error: blocked-by-allowlist`；不是只依据模型总结。
- transcript 同时显示 `curl-exit=0`，但命令为 `curl ... | head -5; echo "curl-exit=$?"`，记录的是管道后状态，不能单独证明 curl 自身退出码。不能据此建立退出码或字符串到权限申请的自动路由。
- 同 run 的 `execution-authority/claude-execution-policy.json` 绑定该 execution；生产 writer 空域名策略与样本拒绝相符。
- self-test-045 → implement-049 → self-test-050 → repair-053 的持久化节点与报告显示同类依赖/网络阻塞再次出现，最后由 revision budget 止损。

准确解释：SUCCESS 是“有效结构化结果被接受”，不是“测试通过”。该状态本身没有撒谎；缺的是在路由前区分环境阻塞和代码修复。
报告中的“没有产品缺陷”不能由本轮证据推出：测试尚未执行，产品正确性仍未知。
403 标记足以帮助本次诊断，但任意远端也能返回相同 header；不能把匹配这个字符串直接升级成自动授权的可信来源。

### 本地代码与一手文档

- `dual-adapter-acpx/.../ClaudeExecutionPolicy.java:83-100`：`allowed_domains=[]`、sandbox enabled、禁止 unsandboxed commands、auto-allow sandboxed Bash。
- `.tools/acpx-e1/source/src/acp/claude-execution-policy.ts:120-148`：域名列表投影到 SDK 与 managed settings，`allowManagedDomainsOnly=true`；local binding 独立投影，不能替代外网域名批准。
- 本地 ACP 0.70.0 的 `dist/acp-agent.js:4213` 将 SDK `canUseTool` 请求转成 ACP；其同级 SDK package 为 0.3.232。静态接线不证明沙箱内部域名拒绝必然触发该回调。
- 上项只证明被检查的安装包，不足以独立证明 SPM9 当时实际加载该副本。缓存并存多个版本；后续 Gate 必须冻结实际 agent 命令、解析后的 ACP 包路径/版本/SHA、SDK 路径/版本/SHA 与 embedded binary 身份，而不只冻结 acpx fork。不能仅用当前缓存反推历史进程。
- [官方 sandbox 文档](https://code.claude.com/docs/en/sandboxing)说明 managed-domain-only 模式会直接阻断未允许域名，而不是走新增域名提示；[官方权限文档](https://code.claude.com/docs/en/permissions)说明 managed 域名规则的优先级。取证日期 2026-09-07。

当前文档用于解释配置语义，不作为 frozen embedded runtime 的新实跑证明。不得把用户 standalone auto mode 与 SDK/headless 实际配置混为一谈。

## 3. Owner 与消费者地图

下表 Java 路径省略 `src/main/java/dev/dual/`。

| 边界 | 当前 owner / 入口 | 唯一消费链或缺口 |
|---|---|---|
| 工具能力 | adapter `AcpxPermissionProfile` | transport CLI 参数 → acpx permission resolver；Bash execute 获批不等于其子进程域名获批 |
| Claude 执行网络 | adapter `ClaudeExecutionPolicy` | ref/SHA → reviewed fork loader → SDK sandbox；当前空域名，无 domain grant 消费者 |
| 工具请求证据 | adapter `AcpxAgentTransport:515-568` | ACP permission 事件 → run timeline；不是任意 Bash stderr 的网络检测器 |
| 人工 WebSearch 授权 | ledger `NodeStateWriter:160-202` | RecoveryAssessment → note/attestation → `permission_approval` → handler `hasApprovedPublicWebSearch:598` |
| 业务修复 | typed self-test decision + frozen definition | SELF_TEST_INCOMPLETE → implementation loop → revision-limit human gate；该 gate 不拥有网络授权 |
| 展示/执行入口 | CLI RecoveryApproveWebSearchCommand、server WorkstreamActionService、console | 三端收敛 ledger；现有入口只覆盖 PUBLIC_WEBSEARCH |
| 自动调度 | server `SchedulerPolicy:12-29` | 仅指定 runtime recovery 分类自动 retry；权限待批准不能借用该自动恢复分类 |
| 通知 | server `NotificationService:18-36,56` | 默认 disabled，消费 scheduler 结果；CLI 打印下一步不等于系统主动通知已送达 |

Definition 声明能力；node instance 持久化当前等待/决定；run execution 持有实际 policy、进程与拒绝证据。
业务 human gate 的 approve/revise、工具授权与域名授权不能互相代替。全局 Codex 配置和 Claude sandbox 也不是同一个 owner。

## 4. 主要发现

### P1-1 拒绝到等待的链路缺失

当前网络拒绝发生在已获准 Bash 的子进程里，SPM9 没有对应 permission timeline 或 adapter signal。
`RecoveryAssessment:85-103` 只有在 execution 被分类为 permission-blocked 且找到 exact WebSearch escalation 时才提供批准动作。
所以现有网络失败不会因为“有 WebSearch 审批功能”就自动等待用户；模型只能提交不完整自测，workflow 据此循环。

### P1-2 批准到有效策略的链路缺失

handler `permissionProfile:584-590` 仅给 WebSearch 增加工具能力；`ClaudeExecutionPolicy.write` 没有域名授权输入。
UI 增加按钮、业务 gate 批准、宿主 auto mode、再次 retry 都不能使新的外网域名进入 managed policy。
已有空域名策略是有效的限制，却不是满足当前合法联网需求的完整执行模式。

### P1-3 验证所需依赖的 owner 缺口

`dual-adapter-acpx/src/main/resources/dev/dual/adapter/acpx/prompts/implement-claude.md:24-26` 禁止安装依赖并要求交给后续验证；
`implement-claude-self-test.md:22-25` 又限制获取依赖，只对任务明确要求依赖变更留例外。
普通验证自动解析已批准 pom 中的缺失包是否属于例外并不明确。样本确实按“不安装”执行。
这不是要求新增安装节点，而是必须明确一个现有执行者负责正常依赖解析，并区分“获取已批准依赖”和“修改版本/增加依赖”。

### P2-1 生命周期及通知不能直接继承“已有审批”结论

WebSearch 正常路径和一次 retry 有测试，不等于域名授权的拒绝、撤销、崩溃、重启、消费时效已经实现。
现有 grant 的 source query SHA 是溯源记录；handler 不将当前 query 与它逐字比对，实际授权范围是该次 execution 的 WebSearch 工具，不是一次查询。
同理，域名授权只能诚实声明域名级能力，不能宣称只允许 GET、只读行情或不会上传数据。
通知通道需明确“终端可见/通知已启用/发送失败”，不应另建通知平台或以默认关闭的 hook 冒充送达。

## 5. 现有 transition 与失败切面

| 前态 / action | 写入与副作用顺序 | 后态 / 下一消费者 | 失败、重启与拒绝语义 |
|---|---|---|---|
| Claude node 待执行 | 生成 run-scoped session → qualification → 写并校验 policy → transport 再验 → ensure → prompt | SDK 使用绑定策略 | 启动前校验失败不得跑 provider；不能更换文件后复用旧 binding |
| ACP WebSearch 被拒 | permission timeline → execution failure → node/current recovery | RecoveryAssessment 提供 approve/abort | 没有可信请求时不提供批准；这不是停住进程等人回答的 live dialog |
| failed node 获批准 | 锁内校验 → node READY+grant → append event → current RUNNING → instance projection | handler 读取 grant，显式 run 或 scheduler 后续启动 | NodeStateWriter:234-272 是多次写，非原子事务；写间崩溃必须按实际残留分析，不宣称冷恢复已证明 |
| 获批 retry 启动 | 新 run/session → 工具 profile 增量 → 新 policy → provider | 当前 execution 获 WebSearch 工具能力 | policy 域名仍空；同 session 不能靠改 SHA 热更新，必须保持 replacement guard |
| 执行完成/再次失败 | NodeResult 完成路由替换 instance result；普通 retry 重建 retryResult | 既有测试覆盖正常 grant 不继承 | 不能推出 start 前后任意 crash 的 exactly-once；新域名 grant 必须明确 claim/失效，不能无限继承 |
| sandbox 子进程网络被拒 | 当前只在工具输出可见；有效自测结果发布 | SELF_TEST_INCOMPLETE → 修复环 | 用户 refuse 尚无此类请求对象；普通 revise 只改业务上下文，不改网络 |
| abort / 终态 | 既有 recovery/terminal 流；session close 有既有清理 | 禁止后续业务执行 | 新 grant 应随终态不可消费；close 失败必须可见，不能用 TTL 冒充及时撤权 |

冷读到 node/event/current 不一致时：本评估要求 fail-closed、禁止猜测重放授权；不在本任务引入 R1 事务存储改造或自动抢救旧账本。
尚无覆盖这些域名授权写间切面的完整测试，故本表不是未来功能 PASS 声明。

## 6. 最小完整方案与开工边界

建议范围：Claude 一次获批 execution/session 的明确域名授权与环境阻塞恢复。先做人工发起，不先做永久/global/跨工作流授权，不做单条 HTTP request 的虚假精度。

### Phase A：operator 发起，先行实施设计

合法请求入口就是 operator 的显式域名集和 note，不依赖捕获 SDK 域名回调。模型的 Next Verification Needed / Checks Skipped / Missing Evidence 是人工判断的 advisory intent，不是授权，也不能自证 sandbox denial。

1. 从受支持的当前等待态，由 operator 明确提交域名集、理由与目标执行范围；展示已有 typed 自测证据。不能从报告自动抽域名并批准，也不把业务 approve/revise 默认为网络授权。
2. 复用 ledger 决定、锁、policy ref/SHA、新 session 和展示入口的既有机制；域名 grant 由 operator 决定生产，policy writer 唯一消费。拒绝保持停止，取消走该状态允许的既有退出路径。
3. 批准后生成新的 execution policy，域名精确受限，保留禁止 unsandboxed、禁止通配、禁止自动扩大至重定向/新域名。域名级能力不承诺 HTTP 方法或内容级只读。
4. 同批明确自测可正常解析已批准依赖，禁止擅自改版本、增加依赖、升级工具链。网络授权与这项职责修订构成同一交付单元；缓存写入的实际 filesystem 能力也必须核实，不能用 prompt 代替执行权限。
5. 明确新 execution 的目标及授权消费一次的语义；不声称恢复同一 HTTP 调用，不回滚既有产品文件，不自动重做非幂等外部操作。授权不扩大文件 scope。

必要状态差异：`approvePublicWebSearch` 要求 active node 为 FAILED；`retryActiveNode` 只接受 FAILED/BLOCKED。SPM9 当前却是 WAITING_FOR_HUMAN 的 repair gate，不能原样调用上述接口，更不能把 gate retry 成 agent 或重开已完成历史 self-test。
最小实施计划必须选定合法的 gate 决定到下一执行的路由、grant 从等待对象到执行对象的唯一传递点，以及取消/claim/失效与写间崩溃语义。只支持哪些前态必须显式列出；不为当前样本增加手改账本救援或通用回跳机制。

Phase A 的诚实边界：人工接手后可以提供完整授权续作，但不证明“首次受阻立即通知”“agent 主动申请”或阻塞前的自动修复循环已消除。进入人工授权处理后不应再用无变化的代码修复代替环境处理；此前的自动识别缺口留给 Phase B。
这些是实施计划要关闭的设计选择，不是等待未知 SDK 接口的理由。Phase A 可以立即进入最小计划评审，不能跳过上述状态设计而宣称实现已 READY。

### Phase B：自动捕获，独立门控

仅研究 pinned SDK/ACP 能否提供可归属 execution 的可信网络请求/拒绝事件。若存在，再将其接入 Phase A 的同一人工授权 owner，并在业务修复前等待；不另建授权模型。
若接口不可用，停在明确 NOT IMPLEMENTED，不靠解析 Bash/模型正文补洞，不新建 proxy/AST 平台。该停止条件只约束 Phase B，不再阻塞 Phase A。

### 不属于当前必要范围

- 自建 proxy、Bash AST 分析器、通用网络 evaluator、完整 DLP、Codex/容器总网络统一。
- OKX/Maven 域名或 HtmlUnit 版本写进 production；节点 id 或文件名特判。
- 永久授权、全局权限管理、业务 gate 重设计、自动迁移旧 ledger。
- 重跑此前完整 E1 安全矩阵。只验证所改权限的允许、拒绝、不串 session 及任务恢复。

宿主预缓存/人工执行 live smoke 是可选 operator 环境准备，不需要假装成 harness 新能力；它能帮助当前 case，但不解决“被拦时通知并授权”的产品诉求。二者应分别报告。

## 7. 通用性与完整验证下限

通用性检查：将 OKX/Maven 换成任意公共数据 API/依赖仓库，链路应不变；输入只有资源、执行身份、人工决定和 policy；批准后仍由既有产品验收决定通过。当前问题跨语言工具链存在，不需要业务识别。

Phase A 必须在同一集成序列证明：受支持等待态 → operator 显式申请/批准 → 合法后继 execution → 新 policy 被实际消费 → 已授权目标成功/未授权目标仍拒 → 原测试真正执行 → 授权失效。包括 gate 等待与 execution failure 两类前态时，分别证明路由，不能以其中一类的测试替代另一类。
Phase B 才追加：首次运行时拒绝 → 可信请求 → 等待而非自动代码修复；Phase A 通过不计作这项能力通过。
配套拒绝序列：无请求/伪造或过期 grant、换 node/session、SHA 篡改、重复点击、拒绝/abort、启动前与 claim 后崩溃、冷启动重放、关闭失败。
兄弟面：Claude implement/self-test 与非 implement；WebSearch 不升级为 WebFetch；CLI/API/console 同一决定；scheduler 不批准；通知失败不改变授权；普通代码测试失败不误分流。
仅测试“批准命令返回成功”或“policy JSON 有域名”不足以 PASS。自然 E2E 仍须完整需求验收；没有自然触发的分支标 NOT EXERCISED。

| Invariant | 要求 | 当前状态 |
|---|---|---|
| Declared by | A：operator 请求与授权；B：另加运行时请求 | WebSearch 有；网络域名缺 |
| Proven by | A：operator 决定 + 来源证据 + policy ref/SHA；B：另加可信运行时事件 | 静态限制有；新授权链缺 |
| Consumed by | policy producer → fork → SDK 同一 grant | 空域名链存在，grant 消费不存在 |
| Enforced before | provider/子进程副作用前匹配授权 | 默认拒绝有效；合法升级未闭合 |
| Failure route | 环境等待、拒绝、abort、不误入业务修复 | 样本反证了当前缺口 |
| Cannot-bypass | 完整允许/拒绝/冷启动/session/产品恢复序列 | WebSearch 局部测试不能替代，尚未完成 |

## 8. 本轮验证与下一步

本轮完成代码控制链、实际 transcript/typed run/current、安装包版本、现有测试断言与官方文档核对；未运行 Maven/fork 套件，未新增能力实测。原 1403 测试结果属于前轮 deadline checkpoint，不是本评估的新证据。
SPM9 保持 repair-053，E2E NOT PROVEN。独立评审 F1/F2 已吸收为 Phase A 先行 + 依赖职责同批，F3 补入实际运行包身份边界，F4 补充管道退出码精度。下一步是 Phase A 最小实施计划与评审，不再以 Phase B 的接口未知阻塞；本次文档修订不授权恢复 case 或放行网络。

独立评审提示词：

> 请独立只读评审两阶段修订，重点核对 Phase A 从实际等待态到获批执行及授权失效的完整链，检查 Declared/Proven/Consumed/Enforced/Failure/Cannot-bypass。判断 gate 与 FAILED node 的不同路由、依赖准备职责、授权消费是否闭合，以及是否有更小方案。不要把 Phase B 自动捕获当作 Phase A 前置，也不要把 Phase A 当作自动捕获已完成。不改代码、不调用 provider；给出 findings 与实施前提。
