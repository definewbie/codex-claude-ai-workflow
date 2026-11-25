# 第八例续作 Gate（待运行批准）

2026-10-24，Asia/Shanghai。仅离线装配，run-authorization.json 为 false。使用原工作流实例 ws-20261023T005431Z-9YYF、原target/worktree及冻结发行，不创建第九例，不改写第八例的INVALID或原stop-required。续作是新的实验准入授权，不是重写旧结果、自动重试或领域恢复命令。

## Verification Gate

- Target：从原READY节点继续，经过原合同/readiness/批准/权限检查，完成实现、实际自测、独立验收、当前最终交付门、隔离Git apply、用户commit与精确COMMITTED；保持原批准谱系及固定事实。不把离线装配PASS当作真实主链PASS。
- Source of truth：同一原ledger的current/node/gate/合同/ref+SHA、原R01/R02/R03/L03和唯一outcome、实际工具观察/candidate/delivery记录。用量由原reader/observer按run+occurrence全量重读，历史十请求已经包含于848,242；不再另加一次底数。累计时间由原程序操作回执底数加新回执组成。新包只有实验准入与观察权，没有领域批准权。
- Trigger precondition：用户具名批准本次EIGHTH_CONTINUATION及checkpoint；原发行/配置/装配、旧停止记录和完整旧包无漂移；第一次执行前原完整ledger检查点一致。无pending、无未结束或结果不明执行；旧83个具名PID确认有成功观察链接，六个本次具名会话保持关闭。新stop-required不存在。单INLINE/liteflow/单ClassLoader；没有并行路由服务、worker或同实例操作者；逐criterion出口仍适用。现在初次节点必须是node-plan-decision-026 READY。
- How precondition is checked before run：授权后先运行本包pre-dispatch-recheck.py（不启动Java/native），重核固定源、配置/安装基线、PATH/宿主依赖、原包/首次ledger、观察链接、同实例、完整固定用量与累计时间/储备。该脚本每次成功把自身耗时记入新回执。runner的第一项是新授权核验，每步再次执行共享inspect及原READY/身份检查。完整新状态依原Java资格与批准消费者核验，静态检查不替代原生接口的实际行为。
- PASS：完整目标及精确COMMITTED成立，所有必要验收有真实证据，独立验收和人门完整；原历史事实未洗白、旧请求未重复计数，累计预算/时间和派发前储备义务满足。
- FAIL：有效装配下主流程误拒/死端、非法采纳/越权、必要证据或用量不可核、尾链储备不足或止损触发。结果与领域状态分列；不伪造WAITING_FOR_RECOVERY、不把UNKNOWN记0、不扩大批准或自动重发。
- INVALID：错误装配/身份/检查点/授权使本次无法证明目标，手改账本、替换判据或重置累计账。原第八例停止保持原分类。合法客户端新形态暴露产品缺陷时仍按事实判FAIL，不能推给INVALID。
- Stop-loss：**同一第八例累计**170万可见可归因totalTokens（含cachedRead）、18次请求（含correction）、3,600秒已定义活动时间；120万预警。沿用原600秒命令/45秒bootstrap，不增加初始化观察。每次模型派发前为全部未完主链、当次返修和一次150k格式修正储备，请求池同构；下一agent块留1,500秒。无新证据同根因两次、有新证据语义循环超过2次、未知副作用、身份/权限/用量不明即停。业务等待不计时；模型、原生权限等待和已记录程序操作计时。预算不是单请求内部硬上限。
- Run artifact freshness：原659源码、CLI/server两个冻结JAR及所选wrapper/dist保持；本包新脚本与固定输入由launch-identity.json核验。产品零变化，不重建、不借历史Maven数字充本包证明。SDK实际选择/活客户端行为仍由原运行证据判断，旧SDK基线不是永久兼容保证。

## 当前累计账与剩余规划

|项目|已计入|原上限内余额|
|---|---:|---:|
|可归因token|848,242|851,758|
|顶层请求|10|8|
|已记录程序活动秒|1,263.444 + 新包成功重核回执|2,336.556 减新回执|

848,242 是累计已用 token，851,758 是原170万上限内的余额，两者使用同一可归因请求 totalTokens 口径（含 cachedRead）：851,758 = 1,700,000 − 848,242。预算核对只消费 observer 核验的累计 actual_tokens，余额由上限相减得到，不是第二份用量事实。

时间底数由21个step回执1,253.416秒、5个人工决定命令6.405秒、12个关闭/核验命令1.976秒、初始本地操作1.647秒组成，原始ref/SHA逐条固定，重读校验不允许清零。该口径是程序已记录操作耗时，不冒称所有人工/离线诊断墙钟时间。人工等待、本轮离线编写/审核不计原生运行时间。以后所有续作step、业务决定命令、清理与成功重核操作都需在本包相应目录记录完整process/result及耗时；有process无result或step无result时禁止重派发，先核外部状态。

正常未完模型尾链为implement 200k、自测150k、独立验收100k，加一次修正150k，共600k/4请求；余额内有251,758 token和4请求额外空间。额度仍为原规划假设而非后半链实测。当前下一步plan.decision本身不调用模型；不得直接跳implement或借此绕EXECUTION资格。已经完成的设计/计划不得为凑fresh形式重跑。

旧会话关闭后的原生入口：AcpxAgentTransport.ensureSession仍按原profile选择new/ensure；受审acpx的ensure按目录查找，默认匹配排除closed记录，没有可用记录时进入createSession。静态源码落点：AcpxAgentTransport.java:669、session-management.ts:287、session/persistence/repository.ts:68/249。未执行原生验证，不推断旧会话一定可恢复，也不手工重开它。若需要额外模型prompt，必须先纳入本预算/储备；不把bootstrap、session创建或ACK当模型执行成功。

## 历史保全与业务责任

原包保持只读；原stop-required的SHA及402文件固定。现有ledger在本轮不变；获准后只允许原Java正常追加与当前状态更新，不手工迁移或修补。旧runs固定字节每步保持，首次以完整541文件检查点防止准备到批准间漂移；新动作必须追加到新steps目录，已有目录不覆盖。旧INVALID永远保留，即使续作成功也单独报告新准入结果与累计主流程结果。

用户仍决定原生once请求、当前业务门、最终交付及产品commit；当前GOAL/readiness材料不自动授写。验收要求、scope或证据类型变化仍走原复评/重批。新授权文件只准运行，不委派业务决定；本轮没有生成任何当前产品批准。原Gate任务、验收出口与边界文件字节复用。

若本续作发生新停止，在新包创建新的stop-required，不删除旧标记、不据旧缺陷特例豁免新失败。进程观察只证明列出的旧目标，不是任意子树或远程停止。必要的新未决进程仍严格读表，PRESENT/UNKNOWN停止。没有PID+birth平台、daemon、新锁或产品兼容代码。

## 操作次序

1. 本包受影响合并复审；用户对本次续作运行作显式决定。当前授权false，无可执行的运行许可。
2. 获准后仅更新本包具名授权及其批准证据，旧fresh授权不继承。立即执行只读重核，任何漂移NO-GO，不重新生成实例绕过。
3. 重核通过后才以本包runner对当前节点执行一个原Java步骤；每步继续核累计账/当前资格，业务门等用户决定。未经许可的命令不会因材料PASS而启动。

所有离线模拟使用临时目录/合成usage/明确Java进程替身；不能将其解释成真实模型、Java状态迁移或Git交付已证。
