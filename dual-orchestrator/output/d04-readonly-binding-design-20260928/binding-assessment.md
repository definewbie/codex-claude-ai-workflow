# D04 强制只读绑定：接口核验与成本边界

日期：2026-09-28。用户已同意优先细化现有 adapter 的强制只读绑定、保留 Claude 作者／Codex 评审，并要求成本收益出现偏差时停止等其确认。本轮仅详细设计、静态核验及离线协议导出；不授权实现。

## 本轮证据

[source-index.json](source-index.json) 绑定 13 个源码/声明文件的内容 SHA 与定位范围；[source-excerpts.txt](source-excerpts.txt) 保存片段。此前 mode 映射证据继续有效。以下“支持”仅指接口/源码层存在，不等于实际工具限制 PROVEN。

| 边界 | 直接核验 | 设计后果 |
|---|---|---|
| Java profile→request | AcpxPermissionProfile 只有免确认工具、权限种类、terminal 等字段；requiresFreshSession 只跟 simpleClaudeCode 关联。TaskHandler 的 policy 建立与 occurrence session 专门针对 Claude | 不能把 readonly 继续表达为 observe 别名；角色限制须覆盖两 agent。不能依赖 Claude-only fresh 逻辑约束 Codex |
| Java correction | TaskHandler 为 correction 复用 session 和 executionPolicy；transport correction 跳过 ensure，直接 run | ACP denyAll 不证明 SDK 原生工具已禁用；correction 必须受相同不可放宽上限，并证明所需更严格工具限制 |
| acpx session 元数据 | agent-command.ts 的 assignClaudeCodeOptions 只转 model、allowedTools、maxTurns；policy 单独合入。SessionAgentOptions 尚无 tools 或跨 agent 只读绑定 | Claude SDK 有 tools 接口并不等于当前命令可下传；须有明确 adapter 私有字段映射及拒绝未知版本 |
| acpx 持久 session | session-options.ts 校验 Claude policy ref/SHA 冲突；client.createSession 带 _meta，但 loadSessionWithOptions/resumeSession 未带这份元数据 | 记录相同 policy 不证明重建后的 provider query 获得相同 policy；需补传播和消费，不只补比较字段 |
| Claude 原生工具 | SDK 声明 tools 是可用内置工具集合，allowedTools 是免确认；adapter createSession 明确采用 userProvidedOptions.tools，最终 options 保留该结果 | 候选可用 `Read/Grep/Glob` 限制集合；仍须证明 native build 的读取可用、MCP/子任务等额外面未绕过 |
| Claude 环境与重建 | SettingsManager 另行读取合并配置，adapter 随后设置 permissionMode；computeSessionFingerprint 只含 cwd、MCP。SDK 声明管理层存在时 parent managedSettings 默认可能被丢弃 | 不能仅凭 settingSources=[] 或 managedSettings 入参宣布有效；policy 身份必须影响 session 复用判定，配置变化必须拒绝放宽。没有读取用户配置来假定本机恰好安全 |
| Codex 创建/执行 | newSession→threadStart；sendPrompt→runTurn 每次显式传 agentMode policy；setMode 与 setConfigOption 都能变更 agentMode | 在受限角色中建立固定上限并在上述入口强制；只改 mode 初始值不能覆盖后续控制入口 |
| Codex 工具与配置 | createSessionConfig 会合并已有 config；mcpServers=[] 时直接返回该 config，不等于关闭已有 MCP。已读协议 ToolsV2 仅声明 web_search，Config 又允许额外键 | 不能把空 MCP 列表或 schema 未列的字段解释为已禁用；环境工具与动态能力必须核验实际值。不能据未找到字段断言引擎完全不支持工具限制 |

### 本机协议导出

使用 pinned 缓存中的 Codex CLI 0.153.4 执行离线 `app-server generate-json-schema --experimental`，退出码 0；未创建 session、未发模型请求。[生成记录](protocol-generation.json)保存命令、binary SHA、保留 schema 的 SHA。启动提示 PATH alias 创建被系统拒绝，未申请提权；该警告不影响 schema 导出结果，也不是隔离反证。

- [ThreadStartParams](protocol/ThreadStartParams.json)：有 sandbox、approvalPolicy、dynamicTools 等字段。
- [TurnStartParams](protocol/TurnStartParams.json)：有 sandboxPolicy，readOnly 形状只要求 type，networkAccess 默认 false；是每 turn 及后续 turn 的覆盖参数。
- [ThreadStartResponse](protocol/ThreadStartResponse.json)：有 sandbox、activePermissionProfile 等返回；可作为 provider 程序回执的来源之一。
- [TurnStartResponse](protocol/TurnStartResponse.json)：只返回 turn，不能假定每 turn 自动返回生效 policy。
- 协议有 FsReadFile/FsReadDirectory 客户端方法，但“客户端可以读”不等于“常规模型评审已经能调用这些只读工具”；dynamicTools 同样只是扩展接口，未被本轮选作新增工具服务。

## 必须保留的未决点

1. Codex 的原生 readOnly 提供文件限制方向，**不自动等于没有 execute 工具**。在当前设计要求的无 terminal/edit/execute 下，必要读取能力如何保留，尚未证明。不可偷偷把受限 shell 改称不存在执行能力，也不可只给固定上下文就宣称任意必要代码可独立检查。
2. Claude 的工具集合接口方向有依据，但 SDK/adapter 环境合并、额外工具、重建、有效配置反馈及真实拒绝仍未证明；不能只凭类型注释冻结。
3. 全链 policy 身份的程序回执还不存在；回执证明参数绑定，不能单独证明底层沙箱有效。必须同时有不可绕过的反证。

## 成本收益判断与止损

目前可确认这是 Java adapter、patched acpx、两 provider adapter 的联合边界工作，涉及正常/恢复/correction 与版本维护。没有证据可称“一处常量小改”，也没有依据估算人日或承诺 token 节省。仍值得优先核验，因为现有 SDK/协议已提供部分强制接口，尚未证明必须引入新平台。

预期收益验收是：批准前产品/control/Git 写入被阻止，合法读取与结构化输出可完成，跨 provider 评审保留，同版本合法续作不丢策略。不是以权限拒绝数量或新增字段数计算收益。

用户要求的停止点已纳入主设计 §8.6：需要 fork 引擎核心、新建工具服务/权限平台或 OS 隔离；只能靠削弱读取、跨 provider 评审、合法续作或批准边界完成；或后续实测成本/收益偏离批准基线时，停止受影响工作，先交证据和选项，不默许扩围。当前“Codex 无执行工具时的读取能力”是冻结前核验项，尚不能断言必然需要上述扩围。

Decision：DESIGN_GAP。本轮形成可审查的绑定候选与核验顺序，尚未证明 D04 可冻结。无修改产品代码、adapter 安装包、用户配置或历史 ledger；本次离线协议导出不是产品测试或真实 E2E。
