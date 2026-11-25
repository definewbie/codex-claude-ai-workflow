# D04 原生边界补证与设计复审材料

日期：2026-09-29。范围：补齐剩余只读可行性证据、回写具体绑定设计，交用户自行发起 CC 复审。无产品/adapter/安装包修改、无真实模型调用、无额外 agent、无旧样本恢复。本轮诊断共 6 个 fresh native session（Claude 3、Codex 3），不是 6 个模型任务；本地桩产生的 usage 数字是合成值，不能算真实 token/账单。

**作者结论：两客户端的基本只读执行面已有正反实证；D04 仍 DESIGN GAP，剩余明确阻塞是 Codex 额外协作控制面的强制限制尚未证明。** 这不是结论预填给 CC。CC 应自行判断证据、冻结边界与尚需取舍，不能把本文当 PASS。

## 1. 已完成的补证

| 问题 | 实际结果 | 证据 / 未外推范围 |
|---|---|---|
| Claude Code 既有 GLM 路由能否必要读取 | 上轮真实 GLM 切片通过 | [旧实证](../d04-claude-code-glm-20260929/result.md)，不重复调用；客户端/后端分离不变 |
| 模型绕过可见列表，强行返回 Write/Edit/Bash/Agent/未知工具 | native 实际拒绝 | [restricted 事件](protocol-r2/restricted/events.jsonl)，9 个隐藏工具调用（Write 5 个目标、Edit/Bash/Agent/未知工具各 1）；桩只发命令，没有伪造工具结果 |
| Claude 越界 Read 与经符号链接越界 | native 实际拒绝 | 同一序列另 2 个负例；合法随机 Read 成功。不能以所有读取都失败满足安全 |
| “拒绝”是否只是探针没接通 | 开放对照真实写成功 | [独立 fixture 对照](open-only/open-counterproof/result.json)，只开放新建样例的 Write；产品/旧样例不受影响 |
| Claude 用户/project/config 中的权限与 hook 诱饵 | 未覆盖候选限制，hook 未执行 | 三处样例 settings 的 model/permission/hook 诱饵，实际请求/工具仍为固定值；不冒称所有系统 managed policy 已穷尽 |
| Claude 程序 scratch 与模型写的区分 | 模型 Write scratch 被禁；native 自身产生配置缓存 | `.claude.json`/backup 出现在独立 config 目录，模型没有写入资格；native 还报告 messaging_socket_path，须归程序 writer，不承诺进程完全不写任何地方 |
| Codex Code Mode 是否只是 shell 的未受控外壳 | 实际宿主测试通过这一切片 | [工具结果](codex-protocol/tool-probe.json)，process/require/fetch/WebSocket 均不可用，node:fs import 被拒；真正 tools.exec_command 读成功 |
| Codex 产品/control/scratch/runtime config 写 | 原生执行器拒绝 | [配置及写入切片](codex-protocol/config-probe.json)，5 个写目标均失败且内容未变；不是模型口头自报 denied |
| 原生 apply_patch / 请求提权是否绕过 shell 限制 | 原生拒绝 | 同上，read-only 拒 patch，never 拒 escalation；不申请也未获得测试目标的提权 |
| Codex 用户配置/AGENTS 注入 | 样例诱饵未进入实际请求 | fresh runtime home、ignore-user-config/ignore-rules、project_doc_max_bytes=0；请求中无两个 canary。不宣称 read-only 等于全宿主读取隔离 |
| Codex native 额外协作入口 | **仍暴露，硬限制未证明** | 实际 `additional_tools` 有 collaboration.spawn_agent/send_message/followup_task 等；同一 command 中 multi_agent=false、multi_agent_v2=false。未调用，未产生子 agent；不能从暴露直接推断可执行，也不能从开关名称推断已禁用 |

[原生 Gate](verification-gate.md)、[Codex Gate](codex-protocol/verification-gate.md)、[可重跑的离线证据检查](verify-evidence.py)、[24 项检查结果与剩余阻塞](evidence-review.json)。这些验证针对固定 native/host/SDK 与声明切片，不宣称任意版本、任意工具或所有攻击均已安全。

## 2. 诊断失误与证据可信边界

Claude 第一次 native 执行完成后，汇总器把 system/permission_denied 的字符串 message 当对象而崩溃；一次 fresh 重跑后，汇总器又把成功 tool_result 省略 is_error 误判为失败。两次都属于诊断消费假设错误，不是客户端安全失败，也不能保留 INVALID 后就假装原始测试曾 PASS。

原 driver/事件/错误汇总都保留。随后停止重跑负例，核对本地 SDK 的 `ToolResultBlockParam.is_error?: boolean`，对已有不可变原始记录做作者离线重判：[声明和纠正记录](typed-event-correction.json)。24 项核验包含正常省略/显式 false、错误携带正确 oracle、缺结果、system 文本分支等对照。原生正向读取有预置文件 SHA+真实随机值支撑，11 负例各有实际错误结果和前后文件证据；开放对照单独运行并成功。CC 应同时审这些错误及重判，不只看最终数字。未冒称作者重判为独立评审。

Codex 先用一次本地 Responses 捕获实际工具契约，再按该契约注入真实 custom_tool_call；Code Mode 输出来自 native host，协议桩只返回命令/结束文本。两次实际工具切片均 exit=0、目标文件不变、两次本地请求完成；未与外部模型通信。原记录在 [capture](codex-protocol/capture.json)、[tool-probe](codex-protocol/tool-probe.json)、[config-probe](codex-protocol/config-probe.json)。合成 usage/最终“完成”文本都不作成功证据。

停止机制的限制也须登记：Claude driver 对文件变化有轮询停止；Codex 两个负例 payload 虽串行执行，但没有把“某一次写返回成功”编成后续调用前立即抛错，driver 只在收尾比对。因本次所有写都被拒，未出现继续处理已成功越权的事实；这不改变已观察到的拒绝证据，但不能称该 Codex 探针完整履行了首错止损的机械化义务。原脚本不倒改；已在 [修正副本](probe-guard-repair/README.md)补同源串行 guard 和外层变化轮询，并以 5 项离线故障注入验证首次/后续成功、未知结果及 transport 异常后零后继调用。修正副本无新 Gate/授权记录即拒绝启动，未另开 native 会话；这不追认旧运行的 Gate 机械化已完整通过。D04 原生切片结果与 Gate 执行纪律评价分开。

## 3. 具体绑定选择及实施落点

本文补充详设 §8.3–§8.7、原 freeze-details §4.1；不是新增 model/权限平台或另一工作清单。以下是设计目标，当前产品未接入。

| 边界 | 固定设计与现有落点 | 失败/兄弟路径 |
|---|---|---|
| 连接/模型与权限分开 | TaskHandler/transport 读取既有运行配置，分别固定连接身份和角色能力；保留必要认证/endpoint/model，不能为禁 hooks 而切到默认 Anthropic。模型品牌不进入核心路由条件 | 未识别/漂移身份拒绝，不 fallback；GLM-specific 分支不进 Dual 核心；费用未知保留原 usage/model/price 状态 |
| Claude native 选择 | 固定可复现 native 2.1.277 和 SDK 0.3.232 的候选组合；现有 claude-agent-acp createSession 已通过 CLAUDE_CODE_EXECUTABLE 支持显式 native，不需要改 engine | 未经核验的 bundled native 不可替代；不自动升级全局命令/依赖。资格先核验路径/SHA，缓存同版本重验 |
| Claude 参数 | `_meta.claudeCode.options` 已支持 tools/env/settingSources/extraArgs；候选只读使用 Read/Grep/Glob、safe-mode+restricted、无自定义工具/配置与 dontAsk | ACP 最终 options 的 permissionMode、MCP/hook/env 覆写顺序必须在实现中收口。接口可传不等于安全绑定已实现；拒绝 model/session 动态放宽 |
| Codex 参数 | pinned 0.153.4 + 实测 host：真正 readOnly policy / never，保留必要 Code Mode/shell/unified_exec；readonly mode 的 workspaceWrite 映射不能沿用 | 新建/每 turn/load/recreate/correction 同一上限；不能只修首次 session。CLI 切片不是 app-server 接线的证明 |
| Codex 配置隔离 | 禁无关外部能力，业务输入由固定 refs 提供；无需读取 AGENTS 的受控角色使用经实测的 project_doc_max_bytes=0，原项目资料按显式输入传入。运行配置、认证和项目文件分别归属 | 不能因禁自动发现而丢掉必须业务文档；implementation 后合法设计/复评序列验证。模型读取权限不宣称与 Claude --restricted 完全相同 |
| 程序 scratch/writer | prepare owner 创建 task 私有 runtime/config/temp 目录，程序 manifest 记录 canonical refs/配置身份；native cache/log/socket 只按真实 writer/进程/时点归属。model 可访问的写入口仍零产品/control/运行配置写权 | Claude config 缓存及 native socket 与 Codex runtime home 均纳入准备/收尾/恢复同一盘点；路径命中或父目录在 tmp 不可自动豁免。D06 未提交 binding 不发调用，D07 未知外部结果不盲重放 |
| 回执与完成 | adapter 报实际发送对象/可用 native init，typed run 事实与输入/binding SHA 进入 D06/D07；模型声称 denied/JSON complete 不授资格 | CLI/API/UI/report 只投影；上轮 GLM 额外文本不通过严格 contract，不能用诊断 JSON 提取改写产品 validator |

静态落点见 [adapter-binding-sources.json](adapter-binding-sources.json)。已确认 Claude options 与 native executable 路径存在；现有 Java/acpx 严格传播及 mode/session/recovery 校验仍是实施工作，没有声称接入已完成。九项设计冻结不被这份诊断自动重开，范围清单与未提交改动对账仍须在实际实施准入时重核。

## 4. 剩余阻塞与止损

当前明确未决项：**固定 Codex 的额外 collaboration 控制面如何由程序强制限定，而非仅依赖模型遵守指令。** 这关乎流程是否由 Dual 控制 agent 数量/执行身份/预算，不能用 shell read-only 代替证明。证据也不足以反向断言“该 native 一定无法限制”或“已发生越权”。

本轮没有启动其子 agent 来碰运气，也不凭未知配置名不断试跑。局部 native 会话预算已完成，后续依赖该限制的“全能力上限已证实”声明停止。仍可走的优先路线是核实已有 native/adapter 的硬限制接口；若最终只能靠外部拦截服务、engine 修改，或接受 native 自行委派及其成本，则触发 §8.6，提交证据和选项由用户决定，不能自动变成新的平台工作。

冻结前必须回答这项能力上限问题；实现后才验收的仍是 request→acpx→adapter 全入口传播、实际回执、D06 写入失败/冷读、D07 恢复及四变体组合。严格输出、真实用户研发交付仍走 W02/W08/W09，不用本地桩替代 real-case proof。上轮纯 JSON 提示未满足属于这部分的待验收输入，不导致放宽合同，也不无故再增一轮模型实验。

## 5. 六维自查与提交范围

| 不变量 | Declared by | Proven by | Consumed by | Enforced before | Failure route | Cannot-bypass |
|---|---|---|---|---|---|---|
| 原生只读切片 | D04 profile / 两份 Gate | 原生合法读取、隐藏调用拒绝、开放对照、Code Mode/patch/提权负例 | native 分派/执行器；作者只消费结果 | 已测工具写入前 | 原生 error，保留现场；未触发不记成功 | fake backend 强行发隐藏命令仍拒；移除 Write 限制的独立 fixture 真写成功 |
| 全部额外控制面 | D04 上限与 D07 执行身份/预算 | **缺 collaboration 硬限制证明** | 应由 native/adapter 唯一强制，尚未确定有效接线 | native 自行创建/控制 agent 之前 | 保持 DESIGN GAP；不运行未知入口 | 尚无合规可用反证，不能写 PASS |
| Dual 整链 | R3 / D01–D10 | 实施后义务，当前未通过 | qualification/transport/session/run/ledger/read model | 首次和后续副作用前 | 按已冻结 L/R 写集、UNKNOWN/INVALID | 四变体/inline/worker/冷启动/同边界 sibling；未声称本轮覆盖 |

交 CC 的对象是当前详细设计、原冻结附录、两项 LOW 补记及本文件原始证据。请独立决定可冻结范围与必须解决的缺口；本轮未给整个 D04 PASS，未授权实现、提交、真实 E2E 或历史恢复。
