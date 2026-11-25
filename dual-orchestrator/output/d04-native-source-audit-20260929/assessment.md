# D04 同版响应协议与副作用顺序核验

2026-09-29。仅依赖源码/发布身份核验；无 native session、响应注入、provider、子代理、Rust 构建或产品改动。D04 仍 DESIGN GAP。本报告是作者自查，未获新一轮独立评审。

## 1. 为什么读 Rust，到哪里停止

Dual 的产品实现未引入 Rust。调用链是 Dual → acpx / codex-acp → Codex 原生程序；此次读取的是最后一层的公开源码，用来核实“现有接口能否在危险动作之前阻止它”。配置名称、字符串检索或报错正文不足以证明这个先后关系。源码仅作为证据保存在 output，不加入构建或运行依赖。

本轮在协议及关键先后关系可判断处停止；不扩展为上游全量审计，不修改/维护 Codex fork。若方案需要修改引擎、扩大 authority，或收益与成本偏离，须交用户决定。以下发现不授权启动实验或实现。

## 2. 同版身份与证据上限

本机 codex-acp 为 1.10.0，Codex 平台包为 0.153.4-darwin-arm64。公开 rust-v0.153.4 tag 指向 commit `3d2ee51ca2d5db578f328aa75e20aa22c0197c9a`。npm 平台包 provenance 声明相同提交、tag 与 rust-release 工作流；本机缓存归档 SHA512 与 registry integrity/attestation subject 相符，归档内 codex 与 codex-code-mode-host 的 SHA256 分别与已安装文件相符。

证据见 local-identity.json、remote-tag.json、npm-platform.json、npm-platform-attestations.json、npm-platform-provenance-decoded.json、cache-package-identity.json。这里验证发布声明与内容身份，**未独立验证 DSSE/Sigstore 签名链，也未做可复现构建**，不把它描述为二进制行为实测。

source-index.json 中每份可引用源码均通过 GitHub 指定提交的文件大小、Git blob SHA1 及本地 SHA256 核对。部分初次下载被截断；`source/` 和失败下载记录仅保留取证，不参与结论，引用只指向 `verified-source/`。此次静态结论适用于该冻结版本，不能外推到后续版本。

## 3. 协议已定位，错误类型不得混同

- `protocol/src/models.rs:976,1040–1058`：ResponseItem 按 type 反序列化；function_call 的 name/call_id 为字符串，arguments 是 **JSON 文本字符串**，namespace 可选。它与 custom_tool_call 的 input 路径分立。V2 协作 handler 接受 Function payload（spawn.rs:272–275）。
- `codex-api/src/sse/responses.rs:357–364`：response.output_item.done 中 item 解析成 ResponseItem；解析失败仅经过该分支的 debug 路径，不能解释成权限拒绝。
- `core/src/tools/router.rs:246–297`：FunctionCall 经 namespace/name 转 ToolPayload::Function；CustomToolCall 走另一分支。不能拿 exec 的 custom 格式直接证明 collaboration 被禁止。
- `core/src/tools/registry.rs:516–564`：找不到工具与 payload 类型不符是不同出口；后者为 Fatal。handler 内参数错误又在后面发生。不能仅凭任意错误正文认定策略生效。
- `protocol/src/models.rs:1075–1095`：function_call_output 的 output 可为字符串或结构化内容数组，不存在本轮已核实的统一“权限拒绝 JSON schema”。后续实验判定必须关联真实出口及 call_id，不能自行假定模型收到 success=false 就是权限拒绝。

这里定位的是 item/decoder/dispatcher 静态契约，**不是完整可运行 SSE 桩已验证**；response 生命周期、收尾和重连仍不可猜测。本轮不产出可执行注入脚本。

## 4. 控制链、失败切面与副作用

下列源码路径均相对 verified-source/codex-rs；完整材料中保留实际文件。

| 顺序/owner | authoritative fact 与下一消费者 | 此处失败能证明什么 / 不能证明什么 |
|---|---|---|
| SSE decoder → router | 解析后的 ResponseItem → ToolInvocation | 协议不合法不代表权限拒绝；尚未进入相同合法动作路径 |
| registry:508–537 | 工具查找前已增加当前 turn 的调用计数；注册表决定是否存在目标 | unsupported 阻止该注册表入口调用，但不是绝对零内部状态变化 |
| registry:549–619 | payload 校验 → PreToolUse hook；Blocked 分支 return | hook 的 Blocked 出口位于 handler 之前；配置未激活/未命中/执行失败如何解释尚未核实，不能宣布现有配置已强制阻止 |
| multi_agents_v2/spawn.rs:113 起 | payload/参数、fork、角色/model/config 等校验 → agent_control | 畸形参数拒绝只证明输入校验；不证明合法 spawn 被权限拒绝 |
| agent/control/spawn.rs:593–625 | 版本/执行容量/驻留与 spawn 槽位等前置 → 创建路径 | 容量不是禁用权限；这些阶段已可能改变管理状态，不能把所有前置失败统称无副作用 |
| agent/control/spawn.rs:677–747 | 创建线程 → reservation commit → 通知 → 记录 spawn 关系 | 创建已早于初始任务发送；不能从后续调用失败或零模型请求推导零子线程；内部具体落盘时点未全量审计 |
| agent/control/spawn.rs:755–768 → handler 返回 | 向已创建线程发送初始任务 → 成功结果/事件 | 发送失败不能排除已创建线程；结果丢失也不能安全重发 spawn。仅凭失败响应自动 retry/resume 会重复副作用 |
| codex-acp 通知消费 | 已有 child thread id → listener / UI 事件 | 当前 adapter 观察发生在子线程出现之后，不是创建前权限守卫（adapter-observation-order.json） |

结果不明或进程中断时必须保留现场；本轮没有证明原生重启/回放的幂等清理，不能把重新发送同一 call_id 当作恢复保证。停止进程也不能逆转已经发生的创建/发送。现阶段不执行有效 spawn 试探，因此没有新增需恢复的原生样本。

`multi_agents_common.rs:235–260` 将 approval policy / permission profile **传给子配置**；在已读 spawn 链中没有发现“readOnly 或 never 因而禁止创建子代理”的专门守卫。这不等于穷尽了引擎所有拒绝路径，也不证明任意 spawn 都会成功；它足以否定单靠这两个配置名认定禁止委派。

## 5. 兄弟入口与一个尚未证明的现成候选

**list 与 spawn 不可互证。** list_agents.rs:42–52 先解析参数，再 register_session_root，随后查询；它不走 spawn 创建链，但仍有内部登记。查询成功不授予/拒绝创建权限；称它“绝对零副作用”过强。

**找到原生 PreToolUse 的 handler 前拦截点。** registry.rs:129–137 对 Function 构造 hook payload；:567–590 消费 Blocked 并提前返回。spec_plan.rs:1493–1507 的 V2 namespace wrapper 没有覆盖默认 pre_tool_use_payload；spawn handler 也未覆盖该默认逻辑。因此它是值得独立检查的既有机制线索，不能再笼统宣称“现成调用前接口完全不存在”。但已有探针关闭 hooks；本轮没有核实可信配置装配、实际匹配名、失败/超时是否拒绝、hook 本身可执行能力，以及其他调用面能否绕过。**不能据此把 D04 改成可冻结，更不能自动重新启用 hook 或写一个拦截器。** 是否仍符合既有 adapter 绑定方向与成本约束，应先独立评审。

**web 必须分层。** spec_plan.rs:1415–1432 在 standalone web.run 禁用时跳过本地注册；hosted_spec.rs:15–20 在 Disabled/None 时不构造托管 web search 声明。这分别是本地工具注册与远端工具请求声明的证据。router 的 WebSearchCall 不作为客户端工具调用分派；伪造一个托管搜索结果不能证明本地发生了网络调用，更不能拿它测试本地“拒绝执行”。网络边界不能把 server-hosted 与 client-dispatched 混在一个注入判据里。本轮补到静态强制点，未做有效配置到注册表的运行证明，未重开 R4/Phase B。

## 6. 六维收口与决策

- Declared by：D04 只允许必要、经过证明的受限执行，不允许未经批准的委派和额外工具能力。
- Proven by：此次仅证明同版发布身份关联、源码协议、关键先后关系；原生运行证据仍取自既有材料，不新增 PROVEN 声明。
- Consumed by：协议由 native decoder/router 消费；注册表与 pre-hook 由 native registry 消费；子配置由 agent_control 消费。adapter 通知属于观察，不是这条调用链的批准 owner。
- Enforced before：本地未注册工具与 hook Blocked 有 handler 前出口；当前 D04 配置对 collaboration 尚无完整绑定证明。
- Failure route：协议/类型/参数/容量/策略错误分开；创建后失败或观察丢失按结果不明处理，不盲目重放。
- Cannot-bypass test：本轮未执行；有效禁止动作与合法读取、兄弟入口、配置漂移/失效、冷启动等组合证明仍缺。静态存在接口不能冒充边界闭合。

**本轮完成同版协议定位与关键顺序核验；D04 仍不可冻结。** 不建议立即用有效 spawn 注入补证：现有材料没有保证在子线程创建前停止。建议 CC 先评审此次证据解释、现成 pre-hook 线索是否值得继续，以及 web 判据是否应分层收窄；没有结论前不增运行预算、不实现。若需要修改 Rust/native 引擎，超出本轮与既有 adapter 方案边界，停止交用户取舍。
