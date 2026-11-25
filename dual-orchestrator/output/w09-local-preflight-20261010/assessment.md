# G05 本地准备与运行前核对

2026-10-10；本地准备 **PASS**；真实 Gate **NOT RUN / NOT AUTHORIZED**。收到用户“批准”后，按上一条答复明确区分的本地准备范围执行；不把该批准扩大成真实模型调用、关键门批准或产品提交许可。

## 已完成

- 新父目录创建前确认不存在；目标 `/private/tmp/dual-w09-newlines-20261010/target` 初始化为 main，只做一次空基准提交 `6081fc1888db9a6a61291a7d0822537d8a0f89ff`，没有产品源码。原 Dual 仓库未提交。
- 冻结 CLI 的 `start` **未带 --run**，由原 GitWorkspaceService 创建 `/private/tmp/dual-w09-newlines-20261010/worktree` 与 `codex/w09-newlines-20261010`，原 ledger 初始化成功。新 workstream `ws-20261010T055644Z-6QCS`；workflow `agent-lifecycle-terminal-delivery@5`。
- target/worktree 的 canonical、Git common-dir、base HEAD/tree 与 clean 实测一致；共享 common-dir 是合法 linked worktree，两个工作目录仍各有用途。程序工件 `.agent` 被 Git 忽略，未据此声称控制目录不可被模型写入。
- 654 冻结源码/资源输入、CLI/server JAR 与受审发行一致。原 workstream 的 frozenRuntimeIdentity 记录实际 CLI/server SHA、liteflow、600s 命令预算和 Java 版本；本地 start 的 JVM 日志另证 entrypoint/kernel 同 loader 与实际 code source。该日志仅证明初始化 JVM，后续真实路由 JVM须再核；FlowExecutorHolder 尚未加载，不冒称已实跑路由。
- 在实际 effective workspace 执行本仓 acpx 的 build-info/config 两次 metadata：patch `1517f828`、wrapper/dist 字节及两客户端 launch-spec 与受审组合一致；global config SHA 未变，该新 worktree 无 project override。默认 approve-all 不产生 Dual authority；原 profile/资格/批准约束保留。没有启动 Claude/Codex 或创建原生 ACP session。配置中可能有敏感命令，原始 config 未落盘，仅保存白名单结构和 SHA。
- 从 effective workspace 运行宿主 Python 的隔离 stdlib 加载，binary/unittest/importlib 依赖字节与前轮观察一致。它只证明宿主条件，不是客户端工具、产品测试或验收证据。

## 当前 authoritative 状态

原账本路径：`/private/tmp/dual-w09-newlines-20261010/worktree/.agent/workstreams/ws-20261010T055644Z-6QCS`。`workflow/current.json` 为 RUNNING，唯一 active node `node-context-intake_claude_brainstorm-001`，节点 READY、handler `context.build_package`、runRef=null、latestExecutionId为空、openGateIds为空。这里的 RUNNING 是实例生命周期，**不表示模型正在运行**；新实例尚无 agent run/执行或批准。

`start` 输出的 Stage: design 来自兼容摘要；当前入口以原 workflow current/node 为准。只记录这个投影限制，不在本地准备中擅自改代码。Delivery 为 WORKFLOW_NOT_DELIVERABLE，canMerge/canFinalize均 false，符合初始状态。

initialized-ledger-snapshot 只是初始化前缀的归档副本，后续执行仍消费原账本。文件 SHA仅标识本次前缀；events 在运行中按原条目 id/身份引用，不比较可追加整文件作漂移判据。局部 operator registry 通过既有 DUAL_OPERATOR_REGISTRY 指向本次父目录，仅为索引，不是新的事实/许可来源。

## Gate 前提与仍待运行验证

逐项结果见 [gate-local-preflight.json](gate-local-preflight.json)。P01/P08 当次需求、目标、预算及责任已获准；P02–P04/P07 的离线、metadata、实例前提已核；P05 原生 ask 若发生才行使，P06 的真实 GOAL/readiness/工具证据须在各自实际时点产生。不能为了“核齐”提前虚构引用或替用户批准。

原生握手、实际 provider/model、工具输出/usage、独立验收与真实目标产品交付均 NOT_RUN；它们是后续同一有界 Gate 的取证对象，不再追加安全/内核实验或 CC 材料轮次。18 次顶层调用、60min 主动窗口、120k预警/160k可见实际token止损以及两次有证据修订上限不变；未知内部总量不填零、不作绝对预算保证。

## 完整性与六维

| 项 | 本次前缀的事实与边界 |
|---|---|
| Declared by | 用户 G05 本地准备批准 + 原已评审 Gate；真实调用尚未授权 |
| Proven by | 新 target 空基准、真实 CLI start/status/trace、原账本与字节身份、workspace metadata/宿主观察；没有补造故障/原生证明 |
| Consumed by | 原 GitWorkspaceService/initialize/readSession/status 消费事实；本报告与 registry仅定位，不产生 authority |
| Enforced before | 未带 --run，零 workflow执行；初始拒交付，GOAL/EXECUTION/最终门未代批。运行授权由当前 operator 守住，不宣称新增平台 enforcement |
| Failure route | 创建/start 中断保留现场、读原 pending/实例后判断，不重复 start、不手补账本；原生外部不明仍 D07，禁止自动重发 |
| Cannot-bypass | 受审离线回归/反证保持源码同一；本次仅正常初始化与阻交付前缀实际观察，不冒称新故障测试或完整真实链 |

本地 verifier 一次断言使用绝对 Path.parts[0]，误报额外文件；实际顶层仅 .git/.agent。已保留 local-check-01-invalid.txt，改用 entry name 后只读重核通过；无 source/ledger/产品修改、无重新初始化，不把错误验证器当产品失败。

## 下一步

取得明确真实运行授权后，在此新实例按原 Gate 单 INLINE、逐段 `run --max-steps 1`，先核每段身份/预算再执行；真实 GOAL 门展示材料给用户。精确 EXECUTION 与最终交付仍逐门用户决定；最终产品 commit仍用户执行。metadata证明不了的连接能力只在同一获准调用池内核验，任何配置/证据/外部UNKNOWN/成本偏差按原止损。

本地准备没有实质设计/代码变化，无需把同一材料再送 CC。真实 Gate 的 PASS/FAIL/INVALID 必须来自后续原记录，此报告不解除真实运行 NO-GO。
