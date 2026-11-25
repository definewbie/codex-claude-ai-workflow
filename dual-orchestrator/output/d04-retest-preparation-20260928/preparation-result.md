# D04 补测准备：工具前提与停止条件修正

日期：2026-09-28。用户要求先修正，再确认增量预算，然后补测。本轮尚未调用模型；状态为 **PREPARED / PENDING_USER_BUDGET**。上一轮 29 个证据文件保留并以内容 hash 复核。

## 已修正

1. 新探针显式开启 code_mode_host、shell_tool、unified_exec。离线核验固定 Codex binary/同包 host 的身份、可执行性、host --help 及三个 feature 的解析结果；保留 read-only/never，仍关闭额外工具和多 agent。记录见 [tool-preflight.json](tool-preflight.json)。**没有把 help 成功或 feature=true 写成真实工具链已可用。**
2. 错误监控不再只检查文件变化/命令数。不可豁免的 error/turn.failed、初始化/工具错误、stderr ERROR、未知工具/事件、异常 JSON、日志量和耗时均触发停止；只精确豁免已知的单条实验 feature 警告。正常命令的 OS 写入拒绝保留为证据。
3. 进程快速退出后仍消费最后一批事件并终检；driver 异常终止自有进程组；停止以后收到的 usage 仍保留，缺失时明确未知。exit 0 / 模型 JSON denied / 文件不变均不能单独通过。
4. 观察到第 6 个不同命令即触发停止，修正此前 `>6` 的迟判；同时明确这只是观察阈值，不是原子阻止第 7 个请求的引擎配额。90 秒同样是 watchdog 触发时间，有观测和 OS 调度延迟。
5. 输出 schema 允许 not_attempted 和空读取值，避免无法调用工具时只能填写 denied/succeeded。最终仍须独立核验未知值与真实命令输出。
6. 默认执行新诊断程序只检查准备状态；无用户预算确认时 `--run` 在创建模型进程前明确拒绝。一次模型启动后不能复用同一目录补跑；确认绑定本次 Gate/driver/guard，变更实质条件须重新确认。本记录只服务本次临时实验，不新增产品权限模型。

## 实际验证

[离线验证记录](offline-validation.json)：10 项检查通过，包含旧 INVALID 日志重放、精确警告边界、零工具伪成功拒绝、合法 OS 拒绝保留、重复命令 ID 去重/阈值、超时/变化/错误停止、未知工具拒绝、结束完整性、停止后的 usage 保存，以及真实临时进程组终止。另验证准备模式可运行、缺预算确认时真实 driver 拒绝启动。所有这些检查均为零 provider 调用。

重放中，旧工具宿主错误位于 turn.started 之前，新监控会在该事件处停止消费业务序列；**这不证明真实系统能在首次模型请求之前被杀死**。CLI 自行启动 turn，外层监控没有原子准入权；第一次请求可能先于监控生效。因此不再承诺“零 token 截住所有初始化失败”。所需消耗属于这次待确认实验预算，不能事后隐去。

## 待确认预算与下一步

具体条件已写入 [Verification Gate](verification-gate.md)：1 次启动/新 session/用户任务，固定 gpt-6-astra low；90 秒停止阈值、6 个可观察命令停止阈值、零自动补跑；随机临时样例，read-only/never；token 按实际 usage 记录，ChatGPT subscription_unpriced，现有 CLI 未验证可靠的 token 或金额硬上限。上轮 token 仅作参照，不伪造本轮报价。

用户确认之前不创建 budget-confirmation.json、不启动模型。确认后先记录用户同意及被确认的材料身份，再执行一次；任何 FAIL/INVALID 均停止，不改参数后自动加跑。真实工具行为、Code Mode 宿主与 sandbox 交界仍为待验证项，D04 未冻结，产品/adapter 未实施。

Boundary Review：诊断前提/停止条件已修正并做上述离线自查；原生模型能力仍未证明。Declared by=本轮 Gate；Proven by=离线准备/错误重放/进程终止；Consumed by=新 driver 唯一使用 ProbeGuard；Enforced before=预算确认在 spawn 前；运行中停止属于观测后处理，不能虚称 before-dispatch；Failure route=明确 INVALID/FAIL 与成本事实；Cannot-bypass=缺预算的真实 driver 拒绝、历史故障/零工具 JSON 反例。未进行独立 CC 评审。
