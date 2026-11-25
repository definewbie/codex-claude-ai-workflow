# B04 实施前核对：原生权限请求到合法续作（2026-10-07）

当前 **PREPARED / NOT IMPLEMENTED**。B03 已收口；用户要求无需重复 CC 时进入下一步，本轮进入已冻结 D04 §8.25/§8.27 及 D06/D07 受影响接线。没有修改产品/测试/patch 或用户配置，没有运行测试、native/provider、额外 agent 或提交。不是 B04 实施 PASS，也不是原生能力证明。

## 主目标与必要性

P0：客户端真正需要人的许可时，保存精确请求→人选择批准/拒绝→同一存活请求一次回复→合法继续或拒绝/恢复。推进 W03/W05/W08；不是关闭这些总体项。B03 已有 claim、R01–R03、prepared 本地写集和权限证据消费，缺通用在途决定/回传。一直保持非交互 fail 只能拒绝，不能替代该正常用户流程。不能重发 prompt 来假装原地续作。

选择依据是原冻结设计与当前代码，不为选项增设一轮材料评审。批末按 §7.1 由用户发起 CC 合并独立判断；如实施中发现设计缺口或成本/收益偏差，先回到具体边界，必要取舍交用户，不自行补新平台。

## 核实的现有接线与差距

| owner / 当前事实 | 可复用 | 仍须实施 |
|---|---|---|
| L：ExecutionLedgerWriter/WorkflowFileLedger | claim、prepared_run、受保护执行事实合并；部分 readiness/恢复入口有 closed guard | 没有精确回复 C 提交；当前 closePermissionReply 拒绝 RUNNING/CREATED/READY，不能冒称完整在途 X；全族失效入口与完成 writer 需同锁排序 |
| R：AgentRunCommitProtocol/handler | R01、run 锁、命令记录/原 executor 守卫、固定 R03与未知裁定 | 不可变完整 request/decision、逐请求关联与外部观测；决定保存不授发送资格 |
| acpx ACP client | SDK onRequest 已有 requestId/signal（elicitation 入口使用）；permission callback 存在 | permission 入口未传逐 RPC 上下文，宿主异常可 fallback，kind 映射可扩大选项；桥接要复用 resolver 自动允许/明确拒绝，只把 ask 交人，原 optionId 精确回复，进入 ask 后故障取消 |
| acpx queue owner / IPC | prompt 外的 cancel/close 等控制入口、owner generation、当前 turn controller | 没有 permission reply 消息/命令；增加已有协议成员并强制 generation、prompt/RPC、取消/期限、一次消费；权限回复不排到正等待它的 prompt 后 |
| transport / health | 有当前调用进展超时、总 commandTimeout 配置、原恢复与 stop 记录 | 持久化待决请求才可显示等人；不能把等人当模型停滞，也不能无限等。默认 commandTimeout=0，600 秒进展超时不是有限总预算；启用在线等待须绑定显式有限已有总上限，未配置不能假定有界 |
| CLI/API/UI/report | 既有应用操作面与中央查询、两 UI 消费同源 | 逐请求展示实际选项/范围、期限、决定保存/C在途/原生结果/X停止确认，并调用同一决定入口；不新建 workflow gate/权限类型 |

核对来源见 source-index.json；文件/内容基线覆盖真实 dirty 前态，见 before-source-*.json。上表为静态核对，不证明 live 回调行为或完整模块无其他入口。

## 完整转换与失败切面（实现必须遵循）

L 是既有 workstream 会话；R 是既有 run 锁，两者不嵌套，不跨原生调用或人工等待持锁。完整字段/写集/12种交错和失效入口义务复用 [已冻结提交协议](../d04-permission-wiring-audit-20260930/reply-commit-protocol.md)；该文件的历史候选状态由详设 §8.27 用户确认覆盖。

| pre-state / action / owner | 固定写序、后态与下一消费者 | 失败 / 重启 / 拒绝 |
|---|---|---|
| 当前唯一 claim、批准/epoch；dispatch / transport | L资格→R01/R02→原生配置与有限预算；原生自动允许/明确拒绝保持既有规则 | 准备无可信记录零启动；intent 后不明走 D07，不复用旧一次许可 |
| live ask / acpx owner | 原 RPC/逐请求 signal+owner generation+prompt identity→R不可变请求→统一待决 read model | 无持久化请求不能显示可批准；取消/过期只保留历史，故障不 fallback allow |
| 人选择实际 optionId / 应用→R | 身份/选项校验→不可变决定保存；决定 ref/SHA供L消费 | 零发送；同键同内容幂等、异内容拒绝；多请求逐项决定 |
| 业务资格仍有效 / L | 同L内重验claim/批准/epoch/deadline/请求决定SHA→prepared C+event→完整提交返回 newly_committed | 部分写只有pending，零外部回复；补本地或重启不重获发送资格 |
| 原live调用获本次C / transport→acpx | 一次IPC（maxAttempts=1，无retryable重送）→acpx同请求校验与先消费标记→精确optionId→R送达观测 | 取消/错owner/过期拒绝；回执/回复/观测不明保留UNKNOWN，零补发；不把pipe成功当执行成功 |
| 人拒绝 / 同一应用链 | 原reject option精确回复，取消来源与人deny分开 | 未授新业务权限；不换工具/模式借权，不把其他工具副作用说成零执行 |
| 资格撤销/结束 / L | 与C同锁提交X并保留历史→释放L→原生停止核对→R观测→L后续 | X先则零新C；C先可能在途（用户已接受）；未确认停止不显示已取消，不启动重叠替代执行 |
| 工具/turn结束 / R→L | R03固定→L03重新核claim/epoch及scope/验证→复评/验收 | 许可不等于业务成功；写失败只补固定字节，不重发provider |
| 重启 / 固定记录消费者 | D06只补本地，D07核结果或人工裁定；旧/未知Dual身份INVALID | 不恢复旧Promise，不从磁盘C发送，不将原生升级版本未知单独拒绝 |

## 一个完整批次的实施与结束条件

1. acpx 现有 callback/resolver/queue/IPC/CLI 相互接通；本次请求精确 optionId、逐请求取消、generation及一次发送条件可离线行使；宿主 fs/terminal 开放的确认入口同语义，未支持的明确拒绝，不伪造接通。
2. 原 L/R owner 保存请求/决定、提交C/关闭X、一次live发送及固定恢复；abort/cancel/epoch/scope/批准失效/stale/retry/node完成/替换claim逐入口检查，完成writer不覆盖事实。
3. 当前应用入口与 CLI/API/两 UI/report 同源呈现；批准保存、C在途、原生结果、取消未确认分别表达。默认不开放持久授权或模式扩权，不新增命令分类器/LLM裁判。
4. 合法 ask→批准→同RPC继续→合格结果→独立复评，以及 deny→不执行被拒操作的成对链；原自动许可仍可用。inline/worker/correction、多请求分项、同id异内容/换owner、取消/超时、C/X barrier、各写前缀和冷JVM重读、IPC失败零补发都须证明，不能只测Map存在或所有路径被拒。
5. 稳定源码下做必要聚焦与单轮全模块回归，源码/patch身份前后核对；相对批前dirty单列增量；最后合并CC独立评审。协议桩/本地Java证明不升格真实native能力，真实验证另走Gate。

上述是一个联合结束条件，不按owner拆成独立完成声明。新发现必要兄弟入口归同一清单；真正扩大目标/authority或成本越界时停止取舍。

## 预计改动面与ROI止损

候选是既有 acpx client/types/permissions、queue messages/IPC/server/current owner、session运行装配/CLI注册，既有 Dual execution writer/ledger、run protocol/handler/transport/health、应用动作/统一查询与 CLI/两 UI及对应测试；受审patch及其build-info/配置检查随实际内容同步，不能只改忽略的 .tools 源码。按实际触及文件持续登记，不以此搜索候选自动批准任意修改。

收益明确：补“合法需要人许可”的实际续作出口，并防错请求/已终止请求借权；不是宣称节省多少成本、兼容任意未来版本或消灭恶意越权。增量需要 adapter 和跨owner接线，不叫一个布尔开关；没有经过实施测量的工时/ROI数字。不新建服务、独立授权账本、权限DSL、引擎/沙箱/内核或通用监督设施；不为回复普遍重跑长任务。若现有机制无法承担，按原 §8.6 提交证据和取舍，不默认继续扩大。

## 六维与当前结论

Declared=详设§8.25/§8.27及用户在途语义确认；Consumed=L唯一C/X、原live sender/acpx当前回调、R外部事实/统一投影；Enforced before=批准提交前L身份资格+原RPC回复前取消/身份/期限（实施目标）；Failure route=D06本地固定补齐、D07外部UNKNOWN；Cannot-bypass=上列合法/拒绝/竞态/重启反证；Proven=当前仅源码/设计核对，产品运行NOT RUN。**不宣称B04闭环或实现PASS。**

作者分析/复核：现有设计没有因本轮核对出现新增平台性选择；已有partial closed字段不能误算完整C/X。下一工作是按本批整链落码与反证，不需要为既有设计和本次事实核对另开CC轮次。W01诊断配对、W06真实Git窗口、W07回显、W08总体组合与W09真实任务仍按主清单保留，不随B04主目标消失。
