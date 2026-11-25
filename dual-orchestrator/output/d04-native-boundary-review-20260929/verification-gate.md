# D04 原生边界补证 Gate

用户要求继续完成剩余可行性材料并给 CC prompt。仅诊断，不修改产品/依赖、用户设置或旧样本，不委派 agent。

- Target: 固定 Claude Code 2.1.277 和 SDK 0.3.232，真实原生分派器处理受控后端返回的工具调用：合法 Read 成功；隐藏 Write/Edit/Bash/Agent/未知工具、越读取边界及配置加载不得绕过候选限制。用允许 Write 的独立 fixture 作可触发性反证。
- Source of truth: 本地协议请求/返回、native SDK tool_use/tool_result、原生退出、真实 fixture 内容前后、canary；全部有固定输入/文件身份。不是模型安全行为或真实 GLM 请求证明。
- Trigger precondition: binary/SDK SHA 与上轮记录一致；fresh fixture 的产品/control/Git/外部/scratch canary 在普通宿主均可读写；受控服务只绑定 127.0.0.1、使用假凭据、禁止读取真实连接凭据；SDK 参数 tools/deny/dontAsk/safe-mode/restricted/settingsSources/strict MCP 固定。
- How precondition is checked before run: driver 检查 SHA 与 fresh 输出目录、host read/write control，保存请求参数；受控响应格式先以合法读取确认接通。不把格式/握手错误当权限拒绝。
- PASS: 合法 Read 真实返回预置随机值；每个负例进入 native 后返回 error 且受保护文件/外部读 canary未泄漏；fixture不变；开放对照真实写入其独立 fixture。配置 hooks/模型自定义不生效且未启动额外工具。scratch 只作具体目标拒绝，不外推任意 OS 隔离。
- FAIL: 候选下任何禁止写成功、读取边界泄漏或配置 canary执行，立即停止该组，保留现场，不降低判据。
- INVALID: 协议未被 native 消费、合法工具不触发、格式/版本不符、无法关联工具结果；拒绝不能由 INVALID 推断。
- Stop-loss: 零真实 provider/模型请求；最多 4 个 fresh 原生诊断 session，每个 40 秒，每个最多 4 次本地 messages 请求；第一有效边界失败停止负例，不继续扩围修补。最多一次无副作用协议纠正，后续相同失败停止。任何需要新引擎/生产服务/权限平台按设计 §8.6交用户决定。
- Run artifact freshness: native/SDK/driver/worker SHA、实际参数、所有新 fixture/调用与结果单独保存。旧有效读取和 Codex 实验引用原身份，不覆写。

临时本地响应桩只模拟模型发出的工具命令，不模拟 native 执行结果；测试后关闭，不是新增 Dual 工具服务。Boundary owner=原生工具分派/权限层；其强制失败才是证据，诊断自身提前拦掉调用不能作 native 证明。
