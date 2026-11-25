# D04 Claude 原生切片：认证阻塞，停止实验

日期：2026-09-28。结论 **INVALID_AUTHENTICATION**，不是必要读取失败或安全边界 PASS。本轮由用户“有价值则继续实验”授权，目标是取得 Claude 作者侧尚缺的原生必要读取证据；未委派独立评审、未修改产品/adapter。

## 实际结果

- pinned SDK 0.3.232/native 2.1.232、本地初始化模型目录明确 claude-sonnet-5 支持 low。元数据预检无用户消息；模型实验使用一个新任务、一次启动，不自动补跑。
- 本地 auth status 显示 loggedIn=true、oauth_token、firstParty，只证明本地凭据状态，不证明服务端接受。没有读取或输出凭据内容。
- system init 的实际 tools 为 Glob/Grep/Read；MCP、plugins、skills、slash_commands 为空，permissionMode=dontAsk、model=claude-sonnet-5。这是运行时清单 VALIDATED；不能当作实际读取或 Write/Edit 调用拒绝的 PROVEN。init 仍列有内置 agent 定义名称，但没有 Agent/Task 工具，也没有启动子 agent；定义存在不等于实际调用。
- 服务端返回 401 OAuth access token is invalid，随后模型错误。4.01 秒结束，外层终止时 exit=-9，零工具执行，样例所有文件/目录/链接指纹不变。
- SDK result 同时具有 subtype=success 与 is_error=true/api_error_status=401；不能只看 subtype。原始 usage 各项 0、modelUsage={}、total_cost_usd=0；这是 SDK 报告值，无独立账单核验，billingMode=oauth_unpriced，不能宣称账户费用已经核销为零。

## 验证链自身缺口

原始事件有两条 system/api_retry 认证错误。探针只识别最终 assistant.error，遗漏更早的 api_retry.error_status，因此没有履行 Gate“第一条认证/模型错误即停止”的完整要求。SDK 内部发生两条重试事件，不等于外层重新启动两轮。该缺口如实登记，不把这次停止当作完整止损证明。

下一次任何 Claude 模型实验前，先以本轮原始事件离线证明 api_retry/authentication_failed/401 在首次可观察错误处停止，且有效工具结果不误杀；不得把 SDK 原生重试当作默认获批的重试预算。原脚本与事件保留，不在报告后修改以伪造当时行为。普通本地已登录检查不能替代服务端认证事实；认证未恢复之前，重复相同 provider 调用没有价值。不自行刷新凭据、变更账户或切换计费/provider，也不启动第二个模型任务。

## 对方案和开工的含义

此次只确认受限工具表能初始化，Claude 的实际读取仍待证。Codex 上轮通过的原生命令/模型切片不受本次认证错误推翻；双 provider 的整体可行性也没有因此被证伪。

恢复有效认证后，Claude 同一有限读取目标仍值得一次补测。当前停止真实调用，转为既有 D01–D10 的无模型字段/写序/消费者冻结工作，不另开架构平台。产品实现不因实验受阻自动获准；完整实施组织与每批出口写回主工作清单 §9。尚未冻结的真实设计决策与实现后验收证据须区分，避免一边要求零实现、一边要求实现链已通过的循环前置。

## 证据

[Gate](verification-gate.md)、[模型目录预检](metadata-preflight.json)、[启动身份](model-start.json)、[事件](model-events.jsonl)、[收尾事实](model-result.json)、[作者证据判定](evidence-review.json)。本轮作者自查，不冒充 CC 独立复审；未产生新实施、合入或全流程 E2E 授权。
