# D04 只读执行面：静态可行性核验与待决定项

日期：2026-09-28。仅本机源代码/包与构建身份只读核验；无 provider、session、安装、配置修改、攻击测试或实现。本报告不是隔离能力 PASS。

## 结论

**已核验的 pinned Codex adapter 常规模式不足以证明 D04。** 仓库配置脚本默认 Codex adapter 1.10.0；本机对应缓存发布代码的 `read-only` mode 使用 workspaceWrite，普通 prompt 每 turn 显式传该 policy。不能以设置这个模式作为“零产品/Git/control 写入”的冻结证据。D04 继续阻塞，需先决定是否允许细化 adapter 的强制只读绑定。

这不是“引擎不支持只读”的结论，也未实测越权写入。内部审计调用确有 readOnly，但不能替代正常 agent 任务的约束。现有配置或更底层强制策略是否还有其他可用路径未穷尽，不能声称必须自建沙箱。

## 来源与内容身份

[source-index.json](source-index.json) 记录 15 个来源的绝对路径、文件 SHA 与片段范围；[source-excerpts.txt](source-excerpts.txt) 固定本次对应片段。范围仅实际核验的绑定链，不等于全部 adapter 审计。

| 来源 | 已核验事实 | 能证明／不能证明 |
|---|---|---|
| scripts/configure-acpx.sh:25,86 | 默认 Claude 0.70.0、Codex 1.10.0，可由环境覆盖 | 证明脚本默认；未读取用户配置，不证明实际运行命令 |
| 本机 Codex adapter package.json 与 @openai/codex/package.json | adapter 1.10.0；其本机依赖 Codex CLI 0.153.4 | 证明本次检查的缓存；不是未来启动身份，依赖版本还需 D07 冻结 |
| codex-acp/dist/index.js:27219–27304 | ReadOnly 使用 workspaceWrite/workspace-write，approvalPolicy=on-request；默认 mode=Agent；公开三个 mode 均非真正 readOnly | 否定 mode 名称等于只读；不证明任意底层配置均无效 |
| 同文件:28406–28416、28656–28674 | newSession 传 session config；sendPrompt 每 turn 显式传 agentMode.sandboxPolicy | 单改初始 session/config 不能直接认定每 turn 只读；需检查有效策略及所有旁路 |
| 同文件:28688–28716 | file-change-report 的独立 fork 和审计 turn 使用 read-only/readOnly | 原生只读参数存在；只覆盖内部审计，不覆盖常规作者/评审 |
| acpx/src/acp/client.ts:650–656、2314–2334 与 filesystem.ts:122–205 | ACP 文件写入走 isWriteApproved；approve-all 放行，deny-all 拒绝，其余要求确认 | 不能把 ACP 写入描述成无条件放行；也不能推导 provider 原生工具必经此函数 |
| AcpxAgentTransport.java:481–495 | 非 Claude 携 ClaudeExecutionPolicy binding 会抛错；Claude 有 binding 时验 ref/SHA，禁止 approveAll | 直接证实当前绑定只适用 Claude；不是跨 provider 的统一强制面 |
| acpx/src/acp/claude-execution-policy.ts 与 Claude acp-agent.js:4750–4776、4850–4946 | policy 经校验映射 SDK/managed settings；adapter 合并用户 options 后又设置 permissionMode、canUseTool、tools 等 | 参数传递存在；最终工具能力、SDK enforcement、MCP/子任务/session 仍未证明 |

本机 `.tools/acpx-e1/bin/acpx build-info --json` 为纯身份命令，没有 provider 调用。[原始结果](acpx-build-info.json) 的 upstream/patch/schema 与仓库 manifest 对应：

- upstream_commit：`fd173f04aa1b56f9e3f5ca5190c034ddcae28792`
- patch_sha256：`4f7b7b8e81adf63b7145907cde01ac26f690113862fd9b933226a7eed4d47c8e`
- policy_schema：`claude_execution_policy.v2`

历史 `.tools/acpx-e1/audit/build-info.json` 仍记旧 patch `41ee5f00c95dc07fc113fbac171270204ca98c842e9fb6838df900f4109e8d2c`；它不是本轮实际命令结果，不能据它宣布当前构建不匹配。保留该差异，不覆盖历史证据。此命令也不验证 adapter 原生隔离或实际 runtime 选用此 executable。

## 待决定的调整

| 选择 | 是否保持既定目标 | 代价及边界 |
|---|---|---|
| 优先细化现有 adapter 的强制只读绑定（建议） | 是，目标是让 role/policy 约束到实际 session/turn/native tools；当前尚未证明 | 需维护受审 adapter 增量、依赖/配置身份和能力对照；不更换 acpx、不新增权限平台。用户同意仅授权详细设计核验，不授权实现 |
| 外层 OS 隔离 | 本地写隔离目标可保持；读能力与远端副作用还需分别核验 | 平台、进程树、运行 scratch、外部 gitdir 和控制 writer 分离成本更高；尚无兼容性证明 |
| 拷贝工作区 | 单独使用只能改变写入落点，不能满足“执行面只读” | 若允许模型写副本，需明确改变验收要求；仍需宿主/control 隔离、回传证据和禁止误回写 |

无授权前不选定或实施上述调整。方向确认后的设计冻结必须逐项列真实接口/消费者：正常 session、恢复/fork/模式变更、每 turn/correction、native edit/shell、ACP fs/terminal、额外 MCP/子任务及模型不可改的 policy。允许读上下文、形成结构化设计输出与程序落盘的正向流程必须同时证明，不能通过拒绝全部工具“完成”只读能力。

能不调用模型完成的策略传播/拒绝/原生文件限制核验先做；任何真实 provider 验证仍需既有 Gate 和授权。后续复评应挑战接口可行性、旁路覆盖与代价，不把本报告的推荐当成已批准答案。
