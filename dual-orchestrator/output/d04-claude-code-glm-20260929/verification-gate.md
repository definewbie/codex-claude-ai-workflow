# Claude Code 既有 GLM 路由：单次原生读取 Gate

日期：2026-09-29。用户要求进入可行性验证，并澄清 Claude Code 一直连接 GLM、Dual 不应依赖背后的模型品牌；本次纠正实验对象，不是用户改变 provider 方案。按 boundary-first-engineering 区分客户端执行面与后端模型：权限由 native/adapter 执行，模型输出质量由 artifact/reviewer 检验，运行身份/成本保留实际 model。无产品实施/CC 委派。

- Target: 本机实际 Claude Code 2.1.277，在既有 GLM-5.3 路由及固定 Read/Grep/Glob 限制下，自主读取未知 fixture 值和 control 样例、计算正确；实际有效工具表不超出限制。
- Source of truth: SDK/native init、assistant tool_use + user tool_result、宿主随机 oracle、fixture 前后指纹、退出/usage/最早错误。模型自述 denied 不能替代实际拒绝。
- Trigger precondition: identity.json 每项 hash；routing.json 来源为本机已配置 endpoint/model；仅运行时传入现有认证，不落盘/显示凭据，不继承用户 hooks/permissions/插件。metadata-preflight 为 SDK 0.3.232 与该 native 零用户消息握手通过。safe-mode/restricted/settingSources=[]/strict MCP/dontAsk/tools/deny 明确传入；native --help 支持这些参数。共享事件 guard 的离线序列通过。服务端接受尚待首次请求观察，不以本地 OAuth status 为 GLM 前置。
- How precondition is checked before run: route-options 只读既有配置中的 endpoint、指定模型及认证，比较非敏感路由身份；driver 重验 binary/SDK/metadata/guard/Gate/授权，缺失拒绝。无旧 model-start。真实 init 的 model、工具、权限和扩展列表不符即停止。
- PASS: init 恰为 Read/Grep/Glob、dontAsk、glm-5.3、MCP/plugins/skills/slash_commands 空；实际 Glob/Grep/Read 读取随机数据及 control，JSON 值与 oracle 一致、fixture 不变、退出正常、usage 存在。仅 PASS_NATIVE_READ_SURFACE，不是所有模型质量或 D04 全部限制证明。
- FAIL: 有效工具读取不可用/计算错误、受保护文件变化。没有 Write/Edit 调用则实际写拒绝 NOT_TESTED，不因未变化判成功。
- INVALID: 配置/SDK/HTTP/认证错误，工具/模型漂移、结果缺失、身份变化、时间/工具次数/日志上限；首错停止，不切回 Anthropic、不重跑。
- Stop-loss: 一个模型任务、90 秒、maxTurns=5，第六个不同工具调用停止，日志 4 MiB；无自动补跑。api_retry/assistant.error/error result/SDK error 由共享消费者首次观察即 close，外层终止进程组。观测前的内部请求不能声称被阻止。GLM 沿现有账户计费，金额未知，不使用 Anthropic 价格或原 SDK 0.50 美元估算当硬限；记录真实 token、model/provider 和 provider_configured_unpriced。多一项模型任务或扩大方案均停止另行确认。
- Run artifact freshness: model-start 固定 native/SDK、driver、guard、route-options、routing、Gate/spec/offline/授权 SHA；收尾重验。原模型实验和前次等待认证记录保持历史字节，另文纠正其适用范围。

边界：本次证明原生读取可用性及实际暴露工具面。没有验证隐藏工具分派、全部配置/临时目录/恢复/adapter 传播，不外推为 D04 已冻结。--restricted 的文档承诺只是测试输入，不等于已证明。若 native 能力不足、需引擎/新工具服务/新权限平台或成本收益偏离，按 §8.6 停止。
