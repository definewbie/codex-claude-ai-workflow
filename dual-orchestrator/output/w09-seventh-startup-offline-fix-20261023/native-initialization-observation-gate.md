# 原生初始化观察：待批准方案

**NOT AUTHORIZED / NOT RUN。** 目标是以最小成本取得当前启动能力或阻塞阶段证据，不承诺复原第七例原因，不是第八例业务Gate。用户尚未授权原生启动，本轮没有运行本方案。

## 授权包的具体范围

- 最多一次 `claude sessions new`，原受审acpx/wrapper、原权限参数和cwd，仅增加既有 `--verbose`，改用独立诊断session名称；argv及原输入SHA见 [observation-command-candidate.json](observation-command-candidate.json)。不复用原会话，不修改旧run、不调用retry/resume、不恢复第七例。
- 允许该初始化所需的既有认证/网络；不登录新账号、不改凭据、代理、policy、包版本、缓存或客户端配置，不预热、不另跑npm命令。若现有配置不能启动，记录失败即停。
- **不发送任何 prompt / session/prompt，不接业务节点、不执行工具任务。** 预计无模型推理请求；不能用“没有prompt”伪称取得了计费token=0的测量。
- 主初始化等待最多45秒；收尾/进程核对另留最多15秒。核对无法完成则停止并报告UNKNOWN，不据此再次启动。启动前只读核验独立完成，失败不启动计时或原生进程。
- 成功即走既有具名session close/show收尾；超时用原有终止机制处理此次可观察进程树并留存日志，不杀无关进程，不声称任意子树全停。
- **不自动启动90秒第二次观察。** 只有本次证据表明延长有判别价值、旧执行已确认停止，才提交一次新的具体决定；不把诊断变为循环试跑。

## Verification Gate

- **Target**：固定本次初始化的结果与最窄可证阶段：启动前/initialize与auth尚未完成/initialize及auth已完成但session创建未完成/成功创建。日志不足时保留更宽范围或UNKNOWN，不猜测具体网络、npm或模型原因。
- **Source of truth**：原生argv与cwd、入口/config/policy身份、stdout/stderr及退出/耗时、既有verbose标记、具名session metadata、成功读取的进程表；mtime仅为辅助修改时间证据。诊断材料不回写业务账本，不作为原第七例usage或authority。
- **Trigger precondition**：用户明确批准本次单次初始化及auth/网络范围；历史stop-required仍在；原cwd和只读policy材料可用且SHA匹配；当前入口/配置差异可解释；新session名称不冲突；成功读取进程表且相关旧执行已停止；已审定捕获/终止/收尾方式不含任何prompt调用。
- **How checked before run**：先用修正后的进程guard取实际成功结果；逐项重核冻结wrapper/dist、客户端配置/程序、policy SHA、cwd及历史快照；静态核对最终argv仅增加verbose和替换诊断名称、无json-strict冲突，核对实际执行环境及PATH。受限环境读表失败即NO-GO，不复用旧ABSENT。授权前不创建会话、不执行任何native初始化。
- **PASS**：本次会话在45秒内成功创建并完成具名收尾，或失败但固定证据确能收窄阻塞阶段；两者分别标记 `BOOTSTRAP_SUCCEEDED` / `FAILURE_LOCALIZED`，不可互相代称。零prompt调用、历史不变、收尾可信是共同条件。成功只证明当前启动，不证明历史根因或完整业务主流程。
- **FAIL**：45秒到期仍未成功且日志不能区分阶段，或出现预料外prompt/工具执行、历史被改动、停止状态不明。记录 `UNRESOLVED_TIMEOUT` 或具体违规事实并停止，不据失败自动延时/改权限/再次启动。
- **INVALID**：工件/config/cwd/policy或进程读取前提不合格、日志丢失/身份无法关联、执行的不是候选命令，导致本次不能回答问题。不能以初始化成功抵消前提错误。
- **Stop-loss**：一次初始化、45秒等待、15秒收尾预算、prompt配额0；遇批准外权限/交互、配置漂移或不可确认外部状态即停。90秒观察和第八例均不在本授权包内。
- **Run artifact freshness**：开跑前固定选定acpx dist/wrapper和实际客户端字节、PATH解析与policy身份；既有源码只能解释匹配装配。产品未修改，不重跑无关Maven或重打包JAR来制造新凭证。

## 顺序与失败切面

| 前态/动作 | durable事实与下一消费者 | 失败处置 |
|---|---|---|
| 未批准 | 只有本方案及argv候选；零外部启动 | 不运行 |
| 批准后只读核验 | 固定身份/进程读取结果，由本次启动检查消费 | 任一UNKNOWN/漂移不启动 |
| 固定命令身份、开日志→启动 | 仅本次原生初始化执行，记录PID/start/时间与输出 | 启动不明不重试，停止核对 |
| launch→initialize/auth→session/new | 消费既有verbose/协议/metadata，按实际证据标阶段 | 缺标记不反推具体原因；45秒到期终止本次 |
| 成功/失败→收尾 | 具名close/show及可读进程观察；报告当前结果 | 停止未确认保持UNKNOWN，禁第二次启动 |
| 固定结果、核历史SHA | 用户据此决定是否需要后续观察或新业务Gate | 不自动产生第八例、预算或人门批准 |

原45秒是外层runner等待，不以CLI `--timeout 45`替代；两者覆盖阶段不同。运行前应按原已实现终止行为接入现有执行方式并离线审定，不能以只发terminate父进程冒充进程树收尾。若需要产品改动、遥测框架或新的权限平台，停止提交成本取舍。

## 六维与证据上限

Declared：本候选，仅用户批准后生效。Proven：当前只有静态入口和历史事实，原生行为NOT RUN。Consumed：授权后的单次诊断执行者消费准入事实，业务状态仍归原owner。Enforced before：未来启动前必须实核批准/身份/进程，当前未装配运行不得称已强制。Failure route：UNKNOWN即停止、无自动重试、历史不回填。Cannot-bypass：沿用已受审进程guard成对证明；最终观察执行方式仍须授权后离线检查“无prompt/失败零后续”。这是方案，不能冒充已完成新Gate的执行证明。
