# W09 第七例：完整主流程 Gate

2026-10-16（Asia/Shanghai）。状态：**OFFLINE_PREPARED / AWAITING_CURRENT_RUN_APPROVAL / REAL_RUN_NOT_STARTED**。

用户已同意离线准备、预算170万token/18请求/60分钟和储备规则；材料齐备后交用户批准，当前不得真实运行。F1/F2修复及发行候选已获用户带回的合并CC认可；本包不重开产品设计，不推断主流程已证。

## 本次买到什么证明

[产品需求](task-requirement.md)与第六例字节相同：标准库换行规范化函数、unittest、README，AC-1/2/3不变。目标为一个fresh任务走完真实提案/复评/返修、当前GOAL批准、设计/计划/复评、必要readiness补齐、当前EXECUTION精确范围批准、实现/自测、独立验收、最终人门、隔离Git apply、用户commit、精确COMMITTED。设计READY、合法full合并或测试通过均不是完整Gate PASS。

本轮不声称省token。full返修封闭字段提示和一次具体诊断修正、混合stdout用量识别，将在合法自然路径上接受真实验证；未发生的分支记NOT_EXERCISED，不制造额外探针。初次GOAL前真实目标级变化仍仅走原有资格出口；已授GOAL/EXECUTION后的目标变化停交用户新workstream，不在线重写。

## Verification Gate

- Target：在本包选定发行与新隔离实例上，真实完成完整研发、逐条验收和COMMITTED交付，同时保持批准/身份/scope/证据与预算纪律。
- Source of truth：原current/node/gate/typed合同与ref+SHA；R01/R02/R03/L03固定输入、完成与唯一outcome；原verification-observations/源码和candidate身份；delivery唯一owner的operation与真实HEAD/parent/tree/clean/COMMITTED。预算消费每run+occurrence的固定可归因usage/acquisition和日志SHA。observer是只读operator检查器，不产生业务事实或授权。
- Trigger precondition：本轮离线前提全部核齐；用户当次批准本Gate和真实运行；新实例尚无run/session/批准；659输入及发行/配置/脚本保持本包身份；单INLINE/liteflow/同一router ClassLoader，无并行worker/server路由；旧具名执行无已知活动；各条验收有已实现出口。
- How precondition is checked before run：本轮原start不带--run已完成本地初始化；见gate-local-preflight.json、selected-release.json、config-show/build-info、host-environment-identity.json、old-execution-recheck.json、verification-exits.md、observer与预算对照。**获准后、首次派发前**再运行只读pre-dispatch-recheck.py，核同一实例/所有冻结输入/零run和初始current、config/native字节与当前PATH；当次授权单独记录，不能靠本包预填true。原实际R01仍核本次解析/兼容身份；宿主环境与npx缓存测量不冒称原生工具执行/活adapter已验证。
- PASS：完整当前批准谱系；每项实际证据与独立验收；exact candidate；当前最终人门；真实隔离apply；用户commit与精确COMMITTED；全程可归因用量、预算准入和止损义务成立。
- FAIL：有效装配下合法流程误拒/死端、非法采纳、产品验收失败、主因漂移、缺必要执行证据；归因不可用/部分量无法聚合、预算储备不足或止损触发。实验FAIL与领域状态分列，合法SUCCESS/RUNNING保持，不为实验停止修改账本。
- INVALID：错误发行/冻结输入/需求/出口/启动前提使本次不能证明目标；或运行中替换判据/工件、手改账本或伪造证据。合法客户端形态触发产品拒绝或归因失败仍属FAIL，不换成INVALID减轻结论。
- Stop-loss：170万可见可归因请求totalTokens（含cachedRead）；120万预警；18次真实顶层prompt（含correction）；3600秒活动时间；每命令600秒。无新证据同根因两次、有新证据语义循环超过2次、外部UNKNOWN、缺usage、身份/scope/authority漂移、必要出口不存在、实质成本收益扩围，均停止新派发并保存证据；无自动重发/加额。
- Run artifact freshness：CLI `6e928a89a364fa24271972bfae3b71fe4b9c909782bfa14ca2d4ef7a36aba3a8`；server `34197f5f746821f87ff8e363b61995aaf57e8615c6bd57903bc32da6cda05c4e`，使用上轮受审的独立冻结副本，不使用随后的target构建JAR。659源码输入本轮重核一致，原1813/210单轮凭证仅作该发行离线依据，本轮未重跑Maven；12 observer/13预算对照另列。材料冻结及派发前重核，不换jar续用本Gate。

## 实际隔离与装配

- target：`/private/tmp/dual-w09-newlines-20261016-fresh-07/target`
- worktree：`/private/tmp/dual-w09-newlines-20261016-fresh-07/worktree`
- branch：`codex/w09-newlines-20261016-fresh-07`
- workstream：`ws-20261016T142045Z-UDHW`，由原start生成，不是手工造ID。

初始Git空树/clean、共享git-common-dir和base一致；原current=RUNNING，唯一初始context READY，**零run/门/业务批准**。初始化是离线准备；零真实请求，不是已启动第七例模型运行。

显式选本仓受审acpx wrapper、600秒、liteflow及隔离operator registry；不改机器环境、设置或安装。实际build-info与1517f828 patch匹配，config show的两客户端命令与第六例相同（Claude Code作者/Codex复评，底层模型品牌不进入Dual领域规则）。PATH上的其他acpx不作为运行入口。Codex配置唯一变化是既存第六例同权限trusted登记，其他字节精确匹配原基线；本轮未写新trust条目。

`defaultPermissions=approve-all`是config元数据，不是Dual许可；实际profile/policy由原transport逐turn绑定，correction仍deny-all。600秒为现有有限等待预算；若真实原生ask出现，仍须用户对当前offered-once请求决定、原C/X提交；ACK不是执行完成。不制造权限请求来刷覆盖。任何未触发lane如实记NOT_EXERCISED。

保留已有模型/effort组合，无新限额/思考/版本锁实验。已核安装候选字节不代表将来npx解析永远不变，实际R01解析不符即停；不得联网安装来悄悄“修齐”本Gate。初始化加载记录表明本次ledger factory/kernel来自选定JAR的同一LaunchedClassLoader；实际路由进程的PID/loader在将来派发时再核。

## 储备式准入（原规则）

见budget.json/check_budget.py。正常9节点940,000；示例加readiness重入175,000、一次intake返修90,000及一次格式修正150,000，共1,355,000/14请求，余345,000/4请求。全部是临时规划额度，不是p95/请求硬上界或成功率承诺。

每次派发前：**已用 + 全部未完成主链储备 + 本次必需返修/重入 + 一次可能修正150,000 ≤1,700,000**；调用池同构≤18。重复早阶段时保留失效尾链，不仅保下一小调用；按occurrence计真实请求，不能按run数猜correction。当前fresh准入需1,090,000/10请求（九节点加一次修正），并不代表这些量已经消耗。

下一agent块活动时间预留1500秒（2×600+300）；业务人门等待单独记暂停，原生执行/权限等待计活动时间。完整完成粒度核用量，不承诺单请求内抢占/绝对零超支；单请求越线后零下一派发，实验FAIL。未知/快照/部分量不能填0；金额与token分列，不把SDK美元当订阅实付。没有可靠provider/model/billing关联时停止并报告，不发明混合定价。

observer按固定R03/usage/acquisition/输出SHA核对，再核当前transcript；缺当前完成才走严格唯一共享出口。stream-only且跨代重复仍UNAVAILABLE即停，不自动开启连接代B案。不按最新/文本/首末匹配、人工回填预算或改会话策略救样本。

## 业务门、验收与交付责任

用户是本次operator和当前人工决定者，负责当前subject批准、必要readiness声明、原生once选择，以及隔离target的产品commit。可以协助执行已明确批准的决定，但不得把本Gate运行授权批量代填后续门。定义中的requirement/GOAL contract/design/plan/EXECUTION及最终门按实际到达的当前read model处理；“预算同意”不等于批准尚未生成的代码范围。

[逐条出口](verification-exits.md)：AC-1/2实际Python测试输出与源码对应，AC-3实际read/例子核对；不要求独立测量退出码，不等于只看completed就验收。readiness在真实GOAL/等待subject产生后经原入口登记ref/SHA，当前不造占位引用；材料不是工具执行证据。工具不可用或切executor/evidence_kind须停、改合同复评重批。

交付只消费当前最终批准candidate，原D09 apply后等用户commit再finalize；提交门未批零apply，不代用户提交。外部UNKNOWN走原裁定，不清账本、不直接retry。停止后关闭当前具名会话、核可观察PID，保留现场，不把父PID缺席泛化成子树全停。第六例FAIL/UNAVAILABLE不变，旧余额无效。

已登记toolCallCount/通用RUNNING恢复投影限制继续保留：预算只用固定occurrence，通用retry提示不是续作许可；若影响必要证据/安全动作立即停止。浏览器、多worker/多ClassLoader、全部故障注入不属本次证明目标。

## 批准范围

本包已完成离线核验。**下一动作：用户批准本Gate、170万/18请求/60分钟及当次真实运行**。批准后记录新的人工授权，运行前只读重核同一实例与冻结身份；重核失败NO-GO，不重新初始化绕过。所有后续业务门仍按当前subject单独决定。
