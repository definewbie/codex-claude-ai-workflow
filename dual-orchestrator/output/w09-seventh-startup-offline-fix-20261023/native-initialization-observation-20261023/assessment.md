# 一次禁 prompt 初始化观察结果

**PASS / BOOTSTRAP_SUCCEEDED。** 获准的一次初始化在3.548秒内正常返回（exit=0），创建协议版本1的具名会话；close/show均exit=0，元数据closed=true。启动到全部观察收尾共4.835秒，收尾在15秒预算内。没有追加90秒观察，没有恢复第七例或启动第八例。

权威记录：[result.json](result.json)、[原生命令结果](initialization-result.json)、[会话关闭后原记录](session-records-after-close.json)、[停止核验](post-stop-process.json)、[授权记录](authorization.json)。这是作者执行结论，不冒充CC独立评审。

## 本次证据支持什么

- 运行前重新核验所有32项入口/配置/策略身份，零漂移；成功读取进程表，60个已知旧PID缺席，具名工作区未见活动进程。启动前再次消费受审进程guard，没有用受限读取失败或旧ABSENT放行。
- 仅调用原受审acpx的 `claude sessions new`，增加既有verbose、替换独立诊断会话名。stdin=DEVNULL；原45秒外层等待、只读工具策略和其他限制不变。源码入口与最终argv的离线核对、两个纯本地进程收尾对照见 [offline-harness-check.json](offline-harness-check.json)；它们不是额外原生初始化。
- 日志依次出现原wrapper/npx启动信息、`initialized protocol version 1`、session/query和创建成功。由此证明**本次**启动链已穿过adapter初始化/必要认证以及session/new；不证明GLM推理或业务工具能力。
- `acpx_record_id` 与 `acp_session_id` 均为 `572ac958-e2a7-4373-90b5-b1cfd401c156`，内部agentSessionId合法缺席。关闭后closed=true，lastPrompt为空、historyEntries=0、request_token_usage为空。执行命令中没有prompt，持久会话没有prompt历史；会话stream文件并未产生，不能把“空方法计数”冒充抓到了完整协议帧。
- **token未测量**，不是实测0。没有以初始化成功声称GLM连接/用量/独立验收/交付已证。

## 收尾事实与实际程序身份

初始化命令退出后，成功读取的进程表还见到一个同自有进程组的SDK进程（PID65884）。本次runner将命令置于独立进程组，按授权仅终止该自有组，再用既有具名close/show关闭记录，最后核验自有组与具名工作区/session无匹配进程。不能由这一短暂观察推断永久泄漏，也不声称任意子树或远端工作全停。

该进程直接显示实际程序是SDK携带的 `@anthropic-ai/claude-agent-sdk-darwin-arm64/claude`（包版本0.3.232），已固定SHA于result.json；**不能以PATH中的个人claude字节代替它的身份**。后续Gate应把该实际被观察程序纳入装配核对，而不是增加版本锁、安装或新身份平台。本次没有修改选择规则。

## 尚未证明与历史保全

第七例内部45秒消耗在哪一阶段仍未证。本次成功没有复现它，不能归因为网络/缓存/沙箱，也没有证据支持提高45秒。日志中的`apiType=native/baseUrl=native`只是初始化诊断，不构成已连接GLM或其他具体provider的证明；本次没有推理请求来回答该问题。

77份历史快照及真实旧ledger逐字节一致、无新增业务账本文件；659产品输入/JAR/配置均保持。旧stop-required不变，第七例仍INVALID，原session_bootstrap_failed及usage UNAVAILABLE不回填。新原生副作用限本次诊断会话及正常原生日志/metadata；这些不赋予旧run新执行资格。

## 六维自查

Declared：用户单次45秒+15秒、禁prompt、必要auth/网络授权。Proven：实际命令exit/耗时、verbose标记、原生metadata、close/show及成功读表、历史SHA。Consumed：本次诊断runner消费准入与命令结果；业务SoT仍由原ledger owner维护。Enforced before：身份和可读进程事实在初始化前重核；命令仅限session创建/具名close/show，不进入业务runner。Failure route：一次尝试消费标记、超时终止自有组、未知停止不重试；本次走成功收尾。Cannot-bypass：受审进程guard成对证明、最终argv精确对照、exclusive文件防重复启动；历史停止标记未被绕开。范围仅本次初始化观察，不外推完整业务主流程PROVEN。

## 下一步最小动作

**离线把受审进程guard装配进下一份fresh Gate，并将实际SDK子进程身份纳入启动核对，然后交用户批准当次真实运行。** 已校准预算可作为规划输入，但旧授权/余额不继承。本次不启动该准备或新例；无需继续无定向追查历史原因、延长超时、修改产品或重跑无关Maven。若未来再次出现相同超时，保留本次已有verbose证据再按实际失败分级处置，不预建遥测平台。

操作记录：首次人工摘要误用camelCase读取snake_case持久字段，产生显示null；随后直接核验原记录并以正确字段固定result。属于本轮检查错误，不是会话字段缺失或新Gate故障，原材料未改。
