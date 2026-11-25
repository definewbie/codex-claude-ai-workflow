# D04：现有硬限制接口核验（2026-09-29）

**结论：未找到足以据此冻结 D04 的已证明硬限制接口。** 两个候选配置的实际工具暴露目标均 FAIL；捕获过程有效，不是 runtime 拒绝实验。不能推导“Codex 无法限制”，也不能推导“协作工具已经能够越权执行”。本轮证据适合交 CC 独立判断是否还漏掉既有接口，尚不足以请求冻结通过。

## 1. 范围与投入

固定 native 0.153.4 与 Code Mode host，SHA 沿用既有绑定；读取 pinned codex-acp JS、新/续/分叉 session 配置及每 turn policy、留存同版协议 schema；核对官方配置参考，仅用于接口候选定位，不当作本地版本证明。

12 次无 turn 的配置检查（首 4 次因 features 子命令不支持 strict-config 而无效，原记录保留；随后 4 次有效 feature/parser 检查，4 次故意缺失 provider 的 strict-config 检查在开 turn 前退出）。2 个 native 接口捕获 session，各 1 次本机桩请求、约 0.57 秒；6 项 final-only guard 离线检查通过。真实模型调用、工具调用、子 agent 均为 0。初次 listener 被宿主 sandbox 拒绝，发生在 native 启动前；保留 INVALID_SETUP 记录，通过本次授权范围内的审批后再运行，未绕过自动审批。

本轮没有产品/安装源码、全局配置或凭据修改；没有外网工具实验、R4/Phase B、旧 ledger 恢复。官方文档检索不是被测 native 的工具网络访问。两次捕获预算已用完，不再猜参数追加试跑。

## 2. 接口逐项结论

| 候选接口 | 已核实的事实 | 对 D04 的含义 |
|---|---|---|
| `features.multi_agent=false` + `features.multi_agent_v2=false` | 隔离 HOME 的 feature list 两项均 false；旧有效请求仍带协作工具 | 名义配置读取不足以证明副作用前限制 |
| `features.multi_agent_v2.enabled=false` | strict-config 可解析；新 capture-structured 请求仍带 6 个 collaboration 工具 | 不是布尔配置改成 table 即解决；目标 FAIL |
| `features.code_mode.excluded_tool_namespaces=["collaboration"]` | strict-config 可解析；与上述关闭组合后 capture-exclusions 仍带相同工具 | 该配置在实测路径未达到排除目标；不推测其内部实现 |
| `agents.max_concurrent_threads_per_session=0` | 本机 parser 明确拒绝，必须至少 1 | 不能用零并发取代禁用；上限 1 仍允许新增 agent |
| 根级 `enabled_tools=[]` | strict-config 明确 unknown field | feature list 的 exit 0 不代表未知字段生效，不存在已证实的这个根级白名单入口 |
| `agents.max_depth` | binary 文案提示仅 V1、V2 忽略，未运行该候选 | 仅发现线索，不作硬限制证据 |
| app-server `multiAgentMode` | 同版 ThreadStartParams 声明已弃用且 ignored；enum 描述是委派指令 | 不能拿 explicitRequestOnly/prompt 替代能力控制 |
| `dynamicTools` / MCP allowlist | schema 可增加动态工具；官方已记录的 enabled_tools 是 MCP/插件作用域 | 未发现它们削减 native 内建协作工具的契约，不能混用 |
| `web_search="disabled"` | 旧、新请求都未暴露网络工具；schema 可表达该字段 | 暴露切片成立；hidden dispatch/托管工具副作用前硬拒绝仍未证 |
| read-only / never | 前轮已证明的文件/受限执行切片保留 | 不推导它们自动覆盖协作、托管网络工具 |

两次新请求均含 `functions`、`clock`、`collaboration`；后者为 followup_task、interrupt_agent、list_agents、send_message、spawn_agent、wait_agent。exec 描述仍含 exec_command，未因候选丢失必要读取入口的声明；本轮不重复读取行为证明。原始请求见各 capture 目录 `result.json`，实际命令和 env 见 `started.json`。

[官方配置参考](https://learn.chatgpt.com/docs/config-file/config-reference)描述 multi_agent 功能开关、子线程并发上限与 MCP 作用域 allowlist；也明确命令网络代理不覆盖托管工具。这里仅作说明书对照，本地行为不被说明书替代。当前官方文档不保证与 pinned binary 的全部内部功能同版。

## 3. owner、消费链与未闭合位置

Definition 为 D04 受限角色的能力上限；Instance 为 Dual 当前已批准工作/运行身份；Execution 为 pinned native 的一次调用。本轮新工件只记录 Execution 诊断，不生成新的合同、批准或业务 SoT。

| 阶段 | authoritative fact / owner | 消费、顺序与失败出口 |
|---|---|---|
| 准备 | D04 binding 与运行身份 / Dual runtime+adapter（待实施） | 固定权限与版本→session/turn 参数；缺失或不支持须在 provider 前拒绝 |
| 原生装配 | 实際工具集合与 dispatcher policy / native | 参数解析→工具装配→请求；本轮只证明装配结果，未证明允许列表在 dispatcher 副作用前强制 |
| 非法调用 | 应为 native typed rejection / native | 必须在创建 agent/访问网络之前拒绝；当前未实测，不能用事后 kill/收尾检查替代 |
| 消费与收尾 | 执行结果及 binding receipt / adapter→runtime | 失败保真→收尾核验→D06 写集/发布；属于实施后义务，不因本轮捕获自动完成 |
| 中断与恢复 | 固定 run identity、已知/不明事实 / D06+D07 | 不明副作用保持现场、人裁定，不重放；旧版本/旧身份不静默继承本轮能力结论 |

六维：Declared by＝详设 §8；Proven by＝本次配置检查与两份 native request，仅限解析/暴露；Consumed by＝native 装配已观测，adapter 全入口消费待实施；Enforced before＝协作/网络副作用前的限制未证；Failure route＝配置无效立即退出、证据不足保持 DESIGN GAP；Cannot-bypass test＝有效 hidden 控制命令的硬拒绝未测。缺少副作用前强制及其不可绕过反证，整体不能称闭环。

兄弟入口已核对 new/resume/fork 的 createSessionConfig 汇合，sendPrompt 每 turn 另传审批/沙箱；没有在 JS adapter 中找到覆盖 native 内建协作工具的独立 veto。回执/恢复/严格产物集成义务不减少。详见 [adapter map](adapter-interface-map.json) 与 [protocol map](protocol-interface-map.json)。这不是全量 adapter 审计，也没有通过 CLI 切片证明 app-server 同行为。

## 4. 止损与交付给评审的问题

当前停在现有接口的证据边界，不发 spawn_agent 或网络调用来试错。不能启动实际子 agent 的约束持续有效；删除工具展示、缩提示词、调预算、事后中断均不能替代原生副作用前硬限制。

尚未穷尽：native dispatcher 的精确 Rust 实现及与本 binary 的对应关系；管理员 requirements 的实际消费（本轮未改变管理员 authority）；未经本轮验证的 app-server 细分运行策略。二进制字符串仅是线索，未当源码证明。不能据本报告断言必须改引擎或建外层权限平台。

建议 CC 先独立判断：是否存在本轮漏掉、且能在不改 authority/引擎/分工的情况下强制上述能力上限的既有接口。若能给出可追溯接口与消费者，再形成针对该接口的有界补证；若不能，不继续猜参数，按 §8.6 提交成本收益及替代方案由用户决定。D04 冻结仍须足够证据与独立结论，产品实施仍另需授权。

本轮曾尝试把 strict-config 用于 features 子命令，命令本身不支持，导致无效检查；纠正后保留前后原记录。另确认非 strict 检查会接受未知根字段，后续不能再把“命令成功”当能力接口存在。全案目标没有变：保留客户端分工与模型无关核心，以可证明的必要受限执行完成研发全流程。
