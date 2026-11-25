# W09 fresh 第八例 Gate：离线装配结果

2026-10-23。**离线装配与只读核对通过；第八例真实运行待用户批准，未启动。** 本轮未运行原生客户端或发 prompt，不恢复第七例。用户授权的初始化观察属于上一包，未重复。

## 已完成与证据

1. 受审 process_check.py 原字节复用，已装入准备、派发前重核、逐步执行三个真实脚本。UNKNOWN/PRESENT 不得通过；三个入口都在各自下一副作用前调用。同文件导入与实际 guard 语句拒绝测试在 test_process_check.py，来源与最小差异在 assembly-provenance.json 和三份 .diff。
2. 完整 pre-dispatch-recheck.py 在受限环境实际停于 ProcessInspectionError；在可成功读取进程表的环境实际 PASS，核62个已知旧PID缺席、18个旧具名session关闭。见 restricted-recheck-proof.json、pre-dispatch-recheck.json。其范围仅具名历史执行，不证明任意子树停止。对 runner 的实际未授权调用也已拒绝，未产生 step/Java 运行，见 unauthorized-runner.stderr.txt/local-operations.json。
3. 本轮原 start 不带 --run，生成新实例 `ws-20261023T005431Z-9YYF`，路径见 instance-location.json。初始21文件固定，clean空Git树、无run/门/业务批准/原生session，授权false。创建空fixture的初始化commit只是本地空基线，非产品交付commit。目标迁到持久 output，避免/tmp准备等待风险；原产品需求未变。
4. 补记上次实际观察的 SDK：`@anthropic-ai/claude-agent-sdk-darwin-arm64` 0.3.232，实际子进程 SHA `7b39c1588df919d001dea3ffd5651adb682f2451b5a0e18d42d4233296b53cc7`。observed-sdk-identity.json 关联原观察与结果SHA；二进制和 package.json 加入真实 frozen_check 的31项配置/安装基线。test_sdk_identity.py 对当前正确基线通过、任一SDK项模拟漂移拒绝。不是拿个人PATH claude推定实际SDK。
5. 170万token/18请求/60分钟、储备规则和校准表不变。首次储备1,105,000/10请求；后五阶段625k仍为未测假设。21项预算对照通过，ALLOW不授业务或运行许可。45秒会话等待与600秒命令等待不变。
6. 659产品输入与两发行JAR重核未变；无产品源码/提示词/schema/配置/安装变更，不重跑无关Maven或重新打包。历史239包内文件、七例中本次第七例77快照及实际账本逐项一致；新实例仍保持21初始文件，见 preservation-check.json。未声称重新审计全部旧六例。

## 证明分层

| 证据 | 本轮结论 | 不外推 |
|---|---|---|
| 21个unittest方法（7进程、12观察器、2 SDK） | 最终同轮全部通过；实际共享守卫、消费者语句、冷子进程、SDK字节消费者 | 不是21次真实模型/原生调用；SDK漂移为离线模拟 |
| 21项预算算术对照 | 准入、阈值和拒绝规则有判别力 | 不与unittest/Maven数字相加，不承诺额度一定够 |
| 完整只读入口的拒绝与通过 | 无权限不推缺席；成功观察后可核具名缺席 | 逐步业务runner尚未获准执行 |
| 原start及初始账本/Git/发行身份 | 新实例有效、未运行、预算池未开始消费 | start不是业务完成；local ALLOW不是执行批准 |
| 上一包3.548秒禁prompt初始化 | 实际SDK来源和一次启动成功已观察 | 不是本轮新调用，不证明GLM推理、不解释旧45秒超时内部阶段，不证明未来始终选同SDK |

日志见 offline-test-results.json 及对应 stdout/stderr；预算细项见 budget-dry-run.json。最初只读拒绝证明的检查器错误期待异常显示UNKNOWN/OS stderr，实际异常只显示message，已按类型/真实源码修正断言并保存原日志；是检查器错误，不是Gate产品失败，未改guard或重跑原生。

## 边界复核（作者自查，非新增独立CC结论）

| 维度 | 结论 |
|---|---|
| Declared by | 用户只授权离线装配、实际SDK身份补记、再交批准；run-authorization.json=false |
| Proven by | 原start、完整只读入口正反、实际未授权拒绝、21方法/21算术及历史字节核对 |
| Consumed by | 三个operator入口共享guard；SDK字节由同一 frozen_check 消费；业务事实仍由原ledger/runtime消费 |
| Enforced before | prepare创建fixture前、只读准入前、runner创建step/启动Java前；SDK当前基线在冻结检查内；这是operator准入，不冒称原生沙箱 |
| Failure route | 观察失败、PRESENT、漂移、缺初始文件立即NO-GO；不改账本，不恢复旧例，不重发UNKNOWN；未来领域失败仍原D02/D06/D07/D09 |
| Cannot-bypass test | 实际false拒绝、完整只读入口拒绝与通过、三消费者拒绝后零后续、实际 frozen_check 的SDK正反、冷子进程 |

Review→Reflect→Analyze：本次修正的是启动前证据可信性P0、预算准入P0.5的operator接线；未改变Definition/Instance/Execution的owner。准备脚本保持一次性不覆盖旧fixture，重核只读，runner只能消费将来明确本次授权。实际新SDK选择、客户端业务工具、真实后半链及COMMITTED仍未证，不可称主流程闭环。

## 下一步最小动作

**用户批准 fresh 第八例本次真实运行**，按 fresh-eighth-gate.md 的170万token、18请求、60分钟和储备规则；获准后立即再运行只读重核，通过才走原逐步入口。任何路径/初始状态或冻结输入漂移均停止，不能重建实例绕过。需求/GOAL/EXECUTION/最终门、原生once及产品commit仍由用户逐项决定，运行许可不代填。

已受审guard的准则不再重开；本包是装配自查，不伪称新增CC PASS。如需对本次增量独立核对，可复制 cc-review-prompt.txt，仅限本包接线及证据边界，不重审历史、不运行原生。
