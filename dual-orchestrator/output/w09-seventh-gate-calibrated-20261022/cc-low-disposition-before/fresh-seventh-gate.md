# W09 第七例：完整主流程 Gate

2026-10-22（Asia/Shanghai）。状态：**OFFLINE_PREPARED / AWAITING_CURRENT_RUN_APPROVAL / REAL_RUN_NOT_STARTED**。

用户已同意离线准备、预算170万token/18请求/60分钟和储备规则；材料齐备后交用户批准，当前不得真实运行。F1/F2修复及发行候选已获用户带回的合并CC认可；本包不重开产品设计，不推断主流程已证。

## 本次买到什么证明

[产品需求](task-requirement.md)与第六例字节相同：标准库换行规范化函数、unittest、README，AC-1/2/3不变。目标为一个fresh任务走完真实提案/复评/返修、当前GOAL批准、设计/计划/复评、必要readiness补齐、当前EXECUTION精确范围批准、实现/自测、独立验收、最终人门、隔离Git apply、用户commit、精确COMMITTED。设计READY、合法full合并或测试通过均不是完整Gate PASS。

本轮不声称省token。full返修封闭字段提示和一次具体诊断修正、混合stdout用量识别，将在合法自然路径上接受真实验证；未发生的分支记NOT_EXERCISED，不制造额外探针。初次GOAL前真实目标级变化仍仅走原有资格出口；已授GOAL/EXECUTION后的目标变化停交用户新workstream，不在线重写。

## Verification Gate

- Target：在本包选定发行与新隔离实例上，真实完成完整研发、逐条验收和COMMITTED交付，同时保持批准/身份/scope/证据与预算纪律。
- Source of truth：原current/node/gate/typed合同与ref+SHA；R01/R02/R03/L03固定输入、完成与唯一outcome；原verification-observations/源码和candidate身份；delivery唯一owner的operation与真实HEAD/parent/tree/clean/COMMITTED。预算消费每run+occurrence的固定可归因usage/acquisition和日志SHA。observer是只读operator检查器，不产生业务事实或授权。
- Trigger precondition：本轮静态离线核对完成且当前Codex/config候选差异通过受影响CC复审；用户当次批准本Gate和真实运行；新实例尚无run/session/批准；659输入及发行/配置/脚本保持本包身份；单INLINE/liteflow/同一router ClassLoader，无并行worker/server路由；旧具名执行无已知活动；各条验收有已实现出口。
- How precondition is checked before run：原10月16日临时实例初始文件已缺失，保持原材料不变；本轮在新的空路径通过原start、不带--run生成替代离线实例。见gate-local-preflight.json、selected-release.json、waiting-period-drift.json、host-environment-identity.json、old-execution-recheck.json、verification-exits.md、observer与预算对照。本轮不调用原生客户端或build-info/config命令；实际acpx dist字节与既有build-info对应，当前配置文件以字节核对和已核安装候选记录为准。**获准后、首次派发前**再运行只读pre-dispatch-recheck.py，核同一实例/所有冻结输入/零run和初始current、config/native字节与当前PATH；当次授权单独记录，不能靠本包预填true。原实际R01仍核本次解析/兼容身份；宿主环境与npx缓存测量不冒称原生工具执行/活adapter已验证。
- PASS：完整当前批准谱系；每项实际证据与独立验收；exact candidate；当前最终人门；真实隔离apply；用户commit与精确COMMITTED；全程可归因用量、预算准入和止损义务成立。
- FAIL：有效装配下合法流程误拒/死端、非法采纳、按已批准验收标准的产品拒绝、主因漂移、缺必要执行证据；归因不可用/部分量无法聚合、预算储备不足或止损触发。实验FAIL与领域状态分列，合法SUCCESS/RUNNING保持，不为实验停止修改账本。
- INVALID：错误发行/冻结输入/需求/出口/启动前提使本次不能证明目标；或运行中替换判据/工件、手改账本或伪造证据。合法客户端形态触发产品拒绝或归因失败仍属FAIL，不换成INVALID减轻结论。
- Stop-loss：170万可见可归因请求totalTokens（含cachedRead）；120万预警；18次真实顶层prompt（含correction）；3600秒活动时间；每命令600秒。无新证据同根因两次、有新证据语义循环超过2次、外部UNKNOWN、缺usage、身份/scope/authority漂移、必要出口不存在、实质成本收益扩围，按下述失败分级与预算规则处置；BOUNDARY/UNKNOWN或止损时停止新派发并保存证据，不自动加额或重发未知执行。
- Run artifact freshness：CLI `6e928a89a364fa24271972bfae3b71fe4b9c909782bfa14ca2d4ef7a36aba3a8`；server `34197f5f746821f87ff8e363b61995aaf57e8615c6bd57903bc32da6cda05c4e`，使用上轮受审的独立冻结副本，不使用随后的target构建JAR。659源码输入本轮重核一致，原1813/210单轮凭证仅作该发行离线依据，本轮未重跑Maven；12 observer/21预算对照另列。材料冻结及派发前重核，不换jar续用本Gate。

## 实际隔离与装配

- target：`/private/tmp/dual-w09-newlines-20261022-offline-07/target`
- worktree：`/private/tmp/dual-w09-newlines-20261022-offline-07/worktree`
- branch：`codex/w09-newlines-20261022-offline-07`
- workstream：`ws-20261022T150557Z-Y2KB`，由原start生成，不是手工造ID。

初始Git空树/clean、共享git-common-dir和base一致；原current=RUNNING，唯一初始context READY，**零run/门/业务批准**。初始化是离线准备；零真实请求，不是已启动第七例模型运行。

显式选本仓受审acpx wrapper、600秒、liteflow及隔离operator registry；不改机器环境、设置或安装。冻结dist字节仍对应既有1517f828 build-info，acpx配置字节未变，两客户端命令承接原记录（Claude Code作者/Codex复评，底层模型品牌不进入Dual领域规则）。PATH上的其他acpx不作为运行入口。等待期间Codex配置和本机Codex程序指纹改变；29项候选文件中27项字节未变。配置中已审的model、effort、context_window、auto_compact阈值与10月11日记录一致，但没有旧完整配置字节可作全部语义差异证明。新候选逐项记录于waiting-period-drift.json/current-config-and-installed-sources.json，交CC判断，不静默视为等价；本轮零设置改写、零新trust登记。程序升级不等于已不兼容，当前字节核验也不证明活接口兼容；真实入口仍在R01核验，失败按typed原因处置。

`defaultPermissions=approve-all`是config元数据，不是Dual许可；实际profile/policy由原transport逐turn绑定，correction仍deny-all。600秒为现有有限等待预算；若真实原生ask出现，仍须用户对当前offered-once请求决定、原C/X提交；ACK不是执行完成。不制造权限请求来刷覆盖。任何未触发lane如实记NOT_EXERCISED。

保留已有模型/effort组合，无新限额/思考/版本锁实验。已核安装候选字节不代表将来npx解析永远不变，实际R01解析不符即停；不得联网安装来悄悄“修齐”本Gate。初始化加载记录表明本次ledger factory/kernel来自选定JAR的同一LaunchedClassLoader；实际路由进程的PID/loader在将来派发时再核。

## 校准后的储备式准入

校准依据：[六例报告](../w09-seventh-budget-calibration-20261022/assessment.md)、逐请求数据与来源SHA。22次请求分析合计835,216，含失败/修正/返修，不是完整链成本；三处已验证只读修正不回填旧账本。四个阶段有小样本，后半五阶段625,000仍是规划假设；不声称p95、节省率或170万必需。

预算以budget.json为本候选唯一规划表，check_budget.py和observer读取它，不另造运行用量来源。需求作者40k、需求复评50k、设计150k、架构复评90k（已测53,075/70,687，原“无样本”表述撤销）；计划100k、计划复评75k、实现200k、自测150k、验收100k仍为未测假设。正常九节点955,000。

| 路径规划 | token | 请求数 |
|---|---:|---:|
| 九节点各一次（不含修正） | 955,000 | 9 |
| 主链+readiness计划重跑+一次修正 | 1,280,000 | 12 |
| 上行+一次需求返修 | 1,370,000 | 14 |
| 主链+readiness计划重跑+一次设计返修+一次修正 | 1,520,000 | 14 |
| 主链+readiness计划重跑+需求/设计各返修一次+一次修正 | 1,610,000 | 16 |

170万仍为用户选定的风险上限，非预定消费。设计返修示例余180k；需求/设计两轮都发生时余90k。格式修正仅两项需求阶段实测26,548/19,570，不能推出其他阶段也便宜；暂保留150k修正储备，不新增动态额度机制。请求18次、活动3600秒、120万预警、单命令600秒不变。

每次派发前：**已用 + 全部未完成主链储备 + 本次返修/重入导致的重复工作 + 一次可能修正150,000 ≤1,700,000**；调用池同构≤18。当前节点若已在未完成尾链中不另加一次；已完成而被本次重入影响的后续节点列入extra。首次准入1,105,000/10请求，不是已消耗量。planning保守预留受重入影响的已完成尾链，不删除尾链刷准入。21项对照包括精确阈值、一token/一秒不足、真实调用计数、设计/双循环与未知/重复路径拒绝。

下一agent块活动时间预留1500秒（2×600+300）；业务人门等待单独记暂停，原生执行/权限等待计活动时间。完整完成粒度核用量，不承诺内部抢占/绝对零超支；单请求越线后零下一派发，实验FAIL。未知/快照/部分量不填0；provider/model/billing与token/金额分别记录，不把SDK美元当订阅实付，不因无法算实付金额删除有效token。

observer消费固定R03/usage/acquisition/日志SHA，再核当前transcript；缺当前完成才走严格唯一共享出口。stream-only且跨代重复仍UNAVAILABLE即停，不自动开启B案。不按最新/文本/首末匹配，不人工回填预算，不改会话策略救样本。预算ALLOW只是operator核对，不是业务、人门、原生once或生产dispatch许可。

失败先按当前AGENTS的typed primaryReason及证据分级：TRANSIENT仅在既有安全合法入口和重试限额内处理，所有实际请求计预算；不因小格式噪声自动重开全套边界审查。BOUNDARY或外部副作用不明停止，禁止靠重试探测。有限储备不保证所有允许重试都能获准，未来每次请求仍重核；本包不新增重试实现、不重开终态。

## 业务门、验收与交付责任

用户是本次operator和当前人工决定者，负责当前subject批准、必要readiness声明、原生once选择，以及隔离target的产品commit。可以协助执行已明确批准的决定，但不得把本Gate运行授权批量代填后续门。定义中的requirement/GOAL contract/design/plan/EXECUTION及最终门按实际到达的当前read model处理；“预算同意”不等于批准尚未生成的代码范围。

[逐条出口](verification-exits.md)：AC-1/2实际Python测试输出与源码对应，AC-3实际read/例子核对；不要求独立测量退出码，不等于只看completed就验收。readiness在真实GOAL/等待subject产生后经原入口登记ref/SHA，当前不造占位引用；材料不是工具执行证据。工具不可用或切executor/evidence_kind须停、改合同复评重批。

交付只消费当前最终批准candidate，原D09 apply后等用户commit再finalize；提交门未批零apply，不代用户提交。外部UNKNOWN走原裁定，不清账本、不直接retry。停止后关闭当前具名会话、核可观察PID，保留现场，不把父PID缺席泛化成子树全停。第六例FAIL/UNAVAILABLE不变，旧余额无效。

已登记toolCallCount/通用RUNNING恢复投影限制继续保留：预算只用固定occurrence，通用retry提示不是续作许可；若影响必要证据/安全动作立即停止。浏览器、多worker/多ClassLoader、全部故障注入不属本次证明目标。

## 批准范围

本包完成离线核对；当前客户端/配置候选变化须随本包交CC受影响复审。**下一动作：CC复审；材料通过后交用户批准当次真实运行**。170万预算方案已获用户确认，不重复索要预算决定。批准后记录新的人工授权，运行前只读重核同一实例与冻结身份；重核失败NO-GO，不重新初始化绕过。所有后续业务门仍按当前subject单独决定。

合法非阻塞验收备注按当前AGENTS处理：不由Gate仅凭存在notes拒绝交付；仍须原typed合同合法、独立验收/当前最终批准及交付资格，不创造新的wire枚举或伪造ACCEPTED。
