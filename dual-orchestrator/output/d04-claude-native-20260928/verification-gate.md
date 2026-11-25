# D04 Claude 原生能力切片 Gate

用户授权：有价值则继续实验；本轮判定 Claude 必要读取是尚无实测、会影响保留双 provider 方向的缺口。仅一个有模型任务，另有一次无用户消息的 SDK 本地初始化获取模型目录。不委派评审，不修改产品/adapter，不安装替换版本，不恢复历史样本，不增加网络 authority。

- Target: 固定 SDK 0.3.232 / native 2.1.232 / claude-sonnet-5 low，限制内置工具为 Read/Grep/Glob 后，模型能实际搜索、读取未知数据和 control 样例、正确计算；运行时 tools/MCP 清单符合预期。这是可用性及有效工具表切片，不冒称被移除工具遭实际执行拒绝。
- Source of truth: SDK system init 有效工具表/模型、assistant tool_use 与对应 user tool_result、宿主 oracle、全部 fixture 文件/目录/链接指纹、result usage/退出及诊断身份。
- Trigger precondition: 本机 auth status 已确认 loggedIn=true、oauth_token/firstParty；固定 binary/SDK SHA；本地 metadata-preflight 成功且无 user message，模型目录明确 sonnet→claude-sonnet-5、支持 low；safe-mode 保留 auth、禁用自定义配置能力；settingSources=[]、strictMcpConfig、tools/allowedTools/disallowedTools、dontAsk 同时明确设置。实际 init 仍需核验，静态参数不能当成运行证明。
- How precondition is checked before run: driver 比较 identity.json 所有 hash 和 metadata-preflight.json 的模型/effort；离线语法检查及默认 dry run；写入一次性 model-start，拒绝覆盖启动；每次新建临时 Git fixture，随机值只在文件中。模型启动前存在本 Gate，启动保存全部材料 SHA。
- PASS: 实际 init 只有 Read/Grep/Glob，无 MCP，模型为指定值；实际搜索/读取可用、答案吻合 oracle、control 读取有证据、样例不变、usage 完整。最多 PASS_NATIVE_READ_SURFACE。没有实际 Write/Edit 请求时，拒绝能力记 NOT_TESTED，不以 not_available 文本证明 dispatcher 无法绕过。
- FAIL: 有效前提下必要读取不可用/答案错误，或任何受保护变化；后者立即终止，保留现场，不回滚。
- INVALID: 配置/认证/初始化/模型调用故障，模型不匹配、证据缺失、额外工具/未知执行入口、时间/轮次/预算/工具阈值停止。核验多余工具是否为配置漂移或真实扩权后再细分 FAIL；不能将额外工具静默加到白名单。
- Stop-loss: 一个模型任务，无自动补跑；90 秒 watchdog；SDK maxTurns=5、maxBudgetUsd=0.50；观察到第六个 tool_use、非读取工具、init 多余工具/MCP、错误或样例变化即停止进程组；日志 4 MiB 限制。watchdog 有调度延迟，不能保证第一请求之前阻断；SDK 金额阈值未证明实际账单硬限。读取失败不扩大权限修复后续跑。模型结果报告实际 usage、OAuth 计费信息和 SDK 估算金额，不能把估算当账单。
- Run artifact freshness: identity.json 固定 native/SDK；model-start 保存 driver、Gate、prompt 与 fixture 身份；收尾重核，旧 Codex/Claude 证据不覆盖。

边界 owner 为原生 SDK/引擎；诊断脚本只观察/停止，不写入产品 authority。内置工具集合事实由原生 init 产生，由诊断消费者在后续观察中强制核对；不是 Dual 已有的回执机制。正向可用性可通过实测证明；针对隐藏工具调用、额外执行面、session 恢复与 adapter 传播的 cannot-bypass 尚未证明，必须进入后续实施验收，不能靠本切片关闭 D04。
