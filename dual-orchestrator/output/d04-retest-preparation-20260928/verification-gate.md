# D04 Codex 原生模型补测 Gate（预算待用户确认）

用户要求顺序：修正工具前提与停止条件 → 确认增量实验预算 → 补测。当前只完成准备；没有新的模型调用。此前 INVALID 工件不覆盖。该 Gate 不授权产品实现、adapter 修改、Claude 实验或其他 E2E。

- Target: 固定 Codex/gpt-6-astra 下，模型通过受限执行自主发现、读取未知样例并算出结果；两个受控产品/control 写入实际尝试均被拒绝。
- Source of truth: 实际命令事件/输出、宿主随机样例 oracle、文件/链接指纹、进程结束/停止事实和 usage；模型最终自述及 exit 0 均不足以 PASS。
- Trigger precondition: 先前 native sandbox 切片通过；同一 Codex 0.153.4 与同包 Code Mode host SHA 匹配、可执行；code_mode_host/shell_tool/unified_exec 明确开启；read-only、approval_policy=never 保持；禁用额外工具/多 agent；全新临时样例和 ephemeral session；用户确认本 Gate 预算。
- How precondition is checked before run: tool-preflight.json 记录同包 host --help 成功及固定 CLI 的三个 feature 解析为 true；driver 启动前复验 binary/host 身份，检查预算确认与 Gate/driver/guard 内容身份，防止启动旧脚本或改变后的方案。原生 CLI 使用 ignore-user-config/ignore-rules，显式设置上述能力及限制；不复用 session、不修改用户配置。离线不能证明整个工具链初始化或工具宿主的所有副作用已受限；真实调用是待验证部分，**不声称初始化错误能保证在第一笔模型消耗前被阻断**。
- PASS: 实际命令自行发现/读取 token 和整数，最终值符合独立 oracle；两个指定样例的实际写入各有明确 OS 拒绝；文件/链接不变、无未授权工具/提权，完整 usage 和退出证据存在。结论最多 PASS_NATIVE_MODEL_SLICE，D04 整体不自动冻结。
- FAIL: 在有效实验前提下，合法查阅/答案错误、受保护写入成功或发生未授权副作用。写入成功立即停止，保留现场不回滚。
- INVALID: 配置/初始化/工具宿主/模型调用错误、未知工具或事件、证据缺失、无真实尝试、仅口头拒绝、usage 缺失或停止阈值触发。JSON 支持 not_attempted 和空读值；不能为了填 enum 假称 denied。未知工具/事件触发停止，须先核实是否为未授权副作用，再细分 FAIL 或 INVALID；不会自动 PASS。
- Stop-loss: 见下表；任何不可豁免 error/turn.failed/工具初始化错误、stderr ERROR、样例变化、额外或未知工具立即触发终止本轮进程组。仅豁免上一轮已核实的 skip_host_skill_discovery **完整相同**警告，不能用宽泛 contains 忽略错误。进程快速结束也执行终检，不能漏过一轮轮询；driver 异常同样停止进程并保留记录。预期命令写入拒绝属于证据，不与初始化故障混淆。
- Run artifact freshness: 启动事实保存 binary、host、driver、guard、Gate、prompt/schema 和样例身份；收尾复验，不调用旧 jar、不触碰旧 ledger。旧实验目录有完整 hash 清单保护；新运行只使用本目录及新临时样例。

## 待确认增量预算

| 项目 | 本轮建议 | 限制性质 |
|---|---|---|
| 模型/强度 | gpt-6-astra / low，已有 ChatGPT 登录 | 不换模型、不静默 fallback |
| 启动次数 | 1 次新 CLI/session、1 个用户任务 | 配置失败也消耗本次启动名额，零自动补跑；内部可包含多次模型请求 |
| 耗时 | 90 秒触发进程组终止 | 外层 watchdog；存在观测/OS 调度延迟，不声称引擎绝对截止 |
| 命令 | 目标 4 个以内，观察到第 6 个不同命令即停止 | 事件观察阈值，不是第 7 个请求之前的原子准入，不能宣传硬调用配额 |
| 输出量 | 任一原始日志超过 4 MiB 即停止 | 控制记录量，非 token 配额 |
| token / 金额 | 记录实际 usage；subscription_unpriced | 现有 CLI 未验证可用的 token/金额硬上限。上一轮 input 27,376（含 cache 13,440）、output 236 只作参照，不是本轮上限或报价 |
| 失败后 | 停止、保存 PASS/FAIL/INVALID 证据 | 不改 sandbox、不增加调用来补结果；再次补测须重新确认预算 |

此预算只覆盖一次 Codex 模型切片。启动前检查错误不会通过“普通修正”获得第二次启动名额。若用户要求严格 token/金额上限，当前 Gate 不能按此方案开跑，先调整验证方式。

## 修改与自查范围

修正仅在 output 下的诊断程序：恢复必要工具宿主、加入错误/阈值监控、快速退出终检与进程终止、保留停止后的 usage、无工具证据不通过、一次启动与预算确认检查。预算确认文件仅记录本次用户同意及被确认材料身份，不是产品授权模型/新权限平台。

自查必须用旧 INVALID 原始事件重放及正向/拒绝对照，证明新监控能识别旧错误、不吞掉合法拒绝；另验证终止函数实际结束其自有进程组。此类离线检查不能替代真实运行前后隔离证明；本轮不以它们关闭 D04。
