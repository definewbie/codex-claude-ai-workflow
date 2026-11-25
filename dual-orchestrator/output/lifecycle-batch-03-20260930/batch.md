# B03 联合实施记录

2026-09-30 开工；2026-10-07 已完成合并批末独立评审，当前 **CLOSED / PASS（本批授权范围与离线验证）**。用户已明确同意把相关合同生产与消费链联合实施；preflight.md 的等待取舍状态为历史，采用其中 A。不新增设计机制/authority，不恢复旧格式、不启动真实运行或额外 agent，不授权提交。

## 范围与主目标

P0 生命周期可靠性；W03/D03 的分期合同、复评/批准与资格链联合 W05/D06 的写集/读会话、必要 D07 的身份/不可变完成桥接及 D08 构造迁移。D05 的 EXECUTION 验证出口按已冻结 schema 对接，不能以空字段跳过资格。D04 仅承接已有 permission C/X 字段及生命周期保留义务，不重新扩展 native 安全目标。

验收对象为：目标批准→GOAL→作者/独立复评→EXECUTION 编译/批准→唯一领取→输入准备/调用→结果验证→本地发布/路由→一致查询与恢复。完整用户链及合法/拒绝对照见 preflight §3；冻结写集 L00–L07、R01–R03 见 freeze-details §6–7。未完成任何一段不得把 helper、字段或测试数量称为完整链闭合。

## 现有 owner 与实现面

- TaskContract/validator、proposal/ContractCompileHandler/TaskContractArtifactWriter：schema v2、条款 id、GOAL/EXECUTION/epoch、不可变编译 occurrence，批准 ref 不写回被批准合同。
- ReviewDecisionHandler、AuthoredStageLineageResolver/ExecutionContext、TaskContractProjectionVerifier、四份 workflow：真实 reviewed_candidate 与人批准分离，调用方明确 required phase；相关 context/agent/artifact/交付消费者迁移。
- WorkflowFileLedger、FileLedgerStore、AtomicJsonWriter、WorkstreamLock 及原 writers：显式会话、内存中完整 after-state、prepared manifest、物理幂等补齐；已计算的 route 不在恢复时重算。
- WorkflowRuntime/AgentWorker、run writer/finalizer、已有恢复入口：短 claim/完成会话、run intent/固定完成引用、同版身份与输入资格重验；外部未知不自动重发。
- CLI/server factory 与 query/report/evidence/delivery/readers：同一装配 ledger/session，移除隐式 builtin 构造与直读旁路；BUSY/pending/INVALID 不组装混代结果。
- 相应原有测试、真实用户序列和失败注入/冷 JVM fixture；B02 LOW-1 同族承接。详细改动路径随实际 diff 相对 before-source 基线登记，搜索候选不是修改授权全集。

## transition 与失败切面

| 转换 | pre-state/触发/owner | 写序与 post-state/消费者 | 失败、重试、恢复 |
|---|---|---|---|
| L00 初始化 | 尚无受支持实例；application 创建 | 固定 runtime identity、完整旧初始化文档与 workflow 初态→manifest→全部文件/事件→移除 pending→注册索引 | manifest 前无业务变化；之后同版补齐；旧/未知身份 INVALID，不猜默认 |
| L01/L02 排队/领取 | 当前合格 node、无竞争 claim；runtime/worker | 同会话验证批准/epoch→task/execution/node/事件完整 after-state→提交 | 写前失败旧态不变；写间 pending；双 worker 仅一位获得新 claim |
| R01/R02 准备/外部启动 | 唯一当前 claim；task/transport | 持久准备与固定引用→短 L 绑定→耐久 dispatch intent→外部调用（不持 L） | 无可信完成的 intent 为 UNKNOWN；不因 PID 消失或无输出判未执行 |
| R03/L03 完成/路由 | 原执行的真实 process/outcome/候选；run 与 domain owner | 固定完成 bytes/ref/SHA→同 L 重验 claim/epoch→一次规划 task/execution/node/后继/事件/current→提交 | 物理补齐不重发 provider、不重新判定或路由；旧 epoch 结果仅留历史 |
| L04 人决定/复评自动推进 | gate subject 或当前 reviewer/author occurrence；原 gate/decision owner | 决定/检查或 reviewed_candidate、后继、event/current 同次固定 | READY 不虚构人批准；revise 走新作者与复评；任意部分写不给后继资格 |
| L05 补证据 | preparation/source/材料身份一致；原 evidence owner | 不可变材料先发布→同 L 核验并提交 gate/node/submission/后继/事件 | 孤立材料不授资格；同请求同 payload 幂等，异 payload 拒绝 |
| L06 恢复/终止/资格变化 | 已核验执行/输入；原恢复 owner | 外部停止先核实→同 L 关闭资格及 node/task/execution/current | 不明停止不称已取消；终态不重开；本地补齐不产生新 permission send 资格 |
| L07 交付 | 原批准 candidate 与目标 Git 事实；原 delivery owner | 本地 intent 提交→外部 Git→本地 observation；锁序 Git common-dir→L | replay 零 Git；未知保留现场；不能凭 APPLIED 宣称 COMMITTED |
| 查询/投影 | 同 canonical workstream 会话；read model | 无 pending 的完整 snapshot→投影 | 锁忙 BUSY；pending 明确未完成；投影失败不反转 authoritative commit |

每个 manifest、目标 replace、append、current/instance、pending 删除的前/后失败均按冻结 D06 解释；目标既非 before/after、已确认 append 前缀变化或身份不符时 INVALID，零自动覆盖。run 锁不嵌套 L。新旧 binary/旧 ledger 不在本批自动兼容范围。

## 测试与评审义务

先写可判别反证，保留先红记录；合法目标空写范围、计划新文件提案、READY 自动推进、首次 EXECUTION 必须真实批准、同授权技术修订重新复评可续作、越范围重批、补证据及终态只读同样必须通过。非法拒绝包括旧 schema/错 phase/错 SHA/跨 epoch/假 reviewed 或人工决定。

持久化验证包含 manifest 前后每次写、append 碎片、末尾删除、真实冷 JVM、并发 reader、重复领取、未知外部零重发和合法本地补齐；覆盖 inline/worker、四变体和查询/证据/交付兄弟入口。基础机制测试不是全链证明。

批末回查 Declared by / Proven by / Consumed by / Enforced before / Failure route / Cannot-bypass test，并按 R3 §7.1 交用户发起 CC 独立评审。当前一切新增验收 NOT RUN。W03/W04/W05/W08 不能因开工或局部绿色被自动关闭，真实 Gate NO-GO 不变。

## 中间核验登记

详见 [progress-and-decision.md](progress-and-decision.md)。本地协议切片 12 项重核通过；测试源码 clean 编译因旧 fixture/API 未迁完失败，完整批次不得报 PASS。D05 事前环境声明生产入口待明确；GOAL 可保持未知，不能误把实施后补证据当事前证明。所有增量保留，未提交。

## 2026-10-06 D05 接线决定补记

上一段“待明确”为 9 月 30 日状态。经 CC 中间评审和用户继续指令，已产出 [事前可行性接线决定](d05-readiness-wiring.md)，含 LOW-1/LOW-2、具名入口与真实生产者限制、L04/L06 写序、编译/dispatch 消费、缺项恢复及回计划复评。新增设计随本批末一并独立审查；未实施、未新增测试证据。范围仍为已授权 B03，不增加环境平台/权限/runner；D07 的资格与 epoch 消费仍依赖联合合同链，不按独立切片提前关闭。

## 2026-10-06 联合实施检查点

接线决定后已继续编码及离线验证，见 [checkpoint](implementation-20261006/checkpoint.md)。readiness 保存/资格消费与 plan 门合法续作已有切片证据；全模块生产编译通过，core 123、ledger 151 通过，runtime 13 项为单独编译的定向测试。旧 runtime 测试整体编译仍失败，整链、运行身份与 R01–R03 等仍未完成。此前“全部 NOT RUN/未实施”为开工或补记时点，当前证据以本检查点为准；B03 IN_PROGRESS、W 项未关闭、真实 NO-GO 保持。

## 2026-10-06 本次续作：运行身份、执行协议、返修与消费者

此前“旧测试未迁/身份及 R01–R03 尚未完成”的段落是历史中间状态。当前实现与逐项证据以 [continuation 检查点](continuation-20261006/checkpoint.md) 和其 verification.json 为准。本轮完成 Dual Java 运行内容身份装配、L00/唯一 claim、R01–R03 固定准备/意图/完成与本地恢复、UNKNOWN 明确裁定、readiness 失败回作者/复评、合同/查询/补证据/交付消费者及测试迁移。已删除无调用的 mutable-run 猜测恢复路径，未恢复旧合同 fallback。当前全模块离线覆盖为 1,604 项、零失败/错误/跳过（完整扫描 + 定点修复和 CLI/server 补跑，生产源码相同；不是单次全绿），具体逐 suite 证据与来源见该检查点。新 CLI/server 包的独立 JVM factory/L00 跨入口读取通过；不是运行服务/真实 provider 证明。不引用 B01/B02 旧数字。

B03 **继续 IN_PROGRESS**。仍需用户确认的 D05 内部工具执行证据语义见 [具体事实与取舍](continuation-20261006/d05-evidence-decision.md)：原生适配器的部分 exit 字段是合成成功/失败状态；不能冒充真实子进程退出码。依赖它的程序事实复用及机器验收消费未完成。运行身份也不冒称已覆盖全部安装 native/agent command/配置；D04 原生权限桥接没有因本轮本地协议自动完成。W03/W04/W05/W08 未关闭，真实运行 NO-GO、未提交及零额外 agent 不变。独立 CC 应审查已实施完整链及这些未竟项，不能用全绿或作者自查替代批次完成判断。

## 2026-10-07 合并批末收口

CC 独立评审 PASS、无新增 WARN/FAIL；接收时 2,985 项受测输入无漂移。本批按 §7.1 收口，历轮中间 IN_PROGRESS/未竟声明保留其历史时点。详见 [评审处置](closure-20261007/disposition.md)。单轮 1,656/199/0 是离线凭证，W 项总体工作、批外边界和真实 NO-GO 保持，不授权后续批次、提交或真实运行。
