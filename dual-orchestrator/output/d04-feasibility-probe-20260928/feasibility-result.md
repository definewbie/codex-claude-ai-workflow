# D04 必要读取与禁止写入／执行：离线可行性核验

日期：2026-09-28。用户要求先验证可行性。本轮无模型调用、无 provider session、无 adapter/产品源码修改。诊断程序仅位于本目录，数据样例位于新建临时目录；未恢复历史实例。

## 结论

**联合可行性尚未证明，D04 不冻结。** 已实测 acpx 宿主处理器的“读可用、写/terminal 拒绝”组合；这不能替代 Claude/Codex 原生模型工具的证明。没有证据宣称方案不可行，也没有证据宣称只需参数绑定即可实现。

本轮推进了两个实际判断：宿主处理器具备候选能力；Codex 当前业务代码没有发现通往该读取接口的调用，因而不能直接把 ACP 读取当作已有完整替代路径。是否需新增工具适配或改变无执行约束，仍取决于原生工具实验；不能现在就下“必须改引擎/建服务”的结论。

## 1. 已执行的成对探针

[Gate](probe-gate.md)、[诊断程序](host-probe.mts)、[原始结果](host-probe.json)。通过 tsx 直接导入未修改的 AcpClient，调用真实宿主 handler；没有复制 handler 实现、替换文件系统或 mock 权限函数。没有建立 ACP wire 连接或模型 session。7 项断言通过，其含义分别是：

| 场景 | 观察 | 可得结论 |
|---|---|---|
| approve-reads / 非交互 deny，读取完整样例 | 返回准确内容 | 宿主正常读取可用 |
| 同配置，按行读取 | 返回指定行 | 宿主分段读取可用 |
| 同配置，修改已有文件 | PermissionDenied，内容不变 | 该处理器在此配置下拒绝修改 |
| 同配置，创建文件 | PermissionDenied，文件不存在 | 该处理器在此配置下拒绝创建 |
| 同配置，请求 terminal | PermissionDenied，执行标记不存在 | 此拒绝来自权限处理器，而非仅能力声明 |
| deny-all，读取相同文件 | PermissionDenied | 全拒绝会破坏合法读取，不能充当正向可行性证明 |
| terminal=false + approve-all，请求固定临时命令 | 命令执行，临时标记出现 | **terminal=false 不是请求时硬拒绝边界** |

最后一项是刻意放宽权限的对照，不是候选只读配置发生了越权。源码对应：AcpClient 注册 terminal handler，构造 TerminalManager 时不传 terminal capability；后者在 spawn 前消费 permissionMode。因此不能把 `--no-terminal` 本身当作执行硬上限。现有设计已经要求 forbid approveAll，但将来的受限角色仍须拒绝能力更新/权限放宽，而不是仅隐藏 terminal 声明。

本轮只在临时样例上执行固定 Node 命令；没有让模型执行命令。该宿主切片的证明不涵盖原生编辑器、shell、MCP、链接路径、权限更新、恢复/correction 或程序工件归属，不提升 I3 成熟度，也不称整链 PROVEN。

## 2. Codex 原生与 ACP 路径

固定 Codex CLI 0.153.4 的离线 `features list` 接受进程级 `features.shell_tool=false`，返回 `shell_tool stable false`，退出 0。没有写用户配置；保存[命令/原始输出](codex-feature-probe.json)。启动仍提示无法创建 PATH aliases；未提权，也未将此系统警告当隔离反证。

[官方配置参考](https://learn.chatgpt.com/docs/config-file/config-reference)描述该键控制默认 shell 工具。本机验证仅确认固定版本认识并解析该键；**不证明关闭后的实际模型工具集合、其他执行面被关闭或读取仍可用**。输出中 unified_exec 等仍有独立有效值，不能由此推断它们必然绕过，也不能由单一开关推断所有执行面都已关闭。官方当前文档不是固定版本运行行为的替代证据。

[作用域搜索](read-route-search.json)对固定 Codex adapter 1.10.0 完整 bundle 分区：`readTextFile` / `fs_read_text_file` 等命中都在内置 ACP SDK；从 `src/CodexJsonRpcConnection.ts` 起的业务部分对六个候选接口字面量均为零命中。结合已读 threadStart/sendPrompt 链路，本轮**未找到**常规模型工具→ACP 宿主读取的连接。文本搜索不是语义穷举，亦不覆盖原生二进制内部，不能写成“引擎不支持读取”。

既有 app-server `fs/readFile` / `fs/readDirectory` 是客户端请求；不是 ACP agent→client 回调，也不是已提供给常规模型的工具。dynamicTools 是可能扩展接口，但当前 adapter 未见传递；本轮未新增动态工具或服务来弥补缺口。

## 3. Claude 对照与共同断点

固定 Claude adapter 0.70.0 + SDK 0.3.232：SDK 声明 `tools` 控制内置工具集合，Read/Grep/Glob 可显式选择；adapter 采用 `_meta.claudeCode.options.tools` 并传给 query。这比 Codex 当前路径更直接，但仍只是接口/源码证据。

重新追踪 Claude ACP readTextFile，仅确认协议转发方法及其包装，未找到模型 Read/Grep/Glob 消费该回调的证据。因此不能用宿主 7 项断言替代 Claude 原生限制验证。原生工具集合、MCP/别名/额外工具、settings 合并与 session 重建尚未实测；两 provider 都不能由 prompt 或“没有观察到写入”获得 PASS。

## 4. 控制链和冻结缺口

| 链段 | 本轮证据 / 缺口 |
|---|---|
| 角色→request/profile→acpx session | 保留已有候选；未实施 readOnlyBinding，不能声称有硬上限 |
| provider 常规 turn→必要读取 | Claude 有工具集合接口依据；Codex 无 execute 下读取仍未证明；ACP 宿主链未接通证明 |
| ACP host read/write/terminal | 上述受控直接 handler 断言；实际 wire、模式更新和兄弟工具面未证 |
| 原生工具、恢复/correction→相同限制 | 尚无实际拒绝和合法续作证据；每 turn policy、load/resume 元数据缺口仍在 |
| 程序产物→baseline→发布/复评 | 本轮未改动，继续依赖 D06/D07 与可信 writer 冻结；不能由宿主拒绝跳过收尾核验 |

六维：Declared by=详细设计 §8；Proven by=本目录仅宿主 handler 切片；Consumed by=真实 FileSystemHandlers/TerminalManager；Enforced before=该配置下 read/write/spawn 前检查；Failure route=PermissionDenied，尚未证明 Dual 整链原因保留；Cannot-bypass=deny-all 正向反例及 terminal=false/approve-all 反例。完整 D04 缺少模型工具入口、强制上限、恢复和发布消费证明。

## 5. 下一证据与停止边界

继续文字评审不能补上当前行为证据。下一步应是**有界的原生能力实验**，先解决固定 Codex 引擎在关闭执行工具后是否还有可独立使用的读取/搜索，再验证 Claude 对照；它是可行性验证，不是实施 W03 或迁移整个 binding。

实验仅允许固定版本、临时样例、显式有效配置及可观测工具请求/拒绝；必须同时验证合法发现→读内容→回答，以及修改/创建/执行被阻止。错误工具名、模型没有尝试、只读到预置上下文或未触发拒绝均不能算成功。若只可通过受限 shell 阅读，那是改变“无 execute”验收，应停等用户选择；若需新工具服务/引擎修改亦同。

当前授权范围下没有启动该原生实验：详细设计 §8.5 明示需要改代码构建验证须先满足实施授权，真实 provider 另按 Verification Gate。后续若用临时探针接原生接口，应明确“只授权验证代码/固定成本实验，不授权产品实现”，并在运行前通过 Gate（实际 provider/model/配置、数据与写入边界、预算、PASS/FAIL/INVALID、停止条件及版本身份）。本报告不虚构尚未核实的运行前提或预算。

**本轮未证明成本收益已经偏离，也未证明联合前提成立。** 不自动扩大为新的权限平台，不切换同 provider，不将沙箱 shell 偷换成“无执行工具”。其余 D04 冻结缺口和整体实施未授权状态均保留。
