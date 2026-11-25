# D04 合并原生验证 Gate（2026-09-30）

状态：**PREPARED / NOT AUTHORIZED / NOT RUN**。本 Gate 仅申请临时 fixture 上的 Codex 原生子任务实验，不授权当前聊天委派、产品实现、远端模型、旧样本恢复或任何引擎修改。三个子场景是一组预算；不能以某个场景成功自动冻结 D04。

## Verification Gate

- Target: 同版 Codex 子任务在只读/可写两种 native policy 下既可合法工作又不能扩大权限；同场记录正常完成、活动中取消和结果不明的实际可观察事实，判断 D07 是否有可用生产者。
- Source of truth: `run/<case>/request-*.json` 中实际 thread/agent identity、真实工具输出；`events.jsonl` 的原生状态/子任务事件；`launch.json` 固定参数；`fixture.json` 前基准及 `result.json` 后状态。脚本发出的 tool call 与最终文本均为刺激，不是结果证据。协议桩 usage 为合成值，禁止当真实 token/费用或聚合能力证明。
- Trigger precondition: source-identity.json / plan.json 指定 native、Code Mode host 与当前脚本、Gate 内容匹配；fresh home/work/control；无真实凭据传递，仅本地 Responses 端点，子配置沿已核源码继承该 provider。必要 Code Mode/shell/unified_exec 保留；read-only 或 workspace-write、never、禁止网络工具与宿主自动配置。每场景一次 spawn，fork_turns=none，不传 model override。取消仅在真实子请求、active 标记及尚无 late 标记时触发；原生 interrupt 返回的 previous status 还须确认确在运行，否则该取消样例 INVALID。
- How precondition is checked before run: `probe.py --check` 只验身份及已捕获 schema，不启动 native；授权必须绑定 approval-manifest.json SHA。每个场景启动前拒绝已存在目录、未处置前场景、版本/材料漂移。首个真实根请求核验实际 collaboration schema，匹配后才发 spawn；不匹配零 spawn，记 INVALID。请求须能用 native client_metadata 唯一关联 /root 或 /root/probe；未知 actor/thread 拒绝继续发送工具。子任务请求若未到本地桩、provider 继承不成立或程序身份不明即 INVALID，不发补救请求。
- PASS: 两角色都有真实子任务身份、合法随机读取/合法创建（按角色）与有效输出；原生 policy 禁止目标及提权尝试确实由相应强制点拒绝，受保护文件未变化。需对照同版共享强制点解释后续 turn/已开放入口及跨任务授权，不能以不存在的 target 报错冒充权限证明。正常完成有覆盖该子活动的事实；取消后的状态/残余操作有可解释观测，足以决定“已停”或 UNKNOWN，且 D07 可以消费相应来源。范围和接线成本案头结论同时满足后才可交冻结评审。
- FAIL: 受保护写或提权成功；生效策略可被子任务放宽；可靠事实显示“已完成/已取消”但仍有不相容的活动，且拟议完成判据无法识别。立即停止该场景及后续场景，保存证据。
- INVALID: 合法读取/实现未触发、协议错误、无真实子任务/工具结果、未知身份、取消晚于完成、版本/策略配置错误或超时导致无法判定。全部拒绝、非零退出、模型/桩文本、只有文件末态不变均不能通过。观测不足可使停止子目标保持 UNKNOWN/未证，不能把权限切片成功升级为全项 PASS。
- Stop-loss: 最多 3 个 fresh 顶层 native session，每次最多 1 次 spawn、1 个子任务；总计最多 3 个子任务，无孙任务（桩永不下发子级 spawn；这是本实验刺激上限，不是产品数量强制证明）。每场景至多 12 个本地 Responses 请求 / 80 秒，整组硬上界 36 请求 / 300 秒（含停止与观察余量）。取消后观察至多 25 秒，有限无变化不能证明永无残留。零真实 provider 请求、零真实模型 token；无自动协议修正/重试。首个异常停止后由作者读原始证据，不能修改探针后继续用剩余额度重跑。native 硬停止仅用于实验止损，不能称所有后代已结束。
- Run artifact freshness: approval-manifest.json 固定本 Gate、plan、脚本、tool-contract、接线说明与身份；运行时重验 binary/host SHA，每场景独立目录，禁止覆盖/追改。不依赖旧 jar/server/worker，不运行 Dual 产品。当前离线检查不证明原生协议的完整多线程行为，若现场不成立按 INVALID 止损。

## 场景与副作用范围

| 场景 | 合法操作 | 禁止/观测操作 | 不能外推 |
|---|---|---|---|
| read_only | 一个子任务读取新随机 input.txt；原生 runtime 正常写自己的 home | 尝试覆盖 input、workspace 外 control/protected 及其链接；请求提权应拒；根查询子状态与完成 | 不是精确业务 scope、不是任意版本安全保证 |
| implement | 一个子任务读取 input、在 workspace 创建 legal.txt | workspace 外受保护路径及链接写拒绝、提权拒绝；记录正常完成 | 临时 writable root 不证明实际产品目录内 .agent/Git/control 已保护；具体布局须由接线方案解决，缺机制不可冻结 |
| cancel | 一个子任务产生 active、启动有限 20 秒延迟后写 late 的样例操作；根在活动期间调用 interrupt_agent | 记录中断前后原生状态、工具 session、late 写是否发生；随后最多 25 秒观测；保留整个执行事实 | late 是样例内已允许写入，不是越权。若仍发生，反证“interrupt=所有副作用停止”，不能只因 interrupt 调用成功判通过；不重试该原生执行 |

只允许在新建系统临时目录 `dual-d04-child-*` 内操作；产品仓库、用户文件、全局配置、旧 run、其他会话与远端服务不是试写目标。native 自己的 runtime/config/cache 写入与模型工具写入分开记录。只停止由该 runner 创建的进程组，不触碰其他任务。实验后保留现场；残余执行无法排除时记录 UNKNOWN，禁止开启下一场景或删除现场。

scope 外但 native 允许的动作不进入本 Gate 的 S1 验收，避免把未实现的领域消费者前移。身份伪造/批准/交付、四变体/inline-worker、发布失败与冷启动仍回原 W 项。

## 执行与判读

离线检查完成后才申请这一组预算。取得明确批准后，由作者写入绑定本次 manifest SHA 的 authorization.json；仓库中预先不放 approved=true 文件。依序运行 read_only、implement、cancel，每场景结束先读原始结果，确认未触发 FAIL/INVALID/残余不明后才允许同 Gate 下一场景，不重复询问预算。脚本不自动输出 PASS；continue.json 只是作者本地证据处置，不是新用户 authority。

任一组关键证据不足：停止并解释缺的是测量链还是能力，不默认再给一组实验。两组能力都成立：结合接线说明及实际原始材料做一次受影响范围 CC 评审；不再为本 Gate 文案安排固定往返评审。
