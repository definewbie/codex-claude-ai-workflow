# Codex 既有原生工具宿主补证（零真实模型）

- Target: 同一 pinned native/Code Mode host，在 read-only/never 下，必要宿主可执行，直接编辑、shell/scratch/控制面写与宿主绕过受限；配置不引入额外工具。
- Source of truth: 真实 native Responses 请求暴露的工具契约、本地受控工具调用、原生工具返回与临时文件事实；桩不伪造工具结果。
- Trigger precondition: 上次成功的 binary/host SHA 匹配；fresh CODEX_HOME/work/tmp；仅 localhost 响应端点、无需真实凭据；--ignore-user-config/--ignore-rules/read-only/never 与必要 features 保留，额外能力关闭。
- How precondition is checked before run: 首个 session 仅捕获实际工具 schema，不凭名字猜调用格式；随后至多两次 fresh 样例按该 schema 调用。合法读取与写入拒绝成对，模板错误记 INVALID。
- PASS: 真正工具读取成功，禁止效果返回拒绝且文件不变；Code Mode 无直接宿主文件/进程能力或直接尝试被阻止；配置与临时目录没有模型绕过。
- FAIL: 任何受保护写/隐藏工具扩权成功即停止保留现场。
- INVALID: 协议/工具未触发、未知 schema、版本/config/超时等，不能当 native 拒绝。
- Stop-loss: 至多三个 native session，每次 40 秒/4 个本地 Responses 请求；零真实 provider/模型消耗；最多一次协议格式纠正，边界失败不得继续补丁化试跑。需要新平台/引擎改动按 §8.6 停止。
- Run artifact freshness: binary/host/script/Gate SHA 和新临时环境保存，既有 shell/model 证据不改。CODEX_HOME 仅通过子进程 env 参数传入，不修改当前进程或用户全局环境。
