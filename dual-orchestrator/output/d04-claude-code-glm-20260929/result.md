# D04：纠正 Claude Code 路由前提，既有 GLM 原生读取通过

日期：2026-09-29。用户明确：Claude 一直绑定 GLM，Dual 不应关心其背后模型；并确认作者侧使用现有 GLM-5.3。本轮是纠正作者此前选错的实验对象，不是新模型方案或要求用户改登录。仍无产品/adapter 实现、CC 调用或额外 agent。

## 前提纠正与职责

本机命令为 /opt/homebrew/bin/claude → Claude Code 2.1.277；用户配置的智谱 Anthropic-compatible endpoint 配合 sonnet/opus alias → glm-5.3。前实验固定另一份 2.1.232 native/默认 Anthropic 路由且禁加载配置，所得 401 只说明该实验配置无效，不能用于判断一直使用的 GLM 路径。要求用户恢复 Anthropic 登录属于作者错误归因，现已撤掉该前置；原始 401 和修正前材料保留，未倒改证据。

Dual 核心依赖客户端/adapter 的执行能力、权限与标准化 artifact/failure，不硬编码 Anthropic/GLM 模型品牌。连接和模型选择归运行配置；实际 endpoint/provider/model 保留为运行追溯、费用与恢复身份事实。客户端分工保持 Claude Code 作者/Codex 评审，不能把客户端品牌直接当底层模型不同或质量独立的证据。核验时固定实际版本/model 是可复现条件，不是产品的模型白名单。

配置隔离不能切断合法连接参数：候选绑定需保留已配置连接值，同时拒绝 hooks/plugins/额外工具与更高权限继承。本轮诊断仅在内存读取既有连接认证，未复制到工件/显示；配置文件不修改，凭据不刷新、不更换账户。SDK 0.3.232 + 本机 native 2.1.277 先完成无模型消息握手，实际调用采用 fixed tools、dontAsk、settingSources=[]、safe-mode、restricted、strict MCP。版本组合有独立 SHA；不外推到旧 pinned adapter/native，也不借此升级生产依赖。

## 实际结果

[Gate](verification-gate.md)、[路由非敏感身份](routing.json)、[启动](model-start.json)、[事件](model-events.jsonl)、[收尾](model-result.json)、[作者判定](evidence-review.json)。

| 事项 | 结论 | 证据边界 |
|---|---|---|
| Claude Code＋GLM 必要读取 | PASS_NATIVE_READ_SURFACE | 10.19 秒，实际 Glob/Grep/Read 共 4 次；随机 token、378+91=469 与宿主 oracle 一致；实际读取 control；fixture 全部指纹不变 |
| 有效工具面 | 本切片核验通过 | init 恰为 Glob/Grep/Read，dontAsk，model 与 assistant message 均为 glm-5.3；MCP/plugins/skills/slash_commands 空 |
| 真实 Write/Edit 拒绝 | NOT_TESTED | 未暴露这两工具，调用为 0；不能从 not_available 文本或文件不变推出被拒绝 |
| 严格纯 JSON 格式 | 未满足提示要求 | 最终输出包含解释和 fenced JSON；其中 JSON 值经离线提取核对正确，仅支持读取 oracle。该提取是诊断，不是 Dual 放宽解析；没有测试 Dual 严格 artifact contract 或 correction |
| 隐藏工具分派、全部宿主配置/读取和 scratch 边界 | UNPROVEN | --restricted help 承诺不是实测，不能以实际工具表代替 cannot-bypass |
| SDK/native 适配、session/resume/correction、模型任务→Dual 全流程 | 未验证 | 未实现产品接入，也未运行产品 E2E |

单个模型任务，exit=0，未停止/未自动重跑，subagent spawned=0。首错消费者的 18 项离线检查沿用同源代码；本次未触发错误，因此没有新的活体首错停止时延证明。

原始 usage：input_tokens=2351、cache_read_input_tokens=5952、cache_creation_input_tokens=0、output_tokens=285；分别保留，不套用 Codex usage 定义。SDK 给出 total_cost_usd=0.021856，但 modelUsage.costBasis=unknown，不能当 GLM 实际账单；billing=provider_configured_unpriced，金额不可可靠计算。SDK provider=firstParty 也不能反推 Anthropic，实际配置 endpoint 与返回 model 分别保存。

## 剩余工作与止损

关闭的只是“既有配置下必要读取有没有实际成功”的证据缺口。D04 仍未冻结：需要真正有判别力的写入/隐藏工具拒绝与配置/scratch 边界负例，并核对能力绑定在目标 acpx adapter 启动组合中的可用性；adapter 全入口接入和恢复序列保留为实现后验收，避免要求先实现再允许实现。

本次一个模型任务预算已使用，不自动启动第二次。下一实验须针对剩余边界选择真实工具尝试或可控原生协议负例，不能反复让模型口头宣称没有工具来凑安全证据。若比较暴露需要引擎修改、新权限/工具平台、改变客户端分工或成本收益偏差，停止交用户决定。可行性证据齐备后提供 CC 冻结复审提示词；本次结果尚不足以要求 CC 关闭全部 D04，也不授权实施。

Boundary Review：原生读取切片 PASS；D04 DESIGN GAP。owner=Claude Code native 工具执行面，model 产生待验证内容；SoT=真实事件/文件身份；消费者=诊断 guard/evidence review；强制点=原生工具配置，隐藏分派仍待证明；failure route=首次 typed 错误停止；cannot-bypass 尚缺的负例不以 prompt/输出文本代替。反思：初始化时禁用配置而未显式保留合法连接，制造了错误的服务端假设；后续所有 Gate 都须分别固定客户端、有效连接/model 与权限上限，不能从命令名/OAuth status 猜后端。
