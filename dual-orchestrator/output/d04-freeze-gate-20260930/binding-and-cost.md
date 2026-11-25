# D04 接线缺项与成本核对（2026-09-30）

这是 §8.23 所需的同一项案头工作，不另建主工作列表。源文件身份见 source-identity.json；本次 15 个引用与既有身份一致（含 binary/host），只读核对不等于已接入或运行通过。

## 具体接线与已有依据

| owner / 落点 | 已确认事实 | 必要增量 / 失败出口 | 验证时点 |
|---|---|---|---|
| Java AcpxPermissionProfile / TaskHandler / AcpxRunRequest | profile 的 autoApprovedTools 是免确认名单；implement 有 Write/Edit/Bash，不能凭未列 Agent 宣称不可用。TaskHandler 仍是准备及收尾 owner | 角色选择真实能力上限并绑定合同/occurrence/版本及 protected roots；明确准备 writer；缺事实在 dispatch 前拒绝，不能回退普通模式 | 接口/字段设计冻结；四变体/正反流程与收尾是实施后 |
| AcpxAgentTransport | validateExecutionPolicyBinding:481–495 的现有 binding 仅 Claude 且允许 null；非 Claude 携该 binding 被拒 | 新只读要求用自身受审绑定承载，保留旧网络 policy 语义；启动、后续 prompt、重连都消费，实际回执入 run，不增加第二授权 owner | 现有 transport 入口/事件 sink 可复用；实际传播后验 |
| 已维护 acpx client.ts / session-options.ts / connected-session.ts | 原接口地图有 new/load/session/queue，load 路径不能假定自动携新 metadata | 固定绑定在 new/load/recreate/correction/queue 一致传递；旧高权限或无绑定 session 拒绝；UNKNOWN 不发新调用 | 属已批准方向的接线开发候选，未授权改文件 |
| Claude acp-agent.js createSession | tools 从 userProvidedOptions 选择；最终 options 又覆盖 permissionMode、合并 MCP/hooks/env；显式 native path 接口已存在 | 固定实际工具集合/禁用面，阻止配置合并重开能力；保留真实 GLM/用户路由。只读复用 native 2.1.277 已测 Read/Grep/Glob + Agent 调用拒绝；不改模型品牌 | 只读原生切片不重测；产品合并/重连反证实施后 |
| Claude implement 角色适用性 | 只读组合证据不能覆盖含写/执行工具的 implement；免确认名单不足以证明 Agent 隐藏 | 案头选择：不为 implement 开放不必要 Agent，使用同一显式 tools 过滤点并保留实现必需工具。该共享分派点提供设计复用依据；实现后用含写工具的真实配置核验禁用/合法实现，若同版参数合并无法保留限制则重开具体缺口，不默认增跑 Claude 全套 | 不能记为 implement 已 PROVEN；不把它藏成“Claude 全完成” |
| Codex index.js AgentMode / newSession / sendPrompt | ReadOnly 实为 workspaceWrite；普通 sendPrompt 每次传 mode policy；独立审计 fork 用真正 readOnly 不能移作主路径证明 | 主任务 session/turn 使用真实 readOnly + never；受控实现用核实过的原生上限；mode/load/fork/correction 不得借权。回执来自实际参数/原生事实，不能假装有协议没有的服务端字段 | 子任务能力本 Gate 前置；Java/acpx/adapter 组合接入实施后 |
| D07 run / D06 ledger / D05 validator | 当前 run 与领域提交已有不同 owner | 原生完成/取消事实绑定 thread/attempt；不能仅 PID、最终文本或 usage 形成完成判据。发布/漂移/不明拒绝沿原 D 项，不新增子任务账本 | 本 Gate 确认事实来源可用；实际消费与冷启动后验 |

适用范围是选定版本/角色/配置。旧已审源码映射指向 npm 发布 bundle 只用于定位；实施时须在受版本控制的既有补丁/构建链维护，不直接改 npm 缓存或用户全局安装。精确实施文件范围待能力结果后与 dirty 对账一起冻结。

## 三个不能被“复用”掩盖的成本

1. **两个 provider adapter 的维护**：当前两端都需要真实参数与恢复传播约束，不能只改 Java profile。Claude 的 options 合并，Codex 的 mode/turn/recreate 是独立维护点；升级必须重验受影响切片。现有 patched acpx 只省去发布机制探索，不抵消两端维护成本。
2. **运行与领域的接线回归**：正常读取/实现、correction/session、inline/worker、收尾/恢复必须成对验证；这是 W03/W05/W08 已有成本，不另算一份 D04 平台。D2-P 报告和逐 LOW 评审已删除。
3. **权限布局与停止的真实不确定性**：可写角色下产品内 control/配置如何被原生 policy 隔离，不能由本次 workspace 外 fixture 推导；必须对照实际 roots/现有机制。若要迁移控制存储、增加隔离服务或维护引擎，超出当前接线成本，停止提交取舍。原生停止不足也不能由新的监督器偷偷补齐。

成本判断：目前可以把下一笔支出限定为三个原生 session 的诊断和现有接口案头核对，不涉及真实模型费用；这笔支出能决定继续接线或否定候选，价值明确。**尚无依据给总实施工期/金额或宣称改动很小**：真实子任务与停止结果未出，精确布局也须核对。冻结前根据结果列具体改动与验证范围，由用户批准成本；这份报告提供成本来源和止损边界，不把未知填零。

不能用临时样例上的权限成功掩盖实际目录布局缺口。能力结果出来后，只核对上述实际差异，不重开接口穷举；若差异无法通过现有机制解决，D04 仍不可冻结。
