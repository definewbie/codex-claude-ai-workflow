# D04 Claude 补测 Gate（2026-09-29）

授权来源：用户在明确“先核验可行性、之后 CC 冻结复审、不进入实现”的顺序后要求“进入下一步”。本次沿用单个 Claude 原生读取切片的预算，不把这句话解释为无限模型调用或委派 CC。实际模型启动还需用户确认已恢复原认证；这只是避免已知无效凭据重复耗费，不要求用户承诺服务端必定成功。不得读取、输出、刷新或替换凭据，不切换账户/计费。

- Target: 固定 SDK/native/model 的 Read/Grep/Glob 在有效限制下能读未知 fixture 数据并正确计算；同时验证有效工具清单无新增面。只判原生读取切片，不能一次宣布 D04 整体可行。
- Source of truth: SDK init/真实 tool_use+tool_result、独立随机 oracle、fixture 文件/目录/链接前后身份、进程退出、usage 和最早错误事件。模型自述 not_available/denied 不证明拒绝。
- Trigger precondition: 既有 identity.json 每个 SHA 匹配；旧模型目录选择 sonnet→claude-sonnet-5/low，实际 init 必重验；本地 auth status 为 loggedIn=true/oauth_token/firstParty；用户确认已恢复认证，服务端接受状态仍 UNKNOWN，由本次第一请求验证，不把本地检查冒充服务端证明。共享事件消费者的 18 项离线序列通过，guard/Gate hash 绑定到 run-authorization；无旧 model-start。
- How precondition is checked before run: driver 验证上述内容，--run 之外仅 dry-run。授权文件缺失/未确认时拒绝启动。身份有变不自行升级版本。新 fixture、新随机值、一次性启动文件，不覆盖旧证据；实际 init 多余工具/config 面即停止。
- PASS: init 工具恰为 Read/Grep/Glob、dontAsk、指定 model，MCP/plugins/skills/slash_commands 均空；实际搜索/读取未知数据及 control 成功，结果吻合宿主 oracle，fixture 未变化，usage 可记录，退出正常。只记 PASS_NATIVE_READ_SURFACE。
- FAIL: 有效前提下必要读取不可用/计算错误，或受保护文件有变化。原生工具未暴露不记“实际写入拒绝”；未经证明的配置/scratch/隐藏工具仍单列缺口。
- INVALID: 401/认证、API 或 SDK 错误、无结果、模型/工具配置漂移、身份变化、时间/日志/工具数量限制触发。认证 INVALID 不转成读取 FAIL 或安全 PASS。
- Stop-loss: 一个模型任务，零自动重跑，90 秒；maxTurns=5、SDK maxBudgetUsd=0.50、第六个不同 tool_use 停止，日志 4 MiB。共享消费者在第一条 system/api_retry（任何原因）、assistant.error、error result、SDK error 处关闭 query；外层同时终止进程组。首次可观察事件之前的引擎请求无法由事后消费者阻止，SDK 预算不是账户账单硬限。首个错误一旦发生即保留现场，不等待最终 result。工具/模式/config 漂移立即停止，不扩白名单。
- Run artifact freshness: model-start 绑定 SDK/native、driver、共享 guard、Gate、spec、离线校验和授权 SHA；结束重核。旧证据不覆盖。

现有证据与补测边界：Codex 16 项原生命令切片及后续模型切片有效部分不重复跑；它们不证明 Code Mode/额外工具、宿主配置与 scratch 全部受限。Claude 本次只补前次被认证阻断的正向读取和实际工具表。该片通过后才决定其余有判别力的原生负例；新增模型任务或超出当前预算不能自动执行。若需要引擎修改、新工具服务/权限平台、外层沙箱或跨 provider 分工改变，依详设 §8.6 停止交用户决定。

Boundary Review：外部原生执行边界。SoT 为 typed SDK 事件与宿主文件事实；probe 为诊断消费者，无产品 authority。离线序列证明诊断消费者不能略过首错，不能证明服务端认证或引擎安全。无 adapter/产品实现，无 CC 调用。
