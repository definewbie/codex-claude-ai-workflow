# 既有接口捕获 Gate（2026-09-29）

用户已要求核验现有硬限制接口；本轮界定为零模型消耗、零工具动作的本地诊断。不是重新开放写入、协作或外网尝试。

Verification Gate:
- Target: 固定 native 下，两个候选配置实际发送的工具集合是否排除 collaboration/网络工具，同时是否保留必要读取入口。
- Source of truth: 本地 Responses endpoint 保存的完整 request.additional_tools/工具声明、原生命令与退出事件；结果只证明暴露，不证明 dispatcher 强制拒绝。
- Trigger precondition: 固定 binary/host SHA；新 HOME/CODEX_HOME/TMPDIR/work；无真实凭据；唯一 provider 为 127.0.0.1 本地桩，auth=false；桩只会返回合法结束消息，不含任何 tool call。
- How precondition is checked before run: driver 检查 hash、严格配置、预算文件 SHA、response validator 离线注入通过；构造完整 command/env 后记录。配置探针已证明 structured flag 与 code-mode exclusion 可解析；不把该解析结果当强制证明。
- PASS: 候选路径单次请求正常结束，记录工具集合；必须分别报告目标工具是否仍暴露以及读取入口是否存在。无工具执行、fixture 不变。
- FAIL: 目标工具仍暴露，或必要工具入口丢失；不继续该候选调用尝试。
- INVALID: 配置/协议/宿主失败、无实际请求、未知事件/结果、读取能力无法从接口确定。无响应不能当禁用成功。
- Stop-loss: 最多 2 个 native session，每个 30 秒、1 次 Responses 请求；retry=0；固定 final-only responder 在发送前做类型断言。任何请求超限、响应形状异常、工具事件、fixture 改变或 timeout 终止进程组并停止全部后继；不开放真实 provider fallback。
- Run artifact freshness: native/host 与既有 pinned SHA 相符，新目录不可覆盖；driver、guard、Gate 与 budget 保存 hash。所有运行文件均为新 evidence 工件，产品/安装源码不改。

LOW-2 对位：从 probe-guard-repair/prepared-future-probe.py 的固定身份、新 fixture、timeout/进程组与文件 watchdog 纪律派生；本探针不发写入或控制动作，不伪用 exit_code guard 证明它们。新增 final-only typed response guard 并先做拒绝工具消息/超限的离线注入。后续若需工具动作，必须另有该动作的副作用前置证明及 guard，不能据本 Gate 执行。
